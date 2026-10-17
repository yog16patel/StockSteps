# Phase 7E — focused diff (caching, concurrency, provider calls)

## 1. Curated universe source — `server/.../screener/CuratedScreenerUniverse.kt`

```kotlin
override suspend fun universe(): UniverseDefinition {
    val (entries, due) = lock.withLock {
        val entries = file.companies.map { c -> val e = enriched[c.symbol]
            UniverseEntry(c.symbol, e?.name ?: c.name, e?.exchange ?: c.exchange, e?.country ?: "US", e?.sector ?: c.sector, e?.industry,
                e?.price, e?.marketCap, null, false, priceDate = e?.priceDate?.toString(), changePercent = e?.changePercent) }
        … val room = (perHour - attempts.size).coerceAtLeast(0)
        val due = file.companies.map { it.symbol }.filter { it !in inFlight && isDue(it, enriched[it], now) }
        entries to due.sortedBy { enriched[it]?.attemptAt ?: Long.MIN_VALUE }.take(room).onEach { inFlight += it; attempts.addLast(now) }
    }
    if (due.isNotEmpty()) pending = scope.launch(ProviderFeature("discover-enrichment") + ProviderPriorityElement(ProviderPriority.LOW)) { … }
    return UniverseDefinition(file.description, entries)
}
```

- **Decision:** the response never waits for the provider. Missing values stay null; the engine excludes and counts them in value
  filters and sorts them last.
- **Risk:** each instance enriches separately (per-instance caches). The budget is per hour per instance.
- **Concurrency:** one in-flight load per symbol (`inFlight`), 4 permits, and the shared caches' single flight underneath.
- **Tests:**
  - `firstOpenNeverWaitsAndEnrichmentIsBudgetedAndNotRepeated` (60 an hour; then nothing new for unchanged data);
  - `failuresStayUnavailableAndAreRetriedAfterTheCooldown`;
  - `repeatedAndConcurrentOpensShareTheCachesAndAColdOpenSendsNothingSynchronously` (310 once; 10 concurrent users + 10 opens → 0 more).

```kotlin
private fun isDue(symbol: String, e: Enriched?, now: Long): Boolean {
    if (e == null) return true
    if (now - e.attemptAt < if (e.failed) retryAfterMillis else 60_000L) return false
    val expected = eod.expectedSession(market, clock.instant())
    val priceBehind = e.priceDate == null || e.priceDate < expected
    val profileOld = e.profileAt == null || now - e.profileAt >= profileRefreshMillis
    return profileOld || (priceBehind && now - e.attemptAt >= 1_800_000L)
}
```

## 2. `ScreenerService`

- `pageQuotes = false` (curated): no `stocks.getStock` per page row. Before, each page cost 20 quote requests, mostly refused on this plan.
- An EOD price gets a `FinancialBasis("Latest close", date = entry.priceDate)` and `changePercent` from the previous trading day's close.
- Warnings:
  - "Prices and market caps are loaded for X of N companies so far…";
  - "Prices are official closing prices (latest close Oct 16, 2026), not live quotes.".
- `UniverseInfo.priced`.

## 3. Engine text search — core `ScreenerEngine.evaluate`

```kotlin
val text = query.text?.trim().orEmpty()
if (text.isNotEmpty()) {
    fun tier(row) = SearchRanking.tier(text, StockSearchResult(symbol = …, name = …, currency = …, exchange = …))
    val found = matches.filter { tier(it) != SearchRanking.Tier.PROVIDER }
    return Evaluation(sort(found, query.sort).sortedBy { tier(it).ordinal }, excluded)
}
```

- **Decision:** reuse the shared Company Search tiers (no new ranking); the sort is kept within a tier (a stable sort). The query
  fingerprint includes `text`, so cursors and the page cache stay per search.
- **Test:** `discoverWorksWithoutTheScreenerOrQuotes…` ("v" → V first; "apple" → AAPL; "brk.b" → BRK-B; no results).

## 4. Wiring — `Application.kt`

```kotlin
val fmpScreener = System.getenv("SCREENER_UNIVERSE")?.lowercase(Locale.ROOT) == "fmp"
val realUniverse by lazy { if (fmpScreener) FmpScreenerUniverse(…) else CuratedScreenerUniverse(stockService::getProfile, charts::getDailyCloses, eodSessions, …) }
ScreenerService(universe = sources.screenerUniverse ?: realUniverse, pageQuotes = sources.screenerUniverse != null || fmpScreener, …)
```

MOCK passes its fixture universe, so the MOCK path, full records and per-row fixture quotes are unchanged.

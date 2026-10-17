# Phase 7A — focused diff (caching, concurrency, provider error classification)

Short excerpts; the full diff is in the working tree.

## 1. Quote outcome cache — `server/.../repositoryImpl/FmpStockProviderRepositoryImpl.kt`

```kotlin
private class QuoteOutcome(val quote: StockQuote?, val failure: StockProviderException?)

override suspend fun getQuote(symbol: String): StockQuote? {
    val ttl = freshness.ttl(SessionData.QUOTE, symbol)
    val outcome = financialCache.getOrLoad("quote:$symbol", ttl,
        resultTtl = { it: QuoteOutcome -> providerCooldown(it.failure, ttl) }) {
        try { QuoteOutcome(loadQuote(symbol), null) } catch (cause: StockProviderException) { QuoteOutcome(null, cause) }
    }
    outcome.failure?.let { throw it }
    return outcome.quote
}
```

- **Decision:** the same key and cache now hold the outcome rather than a success-only list, so no second layer is added. Single flight
  is inherited.
- **Risk:** only `StockProviderException` is cached; cancellation and other exceptions propagate, and nothing is stored for them.
- **Risk:** a cached failure is the same exception instance, rethrown to later callers (the existing Discover pattern).
- **Tests:** `QuoteQuotaProtectionTest` — 200 TTL; 402/403 1 h + recovery; 429 10 min; 503 30 s; 20 concurrent → 1; isolation.

## 2. Cooldown table — `service/SparklineService.kt` (`providerCooldown`, shared by every outcome cache)

```kotlin
failure == null -> success
failure.dailyLimitReached -> FinancialCachePolicy.FAILURE      // 30 s: local denial, no upstream cost
failure.restricted -> FinancialCachePolicy.ACCESS_COOLDOWN    // 1 h (402/403)
failure.failure == RATE_LIMITED -> RATE_LIMIT_COOLDOWN        // 10 min
else -> FinancialCachePolicy.FAILURE                          // 30 s
```

**Risk:** the `dailyLimitReached` row is new for every user of `providerCooldown` (profiles, charts, sparklines, Markets sections, the
screener universe). They recover about 30 s after the budget-day reset instead of 10 minutes. That only happens when a daily limit is
configured.

## 3. Daily allowance — `service/ProviderBudget.kt`

```kotlin
val waitMillis = synchronized(lane) {
    val t = now()
    rollDay(lane, t)
    val daily = lane.limits.dailyLimit
    if (daily != null && lane.today.get() >= daily) -1L
    else {
        lane.refill(t)
        if (lane.tokens - 1 >= reserve) { lane.tokens -= 1; lane.today.incrementAndGet(); 0L }
        else (((reserve + 1 - lane.tokens) * 60_000.0 / lane.limits.perMinute).toLong() + 1)
    }
}
if (waitMillis == -1L) { trial?.let { synchronized(lane) { it.trialInFlight = false } }; return denyDaily(provider, endpoint, lane) }
```

- **Concurrency:** the check and the count happen under the same lane lock as the token. A concurrency-slot denial gives the count back.
  - Test `concurrentCallersNeverExceedTheDailyAllowance`: 50 parallel callers → 10.
  - Benchmark: 50 → 10.
- **Behaviour change:** the daily count used by the existing soft target is now taken when the token is taken, not after the concurrency
  slot. It's refunded if the slot is refused, so the totals are the same.
- **Not done on purpose:** reading 429 bodies ("Limit Reach") to infer a daily cap.
  - Test `providerRateLimitsNeverSwitchOnTheDailyState`.
- **`ProviderLimits.dailyLimit`:** a new optional field, read from `PROVIDER_<NAME>_DAILY_LIMIT` and scaled by `local()`.
  - Test `withoutAConfiguredAllowanceNothingChanges`.

## 4. Typed errors — `service/SnapshotErrors.kt` (`ProviderErrors`), `ApiErrorHandling.kt`

```kotlin
fun of(cause: StockProviderException): Pair<Int, ApiError> = when {
    cause.dailyLimitReached -> 503 to ApiError(DAILY_LIMIT, DAILY_LIMIT_MESSAGE)
    cause.restricted -> 502 to ApiError(RESTRICTED, RESTRICTED_MESSAGE)
    else -> /* unchanged TIMEOUT 504 / RATE_LIMITED 503 / UNAVAILABLE 502 / INVALID 502 */
}
```

- **Decision:** the HTTP statuses stay the same; only the code and message say why. Routes (`StatusPages`) and section errors
  (`snapshotError`) use the same mapping.
- **Tests:** `quoteRouteSaysRestrictedNotNotFoundAndSuccessKeepsItsSchema`; the success body has no new fields; a missing symbol is
  still 404.

## 5. Markets sectors — `service/MarketsService.kt`

```kotlin
val quote = try { quote(symbol) } catch (cause: Exception) {
    if (cause is CancellationException && cause !is TimeoutCancellationException) throw cause
    (cause as? StockProviderException)?.takeIf { it.restricted }?.let(refusals::add); null
}
...
if (usable.isEmpty()) throw refusals.takeIf { it.size == SECTORS.size }?.peek() ?: StockProviderException(UNAVAILABLE)
```

**Decision:** report restricted only when *all* 11 funds were refused; mixed failures stay "temporarily unavailable". Test
`marketsSectorRefreshesDoNotRepeatRefusedQuotes` (11 → 22 → 44 requests over about 3 hours, section code `PROVIDER_RESTRICTED`).

## 6. Practice — `practice/PracticeService.kt`, `userdata/AlertEvaluator.kt` (`WatchMarketData`), core `PracticeFormat.previewPrice`

- `WatchMarketData.box` keeps the typed failure next to the null value. `quoteRestricted(symbol)` reads the same cached entry, so it adds
  no request.
- `priceForTrade` returns `QUOTE_RESTRICTED` only when the quote is null **and** the read was plan-refused; otherwise `QUOTE_MISSING`
  as before.
- `PracticeFormat.previewPrice(p)` → "Unavailable" when `p.price` isn't positive. It's used on the Android order screen (three sites) and
  the iOS one (two sites, via `IosPracticeClient.previewPrice`).
- **Tests:**
  - `aPlanRestrictedQuoteBlocksTradingWithItsOwnReasonAndIsNeverValued`: blocker, execute refused, overview value null, the holding
    kept;
  - `PracticeFormatTest.aPreviewWithoutAUsablePriceSaysUnavailable`.

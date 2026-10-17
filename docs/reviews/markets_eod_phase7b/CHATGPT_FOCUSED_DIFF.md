# Phase 7B — focused diff (financial calculation, caching, provider error handling)

## 1. The close pair — `server/.../service/EodCloses.kt` (`EodChange.of`)

```kotlin
val expected = sessions.expectedSession(m, now)                 // close + publication delay has passed
for (p in points) {
    val date = EodSessions.dateOf(p) ?: continue
    if (date > expected || !sessions.isTradingDay(m, date)) continue   // same-day partial rows, non-trading dates
    if (!p.close.isFinite() || p.close <= 0) { byDate.remove(date); continue }
    byDate[date] = p.close                                       // one value per date (last wins)
}
val (latestDate, latest) = byDate.entries.lastOrNull()?.toPair() ?: return null
val previousDate = sessions.previousTradingDay(m, latestDate)
val previous = byDate[previousDate] ?: return null               // never across a gap
val change = latest - previous; val percent = change / previous * 100
```

- **Decision:** the "previous close" is the previous *trading day* on the listing's own calendar (NYSE or TSX), not the previous row,
  so a provider gap can't create a two-day move.
- **Tests (`MarketsEodTest`):**
  - consecutive days, weekend, Monday, same-day partial;
  - Thanksgiving and the early close;
  - the TSX-only holiday;
  - missing previous/latest, zero, ∞, NaN, a duplicate date, equal closes, no calendar.

## 2. Session-aware lifetime — `EodSessions.ttl`, `PriceChartService`

```kotlin
val ttl = if (latest != null && latest >= expected) minOf(untilNext, MAX_TTL) else minOf(LATE_RETRY, untilNext)
...
private fun dailyTtl(symbol: String, outcome: Outcome) =
    if (outcome.failure == null && eod != null) eod.ttl(symbol, outcome.points, DAILY_TTL) else providerCooldown(outcome.failure, DAILY_TTL)
```

- **Risk:** the same `daily:SYM` series also feeds charts, valuation, portfolio history and earnings reactions. They now see a new close
  within ~30 min of its expected publication instead of up to 6 h later. Between closes, nothing is refetched.
- **Risk:** a provider correction appears at the next expected close at the latest.
- **Test:** `closesLiveUntilTheNextExpectedCloseAndALateCloseIsRetried` (late retry, pickup, weekend, Monday refresh);
  `concurrentReadsShareOneLoad`.

## 3. Markets — `service/MarketsService.kt`

- Indices: `if (dailyCloses != null && !eod.active(symbol, now)) return eodIndex(definition)`. The quote is used only while the market
  is active and the quote works; then the index's own close; then its fund's close (`isProxy`, fund currency, no trend); then
  `INDEX_UNAVAILABLE`. The proxy quote path is gone on the EOD path.
- Sectors: `eodSectors()`:
  - one `closes(symbol)` per fund through the shared cache;
  - rows on the newest `closeDate` only;
  - others named in `unavailable`;
  - every fund failed → `sectionFailure` keeps the type (daily allowance > all restricted > first other failure).
- Most active: `if (dailyCloses != null) return@coroutineScope MoverRules.mostActive(ranked)`. No quote per row; missing volume stays
  null.
- **Tests:**
  - `sectorsComeFromClosesWithTheirDatesAndNoQuotes`, `indicesUseTheirOwnCloseThenALabelledProxyClose`;
  - `anIndexWithoutAnyCloseIsUnavailableAndOtherSectionsStay`, `partialSectorFailureIsReportedPerSector`;
  - `allSectorsUnavailableKeepTheProviderReason`, `aConfiguredDailyAllowanceIsReportedForTheSection`;
  - `mostActiveRowsKeepTheirOwnFieldsWithoutQuoteEnrichment`, `repeatedOpensAndAWeekendReuseTheCloses`.
  - Existing `MarketsTest` (quote path, `dailyCloses = null`) is unchanged and passes.

## 4. Contract and labels — core `model/Markets.kt`, `markets/MarketsPresentation.kt`

```kotlin
val updated = index.closeDate?.let(::closeLabel)                   // "Last close · Oct 16"
    ?: epochSeconds(index.asOf)?.let { "As of ${timeLabel(...)} ET · ${dateLabel(...)}" }
val period = listOfNotNull(section.period, section.closeDate?.let(::closeLabel)?.replace("Last close · ", "Last close "),
    section.unavailable.takeIf { it.isNotEmpty() && !failed }?.let { "Not available: ${it.joinToString(", ")}" }).joinToString(" · ")
```

- **Decision:** both apps already render `updated` and `sectorsCaption(period)` from the shared presentation, so there is no Compose or
  SwiftUI change.
- **Test:** `MarketsPresenterTest.endOfDayValuesAreLabelledWithTheirCloseDate`.

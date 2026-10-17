# Phase 7C — focused diff (financial calculation, persistence-adjacent logic, caching)

## 1. Latest valid close — `server/.../service/EodCloses.kt`

```kotlin
val latest = points.mapNotNull { p -> EodSessions.dateOf(p)?.takeIf { it <= expected && sessions.isTradingDay(m, it) && p.close.isFinite() && p.close > 0 }?.let { it to p.close } }
    .maxByOrNull { it.first } ?: return null
var behind = 0; var d = expected
while (d > latest.first && behind <= 30) { d = sessions.previousTradingDay(m, d); behind++ }
```

- **Decision:** one rule for both features. Each caller sets how many sessions behind it accepts (valuation 5, execution 1).
- **Tests:** `PracticeEodTest.lateStaleMissingAndRestrictedClosesHaveTheirOwnReasons`,
  `PortfolioEodTest.aHoldingWithoutAUsableCloseMakesTheTotalUnavailableNotPartial`.

## 2. Practice execution — `practice/PracticeService.kt` (`eodPriceForTrade`)

```kotlin
val (closes, failure) = closesOrFailure(symbol)
val close = EodPrice.latest(symbol, closes, eod, marketClock.instant())
    ?: return null to (if (failure?.restricted == true) PracticeBlocker("QUOTE_RESTRICTED", …) else PracticeBlocker("QUOTE_MISSING", …))
if (close.sessionsBehind > EodPrice.EXECUTION_MAX_SESSIONS) return null to PracticeBlocker("QUOTE_STALE", "… is from ${shortDate(close.date)} …")
val (rate, fxAsOf) = fxRate(instrument.currency, close.date, null) ?: return null to PracticeBlocker("FX_UNAVAILABLE", …)
return Priced(instrument, PracticePrice(decimal(close.close), instrument.currency, rate, close.closedAt.toString()), EOD_BASIS, fxAsOf) to null
```

- **Risk:** this only changes where the price comes from. `preview()` and `execute()` are unchanged: atomic update, idempotency
  replay, `PRICE_CHANGED` check, cash and share checks in `PracticeEngine.blocker`.
- **Risk:** the blocker codes are the existing ones, so the apps' "Check the price again" button still appears for STALE/MISSING and
  not for RESTRICTED.
- **Tests:**
  - `buysAtTheLastCloseWithItsDateAndNeverReadsAQuote` (2 × 110 USD × 1.35 = 297 CAD; no quote read);
  - `sellsPartiallyAndFullyAndRejectsInvalidOrders`;
  - `theClientCannotSetThePriceAndRetriesNeverFillTwice` (10 concurrent retries → 1 fill).

## 3. My Portfolio valuation — `userdata/PortfolioMarketService.kt`

```kotlin
val closes = eod?.let { sessions -> symbols.map { symbol -> async { symbol to safe { dailyCloses(symbol) }?.let { series ->
    EodPrice.latest(symbol, series, sessions, clock.instant())?.takeIf { it.sessionsBehind <= EodPrice.VALUATION_MAX_SESSIONS } } } }.awaitAll().toMap() }
val marketQuotes = if (closes != null) emptyMap() else /* quote path unchanged */
...
val currentPrices = closes?.mapNotNull { (symbol, close) -> close?.let { c -> priceInTradeCurrency(symbol, c.close, c.date.toString())?.let { symbol to it } } }?.toMap() ?: /* quotes */
```

- **Risk:** `PortfolioEngine.value` is unchanged. A holding without a price is listed in `missing`, so the total, the P/L and the
  allocations stay null. That is the existing "missing stays missing" rule.
- **Decision:** the daily change on closes is `EOD_DAILY_NOTICE`, because the UI labels it "Today".
- **Decision:** `pricesAsOf` is the oldest close used; `quoteAsOf` is null on this path.
- **Tests:** `PortfolioEodTest` (complete: 2 × 120 × 1.40 = 336 CAD, basis 260 at the dated 1.30, P/L 76; partial; late close; shared
  cache across 10 users; split-like note).

## 4. Corporate-action guard

`isSplitLike(EodChange)` → notices only (Practice overview, Portfolio chart notice). No quantity, cost or transaction is changed.
Tests: `aSplitLikeMoveOfAHeldStockIsCalledOutNotAdjusted`, `aSplitLikeMoveIsCalledOut`.

## 5. Wiring — `Application.kt`

`eod = eodSessions.takeIf { dataMode == DataMode.REAL }` for `PortfolioMarketService` and `PracticeService`. MOCK keeps its fixture
quotes.

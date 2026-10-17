# Phase 7D — EOD and FX verification (mock level; live EOD BLOCKED by FMP 429)

| Scenario | Test(s) | Result |
|---|---|---|
| Valid latest close; previous session | `MarketsEodTest.twoConsecutiveTradingDayCloses`, `PracticeEodTest.buysAtTheLastClose…` | PASS |
| Weekend; US holiday (Thanksgiving, early close); TSX-only holiday | `MarketsEodTest.weekendMondayAndSameDayPartialRows`, `holidaysAndEarlyCloses` | PASS |
| Delayed publication (late close accepted for execution, dated) | `PracticeEodTest.lateStale…`, `PortfolioEodTest.aLateCloseIsUsedWithItsDate`, `MarketsEodTest.closesLiveUntil…LateCloseIsRetried` | PASS |
| Missing / invalid / stale close | `PracticeEodTest` (`QUOTE_MISSING`, `QUOTE_STALE` with the date), `PortfolioEodTest` (> 5 sessions → missing) | PASS |
| 402/403, 429 | `PracticeEodTest` (`QUOTE_RESTRICTED`; 429 → `QUOTE_MISSING`), `MarketsEodTest`, `QuoteQuotaProtectionTest` | PASS |
| Local daily allowance | `QuoteQuotaProtectionTest`, `MarketsEodTest.aConfiguredDailyAllowance…` | PASS |
| Concurrent requests, same symbol | `MarketsEodTest.concurrentReadsShareOneLoad`, `PortfolioEodTest.repeated…ShareOneCloseRequestPerSymbol` (10 users → 1) | PASS |
| Server-selected execution price | `PracticeEodTest.theClientCannotSetThePrice…` (`PRICE_CHANGED`) | PASS |
| No REAL → MOCK | `eod` is wired only in REAL (`Application.kt`); tests use REAL adapters with fakes | PASS (code + tests) |
| Missing never zero; EOD not labelled live | `PortfolioEodTest` (`quoteAsOf` null, `pricesAsOf` set, daily notice); `PracticeFormatTest` ("Unavailable") | PASS |
| Windows: execution ≤ 1 session, valuation ≤ 5 | `EodPrice` constants; `PracticeEodTest` (3 sessions → `QUOTE_STALE`), `PortfolioEodTest` (> 5 → missing) | PASS |

## FX date trace (code inspection + tests)

| Path | Price date | FX date | Notes |
|---|---|---|---|
| Practice execution | the close's session | the latest BoC publication ≤ the close date (≤ 7 days) | `fxRate(currency, close.date)`; missing → `FX_UNAVAILABLE`, never 1.0 |
| Practice valuation | the close's session | the same rule at the close date | missing → value null |
| My Portfolio cost basis | trade date | the publication ≤ the trade date (historical FX) | unchanged |
| My Portfolio current value | the latest close (≤ 5 sessions) | **the latest publication ≤ today** (`fxAsOf`) | **mixed dates**: e.g. a Thursday close with Friday's FX after a late publication. Both dates are shown; it's documented as a limitation, not changed |
| Portfolio history | each chart date's close | the publication ≤ that date | unchanged |
| Unsupported currencies | — | — | Practice: USD/CAD only (`UNSUPPORTED_INSTRUMENT`); Portfolio: no rate → missing |

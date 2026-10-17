# Focused diff — Phase 9B (commit on `e69626b`)

Applies because 9B changes provider calls, caching, concurrency and budgets (`AI_REVIEW_WORKFLOW.md` §3.4). Excerpts only.

## 1. Bounded keyless upstream call — `httpclient/NetworkUtils.kt`
```kotlin
suspend fun HttpClient.apiCallText(url: String, maxBytes: Long, configurationBlock: HttpRequestBuilder.() -> Unit = {}): String {
    val guard = ProviderGuard.current()
    val permit = guard?.acquire(ProviderCalls.provider(url), ProviderCalls.endpoint(url))
    …
    val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declared != null && declared > maxBytes) throw StockProviderException(Failure.INVALID_RESPONSE)
    val bytes = response.bodyAsChannel().readRemaining(maxBytes + 1).readByteArray()
    if (bytes.size > maxBytes) throw StockProviderException(Failure.INVALID_RESPONSE)
    …
} finally { if (permit != null) guard.complete(permit, status, …); ProviderCalls.record(url, outcome, started) }
```
Same permit → status classification → single `ProviderCalls.record` path as `apiCall` (the only upstream counter). Risk: a second copy of
the classification logic (kept identical; refactoring `apiCall` was avoided to leave every FMP/Finnhub path untouched). Logs use the bounded
endpoint label, never the path with a CIK. `ProviderCalls.provider` maps `sec.gov` → `sec`; `endpoint` → `xbrl/companyfacts` /
`submissions` / `company_tickers_exchange.json`. Tests: `SecEdgarClientTest.oversizedDocumentsAreRefused`, `endpointLabelsAreBounded…`.

## 2. SEC lane — `service/ProviderBudget.kt`
```kotlin
"sec" to ProviderLimits(perMinute = 300, burst = 10, maxConcurrent = 2)
val PUBLISHED_LIMITS = setOf("sec")      // doesn't block `verified`
ProviderLimits((perMinute ?: d.perMinute).coerceAtMost(if sec 600), (burst …).coerceAtMost(if sec 10), …)
```
SEC publishes 10 req/s; overrides can lower, never exceed (also a startup error above 600). Risk: none for other providers (their
values and `verified` rule are unchanged; `MarketDataConfigurationTest`). Tests: `theSecLaneIsPacedByProviderGuard`.

## 3. Discover daily cap — `screener/CuratedScreenerUniverse.kt`
```kotlin
// selection (under the lock): parts by priority 0 new, 1 close, 2 profile
if (stage == 2 && closesWaiting && perDay != null) { dayFull = true; continue }
val keep = if (stage == 2) closeReserve else 0
if (perDay != null && usedToday + reserved + d.parts + keep > perDay) { dayFull = true; …; continue }
…
// settlement after each company (exact upstream count from RequestCost)
if (perDay != null && budgetDay == day) usedToday += cost.data.get() - d.parts
```
Reserve-then-settle keeps concurrent passes under the cap; `RequestCost` is the counter admission already uses (incremented in
`ProviderCalls.record`), so cache hits and joined loads cost 0 and 402/429 count. `enrich` now requests only due parts. Risks: (a) behaviour
change on REAL: ≤ 100 enrichment requests/day by default (owner D-9B-3); (b) profiles refresh less often under a tight cap; (c) per
instance only. Pre-9B tests pin `perDay = null` to keep testing 7E behaviour. Tests: `DiscoverDailyBudgetTest` (4),
`MarketDataPhase9bBenchmarkTest` (cap every day, storm), `CuratedDiscover*` (unchanged expectations).

## 4. Fallback rules — `marketdata/SourceSelection.kt`
```kotlin
c.mode != requirement.mode -> SkipReason.WRONG_MODE
!gate.permits(c.provider, c.dataset, requirement.channel) -> SkipReason.NOT_LICENSED
requirement.adjustment != null && c.adjustment != requirement.adjustment -> SkipReason.ADJUSTMENT_MISMATCH
… catch (cause: StockProviderException) { RESTRICTED / RATE_LIMITED / FAILED; continue }   // one attempt per candidate
check(result.provenance.provider == c.provider)
if (result.provenance.stale) { keep first stale; continue }                                   // current data wins
```
Not wired to routes yet. Tests: `SourceSelectionAndLicensingTest` (8).

## 5. SEC period building — `marketdata/sec/SecCompanyFacts.kt`
```kotlin
val latestFiled = candidates.maxOf { it.filed }
val tag = candidates.filter { it.filed == latestFiled }.minBy { tags.indexOf(it.tag) }.tag
val sameTag = candidates.filter { it.tag == tag }.sortedWith(compareBy({ it.filed }, { it.accession }))
if (sameTag.groupBy { it.accession }.values.any { fs -> fs.map { it.value.stripTrailingZeros() }.distinct().size > 1 }) return null to FactIssue.AMBIGUOUS
val chosen = if (policy == LATEST_FILED) sameTag.last() else sameTag.first()
// Q4 (flows only): FY − 9-month YTD, same tag, derived = true
```
Risks: taxonomy variety on real filers (untested, no live calls); inferred fiscal years for the oldest comparatives. Tests:
`SecEdgarParsingTest` (10).

## 6. Metrics guards — `marketdata/metrics/FinancialMetricsEngine.kt`
```kotlin
if (!splits.verified && splitSuspected(h, ttm.periods)) → SPLIT_UNVERIFIED
priceCheck: !price.usableForRatios → PRICE_UNUSABLE; currency ≠ → CURRENCY_MISMATCH; fundamentals > 200 d or after price → PERIOD_MISMATCH
if (eps.signum() <= 0) → NOT_MEANINGFUL         // never a negative P/E
init { require((value != null) == (status == AVAILABLE)) }
```
Exact `BigDecimal`; no wiring. Tests: `FinancialMetricsEngineTest` (11, hand-calculated values listed in `FINANCIAL_METRICS_ENGINE.md`).

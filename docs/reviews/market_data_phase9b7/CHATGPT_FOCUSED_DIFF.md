# Focused diff — Phase 9B.7 (commit on `93fca36`)

## 1. HTTP-200 refusals become typed failures before the guard completes — `httpclient/NetworkUtils.kt`
```kotlin
val kind = AlphaVantageAnswers.classify(body)
if (kind != AlphaVantageAnswers.Kind.DATA) {
    status = AlphaVantageAnswers.status(kind)          // RATE_LIMITED 429, PREMIUM_ONLY 402, INVALID_KEY 403, ERROR_MESSAGE 404, other 503
    throw StockProviderException(if (kind == RATE_LIMITED) Failure.RATE_LIMITED else Failure.UNAVAILABLE, status)
}
… finally { guard.complete(permit, status, …); ProviderCalls.record(url, outcome, started) }
this.get(url) { …; parameter("apikey", apiKey) }       // the key never enters `url`
else -> Failure.UNAVAILABLE                            // unknown exceptions: typed, without their message (could hold the URL)
```
Risk: wording-based classification — a new Alpha Vantage phrasing becomes OTHER_NOTICE (503, 30 s), never data. Tests:
`AlphaVantageParsersTest.refusalsInsideHttp200…`, `AlphaVantageClientTest.refusals…`, `…anInBodyRateLimitOpensTheProviderBreaker`, `…theKeyIs…`.

## 2. Lane — `service/ProviderBudget.kt`
```kotlin
"alphavantage" to ProviderLimits(perMinute = 5, burst = 5, maxConcurrent = 1, dailyLimit = 25)   // published free allowance
alphaVantageLimits(env): plan = ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE → perMinute = burst = plan, dailyLimit = ALPHA_VANTAGE_DAILY_BUDGET ?: (null if plan else 25)
if (name == "alphavantage") … if (avEnabled && !planSet) verified = false
```
Tests: `theDailyBudgetAndRateLimitProtectTheQuota`, `theAlphaVantageLaneCountsTowardVerificationOnlyWhenEnabled`.

## 3. Dataset-level sources, no runtime fallback — `marketdata/alphavantage/AlphaVantageProviders.kt`, `Application.kt`
```kotlin
override suspend fun getProfile(symbol) = if (!av.premium) delegate.getProfile(symbol) else av.overview(symbol)?.let { … sector = null … }
override suspend fun getFundamentals(symbol, period) = delegate.getFundamentals(symbol, period)   // FMP by configuration
stockProvider = av?.first ?: fmpRepository; priceHistory = av?.third ?: FmpPriceHistoryProvider(…); closesSplitAdjusted = av == null
```
An AV refusal propagates (test: `…profilesComeFromOverviewOnlyOnAPremiumPlan…` asserts the delegate isn't called).

## 4. Valuation history on as-traded closes — `service/ValuationCalculations.kt`
```kotlin
val factors = if (closesSplitAdjusted) splitFactors(quarters) else quarters.associate { it.periodEnd to 1.0 }
if (spansSplit(date)) return@mapNotNull null                       // window or price may span a detected split
splitAdjusted = closesSplitAdjusted && factors.values.any { it != 1.0 }
```
Default `true` keeps the original method byte-for-byte for FMP. Tests: `RawBasisValuationTest` (3).

## 5. Licence gate at the serving boundary — `marketdata/MarketDataAccessGate.kt`
```kotlin
if (commercial && activeProviders.any { !registry.displayAllowed(it, Dataset.DAILY_PRICES, channel) }) return REFUSED_COMMERCIAL
if (personalAlphaVantage && uid !in personalUids && personalClients.none { matches(it, clientAddress) }) return REFUSED_PERSONAL
→ 502 ApiError("PROVIDER_RESTRICTED", LICENSE_MESSAGE), Cache-Control: no-store, finish()   // before any route or provider call
```
Address = trusted proxy address with `TRUSTED_PROXY_HOPS`, else the socket peer (`request.local.remoteAddress`, unspoofable). Installed
only in REAL and only when active. Tests: `MarketDataAccessGateTest` (5, incl. a real Ktor pipeline).

# Focused diff — Discover REAL backend fix (caching and error mapping)

Commit "Fix Discover Stocks on the LAN REAL backend with distinct plan-restricted, rate-limited and temporary screener errors, a provider cooldown on the universe request and no Retry when the FMP plan excludes the screener on Android and iOS" on `c6baf6a` (pushed, not deployed). Why a focused diff is included (workflow §3.4): it covers provider caching (a cooldown on a
failure outcome) and presenter state (a new field).

## 1. Universe outcome with the existing cooldown (`server/.../screener/ScreenerService.kt`)

```kotlin
private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 4, name = "screener-universe")
private class Outcome(val definition: UniverseDefinition?, val failure: StockProviderException?)

override suspend fun universe(): UniverseDefinition {
    val outcome = cache.getOrLoad("universe", UNIVERSE_TTL, resultTtl = { providerCooldown(it.failure, UNIVERSE_TTL) }) {
        try { Outcome(load(), null) } catch (cause: StockProviderException) { Outcome(null, cause) }
    }
    outcome.failure?.let { throw it }
    return requireNotNull(outcome.definition)
}
```
- **Decision:** this is the same pattern as `SparklineService` and `StockService.getProfile`. The cooldown table is the shared
  `providerCooldown`: 1 h for 402/403, 10 min for 429, 30 s otherwise. No new cache layer and no new TTLs. Single flight is unchanged,
  so concurrent opens share one request.
- **Risk:** a recovered plan shows up after at most 1 h; a transient blip, after at most 30 s. Only `StockProviderException` is cached
  as an outcome. Anything else still propagates uncached, as before. `apiCall` already wraps IO, timeout and JSON failures in it.
- **Tests:** `ScreenerUniverseFailureTest`:
  - `planRestrictionSaysRetryingWontHelpAndIsNotRepeatedForAnHour` (402 and 403);
  - `rateLimitIsReportedAndCoolsDownForTenMinutes`;
  - `anOutageIsTemporaryAndRetriedAfterThirtySeconds`;
  - `anUnreadableProviderAnswerIsTemporary`;
  - `concurrentIdenticalRequestsShareOneProviderRequest`;
  - `discoverJourneysReuseTheUniverseAndPageCaches`.
  - The existing `ScreenerUniverseOptimizationTest` (24 h cache, 3 requests) still passes.

## 2. Failure → API error (`ScreenerService.snapshot`)

```kotlin
private fun universeUnavailable(cause: Exception): ScreenerRequestException {
    val provider = cause as? StockProviderException
    return when {
        provider?.upstreamStatus == 402 || provider?.upstreamStatus == 403 -> {
            log.warn("Screener universe unavailable: provider access denied (status={})", provider.upstreamStatus)
            ScreenerRequestException(503, ScreenerDisplay.CODE_PROVIDER_RESTRICTED, ScreenerDisplay.PROVIDER_RESTRICTED_MESSAGE)
        }
        provider?.failure == StockProviderException.Failure.RATE_LIMITED ->
            ScreenerRequestException(503, ScreenerDisplay.CODE_RATE_LIMITED, ScreenerDisplay.RATE_LIMITED_MESSAGE)
        else -> ScreenerRequestException(503, ScreenerDisplay.CODE_UNAVAILABLE, ScreenerDisplay.UNAVAILABLE_MESSAGE)
    }
}
```
- **Decision:** the status stays 503 for all three, so older apps keep working and show the new message verbatim. Only the code and
  the message differ. A `ProviderGuard` denial has no upstream status, so it stays the temporary `SCREENER_UNAVAILABLE`. That's right:
  it's this server's own budget or circuit, not the plan. The log line carries only the status: no URL, key or symbol.
- **Risk:** 403 from FMP could also mean a bad key. Either way, Retry can't fix it, and an operator sees the WARN line.
- **Tests:**
  - `theRouteAnswersWithTheCodeAndMessage`: 503, JSON, no "Try again shortly", no provider host in the body;
  - `ScreenerRoutesTest.providerFailureIsA503AndPartialFailureKeepsOtherCompanies`, unchanged.

## 3. Presenter state (`core/.../screener/ScreenerPresentation.kt`)

```kotlin
val catalogErrorCode: String? = null,
…
mutable.update { it.copy(catalogLoading = true, catalogError = null, catalogErrorCode = null) }
…
val fallback = if (generateSequence<Throwable>(cause) { it.cause }.any { it is kotlinx.serialization.SerializationException })
    ScreenerDisplay.UNEXPECTED_RESPONSE_MESSAGE else ScreenerDisplay.CONNECTION_MESSAGE
mutable.update { it.copy(catalogLoading = false, catalogError = message(cause, fallback), catalogErrorCode = (cause as? StockStepsApiException)?.error?.code) }
```
- **Decision:** the server message is still shown verbatim (existing rule). The code drives only two display decisions, both in
  `ScreenerDisplay`: `catalogErrorTitle` and `catalogRetryable`. Android and iOS call those helpers, so the rule lives in one place.
- **Risk:**
  - the field is new and optional, so `copy` callers are unaffected;
  - the error and its code reset together at the start of each attempt;
  - the iOS bridge sees the extra property, with no Swift name clash.
- **Tests:** `ScreenerPresenterTest.catalogErrorsSayWhetherRetryingHelps` runs this sequence: restricted, then rate limited, then
  offline, then an unreadable answer, then recovery. It also checks that a loaded catalog isn't requested again and that no presets
  appear on failure.

## 4. UI (display only)

Android `ScreenerScreen.kt`: `actions.onRetryCatalog.takeIf { ScreenerDisplay.catalogRetryable(state.catalogErrorCode) }` and
`title = ScreenerDisplay.catalogErrorTitle(...)`. iOS `DiscoverStocksViews.swift`: the same, with `actionTitle`/`action` set to nil when
the error isn't retryable.

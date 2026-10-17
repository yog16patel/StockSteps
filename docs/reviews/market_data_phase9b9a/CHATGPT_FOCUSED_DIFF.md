# Focused diff — Phase 9B.9A (commit on `30bf1df`)

## 1. Entitlement separate from rate — `AlphaVantageConfig`, `ProviderBudget.alphaVantageLimits`
```kotlin
val entitlement: Entitlement = Entitlement.FREE              // ALPHA_VANTAGE_ENTITLEMENT=premium is the only switch
val premium get() = entitlement == Entitlement.PREMIUM      // was: planRequestsPerMinute != null
FREE:    ProviderLimits(5, 5, conc, dailyLimit = (ALPHA_VANTAGE_DAILY_BUDGET ?: 20).coerceAtMost(25), dailyLimitExact = true)
PREMIUM: ProviderLimits(plan, plan, conc, dailyLimit = ALPHA_VANTAGE_DAILY_BUDGET, dailyLimitExact = true)
local(): dailyLimit = if (l.dailyLimitExact) it else it × margin ÷ instances
```
Tests: `aRateSettingNeverImpliesPremium`, `theFreeDailyBudgetIsExactCappedAndEnforced`.

## 2. No bulk work on FREE — `Application.kt`
```kotlin
val alphaVantageFree get() = alphaVantage?.premium == false && "alphavantage" in marketDataProviders
perDay = if (sources.alphaVantageFree) 0 else …;  fundamentalsPerHour = if (sources.alphaVantageFree) 0 else …
```

## 3. Loopback-only personal clients — `MarketDataAccessGate.matches`, `StartupConfiguration`
```kotlin
allowed.equals("loopback", true) && a in {127.0.0.1, ::1, localhost}     // any other entry matches nothing; startup rejects it
```
Behaviour change vs 9B.7 (LAN IPs were accepted). Tests: `startupRulesForFreeAndPremium`, gate tests.

## 4. Bounded smoke test — `AlphaVantageSmokeTest.kt`, `:server:alphaVantageSmokeTest`
Three opt-ins (key, confirm phrase, terms phrase); ≤ 10 requests counted with an `HttpSend` interceptor; budget checked before each
request; first refusal stops the run; 15 s spacing; report without key/URL/body. Tests: `theSmokeTestIsOptInBoundedAndStopsAtTheFirstRefusal`.

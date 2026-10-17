# ChatGPT review package — Phase 9B.7: Alpha Vantage-first market data

## 1. Phase and Git baseline
Base `main` = `origin/main` = `93fca36` (9B, pushed). Under review: the commit "Add Alpha Vantage-first market data for Phase 9B.7 …" (server + docs only; pushed). No commit, push,
deploy, purchase, subscription change or key use.

## 2. Changes implemented
- Alpha Vantage client (`alphaVantageCall` + `AlphaVantageClient`): HTTPS, key as a server-side parameter only, bounded bodies, 20 s
  timeout, **HTTP-200 refusal classification** (rate limit → 429/breaker, premium → 402, key → 403, `Error Message` → none), ProviderGuard
  `alphavantage` lane (free 5/min · 25/day; plan from `ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE`), ProviderCalls labels `query/<FUNCTION>`,
  single-flight cache, cooldowns, premium functions refused locally without a plan, no retries.
- Parsers for daily (raw, adjusted), quote, search, overview, movers, statements, earnings, dividends, splits.
- Adapters behind the existing interfaces, wired when `MARKET_DATA_PRIMARY_PROVIDER=alpha_vantage` (+ `ALPHA_VANTAGE_ENABLED=true`, key):
  prices (as-traded), quotes (EOD), search (US), movers, statement history, profiles (premium plan). FMP keeps the fundamentals bundle and
  quarterly EPS by configuration; no runtime fallback.
- Valuation history on as-traded closes: no split factors; months that may span a split omitted (default path unchanged).
- Licence registry rows for Alpha Vantage with personal/tester/commercial/caching/AI dimensions; **licence gate at the serving boundary**
  (personal allowlists; commercial fails closed).
- Startup validation for all new settings.

## 3. Affected files
server main: `marketdata/alphavantage/{AlphaVantageConfig,AlphaVantageParsers,AlphaVantageClient,AlphaVantageProviders}.kt`,
`marketdata/MarketDataAccessGate.kt` (new); `httpclient/NetworkUtils.kt`, `marketdata/DataRights.kt`, `service/ProviderBudget.kt`,
`service/ValuationCalculations.kt`, `service/ValuationService.kt`, `appconfig/StartupConfiguration.kt`, `Application.kt`.
server tests: `marketdata/alphavantage/*` (6 files), `marketdata/MarketDataAccessGateTest.kt`, `service/AlphaVantageBenchmarkTest.kt`
(new); `marketdata/SecEdgarClientTest.kt` (lane set). docs: this directory; `PROJECT_HANDOFF.md`, `project-status.md`. Apps: none.

## 4–5. Decisions and results
Dataset-level sources chosen by configuration; refusals never failed over; raw basis for ledgers; gate before any route. Tests: all
suites PASS (server 686/0, +34) — `CHATGPT_TEST_RESULTS.md`.

## 6–7. Android / iOS
No app change; builds and shared suites PASS; devices NOT RUN — `ANDROID_IOS_REGRESSION.md`.

## 8–9. Known issues / unverified
`KNOWN_LIMITATIONS.md`. REAL Alpha Vantage, account entitlement and live field names (except the daily series) **UNVERIFIED** — no key.

## 10. Deviations from the brief
- Phase 7 metric comparison against real companies BLOCKED (no AV key, no SEC User-Agent, no REAL FMP); fixture parity only.
- SEC fallback not wired (needs approval + comparison).
- Splits/dividends parsed but not applied (accounting policy decision).
- Fundamentals bundle, EPS, news and earnings calendar stay on FMP/Finnhub.

## 11. Owner decisions — `OWNER_DECISIONS.md` (D-9B7-1…10).

## 12. Status classes
| Class | Items |
|---|---|
| Implemented and wired (when enabled) | daily prices, quotes, search, movers, statement history, premium profiles, raw-basis valuation rule, licence gate, lane/budget, startup checks |
| Implemented but unused | `dailyAdjusted`, `earnings`, `dividends`, `splits` (client + parsers), `DailyPriceSource` for AV |
| Fixture-tested | everything above |
| REAL-provider tested | none (no key) |
| Licensing blocked | every Alpha Vantage dataset for anyone but the key holder; all market data in commercial mode |
| Deferred | fundamentals bundle on AV, index levels, news/sentiment, earnings calendar, macro, ETF profile, SEC wiring, split policy, chart basis note |

Links: `ALPHA_VANTAGE_ENDPOINT_MATRIX.md`, `ALPHA_VANTAGE_ADAPTER_REVIEW.md`, `ALPHA_VANTAGE_FEATURE_COVERAGE.md`,
`ALPHA_VANTAGE_LICENSE_AND_ENTITLEMENTS.md`, `PROVIDER_FALLBACK_MATRIX.md`, `PRICE_AND_SPLIT_CORRECTNESS.md`, `FINANCIAL_METRICS_COMPARISON.md`,
`CACHE_AND_QUOTA_RESULTS.md`, `COST_SIMULATION.md`, `ANDROID_IOS_REGRESSION.md`, `CHATGPT_FOCUSED_DIFF.md`.

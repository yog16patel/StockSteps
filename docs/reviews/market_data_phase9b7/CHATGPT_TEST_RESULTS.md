# Test results — Phase 9B.7 (2026-10-17, sequential, `--rerun`, working tree on `93fca36`)

Logs and benchmark in git-ignored `captures/`. Counts from the JUnit XML of this run.

| Suite / build | Status | Result | Notes |
|---|---|---|---|
| `:core:jvmTest` | PASS | 625 / 0 | no core change |
| `:core:iosSimulatorArm64Test` | PASS | 623 / 0 | |
| `:server:test` | PASS | 686 / 0, 10 skipped | 9B was 652; **+34 new**; the 10 skips are the Firebase-emulator suites |
| `:app:shared:testAndroidHostTest` | PASS | 289 / 0 | no shared change |
| `:app:shared:iosSimulatorArm64Test` | PASS | 286 / 0 | |
| `:app:androidApp:assembleDebug` | PASS | UP-TO-DATE | no Android input changed |
| `xcodebuild` (iOS Simulator, unsigned) | PASS | BUILD SUCCEEDED | no iOS change |
| Firebase emulator suites | NOT RUN | — | no user-owned flow changed |
| Devices | NOT RUN | — | no UI/contract change; AV can't run without a key |
| Bounded REAL Alpha Vantage smoke test | BLOCKED | — | no key on this machine (D-9B7-1) |
| Live SEC | BLOCKED | — | no approved `SEC_USER_AGENT` |

## New tests (34)
| Class | Tests | Covers |
|---|---:|---|
| `alphavantage.AlphaVantageParsersTest` | 5 | HTTP-200 refusal classes (rate limit, Note, premium, demo/invalid key, Error Message, unknown notice, CSV, data with metadata), daily raw/adjusted, quote, search, overview, movers, statements ("None"/"-" → null), earnings, dividends, splits, malformed |
| `alphavantage.AlphaVantageClientTest` | 8 | key only as a parameter (never in logs/labels/config text), cooldowns (rate 10 min, premium 1 h, Error Message cached), EOD quote lives over the weekend, premium gating and index refusal with 0 requests, compact vs full, single flight (50 → 1), cache expiry, malformed/unknown answers, free daily allowance (20/instance) and rate (4/min), in-body rate limit opens the breaker |
| `alphavantage.AlphaVantageProvidersTest` | 8 | raw basis + provenance, no intraday, EOD quote timestamped at the close, profile by plan (no FMP fallback on refusal, sector null), US-only search, movers, fundamentals delegated, statement mapping and nulls, quarter labels incl. 52/53-week years, degraded statements |
| `alphavantage.RawBasisValuationTest` | 3 | raw closes: no split factors, spanning months omitted; adjusted method unchanged; equal without splits |
| `alphavantage.AlphaVantageConfigurationTest` | 4 | off unless enabled with a key; startup validation (no values echoed; personal mode refused on Cloud Run); lane verification; MOCK never uses AV or the gate |
| `MarketDataAccessGateTest` | 5 | route classification and exemptions, personal allowlists (uid/address/loopback; empty = nobody), commercial fail-closed and allow-after-agreement, real Ktor pipeline (502, no-store, route not reached; non-market route served) |
| `service.AlphaVantageBenchmarkTest` | 1 | `CACHE_AND_QUOTA_RESULTS.md` scenarios |

Development fixes during the phase (no product defect left): fixture date selection, `runBlocking<Unit>` return types, test-app content
negotiation, the free rate limiting before the daily cap (correct behaviour; test adjusted), the conservative split window, a missing
fixture quarter.

## Actual provider requests in this phase
Alpha Vantage documentation page and premium page (2 page loads) and the documentation's sample endpoints with the public `demo` key
(15 requests, 2 answered with data, 13 with the demo notice; no retry). No FMP, Finnhub, SEC, Gemini or Firebase request; no key used.

# ChatGPT review package — Phase 9B.8: Alpha Vantage fundamentals and financial metrics

## 1. Baseline
`main` = `origin/main` = `a3aa026` (9B.7, committed and pushed — the brief's "uncommitted 9B.7" is out of date). Under review:
the commit "Compute the fundamentals bundle from Alpha Vantage for Phase 9B.8 …" (server + docs; pushed). No commit, push, deploy or provider request.

## 2. Changes
- `AlphaVantageFundamentalsProvider` + `AlphaVantageFundamentalsMapper`: the existing `CompanyFundamentals` bundle (same keys) from Alpha
  Vantage statements, dividends and splits, every ratio via `FinancialMetricsEngine`; unverifiable inputs (EPS, shares, estimates)
  unavailable with reasons; quarterly figures only when verified standalone; bundle cache 15 min; statements shared with Comparison.
- Engine: reported total debt in D/E; trailing dividend per share and yield (coverage, split-free window, usable price).
- Wiring: `FUNDAMENTALS_PRIMARY_PROVIDER=alpha_vantage` (default `fmp`) over FMP or Alpha Vantage prices; one Alpha Vantage client.
- Gate: personal mode whenever Alpha Vantage serves any dataset; Gemini gets no market data while Alpha Vantage's AI right is unverified.
- `quoteDelay`: close-stamped quotes are END_OF_DAY.

## 3. Files
server main: `marketdata/alphavantage/AlphaVantageFundamentals.kt` (new); `marketdata/{Fundamentals,Prices,MarketDataAccessGate}.kt`,
`marketdata/metrics/FinancialMetricsEngine.kt`, `Application.kt`, `appconfig/StartupConfiguration.kt`. Tests: 2 new files, 5 extended.
Docs: this directory, `PROJECT_HANDOFF.md`, `project-status.md`. Apps: none.

## 4. Decisions
Engine-only formulas; contract unchanged; dataset-level configuration, no runtime fallback; safe-by-omission for unverified definitions.

## 5–7. Results
All suites PASS (server 697/0) — `CHATGPT_TEST_RESULTS.md`; no app change — `ANDROID_IOS_CONTRACT_REVIEW.md`.

## 8–9. Known issues / unverified — `KNOWN_LIMITATIONS.md`
REAL Alpha Vantage and SEC comparisons BLOCKED (credentials); EPS/P/E/price multiples unavailable from Alpha Vantage.

## 10. Deviations
Stage 3 (SEC real comparison) and Stage 7 parity on real companies blocked; AV EARNINGS not adopted (definition unverified); FMP quarterly
EPS kept for P/E history; legacy snapshot/indices and Finnhub out of scope.

## 11. Owner decisions — `OWNER_DECISIONS.md` (D-9B8-1…8).

## 12. Status classes
| Class | Items |
|---|---|
| Implemented and wired (when `FUNDAMENTALS_PRIMARY_PROVIDER=alpha_vantage`) | AV fundamentals bundle for Company Details, Comparison, Discover, AI input; engine metrics listed in `FINANCIAL_METRICS_PARITY.md`; gate and AI rule |
| Implemented but unused | AV `EARNINGS` parser (definition unverified); SEC adapter |
| Fixture-tested | all of the above |
| REAL-tested | none |
| Blocked by credentials | AV smoke test, SEC comparison, real parity |
| Blocked by licensing | showing any AV-derived value to anyone but the allowlisted owner; Gemini on AV data |
| Deferred | EPS/P/E/multiples, shares, SEC wiring, `source` field, Finnhub/legacy migration |

Upload: this file, `CHATGPT_FOCUSED_DIFF.md`, `CHATGPT_TEST_RESULTS.md`, `FUNDAMENTALS_FIELD_MAPPING.md`, `FINANCIAL_METRICS_PARITY.md`,
`OWNER_DECISIONS.md`.

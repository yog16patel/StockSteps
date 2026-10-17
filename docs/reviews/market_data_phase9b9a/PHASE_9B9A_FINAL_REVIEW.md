# Phase 9B.9A — final review (2026-10-17)

## Git
`main` = `origin/main` = `e163b38` before this review. **D-9B9A-6 was already committed and pushed** (`e163b38`), not uncommitted as the
brief assumed. This review's fixes (F1–F3), tests and docs are committed and pushed on the owner's request as "Finalize Phase 9B.9A with per-field Alpha Vantage quarterly trust for fundamentals, Comparison and interest coverage, deterministic financial scenario tests, and the Phase 9B.9B SEC EDGAR implementation plan".

## Task 1 — D-9B9A-6 audit
| Check | Result |
|---|---|
| Recent-three-year verification never accepts inconsistent quarters | ✓ a failing recent year (or YTD) refuses all quarters; a failing older year's own quarters stay untrusted. Residual risk: the in-progress fiscal year's quarters can't be reconciled until it closes; they're trusted only when the recent 3 years verify. |
| Incomplete fiscal years | ✓ excluded from the recent window and never trusted (e.g. AAPL FY2006 with 2 quarters) |
| Same rules in Fundamentals and Comparison | **F1/F2 found and fixed:** trust was derived from one field (revenue / operating cash flow) and applied to every field of the statement. Now **per field** in both `AlphaVantageFundamentalsMapper.history` and `AlphaVantageStatements.merge`. |
| Quarterly interest coverage | **F3 found and fixed:** it read raw quarterly EBIT and interest expense; now each must verify on its own |
| TTM needs four consecutive trusted standalone quarters | ✓ engine `ttm`: four quarters of 84–98 days, gaps of 1–7 days, one currency, every value present; untrusted quarters carry no values, so no TTM; FY used when it's the newest period |
| Null never becomes zero | ✓ `"None"`/`"-"` → null; every derived value requires all inputs (net debt, FCF, payout, coverage, margins); tests for missing interest expense, missing cash flow, missing balance sheet |
| Portfolio / Practice / other screens | ✓ unaffected: the quarter-trust code is used only inside `marketdata/alphavantage`; Portfolio and Practice use daily closes; FMP remains the default for every dataset; no UI change |

## Task 2 — scenario tests (`FinancialScenariosTest`, 6, deterministic)
June fiscal year (labels, reconciliation, growth 25 %, margins, TTM revenue 5,100 / earnings 1,020 / operating cash flow 5,050);
loss-maker (negative margin kept, growth from a loss refused, P/E unavailable with an EPS note, negative TTM earnings); incomplete
quarterly history and an internal gap (no TTM, annual metrics still work); inconsistent recent year (quarters and quarter-based TTM
refused, annual figures kept; an old inconsistent year leaves recent quarters usable); missing interest expense / cash flow (unavailable,
never 0); per-field trust (revenue standalone, net income YTD → only revenue quarters used, also in Comparison).

## Files
`server/.../marketdata/alphavantage/AlphaVantageFundamentals.kt` (`trustedByField`, per-field history, F3),
`.../AlphaVantageProviders.kt` (per-field merge), `server/src/test/.../alphavantage/FinancialScenariosTest.kt` (new); docs:
`PHASE_9B9A_FINAL_REVIEW.md`, `PHASE_9B9A_TEST_RESULTS.md`, `captures/summary-final.txt` (ignored), `../market_data_phase9b9b/` (plan,
decisions).

## Remaining risks
- In-progress fiscal-year quarters are trusted on the strength of the recent 3 years (can't be reconciled earlier).
- Live validation covers AAPL only (10 of 25 free requests on 2026-10-17); other filers (banks, non-December, loss-makers) are
  fixture-only.
- EPS, shares and price multiples remain unavailable from Alpha Vantage; licensing is personal-only.
- Budgets and caches are per instance.
- Known flaky core-iOS `ApiLoggingTest` race (didn't occur in this run).

No live provider request was made in this review.

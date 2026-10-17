# Price basis and split correctness — Phase 9B.7

| Question | Finding | Status |
|---|---|---|
| AV daily basis | `TIME_SERIES_DAILY` is documented as **raw (as-traded)**; `TIME_SERIES_DAILY_ADJUSTED` (premium) adds adjusted close + split coefficient + dividend amount (sample response checked) | VERIFIED (docs + sample) |
| Latest close | identical in raw and adjusted series (adjustments only change earlier rows) | correct for Portfolio/Practice valuation and execution |
| Charts | show as-traded history: a split appears as a cliff. The chart contract has no basis field; showing "As traded" would be a UI change | known limitation (D-9B7-4) |
| Portfolio history | ledger quantities are as traded; raw closes × as-traded quantities are right **until a split after the trade**; afterwards value drops by the split ratio unless quantities are adjusted | unchanged risk (was unknown basis with FMP); split events not applied |
| Practice | last-official-EOD execution policy unchanged; latest close is basis-independent; positions held across a split are mis-valued until split processing exists | unchanged (D-9B7-5) |
| Valuation P/E history | `ValuationCalculations` assumed split-adjusted closes. With raw closes it now applies **no split factors** and omits any month whose four quarters or price date may span a split detected from share counts (conservative: the split is dated only to a quarter) | **fixed in 9B.7** (`RawBasisValuationTest`) |
| Metrics engine | P/E/yield require a verified split history or no split-like share jump (9B) | unchanged |
| Missing or stale prices | still flagged by `EodPrice`/`EodChange` (valuation ≤ 5 sessions, execution ≤ 1); free tier history = last 100 sessions only | unchanged |
| "SPLITS exists, so it's safe" | **No**: SPLITS gives dates and ratios, but applying them to ledgers changes quantities, cost per share and history → an accounting policy change, not enabled | decision D-9B7-5 |

Proposed split policy (not implemented; isolated tests exist only for the valuation rule): keep raw closes; on a split's effective date,
multiply as-traded quantities held before it by the ratio and divide per-share cost by it, recording a system "split adjustment" entry
(visible, reversible) instead of rewriting trades; Practice the same, with the cash unchanged. Needs owner approval and its own phase.

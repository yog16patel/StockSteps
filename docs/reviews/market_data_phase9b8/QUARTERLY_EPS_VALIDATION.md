# Quarterly EPS validation — Phase 9B.8

Decision: **FMP quarterly EPS stays** the `QuarterlyEarningsSource` for P/E history; Alpha Vantage `EARNINGS` is **not** used.

| Requirement | FMP (current) | Alpha Vantage EARNINGS | SEC companyfacts |
|---|---|---|---|
| Reported (GAAP) vs adjusted | income statement `epsDiluted` (reported) | `reportedEPS` — basis not documented (often consensus-comparable, i.e. adjusted) | GAAP by taxonomy |
| Diluted vs basic | diluted | not stated | both tags |
| Fiscal quarter end | `date` | `fiscalDateEnding` | start/end |
| Filing (availability) date | `acceptedDate` | `reportedDate` (announcement) | `filed` |
| Restatements | provider latest | unknown | latest vs original |
| Share counts for split detection | yes | no | diluted weighted shares |
| Missing quarters / negative EPS | handled by `ValuationCalculations` | values present as `"None"` | handled |

Because the AV definition can't be verified, replacing FMP would silently change P/E history. SEC diluted EPS is the candidate
replacement after D-9B8-2. Tests added: raw-basis split handling (9B.7) and a September fiscal year crossing calendar and fiscal year ends
(`RawBasisValuationTest.aSeptemberFiscalYear…`). The 9B.7 raw-price rule is kept; no Portfolio/Practice change.

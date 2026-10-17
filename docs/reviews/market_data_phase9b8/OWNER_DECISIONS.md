# Owner decisions — Phase 9B.8

| # | Decision | Recommendation |
|---|---|---|
| D-9B8-1 | Set the Alpha Vantage key server-side and approve a bounded REAL smoke test (≤ 20 requests: 4 companies × 5 fundamentals datasets) | yes, LAN dev only |
| D-9B8-2 | Approve `SEC_USER_AGENT` and the six-company SEC/AV/FMP comparison (≤ 20 SEC requests) | yes — unlocks GAAP diluted EPS and shares |
| D-9B8-3 | Enable `FUNDAMENTALS_PRIMARY_PROVIDER=alpha_vantage` on the LAN dev server after D-9B8-1 | yes, owner-only (personal allowlist) |
| D-9B8-4 | Accept the convention changes vs FMP (period margins, FY-average ROE/ROA, reported total debt) | yes, with the parity report first |
| D-9B8-5 | Add an optional `source` field to `CompanyFundamentals` for an in-app "Source" line | later, with a UI phase |
| D-9B8-6 | P/E source: SEC diluted EPS (after D-9B8-2) vs asking Alpha Vantage to document `reportedEPS` / OVERVIEW EPS | SEC |
| D-9B8-7 | `SCREENER_FUNDAMENTALS_PER_HOUR=0` while Alpha Vantage is on a personal plan | yes |
| D-9B8-8 | Approve the 9B.8 code for commit after the ChatGPT review | review first |

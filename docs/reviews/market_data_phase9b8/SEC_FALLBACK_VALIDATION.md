# SEC fallback validation — Phase 9B.8

**BLOCKED by credentials:** no approved `SEC_USER_AGENT` (identifying contact address) exists, so no request to sec.gov was made and the
bounded comparison (AAPL, MSFT, JPM, AMZN, a loss-maker such as RIVN, a filer with nonstandard concepts such as a REIT or an insurer) could
not run. The 9B adapter stays fixture-tested and **off for REAL**.

What is ready (9B): ticker ↔ CIK, companyfacts parsing, fiscal periods by dates, restatement policy (latest vs original, amendments),
YTD never quarters, Q4 derivation for flows only, share-class ambiguity, mapping to `FinancialPeriodStatement` and to the metrics engine.

Comparison protocol once approved (≤ 20 requests: 1 ticker file + 6 companyfacts + 6 submissions, paced 5/s):
1. For each company, latest 3 fiscal years and 8 quarters: revenue, operating income, net income, total assets, liabilities, equity,
   operating cash flow, capex, diluted EPS; FY/quarter mapping; filing and period-end dates; restatements/amendments.
2. Side by side with Alpha Vantage (`AlphaVantageStatements`) and FMP; every difference explained (tag choice, YTD vs standalone, debt
   definition, segment vs consolidated, restated vs original); never silently pick the older or less comparable figure.
3. Priority after validation: Alpha Vantage when entitled, licensed and valid → SEC for eligible US reported fundamentals (EPS, shares
   from the cover page) → FMP only as a configured dev source. SEC is never used *because* Alpha Vantage is rate-limited.

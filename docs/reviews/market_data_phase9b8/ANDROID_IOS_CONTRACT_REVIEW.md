# Android / iOS contract review — Phase 9B.8

No app file changed. `CompanyFundamentals` keys, types and nullability are identical to the FMP bundle (test
`theContractKeysMatchTheFmpBundle` compares every group's key set and serializes with the existing model). With Alpha Vantage fundamentals:
- EPS, P/E, price multiples and valuation-history comparisons arrive as `availability = MISSING` with a note (the apps already render
  missing metrics as unavailable);
- margins/ROE/ROA bases say "annual" or the quarter instead of "TTM";
- dividend yield basis "TTM", dated at the price;
- `datasets` keys are `income`, `balance`, `cashFlow`, `dividends`, `splits`.
Default (`fmp`) is byte-for-byte the previous behaviour. Suites and builds: `CHATGPT_TEST_RESULTS.md`. Devices NOT RUN.

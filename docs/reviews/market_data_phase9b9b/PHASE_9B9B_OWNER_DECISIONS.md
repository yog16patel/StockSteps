# Phase 9B.9B owner decisions (before implementation)

| # | Decision | Recommendation |
|---|---|---|
| D-9B9B-1 | Approve `SEC_USER_AGENT="StockSteps <your contact e-mail>"` and one bounded live validation run (≤ 20 SEC requests, 6 companies) | yes — SEC data is public; the User-Agent is required by SEC fair access |
| D-9B9B-2 | Reference companies: AAPL, MSFT, JPM, AMZN, RIVN + one REIT or insurer (e.g. O or MET) | yes |
| D-9B9B-3 | Price source for P/E, P/S, P/B, market cap with SEC fundamentals: FMP (dev, DDLA pending) or Alpha Vantage (personal testing only) | FMP for dev; neither cleared for public display |
| D-9B9B-4 | Add optional `CompanyFundamentals.source` and filing reference (additive contract change) | yes, with a later UI phase for any visible "Source" line |
| D-9B9B-5 | Split policy for SEC per-share history: normalise to the latest basis when the split date is known, else omit | yes |
| D-9B9B-6 | Opt-in flag `FUNDAMENTALS_PRIMARY_PROVIDER=sec` (FMP default unchanged) | yes |
| D-9B9B-7 | Approve starting Phase 9B.9B implementation | after review of this plan |

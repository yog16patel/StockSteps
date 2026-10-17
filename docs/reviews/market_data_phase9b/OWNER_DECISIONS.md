# Owner decisions — Phase 9B (recommendation first; nothing below is implemented unless marked "in 9B")

| # | Decision | Recommendation | Blocks |
|---|---|---|---|
| D-9B-1 | Ask FMP for a Data Display and Licensing quote (Phase 6 Q1–Q4) and one competing written quote (Q8/Q10: an EOD + fundamentals vendor with an external-display tier, e.g. Alpha Vantage premium) | **Yes, both** — mobile **and** web, shared caching, derived values/AI, logos, attribution | commercial launch |
| D-9B-2 | Enable SEC EDGAR for reported statements (`FUNDAMENTALS_SOURCE=sec`, `SEC_USER_AGENT="StockSteps <owner contact e-mail>"`) after a side-by-side check against FMP on the curated list | **Yes, next phase** (free, cleared for display; removes ≈ 11 FMP requests per company/day) | — |
| D-9B-3 | Discover daily cap `SCREENER_ENRICHMENT_PER_DAY` (in 9B: default 100) | keep 100 on the 250/day key; set ≈ 320 on a paid tier for a full daily refresh | LAN deploy |
| D-9B-4 | Price vendor for launch | one licensed EOD vendor with a stated adjustment basis; no real-time; delayed quotes only if licensed | launch |
| D-9B-5 | Corporate actions (splits, symbol changes, delistings) source and the Portfolio/Practice price basis | licensed split feed + unadjusted closes; quantities adjusted at split events (needs its own approved phase) | launch (8D backlog) |
| D-9B-6 | Finnhub news/earnings: commercial plan or replacement; SEC filings list as an official "Filings" feed | get Q5 answer; prototype SEC filings feed | launch |
| D-9B-7 | Enforce `STOCKSTEPS_LICENSE_MODE=commercial` per route, and the user-facing copy for a section blocked by licensing | design in the next phase; reuse existing "unavailable" shapes (no new error contract) | launch |
| D-9B-8 | Index levels (S&P 500, Nasdaq, Dow): index-owner licence needed? (Q9) | ask; until then show fund closes labelled as funds | launch |
| D-9B-9 | Macro context (rates, CPI) in Learn | only from Treasury/BLS/BEA originals with citation; never FRED copyrighted series | — |
| D-9B-10 | Multi-instance budgets (shared counter in Firestore or Memorystore) | not before production scaling is decided; keep `CLOUD_RUN_MAX_INSTANCES=1` until then | production |
| D-9B-11 | `PROVIDER_FMP_PER_MINUTE=250` / no daily limit on LAN REAL (Phase 6/7A open) | set `PROVIDER_FMP_DAILY_LIMIT` to the verified daily allowance | LAN REAL |
| D-9B-12 | Counsel confirmation of SEC/BoC attribution wording (Q12, Q11) | "Source: SEC EDGAR filings", "Exchange rate: Bank of Canada (indicative)" | launch |
| D-9B-13 | Approve the 9B server changes for commit (marketdata package, SEC lane, `apiCallText`, Discover cap, startup checks, license report) | review the package first | — |

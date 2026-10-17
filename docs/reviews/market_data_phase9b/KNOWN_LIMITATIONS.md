# Known limitations — Phase 9B

1. **Licensing is unresolved for every price, news and earnings dataset.** FMP and Finnhub published terms restrict public display; no
   agreement exists. Commercial launch stays BLOCKED (fail closed). Only SEC, BoC and StockSteps' own data are cleared.
2. **SEC adapter isn't wired** to Company Details (D-9B-2) and was **not run against sec.gov** (no `SEC_USER_AGENT` contact approved; tests
   use SEC-shaped fixtures with invented values). Real-filer variety (banks, insurers, REITs, extension tags, IFRS foreign issuers) is
   untested; foreign private issuers are unsupported.
3. **Metrics engine isn't wired**; FMP-defined ratios still drive the screens. Definitions differ (TTM method, debt components) and need a
   side-by-side review before switching.
4. **Split history:** no licensed corporate-actions source in REAL; the engine only detects split-like share jumps; Portfolio/Practice
   history basis unverified (`PORTFOLIO_PRACTICE_DATA_CORRECTNESS.md`).
5. **Budgets are per instance** (ProviderGuard, Discover daily cap, SEC lane, caches). Multi-instance totals = N × local values; no shared
   counter.
6. **Discover at 100/day** can't refresh all 155 closes every trading day (100–155 latest); profiles refresh mostly at weekends.
7. **Licence gate is report-only** (startup log); routes don't enforce `STOCKSTEPS_LICENSE_MODE=commercial` yet (D-9B-7).
8. **Cost model is modeled**, not measured on REAL (FMP key at its limit; no REAL calls made in 9B); prices UNVERIFIED.
9. Provider terms were read on 2026-10-17 from public pages; FMP pages refused direct fetches (search-indexed content used). Terms can change;
   only signed agreements settle rights.
10. Fiscal-year labels for the oldest comparatives are inferred; TTM per-share Q4 derivation is approximate (flagged).
11. Pre-existing, untouched: Watchlist/Alerts still quote-based; Finnhub history cached under two keys; one BoC cache per consumer; the
    Portfolio history layer can lag a new close by ≤ 6 h; the flaky core-iOS `ApiLoggingTest` race (8E note).
12. Pre-existing (7E), kept unchanged: when a due close refresh fails, the Discover row's previous close is cleared (shown as unavailable)
    rather than kept with its older date. A fix (keep the older dated close) is a small follow-up that changes what users see → approval.

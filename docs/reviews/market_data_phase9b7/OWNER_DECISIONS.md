# Owner decisions — Phase 9B.7 (recommendation first; none enabled without approval)

| # | Decision | Recommendation |
|---|---|---|
| D-9B7-1 | Set `ALPHA_VANTAGE_API_KEY` (server env only, never in chat/Git), `ALPHA_VANTAGE_ENABLED=true`, `ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE=<plan>`, and approve one bounded REAL smoke test (≤ 15 requests: AAPL daily, quote, search, overview, 3 statements, earnings, dividends, splits, movers) | yes, on the LAN dev server only, then confirm field names |
| D-9B7-2 | Personal-mode allowlist: `ALPHA_VANTAGE_PERSONAL_UIDS=<your uid>` and/or `ALPHA_VANTAGE_PERSONAL_CLIENTS=loopback,<your device IPs>`; keep the dev server unreachable from other people | yes; no testers until D-9B7-7 |
| D-9B7-3 | Approve `SEC_USER_AGENT="StockSteps <contact e-mail>"` and a side-by-side SEC vs AV vs FMP statement comparison for the six reference companies | yes, next phase |
| D-9B7-4 | Chart basis: keep as-traded (needs an "As traded" note — UI change) or use `TIME_SERIES_DAILY_ADJUSTED` for charts only (premium; separate cache) | adjusted for charts only, raw for ledgers, after D-9B7-5 |
| D-9B7-5 | Split policy for Portfolio/Practice (system split-adjustment entries on the effective date; quantities × ratio, per-share cost ÷ ratio, cash unchanged) | approve as its own phase with isolated tests first |
| D-9B7-6 | Replace FMP for the fundamentals bundle (AV OVERVIEW/statements + metrics engine) and Finnhub for news/earnings (AV NEWS_SENTIMENT premium / EARNINGS_CALENDAR) | only after the comparison and commercial terms |
| D-9B7-7 | Ask Alpha Vantage sales (Q13): commercial mobile + web display, shared caching, derived values, AI processing, attribution, price at 1k/10k MAU | yes, before any tester sees the data |
| D-9B7-8 | Plan size: 75/min for development; 150/min minimum for 10k DAU on one instance | 75/min now |
| D-9B7-9 | Counsel: is StockSteps operated by a company (ToS §2 (ii) makes even the owner's use commercial)? | confirm |
| D-9B7-10 | Approve the 9B.7 code for commit (no push) | review the package first |

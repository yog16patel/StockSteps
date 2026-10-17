# Draft — questions for Alpha Vantage sales (not sent)

Status 2026-10-17: the owner uses a free personal key for owner-only local testing. These questions are for anything beyond that (company
development, testers, beta, launch), which the Terms of Service §2 treat as commercial use needing a written agreement.
Contact listed in the terms for commercial use: premium@alphavantage.co (send from the owner's own account; review the wording first).

---

**Subject:** Commercial licensing for a beginner investing-education app (mobile + web)

Hello,

We're building StockSteps, a free beginner investing-education app (Android and iOS, a website later) operated by a company. We'd like to
use Alpha Vantage as our primary market-data provider and need a written commercial agreement before any development use. Could you help
with the following?

**Development and testing**
1. Can our team use Alpha Vantage for development and internal testing (a private server, a handful of employees and testers) before
   launch? What plan and agreement does that need, and what does it cost?

**Datasets (US-listed stocks and ETFs, end-of-day only — no realtime, no streaming)**
2. Daily OHLCV history (full history), latest end-of-day quote, symbol search, listing status.
3. Annual and quarterly income statement, balance sheet and cash flow; company overview; earnings history; dividends; splits.
4. Top gainers/losers (end of day). Optionally index levels (e.g. S&P 500, Nasdaq Composite, Dow Jones) — does showing index levels
   need a separate licence from the index owners?
5. For EARNINGS `reportedEPS` and OVERVIEW EPS fields: are they GAAP or adjusted, diluted or basic? Are quarterly cash-flow values
   standalone quarters or year to date?

**Display and channels**
6. Displaying these datasets to the public in our mobile apps, and separately on a website: which plan or licence covers each channel?
7. Required attribution wording and placement; may we show company logos?

**Storage, caching and derived data**
8. May our server cache responses and serve them to many users? For how long? May we persist them in a database?
9. May we show values we compute from your data (growth rates, margins, P/E, dividend yield, account values in a practice portfolio)?
10. May the data be sent to an AI model (Google Gemini) to generate plain-language explanations shown to users?
11. Is redistribution of any kind (exports, downloads, an API) excluded? We don't plan to offer it.

**Volume and pricing**
12. Expected volume with shared caching: about 2,000 requests a day at 100 daily users, 8,000 at 1,000, 35,000 at 10,000; peaks around
    60 requests a minute. What would the agreement cost at 1,000 and 10,000 monthly active users, for mobile only and for mobile + web?
13. Are there exchange fees or per-user reporting for end-of-day prices shown commercially?

Thank you — we're happy to share more detail about the app.

---
Internal notes (not for the email): volume figures from `../market_data_phase9b8/COST_SIMULATION_100_1000_10000.md`; open licensing
dimensions in `../market_data_phase9b7/ALPHA_VANTAGE_LICENSE_AND_ENTITLEMENTS.md`. Don't include keys, account ids or internal URLs.

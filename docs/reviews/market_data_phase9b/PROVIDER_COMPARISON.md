# Provider comparison — Phase 9B (checked 2026-10-17)

Evidence rule: only official pages read this session are cited as **VERIFIED**; anything else is **UNVERIFIED** with the exact question to
ask. No prices are claimed. Technical access ≠ rights. Sources at the end. FMP's own pages refuse automated fetches (HTTP 403), so FMP rows
rest on FMP's published pages as indexed by search plus the Phase 6 reading; they need written confirmation anyway.

| Provider | What StockSteps would use | Auth / quota (published) | Display, caching, redistribution, derived data | Delay / history | Reliability notes | Fit |
|---|---|---|---|---|---|---|
| **SEC EDGAR** (data.sec.gov, sec.gov files) | `company_tickers_exchange.json` (ticker ↔ CIK ↔ exchange), `submissions/CIK##########.json` (identity, fiscal year end, filings with accession, form, filing and report dates), `api/xbrl/companyfacts/CIK##########.json` (all XBRL facts: tag, unit, val, start, end, fy, fp, form, filed, accn, frame); `companyconcept`, `frames`; nightly bulk ZIPs | **No key**. Fair access: **max 10 requests/second**, declare a User-Agent with company name and contact e-mail; no CORS (server-side only) — VERIFIED | sec.gov dissemination policy: information on the site is **public information and may be copied or redistributed** without permission; cite the SEC as source; don't use the SEC seal/logos; "EDGAR"/"SEC" names can't imply endorsement — VERIFIED. No caching limits stated | Submissions update < 1 s, XBRL < 1 min after dissemination; history back to 2009 XBRL; no prices | SEC: no support for scripted access; ticker files "not guaranteed accurate or complete"; taxonomies vary by filer; foreign private issuers (20-F/40-F, IFRS) differ | **Primary for US fundamentals** (9B adapter). Not a price source |
| **FMP** (current) | profile, search, movers, quote, EOD light, 5-min bars, statements, ratios, estimates, dividends, screener | key; plan tiers by calls/min, free Basic 250/day (Phase 6, UNVERIFIED for this account); 402 by symbol/endpoint, 429 "Limit Reach" observed | Published: individual plans are personal/non-commercial; **public display requires a Data Display and Licensing Agreement and prior written approval**; server-side calls don't avoid it; free access doesn't change it — UNVERIFIED for this account, VERIFIED_RESTRICTED as published | EOD light basis UNVERIFIED (Q6) | 402/429 observed on LAN REAL | keep for dev; commercial use only with a DDLA (Q1–Q4) |
| **Finnhub** (current) | market/company news, earnings calendar, `stock/profile2`, quote fallback | key; 30 calls/s cap on top of plan limits (ToS); free 60/min (Phase 6, UNVERIFIED) | ToS: personal plans can't be used by a business even internally without written approval; **no redistribution of data or derived results without written approval**; data must be deleted when the subscription ends — VERIFIED_RESTRICTED | news latency minutes | worked on LAN REAL (Phase 6) | keep for dev; commercial plan or replace (Q5) |
| **Alpha Vantage** (free/dev) | `TIME_SERIES_DAILY` (raw as-traded OHLCV), `GLOBAL_QUOTE`, fundamentals (OVERVIEW, statements), `LISTING_STATUS`, splits/dividends | free key: **25 requests/day**; unlimited for verified open-source/educational projects; premium for volume — VERIFIED | ToS: licence is **personal, non-commercial** unless agreed in writing; commercial use includes making information available to others directly or indirectly — VERIFIED_RESTRICTED for StockSteps without a written agreement | `TIME_SERIES_DAILY` free = **compact (latest 100 points)**; `full` (25+ years) premium; `DAILY_ADJUSTED` premium; realtime/15-min delayed US quotes premium and exchange-regulated — VERIFIED | Nasdaq-licensed vendor (their claim) | **not usable** for the public app on the free key; candidate for a written commercial quote (Q8) |
| **US Treasury Fiscal Data** | e.g. `v2/accounting/od/avg_interest_rates` (Learn context) | **no key**; no published rate limit | data "offered free, without restriction", commercial use allowed — VERIFIED | per dataset | 179 endpoints; daily yield curve lives on home.treasury.gov (not checked: UNVERIFIED) | OFFICIAL_PUBLIC for macro context if a feature needs it |
| **BLS** Public Data API | CPI, unemployment series | v2: registration; **500 queries/day, 50 series/query, 20 years/query**; v1: 25 series, 10 years — VERIFIED | terms page not read: PENDING_CONFIRMATION (BLS data is generally US-government public; confirm citation wording) | release calendar | — | macro context only |
| **BEA** API | GDP, PCE | registration (36-char UserID, accept ToS); **100 requests/min, 100 MB/min, 30 errors/min**, 1-min timeout (guide, April 20 2026) — VERIFIED | ToS accepted at signup: not read here, PENDING_CONFIRMATION | release calendar | — | macro context only |
| **FRED** (St. Louis Fed) | macro series | key | ToU: third-party **copyrighted series need the owner's permission** beyond personal use (the Fed can't grant it); show "not endorsed or certified by the Federal Reserve Bank of St. Louis", link the ToU, don't replicate FRED, delete copies on termination — VERIFIED | per series | per-series rights | use only non-copyrighted series, or prefer the original agency (BLS/BEA/Treasury) |
| **Bank of Canada** Valet | `observations/FXUSDCAD` | no key; don't circumvent request limits — VERIFIED | content may be freely used, copied and distributed with attribution, no implied endorsement; FX **indicative, not for trade execution**; underlying data from LSEG; paid services must say it's free at the source — VERIFIED_ALLOWED (with conditions) | daily publication | 1 call per 6 h window today | keep |
| **Gemini** | AI explanations (StockSteps+) | key; per-user durable quotas (Phase 4B) | not a data source; outputs inherit input rights (derived use must be allowed by FMP/Finnhub terms) | — | — | unchanged |
| **Firebase / Firestore** | auth, user data | project credentials | USER_OWNED data | — | — | unchanged |
| Other licensed vendors (Twelve Data, Massive/Polygon, Tiingo, EODHD, Intrinio, Nasdaq Data Link…) | EOD + fundamentals with external-display tiers | — | **UNVERIFIED** (not checked this session) | — | migration effort | get one written quote (Q8) to compare with an FMP DDLA |

**Not considered:** scraping (Yahoo/Google Finance or exchange sites) and unofficial endpoints.

## Exact open questions (add to Phase 6 Q1–Q8)

- **Q9 (index owners / vendor):** Does showing S&P 500, Nasdaq Composite and Dow Jones Industrial Average **levels** (EOD) need an index
  licence from S&P DJI / Nasdaq in addition to the data vendor's display licence?
- **Q10 (Alpha Vantage sales):** Price and terms for a free consumer app (mobile + web) showing US EOD closes (5 years), split/dividend
  adjusted series, shared server caching and derived values (returns, P/E).
- **Q11 (BLS, BEA):** Confirm the citation wording and any restriction on redistributing series values in a commercial app (both are
  US-government statistics; BEA's signup terms need reading).
- **Q12 (counsel):** Confirm that showing SEC XBRL values and StockSteps-computed ratios from them, with "Source: SEC EDGAR filings", needs
  no further licence, and that the SEC trademark guidance is met by that wording.

## Sources (retrieved 2026-10-17)
- SEC, "Accessing EDGAR Data": https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data
- SEC, "EDGAR Application Programming Interfaces": https://www.sec.gov/search-filings/edgar-application-programming-interfaces
- SEC, Privacy and Security Policy (Website Dissemination): https://www.sec.gov/privacy
- Alpha Vantage Terms of Service (PDF): https://www.alphavantage.co/terms_of_service/ ; Support: https://www.alphavantage.co/support/ ; Documentation: https://www.alphavantage.co/documentation/
- Finnhub Terms of Service: https://finnhub.io/terms-of-service
- FMP (search-indexed; direct fetch 403): https://site.financialmodelingprep.com/terms-of-service , https://site.financialmodelingprep.com/insights/platform/can-you-use-fmp-data-in-a-public-app-website-or-client-dashboard
- FRED API Terms of Use: https://fred.stlouisfed.org/docs/api/terms_of_use.html
- BLS API: https://www.bls.gov/developers/ , https://www.bls.gov/bls/api_features.htm
- BEA API User Guide (PDF, April 20 2026): https://apps.bea.gov/api/_pdf/bea_web_service_api_user_guide.pdf
- Treasury Fiscal Data API: https://fiscaldata.treasury.gov/api-documentation/
- Bank of Canada Terms and Conditions: https://www.bankofcanada.ca/terms/

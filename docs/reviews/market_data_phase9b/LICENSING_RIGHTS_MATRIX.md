# Licensing rights matrix — Phase 9B

The same matrix is code: `server/.../marketdata/DataRights.kt` (`LicenseRegistry.DEFAULT`, tested in `SourceSelectionAndLicensingTest`).
**Fail closed:** only `VERIFIED_ALLOWED` display for a channel clears a dataset; unknown provider/dataset pairs are not allowed; derived
values inherit the most restrictive input (`derivedDisplayAllowed`). A signed agreement is recorded with `LicenseRegistry.with(...)`.
Mobile and web are separate channels.

Status words: VERIFIED_ALLOWED · VERIFIED_RESTRICTED · PENDING_CONFIRMATION · NOT_ALLOWED. "Restricted" = published terms restrict the use
on the current plan/terms; a vendor agreement could change it.

| Provider · dataset | Class | Display (mobile / web) | Shared server cache | Derived values | Redistribution (exports, API) | Attribution | Evidence |
|---|---|---|---|---|---|---|---|
| SEC · symbol reference (tickers, CIK, exchange) | OFFICIAL_PUBLIC | VERIFIED_ALLOWED / VERIFIED_ALLOWED | VERIFIED_ALLOWED | VERIFIED_ALLOWED | VERIFIED_ALLOWED | "Source: SEC EDGAR filings" (requested citation); no SEC seal/logos; no implied endorsement | sec.gov privacy/dissemination policy; fair access 10 req/s + User-Agent |
| SEC · reported fundamentals (XBRL companyfacts, submissions) | OFFICIAL_PUBLIC | VERIFIED_ALLOWED / VERIFIED_ALLOWED | VERIFIED_ALLOWED | VERIFIED_ALLOWED | VERIFIED_ALLOWED | as above | same; counsel check Q12 recommended before launch |
| Bank of Canada · FX USD/CAD | OFFICIAL_PUBLIC | VERIFIED_ALLOWED / VERIFIED_ALLOWED | VERIFIED_ALLOWED | VERIFIED_ALLOWED | VERIFIED_ALLOWED (if sold: say it's free at the source) | "Source: Bank of Canada" + indicative rate; LSEG is the underlying data source | bankofcanada.ca/terms |
| StockSteps · derived metrics | INTERNALLY_DERIVED | allowed by us, **gated on every input** | — | — | — | name the inputs' sources | `derivedDisplayAllowed` |
| FMP · search, profile, logos, statements, ratios, estimates, EOD, 5-min, quotes, movers, index levels, dividends | UNAVAILABLE_PENDING_LICENSE | VERIFIED_RESTRICTED / VERIFIED_RESTRICTED | PENDING_CONFIRMATION | VERIFIED_RESTRICTED | VERIFIED_RESTRICTED | per agreement | FMP ToS + pricing/insight pages: personal non-commercial licence; public display needs a DDLA and prior written approval (Q1–Q4) |
| Finnhub · company/market news, earnings calendar, profile2, quotes | UNAVAILABLE_PENDING_LICENSE | VERIFIED_RESTRICTED / VERIFIED_RESTRICTED | PENDING_CONFIRMATION | VERIFIED_RESTRICTED | VERIFIED_RESTRICTED | publisher names on headlines | Finnhub ToS: personal plans not for business use without approval; no redistribution of data or derived results; delete on termination (Q5) |
| Alpha Vantage · any (free key) | not integrated | VERIFIED_RESTRICTED | — | VERIFIED_RESTRICTED | VERIFIED_RESTRICTED | — | AV ToS §2: personal, non-commercial unless agreed in writing (Q10) |
| Treasury Fiscal Data · any | OFFICIAL_PUBLIC (not integrated) | VERIFIED_ALLOWED (published licence) | allowed | allowed | allowed | cite the dataset | fiscaldata.treasury.gov: "free, without restriction" |
| BLS · series | OFFICIAL_PUBLIC (not integrated) | PENDING_CONFIRMATION | — | — | — | BLS citation | terms page not read (Q11) |
| BEA · series | OFFICIAL_PUBLIC (not integrated) | PENDING_CONFIRMATION | — | — | — | — | signup ToS not read (Q11) |
| FRED · series | mixed (not integrated) | per series: non-copyrighted → allowed with the required notice; copyrighted → NOT_ALLOWED without the owner's permission | not addressed | — | — | "not endorsed or certified by the Federal Reserve Bank of St. Louis" + ToU link | FRED API ToU |
| Firestore · user ledgers, watchlists, alerts | USER_OWNED | the owner only (9A central gate) | **never** in shared caches | per user | no | — | 9A |
| Gemini · AI text | derived | gated on inputs | version-keyed per input | — | — | "AI" label (existing) | input licences must allow derived/AI use |

## What this means for launch

- **Cleared today:** SEC fundamentals and ratios computed only from them (margins, growth, ROE/ROA, D/E, current ratio, FCF, EPS), SEC
  company identity, BoC FX, StockSteps' own text and lists, the users' own ledgers.
- **Blocked until an agreement:** every price (quotes, EOD, charts, index levels), Discover prices and market caps, news, earnings
  calendar/estimates, logos, and every ratio that uses a price (P/E, P/S, dividend yield, P/E history, Portfolio/Practice valuations).
- **Not affected by licensing:** the user's own entries; Practice's *mechanics* (but its prices are licensed data).
- `STOCKSTEPS_LICENSE_MODE=commercial` makes `LicenseGate.permits` fail closed; in 9B it only drives the startup report
  (`Market-data licensing: … notCleared=[…]`). Route enforcement and the user-facing copy for a blocked section are owner decision D-9B-7.

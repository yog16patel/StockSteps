# Alpha Vantage endpoint matrix — Phase 9B.7 (official documentation, retrieved 2026-10-17)

Source: https://www.alphavantage.co/documentation/ (saved and searched locally), https://www.alphavantage.co/premium/, Terms of Service PDF.
"Premium" = the documentation's label or tip ("this is a premium API function"). **Account entitlement is UNVERIFIED** — no Alpha Vantage
key exists on this machine, so no account-specific probe was possible. Field names: **checked** = against the documentation's own sample
response (fetched with the public `demo` key, IBM, 2 requests: only the two daily series answered; all others returned the demo-key
`Information` notice); **documented** = from the documented response shapes, to be confirmed by the first bounded REAL smoke test.

| Function | Entitlement (docs) | Fields used | Freshness / history | Adjustment basis | StockSteps use | Status |
|---|---|---|---|---|---|---|
| TIME_SERIES_DAILY | free (`compact` = latest 100 points); `outputsize=full` (25+ years) premium | `Meta Data` (`2. Symbol`, `3. Last Refreshed`, `4. Output Size`, `5. Time Zone`); per date `1. open`…`4. close`, `5. volume` (**checked**) | end of day; 100 sessions free, full on premium | **raw (as-traded)** — documented | charts, Markets sector/fund closes, Portfolio/Practice closes, Discover closes, P/E history | implemented + wired |
| TIME_SERIES_DAILY_ADJUSTED | **premium** | + `5. adjusted close`, `6. volume`, `7. dividend amount`, `8. split coefficient` (**checked**) | EOD; `entitlement=realtime/delayed` premium, personal | adjusted close + events | split/dividend events (future, D-9B7-5) | client + parser, not wired |
| TIME_SERIES_WEEKLY/MONTHLY(_ADJUSTED) | free | — | weekly/monthly | adjusted variants include dividends | not needed | not implemented |
| GLOBAL_QUOTE | free; updated **at the end of each trading day** for all users; realtime / 15-min delayed premium "for your personal use", commercial "contact sales" | `Global Quote`: `01. symbol`…`10. change percent` (documented) | EOD unless `entitlement` set (never set by StockSteps) | as traded | Details quote, Watchlist, alerts, Markets index quotes (US indices `^…` not supported → fund-close fallback, 7B) | implemented + wired |
| REALTIME_BULK_QUOTES | premium, "Realtime US Market Data" plans, personal use | — | realtime | — | not used (no realtime) | not implemented |
| SYMBOL_SEARCH | free ("Utility") | `bestMatches[]`: `1. symbol`, `2. name`, `3. type`, `4. region`, `8. currency`, `9. matchScore` (documented) | reference | — | Company Search (US equities/ETFs only) | implemented + wired |
| MARKET_STATUS | free | — | live | — | not used (local calendars) | not implemented |
| LISTING_STATUS | free (CSV) | symbol, name, exchange, assetType, ipoDate, delistingDate, status | daily; history from 2010 | — | delistings / survivorship (future) | not implemented |
| OVERVIEW | **premium** (current docs) | `Symbol`, `AssetType`, `Name`, `Description`, `CIK`, `Exchange`, `Currency`, `Country`, `Sector`, `Industry`, `OfficialSite`, `FiscalYearEnd`, `LatestQuarter`, `MarketCapitalization`, `SharesOutstanding` (documented) | "refreshed on the same day a company reports" | — | profiles on a premium plan (sector left null: SEC office names ≠ Discover's 11 sectors) | implemented + wired (premium only) |
| ETF_PROFILE | free | net assets, expense ratio, holdings, sectors | — | — | not consumed today | not implemented |
| INCOME_STATEMENT / BALANCE_SHEET / CASH_FLOW | free ("Trending") | `annualReports[]`/`quarterlyReports[]`: `fiscalDateEnding`, `reportedCurrency`, `totalRevenue`, `grossProfit`, `operatingIncome`, `netIncome`, `totalAssets`, `totalCurrentAssets`, `cashAndCashEquivalentsAtCarryingValue`, `totalLiabilities`, `totalCurrentLiabilities`, `shortLongTermDebtTotal`, `totalShareholderEquity`, `operatingCashflow`, `capitalExpenditures`, `dividendPayout`; missing = `"None"` (documented) | same day as the report; ~20 years annual | "normalized fields mapped to GAAP and IFRS taxonomies" | statement history (Comparison history/research) | implemented + wired |
| SHARES_OUTSTANDING | free | quarterly diluted and basic shares | quarterly | — | not consumed (OVERVIEW / FMP cover it) | not implemented |
| EARNINGS | free | `annualEarnings[]`, `quarterlyEarnings[]` (`fiscalDateEnding`, `reportedDate`, `reportedEPS`, `estimatedEPS`, `surprise…`, `reportTime`) | per report | EPS basis (GAAP vs adjusted, diluted vs basic) **not documented** | P/E history would need it; FMP keeps quarterly EPS (diluted, with share counts for split detection) | client + parser, not wired |
| EARNINGS_ESTIMATES | free ("Trending") | EPS/revenue estimates | — | — | Valuation estimates (FMP today) | not implemented |
| EARNINGS_CALENDAR | free (CSV) | expected dates, 3/6/12 months | — | — | Finnhub today (D-9B7-6) | not implemented |
| DIVIDENDS | free | `data[]`: `ex_dividend_date`, `declaration_date`, `record_date`, `payment_date`, `amount` (documented) | historical and declared | per share as paid | Practice/Portfolio dividends (future), yield | client + parser, not wired |
| SPLITS | free | `data[]`: `effective_date`, `split_factor` (documented) | historical | ratio | split detection for Portfolio/Practice (D-9B7-5) | client + parser, not wired |
| TOP_GAINERS_LOSERS | free; **end of each trading day** by default; realtime/delayed premium, personal | `top_gainers`/`top_losers`/`most_actively_traded[]`: `ticker`, `price`, `change_amount`, `change_percentage`, `volume`; `last_updated` (documented) | EOD | — | Markets movers (no company names in this function) | implemented + wired |
| NEWS_SENTIMENT | **premium** | — | live | — | Finnhub today; licensing pending | not implemented |
| INDEX_DATA | **premium** (150/300/600/1200-per-minute plans only) | OHLC per index | daily/weekly/monthly | — | index levels; also needs index-owner licensing (Q9) | not implemented |
| INDEX_CATALOG | free | index symbols | — | — | — | not implemented |
| FX_DAILY | free | OHLC per pair | daily | — | Bank of Canada stays (official, cleared) | not implemented |
| TREASURY_YIELD, CPI, … | free | economic series | per release | — | no feature consumes macro data | not implemented |

**Quota (published, premium page):** free = 25 requests/day; premium plans 75 / 150 / 300 / 600 / 1,200 requests per minute (monthly list
prices $49.99 / $99.99 / $149.99 / $199.99 / $249.99 on 2026-10-17; annual plans also listed), "No daily limits". Refusals arrive **inside
HTTP 200** (`Information` / `Note` / `Error Message`), observed: the demo-key notice. Caching/persistence permissions: not addressed in the
terms; display to others = commercial (see `ALPHA_VANTAGE_LICENSE_AND_ENTITLEMENTS.md`).

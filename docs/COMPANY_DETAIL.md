# Beginner company detail implementation

## Inspection and design decisions

The existing app uses KMP domain/network models with Koin; Android uses lifecycle
ViewModels/Compose and native iOS uses Observation/SwiftUI with Kotlin use-case
adapters. Search routes carry identity/arguments. Scenes acquire models and wire
events; screens only render state and callbacks. The redesign preserves this.

Existing quote/profile jobs already load independently. The shared theme defines
spacing, typography and positive/negative colors. Existing adaptive pane geometry
keeps detail content away from hinges. Existing NewsCard components render source,
time, AI summary, sentiment, why it matters and original links. Watchlist identity
and offline/cloud sync remain reused. No chart library or detail-specific Figma
frame was present in the repository. Earlier Figma work covers login/Home; this
screen follows existing native theme/components rather than claiming a new Figma
match. No new provider account or client-side credentials were introduced.

## Initial inspection data mapping and gaps

The Financials/Valuation rows below describe the initial inspection. Their later
implementation and live availability are documented in the integration and UX
redesign sections below.

| Required data | Existing/new integration | Endpoint | Current availability / missing inputs |
|---|---|---|---|
| Name, description, logo, sector, industry, currency, exchange | Existing FMP profile | `/stable/profile?symbol=…` | Nullable provider fields; subscription errors remain section-level |
| Price, dollar/percent change, day range | Existing FMP or configured Finnhub quote | FMP `/stable/quote`; Finnhub `/api/v1/quote` | Existing values; no fallback to a different listing |
| Market cap | Existing FMP quote DTO, newly carried to shared StockQuote | `/stable/quote` | Available when FMP supplies valid marketCap; nullable for Finnhub |
| Company news | New Finnhub route, existing news DTO/AI cache | `/api/v1/company-news?symbol=…&from=…&to=…` | Latest 30 UTC days, newest first, up to 20; access/empty/error handled |
| Price history | Not connected | Candidate FMP `/stable/historical-price-eod/full`; intraday endpoints for 1D | No historical series in current repository; chart explicitly empty, periods disabled |
| Revenue, net income, EPS, growth | Not connected | Candidate FMP `/stable/income-statement` and growth endpoints; Finnhub `/api/v1/stock/metric` | Must validate account access, units and comparable periods before mapping |
| Margins, ROE, ROIC | Not connected | Candidate FMP `/stable/ratios` / key-metrics; Finnhub stock/metric | No verified inputs; no invented labels or thresholds |
| Cash, debt, debt/equity, interest coverage | Not connected | Candidate FMP balance-sheet-statement/ratios | No verified statement integration |
| Operating cash flow, CapEx, FCF | Not connected | Candidate FMP cash-flow-statement | Requires signed CapEx normalization and period consistency |
| P/E, forward P/E, PEG, P/S, P/FCF, EV/EBITDA | Not connected | Candidate FMP ratios/analyst estimates; Finnhub stock/metric | Current, historical and industry comparisons unavailable |
| Dividends, growth, buybacks, share count/history | Not connected | Candidate statements/dividend endpoints | No verified integration; explanatory rows only |
| Snapshot scores / summary, risk analysis | No backend computation yet | No endpoint | Unassessed; never inferred from missing data or the UI |
| Price alerts | No existing feature | None | Omitted rather than presenting an inactive promise |

Provider references: [FMP API documentation](https://site.financialmodelingprep.com/developer/docs),
[Finnhub official client examples](https://github.com/Finnhub-Stock-API/finnhub-python/blob/master/examples.py).
Candidate endpoints above are investigation targets, not claims of free access.
US-only search does not guarantee that every US stock is accessible on a plan.

## Architecture and UI

`core/companydetail` holds immutable CompanyDetailUiState, metric education,
financial sections, snapshot/risk models, period/chart/status models and a shared
presentation mapper. Only FMP market cap is newly mapped as a financial metric.
The mapper performs formatting and historical comparisons; it never synthesizes
financial facts, scores, risk flags or investment recommendations.

Android StockSearchViewModel publishes mapped detail state, selected tab, metric
sheet state and independent company news state; explicit CompanyDetailAction
handles tabs/education/period choices/news retry. Native Observation model uses
the same shared mapper/glossary and a small native action enum. Existing quote and
profile jobs remain independent. Selecting another listing cancels previous news;
failed news does not discard company/quote state. The backend route
`GET /api/v1/stocks/{symbol}/news` validates ticker input, retrieves exact-symbol
Finnhub company news and reuses the existing simplification/cache service.

The header shows name/listing/sector, logo when supplied, formatted price/change,
directional arrows and watchlist action. Overview includes description, empty
price-history state, unassessed snapshot, available key metrics, education links
and an honest absence of verified risk analysis. Financials exposes educational
rows grouped by growth/profitability/health/cash flow/shareholders; missing inputs
are labelled Unavailable. Valuation shows missing comparison inputs explicitly
and avoids cheap/expensive labels. News reuses actual cards and source links.

Android compact screens now devote the full pane to the selected detail instead
of squeezing it into the old 240dp nested scrolling card. Larger/hinged screens
retain the existing sidebar/detail geometry. Both platforms have pinned section
tabs, lazy content and reusable education sheets. iOS caps reading width and uses
native toolbar/Glass button support. Logo images use Coil Compose/Ktor on Android
and AsyncImage on iOS. Financial charts accept separate point/state models; their
renderer is independent of APIs. There are no fabricated production chart points.

## Future calculations and verification

Before activating financials, normalize units, statement currency, period and
as-of dates in backend/domain mapping. Growth is `(current / comparable_prior - 1)
* 100` only when the prior denominator is meaningful; negative/zero bases require
explicit handling. Net margin is `net_income / revenue * 100` with valid revenue.
FCF is operating cash flow minus normalized positive capital expenditure.
Historical valuation comparisons require comparable ratios, observation dates and
positive meaningful denominators. Never compare annual with quarterly values.
Industry/peer averages require a documented universe and aggregation method.
Scores and risk insights require a published backend methodology and provenance;
missing inputs should remain unassessed, not score zero.

Previews are explicitly labelled sample data and are isolated from runtime mapping:
normal, missing metrics, loading, partial error, dark, large pane, profitable and
loss-making examples. Core tests cover missing/invalid values, signed percent and
money formatting, independent quote/profile mapping, market-cap mapping, neutral
states and guarded historical comparisons. Backend tests cover exact-symbol news,
valid date parameters, safe failures and invalid symbols. Existing shared tests
cover independent quote/profile failures and retries.

Remaining: live provider/account checks for company news, historical price/financial
integrations, statement period selectors, populated historical charts, provenance
and scoring/risk methodology, UI runtime accessibility checks on both platforms.
The actual iOS Firebase pin still needs Xcode 26.2+; the local compatibility compile
is an API/syntax check, not a production Firebase build.


### Company-news relevance correction

Finnhub's company feed is broad and can include articles centred on a competitor.
The DTO now retains `related` symbols. Backend filtering runs before the 20-item
limit and AI enrichment. A non-empty related-symbol list must include the exact
requested ticker. Articles also need explicit ticker evidence in headline/summary
or the verified company name in the headline. A name appearing only in a summary
is insufficient, so incidental competitor references are excluded. Short tickers
need explicit stock notation to avoid matching ordinary words. Name resolution
uses Finnhub profile2, validates its ticker and caches up to 256 identities for
six hours (failed lookups for five minutes). A lookup failure keeps ticker-backed
articles rather than failing the whole news section. This is a conservative
relevance heuristic; brand aliases and genuinely relevant articles without textual
identity evidence may be omitted. Multi-company articles with explicit evidence
can remain. General market news is unchanged.

Optional identity lookup has a two-second budget and runs alongside the news
request; parent request cancellation still propagates.

## Financials and valuation integration (2026-10-05)

`GET /api/v1/stocks/{symbol}/fundamentals?period=annual` supplies both tabs.
`period=quarter` selects quarterly statements and compares with the same fiscal
quarter one year earlier. Annual CAGR and annual valuation history stay annual.
Quote, profile, and news remain separate requests; fundamentals do not gate them.

The provider adapter owns FMP DTOs. `CompanyFundamentals` exposes provider-neutral
financial groups and valuation maps keyed by stable StockSteps metric IDs, each
containing `FinancialFact`: nullable integer amount or numeric value, source,
availability, basis (period/date/fiscal year/currency), and explanatory context.
No money strings, API credentials, or FMP response objects cross the API boundary.
Static metric education stays in `MetricEducation`; calculated context identifies
its source separately. Native SwiftUI and Android use the same presenter.

### Provider mapping

All paths use FMP `/stable/`, exact symbol, and the backend's existing API key.

| Dataset | Properties used | StockSteps metrics |
| --- | --- | --- |
| `income-statement` annual/quarter | `revenue`, `netIncome`, `epsDiluted`, `grossProfit`, `operatingIncome`, `ebit`, `ebitda`, `interestExpense` | Revenue/net income/diluted EPS, YoY, 3Y/5Y CAGR, matched-period fallback margins/coverage |
| `income-statement-ttm` | `netIncome`, `ebitda`, `interestExpense`, `reportedCurrency`, `date` | Validate trailing earnings/EBITDA/interest denominators and label TTM basis |
| `balance-sheet-statement` annual/quarter | `cashAndCashEquivalents`, `totalDebt`, `totalAssets`, `totalLiabilities`, `totalStockholdersEquity`, `totalCurrentAssets`, `totalCurrentLiabilities`, `netDebt` | Health balances, direct/fallback debt-equity/current ratio, net debt |
| `cash-flow-statement` annual/quarter | `operatingCashFlow`, `netCashProvidedByOperatingActivities`, `capitalExpenditure`, `freeCashFlow`, `commonStockRepurchased` | OCF, CapEx spending, FCF, FCF margin, gross buyback spending |
| `cash-flow-statement-ttm` | `freeCashFlow`, `commonDividendsPaid` | Validate trailing FCF and corroborate dividend status |
| `ratios-ttm` | `priceToEarningsRatioTTM`, `priceToEarningsGrowthRatioTTM`, `priceToSalesRatioTTM`, `priceToBookRatioTTM`, `priceToFreeCashFlowRatioTTM`, `enterpriseValueTTM`, `enterpriseValueMultipleTTM` | Current valuation; EV/EBITDA fallbacks |
| `ratios-ttm` | `grossProfitMarginTTM`, `operatingProfitMarginTTM`, `netProfitMarginTTM`, `debtToEquityRatioTTM`, `currentRatioTTM`, `quickRatioTTM`, `interestCoverageRatioTTM` | Profitability/health ratios |
| `ratios-ttm` | `dividendYieldTTM`, `dividendPerShareTTM`, `dividendPayoutRatioTTM` | Trailing dividend metrics |
| `key-metrics-ttm` | `enterpriseValueTTM`, `evToEBITDATTM`, `returnOnEquityTTM`, `returnOnAssetsTTM`, `returnOnInvestedCapitalTTM`, `netDebtToEBITDATTM` | EV, EV/EBITDA, ROE/ROA/ROIC, net debt/EBITDA |
| `ratios` annual | `priceToEarningsRatio`, `priceToSalesRatio`, `priceToBookRatio`, `priceToFreeCashFlowRatio`, `enterpriseValueMultiple` | Annual historical observations and comparisons |
| `analyst-estimates` annual | `epsAvg`, `date`, `numAnalystsEps` | Forward P/E: price / earliest eligible future FY consensus EPS; currencies must match |
| `dividends` | `adjDividend`, `date` | YoY change in completed calendar-year adjusted payment totals; future events excluded |
| `shares-float` | `outstandingShares`, `date` | Actual point-in-time share count, distinct from free float |
| Existing `quote`/`profile` | Existing domain mappings | Company identity, price, daily movement, market cap, sector and industry |

Official schema references:
https://site.financialmodelingprep.com/developer/docs
https://site.financialmodelingprep.com/es/developer/docs/stable/metrics-ratios-ttm
https://site.financialmodelingprep.com/de/developer/docs/stable/key-metrics-ttm
https://site.financialmodelingprep.com/developer/docs/stable/cashflow-statement

### Calculation and cache rules

- YoY: `(current - previous) / abs(previous) * 100`; nonzero denominator.
- CAGR: `((current / beginning)^(1 / years) - 1) * 100`; positive endpoints
  and exact fiscal-year distance. Six fiscal years support five-year CAGR.
- FCF: prefer direct provider value; otherwise OCF minus CapEx spending.
  FMP negative CapEx outflows become positive spending. Unexpected positive
  CapEx cannot support a calculated fallback. Preserve legitimate negative FCF.
- FCF margin: FCF/revenue*100, matched date/period/currency and positive revenue.
- Historical averages use the latest five completed fiscal years, preserving gaps.
  At least three positive finite observations are needed. Partial averages show
  their valid-year count, not "5Y average." Return median/minimum/maximum and
  annual observations. A >10x range suppresses comparisons without discarding
  extreme observations; this conservative reliability rule is centralized/tested.
- Above/below: `(current - average) / average * 100`; positive comparable values.
- Margin changes use percentage points. Revenue acceleration compares adjacent
  YoY rates. Debt and FCF context compares matched periods; no arbitrary ratings.
- Quote: 30s; price-sensitive TTM ratios/key metrics: 5min; TTM statements and
  estimates: 6h; statements/history/profile/dividends/shares: 24h.
- A bounded process-local keyed cache coalesces identical requests. Quote/profile
  caches are shared with their existing endpoints. Transient failed datasets receive
  a 30s cooldown; access-denied (402/403) datasets receive a one-hour cooldown; successful dataset TTLs remain separate. No Redis/database.
- Phase 2 cost work (`docs/FINANCIAL_API_CACHE_IMPLEMENTATION.md`): concurrent callers share one in-flight load *and* its failure
  (nothing failed is stored); quarterly income uses the same 24-quarter request as Valuation (newest 8 shown); the market status
  comes from the NYSE calendar (`UsMarketCalendar`) instead of FMP `exchange-market-hours`; every upstream request is metered once
  in `apiCall` (`/internal/metrics/usage`).
- Nullable/flexible FMP decoders accept string/numeric fiscal years and preserve
  other fields when an optional financial number is malformed.

### Partial data and limits

Provider 402/403 access restrictions and other HTTP/timeout errors produce
per-dataset availability without dropping successful groups. Cancellation still
propagates. Mismatched returned symbols are rejected. Negative/non-positive
trailing earnings suppress P/E and PEG. Zero trailing interest expense does not
become a zero coverage ratio. Verified zero trailing DPS plus zero common
payments, without contradictory recent dividend events, shows "No dividends paid
in the trailing year" rather than a fabricated zero yield.

Outstanding-share history and split-adjustment consistency have not been verified:
`sharesChange5` intentionally remains unavailable. Weighted average shares are not
silently substituted for actual outstanding shares. Industry averages, arbitrary
scores, qualitative ratings, FFO/AFFO, and price history remain out of scope.
Banks/REIT metric applicability is explained. Dividend growth includes special
payments and requires the response to extend before the compared calendar years.
No forward earnings or valuation value is fabricated when estimates/currency
metadata are unavailable. The account-specific live observations below supersede the earlier unverified
access limitation; fixture tests alone do not establish paid/free access.

Restart the backend and rebuild the apps to use this endpoint. Existing IDE-run
servers do not automatically reload source changes.

## Financials UX redesign and live access audit (2026-10-05)

The previous screen rendered every metric, even null values, and concatenated
`FinancialFact.note`, reporting period and access status into each description.
This is why users saw repeated diagnostic text. The UI now consumes a shared
`FinancialsUiState` with section status and provider-independent `MetricAvailability`.
Numeric domain facts stay numeric; `FinancialsFormatter` formats display values.
The public service drops arbitrary notes/warnings and maps access/network failures
to `TEMPORARILY_UNAVAILABLE`. A serializer decodes old access/error enum names into
that neutral value so the new apps can also use an existing IDE-run server.

### Live Apple results (existing localhost backend, annual and quarterly)

No credentials were read to perform this check. These observations are a snapshot
of the account's returned data, not a guarantee for every symbol or future date.

| Available value | Observed AAPL value | Existing source |
| --- | --- | --- |
| Gross / operating / net margin | 48.65% / 33.17% / 27.62% | FMP `ratios-ttm`; fractional ratios × 100 |
| ROIC / ROE / ROA | 51.87% / 137.18% / 33.64% | FMP `key-metrics-ttm`; fractional returns × 100 |
| Debt/equity / current / quick ratio | 0.784 / 1.003 / 0.929 | FMP `ratios-ttm` |
| Net debt/EBITDA | 0.266 | FMP `key-metrics-ttm`; available in the API, outside the primary cards |
| Dividend yield / DPS / payout ratio | 0.318% / 1.06 / 12.13% | FMP `ratios-ttm` |
| Shares outstanding | 14,594,180,000 | FMP `shares-float` |
| Analyst estimates | Dataset available | FMP `analyst-estimates`; forward P/E still requires matching fiscal/currency inputs |

Access restrictions were reported for `/stable/income-statement` (annual and
quarterly), `/balance-sheet-statement`, `/cash-flow-statement`, `/ratios` (annual
history), `/income-statement-ttm`, `/cash-flow-statement-ttm`, and `/dividends`.
Consequently revenue/net income/EPS, YoY/CAGR, cash/debt/balance totals,
OCF/CapEx/FCF/FCF margin, dividend growth, buybacks and historical comparisons
remain unavailable for this live account. Historical shares are separately
unverified. The old live backend also returned interest coverage = 0 without a
validating interest-expense statement; the new mapper suppresses that ambiguous
zero rather than interpreting it as a verified financial fact.

The stable income-statement path matches the [official endpoint documentation](https://site.financialmodelingprep.com/developer/docs/stable/income-statement).
FMP's [current plan comparison](https://site.financialmodelingprep.com/pricing-plans)
places annual fundamentals/ratios in Starter and full fundamentals/ratios in
Premium. Exact account tier and endpoint entitlement cannot be inferred from the
existing backend's grouped 402/403 status; check the account dashboard/support
before selecting a plan. This audit does not claim every unavailable endpoint
requires the same tier. No scraping or alternate paid bypass was introduced.

Accessible TTM ratios already supply equivalent profitability/health/dividend
ratios. Quote/profile and current share count cannot safely reconstruct historical
statement totals or growth: actual point-in-time shares differ from historical
weighted-average shares. Finnhub's existing integration supplies quotes and news,
not basic financials/statements. Its documented `stock/metric` endpoint would need
a separate verified integration and account check; it is not silently used here.

### Presentation and calculations

- Growth prioritizes revenue/net income/EPS with comparable YoY context. Available
  CAGR goes into expandable growth history. Missing primary values do not discard
  available cards, and missing secondary values are omitted.
- Growth insights require matching reporting bases and valid positive current
  earnings/EPS; improving losses are not described as growing profits.
- Profitability prioritizes gross/operating/net margins and ROIC, with optional
  ROE/ROA. Net-margin explanation rounds net profit (or loss) per 100 revenue;
  it never turns a loss into a positive claim.
- Health prioritizes cash/debt/debt-equity/current ratio; cash/debt YoY context is
  numeric and uses matching balance periods/currencies. No qualitative thresholds.
- Cash-flow arrows are shown only with all three matched amounts and a reconciling
  OCF − spending = FCF calculation (within two whole currency units of rounding).
  Otherwise available values remain separate cards with the general definition.
- Shareholders prioritize yield/growth/buybacks/shares. Confirmed non-payers get
  one statement; missing or ambiguous zero yield is not treated as confirmed absence.
- Existing server calculations remain YoY, CAGR, fallback FCF/margins/health ratios,
  dividend growth, matched forward P/E and historical valuation comparisons. These
  stay absent when inputs fail validation. No AI or fabricated investment scores.

Android and SwiftUI use separate Financials component files, neutral value text,
white/dark theme surfaces, blue education links, actual movement colors, responsive
one/two-column cards and shared layout tokens. Large text uses one column. Tabs
scroll horizontally; period controls sit below them with 16-unit separation.
Loading uses skeletons; empty/failing sections have compact friendly copy and
retry. Financial state no longer appears above Overview, Valuation or News.
Repeated denied dataset requests are cached for one hour per key; temporary failures
retain 30 seconds. A backend restart clears that process cache after changing account
configuration. Retrying in the app respects cache TTLs and cannot unlock entitlement.

### Changed files for this redesign

- `core/.../companydetail/FinancialsPresentation.kt` (new), `CompanyDetailModels.kt`
- `core/.../model/CompanyFundamentals.kt`
- `app/shared/.../presentation/companydetail/CompanyFinancialsSection.kt` (new),
  `CompanyDetailScreen.kt`, `CompanyDetailPreviews.kt`
- `app/shared/.../presentation/stocksearch/StockSearchState.kt`, `StockSearchViewModel.kt`
- `app/shared/.../theme/FinancialsTokens.kt` (new)
- `app/iosApp/iosApp/CompanyFinancialsView.swift` (new), `CompanyDetailView.swift`,
  `StockSearchUiState.swift`, `StockSearchViewModel.swift`
- `server/.../service/CompanyFinancialService.kt`, `CompanyFinancialCache.kt`
- `server/.../repositoryImpl/FmpFundamentalsLoader.kt`, `FmpFundamentalsMapper.kt`
- Tests: `FinancialsPresenterTest.kt` (new), `FinancialCacheCooldownTest.kt` (new),
  extended `CompanyFinancialRoutesTest.kt` and `CompanyFundamentalsTest.kt`
- `docs/COMPANY_DETAIL.md`, `PROJECT_HANDOFF.md`

Tests cover formatting, partial/missing sections, safe growth insights, losses,
confirmed no-dividend versus missing, cash-flow reconciliation, legacy enum decoding,
public diagnostic stripping, cash/debt YoY, ambiguous coverage zero and denied-call
cooldown expiry. Android previews cover partial/empty/loading/loss/dark/tablet states.

Financial retry preserves successful sections while missing sections load. If a
refresh fails, one Financials-only message identifies previously loaded figures.
Changing company or Annual/Quarterly clears the old-period facts. The existing
ViewModel regression now covers refresh failure retention as well as period switch
and cancellation, and the shared presenter tests cover section status on refresh.

Final validation: 58 server cases (3 pre-existing skips), 32 core JVM and 20 shared
Android host tests, zero failures. Android debug APK and shared iOS simulator
framework built; native SwiftUI compiled in the existing isolated Firebase
compatibility project. Production Firebase pin remains unchanged and needs its
supported Xcode version. Android emulator visual inspection confirmed the full
News label and two-column Apple profitability cards with friendly Growth state.
Native iOS visual acceptance remains pending. The new Android build can decode the
older running server's availability names and omits optional zero interest coverage;
restart the IDE backend to serve the sanitized public response and typed cash/debt
context. No production/cloud deployment or credentials read was performed.

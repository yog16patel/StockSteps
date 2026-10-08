# Smart Stock Screener & Stock Comparison

Markets → **Discover Stocks** and **Compare Stocks** (pushed destinations; the bottom bar is
unchanged: Home | Markets | Portfolio | Watchlist | Learn). Company Details stays the one company
destination and gains a **Compare** action. Android (Compose) and iOS (SwiftUI) render the same
shared Kotlin presenters.

## Architecture

| Layer | Files |
| --- | --- |
| Models, metric catalog, presets | `core/.../screener/ScreenerModels.kt`, `ScreenerCatalogDefinitions.kt` (`ScreenerDefinitions`) |
| Engine (pure, tested) | `ScreenerEngine.kt`: `CompanyRecordBuilder`, `ScreenerEngine` (validate/filter/sort/page), `ComparisonEngine` (observations), `PerformanceNormalizer` |
| Presenters / formatting | `ScreenerPresentation.kt`: `ScreenerPresenter`, `ComparisonPresenter`, `ComparisonSelection` (shared, max 4), `SavedScreensRepository`, `MetricFormatter` |
| Education | `companydetail/MetricEducation.kt` (extended with *how it's calculated* and *why investors look at it*; reused, not duplicated) |
| Server | `server/.../screener/ScreenerService.kt` (universe sources, record index, search, compare, performance, rate limiter), `SavedScreensService.kt`, `ScreenerRoutes.kt` |
| Android | `presentation/screener/{ScreenerRoute,ScreenerScene,ScreenerScreen,ComparisonScreen}.kt`; Markets tiles; Company Details "Compare" |
| iOS | `ScreenerScenes.swift`; `IosScreenerClient` bridge; Markets tiles; Company Details "Compare" |
| Fixtures | `scripts/generate_screener_fixtures.py` → `fixtures/stocks/{KO,JPM,XOM,JNJ,RIVN,RY.TO,ENB.TO,CNR.TO,SHOP.TO,BCE.TO,CSU.TO,BB.TO}/…`, `fixtures/screener/universe.json` |

## Metric normalization

There is one value per metric per company. `CompanyRecordBuilder` reads the same
`CompanyFundamentals` facts (keyed by `pe`, `revenueGrowth`, `netMargin`, …) that Company Details,
Financials and Valuation show, plus the quote and profile. A trailing P/E in the Screener is
exactly the Company Details trailing P/E (a test asserts this). Each value keeps its availability
and basis (TTM / annual / balance-sheet date / currency).

- Market cap = latest price × shares outstanding (falls back to the quote's value). For filtering
  and sorting it's converted to USD at the latest Bank of Canada rate (MOCK: 1.35), so CAD and USD
  listings compare. With no rate it's unavailable, never guessed.
- Growth across a loss isn't a percentage. EPS and net income growth are `UNRELIABLE_COMPARISON`
  when the prior fiscal year was ≤ 0. This was fixed at the source (`FmpFundamentalsMapper`), so
  Company Details now behaves the same way. Free cash flow growth (new) follows the same rule and
  also needs matching currencies.
- Trailing P/E and forward P/E (analyst estimate) are separate metrics, never mixed. Forward P/E
  is comparison-only.
- A company that paid no dividend in the trailing year counts as 0% yield and is labelled "None".
  Unknown dividend history is unavailable.
- Stale: the latest reported fiscal period is older than 550 days, or the fundamentals were
  retrieved more than 30 days ago.

Filterable metrics (`ScreenerDefinitions.metrics`):
- Market: market cap (USD), price, volume, 52-week range position.
- Valuation: P/E, P/S, P/B, EV/EBITDA, dividend yield.
- Growth: revenue, EPS, net income and free cash flow growth.
- Profitability: gross, operating and net margin; ROE; ROIC.
- Health: debt/equity, current ratio, interest coverage, operating cash flow, free cash flow.
- Shareholder: payout ratio.
- Company attributes: country, exchange, sector, industry, security type.

Comparison-only: forward P/E, cash, debt, dividend growth, and 1Y/3Y price performance.

Debt/equity, current ratio and interest coverage aren't applied to Financial Services companies
(banks and insurers). The row says so ("…isn't applied to financial services companies") rather
than passing or failing them silently.

## Presets

These are screening shortcuts with visible criteria, not recommendations. All four sort by market
cap so no ordering implies "best".

| Preset | Filters | Period | Missing data |
| --- | --- | --- | --- |
| Growing Companies | revenue growth ≥ 5%, EPS growth ≥ 5%, market cap ≥ $2B | latest FY vs prior FY | prior EPS ≤ 0 → no EPS growth → not included |
| Dividend Stocks | dividend yield ≥ 1.5%, payout ratio ≤ 100% | TTM | unknown history or uncalculable payout → left out |
| Financially Strong | operating cash flow > 0, net margin ≥ 0.1%, debt/equity ≤ 1.5, interest coverage ≥ 3×, current ratio ≥ 1 | TTM; latest balance sheet | leverage criteria not applied to banks/insurers |
| Explore Valuations | P/E 5–25, P/S ≤ 5, EV/EBITDA ≤ 15 | TTM | non-positive earnings/EBITDA → left out |

Users can inspect a preset (ⓘ) and modify any filter afterwards. Draft edits don't search until
"Show results". Changing filters creates a custom screen.

## Backend strategy

- **Routes:**
  - Public: `GET /api/v1/screener/catalog`, `POST /api/v1/screener/search`
    (`ScreenerQuery` → `ScreenerPage`), `GET /api/v1/compare?symbols=A,B[,C,D]`,
    `GET /api/v1/compare/performance?symbols=…&period=1M|3M|1Y|3Y|5Y`.
  - Signed in: `GET|POST /api/v1/me/screens`, `PUT|DELETE /api/v1/me/screens/{id}`.
- **Server-side work:** filtering, sorting and paging run on the server over the whole defined
  universe. The cursor is `offset.fingerprint`; a cursor from a different query gets 400. The
  sorted match list is cached for 5 minutes per query, so later pages are consistent. Missing
  values always sort last. Only the current page's rows get a fresh quote, which bounds the
  per-page calls.
- **MOCK universe:** 23 fixture companies, built through the same fixture pipeline as Company
  Details, with full coverage. No provider, Firebase or AI calls.
- **REAL universe:** defined by one FMP `company-screener` request per exchange, cached 24 h:
  - exchanges `SCREENER_EXCHANGES` (default `NASDAQ,NYSE,TSX`)
  - top `SCREENER_UNIVERSE_LIMIT` per exchange (default 100)
  - minimum `SCREENER_MIN_MARKET_CAP` (default $2B)
  - it supplies price, market cap, volume, sector and industry, so market filters need no
    per-company calls.
- **REAL fundamentals budget:** each company costs about 13 FMP calls. At most
  `SCREENER_FUNDAMENTALS_PER_HOUR` companies (default 25) are loaded per hour, in the background,
  four at a time, and cached for 6 h. Searches never wait for them.
- **Coverage is always reported:** a response states its universe and evaluated count ("Financial
  filters were applied to X of Y companies…"). Results are never presented as full-market matches
  when coverage is partial.
- **Limits and errors:** per-client rate limit `SCREENER_REQUESTS_PER_MINUTE` (default 60) → 429.
  A universe failure → 503. If one company fails in a comparison, the others still load and its
  column explains why.

## Comparison

- **Selection:** 2–4 companies, no duplicates. The selection is shared across Discover, Company
  Details and Compare and kept while navigating.
- **Rows:** Overview, Valuation, Growth, Profitability, Financial health, Shareholder returns
  (incl. 1Y/3Y price performance) and Financial growth (revenue, net income and FCF for the last
  three fiscal years, each in its reporting currency and never converted).
- **Gaps:** N/A cells explain why when tapped.
- **Info buttons:** each metric opens a sheet built from `MetricEducation` (meaning, calculation,
  why, limitations, period).
- **Layout:** a fixed metric-name column. On Android, company columns scroll horizontally together
  under a sticky header. On iOS, the header sits inside the same horizontal scroll (not pinned) so
  alignment is guaranteed. Groups are expandable; rows have a 48 dp minimum height.
- **Observations** (deterministic, `ComparisonEngine`):
  - Only metrics available for at least two companies, on the same basis (TTM vs annual is skipped).
  - When period ends differ by more than 120 days, a caveat names them.
  - Money metrics across currencies get sign-only statements ("Both companies report positive
    free cash flow").
  - Wording says "higher/lower" and "ranges from … to …", never "better" or "winner". Tests
    reject ranking or advice words.
- **AI:** the `ComparisonExplainer` interface exists; the shipped `NoComparisonExplainer` makes
  no call.

## Historical chart methodology

`PerformanceNormalizer`:
- Each company's daily closes are rebased to 100 at the first close in the period (which must be
  within 7 days of the period start, otherwise "history doesn't cover this period").
- All series share one date axis; holidays carry forward up to 5 days.
- Optional split adjustment divides closes before a split by its ratio (tested). Provider history
  (FMP `historical-price-eod/light`) is documented as split-adjusted, so no splits are passed today.
- Labelled **price return** (dividends excluded). Each line is a return in its own trading
  currency.

## Watchlist and Portfolio

Result rows and Company Details reuse the existing watchlist chooser
(`UserWatchlistsRepository` / `WatchlistsModel`) and the existing Portfolio entry flow
(`PortfolioEntryRoute` / `PortfolioEntryScene`). The two stay independent: adding to one never
changes the other.

## Saved screens and StockSteps+

- **Storage:**
  - REAL: Firestore `users/{uid}/meta/screens` (server-only; clients are already denied by
    `firestore.rules`).
  - MOCK: in memory.
  - Only filter definitions and sort are stored, never results.
- **Limits:**
  - Free 3, StockSteps+ 25, via the existing `EntitlementService` (server-enforced) → 403
    `SAVED_SCREEN_LIMIT`.
  - Expired plans keep every existing screen (view, apply, rename, delete); only new ones beyond
    the free limit are blocked.
- **Ownership:** comes from the verified token only (another user gets 404). Names are unique per
  user (409).
- **Client:** `SavedScreensRepository` follows the existing user-data pattern, cached per
  environment and account and cleared on sign-out.
- **Tier:** basic presets and screening are free.

## FMP / Finnhub capability matrix

Status legend:
- **MOCK** = exercised by fixtures in this repo.
- **REAL-used** = already called by the existing REAL backend (Company Details, charts), so the
  plan supports it.
- **Documented** = in the provider's public docs but not verified live in this pass (no paid
  quota was spent).
- **Unknown/plan** = depends on the subscription tier.

| Capability | FMP | Finnhub | Status |
| --- | --- | --- | --- |
| Company screening (`company-screener`) | yes | no (no screener in free tier) | FMP Documented; MOCK via fixture universe |
| Exchange / sector / market-cap filtering | `company-screener` params | — | Documented |
| Financial ratios TTM (`ratios-ttm`, `key-metrics-ttm`) | yes | `stock/metric` (basic financials) | FMP REAL-used; Finnhub Documented, not integrated |
| Revenue / EPS growth (statements) | `income-statement` | — | REAL-used |
| Margins, debt ratios | ratios TTM + statements | partial (`stock/metric`) | REAL-used |
| Dividends | `dividends` | — | REAL-used |
| Historical prices | `historical-price-eod/light` (split-adjusted per docs) | candles (plan) | REAL-used (FMP) |
| Canadian exchanges (TSX `.TO`) | listed in docs | limited | Unknown/plan; MOCK fixtures cover TSX |
| Bulk fundamentals | bulk endpoints exist | — | Requires upgraded plan (not used) |
| Pagination | `limit` only on screener | — | Server-side paging over the cached universe |
| Historical depth | statements ~5 FY used; prices ~5 Y | — | REAL-used |
| Rate limits | plan-based; HTTP 429 observed 2026-10-06 | 60/min free | Mitigated: 24 h universe cache, hourly fundamentals budget, per-client limiter |
| Redistribution / display | per FMP terms (display in-app; no bulk redistribution) | per Finnhub terms | Review before launch |

Confirmed limitations:
- FMP's screener can't filter by most fundamental metrics, so those come from the budgeted index.
- REAL index coverage starts small and grows within the budget.
- TSX and `company-screener` behaviour on the current plan hasn't been verified live.
- Total-return series aren't available, so performance is price return only.

## MOCK scenarios (all 27)

The 27 scenarios from the spec are listed below with what exercises each one.

**Screener**

| Scenario | Exercised by |
| --- | --- |
| Growing / Dividend / Strong / Valuation presets | each preset over the fixture universe |
| Custom filters, multiple filters | the presenter and route tests |
| No matches | `pe 1–2` |
| Missing P/E, negative earnings | RIVN, BB.TO |
| Missing dividend history | CSU.TO |
| Canadian and US companies | RY.TO, ENB.TO, CNR.TO, SHOP.TO, BCE.TO, CSU.TO, BB.TO, TD |
| Mixed currencies | CAD listings; SHOP.TO/CSU.TO report in USD |
| Different reporting periods | AAPL Sep, MSFT Jun, RY.TO Oct, BB.TO Feb |
| Pagination, sorting | paging and sorting tests |
| Stale fundamentals | BB.TO |

**Comparison and charts**

| Scenario | Exercised by |
| --- | --- |
| Compare 2 / compare 4 / remove a company | comparison tests |
| Missing comparison metric | RIVN P/E; banks' current ratio |
| Missing chart history | BB.TO: 8 months of closes |

**Accounts, plans and failures**

| Scenario | Exercised by |
| --- | --- |
| Save a screen, free-tier limit, StockSteps+ limit | saved-screen route tests |
| Provider failure | universe throws → 503 |
| Partial failure | one company's fundamentals fail in a comparison |

## Tests

- `core/.../screener/ScreenerEngineTest.kt`:
  - presets; min/max and combined filters; empty results; missing, negative and NaN values;
    validation; sector, exchange and Canadian filtering; financial-sector exemption
  - sorting with missing values last; cursor paging over the whole universe; record building
    (same facts, USD market cap, staleness, turnaround)
  - observations for 2 and 4 companies; period mismatch; currency sign-only; no ranking words
  - normalization; split adjustment; unavailable history; selection limits and duplicates;
    formatting and education
  - presenter: draft vs applied, presets, load more, sort, reset, retry, compare selection,
    comparison alignment and N/A
- `server/.../screener/ScreenerRoutesTest.kt`:
  - catalog; every preset over the fixtures (values within thresholds); values match Company
    Details; Canadian, stale and mixed-currency cases
  - paging and sorting the whole universe; validation and rate limit; cache reuse; provider
    failure; comparison validation and partial failure; performance
  - saved-screen ownership, limits, rename/delete and expiry; REAL budget and coverage labelling
- `MockModeTest` validates the new fixture files against the public contracts.

UI checks for themes, font scaling, small screens, horizontal scrolling and VoiceOver/TalkBack are
manual (see the handoff). The layouts use theme tokens, 48 dp targets, accessibility labels on rows
and cells, and text alternatives for charts.

## Remaining limitations / next

- REAL `company-screener`, TSX coverage and the budgeted fundamentals index haven't been verified
  live (no quota spent). Run a controlled acceptance test before launch.
- No total-return comparison (dividend-adjusted series not integrated). Forward P/E isn't
  filterable.
- No Compose UI or snapshot tests in the repo for these screens; visual, TalkBack and VoiceOver
  passes are manual follow-ups.
- The comparison selection is in memory, not persisted across app restarts (by design, as it's
  public data and not account-specific).
- Finnhub `stock/metric` isn't integrated; FMP remains the single fundamentals source to keep
  definitions consistent.

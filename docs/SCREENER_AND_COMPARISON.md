# Smart Stock Screener & Stock Comparison

> **Company Comparison Phase 3 — Historical Financial Comparison (2026-10-09, commit "Add Company Comparison Phase 3 historical financial comparison (1Y free, 3Y/5Y StockSteps+) on Android and iOS")** is documented first, then
> **Phase 2 — Guided Metric Interpretation (commit "Add Guided Company Comparison Phase 2 (guided metric interpretation) on Android and iOS")**.
> **Phase 1 review and completion** (commit "Improve Company Comparison Phase 1 for beginners on Android and iOS") follows it. Both
> improve the comparison shipped in `4c65521` in place; the older sections below still describe the Screener and the shared architecture.

### Company Comparison roadmap
| Phase | Scope | Plan | Status |
|---|---|---|---|
| **1. Basic company comparison** | 2–4 companies (2–3 recommended), beginner metrics, price-change chart, deterministic observations | Free | **Implemented** |
| **2. Guided metric interpretation** | what each metric means, what these companies' values show, caveats, comparability, related metrics, research questions, learning summary | Free | **Implemented** |
| **3. Historical financial comparison** | latest four quarters (free); 3Y/5Y annual history, growth, margin, EPS growth, revenue index (StockSteps+) | 1Y free; 3Y/5Y StockSteps+ | **Implemented** |
| 4. Guided comparison checklist | comparison-driven research steps | Free | Not started |
| 5. AI comparison assistant | `ComparisonExplainer` exists as an interface only (`NoComparisonExplainer`) | StockSteps+ | Not started |
No paywall, entitlement check or upgrade prompt is applied to Phases 1–2. Phase 3's 3Y/5Y and advanced metrics are StockSteps+,
enforced on the server; its free 1Y view needs no account.

## Company Comparison — Phase 3: Historical Financial Comparison (1Y free; 3Y/5Y StockSteps+)

Answers "how have these companies' reported results changed over time?" with reported figures only — no rankings, winners, scores,
predictions, causes or interpolated/converted values.

### Free vs StockSteps+
| | Free (guests too) | StockSteps+ (server-verified) |
|---|---|---|
| Range | **1Y** = latest four completed fiscal quarters (not "exactly 365 days") | 1Y, **3Y**, **5Y** (latest three / five completed fiscal years) |
| Metrics | Revenue, Net income, Diluted EPS | + Revenue growth (YoY), Net profit margin, EPS growth, Revenue index |
| Chart, values table, definitions, source/freshness, missing-data reasons | Yes | Yes |
| Observations | Basic (trend per company, losses, sign changes, alignment, currencies, coverage) | + growth/margin ranges, change over the window, cross-company index comparison |
Locked ranges/metrics show "· Plus" and open a preview (benefits; "the latest four quarters stay free"; "in-app purchase isn't
available yet; your StockSteps+ status is in Settings") with **See StockSteps+** (→ Settings, the existing upgrade path) or **Sign In**
for guests. The preview never triggers a request. There is no StockSteps+ trial (Practice's 14-day trial is Practice-only), so
trials don't unlock history.

### UX (Android `ComparisonHistoryUi.kt` → `HistoricalComparisonCard`; iOS `ComparisonHistoryViews.swift` → `HistoricalComparisonView`)
A **Historical comparison** card after "Share price change" on the Compare screen (no new tab): range chips 1Y / 3Y / 5Y, metric chips,
a line chart (one line per company; same component and company order/colours as the price chart; each line has its own colour *and*
dash/marker; missing periods split the line — never joined; a dashed zero line when values cross zero, a 100 line for the index;
tap/drag shows the slot's values), a **values table** (newest first; rows "Latest", "1 quarter earlier"…; each cell shows the
company's own fiscal label and period end — "Q3 FY2026, ended 2026-06-30"; N/A ⓘ explains itself; row notes when periods don't
line up), **What does this mean?** (3 observations, "Show all"), source, notes and plan messages. Loading, error (retry), partial
data, empty metric and preview states. TalkBack/VoiceOver: merged row labels with periods and reasons; chips announce "requires
StockSteps+"; chart has a full spoken description.

### Architecture
`HistoricalComparisonEngine` (core `screener/ComparisonHistory.kt`, deterministic, runs **on the server**) → `ComparisonHistoryService`
(server `screener/ComparisonHistoryService.kt`) → `CompanyFinancialService.getFundamentals(symbol, "quarter"|"annual")` (the statements
Company Details uses; REAL = `FmpFundamentalsLoader`) . Apps: `ComparisonPresenter` (history state, actions `selectHistoryRange`,
`selectHistoryMetric`, `retryHistory`, `dismissHistoryUpsell`) → `ComparisonHistorySource` = `RemoteComparisonHistory` (public route
for guests, `UserApi.compareHistory` when signed in) → formatting only in `HistoryView.of` (`ComparisonHistoryPresentation.kt`).
The presenter refetches when the selection, range, or **account key** ("uid|tier|status" from `AccountDependencies.comparisonAccount`)
changes; results are cached per selection by range+account (switching back doesn't refetch; a sign-out or plan change does);
`collectLatest` cancels obsolete requests; history for a previous selection is cleared before the new one loads. Metric switching
never requests (all metrics for a range arrive in one response).

### API
- `GET /api/v1/compare/history?symbols=A,B[,C,D]&range=1Y` — **public, free view only**. Never reads `Authorization`; `range=3Y|5Y` →
  401 `SIGN_IN_REQUIRED`.
- `GET /api/v1/me/compare/history?symbols=…&range=1Y|3Y|5Y` — signed in (Firebase ID token; MOCK `mock-user:<uid>`).
- Response `HistoricalComparison`: `range`, `granularity` (QUARTERLY|ANNUAL), `companies` (symbol, name, latest reporting currency,
  per-company `error`, `retrievedAt`), `metrics[]` (`metric`, `series[]` of `points` oldest→newest: `label` "Q3 FY2026"/"FY2025",
  `fiscalYear`, `fiscalPeriod`, `periodEnd`, `currency`, `value`, `availability`, `note`; index series carry `base` "FY2021 = 100"),
  `periods[]` (per slot: each company's label and end date + `alignment` ALIGNED|CLOSE|DIFFERENT|UNKNOWN + note), `insights`, `notes`,
  `access` (`plus`, `signedIn`, allowed `ranges` and `metrics`, `message`), `source`, `asOf`, `sampleData`. Free responses contain
  only the three free metrics — premium series are never computed for them.
- Errors (`ApiError`): 400 `INVALID_COMPARISON` / `INVALID_SYMBOL` (shared `parseComparisonSymbols`), 400 `INVALID_HISTORY_RANGE`,
  401 `SIGN_IN_REQUIRED`, 403 `PLUS_REQUIRED` (existing code), 503 `ENTITLEMENT_UNAVAILABLE` (existing), 503
  `HISTORICAL_DATA_UNAVAILABLE` (every company failed), 429 `RATE_LIMITED`. One company failing/timing out (20 s) → that company's
  `error`, the rest still load.

### Entitlement enforcement (server)
1. Authenticate (`user(auth)`), 2. validate symbols and range, 3. `EntitlementService.get(uid)` from the stored record — active,
canceled-but-paid and grace = StockSteps+; expired, payment-failed, none = free; debug records only in MOCK; 4. 3Y/5Y without
StockSteps+ → 403 **before any statement is loaded** (tested with a counting loader); unreadable plan → 503 for 3Y/5Y, free view for 1Y.
No client flag, header or query (`plus=true`, `X-StockSteps-Plan`…) is read (tested). Statements are cached per symbol and frequency
(public data, 6 h); responses are assembled per request for the caller's tier and never cached, so premium content can't reach
another account or a free caller; an expired plan is refused on the next request.
**Overlapping route (unchanged, documented):** the existing free Company Details endpoint (`companyFinancialRoutes`, statements for one
company, annual/quarterly) still returns raw reported statements. Phase 3 protects the comparison's multi-year view and derived
analytics; restricting Company Details would break an existing free feature and needs a product decision.

### Formulas and rules (`HistoricalComparisonEngine`)
- Revenue = income-statement `revenue`; Net income = FMP income-statement `netIncome` (the provider's net income attributable to the
  company; `bottomLineNetIncome` is not used); Diluted EPS = `epsDiluted` only (basic EPS is never substituted; missing → N/A).
- Revenue growth % = (current − same fiscal period a year earlier) ÷ |earlier| × 100 — quarterly compares Q*n* FY*y* with Q*n* FY*y−1*
  (never Q1 vs Q4); annual compares consecutive fiscal years. Earlier value missing → `INSUFFICIENT_HISTORY`; zero/negative →
  `NON_POSITIVE_DENOMINATOR`; currency changed → `PERIOD_MISMATCH`.
- Net profit margin % = net income ÷ revenue × 100, same period; revenue ≤ 0 → not meaningful; a loss is a negative margin.
- EPS growth % only when the earlier EPS > 0; otherwise `UNRELIABLE_COMPARISON` with words ("from −0.50 in FY2024 to 0.20 (from a loss
  to a profit)").
- Revenue index = revenue ÷ revenue in the company's first displayed period × 100 (base must be positive; points in another currency
  than the base aren't indexed). Company-specific base periods are labelled ("FY2021 = 100"); it's "indexed financial performance,
  not an investment return". Net income and EPS are never indexed.
- Periods: completed periods only (end date ≤ today); the window is the expected sequence ending at each company's latest period, so a
  missing quarter/year is an explicit empty slot (never interpolated). Quarterly and annual points are never mixed.
- **Alignment**: companies are aligned by position from their own latest period (n-th most recent fiscal period); each cell keeps its
  own label and end date. Per slot: ≤ 31 days apart ALIGNED, ≤ 92 CLOSE ("different months"), more DIFFERENT, missing dates UNKNOWN —
  disclosed, never shifted. Restatements: the latest provider values are shown (no revision history).
- **Currencies**: each point keeps its reporting currency; amounts are never converted (no historical FX is invented); explicit
  US$/C$ markers when companies differ; trend statements never compare amounts across a currency change.
- Observations: annual "increased/decreased in each displayed fiscal year" (only with all periods present), else "higher/lower in X
  than Y"; quarterly comparisons carry a seasonality reminder and no "% change across quarters"; losses counted; sign changes named.

### MOCK (fixtures only)
Quarterly fixtures: AAPL, MSFT, NVDA, TD, TSLA; annual fixtures for most companies; other companies get labelled MOCK sample statements.
New fictional **SSHC.TO** ("StockSteps Sample History Co (fictional)", TSX, CAD, fiscal year ends March; `keepMissing`, in search):
missing Q3 FY2026, missing diluted EPS (Q2 FY2026), zero revenue (Q1 FY2026, the baseline for Q1 FY2027 growth), reporting currency
USD → CAD (FY2025 / Q3 FY2025), losses turning to a profit, improving margin. Scenarios covered by `ComparisonHistoryRoutesTest`: two US
(AAPL/MSFT), two Canadian (RY.TO/CNR.TO 3Y; TD/SSHC.TO 1Y), mixed (AAPL/RY.TO), three companies, different fiscal calendars, complete
4Q / 3Y / 5Y, missing quarter, missing diluted EPS, negative net income (RIVN, SSHC.TO), zero revenue, negative EPS baseline, revenue
decline (BB.TO), improving margin, currency change, partial provider failure and timeout, free 1Y / 3Y / 5Y, StockSteps+, expired,
payment-failed, canceled, grace, entitlement unavailable, no/invalid token, spoofed flags, data for two of three companies.

### REAL
Uses existing FMP statement requests through `CompanyFinancialService` (quarterly: 8 income/balance/cash-flow rows = 4 shown + 4 for
year-over-year; annual: 6 rows = 5 shown + 1 for growth); the quarterly load is a full fundamentals load (~13 FMP calls per company on a
cold cache, then cached). **Not live-verified** (no authorized REAL calls): TSX quarterly coverage, `netIncome` semantics per FMP
plan, fiscal labels for non-calendar companies, statement freshness.

### Tests
core `ComparisonHistoryTest` (15: windows, gaps, 3Y/5Y, same-quarter YoY, zero/negative/missing/currency baselines, margin, EPS
turnarounds, missing diluted EPS, index, tier filtering, alignment, currencies, observations, failures, formatting) and
`ComparisonHistoryPresenterTest` (4: free preview without requests, guest sign-in, StockSteps+ ranges/metrics/cache/plan expiry,
selection change/retry); server `ComparisonHistoryRoutesTest` (10, routes called directly).
Validation: `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`: core JVM 389, server 336, shared Android host 55, shared iOS 49 — 0 failures; `assembleDebug` succeeded; iOS `xcodebuild` (default DerivedData) BUILD SUCCEEDED. **Not verified:** `:core:iosSimulatorArm64Test` failed with `java.io.EOFException` from the simulator test runner (infrastructure, not an assertion) before writing results, so the 19 new core history tests have run on JVM only; a rerun was stopped. No live MOCK curl pass of `/api/v1/compare/history`, no device walkthrough, no REAL calls.

### Known limitations
No billing (StockSteps+ comes from MOCK debug plans or a server-written record); English only; no quarterly 3Y/5Y view (annual by
design); no restatement history or filing dates in the response; Company Details still exposes raw statements for free (see above);
no device walkthrough or UI automation; REAL not live-verified. Phase 4 (guided checklist, free) and Phase 5 (AI assistant,
StockSteps+) are not started — Phase 3 uses no AI.

## Company Comparison — Phase 2: Guided Metric Interpretation (free)

Phase 1 answers "what are the differences?". Phase 2 answers "what do they mean, what don't they show, and what should I look at
next?" — without rankings, winners, scores, buy/sell/hold language, price targets, predictions, universal thresholds, invented
industry averages or AI.

### What users see (Android `ComparisonScreen.kt` + `GuidedMetricExplanation.kt`; iOS `CompareStocksScreen`, `GuidedMetricExplanationView`, `ComparisonLearningSummaryView` in `ScreenerScenes.swift`)
- **What can we learn from this comparison?** (above the table): 2–3 grounded lines — industry context (different sectors, same
  industry, or sector not reported) and then growth, profitability, valuation or financial-health observations that the data
  supports. Each metric line has **Explain this**, which opens and scrolls to that metric. Partial failures and sparse data are said
  plainly ("Missing values are never filled in or estimated").
- Each beginner metric row (market cap, revenue growth latest quarter / fiscal year, net profit margin, debt to equity, P/E, price to
  sales, dividend yield) has **Explain / Hide** (48 dp, state announced). The explanation opens **inline below the values**, so the
  company columns stay aligned. Other rows (sector, "More metrics") keep the Phase 1 info sheet.
- The explanation card (concise by default): **title** ("Understanding P/E") + comparability tag (*Comparable*, *Compare with care*,
  *Not directly comparable*, *Not enough data*); **What it measures** (A); **Your comparison** (C: the actual values and what the
  difference means, qualified); **What to keep in mind** (D: the two most relevant caveats, data-specific first); **Explore next** (E:
  related-metric chips that open and scroll to that metric — across groups, and into "More metrics" for extra metrics — plus one
  research question). **Learn more** adds **Why it matters** (B), **How it's calculated**, more caveats/education and all research
  questions. **Close** collapses it. Selections, chart period and data are never touched by these interactions (no refetch).
- Accessibility: headings for the card and its parts, comparability announced, Explain/Learn more expose expanded/collapsed state,
  related chips are labelled "Open …", text scales with Dynamic Type / font size, scrolling to a related metric respects Reduce Motion
  on iOS. Theme tokens only (dark/light/system).

### Architecture
`ComparisonResponse` (unchanged endpoint) → `ComparisonPresenter.build` → **`ComparisonInterpretationEngine.interpret(companies, fx)`**
(core `screener/ComparisonInterpretation.kt`, pure and deterministic, shared by Android and iOS via `IosScreenerClient`) →
`ComparisonUiState.guides` (metric id → `MetricInterpretation`), `insights` (`ComparisonInsight`), `industryNote`. UI state lives in the
presenter: `expanded`, `deeper`, `showMore` (moved from screen state so related links can open "More metrics"), `focus` (one-shot
scroll request). Actions: `toggleExplanation`, `toggleDeeper`, `openRelated`, `clearFocus`, `collapseExplanations`, `toggleMore`.
Education text lives in one place (`MetricGuides`, internal; ready for localization). Row labels are shared
(`ComparisonInterpretationEngine.ROW_LABELS`). No new endpoint, no AI, no extra provider call.

`MetricInterpretation`: `metricId`, `label`, `title`, `definition` (A), `whyItMatters` (B), `comparability`, `observation` + `meaning`
(C), `caveats` (D; `keyCaveats` = first two), `learnMore`, `calculation` (from `MetricEducation`), `related` (`RelatedMetric(id, label,
guided)`) + `questions` (E), `headline` (short form for the summary; null when nothing can be stated).

### API change (additive, backward compatible)
`GET /api/v1/compare` gains `fx: [{from, to, rate, date, source}]` — the CAD→USD rate behind the converted market caps (Bank of Canada in
REAL, "fixed sample rate" 1/1.35 in MOCK), only when a CAD listing is compared. Omitted from JSON when empty; older apps ignore it.
`Application.kt` now shares one FX source between the conversion and this disclosure (the Bank of Canada client is created once, so
its 6 h cache is actually reused).

### Interpretation rules (deterministic)
1. **Readings**: a company contributes a value only when it is `AVAILABLE` and finite; `NO_DIVIDEND` is a real 0% ("paid none");
   anything else is left out with its Phase 1 reason (`MetricFormatter.explanation`: loss → no P/E, equity ≤ 0 → no debt to equity,
   unknown dividend history ≠ no dividend). Values are quoted exactly as the table displays them (`MetricFormatter.cell`).
2. **Comparability** (per metric):
   - fewer than two readings → `INSUFFICIENT_DATA` ("Only X has a value for …"; nothing invented);
   - all companies left out because of their industry (debt to equity for banks/insurers/financials) → `NOT_COMPARABLE`;
   - different kinds of period (TTM vs fiscal year vs quarter) → `NOT_COMPARABLE` (values listed with their periods, no relative statement);
   - otherwise `COMPARABLE`, downgraded to `COMPARABLE_WITH_CAVEATS` when: a period is unreported; period ends differ by month or by
     more than 120 days (`PERIOD_TOLERANCE_DAYS`); statements are stale (> 18 months); sectors differ or a sector is unknown
     (sector-sensitive metrics); a company was left out; growth rates are reported in different currencies; market caps needed conversion.
3. **Currencies**: ratios and percentages are compared across CAD/USD (currency cancels out; said in "Learn more"). Market caps in one
   currency are compared directly; mixed currencies are compared only through the server's converted USD values, with the rate, its
   date and source disclosed ("1 CAD = 0.7407 USD (Bank of Canada, YYYY-MM-DD)") and both amounts shown ("C$245.0B ≈ US$181.5B");
   with no conversion available → `NOT_COMPARABLE`. No silent FX.
4. **Statements**: two companies → "A has a higher X than B (a vs b)" plus a qualified meaning; three or four → "X ranges from low (L)
   to high (H). Values: …" listed in **selection order** (never a ranking). Equal at display precision → "the same … as displayed";
   very close → "similar … says little on its own" (percentages: < 0.5 points and ≤ 10% relative; ratios/amounts: ≤ 5% relative —
   presentation thresholds, not judgements). Growth wording follows the sign (grew vs declined). Negative net margin → "reported a net
   loss … doesn't mean a company will stay unprofitable". Dividends: payers vs non-payers described as such.
5. **No universal thresholds**: the same sentence is produced for P/E 8 vs 12 and 80 vs 120 (tested); no "cheap", "expensive", "safe",
   "too much debt" levels.
6. **Summary**: up to 3 lines — data problems, industry context, then the first available headline from growth (latest quarter, else
   fiscal year), profitability, valuation, financial health. Headlines exist only when the values support a statement; "Compare with
   care (see Explain)" is appended when caveats apply.

### Metric guide (formulas from `MetricEducation`)
| Metric | What it measures | Key cautions taught | Related |
|---|---|---|---|
| Market cap | price × shares outstanding; current market value of the shares | not revenue/profit/cash; bigger ≠ better; currency conversion | revenue growth, P/S, P/E |
| P/E (trailing) | price ÷ TTM diluted EPS | low can reflect risk/slow growth/peak earnings; high can reflect growth expectations; loss → not meaningful; cross-industry care | revenue growth, net margin, P/S |
| Price to sales | market cap ÷ TTM revenue | sales aren't profit; margins differ; low ≠ undervalued; revenue quality | net margin, revenue growth, P/E |
| Revenue growth (FY / latest quarter) | change vs prior fiscal year / same quarter a year earlier | faster ≠ better (acquisitions, prices, currency, base effect); one period ≠ trend; quarters can end in different months | net margin, the other growth row, P/E |
| Net profit margin | net income ÷ revenue | industry structure; one-time items; a loss isn't permanent; same kind of period | P/S, revenue growth, P/E |
| Debt to equity | total debt ÷ shareholders' equity | higher ≠ dangerous; equity ≤ 0 → not meaningful; provider definitions differ; banks/insurers not comparable | interest coverage, current ratio, net margin |
| Dividend yield | trailing-year dividends per share ÷ price | not guaranteed; can rise because the price fell; many companies reinvest; industry habits | payout ratio, net margin, debt to equity |

### Industry caveats (qualitative only — no benchmarks)
Business model from provider sector/industry (`IndustryKind`): BANK, INSURER, OTHER_FINANCIAL, REIT, UTILITY, GENERAL, UNKNOWN.
Different sectors → industry note + per-metric caveats (cross-industry comparisons are never blocked). Banks/insurers/financials: debt to
equity not compared (consistent with `notApplicableSectors`); margins and P/S need extra care when mixed with other industries; bank P/E
context. REITs: FFO (not shown) vs earnings for P/E; high payouts by design; property borrowing. Utilities: infrastructure debt; steady
dividends. Same industry → "usually makes their ratios easier to compare". No industry or sector averages are shown anywhere.

### MOCK scenarios (fixtures only; `ComparisonInterpretationMockTest`)
| Scenario | Symbols |
|---|---|
| Same industry, cross-border, CAD vs USD, banks (debt to equity not comparable), FX disclosed | RY.TO, TD |
| Different industries (tech vs bank), fiscal Sep vs Dec | AAPL, JPM |
| Three companies, same industry, a non-payer | AMD, NVDA, INTC |
| Four companies, three sectors incl. a REIT and a bank, mixed currencies, periods far apart | AAPL, MSFT, RY.TO, GIPR |
| Negative EPS (no P/E), negative net margin, non-payer | KO, RIVN |
| Unknown dividend history, missing price to sales | CSU.TO, LONGN |
| Stale statements, loss, Feb fiscal year | BB.TO, MSFT |
| Missing sector; equal displayed values (P/S 10.6× both) | LUCY, AAPL |
| Partially available data / missing P/E (fundamentals provider failure) | AAPL, MSFT with MSFT failing |
| Different fiscal calendars | AAPL (Sep) vs MSFT (Jun) |
Zero/negative equity, slightly different values, missing period metadata and missing reporting dates are covered by core unit tests
(no fixture has them; the FMP adapter test covers negative equity from provider JSON).

### REAL
Works from the same `/api/v1/compare` data FMP/Finnhub already provide — **no additional provider calls** (the only new server work is
reading the Bank of Canada rate already used for market-cap conversion). Not live-verified (no authorized REAL run): sector/industry
labels from FMP for TSX names, period metadata completeness, and the Bank of Canada date shown.

### Tests
- core `ComparisonInterpretationTest` (18): five sections for every metric, two-company wording, no thresholds (8 vs 12 ≡ 80 vs 120),
  equal / slightly different / small-but-relatively-large values, 3–4 companies in selection order, growth sign wording, negative EPS,
  zero/negative equity, missing vs no dividend, negative margin, TTM vs annual, different months, far-apart periods, missing period
  metadata, stale data, market cap with/without FX, ratios across currencies, cross-industry, same industry, missing sector, REIT,
  no invented benchmarks, summary limits, every summary number is a displayed value, determinism and input-order independence,
  partial failure. `ComparisonGuidancePresenterTest` (4): open/collapse/deepen without refetching or touching the selection, related
  metric switches group and opens "More metrics", changing companies recomputes guides and keeps open explanations, load failure → retry.
- server `ComparisonInterpretationMockTest` (5): the engine over real MOCK `/api/v1/compare` responses (scenarios above), `fx` field
  serialized and decoded, no FX lookup for USD-only comparisons, every quoted number is a displayed value, no ranking/advice words,
  determinism across requests.
- No Compose UI / XCUITest framework exists: rendering, TalkBack/VoiceOver, large text and dark/light need a manual device pass.
- Validation (2026-10-08): `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug` → BUILD SUCCESSFUL: core JVM 370, core iOS 370, server 326, shared Android host 55, shared iOS 49 — 0 failures; APK built (this also completes the Phase 1 full-suite verification that `9bbf586` lacked). A first run had 2 intermittent core JVM presenter failures (duplicate 1Y price-history request; a timeout) caused by the unguarded request cache on `Dispatchers.Default`; fixed with the mutex, then the full suite passed and both presenter test classes passed 5 consecutive `--rerun`s. Server tests re-run after the final FX-date change: 326, 0 failures. iOS `xcodebuild` (default DerivedData) BUILD SUCCEEDED on the final code. Live MOCK (`:server:runMock`): `/api/v1/compare?symbols=RY.TO,AAPL` returns `fx` = 1 CAD = 0.7407 USD, "fixed sample rate", 2026-10-07 (pinned MOCK clock); USD-only comparisons omit `fx`. No REAL provider, AI or Firebase calls; no device/simulator UI walkthrough.

### Known limitations
- Explanations are English only (strings centralized in `MetricGuides` for later localization).
- "Similar" thresholds are presentation choices (documented above), not financial judgements.
- Industry context is qualitative; no authorized benchmark source exists, so no averages are shown.
- Debt to equity with mixed bases (provider TTM ratio vs calculated annual) is `NOT_COMPARABLE`, as in Phase 1 observations.
- The FX disclosure covers CAD only (the only converted currency today).
- No manual device walkthrough yet; REAL not live-verified.

## Company Comparison — Phase 1 (free)

### Entry points (no new tab)
Markets → **Compare Companies** tile; Company Details → **Compare** (adds that company); Discover Stocks (Screener) result
rows → Compare; **Watchlist → "Compare these companies"** (first three companies of the shown list; new). The selection
(`SharedComparisonSelection`) is shared by all of them for the app session.

### UI behaviour (Android Compose `ComparisonScreen.kt`, iOS `CompareStocksScreen` in `ScreenerScenes.swift`)
- Header "Compare Companies" + educational subtitle. **Companies card**: name, ticker and listing (exchange · currency) per
  company with **Replace** and **Remove** (in place, no restart), **Add a company** (existing stock search, debounced), and the
  hint "two or three are easiest to read on a phone" (four still supported; a fourth shows a gentler hint).
- Empty (0–1 companies): three **examples** (Apple vs Microsoft; Royal Bank vs TD, cross-border; Coca-Cola vs Rivian, dividend
  payer vs loss-making non-payer) plus Discover Stocks.
- **Table without horizontal scrolling**: each metric's label, period and info button sit above equal-width company columns;
  the company header is pinned. Three companies fit a 360 dp phone; four are tight but readable (values wrap to two lines).
- Groups: **Overview** (market cap, sector, industry, listing) → **Growth** → **Profitability** → **Financial Health** →
  **Valuation** → **Shareholder Returns**. Everything else is under **More metrics (N)** (collapsed): more valuation, growth,
  profitability and health measures, payout ratio, 1Y/3Y price change, fiscal-year figures.
- Every unavailable value shows "N/A ⓘ"; tapping it gives the company-specific reason (source note first, then a
  metric-aware reason: loss → no P/E, zero/negative equity → no debt to equity, unknown dividend history ≠ no dividend).
- Periods: when companies' values cover different periods (e.g. fiscal years ending Sep vs Oct), the row says "Periods differ
  by company" and each value shows its own period ("FY ended Sep 2025"). Latest-quarter growth always shows the quarters
  compared ("Q3 FY2026 vs Q3 FY2025").
- Currencies: when compared companies use different currencies every amount gets an explicit marker (US$ / C$); market cap is
  shown in each listing's own currency with an approximate converted USD amount underneath.
- **Share price change** card (after the table): period chips, normalized chart with a dashed "Start (100)" line, each company's
  change and any note, and plain notes (price only, not total return, not business performance, past ≠ future).
- **What the numbers show** (deterministic observations), then **Sources and notes** (provider, retrieval dates, fiscal-year
  differences, currencies, prepared-at time) and the education disclaimer.
- Loading, error (retry), partial company failure ("Partial data" in the header, reason in its cells), empty search, search
  failure and chart failure (retry) states. 48 dp targets, merged accessibility labels per row (includes periods and reasons).

### Metric definitions (Phase 1 core)
| Metric | Formula | Period | Unavailable when |
|---|---|---|---|
| Market cap | latest price × shares outstanding (quote value as fallback) | latest quote; listing currency (≈USD shown, Bank of Canada rate; MOCK 1.35) | no price/shares |
| Sector / industry | provider profile | — | ETFs or not reported |
| Revenue growth — latest quarter | (latest reported quarter revenue − same fiscal quarter a year earlier) ÷ that earlier revenue | latest reported fiscal quarter (Earnings Results) | prior-year quarter missing, different currency, fiscal-calendar change, zero/negative base |
| Revenue growth — fiscal year | (FY revenue − prior FY revenue) ÷ prior FY revenue | latest fiscal year vs prior; same reporting currency | currency changed, missing year |
| Net profit margin | net income ÷ revenue | provider TTM ratio, else latest fiscal year (each value says which) | missing |
| Debt to equity | total debt ÷ total shareholders' equity | latest balance sheet (provider TTM ratio when available) | equity ≤ 0 → not meaningful; not comparable for banks/insurers (noted) |
| P/E (trailing) | price ÷ trailing-twelve-month diluted EPS | TTM | earnings ≤ 0 → not meaningful (never negative/∞) |
| Price to sales | market cap ÷ TTM revenue | TTM | ratio ≤ 0 or missing |
| Dividend yield | trailing-year dividends per share ÷ current price | trailing year | "None" = paid nothing; N/A = history unknown. Not guaranteed future income |
Values are server-calculated; apps only format. Non-finite values are never shown (N/A). Missing ≠ zero.

### Price chart (`PerformanceNormalizer.compute`)
Common base date = the latest of the companies' first closes in the period; every line = 100 there. Dates sorted and
deduplicated. A closed market carries its last close for ≤ 5 days (disclosed); longer gaps stay empty; no closes are
invented. A company whose history starts after the period begins is excluded with a reason; one whose history ends early keeps
its line but its change is measured to its last close (note + `lastDate`). FMP closes are split-adjusted; dividends are not
included (price return, never called total return). Each line is in its own trading currency (disclosed). The chart and the
1Y/3Y price-change rows share one request per period (no duplicate fetch).

### Architecture (MOCK and REAL)
`ComparisonPresenter` (core, shared by Android and iOS via `IosScreenerClient`) → `RemoteScreenerDataSource` → Ktor
`GET /api/v1/compare` / `GET /api/v1/compare/performance` (`ScreenerService`) → `StockService` (quote, profile),
`CompanyFinancialService.getFundamentals(annual)` (cached 6 h), `EarningsService.latestResults` for latest-quarter growth (cached
6 h; same rules as Earnings Results), `PriceChartService.getDailyCloses`. MOCK: fixtures only (`FixtureMarketDataSource`, earnings
fixtures; no provider, AI or Firebase calls; values labelled sample). REAL: FMP for quotes, profiles, statements, ratios and
prices; Finnhub (default earnings source) for quarterly results. The FMP adapter now marks P/E with a loss and debt to equity
with zero/negative equity as `NON_POSITIVE_DENOMINATOR` with a note (previously "missing").

### API (unchanged paths; additive fields only)
- `GET /api/v1/compare?symbols=A,B[,C,D]` → `ComparisonResponse`. 2–4 exchange-qualified symbols (`TD` ≠ `TD.TO`); duplicates
  (case-insensitive), empty or malformed → 400; per-client rate limit → 429. Each company loads independently: a failure gives
  that column an `error` and the others still load. New: `metrics.quarterRevenueGrowth` per company (with basis and the quarters
  compared in `note`), provenance and retrieval notes.
- `GET /api/v1/compare/performance?symbols=…&period=1M|3M|1Y|3Y|5Y` → `PerformanceComparison`. New: `baseDate`, per-series
  `note` and `lastDate`.

### MOCK scenarios (Phase 1)
US + Canadian and cross-currency (RY.TO CAD vs AAPL USD; Royal Bank vs TD example); negative earnings (RIVN, BB.TO: no P/E);
missing metrics (CSU.TO unknown dividend history, LONGN price to sales, TSLA market cap); non-dividend payers (RIVN, SHOP.TO,
TSLA); different fiscal calendars (AAPL Sep, MSFT Jun, RY.TO Oct, BB.TO Feb); partial price history (BB.TO, 8 months); provider
errors (fundamentals failure for one company; quarterly-results provider failure → that row "temporarily unavailable").

### Tests
- core `ScreenerEngineTest.kt`: common base date across calendars, early-ending history, no invented points, replace/set
  selection (position kept, duplicates, limit, cross-exchange identity); `ComparisonPhase1Test`: metric-specific reasons,
  NaN/∞ never shown, explicit currencies, presenter groups/periods/currency details/one request per chart period/replace/examples/
  max count, observations (no ranking words, TTM vs annual skipped, quarterly caveat).
- server `ScreenerRoutesTest.kt`: Phase 1 MOCK scenarios over fixtures, latest-quarter growth equals Earnings Results,
  quarterly-provider failure is partial, chart base date and notes, empty/malformed/duplicate/same-ticker-two-exchanges;
  `repositoryImpl/ComparisonMetricsAdapterTest.kt`: REAL FMP adapter with stubbed provider JSON (negative equity, losses,
  calculated D/E, missing vs not meaningful).
- No Compose UI / XCUITest framework exists: layouts, dark/light, large text and screen readers need a manual device pass.
- Validation at commit time: Verified: core screener/comparison tests (`:core:jvmTest --tests org.example.stocksteps.screener.*`, 30 passed); server screener + FMP adapter tests (`:server:test --tests org.example.stocksteps.screener.* --tests org.example.stocksteps.repositoryImpl.*`, all passed); `:app:shared:compileAndroidMain` succeeded; iOS `xcodebuild` BUILD SUCCEEDED. **Not run to completion:** the full suite (core iOS, all server tests, shared Android/iOS host tests) and `:app:androidApp:assembleDebug` were started but stopped before finishing; no live MOCK curl pass; no REAL calls.

### Known limitations / production verification still needed
- REAL comparison has not been live-verified in this pass (no paid provider calls were made): FMP fundamentals/ratios for TSX
  listings, Finnhub quarterly coverage for Canadian companies, and the latest-quarter growth for REAL symbols need an
  authorized acceptance run.
- Each comparison loads full annual fundamentals per company in REAL (about 13 FMP calls, cached 6 h) — the existing design;
  a lighter metrics-only path would reduce cost.
- Market-cap USD conversion uses the latest rate only; other amounts aren't converted (by design).
- No total-return series; the chart is price change only. (Per-metric industry context is now provided by Phase 2.)
- The selection isn't persisted across app restarts; manual UI/accessibility checks pending.

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

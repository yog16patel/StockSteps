# StockSteps — Claude Code session guide

Read this first, then `docs/project-status.md` (feature status, decisions, pending work) and
`PROJECT_HANDOFF.md` (milestone log + validation history). `AGENTS.md` has the same core rules.
Always verify against the code and `git status`/`git log` — docs can lag the repository.

**Review workflow (mandatory):** Before planning, implementing, verifying, or handing off a development phase, read `docs/AI_REVIEW_WORKFLOW.md`.
Follow its evidence collection, review-package, and approval requirements. At the end of an implementation phase, generate the appropriate compact
ChatGPT review package unless the owner explicitly opts out.

## What StockSteps is

A **beginner-focused** investing education app (Android + iOS) with a Kotlin Ktor backend.
Philosophy: **NUMBER → CONTEXT → EXPLANATION → EDUCATION**. Journey: Learn → Research → Practice →
Understand. It is not a trading terminal and never gives buy/sell/hold advice, scores, ratings or
price predictions. Advanced analytics belong to a separate product, **PortIQX**.

## Current task (as of 2026-10-15, search logos committed locally)

Phase 5D.5.4A ("Refine Company Details for Global UI Refinement Phase 5D.5.4A with a borderless chart and day range, one action row, a pinned in-page section bar, a What it does row, a beginner-first section order and borderless sections on Android and iOS") is committed and pushed (`0fbce92`) and closed. The plan for all of 5D.5.4 is `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4_PLAN.md`
(approved D-E1 to D-B1). Report: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4A.md`; review package: `docs/reviews/phase5d_5_4a/`.
Phase 5D.5.4 (Company Details, A–D) is pushed through `0c5c4c4`; the owner runs the TalkBack/VoiceOver pass manually (`docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4D.md` §9).
Phase 5D.5.5 Company Search: the plan (`docs/COMPANY_SEARCH_REFINEMENT_PHASE5D_5_5_PLAN.md`), 5D.5.5B (`08723a6`) and 5D.5.5C (`1d90195`) are pushed. The search-logo addendum ("Show company logos in Company Search results on Android and iOS with a server-built logo URL per result and a ticker-initials fallback, without extra provider calls") is committed locally, **not pushed** (report `docs/COMPANY_SEARCH_REFINEMENT_PHASE5D_5_5C.md` §9).
**Start with `docs/PROJECT_HANDOFF.md` §SEARCH-LOGOS-2026-10-15.**
Next: push on approval; then 5D.5.5D (full device matrix, screen readers, polish items in the 5D.5.5C report §6). Owner to confirm FMP logo display rights.
5D.5.4E (verification).
Tracked separately:
- the `PortfolioSessionStateTest` fix (uncommitted, worktree `charming-hodgkin-98e850`);
- the intermittent `UserDataRepositoriesTest` on core iOS.
Still open:
- iOS Earnings Details after the owner signs in;
- the iOS Home AX5 heading break and history navigation;
- 5D.5.3 follow-ups: TalkBack/VoiceOver, iOS signed-in regression, Android "Key Ratios" contextual learning (planned in 5D.5.4C).
The iPhone 16 simulator app is in guest mode. Disk space is tight: Xcode's default DerivedData was cleared on 2026-10-15.

Earlier: Contextual learning closed with 5D.5.3 (`3b7a7f5`, `73a6c23`), see §PHASE-5D-5-3-CLOSED-2026-10-15 in the handoff.
Earlier: Learn closed with 5D.5.2 (`057d6e6`, `0730773`, `600c295`), see §PHASE-5D-5-2-CLOSED-2026-10-15 in the handoff.
Earlier: Daily Brief closed with 5D.5.1c (`9e88678`), see §PHASE-5D-5-1C-2026-10-14 in the handoff.
Earlier: 5D.5.1b (`5bfe273`), see §PHASE-5D-5-1B-2026-10-14 in the handoff.
Earlier: Phase 5D.5.1 (`38002c0`), see §PHASE-5D-5-1-2026-10-14 in the handoff.

Earlier (Earnings 5D.2–5D.4.3, all pushed through `7326e62`): see `docs/PROJECT_HANDOFF.md` §SESSION-END-2026-10-14-EARNINGS.

Earlier (Portfolio Phase E.2):

**Phase E.2** ("Add Portfolio Phase E.2 with a readable iOS account title, the trade currency shown with the amounts, and a Session expired banner
with Sign in again on Android and iOS") is committed and pushed, and its verification is recorded (all suites pass; device checks mostly PASS). Closed as PASS WITH CONDITIONS (report §5): the
on-device currency round-trip and dark theme remain. **Next: Phase 5D** — Earnings audit done (`docs/GLOBAL_UI_REFINEMENT_PHASE5D_AUDIT.md`); 5D.1 (+1A/1B currency) committed; next 5D.2 (design-system alignment) after owner approval. Report: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_E_2.md`.

Earlier context (Phase E.1):

HEAD: "Record the Ubuntu MOCK backend deployment and Phase 3B Android and iOS connectivity verification against the LAN backend" (`863d756`, backend session). Portfolio Phase E = `3d91ef3`.

**Phase E.1**: the session-ended message fix (both platform auth adapters) and the allocation-denominator proposal are committed and pushed ("Fix Portfolio Phase E.1 session-refresh messaging so an ended session says to sign in again instead of blaming the password, and record the allocation-denominator proposal and blocked screen-reader checks").
Verdict: **NOT READY FOR SIGN-OFF**. TalkBack journeys and iOS signed-in workflows need the owner; VoiceOver and Accessibility Inspector are
blocked. See `docs/PORTFOLIO_UI_REFINEMENT_PHASE_E_1.md`.

Read `docs/PROJECT_HANDOFF.md` §PHASE-E-1-2026-10-13 first (it includes the resume steps).

Next:
1. Finish the TalkBack and iOS signed-in passes with the owner.
2. Owner decision on `docs/PORTFOLIO_ALLOCATION_DENOMINATOR_PROPOSAL.md` (separate phase).
3. Phase 5D after Phase E closure.

The backend session works in this checkout too: stage by explicit path. Commit or push only when the owner asks.

Portfolio conventions (permanent):
- **Forms.** Accounts are chosen and submitted by **id** (`PortfolioFormRules.accountOptions` / `resolveAccountId`). Field visibility, order and labels
  come from `PortfolioFormRules`; copy from `PortfolioDisplay`; never show raw enum names.
  - Validation goes through `PortfolioFormRules.check`, which only re-runs `PortfolioEngine.validate`: never add rules, never change typed values.
  - Submit with `visibleTransaction`, so hidden fields are never sent. On a type change keep only `retainedErrors`.
  - Dates are stored as `YYYY-MM-DD` via `PortfolioFormDates` (whole days, no time zone).
- **Transaction history (D2).**
  - Filters, holding scope and month groups come only from `PortfolioTransactionsDisplay`: display only, presenter order, no sums.
    Opening position is **Other**, never Buys.
  - Rows, edit sheet, delete dialog and account selector are shared (`PortfolioTransactionViews.kt` / `PortfolioTransactionsScreen.swift`).
  - Samples: no Delete (`onDelete = null` when `PortfolioFormRules.readOnly`). Sample states are `busy = true`, so read-only sheets must close
    regardless of `busy`.
- **Transaction details and feedback (D3).**
  - Details and rows show **recorded** values only (`PortfolioTransactionDetailsDisplay`): the amount is the server-normalized `grossAmount`;
    0 / unrecorded → no amount, never "0.00".
  - Success feedback only from `PortfolioUiState.actionResult` (set after the repository returned), shown once via
    `AccountDependencies.portfolioFeedback`. Never infer success from busy/error transitions; forms close on their own confirmed result.
- **Stabilization (D3.1).**
  - Allocation % is of account value **including cash** (the engine's documented rule): explain with `PortfolioAllocationDisplay.cashNote`, never
    renormalize.
  - Negative cash is a supported state: show `PortfolioPresentation.moneyLine` ("-USD 51.50") + "Unfunded", never clamp.
  - Error banners use `PortfolioDisplay.errorTitle` / `errorRetries` (from `PortfolioUiState.errorSource`).
  - The Portfolio tab's destinations are listed once in `PortfolioTabRoutes`.
  - Samples have no row actions.
- **Accessibility (Phase E).** Error and rejection messages are announced once: Compose live region / iOS `AccessibilityNotification`, with the
  screen banner quiet for a rejected save (`PortfolioDisplay.errorAnnounced`). Draw metric labels through `PortfolioPresentation.keepTogether`
  ("P/L" never breaks). History count announces only on a filter change.
- **Auth messages (E.1).** A failed ID-token refresh uses the platform `tokenMessage` mapper ("Your session has ended…"), never the sign-in mapper
  ("Check your email and password" is only for real sign-in failures).
- **Session recovery (E.2).** Sync failures carry a `UserDataErrorKind` (SESSION / NETWORK / SERVER; `userDataErrorKind`). The banner's title,
  message and action come only from `PortfolioDisplay.errorTitle` / `errorMessage` / `errorAction`. SESSION → "Session expired" + "Sign in again",
  which signs out first (`signInAgain` / `portfolioSignInAgain`); never open sign-in while the expired user is still signed in. A token failure
  that only couldn't connect is `AccountConnectionException` (NETWORK; text `AccountMessages.TOKEN_CONNECTION`). Add is unavailable only while
  `sessionExpired`. Trade currency sits in Amounts; `PortfolioFormRules.currencyNote` explains a currency other than the reporting currency.
- **Android keyboard.** A screen that uses `imePadding` calls `KeyboardResizesContent()` (scoped, reference-counted; restores the previous mode). Don't
  change the manifest's soft-input mode app-wide.
- **Display only.** Overview, holdings and allocation come from the shared display objects (`PortfolioOverviewDisplay`, `PortfolioHoldingsDisplay`,
  `PortfolioAllocationDisplay`). History loads on demand only; the chart is "Account value", never performance. No UI-computed return %. The donut
  only when `allocationDonutAllowed` (`StockAllocationLayout` geometry). Missing stays missing.
- **Company Details (5D.5.4A).**
  - The page order is plan §C. Sections are borderless per call site.
  - The in-page section bar (Overview · Financials · News · About) only scrolls; its entries and selection come from
    `CompanyDetailsPresentation.sections` / `selectedSection`.
  - iOS keeps a plain VStack pinned to the content width, because `LazyVStack` can't `scrollTo` anchors it hasn't created. Scroll anchors use
    their own string ids (the tabs already use the section value).
  - Chart stats go through `CompanyDetailsPresentation.chartStats` (High/Low only without a day range).
  - "What … does" text is `whatItDoes` (the first sentence, verbatim).
  - Understand rows (5D.5.4B) come from `CompanyDetailsPresentation.assessment`: labels, tones and sentences are the core rows'; the fact is
    display-formatted (`supportingMetric`: "year over year"; the Valuation row shows only the current P/E — P/E vs average lives in the
    Valuation card). Since r2 the rows use the default pill on the title line (iOS tries the compact pill before stacking). Each row opens the
    sheet for `metricId`: `GLANCE_SHEET_IDS` use the glance sheet, others the 5D.5.3 `MetricExplanationSheet`.
  - At a Glance uses `StockMetricGrid(tiled = true)`: borderless tiles with ⓘ at the top-right. Beginner Insight is an inset inside the
    Understand group; its icon goes above the text at large sizes.
  - Why Did It Move? order: observed move (`observedMove`, the header's quote values; word joiners keep each figure whole) → `whyMoving` lead →
    "Possible reasons" holding the verbatim rest → caution → breakdown. Never reword the server summary or invent reasons.
  - Badges up to `StockBadgeLayout.INLINE_MAX_CHARACTERS` (20) share a title's line when they fit. The iOS assessment-row badge falls back to
    the compact pill, then to wrapping tinted text.
- **Financials (5D.5.4C C1).**
  - The page layout comes only from `FinancialsDisplay` (display only): key-metric tiles from the model's own values and change texts (never a
    margin change, D-F3), the overview chart (moved, not repeated in its section), and the Income · Balance Sheet · Cash Flow bar.
  - The bar's tab is the group of the section nearest the bar (the model interleaves groups). Off-screen sections rank in page order, never at
    a fixed value.
  - A change caption carries the direction through `StockMetric(helperDirection)`; the value stays neutral.
- **Valuation (5D.5.4C C2).**
  - Layout comes only from `ValuationDisplay` (display only): ratio tiles (P/E first, then the model's ratios; captions = first sentence of the
    existing description; a missing ratio has no tile), the P/E vs History comparison only with a reliable average, one P/E Learn link, the
    Understanding fold and the D-V2 footnote. Never compute or reword values, averages, badges or methodology.
  - Ratio abbreviations in labels go through `ValuationDisplay.keepTogether` (word joiners; sheet titles and hints use `plain`).
  - The iOS P/E vs History badge falls back regular → compact → wrapping, so it never widens the column at AX5.
  - (C3) Valuation & Growth shows `ValuationDisplay.growthRows` (no P/E row; P/E leads the tiles) only when `growthVisible`. Range stats use
    `StockMetricGrid(maxColumns = 2, rowsWhenNarrow = true)`. Model sentences, the Learn link and the chart reference go through `keepTogether`.
  - (C3) iOS label/value row groups use `StockInfoLineGroup` (one stacking decision per group, shared `StockLayout.metricRowStacked`; values
    stay whole); `StockInfoLine.stacked` stays nil elsewhere. The Financials Profitability rows stay (owner decision).
- **Company Search (5D.5.5).**
  - Relevance comes only from core `search/SearchRanking` (exact ticker, exact name, ticker prefix, name word/prefix, later word, substring,
    provider; then major exchange before OTC, then provider order). It runs on the server (`StockService.search`) over both FMP endpoints.
    Never drop results; never use popularity, market cap or AI.
  - Scope is USD listings on US exchanges (D1). No type or sector labels (D3/D4); no FMP `limit` change (D8). Logos (D5 superseded): the
    server sets `StockSearchResult.logoUrl = fmpLogoUrl(symbol)` (no provider call, no key on devices); rows pass it to `StockTickerAvatar`,
    which keeps ticker initials when there is no logo. Never fetch profiles to get logos for search.
  - Sections, "Best match" (unique exact ticker or name and no other result for the same company) and row text come only from
    `search/SearchDisplay`. The badge uses the INFO kind, never green.
  - Rows: `StockSearchResultRow` (Compose, stacks ≥ 1.75×) / `SearchResultRow` (SwiftUI, stacks ≥ AX3). The legacy inline pane and the tablet
    sidebar are untouched (D7).
  - (5D.5.5C) Recent searches come only from core `search/RecentSearchStore` (selected results only, never typed queries; newest first,
    max 10; one entry per listing via `SearchRanking.ticker`; local key `stocksteps.recentSearches` — Android SharedPreferences, iOS
    UserDefaults; never synced, no account). Screen states come only from `SearchDisplay.phase` and its copy; Retry re-runs the current query
    once and stale responses never apply. The Comparison picker uses the same row (`enabled`/`note` "Already added") and keeps its own
    add/replace outcome.
  - Android debug builds use the LAN MOCK host from `~/.gradle/gradle.properties`; to test server changes on the local MOCK server, build with
    `-PstockstepsMockBackendUrl=http://127.0.0.1:8081`.
- **Company Details large text (5D.5.4D, iOS).** `StockSectionHeader` puts its action under the title only when the title's longest word and
  the action can't share a line (`StockLayout.metricRowStacked`); the `StockBarChart` legend stacks one entry per line instead of shrinking;
  Financial Highlights and Key Ratios rows go through `StockInfoLineGroup`. Screen readers can't be driven by injected input on the emulator
  (TalkBack focus doesn't move) — the owner runs them manually.
- **Contextual learning (5D.5.3).**
  - Metric → Learn term only via core `MetricLearnTerms` (by id: exact term id, then a single `relatedMetrics` owner, else null); never match visible
    titles and never add educational text.
  - Rows carry `InfoRow.metricId`; the ⓘ (`info = hasTerm`) marks only mapped metrics; unmapped metrics get the sheet without a Learn button.
  - Sheets close before navigating; term pages open on the same stack (`LearnTermRoute` / `LearnTermView`), so Back returns to the stock.
- **Learn (5D.5.2).**
  - Learn copy and rules come only from core `LearnDisplay`.
  - Topics are the catalogue's `topic` values; never add topics, lessons or educational text without approval.
  - Topic icon colours are decorative theme roles (`topicTone`), never a gain/loss cue.
  - Term search order comes only from `LearnDisplay.search` / `searchTier` (exact → prefix → title → title or topic → definition; never drop
    matches).
  - At very large text (Android ≥ 1.75×, iOS AX4+) topic tiles are one column of compact rows.
  - At large text the Start here sentence folds under "What's covered".
  - Lists show `LearnDisplay.preview` (the first sentence); the term page shows the full catalogue text.
  - Learn titles go through `LearnDisplay.unbroken` (no break after "/").
  - Topic tiles follow `StockCardGridLayout` with the icon counted as padding (compact icon-above tiles at large text).
  - New Learn pages are `LearnTopicsRoute` / `LearnTermRoute` (Android) and `LearnTarget` (iOS).
- **Daily Brief layout (5D.5.1c).**
  - The story group is borderless per call site (`StockCard(bordered = false)` / `.stockCard(bordered: false)`), the same surface as the index
    tiles.
  - The market-session lines sit under Markets.
  - `DailyBriefDisplay.freshness` shows the session wording only when no index shows it.
  - The empty watchlist uses one secondary "Add companies" button.
  - The footer links share one compact label style.
  - The global card default is unchanged.
- **Daily Brief takeaway and tiles (5D.5.1b).**
  - The headline comes only from `DailyBriefDisplay.takeaway`:
    - index values and sessions only;
    - close or live quotes only — never stale or unavailable ones;
    - higher / lower / about unchanged / mixed;
    - "so far today (delayed)" for live quotes; "In the last session (date)" for pre-market, weekend and holiday editions;
    - a market closed for a holiday says so, never its older close;
    - no magnitude ("slightly") or cause words.
    - Owner decision D-B1: changing this wording needs approval. The server summary stays verbatim below it.
  - Index tiles (`StockIndexTile`) only while `StockIndexTilesLayout.tiles` (measured tokens, 8 dp tile padding) says they fit; otherwise
    `BriefIndexRow`.
  - Stale and offline share one banner (`DailyBriefDisplay.status`).
  - Earnings badges show the server's date status verbatim.
- **Daily Brief (5D.5.1).**
  - Reader copy and rules come only from core `DailyBriefDisplay`.
  - The server `summaryLine` is the headline, never reworded. Never add causes ("what moved the markets"), key points or invented headlines.
  - One meta line with the SAMPLE badge; freshness is relative, with no time zone guessed.
  - Index rows go through `StockIndexRow` / `BriefIndexRow` (shared with Home).
  - Stories show `DailyBriefDisplay.excerpt` (hidden when it only repeats the headline) through `StockShowDetails`. The evidence note sits once in
    "About these stories", and the notes and disclaimer once in "About this brief".
  - Text actions on a toggle row use `StockLinkAction`.
  - `StockNavigationRow.summary` is never truncated (a clipped "S&P 50…" misleads).
  - The disclosure components are design-system `StockDisclosure` / `StockShowDetails`; the Earnings names are wrappers.
- **Earnings visual polish (5D.3).** Key numbers come from the presenters' `keyMetrics` (drawn with `EarningsKeyMetrics` / `StockMetricGrid`); the
  Details headline only from `EarningsFormatter.epsHeadline` (server classification, else the server summary); methodology, sources and disclaimers fold
  into `EarningsDisclosure` (each note once, `EarningsDisplay.resultsDisclosures`); sample data shows a SAMPLE badge (D-1), never removed.
- **iOS simulator tool:** tap/swipe coordinates are device **points** (screenshot pixels ÷ 3 on iPhone 16), not image pixels; wrong units looked like
  "taps not registering" in 5D.4–5D.4.2.
- **Earnings surfaces (5D.4.2).** Earnings content sections are borderless per call site (`StockCard(bordered = false)` / `.stockCard(bordered: false)`);
  never change the global card default. "Comparison unavailable" sits below card titles (`EarningsDisplay.badgeBelowTitle`); secondary key metrics use
  `StockMetricItem(quiet = true)`.
- **Earnings cards (5D.4.1).** One collapsed `EarningsShowDetails` per card (key metrics and badge always visible); a missing estimate is
  `EarningsFormatter.NO_ESTIMATE`, never 0 or a beat/miss; the unknown-currency caption stays visible (`EarningsDisplay.visibleCaveat`).
- **Earnings UX (5D.4).** Long server explanations show `EarningsDisplay.firstSentence` + "Show details" (never reword server text in the UI); key
  metric groups use `StockMetricGrid(rowsWhenNarrow = true)`; chart axis labels only from values already shown (`epsChartYLabels`).
- **Earnings layout (5D.2).** Earnings label/value groups draw through `EarningsLines` (Compose and SwiftUI) with the shared
  `StockLayout.labelColumn` rule (one decision per group; never a weighted label beside an unweighted value). Learn links are named after the lesson
  they open (`EarningsDisplay.cardTopic` / `topicAction`).
- **Earnings display (5D.1).** BEAT/MET/MISS come only from `EarningsDisplay.classification` (label + `StockBadgeKind`, drawn with `StockStatusBadge`) on
  every Earnings screen. Amounts use `EarningsFormatter` with the source currency: a null currency shows no symbol (never "$") plus
  `EarningsFormatter.UNKNOWN_CURRENCY` once per card or section; never infer a currency from the ticker or exchange.
- **Large text.** Portfolio sections are borderless (`bordered = false` per call site). Rows in one group stack together (one decision per group,
  `StockLayout.metricRowStacked`), never one by one. Keep iOS badge text short (badges never wrap). Sample scenarios live in the grouped ⋯ sheet
  (`PortfolioScenarioGroups`).

iOS device checks: build with `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081` (the default build bakes the LAN host from git-ignored
`app/iosApp/Configuration/Local.xcconfig`). A fresh simulator install defaults to REAL (`localhost:8080`): set MOCK in Settings or
`defaults write org.example.stocksteps stocksteps.backendEnvironment MOCK`. The app needs iOS ≥ 18.2 (the iOS 17.5 SE simulator can't install it).
iOS sample scenarios need a signed-in session: the iPhone 16 simulator has a cached test session. The Firebase Auth emulator
(`firebase/node_modules/.bin/firebase`) runs only with owner approval.
Visual direction: `docs/design/stocksteps-ui-reference.png` (+ `stocksteps-portfolio-reference.png`).
Still open:
- approved LAN backend redeploy;
- screen readers: TalkBack's first start asks a notification permission the owner must answer; VoiceOver needs a device;
- physical devices.

Cloud Run staging remains deferred. The LAN MOCK backend runs on the Ubuntu host (address in git-ignored `deploy/local/server.env`).

## Repository layout

| Module | Path | Contents |
|---|---|---|
| `:core` | `core/src/commonMain/kotlin/org/example/stocksteps/` | KMP shared domain/data: models, network clients (`network/StockStepsApi.kt` public, `data/userdata/UserApi.kt` signed-in), repositories, deterministic engines and **presenters** (StateFlow). Shared by server and apps. |
| `:app:shared` | `app/shared/src/commonMain/.../presentation/` | Compose Multiplatform UI (Android uses it). `di/AccountDependencies.kt` (Koin account graph, presenters), `di/StockStepsDependencies.kt` (public data graph). `iosMain/` has `Ios*Client.kt` bridges for SwiftUI. |
| `:app:androidApp` | `app/androidApp/` | Android entry (`MainActivity`), FCM (`account/AndroidPush.kt`). |
| iOS app | `app/iosApp/iosApp/` | **Native SwiftUI** (not Compose) mirroring shared presenters via `Ios*Client` bridges; `AppScene.swift` owns navigation. Xcode project uses synchronized folders (new `.swift` files are picked up automatically). |
| `:server` | `server/src/main/kotlin/org/example/stocksteps/` | Ktor backend: `Application.kt` (wiring + data sources), feature packages (`brief`, `practice`, `learning`, `earnings`, `screener`, `news`, `service`, `userdata`, `repositoryImpl/fixture`). |

Core feature packages: `brief` (Daily Market Brief), `practice` (Practice Portfolio), `learning`
(Guided Research), `earnings`, `screener`, `portfolio` (+`analytics`), `home`, `markets`, `news`,
`companydetail`, `watchlist`.

## Non-negotiable rules

- **Navigation boundary** (both platforms): Route = destination identity/args only; Scene = acquires
  presenter/ViewModel, collects state, wires navigation; Screen = renders state + action callbacks,
  never resolves dependencies.
- **MOCK-first**. `STOCKSTEPS_DATA_MODE=mock` server on :8081 uses fixtures
  (`server/src/main/resources/fixtures/`, manifest `keepMissing`) and must make **zero** paid provider,
  production Firebase, live AI or real push calls, with no silent fallback to REAL. MOCK market clock is
  pinned to fixture capture `2026-10-07T21:15Z` and advances.
- **REAL never fabricates** prices, returns, news, publishers or URLs. Missing data is `null` and shown
  as unavailable/labelled — never estimated or replaced with zero.
- **Money**: use the exact `portfolio/Decimal` (decimal strings), never Float/Double for accounting.
- **Plans are enforced server-side** via `EntitlementService` (unified **StockSteps+**; no per-feature
  subscriptions). Debug plan records only count in MOCK. Expired plans never delete user data.
- **AI**: StockSteps+ only, checked on the server *before* any provider call; daily quotas; bounded,
  source-grounded context; outputs validated (no links, invented numbers/sources, advice, predictions,
  invented causes). MOCK uses deterministic templates labelled "Sample". No AI calls from mobile.
- Secrets stay on the server (`FMP_API_KEY`, `FINNHUB_API_KEY`, `GEMINI_API_KEY`…). Never print or commit
  keys; never repeat the old FMP key from history; don't read IDE credential files.
- Don't enter credentials/passwords on emulators/simulators; don't drive the emulator UI unless asked;
  don't bypass simulator permission prompts.
- **Commit/push only when the user explicitly asks** (each time). Every commit must update
  `PROJECT_HANDOFF.md` in the same commit (completed work, validation, limits, next items), identify the
  current commit by title, and never describe committed work as uncommitted. Commit trailer:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Earnings** (permanent since Phases 1–4):
  - event/report identity is `SYMBOL:YYYY-Qn` (fiscal period, exchange-qualified symbol);
  - status comes from source data, never from the date passing;
  - EPS/revenue classification is exact decimal (MET only when equal, no tolerance) via `EarningsMath`;
  - results, insights, price reactions and reminder schedules are calculated on the server only;
  - price reactions use `PriceReactionEngine` (exchange calendars) for every screen;
  - earnings notifications go only through `EarningsReminderService`, never `AlertType.EARNINGS` alerts (the Phase 5 weekly
    digest is a `WEEKLY_DIGEST` delivery in the same pipeline);
  - Phase 5 AI: StockSteps+ verified server-side (fail closed), one `EarningsAiQuotaLedger` for every earnings AI feature,
    context only from `EarningsGrounding`, every output through `EarningsAiValidator`; history via `HistoricalEarningsEngine`.
- **Provider calls and caching** (since the cost program, Phase 2):
  - all FMP/Finnhub requests go through `apiCall` (`httpclient/NetworkUtils.kt`); `ProviderCalls.record` there is the **only** place
    that counts `upstream` requests — don't add parallel `upstream` counters; BoC/Gemini call `ProviderCalls.record` themselves;
  - cache provider data with the existing `CompanyFinancialCache` at the lowest shared owner (adapter or the single service instance);
    keys must include endpoint, exchange-qualified symbol, period/frequency and row limit; don't add a second layer over an effective one;
  - failures are shared with concurrent callers and never stored as data; cooldowns only via `resultTtl`/`providerCooldown`;
    never retry 429/402/403;
  - measure provider-call changes with MockEngine benchmarks (`server/src/test/.../service/*Benchmark*Test.kt`), never REAL calls.
- **Phase 4 guards** (keep them in the path of new code): every provider-backed route belongs to an admission `RouteGroup`
  (`security/Admission.kt`); never key limits on `remoteHost` or the first `X-Forwarded-For` value (use `ClientIdentity`/`limiterKey()`); every new
  upstream call path must acquire a `ProviderGuard` permit and `complete` it (as `apiCall` does); new signed-in AI features use `DurableAiQuota`
  (reserve before the provider call; release only failures that produced nothing); per-symbol bundles go behind `SymbolExistence`; logs never contain
  provider URLs, keys or tokens (`io.ktor` stays at WARN).
- **Buttons**: primary fills use `primaryAction` (AA with white), never brand `primary`; on iOS use `.buttonStyle(.stockPrimary)` /
  `.stockSecondary`, not `.borderedProminent` (it fills with the brand-blue tint). Metric values wrap (`StockMetric`/`StockMetricGrid`);
  show readable labels for enums (`StockLabels.humanize` fallback). Visual direction: `docs/design/stocksteps-ui-reference.png`.
  Status labels use `StockStatusBadge` with a `StockBadgeKind` (MOCK/simulated = SAMPLE), title + badge rows use `StockTitleWithBadge`, banners
  `StockBanner`; badge/banner colours only via the shared `StockSemanticStyles`.
- **Responsive layout decisions** (Global UI Refinement 4C.3–5A.1) live in shared objects in `app/shared/.../theme/StockSemanticStyles.kt`
  (`StockPillLayout`, `StockActionLayout`, `StockBadgeLayout`, `StockQuoteRowLayout`, `StockCardGridLayout`, `StockComparisonLayout`) with tests in
  `ResponsiveLayoutRulesTest`; Compose and SwiftUI only measure text and call them. Screen copy and spoken labels shared by both platforms live in core
  `*Display` objects (`ScreenerDisplay`, `ComparisonDisplay`). iOS: a value and its period/caption must scale on the same Dynamic Type curve
  (`relativeTo: .footnote` for both) — small styles like `.caption2` grow faster and would end up larger than the value at XXXL. Scrolling chip/pill rows use `StockFadingScrollRow` on both platforms. New component parameters stay optional with
  unchanged defaults. iOS device checks need a build with `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081` (the default build targets the LAN host).
- **Comparison/large-text rules (2026-10-12)**: multi-series charts use `StockChartSeriesStyle` (colour + dash per series, drawn legend swatches,
  never colour alone); tab labels follow `StockNavigationLayout.labelScale` (never ellipsized); a heading may shrink only via `StockHeadingFit.scale` when
  one of its words can't fit its line (measure font-independent widths — never the heading's own frame, which feeds back); server note sentences are
  regrouped only by known openings with unknown text kept verbatim (`ComparisonNotesDisplay`, `ComparisonObservationsDisplay`). iOS: `StockStepsTheme.font`
  resolves the current content-size category, but a view refreshes on a **live** Dynamic Type change only if it depends on `dynamicTypeSize`
  (read it in `body`; use `.id(typeSize)` for `ForEach` groups whose data doesn't change).
- **Portfolio display** goes through `presentation/portfolio/PortfolioPresentation` (labels for account types/transaction kinds, signed
  changes, direction, holding text), `PortfolioDates` (readable dates; UTC stays UTC) and `PortfolioChartRules` (chart only with ≥ 2 dated
  values) on both platforms — never raw enums, never `0.00` for missing data; Portfolio is the reference screen for later UI phases.
- **Sign-in/create-account screens use `StockStepsTheme`** (colours/typography); `theme/AuthTokens.kt` holds sizes only — never a
  separate hard-coded palette (it hides the screens from theme changes and dark mode).
- Match existing code style (dense Kotlin, KDoc on intent, theme tokens: `StockStepsTheme.spacing/
  colors/typography/dimensions/shapes`, `StockCard`, `StockButton`, 48dp touch targets, gain/loss in
  words not colour alone). Don't add a sixth bottom tab (Home | Markets | Portfolio | Watchlist | Learn).

## Build, run, test

```sh
./gradlew :server:runMock                     # mock backend :8081 (or installDist + STOCKSTEPS_DATA_MODE=mock PORT=8081 server/build/install/server/bin/server)
./gradlew :server:stopMock                    # stop it
adb reverse tcp:8081 tcp:8081                 # Android emulator → local mock
./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
./gradlew :app:androidApp:assembleDebug       # or installDebug
./gradlew :app:shared:compileAndroidMain      # fast Android compile check
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build   # default DerivedData; disk space is tight
```

Restart the mock server after changing server code or fixtures. Test results: `*/build/test-results/`.

## Kotlin ↔ Swift interop gotchas (seen in this repo)

- Kotlin `List<Double?>` arrives as `[Any]` → use `as? NSNumber`. `Double?`/`Int?` → `KotlinDouble?`/`KotlinInt?`.
- Properties named `description` become `description_`; `short` becomes `short_`.
- Nested classes: `LearningProgressRepository.State` (not `…RepositoryState`).
- Name clashes get a `_` suffix (e.g. two `AllocationSlice` types) — prefer unique names (`PracticeAllocationSlice`).
- Kotlin/Native: avoid JVM-only APIs (`toSortedSet`, `sortedMapOf`); cross-module properties can't be smart-cast (use locals).
- JUnit tests returning `runBlocking` need `: Unit`.
- Kotlin methods starting with `new…`/`copy…` (and `alloc`/`init`) are renamed `do…` in Swift (`doNewConversation`) — name them otherwise
  (`startOver`, `addAiAnswerToNote`).
- In `Ios*Client` bridges never give a use-case property the same name as a member function: inside `scope.async { searchStocks(q) }` Kotlin calls
  the function (infinite recursion), not the property's `invoke`.
- Objects created in a SwiftUI view `init` for `@State` are re-created on every parent update (only the first is kept): no network/observation in
  their `init` — use `start: false` + `activate()` from `.task` (see `AppScene`).
- iOS simulator builds that sign in with Firebase Auth must be ad-hoc signed (`CODE_SIGN_IDENTITY=- CODE_SIGNING_ALLOWED=YES`); unsigned builds fail with
  keychain error 17995. Test sign-in only with the Firebase Auth emulator (`--firebase-emulators`; Android `-PfirebaseEmulators=true` + `adb reverse tcp:9099`).
- SwiftUI re-runs scene `init`s and evaluates `navigationDestination` builders on parent updates: models created there must not start presenters or
  clients in `init` (create on first use, subscribe from `.task`); scenes owning destinations must not declare `@Environment(\.openURL)` (render loop —
  use `ExternalURLOpener`).
- SwiftUI `Text("you@example.com")` (a string literal) is parsed as Markdown and auto-links emails/URLs — use `Text(verbatim:)` for
  placeholders and data.

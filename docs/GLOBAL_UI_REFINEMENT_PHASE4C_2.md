# Global UI Refinement Phase 4C.2 — final Company Details UX polish

Status: **committed with Phase 4C.3** as "Polish Company Details: natural loss wording, chart error states, dated change label, responsive actions and range selector (Global UI Refinement Phases 4C.2 and 4C.3)" (2026-10-10). Phase 4C.3 later ran the device checks this phase lists as NOT RUN — see `docs/GLOBAL_UI_REFINEMENT_PHASE4C_3.md`. Base: `7adc89c` "Refine Company Details for beginners with chart scroll fix
and accessible price summary (Global UI Refinement Phases 4C and 4C.1)". Reference: `docs/design/phase4c1/stocksteps-company-details-final-reference.webp`.
No device, emulator, simulator or LAN backend was used (device access not approved), so every on-device item below is **NOT RUN**.

## 1. Confirmed bugs (fixed) vs optional recommendations (not changed)

| # | Type | Finding | Fix |
|---|---|---|---|
| B1 | Bug | Beginner Insight said "Rivian kept about $-80 of every $100 in revenue as profit" (also the Financial Highlights Net Margin helper) | New core `MarginWording.sentence`: positive "kept about $32 of every $100 in revenue as profit", negative "lost about $80 for every $100 in revenue", rounds to $0 → "less than $1", exactly 0 → break-even, missing/non-finite → no sentence. Used by the Profitability row and the insight; margin values unchanged |
| B2 | Bug | Offline chart said "Price history isn't available for this range" and every chart failure offered Try again | `StockDataException` now keeps the backend `status`/`code` (null = backend not reached). Core `ChartPresentation.issue/notice`: `CHART_NOT_FOUND`/`SYMBOL_NOT_FOUND` or a successful response with < 2 prices → "No price history is available for this period. Try another range." (no retry); no status → "Couldn't load the chart. Check your connection and try again." (retry); any other status (5xx, 429, provider outage) → "The chart is temporarily unavailable. Try again in a moment." (retry). Loading stays the skeleton. Nothing is padded or estimated |
| B3 | Bug | The daily change was always labelled "Today", even for a quote from an earlier session | `CompanyDetailsPresentation.changeLabel`: "Today" only when the quote's own New York date equals today's New York date **and** the server reports a known non-pre-market session; otherwise the quote's date ("Oct 8"); no quote time → "Today" only while the server says open, else "Latest session". Same label in the spoken price sentence ("Up $2.13, 1.21%, on Oct 8."). Quote time, timezone and status chip unchanged |
| B4 | Bug (a11y) | "3 sources →" pill was a ~28 dp/pt touch target and read the arrow aloud | Compact pill kept, wrapped in a 48 dp (Android) / `touchTarget` pt (iOS) target; spoken "3 sources, button" |
| B5 | Repetition | The net-margin sentence appeared three times (Profitability row, Beginner Insight, Financial Highlights helper) | Highlights row shows the figure only; the sentence stays in Profitability and the insight (as in the reference) |
| B6 | Repetition | Growth row explanation restated its own number ("Revenue grew 6.4% compared with the prior year" under "Revenue +6.4% YoY") | Interprets instead: "Apple brought in more money from sales than a year earlier." (less / about the same for ≤ 0) |
| R1 | Recommendation | The valuation headline ("Trading near its historical P/E valuation.") appears in the Understand Valuation row and the Valuation section | Kept: the reference shows both; the second is the detailed section |
| R2 | Recommendation | MOCK banner sits above the tabs on Android but below the tab bar on iOS | Both stack outside content (no overlap, by code); consistent placement is a design decision |
| R3 | Recommendation | `BackendInfo` asks once; a failed `/info` call hides the MOCK banner for the session | Pre-existing; a retry would add a request — needs approval |
| R4 | Recommendation | A malformed (non-JSON) response is classified as a connection failure | Rare; both offer Try again, so the action is still right |
| R5 | Recommendation | iOS keeps its own `quoteTime` formatter (same format as the shared `formatQuoteTime`) | Cosmetic duplication |

## 2. Review items without code changes

- **Compact layout (code review only):** name wraps (no line limit), tags wrap (`FlowRow` / `ViewThatFits` → stacked), price + status chip wrap,
  change + new label wrap ("Latest session" is the longest label), actions stack at large text (Android ≥ `largeFontScale`, iOS ≥ XXXL), pill
  selector unchanged from 4C.1, metric grids wrap values. No font sizes reduced. Android 360 dp at 1.3× and iOS XXXL / narrow: **NOT RUN**.
- **MOCK banner:** shown only when the backend reports `isMock` (REAL reports false, so it never appears there). Android: in the Scaffold
  `bottomBar` above the five tabs, so `innerPadding` keeps content above it; without tabs it pads the navigation bar. iOS: stacked below the app
  (tab bar included) and fills the home-indicator strip. Five tabs unchanged. On-device check: **NOT RUN**.
- **Chart gestures:** `StockLineChart` (both platforms) untouched — the 4C.1 horizontal-only scrub and vertical scroll are preserved. Device
  check **NOT RUN**.
- **Screen readers:** price block is still one element; it now names the session. TalkBack **NOT RUN**, VoiceOver **NOT RUN**.

## 3. Files changed

Core: `domain/StockRepository.kt` (`StockDataException.status/code`), `data/StockDataRequest.kt`, `companydetail/ChartPresentation.kt`
(`ChartIssue`, `ChartNotice`, `issue`, `notice`), `companydetail/CompanyOverview.kt` (`MarginWording`, growth/profitability explanations,
highlights helper removed, insight).
Shared: `presentation/companydetails/CompanyDetailsPresentation.kt` (`ChangeLabel`, `changeLabel`, `priceDescription(…, change)`),
`CompanyDetailsViewModel.kt` (`chartIssue`), `CompanyDetailsScreen.kt` (label, chart notice, sources target), `QuoteTime.kt` + android/jvm/ios
actuals (`exchangeDate`, `currentExchangeDate`), `iosMain/IosCompanyDetailsClient.kt` (`loadChart` → `ChartLoad`; `getChart` removed, no other
caller), `composeResources/values/strings.xml` (unused `details_today`, `details_chart_unavailable` removed).
iOS: `CompanyDetailsModel.swift` (`chartIssue`), `CompanyDetailsScreen.swift` (label, `chartNotice`, sources button).
Tests: `CompanyOverviewPresenterTest` (+4), `RemoteCompanyDetailsRepositoryTest` (+1), `CompanyDetailsPresentationTest` (+2, 1 updated),
`CompanyDetailsViewModelTest` (+1).

No API contract, server, fixture, provider, Firebase or navigation change. No new requests.

## 4. Before / after (text)

| Case | Before | After |
|---|---|---|
| RIVN insight | "…kept about $-80 of every $100 in revenue as profit." | "…revenue fell X%. It lost about $80 for every $100 in revenue." |
| Offline chart | "Price history isn't available for this range." + Try again | "Couldn't load the chart. Check your connection and try again." + Try again |
| No history for range | same message + Try again | "No price history is available for this period. Try another range." (no button) |
| Saturday, Friday quote | "+$2.13 (+1.21%) Today" / spoken "…today." | "+$2.13 (+1.21%) Oct 9" / spoken "…on Oct 9." |
| MOCK (clock pinned to Oct 7, device date later) | "Today" | the fixture quote's date — accurate, but MOCK screenshots no longer match the reference's "Today" |

Screenshots: **NOT RUN** (no device access).

## 5. Tests and builds (sequential)

`./gradlew :core:jvmTest` → **435/0** (+5); `:app:shared:testAndroidHostTest` → **104/0** (+3); `:core:iosSimulatorArm64Test` → **435/0**;
`:app:shared:iosSimulatorArm64Test` → **100/0**; `:app:androidApp:assembleDebug` → OK; iOS `xcodebuild … generic/platform=iOS Simulator … build` →
**BUILD SUCCEEDED** (after a fix to a Swift expression the compiler could not type-check). `:server:test` not run: no server code or core API
used by the server changed.

## 6. Remaining limitations

- No on-device verification: Android 360 dp / 1.3×, iOS XXXL / narrow, light/dark, banner, chart gestures, error states — all NOT RUN.
- TalkBack and VoiceOver NOT RUN.
- "Today" uses the device clock only as a cross-check of the quote's own date plus the server status; a wrong device date can only turn
  "Today" into a date, never the other way round when the status is pre-market or unknown.
- R1–R5 above are open for decision.

## 7. Verdict

**PASS WITH CONDITIONS** — B1–B6 fixed on both platforms with regression tests, all suites and both builds green; conditions: the on-device
pass (layouts, banner, gestures, screenshots) and TalkBack/VoiceOver checks once device access is approved. Phase 5 not started.

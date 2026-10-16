# ChatGPT review package — UI/UX Refinement Phase 3: Company Details, Financials & Valuation

## 1. Phase and Git baseline
- Phase: UI/UX Refinement Phase 3 (Company Details, Financials, Valuation; Android Compose + iOS SwiftUI).
- Base: `ff5662e` "Fix iOS landing content under the status bar and shorten company page titles for UI/UX Refinement Phase 2 (KAN-16) …".
- Under review: the commit "Refine Company Details, Financials and Valuation for UI/UX Refinement Phase 3 with a delayed-quote freshness line, one chart direction for line, caption and spoken summary, a fixed iOS AX5 chart axis, explained highlight and ratio rows, consistent P/E difference rounding and readable financial dates on Android and iOS" (on `0696f7e`, pushed). `0696f7e` is the REAL Portfolio/Practice fix from another session; it contains none
  of this phase's files.

## 2. Changes implemented (both platforms)
- Price header: price alone on its line; session chip on the secondary line with "Updated 4:00 PM EDT · Oct 7 · Prices may be delayed" (U1 approved).
- Chart: one direction per chart — the selected period's own change drives the line colour, the caption and the spoken summary (U3).
  Captions name their period: "-1.87% from 9:30 AM on Oct 7" (1D), "since 2021" (ALL). iOS AX5: chart axis labels capped at AX1.
- Financial Highlights: no "—" for rows without a change; Highlights and Key Ratios rows open their existing explanations.
- P/E difference "+1.8%" on Company Details, matching the Valuation page (was "+2%").
- Earnings card: one View Results action (U2 approved; "Explain With AI" opened the same page — verified, report §4).
- Financials: readable dates ("Sep 30, 2025"); the Revenue section no longer repeats the first Key Metrics tile.
- Large text: Android ⓘ stays with the last word of a wrapping label; iOS status chip redraws on live Dynamic Type changes.

## 3. Affected files
- core: `companydetail/ChartPresentation.kt`, `CompanyOverview.kt`, `FinancialStatementsPresentation.kt`; tests `CompanyOverviewPresenterTest`,
  `FinancialStatementsPresenterTest`.
- shared: `presentation/companydetails/CompanyDetailsPresentation.kt`, `FinancialsDisplay.kt`, `CompanyDetailsScreen.kt`,
  `CompanyFinancialsScene.kt`, `designsystem/components/StockMetric.kt`, `composeResources/values/strings.xml`; tests
  `CompanyDetailsPresentationTest`, `FinancialsDisplayTest`.
- iOS: `CompanyDetailsScreen.swift`, `CompanyFinancialsScene.swift`, `DesignSystem/StockDetailComponents.swift`, `DesignSystem/StockComponents.swift`.
- docs: `docs/COMPANY_DETAILS_UI_REFINEMENT_PHASE3.md`, this package; this phase's entries in `docs/PROJECT_HANDOFF.md` and `docs/project-status.md`.

## 4. Architectural decisions
Rules live in core/shared presentation (`ChartPresentation`, `CompanyDetailsPresentation.freshness/explains`,
`FinancialsDisplay.headlineInSection`, `FinancialStatementsPresenter.readableDate`); both platforms call them. Display only — no value,
average, change or position is computed differently; no server or API contract changes (`ChartPresentation.summary` lost an app-internal
parameter). Design-system touches are scoped: iOS axis cap only for the opted-in chart; Android ⓘ rendering inside the same component;
iOS chip refresh. Navigation, loading, state and requests untouched. Details: report §4, `CHATGPT_FOCUSED_DIFF.md`.

## 5. Test and build results
Final run (sequential): `:core:jvmTest` PASS 611/0, `:core:iosSimulatorArm64Test` PASS 609/0, `:app:shared:testAndroidHostTest` PASS 281/0,
`:app:shared:iosSimulatorArm64Test` PASS 278/0, `assembleDebug` PASS, `xcodebuild` PASS. Three earlier full runs also all PASS; no flakes.
See `CHATGPT_TEST_RESULTS.md`.

## 6. Android verification (emulator-5554, 427 dp, MOCK :8083, signed-in test session)
PASS (after build): light top/middle/bottom before/after, dark top, dark 2.0× (no clipping), PFAI, Financials annual/quarterly light/dark,
Valuation light/dark, Key Ratios sheet. Requests: one per endpoint on open; 0 on scroll, sheets, Back; 0 over 6 repeat tab switches.
PASS (final build, after relaunching the AVD on the owner's go-ahead): 1D red line + red caption "-1.87% from 9:30 AM on Oct 7", ALL/1M green;
spoken chart text in the accessibility tree matches ("Price chart, 1D: down 1.87% …"); 2.0× ⓘ stays with its last word ("Net / Income ⓘ",
"(EPS) ⓘ"); 1.0× rows and Financials tiles unchanged.

## 7. iOS verification (iPhone 16 simulator, MOCK :8083, guest)
PASS (after build): light/dark, AX5 chart with a live change, PFAI, ALL, Financials, Valuation, Highlights sheet; 11 requests on open,
0 over 4 tab switches. PASS (final build): 1D red line + red caption "-1.87% from 9:30 AM on Oct 7"; status chip grows on a live change to AX5.

## 8. Known issues
MOCK fixture inconsistencies (1D intraday end vs quote; market cap); iOS chip dot fixed size; Key Ratios group taller (48-pt rows); iOS Markets
reselect-to-top not observed in simulator taps (navigation untouched, not investigated). Report §7.

## 9. Unverified scenarios
- Incident (resolved): an outside Claude process killed both emulators during the first final-capture attempt; Pixel_9_Pro was relaunched as
  emulator-5554 on the owner's go-ahead (emulator-5556 not relaunched — not this session's device).
- BLOCKED: iOS signed in; Android guest.
- NOT RUN: TalkBack/VoiceOver (spoken chart text checked in the accessibility tree only); loading/error captures; tablets/foldables;
  Android dark 2.0× on the final build.

## 10. Deviations from the plan
D1 moves the status chip away from the price (approved as U1). U3 reverses the 5D.5.4A rule that the 1D line follows the header's day direction.
Fixes found during checks (caption wording, colour, chip refresh, ⓘ wrap) were added within scope with full reruns.

## 11. Decisions requiring owner approval
None open (U1 and U2 approved, U3 fixed).

## 12. Links
Report `docs/COMPANY_DETAILS_UI_REFINEMENT_PHASE3.md`; contact sheets `CHATGPT_UI_REVIEW_ANDROID.png` and `CHATGPT_UI_REVIEW_IOS.png`
(before / after / final); originals in git-ignored `captures/before`, `captures/after`, `captures/final`; logs in `captures/logs*`.

**Recommendation: APPROVE** (implementation). Remaining owner passes, not blockers for this UI phase: signed-in iOS, TalkBack/VoiceOver.

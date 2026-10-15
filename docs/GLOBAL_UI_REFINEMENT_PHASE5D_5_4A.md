# Global UI Refinement Phase 5D.5.4A: Company Details header, chart and overview hierarchy

- **Status:** implemented and verified on Android and iOS against the local MOCK server. Committed and pushed as "Refine Company Details for Global UI Refinement Phase 5D.5.4A with a borderless chart and day range, one action row, a pinned in-page section bar, a What it does row, a beginner-first section order and borderless sections on Android and iOS" (`0fbce92`). **Closed 2026-10-15** (§8). Baseline: `73a6c23`
  "Record the closure of Global UI Refinement Phase 5D.5.3 contextual learning …".
- **Plan:** `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_4_PLAN.md` (approved: 5D.5.4A first, D-E1 to D-B1 as recommended, borderless at every Company Details
  call site).
- **Review package:** `docs/reviews/phase5d_5_4a/` (report, Android and iOS contact sheets, focused diff; `captures/` is git-ignored).

## 1. What changed
- **Header (D-E3):**
  - Identity, price, change with its session, the market status and "Updated …" stay.
  - The sector, industry and size tags are gone. Sector and industry now appear in "What Apple does" and About; size is the Market Cap helper.
  - This also fixes the iOS AX5 tag truncation (F3).
- **Chart:**
  - The chart surface is borderless.
  - The stats are Open and Volume as a `StockMetricGrid`: label above value, one column at large text. This fixes the iOS AX5 values that split
    mid-word (F1).
  - The **Day Range** bar moved from At a Glance to the chart, and High/Low are hidden while it shows them (D-E4). Without a day range,
    High/Low stay, so no value disappears.
  - iOS: at accessibility sizes the time axis keeps only the first and last dates and its labels stop growing past AX1. This is the new
    optional, off-by-default `StockLineChart(compactAxisAtLargeText:)`.
- **Actions (D-E2):**
  - **Add to portfolio** and **Compare** sit on one row after the chart, and stack only when they can't fit.
  - **Practice Buy** (and **Earnings** when the page has no Earnings section) moved to a new **Research further** section with Guided Research.
- **Section bar (D-E1):**
  - **Overview · Financials · News · About**, pinned under the top bar.
  - Tapping a tab scrolls to that section within the page; it never opens a screen.
  - The selected tab follows the scroll position (shared `CompanyDetailsPresentation.selectedSection`).
  - Financials and About appear only when the page has those sections.
- **"What Apple does":** the first sentence of the existing description, plus "Sector · Industry" (`CompanyDetailsPresentation.whatItDoes`).
  Tapping it scrolls to About. No new text.
- **Order (plan §C):** identity and price → chart → actions → section bar → What … does → At a Glance → Understand → Why Did It Move? (D-E5) →
  Valuation & financial health → Earnings → News → About (its own section) → Research further → disclaimer.
- **Borders (D-B1):** every Company Details card is borderless per call site (`StockCard(bordered = false)` / `.stockCard(bordered: false)`). The
  global card default is unchanged.

## 2. Owner feedback during the phase
1. **"Financial Health is not properly aligned with other items"** (iOS).
   - Cause: `StockAssessmentRow`'s text column didn't fill the row. A row whose badge stacks has no Spacer, so it was narrower and the list
     centred it.
   - Fix: the column fills the row, leading-aligned.
2. **"More debt than cash should be … not under financial health"**: the long Financial Health badges now share the title's line like the other
   badges.
   - The shared rule `StockBadgeLayout.INLINE_MAX_CHARACTERS` changed from 14 to 20; the test was updated.
   - Each layout still moves a badge below the title when they don't fit (Compose FlowRow, SwiftUI ViewThatFits).
   - iOS: side by side only when the whole title and the whole pill fit on one line. Otherwise the title wraps between words, and the badge
     falls back to the compact pill, then to tinted text that wraps. Before this, a fixed-width pill pushed the row past the screen edge at
     AX5.
   - **This is a shared rule.** It also applies wherever else `StockAssessmentRow` shows a badge of 15–20 characters.

## 3. Files
- **shared:**
  - `presentation/companydetails/CompanyDetailsPresentation.kt`: `chartStats` (High/Low only without a day range), `Section`, `sections`,
    `hasAbout`, `selectedSection`, `WhatItDoes`, `whatItDoes`, `firstSentence`.
  - `presentation/companydetails/CompanyDetailsScreen.kt`: ordered page keys, `stickyHeader` section bar, scroll and selection, new order,
    borderless cards, `WhatItDoesRow`, `ResearchSection`.
  - `designsystem/components/StockChip.kt`: `StockSectionBar` (new).
  - `theme/StockSemanticStyles.kt`: `StockBadgeLayout.INLINE_MAX_CHARACTERS` 14 → 20.
  - `designsystem/components/StockAssessmentRow.kt`: comment only.
  - Tests: `CompanyDetailsPresentationTest` (+4) and `ResponsiveLayoutRulesTest` (badge rule).
- **iOS:**
  - `CompanyDetailsScreen.swift`: plain `VStack` pinned to the content width, inline bar plus pinned overlay copy, string scroll anchors,
    selection, new order, borderless.
  - `DesignSystem/StockDetailComponents.swift`: `StockSectionBar` (new), `StockLineChart(compactAxisAtLargeText:)`, `StockAssessmentRow`
    alignment and badge fallback.

## 4. Verification
### Automated
| Suite / build | Result |
|---|---|
| `:core:jvmTest` | PASS 541/0 |
| `:core:iosSimulatorArm64Test` | PASS 541/0 |
| `:app:shared:testAndroidHostTest` | PASS 250/0 (+4 new), then `ResponsiveLayoutRulesTest` and `CompanyDetailsPresentationTest` rerun after the badge-rule change: PASS |
| `:app:shared:iosSimulatorArm64Test` | PASS 246/0 before the badge-rule change, and PASS 246/0 again on final code (`--rerun-tasks`, after freeing disk space) |
| `:server:test` | PASS 501/0 |
| `:app:androidApp:assembleDebug` | PASS (after the final Kotlin change) |
| `xcodebuild` (iPhone 16, MOCK, ad-hoc) | PASS (final code) |

### Android: emulator-5554, 360 dp, MOCK, Apple
| Check | Result |
|---|---|
| First viewport 1.0×: identity, price, chart, Open/Volume, Day Range, one action row, section bar, start of What Apple does | PASS |
| Section bar pins; selection follows scroll (Financials in valuation and earnings) | PASS |
| Tap Overview / Financials / News / About → section starts under the bar; About selected at the page end | PASS |
| UI tree: tabs are selectable with `selected=true` on the current one | PASS |
| "What Apple does" → scrolls to About | PASS |
| 2.0×: stats stack, bar scrolls with fade, no clipped values | PASS |
| Dark (in-app theme, restored to Light) | PASS |
| See Financials → Back restores position and selection | PASS |
| Understand: Financial Health badge on the title line, rows aligned | PASS |

### iOS: iPhone 16, MOCK, guest
| Check | Result |
|---|---|
| Default first viewport; borderless chart with Open/Volume and Day Range; one action row; bar | PASS |
| Tab taps (inline and pinned) scroll to the section; selection follows scroll | PASS (after two fixes found here: a LazyVStack didn't create off-screen anchors, and the anchors shared ids with the tabs) |
| Understand: Financial Health aligned, badge on the title line (default, light and dark) | PASS |
| AX5 dark: page fits the width, stats stacked, Day Range readable, actions stacked | PASS (after pinning the column width) |
| AX5: "What Apple does" wraps whole words (chevron hidden at accessibility sizes) | PASS |
| AX5: Understand title and long badge wrap, no overflow | PASS |

## 5. Known issues and limits
- **Disk space:** the Mac's data volume filled up during verification (155 MB free).
  - I first removed my own scratch build data.
  - With owner approval I then deleted `~/Library/Developer/Xcode/DerivedData` (7.4 GB, a rebuildable cache), leaving about 6.9 GB free after
    the test rerun.
  - The next iOS build is a full rebuild.
  - CoreSimulator (19 GB) and Gradle (12 GB) are untouched.
- **iOS:**
  - **Live Dynamic Type:** the existing refresh limit remains. Checks were done after reopening the screen.
  - **AX5 range row:** the chart range row scrolls with a fade (unchanged).
  - **Plain VStack:** it creates every section at once instead of lazily. The page is about a dozen sections with three news items; no lag was
    seen on the simulator.
- **Repetition (D-E4 beyond the chart, F5):** P/E still appears in Glance, the Understand row and the Valuation card. Phase 5D.5.4B covers this.
- **Screen readers:** TalkBack NOT RUN; VoiceOver BLOCKED (device). **iOS signed-in:** BLOCKED (guest mode).
- **MOCK data:** the fixture's $127.2B Market Cap is a data issue (out of scope).

## 6. Deviations from the plan
- Two owner-directed changes (§2).
- The shared badge rule changed at the owner's direction; the plan had kept long badges below the title.
- iOS uses a VStack with an overlay bar instead of `LazyVStack(pinnedViews:)`, which couldn't scroll to sections it hadn't created yet.

## 7. Verdict
**PASS WITH CONDITIONS:** approved and closed by the owner on 2026-10-15 (§8). The conditions are the accessibility and device items in §8.

## 8. Closure verification (2026-10-15)
- **Scope:**
  - The implementation is `0fbce92` (pushed): 27 files, all in this phase.
  - The working tree was clean before closure; the Portfolio worktree `charming-hodgkin-98e850` and the other scratch worktree were untouched.
  - The closure commit contains documentation only.
- **Disk:**
  - At the start of closure 9.5 GB was free (the owner-approved removal of `~/Library/Developer/Xcode/DerivedData` had already been done).
  - The full iOS rebuild recreated DerivedData; 6.4 GB was free at the end.
  - Large shared caches were left in place, and no further cleanup was needed: CoreSimulator 19 GB, `~/.gradle` 12 GB, `~/.konan` 6.7 GB,
    `app/shared/build` 1.2 GB.
- **Tests:**
  - `:app:shared:iosSimulatorArm64Test --rerun-tasks` on final code: PASS 246/0, including `ResponsiveLayoutRulesTest` 11/0 and
    `CompanyDetailsPresentationTest` 12/0.
  - Android targeted rerun of `ResponsiveLayoutRulesTest`, `CompanyDetailsPresentationTest`, `CompanyDetailsViewModelTest` and
    `MetricLearnTermsTest`: PASS 27/0.
  - Full iOS MOCK rebuild: PASS.
- **Shared badge rule 14 → 20 (impact audit):**
  - `StockAssessmentRow` is used only on Company Details: the Understand rows and the Why Did It Move? sources sheet, which has no badge.
  - Of all the assessment badges, only "More cash than debt" and "More debt than cash" (19 characters) fall in the newly inline 15–20 range;
    the others are 7–12.
  - The valuation-screen labels ("Near Historical Average", 23–24 characters) don't use this rule and stay as before.
- **Section bar after asynchronous loading and with optional sections absent (Android):** PASS.
  - Opening a company ~0.4 s after the tap still shows the search screen, so no half-built bar was seen.
  - Loaded pages show all four tabs. An ETF without the same data (AAPY) also shows all four, because its About section exists.
  - An uncached company (MSFT) opened while the server was unreachable rendered from the app's cache with the full bar and no errors, so a truly
    empty overview couldn't be reproduced on device.
  - The absent-section rule is covered by `sectionBarListsOnlySectionsThePageShows` (`sections(null)` = Overview, News; no About without a
    description, country or classification).
- **Selection and Back:**
  - Android: tabs scroll and select; Back from See Financials restores position and selection. PASS.
  - iOS: the Financials tab selects; Back from Apple Financials keeps the position and the Financials selection; flings up and down update the
    selection (News near the bottom, Overview near the top). PASS.
- **iOS scrolling performance:** fast flings up and down rendered without visible hitching or blank frames on the simulator. This is an
  observation, not an Instruments profile: NOT RUN.
- **Contextual Learn (5D.5.3) preserved:**
  - Android: Microsoft P/E tile → sheet "Learn more about P/E ratio" → "What is the P/E ratio?" → Back to the stock at the same position. PASS.
  - iOS: Apple P/E sheet → term → Back to Overview. PASS.
- **Large text:**
  - Android 1.3× (new): stats, badges, bar and "What Apple does" fit without clipping. PASS.
  - iOS AX XL (new): compact axis (first and last date), stacked stats, the badge stacks as a pill under "Financial Health", no overflow. PASS.
  - Android 2.0× and iOS AX5 were checked earlier (§4).
- **Tracked, not resolved:**
  - iOS live Dynamic Type refresh (existing limit; checks were made after reopening the screen);
  - TalkBack NOT RUN;
  - VoiceOver BLOCKED (needs a device);
  - signed-in iOS BLOCKED (guest mode);
  - physical devices and smaller phones NOT RUN;
  - an Instruments scroll profile NOT RUN.
- **Review package:** unchanged, because no meaningful change was found. Closure captures are in `docs/reviews/phase5d_5_4a/captures/closure/`
  (git-ignored).
- **Next:** Phase 5D.5.4B (At a Glance and Understand) can begin after owner approval.

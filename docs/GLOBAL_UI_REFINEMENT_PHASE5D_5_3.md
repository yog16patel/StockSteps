# Global UI Refinement Phase 5D.5.3: contextual learning integration

- **Status:** implemented and verified on Android and iOS against the local MOCK server. committed as "Add contextual Learn links for Global UI Refinement Phase 5D.5.3 with an info mark on metrics that have a Learn term, explanation sheets for Financials and Valuation rows, and Learn more buttons that open the term page on the same stack on Android and iOS" (baseline `600c295`,
  "Record the closure of Global UI Refinement Phase 5D.5.2 Learn with final verification, remaining checks and follow-ups").
- **Plan:** `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_3_PLAN.md`. Approved as A, then B+C, then D, with D-C1 to D-C6 as recommended.
- **Review package:** `docs/reviews/phase5d_5_3/` (report, Android/iOS contact sheets, focused diff).

## 1. What changed
- **Glance tiles** (Company Details): tiles that have a Learn term show a small ⓘ after the label: Market Cap, P/E Ratio, Revenue Growth and
  Dividend Yield. The whole tile stays tappable (D-C2). The existing sheet keeps its company sentence and adds
  **"Learn more about {term}"** (D-C5).
- **Financials and Valuation rows** are now tappable. Each opens a sheet with the row's label, the existing `MetricEducation` text and, only when
  the metric has a term, the Learn button. The ⓘ marks only the mapped rows.
  - Metrics without a term (operating/gross margin, P/S, P/B, EV/EBITDA, PEG, ROE…) get the sheet with no button (D-C6).
- **Section explanations** ("What is this?") in Financials add a Learn link when the section has a term (Revenue, EPS). The Understanding P/E card
  on Valuation adds "Learn more about P/E ratio".
- **Navigation:**
  - Android pushes `LearnTermRoute(id)` on the same back stack, so Back returns to the same stock and scroll position, and the Learn tab is
    highlighted while the term is open (D-C3).
  - iOS pushes `LearnTermView` on the same `NavigationStack`; the back button names the previous screen ("Apple Financials").
  - No toast on return (D-C4). The layout is otherwise unchanged (D-C1).
- **No new educational text**, and no backend, calculation, entitlement or global-theme changes.

## 2. Files
- **core:**
  - `learning/MetricLearnTerms.kt` (new): metric id → Learn term, plus `MetricExplanation`.
  - `companydetail/CompanyOverview.kt`: `InfoRow.metricId`, optional, default null.
  - `FinancialStatementsPresentation.kt`: 25 rows tagged with a `metricId`.
  - `ValuationPresentation.kt`: 9 rows tagged with a `metricId`.
  - Test: `learning/MetricLearnTermsTest.kt` (new, 7 tests).
- **shared (Compose):**
  - `designsystem/components/StockMetric.kt`: `info` flag, `StockMetricLabel`, `StockInfoRow(onClick, onClickLabel, info)`; all optional with
    unchanged defaults.
  - `learn/BeginnerEducationUi.kt`: `MetricExplanationSheet` and `MetricLearnMore`.
  - `companydetails/*`: `CompanyDetailsAction.LearnTerm`; scenes and screens wired.
  - `AppNavigation.kt`: `onLearnTerm` → `LearnTermRoute`.
- **iOS:**
  - `DesignSystem/StockControls.swift` (`info`, `StockMetricLabel`) and `StockDetailComponents.swift` (`StockInfoLine.info`).
  - `LearnViews.swift`: `MetricExplanationSheet`, `MetricLearnMoreButton`, `ExplainableInfoLine`.
  - `CompanyDetailsScreen/Scene.swift`, `CompanyFinancialsScene.swift`, `CompanyValuationScene.swift`.

## 3. Mapping rule
`MetricLearnTerms.termFor(metricId)` works on ids only, never on visible titles:
1. An exact Learn term id wins.
2. Otherwise the single term whose `relatedMetrics` lists the metric.
3. Otherwise null; ambiguous matches are never guessed.

`cash` is listed by both the cash and debt terms and resolves to `cash` through rule 1. The test pins all 12 mapped metrics and the metrics that
have no term.

## 4. Verification
### Automated
| Suite / build | Result |
|---|---|
| `:core:jvmTest` | PASS 541/0 on rerun. The full run had 1 failure in `PortfolioSessionStateTest` (known 5 s timeout flake, unrelated; its fix is in the separate Portfolio worktree). |
| `:core:iosSimulatorArm64Test` | PASS 541/0 |
| `:app:shared:testAndroidHostTest` | PASS 246/0 |
| `:app:shared:iosSimulatorArm64Test` | PASS 242/0 |
| `:server:test` | PASS 501/0 |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` (generic, and iPhone 16 MOCK ad-hoc build) | PASS |

After the suites ran, one iOS line changed: the glance sheet now looks up its term by `educationId`, the same key the ⓘ and Android use. It was
rebuilt (PASS) and re-checked on device. No Kotlin changed after the test run.

### Android: emulator-5554, 360 dp, MOCK, Apple
| Check | Result |
|---|---|
| Glance ⓘ on Market Cap, P/E, Revenue Growth, Dividend Yield (1.0×, light) | PASS |
| P/E sheet: company sentence + "Learn more about P/E ratio" → term page; Learn tab highlighted; Back returns to the same stock and position | PASS |
| Financials: ⓘ on Net Income and Net Margin only; Net Margin sheet → "Learn more about profit margin" | PASS |
| Financials: Operating Margin sheet has no button; Operating and Gross Margin have no ⓘ | PASS |
| Valuation: "Learn more about P/E ratio" link → term; Back → Apple Valuation | PASS |
| Valuation: P/S sheet shows its explanation, no Learn button | PASS |
| 2.0× glance: tiles stack, ⓘ inline after the label, no clipping | PASS |
| Dark theme | PASS at closure (Settings → Appearance → Dark, then restored to Light): glance ⓘ, P/E sheet with button, P/S sheet without; see §8 |

### iOS: iPhone 16 simulator, MOCK, guest mode (never signed in)
| Check | Result |
|---|---|
| Glance ⓘ on the four mapped tiles (Default, light) | PASS |
| P/E sheet + "Learn more about P/E ratio" → term page → Back returns to Apple at the same scroll position | PASS |
| Financials: Revenue "What is this?" shows "Learn more about revenue"; ⓘ on Net Income and Net Margin only | PASS |
| Net Margin sheet → "What is profit margin?" → Back to Apple Financials at the same position | PASS |
| Operating Margin sheet: explanation, no button | PASS |
| Valuation: P/E Learn link → term → Back; ⓘ only on Dividend Yield, P/E and Revenue Growth | PASS |
| Dark (Default size): Valuation link and rows readable | PASS |
| AX5 (dark): glance labels wrap with the ⓘ kept on the last word; sheet button wraps to full width and is reachable by scrolling | PASS |

## 5. Known issues and limits (outstanding items)
1. **iOS live Dynamic Type refresh:** switching to AX5 while Valuation is open resizes the headings but not the rows until the screen is
   reopened. This is the existing live-refresh limit noted in `CLAUDE.md`, not new in this phase. A fresh render at AX5 is correct.
2. **TalkBack and VoiceOver:**
   - TalkBack: NOT RUN (its first start asks for a notification permission the owner must answer).
   - VoiceOver: BLOCKED (needs a device).
   - Labels, roles and target sizes were checked in code and in the UI tree (§8), but not by ear.
3. **iOS signed-in regression:** BLOCKED (guest mode only; Claude never signs in). The changed screens don't depend on sign-in, but signed-in
   navigation around them is unverified.
4. **Android Company Details "Key Ratios" card:** still non-interactive. This is a contextual-learning opportunity (its EPS and Debt to Equity
   rows have terms), left for a later phase; not in the approved scope.
5. **Intermittent `PortfolioSessionStateTest` timeout** (core JVM, 5 s): failed once in the full run and passed on rerun. Unrelated to this
   phase; the candidate fix is uncommitted in worktree `charming-hodgkin-98e850`, which this phase did not touch.
- Also noted: the iOS "Understand Apple" → Valuation row opens the P/E sheet (existing behaviour), which now also offers the Learn button.

## 6. Deviations from the plan
- One alignment fix (iOS glance sheet keyed by `educationId`, see §4). Nothing else.

## 7. Verdict
**PASS WITH CONDITIONS**: approved by the owner and closed; items 2 and 3 in §5 remain open.

## 8. Closure verification (2026-10-15)
- **Scope:**
  - The implementation is commit `3b7a7f5` ("Add contextual Learn links for Global UI Refinement Phase 5D.5.3 …", pushed): 36 files, all in this
    phase (core mapping and test, tagged rows, shared/iOS Company Details, Financials and Valuation screens, design-system parameters, docs,
    review package, reference images).
  - The working tree was clean before closure, and the Portfolio worktree `charming-hodgkin-98e850` was not modified.
  - The closure commit contains documentation and the updated Android contact sheet only.
- **Android dark (in-app theme):** PASS.
  - Setup: emulator-5554, MOCK, 1.0×, Settings → Appearance → Dark.
  - Glance ⓘ markers are visible on the dark surface.
  - The mapped P/E sheet shows the company sentence and "Learn more about P/E ratio" (primary fill, white text).
  - The unmapped Valuation P/S sheet uses the same handle, title and body, with no button.
  - The theme was restored to Light afterwards; the MOCK server is stopped and `adb reverse` removed.
  - Captures: Android contact sheet tiles 2 and 5; originals in `docs/reviews/phase5d_5_3/captures/` (git-ignored).
- **Mapped vs unmapped sheet consistency:** PASS.
  - Both kinds go through one component per platform: Compose `MetricExplanationSheet` / `EducationSheet`, and iOS
    `MetricExplanationSheet` / `InfoSheet`.
  - The Learn button (`MetricLearnMore` / `MetricLearnMoreButton`) renders nothing when the term is null, so an unmapped sheet differs only by the
    missing button.
  - Confirmed on device in light and dark (Android) and in light, dark and AX5 (iOS).
- **Accessibility (code and UI tree):** PASS, with screen readers still open (§5 item 2).
  - Sheet titles are headings.
  - Tappable rows use `clickable(onClickLabel = "Explain {label}", role = Role.Button)` with a 48 dp minimum height (iOS: a 48 pt `Button`
    with an accessibility hint).
  - The ⓘ is decorative; the click label carries the meaning.
  - UI tree at 480 dpi: the Learn button is 144 px tall (48 dp) and the sheet handle 144 × 144 px.
  - The sheet closes before navigating.
  - Observed outside this phase: the Settings theme chips don't report a selected state in the UI tree (seen while switching the theme for
    this check).

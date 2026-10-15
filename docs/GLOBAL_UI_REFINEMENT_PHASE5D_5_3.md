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
| Dark theme | NOT RUN (the app keeps its own Light setting, so system night mode has no effect; not changed for this check) |

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

## 5. Known issues and limits
- **iOS live Dynamic Type:** switching to AX5 while Valuation was open resized the headings but not the rows until the screen was reopened. This
  is the existing live-refresh limit noted in `CLAUDE.md`, not new in this phase. A fresh render at AX5 is correct.
- **iOS "Understand Apple" → Valuation row:** this row opens the P/E sheet (existing behaviour), which now also offers the Learn button.
- **Company Details "Key Ratios" card** (Android) stays non-interactive. It is not in the approved scope.
- **Screen readers:**
  - TalkBack: NOT RUN.
  - VoiceOver: BLOCKED (needs a device).
  - The rows carry "Explain {label}" labels and hints, but they are not verified by ear.
- **iOS signed-in flows:** BLOCKED (guest mode only; Claude never signs in). The changed screens don't depend on sign-in.

## 6. Deviations from the plan
- One alignment fix (iOS glance sheet keyed by `educationId`, see §4). Nothing else.

## 7. Verdict
**PASS WITH CONDITIONS**: Android dark, TalkBack and VoiceOver remain open. Committed and pushed at the owner's request; the open checks above remain.

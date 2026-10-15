# Review package: Phase 5D.5.3 contextual learning (implementation)

## 1. Phase and Git baseline
- **Phase:** Global UI Refinement 5D.5.3.
- **Base:** `600c295`, "Record the closure of Global UI Refinement Phase 5D.5.2 Learn…".
- **Under review:** the commit "Add contextual Learn links for Global UI Refinement Phase 5D.5.3 with an info mark on metrics that have a Learn term, explanation sheets for Financials and Valuation rows, and Learn more buttons that open the term page on the same stack on Android and iOS".

## 2. Changes implemented
- Company Details glance tiles that have a Learn term show ⓘ. Their sheet keeps the company sentence and adds **"Learn more about {term}"**.
- Financials and Valuation rows open an explanation sheet. Only rows with a term show ⓘ and the Learn button; the other rows get the sheet alone.
- Section "What is this?" panels (Revenue, EPS) and the Understanding P/E card add a Learn link.
- Term pages open on the same stack; Back returns to the same stock and scroll position.
- No new educational text, and no backend, calculation or theme changes.

## 3. Affected files
- **core:** `MetricLearnTerms.kt` (new) + test, `CompanyOverview.kt` (`InfoRow.metricId`), `FinancialStatementsPresentation.kt`,
  `ValuationPresentation.kt`.
- **shared:** `StockMetric.kt`, `BeginnerEducationUi.kt`, `companydetails/*` (Action, Scene, Screen, Financials, Valuation), `AppNavigation.kt`.
- **iOS:** `StockControls.swift`, `StockDetailComponents.swift`, `LearnViews.swift`, `CompanyDetailsScreen/Scene.swift`,
  `CompanyFinancialsScene.swift`, `CompanyValuationScene.swift`.

## 4. Architectural decisions
- **One core mapping** (`MetricLearnTerms`), by id: exact term, then a unique `relatedMetrics` owner, else null. Both platforms call it.
- **Rows carry ids** (`InfoRow.metricId`), never matched by title.
- **New component parameters** (`info`, `onClick`) are optional with unchanged defaults.
- **Navigation** reuses the existing Learn routes and views.

## 5. Tests and builds
| Suite / build | Result |
|---|---|
| core JVM | PASS 541/0 on rerun (known `PortfolioSessionStateTest` timeout flake in the full run) |
| core iOS | PASS 541/0 |
| shared Android | PASS 246/0 |
| shared iOS | PASS 242/0 |
| server | PASS 501/0 |
| `assembleDebug` | PASS |
| `xcodebuild` | PASS |

## 6. Android verification
emulator-5554, 360 dp, MOCK:
- **PASS:** glance ⓘ; P/E sheet → term (Learn tab highlighted) → Back; Net Margin sheet with Learn; Operating Margin and P/S sheets without it;
  Valuation link → term → Back; 2.0× glance without clipping; dark via the in-app theme at closure (P/E sheet with button, P/S sheet without).
- Sheet: `CHATGPT_UI_REVIEW_ANDROID.png`.

## 7. iOS verification
iPhone 16, MOCK, guest:
- **PASS:** glance ⓘ; P/E sheet → term → Back; Revenue section link; Net Margin → term → Back to Financials; Operating Margin without a button;
  Valuation link and row ⓘ; dark; AX5 glance and sheet button.
- Sheet: `CHATGPT_UI_REVIEW_IOS.png`.

## 8. Known issues
- iOS rows resize on a live AX5 switch only after the screen is reopened. This is the existing Dynamic Type live-refresh limit.
- The Android Company Details "Key Ratios" card is not tappable (out of scope).

## 9. Unverified scenarios
- **NOT RUN:** TalkBack.
- **BLOCKED:** VoiceOver (needs a device); iOS signed-in flows (guest only, never signed in).

## 10. Deviations
- One line: the iOS glance sheet looks up its term by `educationId`, the same key the ⓘ and Android use. Rebuilt and re-checked.

## 11. Owner decisions
- Closed by the owner on 2026-10-15 (report §8).
- Optional follow-ups: make the "Key Ratios" rows explainable; fix the intermittent `PortfolioSessionStateTest` timeout (separate worktree).

## 12. Links
- Report: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_3.md`; plan: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_3_PLAN.md`.
- Focused diff: `CHATGPT_FOCUSED_DIFF.md`.
- Plan-stage mockups: `CHATGPT_UI_REVIEW_PROPOSED.png` and `docs/design/phase5d_5_3/`.

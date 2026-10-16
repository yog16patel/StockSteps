# UI/UX Refinement Phase 2 — Shared Top Bars, Safe Areas & Accessibility (KAN-16)

Status: committed and pushed as "Fix iOS landing content under the status bar and shorten company page titles for UI/UX Refinement Phase 2 (KAN-16) with a page-background status strip, ticker Financials and Valuation titles, a symbol Back label from Company Details and trailing Done on iOS sheets" (owner and ChatGPT review pending). KAN-16 is **not** Done.
Base: `77a2656` "Refine Home for UI/UX Refinement Phase 1 (KAN-14) …" (on `c6c3407` DEBUG API logging); clean tree at start.
Review package: `docs/reviews/top_bar_ui_phase2/`.

## 1. Pre-flight
- `git status` clean; `git log -5`: `77a2656` (Home Phase 1), `c6c3407` (DEBUG API logging), `44d0247` (5D.5.6D), `4c5990f`, `48f5a3a`.
  Home Phase 1 and DEBUG API logging are both committed and pushed; there was no parallel-session work in this checkout.
- Read: `CLAUDE.md`, `docs/AI_REVIEW_WORKFLOW.md`, handoff / status, `docs/NAVIGATION_TOP_BAR_PHASE5D_5_6_PLAN.md` §7 (landing bar) and the
  5D.5.6D and Home Phase 1 packages.

## 2. Inventory (before)

### 2.1 Landing pages (Home, Markets, Portfolio, Watchlist, Learn)
| | Android (Compose) | iOS (SwiftUI) |
|---|---|---|
| Component | `StockLandingTopBar` (shared), drawn as the first scroll item | `StockLandingTopBar` (DesignSystem), first scroll item |
| Heading | brand mark (Home) / `screenTitle` (others) from `LandingTopBarDisplay` | same |
| Actions | page actions, then Notifications (bell) and Settings (gear); 48 dp targets | same; 48 pt targets, fixed 20 pt symbols |
| Large text | icons stay on the title line at 2.0× (verified) | icons move to a row under the title via `StockLayout.metricRowStacked` (Home at AX5) |
| Safe area | `Scaffold` applies the status-bar inset; content is clipped below it | **Defect:** the tab root hides the navigation bar (`.stockStepsTopBar(.screen(…, visible: false))` in `AppScene.tabStack`), so nothing covers the status-bar inset; scrolled content ran under the clock and battery on every landing page |
| Consistency | title baseline, icon order and spacing identical across tabs (light/dark, 1.0×/2.0×) | identical across tabs |

### 2.2 Child destinations
| Screen | Android | iOS | Finding |
|---|---|---|---|
| Company Details | own bar: Back icon ("Back") + star; no title (header names the company) | system bar: Back + star; no title | iOS: no `navigationTitle`, so every page pushed from it showed a bare **"‹ Back"** |
| Financials | own bar: "%1$s Financials" with `shortName`, 1 line, ellipsis | inline title "\(shortName) Financials" | **Defect:** long names truncated the page name: "Pinnacle Food Group Limited Class A C…" (iOS; Android uses the same string in one ellipsized line) and squeezed iOS Back to a chevron |
| Valuation | "%1$s Valuation" | "\(shortName) Valuation" | same as Financials |
| News, Why it moved, Explained simply | "Company news", "Why did PFAI move?", "Explained simply" | same | fine (short) |
| Learn Term | app bar "Learn" | "‹ Learn", no bar title (page heading), Learn pattern | fine |
| Guided Research, Practice order, Holding details, Earnings, Daily Brief | app-bar titles from `AppNavigation` | `navigationTitle` per scene | fine; titles are short constants |
| Search | full-screen "Search stocks" | sheet "Search stocks" + trailing **Done** | fine |
| Notifications, Settings | pushed with Back (Android convention) | sheets with **leading "Done"** (`.cancellationAction`) | **Inconsistent:** every other iOS sheet puts Done trailing (`.confirmationAction`) and Cancel/Close leading |
| Back labels (iOS) | — | native titles + `AppDestination.backTitle` for cross-tab hops ("‹ Home", "‹ AAPL") | Company Details was the only parent without a title |

No duplicated top-bar implementations needed consolidation: Android has one `StockStepsTopBar` and one `StockLandingTopBar`; iOS has
`StockLandingTopBar` and the system bar via `.stockStepsTopBar` / `navigationTitle`.

## 3. Plan and decisions
| ID | Decision | Why |
|---|---|---|
| T1 | **iOS status-bar backdrop** at the container level: `.landingStatusBarBackdrop()` on each tab root in `AppScene.tabStack` | One place covers all five landing pages; pushed screens keep the system bar |
| T2 | Backdrop = the page background (`appBackground`) filling exactly the top inset, drawn above the content; no material, no divider | Matches Android (content clipped at the status bar), theme-aware, native status-bar text unchanged |
| T3 | Financials / Valuation titles use the ticker: `FinancialsDisplay.title` → "PFAI Financials", `ValuationDisplay.title` → "PFAI Valuation" (both platforms) | Never truncates the page name; matches existing "Why did PFAI move?" / "PFAI alerts"; the company name stays in the page header |
| T4 | iOS Company Details sets `navigationTitle(symbol)` with a blank principal item | Children get "‹ PFAI" instead of "‹ Back" (`AppDestination.backTitle` convention); the bar stays title-free (Learn pattern) |
| T5 | iOS Settings and Notifications sheets: Done → `.confirmationAction` (trailing) | The convention of every other sheet in the app |
| T6 | No change to Android landing or child bars beyond T3 | Already consistent; 2.0× fits; Back has a "Back" label; 48 dp targets |
| — | Not changed | routing, stacks, cross-tab trail, reselection, Portfolio nested tabs, Home Phase 1 layout, data, requests |

Trade-offs: the opaque strip is a hard edge (no blur) — chosen for contrast and parity with Android; a material would show tinted content under
the clock. Ticker titles are less friendly than "Microsoft Financials" but never truncate; the full name is the page's first line.

## 4. iOS safe-area fix
Cause: landing pages hide the navigation bar and draw their own header inside the `ScrollView`. With no bar, the status-bar inset has no
background; SwiftUI scrolls content through the top safe area, so text ran under the clock (Home Phase 1 §5).
Fix (`DesignSystem/StockLandingTopBar.swift`):
```swift
content.overlay(alignment: .top) {
    StockStepsTheme.colors(scheme).appBackground
        .ignoresSafeArea(edges: .top)   // grows up through the inset only…
        .frame(height: 0)               // …from a zero-height frame at the safe-area top
        .allowsHitTesting(false).accessibilityHidden(true)
}
```
- The first attempt (`.frame(height: 0).ignoresSafeArea(.top)`) drew nothing: the fixed frame wins. The order above is required (verified).
- No layout change: the content keeps its own safe area, so there is no extra top padding or blank area (landing titles sit where they did).
- No view identity change, no state, no request; tab scroll positions, reselect-to-top and pull to refresh are unchanged (verified).
- Light/dark: the strip uses the theme's page background; Dynamic Type has no effect on it (verified at AX5).

## 5. Files
| Module | File | Change |
|---|---|---|
| iOS | `app/iosApp/iosApp/DesignSystem/StockLandingTopBar.swift` | `LandingStatusBarBackdrop` + `.landingStatusBarBackdrop()` |
| iOS | `app/iosApp/iosApp/AppScene.swift` | backdrop on every tab root; Settings / Notifications Done trailing |
| iOS | `app/iosApp/iosApp/CompanyDetailsScreen.swift` | `navigationTitle(symbol)` + blank principal |
| iOS | `app/iosApp/iosApp/CompanyFinancialsScene.swift`, `CompanyValuationScene.swift` | titles from `FinancialsDisplay.title` / `ValuationDisplay.title` |
| shared | `presentation/companydetails/FinancialsDisplay.kt`, `ValuationDisplay.kt` | `title(symbol)` |
| shared (Android) | `presentation/companydetails/CompanyFinancialsScene.kt`, `CompanyValuationScene.kt` | use the display titles |
| shared | `composeResources/values/strings.xml` | removed the now-unused `financials_title`, `valuation_title` |
| tests | `FinancialsDisplayTest.kt`, `ValuationDisplayTest.kt` | title tests (+2) |

## 6. Verification (local MOCK :8083; Android emulator-5554 signed-in test session; iPhone 16 guest)
| Scenario | Android | iOS |
|---|---|---|
| Five landing tabs, light: top + scrolled | PASS (before; no landing change) | PASS before/after — before: content under the clock on Home, Markets, Learn; after: clean strip |
| Five landing tabs, dark | PASS (before) | PASS (Learn scrolled before/after) |
| Home top / middle / bottom | PASS (Home Phase 1 frames + this phase) | PASS top and middle; bottom covered by Home Phase 1 (unchanged) |
| Company Details, long name (PFAI) | PASS after (dark) | PASS before/after |
| Financials / Valuation, long name | PASS after ("PFAI Financials", "PFAI Valuation"); before NOT RUN (install restarted the app mid-capture; old title code path documented §2.2) | PASS before ("Pinnacle Food Group Limited Class A C…", bare chevron) / after ("‹ PFAI · PFAI Financials", "‹ PFAI · PFAI Valuation") |
| Learn Term | — (unchanged "Learn" bar) | PASS ("‹ Learn") |
| Search / Notifications / Settings | NOT RUN visually (unchanged pushes) | PASS: Search sheet; Notifications (guest, "My Alerts"); Settings (guest) Done trailing after |
| Large text | PASS 2.0× all five landing bars (one line, no overlap) | PASS AX5 live change: Learn (title + icons share a line), Home (icons stacked); status strip clean at AX5 |
| Back, swipe-back, tab switching | PASS (Back from Company Details) | PASS: Back keeps Home's scroll; swipe-back Financials → Company Details at its reading position; Home kept scroll across 5 tab switches; reselect scrolls to top |
| Requests (DEBUG API log) | 0 `REQ` over 6 tab switches (channel live: 6 on opening a company) | 0 `REQ` over 5 tab switches after first visits |
| Signed-in iOS / guest Android | — | BLOCKED (no approved iOS test account; Android sign-out not allowed) |
| TalkBack / VoiceOver | NOT RUN | NOT RUN |

Accessibility: Android Back icon "Back", landing icons 48 dp with labels from `LandingTopBarDisplay`; iOS landing icons 48 pt, system Back says
"Back" plus the label, the hidden-title principal item is `accessibilityHidden`, the backdrop is hidden and not hit-testable. Contrast: the
strip is the page background, so status-bar text keeps the system contrast in both themes.

### Tests and builds
| Suite / build | Result |
|---|---|
| `:core:jvmTest` | First run FAIL 606/1 (`PortfolioSessionStateTest.anEndedSessionIsSessionExpiredUntil…`, known flake); `--rerun` PASS 606/0 |
| `:core:iosSimulatorArm64Test` | First run FAIL 604/1 (`PortfolioSessionStateTest.aChangeThatFailsWithAnEndedSessionMarksItExpired`, same flaky class); `--rerun` PASS 604/0 |
| `:app:shared:testAndroidHostTest` | PASS 278/0 (+2 new) |
| `:app:shared:iosSimulatorArm64Test` | PASS 275/0 (+2 new) |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` (final, with all iOS changes) | PASS |

The flake is pre-existing and core-only (this phase changed no core code); its test-only fix is uncommitted in worktree `charming-hodgkin-98e850`.

**Recommendation: APPROVE WITH FIXES** — the iOS status-bar defect and the truncated child titles are fixed and verified; owner passes remain
(signed-in iOS, screen readers) and decisions T2 (opaque strip) and T3 (ticker titles).

## 7. Known limitations
- Android child-title BEFORE frames: NOT RUN (see §6). Android Notifications/Settings/Search visuals not re-captured (unchanged).
- Other iOS parents with an empty `navigationTitle` (Portfolio Insights, Discover's root) would also give children a bare "‹ Back"; not
  observed in this pass, left unchanged (follow-up candidate).
- The strip is opaque, without a scroll-edge divider; a hairline on scroll would need scroll-offset state per landing page (not added).
- Android Company Details keeps an untitled bar (as iOS); its Back is icon-only by platform convention.

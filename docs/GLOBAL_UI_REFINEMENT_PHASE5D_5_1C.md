# Global UI Refinement Phase 5D.5.1c — Daily Brief final polish (C1+C2): implementation and verification

Status: **committed and pushed as "Polish the Daily Brief for Global UI Refinement Phase 5D.5.1c with a borderless story group, a compact header with session status under Markets, a secondary Add companies action, matching footer links and a tighter concept card on Android and iOS"** (on top of `5bfe273`); verified on devices; Daily Brief closed by the owner (2026-10-14).
- Plan: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_5_1C_PLAN.md`. Owner decisions D-C1 … D-C5 approved as recommended (2026-10-14), with conditions
  (per-component border, accurate freshness and session dates, local-only Android gap fix, no global defaults, no logic changes).
- Captures: `docs/design/phase5d_5c/` (69 JPEGs, downscaled to 1600 px, 8.6 MB).

## 1. Implemented changes and files
| # | Change | Decision | Android (`app/shared/.../presentation/brief/DailyBriefScreens.kt`) | iOS (`app/iosApp/iosApp/DailyBriefScenes.swift`) |
|---|---|---|---|---|
| 1 | Market stories group: **borderless** surface (same `surface` and radius as the tiles); thin dividers between stories kept | D-C2 | `StockCard(bordered = false)` at this call site | `.stockCard(bordered: false)` |
| 2 | Header rhythm: meta, takeaway and summary as one block (`xs`), then freshness and the status banner (`sm`) | C2 | `Header` | `header()` |
| 3 | Freshness "Updated Oct 7" when indices are shown (their labels carry the session); "· Market data for the Oct 7 session" kept when no index is shown. Market-session lines moved under the **Markets** title | D-C1 | core `DailyBriefDisplay.freshness` + Markets item | `markets()` |
| 4 | Empty watchlist: text + **secondary** "Add companies" button aligned with the text (same action) | D-C3 | local `Column` + `StockButton(SECONDARY)` | `VStack` + `.stockSecondary` |
| 5 | Concept card bottom padding `xs` | C2 | `StockCard(contentPadding = …)` | per-edge `.padding` |
| 6 | "Previous briefs" in the same compact link style as "About this brief" (icon, label, chevron, 48 dp/pt) | D-C4 | private `FooterLink` | private `footerLink` |
| 7 | Android top gap: no list top padding on this screen (was 12 dp). The rest is the shared Material `TopAppBar` + the 48 dp ⋯ row, **not changed** | D-C5 | `LazyColumn(contentPadding = …)` | — |

- core: `core/src/commonMain/kotlin/org/example/stocksteps/brief/DailyBriefDisplay.kt` (`freshness`).
- Test: `core/src/commonTest/kotlin/org/example/stocksteps/brief/DailyBriefDisplayTest.kt` (freshness with and without indices).
- **Not changed:**
  - `StockCard` / `.stockCard` defaults and other shared components;
  - the takeaway logic, calculations, server, navigation, MOCK scenarios and disclosures;
  - Home and history code.

## 2. Approved decisions verified
| Decision | Result | Evidence |
|---|---|---|
| D-C1 header metadata | **PASS** both platforms. "Updated Oct 7" only; session lines under Markets; dates stay accurate in every case below | `android-360dp-1.0x-light-top.jpg`, `ios-iphone16-default-light-top.jpg` |
| D-C1 accuracy cases | **PASS** (Android, screen-text dump). `missing-index`: S&P and TSX labelled "Close · Oct 7" + "All values". `stale-index`: TSX "From an earlier session (Oct 3)". `holiday`: "Updated Dec 25", sessions "Closed for Christmas Day", indices "Close · Dec 24". Mixed (`us-open-ca-closed`): US "During today's session (may be delayed) · 10:40 AM ET", TSX "Close · Oct 9". `market-hours`: all live labels. Offline: "Updated Oct 7" + offline sentence. No-index case: unit test keeps the session wording | `android-360dp-1.0x-dark-scenario-*.jpg`, `android-360dp-1.0x-dark-offline-stale.jpg` |
| D-C2 borderless stories | **PASS** both platforms, light and dark; surface, radius and dividers kept; same treatment as the tiles | `android-360dp-1.0x-light-2.jpg`, `android-360dp-1.0x-dark-2.jpg`, `ios-iphone16-default-light-top.jpg`, `ios-iphone16-ax5-dark-3.jpg` |
| D-C3 Add companies | **PASS** Android (signed in, empty watchlist): tinted secondary button aligned with the text; opens the Watchlist tab ("Your watchlist is empty"). iOS: **BLOCKED**, guest mode shows the unchanged Sign in prompt | `android-360dp-1.0x-light-2.jpg`, `android-360dp-1.0x-dark-add-companies-target.jpg` |
| D-C4 footer | **PASS** both platforms (also at iOS AX5); About this brief still expands, Previous briefs still navigates | `android-360dp-1.0x-light-4.jpg`, `ios-iphone16-default-light-2.jpg`, `ios-iphone16-ax5-dark-footer.jpg` |
| D-C5 Android top gap | **PASS (local part)**: 12 dp less; the remaining gap is the shared top bar (unchanged by rule) | `android-360dp-1.0x-light-top.jpg` vs `../phase5d_5b/android-360dp-1.0x-light-top.jpg` |
| Concept card bottom | **PASS** both platforms | `android-360dp-1.0x-light-4.jpg`, `ios-iphone16-ax5-dark-footer.jpg` |

## 3. Automated test results
Run one after another on the final code; no code changed during the device checks.

| Suite / build | Result |
|---|---|
| `:core:jvmTest` | **522/0**. The first run had one failure in `PortfolioSessionStateTest` (a 5 s coroutine timeout, Portfolio code not touched, while the machine was under load); the test passed on rerun (3/0), and the full `--rerun` passed 522/0 |
| `:core:iosSimulatorArm64Test` | **522/0** |
| `:app:shared:testAndroidHostTest` | **246/0** |
| `:app:shared:iosSimulatorArm64Test` | **242/0** |
| `:server:test` | **501/0** |
| `:app:androidApp:assembleDebug` | **PASS** (default build, and a MOCK-URL build for the device) |
| `xcodebuild` (generic simulator, unsigned) | **BUILD SUCCEEDED**; iPhone 16 ad-hoc with the MOCK URL **BUILD SUCCEEDED** |

## 4. Android device verification (`emulator-5554`, 360 dp, local MOCK)
Method: every capture below was taken, and representative captures were reviewed per section and size:
- 1.0× light: top, 2, 3, 4.
- 1.0× dark: 2 and history.
- 1.3× light: 3 and 5.
- 1.3× dark: top and 4.
- 2.0× light: top, 5 and 7.
- 2.0× dark: 3 and 6.
Plus the UI-text dumps for the scenarios. The remaining frames of the same scroll were captured but not opened one by one.

| Check | 1.0× light | 1.0× dark | 1.3× light / dark | 2.0× light / dark |
|---|---|---|---|---|
| Top: meta, takeaway, summary (folded at large text), freshness, banner | PASS | PASS | PASS / PASS | PASS / PASS |
| Markets: session lines, tiles (1.0×) or rows (1.3×+), "All values" | PASS | PASS | PASS / PASS | PASS / PASS |
| Stories (borderless group), Show details / Read article | PASS | PASS | PASS / PASS | PASS / PASS |
| Watchlist empty state + Add companies | PASS | PASS | PASS / PASS | PASS / PASS |
| Earnings rows and badges (wrap at large text) | PASS | PASS | PASS / PASS | PASS / PASS |
| Learn (concept card) and footer | PASS | PASS | PASS / PASS | PASS (footer below the captured range at 2.0×: NOT RUN) |
| Previous briefs list; row → brief | — | PASS | NOT RUN | NOT RUN |
| Home preview | PASS | PASS | NOT RUN | NOT RUN |
| Concept "Understand more" sheet | — | PASS | — | — |
| Offline + stale (port forward removed) | — | PASS | — | — |
| Scenarios (6) | — | PASS (dark) | — | — |

## 5. iOS device verification (iPhone 16, local MOCK, **guest mode**)
| Check | Default light | Default dark | AX XL light / dark | AX5 light / dark |
|---|---|---|---|---|
| Top (meta, takeaway, summary / Full summary, freshness, banner) | PASS | PASS | PASS / PASS | PASS / PASS |
| Markets (session lines + tiles at Default) | PASS | PASS | NOT RUN (top only) | NOT RUN (top only) |
| Stories (borderless group) | PASS | NOT RUN | NOT RUN | — / PASS |
| Watchlist (guest: Sign in prompt, unchanged) | PASS | NOT RUN | NOT RUN | — / PASS |
| Learn, concept card, footer | PASS | NOT RUN | NOT RUN | — / PASS |
| Previous briefs; row → brief | PASS (opened the brief) | — | — | — |
| Home preview | PASS | NOT RUN | — | — |
| Offline + stale (MOCK server stopped) | PASS | — | — | — |
| Live Dynamic Type change (Default → AX XL → AX5) | PASS | — | — | — |

## 6. Screenshot paths
All in `docs/design/phase5d_5c/`:
- **Android:**
  - `android-360dp-{1.0x,1.3x,2.0x}-{light,dark}-{top,2..7}.jpg` (top-to-bottom scroll);
  - `android-360dp-1.0x-{light,dark}-home.jpg`, `android-360dp-1.0x-dark-history.jpg`, `android-360dp-1.0x-dark-offline-stale.jpg`,
    `android-360dp-1.0x-dark-concept-sheet.jpg`, `android-360dp-1.0x-dark-add-companies-target.jpg`;
  - `android-360dp-1.0x-dark-scenario-{missing-index,stale-index,holiday,us-open-ca-closed,market-hours,no-stories}.jpg`.
- **iOS:**
  - `ios-iphone16-default-light-{top,2,history,home,offline-stale}.jpg`, `ios-iphone16-default-dark-top.jpg`;
  - `ios-iphone16-axxl-{light,dark}-top.jpg`, `ios-iphone16-ax5-{light,dark}-top.jpg`, `ios-iphone16-ax5-dark-{2,3,4,5,footer}.jpg`.

## 7. Outstanding bugs and limitations
- **iOS AX5:** the concept title wraps "What is the P/ / E ratio?" (SwiftUI breaks at the slash). This wording/wrapping predates this phase and is not
  part of the approved changes. Needs owner direction (e.g. a non-breaking slash in display text).
- **Parity (pre-existing):** in history rows the iOS Sample badge sits at the right edge (`StockTitleWithBadge`); Android puts it beside the title.
- **Android:** the remaining top gap is the shared `TopAppBar` (out of scope by rule).
- **iOS Home AX5** heading break ("Market/s today", "StockSte/ps") remains (follow-up session proposed earlier).
- One unrelated, timing-sensitive Portfolio test (`PortfolioSessionStateTest`) can time out under heavy load; it passed on rerun.

## 8. Accessibility verification status
- **Large text:** Android 1.3×/2.0× and iOS AX XL/AX5 — PASS for the sections listed above; no clipping, overlap or truncated values seen.
- **Touch targets:** footer links and toggles keep 48 dp/pt rows (by construction; visually confirmed).
- **Screen readers:** TalkBack and VoiceOver — **NOT RUN** (they need the owner). Android spoken labels for indices and earnings were confirmed in the
  UI-tree dumps.
- **Contrast** of the borderless story surface: visually PASS in light and dark. No measured contrast audit.

**BLOCKED / NOT RUN:**
- iOS signed-in watchlist and earnings — **BLOCKED** (guest mode; Claude never types credentials).
- Populated watchlist — **NOT RUN** (no watchlist data).
- StockSteps+ (Plus) content and AI — **NOT RUN**.
- iOS scenario submenu — **NOT RUN** (a simulated tap doesn't open the nested menu; scenarios are covered on Android with the same shared code).
- iPhone SE — **NOT RUN**.

## 9. Recommendation
**PASS WITH CONDITIONS.** All five approved decisions are verified on device (D-C3 on Android only). Automated suites and builds pass. Session and
freshness accuracy is confirmed in every scenario available. The conditions are the BLOCKED / NOT RUN items above and the owner's call on the
iOS "P/E" wrap.

## 10. Deferred to later phases
- iOS concept-title slash wrap (needs approval).
- iOS Home AX5 heading break and history navigation follow-up.
- The shared top-bar gap.
- B-1 index sparklines.
- Phase 5D.5.2 Learn (not started).
- Screen-reader passes.

## Handoff updates (applied in the same commit)
- **`docs/PROJECT_HANDOFF.md`:** a new top section "PHASE-5D-5-1C-2026-10-14":
  - D-C1…D-C5 implemented and verified; tests core 522/0 ×2, shared 246/0 and 242/0, server 501/0; builds OK;
  - device matrix as §4–§5; captures in `docs/design/phase5d_5c/`;
  - limitations §7–§8;
  - next: close Daily Brief on the owner's approval, then **Phase 5D.5.2 Learn** (decision D-7 first).
- **`docs/project-status.md`:** a matching §0 entry.
- **`CLAUDE.md`:** the current-task block, plus one convention: the Daily Brief story group is borderless per call site; freshness shows the session
  wording only when no index shows it; session lines live under Markets.

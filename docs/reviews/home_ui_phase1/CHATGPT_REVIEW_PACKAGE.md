# Home UI Refinement Phase 1 (KAN-14) — review package

## 1. Phase and Git baseline
- Phase: UI/UX Refinement Phase 1 — Home screen, Android (Compose) and iOS (SwiftUI). Jira KAN-14 (not marked Done).
- Base: `44d0247` "Stabilize navigation for Phase 5D.5.6D …". Under review: "Refine Home for UI/UX Refinement Phase 1 (KAN-14) with open sections, borderless row groups, a wider section rhythm, first-load skeletons and live Dynamic Type on Android and iOS", on top of the DEBUG API logging commit (separate).
- Report: `docs/HOME_UI_REFINEMENT_PHASE1.md` (audit, decisions, verification).

## 2. Changes implemented
- Home sections are **open**: title (and "View all" / "Clear" / Sample badge) on the page background, content below; no outline around
  every section. Markets, Watchlist highlights, Today in your watchlist, Upcoming & alerts, News for you and Recently viewed group their rows on
  one **borderless surface** with dividers.
- **Your portfolio** stays a card (one tap target with chevron), now borderless. Learn banner and Practice card unchanged.
- Rhythm: 20 dp/pt between sections (was 12), 20 at the bottom (was 12), header-to-content 4; the same as Learn and Daily Brief.
- Empty watchlist / section errors are plain text + action (no box). News stories are divided rows in one group (no card per story).
- Loading: skeleton placeholders on first load (Markets, Portfolio, and the iOS dashboard restore that used a full-width spinner); price
  refresh keeps rows visible with a thin bar on both platforms.
- iOS: Markets session lines, the Sample badge, the Learn banner and the Practice card now follow a **live** Dynamic Type change.
- Not changed: copy, order, values, requests, navigation, auth/guest rules, design-system defaults.

## 3. Affected files
- shared (Compose): `presentation/home/HomeComponents.kt`, `presentation/home/HomeScreen.kt`
- iOS: `HomeComponents.swift`, `HomeScreen.swift`, `PracticeScenes.swift` (`PracticeEntryCard`, Home-only consumer: one environment read)
- core / server / Android app module: none
- docs: `docs/HOME_UI_REFINEMENT_PHASE1.md`, this package, `docs/design/home_ui_phase1/` (6 downscaled frames), `docs/PROJECT_HANDOFF.md`,
  `docs/project-status.md`

## 4. Architectural decisions
- Borderless **per call site** via `StockCard(bordered = false)` / `.stockCard(bordered: false)`; the global defaults, `StockSectionHeader`,
  `StockRow` and the skeleton components are unchanged, so no other screen moves.
- Two small Home-local helpers per platform (`HomeSection`, `HomeRowGroup` / `.homeRowGroup()`) replace six hand-built card blocks; no new
  design-system component and no new token (`spacing.xl` is the existing open-section gap used by Learn and Daily Brief).
- Branch conditions for loading / error / empty are unchanged; only what each branch draws changed.

## 5. Test and build results (sequential, this working tree)
| Suite / build | Result |
|---|---|
| `:core:jvmTest` | PASS — 606 tests, 0 failures (fresh `--rerun`) |
| `:core:iosSimulatorArm64Test` | PASS — 604 tests, 0 failures (fresh `--rerun`) |
| `:app:shared:testAndroidHostTest` | PASS — 276 tests, 0 failures |
| `:app:shared:iosSimulatorArm64Test` | PASS — 273 tests, 0 failures |
| `:app:androidApp:assembleDebug` | PASS (`-PstockstepsMockBackendUrl=http://127.0.0.1:8083`) |
| `xcodebuild … -sdk iphonesimulator build` | PASS (final build after the Dynamic Type fixes) |

No new unit tests: no shared rule, `*Display` or presenter changed (`HomeDashboardPresentationTest` unchanged and passing).

## 6. Android verification (emulator-5554, Pixel 9 Pro, 411 dp, signed-in cached test session, local MOCK :8083)
- Light 1.0×: top, scrolled middle, scrolled bottom — PASS (before/after, scenario "watchlist-and-portfolio").
- Dark 1.0×: top, middle, bottom — PASS. 2.0× (dark): top and two scrolled frames — PASS, no clipping or broken words.
- Empty ("My saved companies" on a fresh MOCK store) — PASS. Error/offline (MOCK server stopped): brief "Offline copy", watchlist and alerts
  messages with Try again — PASS (before/after).
- Restored: theme Light, font scale 1.0, scenario "My saved companies" (not persisted; reset on relaunch).

## 7. iOS verification (iPhone 16 simulator, guest, local MOCK :8083)
- Default size light: top, middle, bottom — PASS (before/after, "watchlist-and-portfolio"). Dark: top, bottom — PASS.
- AX5: before = session lines and badge stayed small after a live switch; after = PASS live and after relaunch (Markets, Learn banner,
  Practice card, Recently viewed).
- Empty and error/offline (server stopped) — PASS after; error before NOT RUN.
- Restored: appearance Light, content size Large, scenario default.

## 8. Known issues
- Pre-existing, not changed: iOS landing content scrolls under the status bar; Compose `StockSectionHeader` lacks iOS's stacked-action rule;
  Android-only "Refresh dashboard" button (iOS has pull to refresh); guests see the existing "Your portfolio · Create portfolio" card.
- Android skeletons announce the shared "Loading" instead of "Loading market overview" / "Loading your portfolio" (iOS keeps them).
- **QA incident:** a cleanup command (`pkill -f "cat" -n`; BSD pkill read `-n` as a second pattern) killed every process matching "cat"
  or "-n" at 14:03 EDT: both emulators, the :8081 MOCK server (pid 492), the :8083 server and Claude helper processes. Restored: Pixel_9_Pro
  on port 5554 (same `-no-snapshot-save`), Pixel_9_Pro_Fold on 5556 (now a visible window, previously embedded in Android Studio), a MOCK
  server on :8081 from `server/build/install` (may differ from what pid 492 ran), `adb reverse` 8081/8083 on emulator-5554. emulator-5554
  now runs this phase's debug build pointed at `127.0.0.1:8083` (my MOCK server is left running for it).

## 9. Unverified scenarios
- Loading skeletons on device: NOT RUN (first load finished before capture on both platforms).
- Android guest: BLOCKED (signing out of the cached test session is not allowed). iOS signed-in: BLOCKED (no approved test account).
- Signed-in portfolio summary with an account (owned card, borderless): BLOCKED (MOCK store had no account; not created).
- TalkBack / VoiceOver: NOT RUN (owner's manual pass). Tablet / foldable widths: NOT RUN.

## 10. Deviations
- No approved plan document; the audit and decisions are in the report §1–§2 (the brief asked for an audit before implementation).
- `PracticeEntryCard` (iOS, Practice file) touched for live Dynamic Type; its only consumer is Home.

## 11. Decisions requiring owner approval
- H1–H9 (report §2), in particular: section gap 20 instead of 12 on Home only; Portfolio summary kept as a (borderless) card.

## 12. Links
`docs/HOME_UI_REFINEMENT_PHASE1.md` · `docs/design/home_ui_phase1/` · `CHATGPT_UI_REVIEW_ANDROID.png` · `CHATGPT_UI_REVIEW_IOS.png` ·
`CHATGPT_FOCUSED_DIFF.md` · originals and logs in `captures/` (git-ignored).

**Recommendation: APPROVE WITH FIXES** — the visual refinement is complete and verified on both platforms; before closing KAN-14 the
owner should run the signed-in iOS and guest Android passes, the screen-reader pass, and decide H3/H4.

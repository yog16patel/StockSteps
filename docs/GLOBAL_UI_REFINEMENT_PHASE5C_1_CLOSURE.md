# Global UI Refinement Phase 5C.1 — closure pass (Company Comparison)

Status: **committed and pushed** as "Close Company Comparison Phase 5C.1 with section-based sticky header and spacing fixes, and add the Portfolio UI audit and Phase P-A display rules" (2026-10-12), on top of `42c749d` "Add end-of-session handoff after Global UI Refinement
Phase 5C.1 and point to the Phase 5D Earnings audit". Small UI corrections only: no API, request, calculation, fiscal-period label, sample-data disclosure, entitlement or
navigation change; MOCK fixtures untouched (quarterly = annual revenue growth stays a documented fixture artifact).

## 1. Changes

| Item | Before | After | Platforms |
|---|---|---|---|
| Gap between section header / notes and the first metric | Open section card padded `xs` at its bottom, plus the first row's own `xs` top padding (iOS: also `xs` under an empty notes block) — ~60 dp from "What to keep in mind" to the first metric title on Android | Section card has no extra bottom padding; the first row gets `xxs` top padding (other rows keep `xs`); only *expanded* notes get an `sm` gap below them | Android, iOS |
| Pinned company header placement (redundant tickers) | Pinned over **every** metric section whenever values could sit in columns — including bar rows, which already name each company on every line | New shared rule `ComparisonDisplay.companyHeaderSection(openSections, sideBySide)`: the header starts at the **first open section with a row in unlabelled columns** (e.g. Valuation P/E); nothing above it (Growth/Profitability bars) gets a header; no header at all when only bar rows or stacked lines are open | core rule; Android `comparisonEntries(pinFrom)`; iOS two `Section`s |
| Pinned header height | 48 dp / pt targets + `xs` vertical padding (≈ 56 dp) | `xxs` vertical padding, columns centred in their 48 dp target (≈ 52 dp, Android) / ≈ 50 pt (iOS) | Android, iOS |
| iOS metric title row | `HStack(alignment: .top)`: the ⓘ's 44 pt frame sat below a one-line title (looked misaligned, added space) | Centred, as Compose | iOS |
| Focus scrolling (Android) | Offset by the header height for every metric | Only for metrics below the header's start | Android |

Unchanged on purpose: 48 dp / 44 pt targets (section header, notes control, ⓘ, reason controls), Dynamic Type / font-scale behaviour, bar rules,
short/full period texts, spoken labels, Sample badge and "Sample fixture data…" note.

## 2. Files changed

- Core: `screener/ComparisonDisplay.kt` (`companyHeaderSection`); test `screener/ComparisonDisplayTest.kt`
  (+1: `companyHeaderPinsFromTheFirstSectionWithUnlabelledColumns`).
- Android: `presentation/screener/ComparisonScreen.kt`.
- iOS: `ScreenerScenes.swift`.
- Docs: this file; screenshots `docs/design/phase5c_1/closure/` (14).
- Same working tree, separate work: Portfolio audit `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md` and Portfolio P-A (`docs/PORTFOLIO_REFINEMENT_PHASE_PA.md`:
  `PortfolioDisplayRules` + 2 tests in `PortfolioPresentationTest`).

## 3. Device validation (actually performed; local MOCK `:8081` only, never the LAN host)

| Check | Device | Result |
|---|---|---|
| Four companies (AAPL, MSFT, NVDA, TSLA), light, 1.0× | Android emulator-5554 at 360 dp (`wm size 1080x2400`) | Growth/Profitability bars without a pinned header; notes collapsed and expanded — first metric follows directly; Valuation shows the compact header directly above it |
| Dark (in-app Theme → Dark), 1.0× | same | Before the placement refinement the header sat over Growth bars once Valuation was open (`BEFORE-REFINEMENT-…`); after: header only above Valuation |
| 1.3× font scale, dark | same | Four companies switch to one line per company; no header; no clipping |
| Light, Large | iPhone 16 Simulator (iOS 18.3) | No header over bars; header above Valuation and pinned while scrolling; ⓘ centred after the fix (`BEFORE-FIX-…` shows the old offset) |
| Dark, Large | same | Pinned header compact, legible |
| Dark, AX5 (largest accessibility size) | same | Stacked lines, no header, no clipping |
| Smaller screen, Large | iPhone SE (3rd gen) Simulator (iOS 27; the iOS 17.5 SE can't install the app — minimum iOS 18.2) | Four-company bars and the Valuation columns fit; header above Valuation only |

Not covered on devices: Android light at 1.3× (dark only), iOS XXXL non-accessibility size (AX5 checked instead), the 2 dp/pt first-row gap
(applied after the captures; the captures show a 0 gap; it only adds 2 dp/pt).

### Screen readers

- **TalkBack: NOT RUN.** Enabling TalkBack on emulator-5554 raised a system dialog "Allow Android Accessibility Suite to send you
  notifications?". The project rules forbid answering permission prompts on the owner's behalf, so TalkBack was turned off again and the dialog was
  **left open for the owner** (permission still undecided: `POST_NOTIFICATIONS: granted=false`).
- **VoiceOver: NOT RUN** — not available in the iOS Simulator; needs a physical device (or an Accessibility Inspector audit, not done).
- Code-level: spoken labels unchanged (section headers "Expanded/Collapsed", notes control, ⓘ labels, cell labels name company + metric + full
  period); the pinned header keeps "Name, ticker, price …" + "Open company details".

### Device state after the pass

Emulator: size reset (1280×2856), font scale 1.0, night mode yes (as found), `adb reverse` removed, TalkBack off; **the app's in-app Theme is left on
Dark** (was Light) because the open permission dialog covers the app — switch back in Settings → Theme. iPhone 16: Light / Large (as found). iPhone SE
iOS 27: booted for this pass, then shut down; its app copy has the MOCK backend preference set. MOCK server stopped.

## 4. Tests and builds

Run 1 (2026-10-11, test outputs cleaned, one sequential command `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest
:app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` → **BUILD SUCCESSFUL in 9m 23s**, then the default
`xcodebuild … -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`). Run 2 (2026-10-12, after the Portfolio P-A tests were
added; shared outputs cleaned): `:app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug` → exit 0.

| Check | Result |
|---|---|
| Core JVM | **466 passed, 0 failed, 0 skipped** (465 + 1 new `ComparisonDisplayTest`) |
| Core iOS simulator | **466 / 0 / 0** |
| Shared Android host | **113 / 0 / 0** (run 2; 111 + 2 Portfolio P-A tests; run 1: 111 / 0 / 0) |
| Shared iOS simulator | **109 / 0 / 0** (run 2; run 1: 107 / 0 / 0) |
| `:app:androidApp:assembleDebug` | OK (both runs) |
| iOS `xcodebuild` (default, LAN MOCK URL) | **BUILD SUCCEEDED**, no warnings in changed files |
| iOS `xcodebuild` with `STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081` (device checks) | BUILD SUCCEEDED (Info.plist checked: `http://localhost:8081`) |
| `:server:test` | not run (no server change) |

## 5. Remaining issues

- TalkBack / VoiceOver passes still outstanding (see above); physical devices not used.
- Android dark at 1.3× checked, light at 1.3× not re-captured.
- MOCK fixture artifact (quarterly = annual revenue growth) unchanged by request.
- The pinned header still appears over later bar sections *below* the first column section (e.g. if Valuation and a bar section after it are both
  open) — sticky headers can't be interrupted per section without splitting the list further; acceptable.

## 6. Verdict

**PASS WITH CONDITIONS.** Every closure item is done on both platforms using a shared, tested rule: tighter notes → first-metric
spacing, a compact pinned header that appears only where columns need it, and no repeated tickers over bar rows. The device captures (Android 360 dp
light/dark at 1.0× and dark at 1.3×; iPhone 16 Large light/dark and AX5 dark; iPhone SE) show no clipping, overflow, overlap or wrong pinning, and the
Sample badge and fixture note are still shown. All suites and builds pass. Conditions: **TalkBack NOT TESTED** (system permission dialog left for the
owner), **VoiceOver NOT TESTED** (Simulator), **physical devices NOT TESTED**, and the Android light 1.3× and 2 dp first-row gap captures listed in §3
were not taken.

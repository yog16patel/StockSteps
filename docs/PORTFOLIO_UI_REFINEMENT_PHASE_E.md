# Portfolio UI Refinement — Phase E: accessibility and cross-platform QA (report)

Status: **fixes and runtime checks committed and pushed as "Add Portfolio Phase E accessibility fixes with iOS error and rejection announcements, unbroken P/L labels, filter-only history announcements and single-announcer save errors, plus Android and iOS large-text QA" (2026-10-13); screen-reader passes still blocked.** Plan: `docs/PORTFOLIO_UI_REFINEMENT_PHASE_E_PLAN.md` (approved
Q1–Q7). Base: `fa7c563` (pushed); Portfolio D3.1 = `2bb39af`.

> **Do not stage with Phase E** (the parallel backend session's uncommitted work, present in the same tree): root `PROJECT_HANDOFF.md`,
> `docs/project-status.md`, `docs/UBUNTU_BACKEND_VERIFICATION_2026-10-13.md`. Phase E deliberately did **not** edit those files.

## 1. Fixes (E-D)

| ID | Priority | Platform | Change | Verified |
|---|---|---|---|---|
| E-01 | P2 | iOS | `portfolioErrorBanner` posts an `AccessibilityNotification.Announcement` ("title. message") when the banner appears or its error changes — parity with Compose's polite live region | Build + unit rule; **spoken output not verified** (VoiceOver blocked) |
| E-02 | P2 | iOS | Transaction form announces its form-level message and a rejected save ("Couldn't save transaction. <server text>") | Build; **spoken output not verified** |
| E-03 | P2 | Both | `PortfolioPresentation.keepTogether` joins "P/L" with word joiners (U+2060) where Portfolio draws metric labels (overview, Holding details), so labels break "Unrealized / P/L", never "P/ / L". Data labels and spoken text unchanged | **Device:** Android 360 dp dark 1.0× shows "Unrealized / P/L" (`a-dark1x-12-holding-details`; before: D3.1 capture "P/" + "L") |
| E-04 | P3 | Android | History count is a live region only for ~1.5 s after the user changes the filter (as iOS announces only on filter change), so a delete isn't followed by a second "All: 3 transactions" announcement | Build + code review; **spoken output not verified** (TalkBack blocked, §3) |
| E-05 | P3 | Both | (found while fixing E-01) A rejected save was announced twice — by the form and by the screen banner behind it. `PortfolioDisplay.errorAnnounced` keeps the screen banner quiet for `SAVE_TRANSACTION` (the form announces it); new optional `StockBanner(announce = true)` parameter, default unchanged | Unit test |

No calculation, ledger, API, entitlement or Firebase-configuration change. Financial display unchanged except the word joiners in two labels.

## 2. Android runtime (E-B) — emulator-5554, 360 dp, local MOCK

Account "PhaseE" (in-memory MOCK; gone after the server stopped): opening position, deposit, buy (cash below zero), sell.

**Layout matrix** — 12 screens per configuration (dashboard top / mid / allocation / cash / transactions, history, details top / actions, delete
dialog, Add form, Add form with keyboard, Holding details): `docs/design/portfolio_phase_e/a-<config>-NN-*.jpg`.

| Configuration | Result |
|---|---|
| Light 1.0× | PASS |
| Light 1.3× | PASS — large-text layouts engage (rows / details stack, pills scroll); no clipping |
| Light 2.0× | PASS — delete dialog fits, account selector and segment wrap, nothing clipped |
| Dark 1.0× | PASS (app theme set to Dark for the run, restored to Light) |
| Dark 2.0× | PASS |

Keyboard: the Add form resizes (top bar and focused field stay visible) at 1.0× / 1.3× / 2.0×. Empty account state captured
(`a-light1x-dashboard-empty-account`). Tab highlight remained on Portfolio on all sub-screens (visible in every capture).

**TalkBack — BLOCKED.** TalkBack was enabled with "Display speech output" (no first-run prompt appeared). Its initial announcement was captured:
"Portfolio. Heading." (`a-talkback-initial-focus`). But TalkBack ignores every injected input on this emulator — `adb input` swipes, taps,
`motionevent` touch-explore and the Alt+Arrow keyboard shortcut — so focus could not be moved through the 10 journeys. A real pass needs gestures on
the emulator window (owner, or an approved desktop-control session). TalkBack and the speech display were turned off again afterwards.

Static semantics review (not TalkBack verification): rows read one spoken sentence with a separate labelled ⋯; headings on section and month
titles; `error()` semantics on invalid fields; polite live regions on error/warning banners and the success banner.

## 3. iOS runtime (E-C) — iPhone 16 (iOS 18.3), ad-hoc signed MOCK build, local Auth emulator

The cached simulator session is unknown to a freshly started Auth emulator, so the app ran **offline** with its last saved portfolio ("Test account",
1 opening position). Signing in again would require typing test credentials into the simulator, which the project rules don't allow Claude to do.

| Check | Result |
|---|---|
| Large, light: dashboard rows, details, details actions, delete confirmation (with summary line) | PASS (`i16-large-01…05`) |
| AX5, light: details summary / rows / stacked actions; dashboard rows; history (pills scroll with fade) | PASS (`i16-ax5-01…05`) — live Dynamic Type change applied without relaunch |
| Large, dark: history; Add form with keyboard (Done bar, field visible); validation (focus moves to first invalid field, error shown) | PASS (`i16-large-dark-06…08`) |
| Offline banner wording and Retry | Seen (pre-existing offline state) |
| Tab highlight on sub-screens | PASS by structure (Portfolio tab stays selected; visible in captures) |
| Add / edit / delete success, rejected save/delete titles, samples, account switching | **BLOCKED** — needs a signed-in session |
| VoiceOver | **BLOCKED** — needs a physical device (Q4) |
| Accessibility Inspector audit | **NOT RUN** — a GUI app; no headless interface available to this session |

Simulator restored to Large / light. Auth emulator and MOCK stopped.

## 4. Automated tests and builds (E-E) — sequential, `--rerun`

| Suite | Result |
|---|---|
| core JVM / core iOS | **474 / 0** each |
| server (run alone) | **499 / 0** (3 skipped) |
| shared Android host / shared iOS | **219 / 0** / **215 / 0** (+2 `PortfolioPhaseETest`: P/L labels, one announcer per error) |
| `:app:androidApp:assembleDebug` | OK |
| default unsigned `xcodebuild` | **BUILD SUCCEEDED** |

## 5. Parity matrix (after Phase E)

| Area | Android | iOS | Notes |
|---|---|---|---|
| Dashboard, holdings, allocation note, negative cash | Runtime ✓ | Runtime ✓ (offline data) | Shared display objects |
| Add / edit / delete, feedback | Runtime ✓ (D3, D3.1) | Blocked (sign-in) | Same presenter contract |
| Error announcements | Live region ✓ | Announcement (E-01/E-02, unverified spoken) | Parity in code |
| History, filters, details, samples | Runtime ✓ | Partial (offline) | Shared rules |
| Large text | 1.0 / 1.3 / 2.0, light + dark ✓ | Large / AX5, light + dark ✓ | — |
| Screen readers | Blocked | Blocked | §2, §3 |

## 6. Open items

- **TalkBack journeys** — needs real gestures on the emulator window.
- **VoiceOver** — needs a physical iPhone.
- **iOS signed-in flows** — owner signs in on the simulator with an Auth-emulator test account (or approves another approach).
- **Accessibility Inspector** — owner or a desktop-control session.
- K-01 (known, not changed): Insights excludes borrowed cash from its denominator; the dashboard includes it.
- A second emulator (`emulator-5556`) appeared during the session (not started by Claude); all commands were pinned to `emulator-5554`.

## 7. Files

- Shared Kotlin: `PortfolioPresentation.kt` (`keepTogether`), `PortfolioDisplay.kt` (`errorAnnounced`), `PortfolioScreen.kt` (labels),
  `PortfolioTransactionsScreen.kt` (E-04), `PortfolioTransactionViews.kt` (banner announce), `designsystem/components/StockBanner.kt` (optional
  `announce`).
- iOS: `PortfolioTransactionsScreen.swift` (E-01), `PortfolioForms.swift` (E-02), `PortfolioScreen.swift` (labels).
- Tests: `PortfolioPhaseETest.kt` (new).
- Docs: this report, the plan, `docs/PROJECT_HANDOFF.md`, `CLAUDE.md`. Captures: `docs/design/portfolio_phase_e/` (75).

**Phase E is not complete:** the screen-reader passes and the iOS signed-in flows remain blocked as listed in §6.

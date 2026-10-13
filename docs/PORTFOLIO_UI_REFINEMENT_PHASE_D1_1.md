# Portfolio UI Refinement — Phase D1.1: transaction form polish and regression closure

Status: **closed 2026-10-13.** The owner approved the UI changes after reviewing the captures and this report; the final repository verification
(§8) passed and D1.1 is committed locally as "Close Portfolio Phase D1.1 transaction form polish with all-at-once field validation, type-switch error clearing, selected-security line, scoped Android keyboard resize and stacked large-text actions on Android and iOS" (not pushed). Originally It sits on top of "Add Portfolio Phase D1 transaction entry with sectioned forms, native
date pickers, field-level validation, keyboard navigation and read-only sample handling on Android and iOS" (`d86c0e5`). The working tree was clean before
D1.1. **Phase D2 is not started.**

What D1.1 does **not** change:
- the ledger, calculations, `PortfolioEngine.validate`, decimal precision or market data;
- the API, Firebase, entitlements or presenter contracts;
- routes or the five tabs.

Every change is UI-level or a shared display rule.

Evidence:
- "after" captures: `docs/design/portfolio_d1_1/` (`a-*` Android 360 dp, `i16-*` iPhone 16);
- "before" captures: the D1 set in `docs/design/portfolio_d1/` (named in each row below).

## 1. Changes (before → after)

| # | Area | Before | After | Evidence (before → after) |
|---|---|---|---|---|
| 1 | Layout | Intro paragraph above every form; sections and fields both 12 dp apart | The "earlier returns" note sits under **Effective date**, only for Opening position (`PortfolioFormRules.dateNote`). Android sections are 20 dp apart (`spacing.xl`), fields 12 dp. iOS lost the intro section, and text labels no longer repeat as placeholders. Touch targets unchanged (48 dp / 44 pt) | `portfolio_d1/a-light1x-form-top` → `a-light1x-form-top-after`; `portfolio_d1/i16-large-form-1` → `i16-large-form-top-after` |
| 2 | Security | A plain text link under the symbol; the chosen security was never named | The chosen security appears under the symbol when its details are known ("Apple Inc. · NASDAQ · USD", shared `selectedSecurity`; nothing is looked up). Android search is an **outlined** secondary button tied to the field; iOS keeps a native row link under the symbol and its error. Same search route and selection behaviour | `a-light1x-selected-security-after`, `i16-large-edit-selected-security` |
| 3 | Validation | One message per save attempt (`validate` stops at the first rule); a two-field message ("Choose a security and a positive share quantity.") under one field | `PortfolioFormRules.check` re-runs the **unchanged** `validate`. After each failure it gives that field a check-only stand-in and runs again, so every problem shows at once. Two-field messages are split into one sentence per failing field (`guidance`). Submitted values are always exactly what was typed (unit-tested) | `portfolio_d1/a-light1x-required-errors` → `a-light1x-errors-all-at-once-1/-2`, `i16-large-errors-all-at-once(-2)` |
| 4 | Stale errors | D1 kept rule errors and a server-rejection banner after a type change (`portfolio_d1/a-light1x-type-switch-stale-error-before`) | A type change keeps only format errors on still-visible fields (`retainedErrors`) and clears the save banner and summary. Verified on device: Buy (3 errors) → Cash deposit; Sell rejection ("would sell … more shares than owned") → Cash deposit | `a-light1x-type-switch-after`, `a-light1x-sell-rejected` → `a-light1x-rejection-cleared-on-type-switch`, `i16-large-type-switch-after` |
| 5 | Keyboard (Android) | The window **panned** while typing: the top bar slid under the status bar and a blank gap appeared above the keyboard (the screen's `imePadding` plus the pan) | New `KeyboardResizesContent()` (`expect`/`actual`; Android only, no-op on iOS/JVM) switches the activity window to resize mode **only while the Add screen is shown**. It is reference-counted across navigation transitions and restores the previous mode on exit (verified with `dumpsys`: pan → resize → pan). No manifest or app-wide change | `a-light1x-keyboard-pan-before` → `a-light1x-keyboard-resize-after(-2)` |
| 6 | Bottom actions | At 2.0× the Cancel + Save pair shared a row | At large text Save and Cancel each take a full-width row (edit sheet) | `a-light2x-edit-sheet-buttons-after` |

## 2. Regression results

| Check | Result |
|---|---|
| Combined message split per field (security / shares; negative shares/price/fees) | PASS (unit + device) |
| All problems shown in one attempt (symbol, shares, price, settlement) | PASS (unit + Android + iOS) |
| Type switch drops old rule errors; format errors kept only while visible (Sell → Cash deposit, Sell → Opening position, hidden field) | PASS (unit + Android + iOS) |
| Server rejection cleared on type switch | PASS (Android device) |
| Hidden fields excluded for **every** type (shares/price/amount/FX) | PASS (unit); device: shares 5 / price 40 typed, switched to Cash deposit, saved → row "Cash deposit, Oct 13, 2026, USD 250.00" with no shares |
| Check never alters submitted values | PASS (unit) |
| Values kept after failed validation and after a server rejection | PASS (Android, iOS) |
| Duplicate saves while busy | PASS (existing `PortfolioPresenterSaveTest`; button disabled while saving) |
| Android closes after confirmed success | PASS (dashboard shows the new row) |
| Keyboard Next/Done, focus to the first error | PASS (Android 1.0×/2.0×, iOS Large) |
| Save reachable with the keyboard open, no overlap with keyboard, navigation or MOCK strip | PASS (Android after fix 5; iOS Form scrolls) |

## 3. Accessibility findings

- **Android 360 dp, 1.0× and 2.0× (light):**
  - sections, labels and errors wrap between words, with nothing clipped;
  - the first error field gets focus with the keyboard open;
  - the top bar stays visible while typing;
  - the search button wraps inside its outline at 2.0×;
  - edit-sheet buttons stack.
- **iPhone 16, Large and AX5 (dark, live switch):**
  - labels sit above values at AX5;
  - the type and account values wrap in full;
  - the selected-security line wraps;
  - the date note wraps;
  - no truncation or mid-word breaks.
- **Reading order:** label → value → error → helper; Android errors use `error()` semantics; iOS errors are read with their field label.
- **Not verified:** TalkBack, VoiceOver, Android dark mode, iPhone SE (excluded by the owner).

## 4. iOS offline MOCK session (investigated, not resolved; owner decision)

The cached iPhone 16 session is launched with `--firebase-emulators`, so Firebase Auth points at `127.0.0.1:9099`. That emulator isn't running, so ID
tokens can't be fetched and every authenticated request fails as "Could not connect". This is pre-existing environment state, not D1.1 code. The owner
chose not to start the Auth emulator for this phase, so **a successful iOS save remains NOT VERIFIED**. The failure path (values kept, message shown)
was verified in D1.

## 5. Files changed

- **Shared Kotlin:**
  - `presentation/portfolio/PortfolioFormRules.kt`: `check` (re-run), `guidance`, `retainedErrors`, `selectedSecurity`, `dateNote`; removed the
    unused `fieldFor`;
  - `PortfolioDisplay.kt`: 4 per-field sentences;
  - `PortfolioForms.kt`: layout, security, type-switch errors, stacked buttons;
  - `PortfolioScene.kt`: `KeyboardResizesContent()`;
  - new `KeyboardResize.kt` + `androidMain`/`iosMain`/`jvmMain` actuals.
- **iOS:** `PortfolioForms.swift` (type-switch errors, selected-security line, date note, intro removed).
- **Tests:** new `PortfolioPhaseD11Test` (7); `PortfolioPhaseD1Test` updated for the per-field wording.
- **Docs:** this file, the plan, `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`.
- **Captures:** `docs/design/portfolio_d1_1/` (20).

## 6. Remaining unverified items

- A successful save on iOS (Auth emulator not approved; §4).
- TalkBack and VoiceOver navigation.
- Android dark mode for the form (light only this phase); iPhone SE (excluded).
- Comma decimal separators: still explained, not converted (ledger rule, unchanged).

## 7. Final run (sequential, clean test outputs)

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` | **BUILD SUCCESSFUL** (8 m 18 s) |
| core JVM / core iOS | **472 / 0** each |
| shared Android host / shared iOS | **190 / 0** / **186 / 0** (+7 `PortfolioPhaseD11Test`) |
| default `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | **BUILD SUCCEEDED** |
| `:server:test` | NOT RUN (no server change) |

Devices restored afterwards:
- Android: size and font reset, `adb reverse` removed;
- iPhone 16: Large/light;
- MOCK stopped (its in-memory test account "D11Test" is gone).

No Auth emulator was started.

**Recommendation (at the time):** ready for owner review. Phase D2 is not started.

## 8. Closure (owner approval and final repository verification, 2026-10-13)

The owner reviewed the captures and this report and approved the D1.1 UI changes, subject to a final repository check.

**Diff audit.** Every change belongs to D1.1:
- the shared Kotlin, Swift, test, doc and capture paths listed in §5;
- `CLAUDE.md` ("Current task" and the Portfolio form / Android keyboard conventions).

No server, fixture, build or backend-migration file was touched. The ledger, `PortfolioEngine`, decimal handling, API, entitlements, sample-data
labelling, routes and the five tabs are unchanged.

**Final run (sequential; the test tasks were forced to re-run with `--rerun`, so no cached results were counted):**

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test` (each `--rerun`) | **BUILD SUCCESSFUL** (8 m 22 s) |
| core JVM / core iOS | **472 / 0** each |
| server | **492 / 0** (3 skipped, pre-existing) |
| shared Android host / shared iOS | **190 / 0** / **186 / 0**; `PortfolioPhaseD11Test` 7/0 and `PortfolioPhaseD1Test` 8/0 on both |
| `./gradlew :app:androidApp:assembleDebug` | OK |
| default `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | **BUILD SUCCEEDED** |

**Behaviour re-confirmed:**
- **Field-specific validation:** `PortfolioPhaseD11Test` (split messages, all problems at once, values never changed) and `PortfolioPhaseD1Test`.
- **Type-switch error clearing:** `retainedErrors` unit tests. Both forms call it on a type change: Android `onSelect`, iOS `.onChange(of: type)`.
- **Keyboard resize:** `KeyboardResizesContent()` is called only from `PortfolioEntryScene`. It is reference-counted and restores the previous mode;
  the iOS/JVM actuals are no-ops.
- **Large-text layouts:** the stacked Save/Cancel branch (`fontScale >= largeFontScale`) is unchanged.

The device evidence for keyboard resize and large text is the §1–§3 capture set (Android 360 dp 1.0×/2.0×, iPhone 16 Large/AX5). Devices were
**not** re-driven for the closure. By file times:
- the last source edit (`KeyboardResize.android.kt`, reference counting, 23:44) came after the 1.0× keyboard captures (23:39);
- it came before the 2.0× keyboard/edit-sheet captures (23:53–23:54) and the iPhone 16 set (23:57–00:02), so those show the final code.

**Still NOT VERIFIED** (unchanged by the closure):
- a successful iOS save (§4);
- TalkBack, VoiceOver;
- Android dark mode for the form;
- iPhone SE (excluded by the owner), physical devices.

Next: Phase D2 (Transaction history). Its plan is submitted for owner review and is not implemented.

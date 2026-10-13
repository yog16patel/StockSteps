# Portfolio UI Refinement — Phase D1: transaction entry and forms

Status: **implemented 2026-10-12, committed and pushed as "Add Portfolio Phase D1 transaction entry with sectioned forms, native date pickers, field-level validation, keyboard navigation and read-only sample handling on Android and iOS".** It sits on top of "Finish Portfolio Phase C.1 cleanup with concise insight cards, expandable
details, currency on insight amounts and clearer stacked metric spacing on Android and iOS" (`9a1b884`). Phase C.1 was approved, committed and pushed
before D1 started, so none of its fixes are mixed in here. **Phase D2 is not started.**

What D1 does **not** change:
- calculations, the ledger, presenter contracts, API, server, entitlements, Firebase or market data;
- validation rules (no new ones);
- routes: Android edit uses a sheet, not a new route.

The reference image was used for layout direction only. Its review, success, Transactions-list and success-confirmation screens are **not** built,
because they need data or routes that don't exist (§8).

## 1. Inventory: what exists

| Area | Existing implementation (used, not replaced) |
|---|---|
| Transaction types | `TransactionType` (Buy, Sell, Opening position, Dividend, Dividend reinvestment, Cash deposit/withdrawal/adjustment, Stock split, Transfer in/out, …). Field visibility from `PortfolioFormRules.visibleFields` |
| Validation | `PortfolioEngine.validate` (core, also on the server), `PortfolioEngine.validDate`, `Decimal.parse` (plain decimals, ≤ 8 decimal places) |
| Accounts | Chosen and submitted by id (`accountOptions`, `resolveAccountId`) |
| Save flow | `PortfolioPresenter.saveTransaction(tx, edit)` → `command` (ignores calls while busy; no-op during a sample scenario) → `state.busy`/`state.error`. Android leaves the entry screen when the ledger revision increases; iOS dismisses the sheet when busy goes false without an error |
| Entry points | Android: `PortfolioEntryRoute` (Add, search hand-off, Company details "Add to portfolio"), edit in a dialog. iOS: a sheet for Add and Edit |
| Samples | `PortfolioUiState.mockScenario`; the dashboard's Add is disabled with `ADD_DISABLED_SAMPLE` |

## 2. What changed

| # | Change | Android | iOS |
|---|---|---|---|
| 1 | **Sections** from shared `PortfolioFormRules.sectionFields`: "Transaction information" (type, date, account, security), "Amounts" (only the fields the type uses), "More options" collapsed (currencies, FX rate, settlement, company name, exchange, notes). It opens by itself when it holds a value or an error | Headings + `StockSelectField`/`StockTextField`; More options is a 48 dp toggle with expanded/collapsed state | `Form` sections + `DisclosureGroup` |
| 2 | **Labels name their currency**: "Price per share (USD)", "Amount (CAD)", "Fees (USD, optional)"; date labels "Trade date" / "Effective date" (shared `label`) | ✔ | ✔ (persistent label above each text input; the placeholder-only label vanished once fees showed "0") |
| 3 | **Native date pickers**. The stored value stays `YYYY-MM-DD`; pickers exchange whole days (`PortfolioFormDates`, no time zone). Earliest selectable day is 1900-01-01, the ledger's own limit; nothing else is restricted | New `StockDateField` + Material `DatePickerDialog` (keeps its text-entry mode) | `DatePicker(.date)` in UTC, so the day never shifts |
| 4 | **Field-level errors** from shared `PortfolioFormRules.check`: invalid numbers (same parser as the ledger) and dates are explained in plain words, then `PortfolioEngine.validate` runs unchanged and its message goes under the field it is about (`fieldFor`). Unknown messages stay form-level, unaltered. A field's error clears when that field changes. The first problem is focused | ✔ | ✔ **iOS now validates before sending** (it used to rely on the server) |
| 5 | **Hidden fields never submitted**: `visibleTransaction` empties fields the type hides (Buy → Cash deposit no longer sends shares/price; FX only while the cash currency differs). Blank fees → "0", the same as the iOS bridge always sent | ✔ | ✔ |
| 6 | **Keyboard**: decimal keyboards; Next moves through the visible inputs in order and the last one shows Done | IME Next/Done with explicit focus order; `imePadding` (as before) | `@FocusState`, `.submitLabel`, a keyboard Done bar, `.scrollDismissesKeyboard(.interactively)` |
| 7 | **Save feedback**: "Saving…" while busy (button disabled; the presenter also ignores repeat taps); a server rejection stays on the form in a "Couldn't save the transaction" banner with every value kept. A stale failure from another action is never shown as a save error (cleared on open; shown only after this form's own submit; reset on a type change) | `StockBanner(ERROR)` | Inline message |
| 8 | **Edit** opens the full form with Cancel and stays open while saving, closing only after success. No route change | Full-height `ModalBottomSheet` (was a cramped `AlertDialog`) | Sheet (as before) |
| 9 | **Sample (read-only) portfolios**: entry shows "Transactions are read-only" + "Go to My saved accounts" (existing `showScenario(null)`) instead of a save that silently does nothing | ✔ (reachable from Company details → Add to portfolio) | ✔ |
| 10 | **Large text** | Sections and fields stack; no fixed heights | At accessibility sizes, picker and date labels sit above their values, and pickers use a wrapping Menu label ("Opening position" was "Ope…sition" at AX5) |

## 3. Reusable components and shared rules

- **New:**
  - `StockDateField` (Compose);
  - `StockIcons.Calendar`;
  - shared objects `PortfolioFormSection`, `PortfolioFormCheck`, `PortfolioFormDates`;
  - `PortfolioFormRules.section`/`sectionTitle`/`sectionFields`/`moreOptionsOpen`/`readOnly`/`visibleTransaction`/`check`/`fieldFor`;
  - D1 copy in `PortfolioDisplay`;
  - iOS bridge `IosAccountClient.clearPortfolioError()`.
- **Updated:**
  - `StockTextField(imeAction, keyboardActions)`: optional, defaults unchanged for every other caller;
  - `PortfolioFormRules.redesignOrder` (now used);
  - `label` (currency names, date labels).

## 4. Validation behaviour

- **Required values:** the existing rule messages appear under the field. Examples:
  - "Choose a security and a positive share quantity." under Symbol when no security is entered, else under Shares;
  - "Trade price must be positive.";
  - "Amount must be positive.";
  - "Settlement cannot precede the trade."
- **Invalid numbers:** "1,000", "$5" or more than 8 decimal places show "Enter a number like 1250.50: digits and one period, up to 8 decimal places, no
  commas or currency symbols." The text is never rewritten or rounded.
  - The ledger accepts only `.` as the decimal separator. A comma-decimal locale's keyboard therefore gets that message rather than a silent
    conversion (§8).
- **Server rejections** (for example "Transaction would sell or transfer more shares than owned on 2026-10-08.") stay visible until the next attempt.

## 5. Tests and builds (actually run)

- **New:**
  - `PortfolioPhaseD1Test` (8: sections, currency labels, hidden-field clearing, required-field placement, invalid numbers / precision, date round
    trip, More-options opening, sample read-only);
  - `PortfolioPresenterSaveTest` (core, 1: repeated taps save once, busy state, server rejection kept until the next save, then a successful save).
- **Updated:** `PortfolioFormRulesTest` (price labels now include the currency).
- **Final sequential run:** §9.

## 6. Device verification (local MOCK backend only; in-memory server data, cleared when it stops)

| Check | Android 360 dp | iPhone 16 |
|---|---|---|
| Sections, labels, More options | PASS 1.0× (`portfolio_d1/a-light1x-form-top`), 2.0× (`a-light2x-form-1`) | PASS Large (`i16-large-form-1`), AX5 dark (`i16-ax5-form`, after the AX fix) |
| Date picker | PASS (`a-light1x-date-picker`; Oct 8 chosen and kept) | PASS (`i16-large-date-picker`; Oct 8 kept) |
| Required-field errors | PASS (`a-light1x-required-errors`; 2.0× focuses the field: `a-light2x-errors`) | PASS (`i16-large-required-errors`, keyboard Next + Done bar) |
| Invalid numbers | PASS (`a-light1x-number-errors`) | Not typed: the decimal pad has no comma key (unit-tested) |
| Keyboard Next / Done | PASS (Next symbol → shares → price; Done closes) | PASS (Next to Shares; Done bar) |
| Successful save → dashboard | PASS (`a-light1x-saved-dashboard`, holding appears) | NOT VERIFIED: the cached iOS session is offline against this MOCK (pre-existing), so saves fail |
| Server rejection kept on the form | PASS (`a-light1x-server-rejection`) | PASS: offline failure kept with values (`i16-large-save-failed`) |
| Type switch clears hidden fields | Unit-tested. Device: Sell → Cash deposit showed Amount (USD) and kept the old Sell rejection banner (`a-light1x-type-switch-stale-error-before`); **fixed in code (banner resets on a type change), not re-captured** | Same fix, not re-captured |
| Edit (full sheet, Cancel, closes after save) | PASS (`a-light1x-edit-sheet-2`; 10 → 12 shares saved) | Existing sheet; NOT VERIFIED (offline session) |
| Sample read-only + Go to My saved accounts | PASS (`a-light1x-sample-readonly`; switched back with TD.TO kept) | Code path shared; NOT VERIFIED on device |
| Touch targets | uiautomator: only full-width pickers measured < 48 dp tall at the screen edge while partly scrolled off; on screen all ≥ 48 dp | 44 pt frames in code |
| TalkBack / VoiceOver, iPhone SE, dark Android | NOT VERIFIED (SE excluded by the owner) | — |

## 7. Android / iOS differences

- Android edit uses a full-height sheet; iOS keeps its sheet.
- Android date fields are a tappable field plus a dialog; iOS uses the compact inline `DatePicker`.
- iOS adds a keyboard Done bar; Android uses the IME action key.
- iOS large-text pickers use a wrapping Menu label; Android's `StockSelectField` already wraps.

## 8. Not implemented (unsupported, documented instead of faked)

- **Review screen with total amount / estimated total** (reference 3–4): the net amount is computed by the server's ledger only. A UI total would be a
  new client calculation (D-R7, needs approval).
- **Success screen** (reference 5): needs a new destination. The existing behaviour returns to the refreshed dashboard.
- **Transactions list with filters** (reference 6): Phase D2; needs the approved Transactions destination (Q4).
- **Search-first security picker inside the form:** the existing search is kept beside the symbol field. Recent-securities chips are backlog.
- **Comma decimal separators:** not converted; the ledger accepts only `.` (explained next to the field instead).

## 9. Final run (sequential, clean test outputs)

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` | **BUILD SUCCESSFUL** (8 m 57 s) |
| core JVM / core iOS | **472 / 0** each (+1 `PortfolioPresenterSaveTest`) |
| shared Android host / shared iOS | **183 / 0** / **179 / 0** (+8 `PortfolioPhaseD1Test`) |
| default `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | **BUILD SUCCEEDED** |
| `:server:test` | NOT RUN (D1 changes no server code) |

One earlier full run failed in `PortfolioPresenterSaveTest` (JVM). The cause was a timing assumption in the new test: the mock has no valuation data, so a
background "Market valuation unavailable" message could replace the save error. The test now waits for that startup message and clears it. It then
passed 3 isolated reruns and the final full run. No app code changed for it.

**Working-tree note:** while D1 was in progress, another local session ("StockSteps Ubuntu backend migration") changed server files in this checkout:
- `Application.kt`, fixture sources, `ScreenerService.kt`, `MockModeTest.kt`;
- new `SampleCompanyCatalog.kt`, `catalog.json`, `MockCatalogTest.kt`;
- `docs/LOCAL_DEVELOPMENT_SERVER.md`.

They are **not part of D1** and were not modified here; that session committed them as `231a0a1`. They are also likely why the running MOCK server once threw `NoClassDefFoundError` mid-session
(it was restarted).

## 10. Phase D2 readiness

Ready after review, once the Transactions destination (Q4) is approved. D1 added no routes.

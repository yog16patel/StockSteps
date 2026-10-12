# Portfolio UI Refinement — Stage 1 P0 fix + Phase A (foundation and correctness)

Status: **committed and pushed** as "Fix Portfolio account selection by id and add Phase A shared form rules, readable labels, formatted holding amounts and consistent copy on Android and iOS" (2026-10-12), on top of `f55fd90` "Finish Company Comparison QA with a two-column chart selection summary,
scannable chart limitations, heading fit at large text and live Dynamic Type for buttons on Android and iOS". Plan:
`docs/PORTFOLIO_UI_REFINEMENT_PLAN.md` §Phase A (items A1–A9). Findings: `docs/PORTFOLIO_UI_AUDIT.md`. **Phase B is not started.**

No presenter-contract, API, ledger, calculation, persistence, entitlement, route or fixture change. `PortfolioEngine.validate` is unchanged and still
the only validation (Android client + server; iOS server).

## 1. Findings and fixes

### Stage 1 — A1 / finding A-F1 (P0): account chosen by name (Android)
- **Root cause:** `PortfolioTransactionForm` (Android) kept the account as an id but chose it with
  `PortfolioChoice("Account", …, available.map { it.name }) { selected -> accountId = available.first { it.name == selected }.id }`.
  With two accounts named "TFSA", both menu items returned "TFSA" and `first { it.name == … }` always resolved to the first account. Choosing the
  second account silently saved the transaction into the first one.
- **Fix:**
  - New shared `PortfolioFormRules.accountOptions` / `resolveAccountId` and an id-keyed `StockSelectField<PortfolioAccountOption>`. The option's `id`
    is what's selected, saved (`rememberSaveable` `String?`) and submitted.
  - Labels are display-only and always distinct: "TFSA · CAD", then + category, then "(n of m)". The supporting line shows "TFSA · reports in CAD".
  - The submitted transaction is built by `PortfolioFormRules.transaction(...)`, with the same field defaults as before and the account id passed
    through unchanged.
- **Safety rules in `resolveAccountId`:**
  - no choice yet → the dashboard's account, only if it's active;
  - a chosen account that was deleted → `""`, so Save is disabled until the user picks again (a transaction is never moved silently);
  - the dashboard default is pinned once (`LaunchedEffect`), as before;
  - an archived account of an edited transaction stays listed and selected.
- **iOS** already used `.tag($0.id)`. It now shares the same option labels.

### Phase A
| Item | Finding | Fix |
|---|---|---|
| A2 | A-F2 raw enum labels | Android `StockSelectField` with `PortfolioPresentation.transactionLabel` / `categoryLabel`; iOS `Picker` rows `Text(present.transactionLabel(type:))` / `categoryLabel` with the enum **name as tag** (bridge, payloads and storage unchanged). Covers options, the selected value and spoken values |
| A3 | A-F3 raw holding amounts | `PortfolioPresentation.holdingRealized` ("+USD 3.50", "-USD 1,200.25", "USD 0.00") and `holdingDividends` ("USD 12.50"); unparsable → "USD —". Realized also passes its direction to `StockMetricItem` |
| A4 | A-F7 buttons/errors | Android: Save and "Create an account first" → `StockButton` (PRIMARY = `primaryAction`); "Search and select a security" → `StockButton` TEXT + search icon; error text `negativeText` (polite live region). iOS: Save transaction / Save account / Create an account first → `.buttonStyle(.stockPrimary)`; error `colors.negativeText` (was `.red`). Delete confirm buttons use `negativeText` on Android |
| A5 | A-F4 keyboards | Android fields → `StockTextField`; Shares, Price, Amount, Fees, FX rate use `KeyboardType.Decimal` (symbol `Ascii`). Text is stored as typed: no rounding, trimming or normalising; blank/invalid input still goes to `PortfolioEngine.validate` |
| A6 | A-F18 copy drift | New `PortfolioDisplay` (shared): titles, sample banner, status lines, empty states, chart title/note/empty/spoken summary, About texts, form and account-form copy, delete confirmations, insights names. Used by Compose and SwiftUI |
| A7 | A-F10 | New `PortfolioFormRules`: `PortfolioFormField` (current order + `advanced` flag for Phase D), `visibleFields` / `shows`, `movesShares` (same condition as `PortfolioEngine.validate`), `label`, `decimal`, `primaryTypes` (Buy, Sell, Opening position), `otherTypes`, `typeOptions`, `categoryOptions`, `currencyOptions`, `redesignOrder` (Phase D, unused). Both forms render the visible fields in this order |
| A8 | A-F14 | `PortfolioDisplay.asOf` → `PortfolioDates.dateTime`: "As of Oct 8, 2026, 9:15 PM UTC" (UTC kept; unknown formats shown as given) on both platforms |
| A9 | A-F12 | "Portfolio insights" for the Android top bar, iOS navigation title, both screen headings and the entry card (+ spoken hint/click label). Routes and entitlements unchanged |

## 2. Files changed

- Shared (new):
  - `app/shared/.../presentation/portfolio/PortfolioFormRules.kt`
  - `PortfolioDisplay.kt`
  - test `PortfolioFormRulesTest.kt`
- Shared (modified):
  - `PortfolioForms.kt` (transaction + account forms)
  - `PortfolioScene.kt` (entry actions)
  - `PortfolioScreen.kt` (copy, holding details, delete dialogs)
  - `PortfolioHistoryChart.kt` (title/note/copy)
  - `PortfolioInsightsScreen.kt` (title, "Key observations", as-of)
  - `PortfolioPresentation.kt` (`signedMoney`, `holdingRealized`, `holdingDividends`)
  - `presentation/AppNavigation.kt` (three title strings from `PortfolioDisplay`)
  - test `PortfolioPresentationTest.kt`
- iOS:
  - `PortfolioForms.swift`
  - `PortfolioScene.swift`
  - `PortfolioScreen.swift`
  - `PortfolioHistoryChart.swift`
  - `PortfolioInsightsScreen.swift`
  - `PortfolioInsightsScene.swift`
- Docs:
  - this file
  - `docs/PORTFOLIO_UI_AUDIT.md`, `docs/PORTFOLIO_UI_REFINEMENT_PLAN.md` (written in the audit pass, same commit)
  - `docs/PROJECT_HANDOFF.md`
  - `docs/project-status.md`

## 3. Shared rules/components introduced

- `PortfolioFormRules` + `PortfolioFormField` + `PortfolioAccountOption` (display/selection only).
- `PortfolioDisplay` (copy).
- `PortfolioPresentation.signedMoney`.
- No new UI components: `StockSelectField`, `StockTextField`, `StockButton` and `.stockPrimary` are reused. No new tokens or palette.

## 4. Tests and results

New `PortfolioFormRulesTest` (18 tests):
- P0 account tests:
  - duplicate names give distinct labels and keep their ids;
  - same name with a different category is told apart by category;
  - selecting the first account and the second same-named account each submit that account's id;
  - switching between accounts;
  - a restored saved id;
  - a deleted account is never replaced silently;
  - with no choice, only the active dashboard default is used;
  - an archived account of an edited transaction stays selectable;
  - uniquely named accounts keep plain labels;
  - the built transaction keeps the existing defaults.
- Field-rule tests:
  - every `TransactionType` has its field set (an exhaustive `when`);
  - share fields match `PortfolioEngine.validate` for every type, with and without a symbol;
  - labels are readable and type-specific;
  - the type picker order;
  - decimal keyboards only on number fields;
  - `redesignOrder` covers every field;
  - typed numbers are submitted exactly as typed.

`PortfolioPresentationTest` +3 tests:
- holding-detail formatting;
- shared copy keeps the disclosures and has no advice words;
- Insights "As of" is readable and stays UTC.

Results, sequential run after cleaning the test outputs, 2026-10-12:

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue` | **BUILD SUCCESSFUL** (8 m 27 s) |
| core JVM | **471 / 0 failures** (unchanged) |
| core iOS simulator | **471 / 0** |
| shared Android host | **138 / 0** (was 117: +21) |
| shared iOS simulator | **134 / 0** (was 113: +21) |
| `:app:androidApp:assembleDebug` | OK |
| `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` | **BUILD SUCCEEDED**, no warnings in Portfolio files |
| `:server:test` | NOT RUN (no server change) |
| Device / emulator / simulator checks, captures, TalkBack, VoiceOver | **NOT RUN**: no approval for device use in this brief. Normal and disabled button states in both themes are verified only in code, through the shared `StockButton` / `.stockPrimary` (which own both states), not on screen |

## 5. Android / iOS parity checklist

| Check | Android | iOS |
|---|---|---|
| Account chosen and submitted by id | ✔ (fixed) | ✔ (already) |
| Same account option labels (duplicates told apart) | ✔ | ✔ |
| Readable type/category labels; enum names only as stored values | ✔ | ✔ |
| Same visible fields per type, same order, same labels | ✔ `PortfolioFormRules` | ✔ `PortfolioFormRules` |
| Decimal keyboard on number fields | ✔ (new) | ✔ |
| Primary form buttons on `primaryAction` | ✔ `StockButton` | ✔ `.stockPrimary` |
| Error text `negativeText` | ✔ | ✔ |
| Holding details Realized/Dividends formatted with currency | ✔ | ✔ |
| Same copy for sample, empty, status, delete, about, chart | ✔ | ✔ |
| Delete transaction confirmation explains recalculation | ✔ | ✔ (message added) |
| "Portfolio insights" name; readable "As of" | ✔ | ✔ |
| Client validation before sending | ✔ | — (server only; Phase D) |
| Edit form with Cancel | — (AlertDialog; Phase D) | ✔ |
| Date picker | — (Phase D) | — (Phase D) |

## 6. Known limitations

- Not checked on any device: layout of `StockTextField` / `StockSelectField` inside the Android edit `AlertDialog` (taller rows than before) and the
  `.stockPrimary` row inside an iOS `Form`. These need captures before sign-off.
- Android edit still opens inside an `AlertDialog` without Cancel (A-F8, Phase D). Dates are still typed (A-F5, Phase D). iOS still validates only on
  the server, and errors still come from the global presenter error (A-F9, Phase D).
- The Insights sample-scenario picker still uses `PortfolioChoice` (a Material `OutlinedButton`). It now shows scenario ids as given; those use dashes,
  not underscores. Insights styling (system fonts, 40 dp rows, `FilterChip`) is Phase C (C7).
- The holding-detail metric labels ("Price", "Cost", "Realized", "Dividends") are unchanged and not yet in `PortfolioDisplay`.

## 7. Remaining work (Phases B–E)

Unchanged from the plan:
- **B** — overview card (C1), "Show 1M", flat-series rule, iOS Dynamic Type pairing and touch targets, currency rule Q5, sample selector into ⋯.
- **C** — holdings rows, weight %, optional sort, donut, chart spec, holding details actions, Insights pass.
- **D** — date pickers, search-first layout with Advanced, validation placement and iOS client validation, Android edit with Cancel, Transactions
  destination.
- **E** — QA, captures and screen readers.

## 8. Deviations from the approved plan

1. **Chart title and note moved forward from B2.** The Phase A brief listed "account-value chart title and explanation" under A6, so the card now
   reads "Account value" plus the note "Includes cash, deposits and withdrawals, so a rise isn't always a gain. Returns that leave out deposits are in
   Portfolio insights (StockSteps+)." The empty state reads "No account value history shown". Layout is unchanged; the card isn't moved.
2. **Field visibility unified (A7)**, so one platform's set changed slightly for some types:
   - Android hides company name and exchange for cash-only types until a symbol is typed (the iOS behaviour);
   - iOS no longer shows shares/price for cash types that name a security, or the amount for share transfers (the Android/engine behaviour, i.e.
     what `PortfolioEngine` actually uses). Hidden values are still submitted as before; no rule changed.
3. Label wording:
   - "Trade / cash currency" → "Trade currency" (it sets the trade currency; the cash currency is its own field);
   - Android "Exact security symbol" and iOS "Security symbol (optional for cash)" → "Security symbol", or "(optional for cash)" for non-share types;
   - "Transaction" → "Transaction type".
4. The second "Insights" heading inside Portfolio insights (the engine's observation list) is now "Key observations", so the destination name isn't
   repeated.
5. iOS delete confirmations show the shared title with the account name, and the transaction message ("Balances will be recalculated…"), as Android does.
6. Merged copy where the platforms differed:
   - the sample banner: "Read-only sample. Your saved accounts and watchlists stay unchanged; switch to My saved accounts to make changes.";
   - the account note now carries both the moving-average-cost caveat (iOS) and "not tax advice" (Android);
   - iOS create-account title "Create investment account";
   - iOS add title "Add transaction" (was "Add investment").

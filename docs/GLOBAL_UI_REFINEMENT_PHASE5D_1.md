# Global UI Refinement Phase 5D.1 — Earnings accuracy and consistent meaning

Date: 2026-10-13. Baseline `c224890`. Scope: audit findings E-01, E-02, E-03 only (`docs/GLOBAL_UI_REFINEMENT_PHASE5D_AUDIT.md`). Status:
**implemented, automated suites pass, Android verified on device, iOS runtime BLOCKED** (simulator taps). Committed and pushed as "Add Global UI Refinement Phase 5D.1 Earnings accuracy with one BEAT, MET and MISS badge, placeholder-free estimate sentences, currency-correct history tooltips and Insights, and no invented dollar sign for a missing currency on Android and iOS".

## 1. Findings addressed

| ID | Problem | Resolution |
|---|---|---|
| E-01 | Results showed BEAT/MISS as a green/red pill; Details showed the same result in neutral brand blue | One shared mapping `EarningsDisplay.classification` → label + `StockBadgeKind` + spoken text, rendered with the design system's `StockStatusBadge` on both screens and both platforms |
| E-02 | "Analysts expect EPS of — and revenue of —." on missing estimates; iOS formatted the numbers itself (EPS without currency, revenue always "B") | One sentence built in core, `EarningsFormatter.estimateSentence`, from the existing EPS/revenue formatters; names only the estimates that exist; both platforms render `EarningsDetailsState.nextEstimate` |
| E-03 | Android history-chart tooltips formatted EPS with `currency = null` while rows showed the currency | `EarningsFormatter.historyPoint` builds each tooltip with that quarter's currency and the same formatter as the rows; `EarningsDetailsState.epsChartDetails` feeds the chart |

## 2. Shared design

```
core  EarningsFormatter.estimateSentence(estimate)      → "Analysts expect EPS of C$1.90 and revenue of C$4.50B." | "...EPS of $1.25." | "Analyst estimates aren't available yet."
core  EarningsFormatter.historyPoint(label, actual, est, currency) → "Q4 25 · reported C$1.86 · est. C$1.77" ("not reported" / "no estimate" when missing)
core  EarningsDetailsState.nextEstimate, .epsChartDetails  (filled in EarningsDetailsPresenter.build)
shared EarningsDisplay.classification(Classification) → EarningsClassificationDisplay(label, kind: StockBadgeKind, spoken)
         BEAT → "Beat estimate" / POSITIVE · MET → "Met estimate" / NEUTRAL · MISS → "Missed estimate" / NEGATIVE · UNAVAILABLE → "Comparison unavailable" / NEUTRAL
Android  ClassificationBadge(classification) = StockStatusBadge(label, kind, contentDescription = spoken)   (Results + Details)
iOS      earningsClassificationBadge(classification) = StockStatusBadge(text:kind:accessibilityText:)        (Results + Details)
```

`EarningsDisplay` lives in `app/shared` (next to `StockBadgeKind`, which is a UI type); the sentence and tooltip live in core next to
`EarningsFormatter`. Classification values come from the server; nothing is recomputed. Labels describe the result against the estimate, never
the investment (tested: no "good/bad/buy/sell/strong/weak/opportunity").

## 3. Files

| File | Change |
|---|---|
| `core/.../earnings/EarningsPresentation.kt` | `estimateSentence`, `historyPoint`; `EarningsDetailsState.nextEstimate`, `epsChartDetails`; presenter fills them |
| `app/shared/.../presentation/earnings/EarningsDisplay.kt` (new) | classification display contract |
| `app/shared/.../presentation/earnings/EarningsScreens.kt` | Results + Details use `ClassificationBadge` (old `SurpriseIndicator` and the neutral-blue headline removed); estimate sentence and chart tooltips from state |
| `app/iosApp/iosApp/EarningsScenes.swift` | Results + Details use `earningsClassificationBadge`; the Swift-side estimate formatting removed in favour of `state.nextEstimate` |
| `core/src/commonTest/.../earnings/EarningsPhase5D1Test.kt` (new, 8) | sentence: both / EPS only / revenue only / neither / NaN; zero and negative EPS; M vs B; missing currency; tooltip USD/CAD/null, row consistency, missing values, display-only rounding |
| `app/shared/src/commonTest/.../earnings/EarningsDisplayTest.kt` (new, 3) | each classification's label/kind/spoken text; no advice wording; same input → same display |

Unchanged: `EarningsMath`, classifications and thresholds, server, API, fixtures, StockSteps+, reminders, AI, navigation, layout.

## 4. Verification

| Verification | Result | Evidence |
|---|---|---|
| Core JVM tests | **PASS** | 486 / 0 (incl. 8 new) |
| Core iOS tests | **PASS** | 486 / 0 |
| Shared Android tests | **PASS** | 229 / 0 (incl. 3 new) |
| Shared iOS tests | **PASS** | 225 / 0 |
| Android build (`assembleDebug`) | **PASS** | BUILD SUCCESSFUL |
| iOS build (unsigned `xcodebuild`) | **PASS** | BUILD SUCCEEDED (+ ad-hoc signed simulator build) |
| Server tests | NOT RUN | no server code changed |
| Android runtime (dedicated `emulator-5554`, 360 dp, 1.0×, light, local MOCK) | **PASS** for E-01 and E-03 | see §5 |
| Android E-02 sentence on device | **NOT RUN** | no MOCK scenario reaches the "Not reported yet" card (it needs a next report with no current one); fixtures not modified; covered by unit tests |
| Android dark theme | NOT RUN | — |
| iOS runtime (iPhone 16, Large) | **BLOCKED** | simulator rebooted (only this app's panel was attached); launch, search and scrolling work, but taps on buttons ("View Results") don't register, the same failure as at the end of E.2 |
| Screen readers | NOT VERIFIED | deferred by the owner |

## 5. Screenshots (`docs/design/earnings_phase5d_1/`)

- `before-a360-results-ssrv.png`: Results before ("EPS Beat" custom pill).
- `after-a360-results-ssrv-beat.png`: Results "Beat estimate" (positive badge), SSRV.
- `after-a360-results-cnr-missed-unavailable.png`: Results "Missed estimate" (negative) and "Comparison unavailable" (neutral), CNR.TO in C$.
- `after-a360-details-cnr-missed.png`: Details now shows the same "Missed estimate" badge (before: neutral blue "Miss (−3.0%)" by code).
- `after-a360-details-cnr-history-tooltip-cad.png`, `…-tooltip-q4-selected.png`, `…-tooltip-matches-row.png`: tooltip "Q4 25 · reported C$1.86 ·
  est. C$1.77" equals the expanded row "C$1.86 vs C$1.77 est." (before: "$1.86" via `eps(it, null)`, by code).
- `after-a360-details-topp-unavailable.png`: TOPP upcoming, both cards "Comparison unavailable" with the estimates.
- `ios-i16-aapl-company-earnings-blocked-tap.jpg`: where iOS verification stopped.

## 6. Limitations and observations

- **Missing currency** keeps `EarningsFormatter`'s existing behaviour: `null` currency is shown as `$` (the formatter treats null like USD). This
  is the same for every earnings amount; changing it is a formatter-contract change, proposed for 5D.2, not done here.
- **E-03 on iOS**: the free iOS Details screen has no EPS history chart (Android has); the iOS StockSteps+ history chart already formatted with the
  currency (`EarningsPremiumFormat.chart`). Nothing to change there.
- **Out of scope, seen on device (recorded for later phases):**
  - Company Details' earnings summary still says "EPS Miss" / "EPS Beat · Revenue Miss" (a server-built headline, not this display contract).
  - History rows keep the server classification text "EPS Beat (+5.1%)" (plain text, not badges).
  - The Details "Insights" card (server text) shows "$1.94" for a CAD company.
  - The Details price-reaction label "Source" breaks mid-word at 1.0× ("Sou / rce"), audit E-10.
- No scope deviations.

## 7. Recommendation

**PASS WITH CONDITIONS.** E-01, E-02 and E-03 are implemented in shared code with tests, all suites and both builds pass, and Android device
checks confirm E-01 and E-03. Conditions: iOS runtime verification (blocked by simulator input), E-02 on a device (needs a data scenario MOCK
doesn't have), and dark theme.

## 8. Next (5D.2, not started)

Design-system alignment per the audit: warnings → `StockBanner`, other chips → `StockStatusBadge`, buttons/fields, iOS theme fonts, muted text;
plus the observations in §6 if the owner agrees.

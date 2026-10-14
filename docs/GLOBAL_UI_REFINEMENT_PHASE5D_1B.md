# Global UI Refinement Phase 5D.1B — Earnings currency accuracy fixes

Date: 2026-10-13. Owner decisions: **5D1A-1 approved** (Insights template uses the result's currency), **5D1A-2 approved, B1** (no symbol for a
null currency, plus one caption per card or section), **5D1A-3 deferred** (no REAL Finnhub currency enrichment). Status: implemented, all suites
pass, committed and pushed as "Add Global UI Refinement Phase 5D.1 Earnings accuracy with one BEAT, MET and MISS badge, placeholder-free estimate sentences, currency-correct history tooltips and Insights, and no invented dollar sign for a missing currency on Android and iOS".

## 1. Changes

| File | Change |
|---|---|
| `core/.../earnings/EarningsPresentation.kt` | `EarningsFormatter.symbol(null)` → no symbol (was "$"); new `UNKNOWN_CURRENCY` ("Currency not provided by the source.") and `currencyNote(vararg)`; estimate sentence appends the caption; Details `ResultCard.note` and the reaction sentence get it once; new `EarningsDetailsState.historyNote` |
| `core/.../earnings/EarningsCalculations.kt` | `EarningsInsightEngine`: private hard-coded-dollar `money()` removed; the EPS-surprise explanation uses `EarningsFormatter.eps(value, eps.currency)` and appends the caption when the currency is null. Titles, classification, ids and the API shape are unchanged |
| `core/.../earnings/EarningsResults.kt` | Results `ComparisonCardView.basisNote` gets the caption once per EPS / revenue card when its currency is null |
| `core/.../earnings/EarningsPriceReaction.kt` | Results price-reaction `warnings` get the caption once when prices have no currency |
| `core/.../earnings/EarningsPremiumPresentation.kt` | StockSteps+ history chart note gets the caption once when a quarter has no currency |
| `server/.../earnings/EarningsAi.kt` | `EarningsGrounding.UNKNOWN_CURRENCY_FACT` added to the context's *unavailable* list when a grounded amount (actual, estimate or reaction price) has no currency: "Currency: not provided by the source. Amounts are shown without a currency symbol; don't state or assume a currency." No prompt, model, entitlement or validator change |
| `app/shared/.../earnings/EarningsScreens.kt`, `app/iosApp/iosApp/EarningsScenes.swift` | render `historyNote` under "Earnings history" (the other captions use fields both platforms already render) |
| Tests: `core/.../EarningsPhase5D1BTest.kt` (new, 6), `EarningsPhase5D1Test.kt` (missing-currency test updated to the new rule), `server/.../EarningsGroundingCurrencyTest.kt` (new, 2) | see §3 |

**Weekly digest:** reviewed `server/.../EarningsDigest.kt`. It formats only dates, sessions and percentages, never amounts, so it can't imply a
currency. No change.

## 2. Before / after

| Case | Before | After |
|---|---|---|
| CAD Insights | "EPS was $1.94 against an estimate of $2.00 (GAAP diluted)." | "EPS was C$1.94 against an estimate of C$2.00 (GAAP diluted)." (verified on device) |
| USD Insights | "EPS was $1.94 against an estimate of $2.00 …" | unchanged |
| Unknown-currency Insights | "EPS was $1.94 …" | "EPS was 1.94 against an estimate of 2.00 (GAAP diluted). Currency not provided by the source." |
| Negative EPS, CAD | "−$0.05" | "−C$0.05" |
| Amounts, null currency | "$1.94", "$4.45B" | "1.94", "4.45B", plus one caption per card / section |
| Estimate sentence, null currency | "Analysts expect EPS of $1.25." | "Analysts expect EPS of 1.25. Currency not provided by the source." |
| History tooltip, null currency | "Q2 26 · reported $1.94 · est. $2.00" | "Q2 26 · reported 1.94 · est. 2.00", with the caption under "Earnings history" |
| AI grounding, null currency | facts "$1.94", no note | facts "1.94" plus the explicit unavailable-currency line |
| USD / CAD / EUR elsewhere | "$…", "C$…", "EUR …" | unchanged |

## 3. Verification

| Check | Status | Evidence |
|---|---|---|
| Core JVM | **PASS** | 492 / 0 (6 new) |
| Core iOS | **PASS** | 492 / 0 |
| Shared Android | **PASS** | 229 / 0 (E-01 `EarningsDisplayTest` unchanged and passing) |
| Shared iOS | **PASS** | 225 / 0 |
| Server | **PASS** | 501 / 0, 3 skipped (2 new; run alone) |
| Android build | **PASS** | `assembleDebug` OK |
| iOS build | **PASS** | unsigned `xcodebuild` BUILD SUCCEEDED |
| Android runtime: CNR.TO Insights (restarted MOCK, `emulator-5554`, 360 dp) | **PASS** | `after-5d1b-a360-details-cnr-insight-cad.png`; UI text "EPS was C$1.94 against an estimate of C$2.00 (GAAP diluted)."; the API returns the same string |
| Android runtime: unknown-currency scenario | **NOT RUN** | every MOCK fixture carries a currency, and production MOCK data wasn't modified. Covered by unit tests (formatter, sentence, Insights, grounding) |
| Android dark theme | **NOT RUN** | the app follows its own theme setting (Light); system dark mode doesn't change it, and app settings weren't changed |
| iOS runtime | **BLOCKED** | simulator taps (5D.1) |
| Screen readers | NOT VERIFIED | deferred by the owner |

Tests cover: CAD/USD/EUR/null formatting; zero and negative values; missing actual or estimate (no surprise insight); Insights text for CAD/USD/null;
tooltip and row consistency (5D.1 tests); AI grounding with CAD/USD/null and the estimate-currency fallback; E-01 labels unchanged; no "$$",
"C$C$", "C$$", "$C$" or dash in sentences; no "$" anywhere for a null currency.

## 4. Limitations

- REAL Finnhub calendar figures still have no currency (5D1A-3 deferred), so in REAL they now show without a symbol plus the caption.
  That's accurate, but less informative than a real currency.
- Already-cached server responses keep their old Insights text until refreshed (MOCK restarts; REAL caches expire per their TTL).
- Out of scope, seen again on device: price-reaction labels "Window" and "Source" break mid-word at 1.0× (audit E-10, 5D.3); Company Details'
  "EPS Miss" summary is server text.

## 5. Recommendation for Phase 5D.1

**PASS WITH CONDITIONS.** E-01, E-02, E-03 and both currency fixes are implemented with tests, and all suites (including server) and both builds
pass. Android device checks confirm E-01, E-03 and the CAD Insights fix. Conditions: iOS runtime (simulator input), an on-device unknown-currency
scenario (needs data MOCK doesn't have), and dark theme.

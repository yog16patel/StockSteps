# Global UI Refinement Phase 5D.4 — Earnings UX refinement

Date: 2026-10-14. Status: **committed and pushed** as "Add Global UI Refinement Phase 5D.4 Earnings UX refinement with EPS merged into the takeaway, compact metric rows, first-sentence explanations, a labelled EPS history chart and folded comparisons and notes on Android and iOS" (on top of `91961a2`). Plan: `docs/GLOBAL_UI_REFINEMENT_PHASE5D_4_AUDIT.md`.
Owner decisions (approved: "go ahead with your recommendation"):
- 5D4-1: simulator restart (no erase);
- 5D4-2: option A, first sentence + "Show details", server text unchanged;
- 5D4-3: EPS merged into the Details takeaway;
- 5D4-4: MOCK scenario chips left as they are.
D-3 (Auth emulator) is still not done.

Display only. Nothing changed in calculations, classifications, currency rules, the API, server text, AI, entitlements, reminders or navigation.

## Changes
| ID | Change |
|---|---|
| D4-01 | Details: the takeaway card now holds **Reported · Estimate · Surprise** (the EPS card's key metrics). Difference, Basis, the note and "What is earnings surprise?" sit in a collapsed **EPS details**. The separate EPS card shows only when there is no takeaway. Results keeps its full EPS card |
| D4-02 | Key metrics use `StockMetricGrid(rowsWhenNarrow = true)`: when three can't share a row, they become aligned label/value rows instead of three tall blocks. Secondary lines ("Growth vs last quarter") are one caption. At 2.0× the Revenue card went from about 1.5 screens to under 1 |
| D4-03/06 | Details notes, the sample sentence and the disclaimer fold into **Sources and disclosures** (each once, `EarningsDisplay.detailsDisclosures`) |
| D4-04 / R4-01 | Long server explanations show their **first sentence + "Show details"** (`EarningsDisplay.firstSentence`, which never splits amounts or percents): Details takeaway, Results EPS/revenue, insight cards (via the existing `StockInsightCard.details`) |
| D4-05 | History chart: **y-axis labels** (highest / middle / lowest of the shown values, in the quarters' currency; none with fewer than 2 reported values or mixed currencies) and **every quarter label** when up to 5 fit (3 at large text), else first and last only. No new data |
| R4-02 | Results: the previous-quarter comparison becomes a secondary revenue row ("Previous quarter (Q1 FY2026)": "C$4.28B · +4.2%"). The YoY and QoQ explanations sit under **About these comparisons**. The QoQ card only remains when its percent is unavailable |
| R4-03 | Results title "Q3 FY2026 results" at section size (was the 2-line "Q3 FY2026 Earnings Results" at screen-title size) |
| R4-04 | The server reaction explanation moved into "How to read this", between the numbers and the chart |
| iOS | `EarningsResultsScreen` and `PriceReactionSection` read `dynamicTypeSize` (live Dynamic Type refresh) |

Files:
- core: `EarningsPresentation.kt` (`epsChartYLabels`, `epsAxis`), `EarningsResults.kt` (`foldQuarter`, `quarterOverQuarterFolded`, `comparisonNotes`);
  test `EarningsPhase5D4Test.kt`.
- shared: `EarningsDisplay.kt`, `EarningsComponents.kt` (`EarningsExpandableText`), `EarningsScreens.kt`; test `EarningsDisplayTest.kt` (+5).
- iOS: `EarningsComponents.swift`, `EarningsScenes.swift`.

## Verification
| Check | Result |
|---|---|
| `:core:jvmTest` / `:core:iosSimulatorArm64Test` | **PASS** 502/0 / 502/0 |
| `:app:shared:testAndroidHostTest` / `iosSimulatorArm64Test` | **PASS** 242/0 / 238/0 (the first run failed to compile one test: a missing `assertNull` import, fixed) |
| `:app:androidApp:assembleDebug` | **PASS** |
| `xcodebuild` (iPhone 16, ad-hoc signed, MOCK URL) | **PASS** |
| `:server:test` | NOT RUN (no server change) |
| Android 360 dp light 1.0×: Details (merged takeaway, chart with y and all quarter labels, history rows) and Results | **PASS** (`after-a360-*.png`) |
| Android 360 dp 2.0×: Details Revenue | **PASS**, one screen (`after-a360-details-revenue-2.0x.png`) |
| Android dark (5D.4 build) | NOT RUN (5D.3 dark PASS; same tokens) |
| **iOS Earnings Results**, Large light and dark | **PASS** (`after-ios-results-*.png`) after the approved simulator restart: metrics, badges, Show details, reaction headline and chart, Sample badge |
| iOS accessibility XL | **PASS on a fresh launch** (`after-ios-results-ax-dark-top.png`: metrics switch to aligned rows). After a *live* size change, a few labels kept the old size until relaunch. The 5D.4 `dynamicTypeSize` fix is in the build but wasn't re-checked live |
| iOS Earnings Details | **BLOCKED**: needs the signed-in endpoint (D-3, Auth emulator) |
| TalkBack / VoiceOver | NOT VERIFIED |

Seen outside scope: the iOS Home brand "StockSteps" breaks mid-word at accessibility sizes (pre-existing, Home).

## Recommendation
**PASS WITH CONDITIONS.** Conditions: iOS Details (D-3), a live Dynamic Type re-check on iOS, and Android dark on this build.

## 5D.4.1 polish (2026-10-14, after `e965618`) — committed and pushed as "Polish Global UI Refinement Phase 5D.4 Earnings with one collapsed Show details per card, No estimate for missing estimates, separate Reported and Next header lines, dated reaction prices and compact insight rows on Android and iOS"

Owner approval: "Go ahead with your recommendation". That covers plan items 1–6 and 8–9, insights option (a) compact rows only (no insight hidden), the header split, and
D-3 (start the Auth emulator).

| # | Change (display only; Android + iOS) |
|---|---|
| 1 | **One "Show details" per card, collapsed by default** (`EarningsShowDetails`). Results EPS and Revenue keep their key metrics and badge visible. Behind the toggle: the rest of the server sentence, reason, basis, Difference, Year ago / Previous quarter and the comparison notes (previously always visible under a toggle that only expanded the sentence). The unknown-currency caption stays visible (`EarningsDisplay.visibleCaveat`) |
| 2 | Details takeaway: one "Show details" (rest of the explanation, Difference/Basis, note, lesson) plus "Learn more". The separate "EPS details" toggle is gone |
| 3 | A missing estimate reads **"No estimate"** (`EarningsFormatter.NO_ESTIMATE`, core) in the Estimate metric on Details and Results. The reason moves into Show details; the "Comparison unavailable" badge is unchanged. Never 0, never a beat/miss |
| 4 | Details header: "Reported · Wed, Jul 22 · After market close", then "Next · Q3 FY2026 · Thu, Oct 22" with "After market close · Confirmed" beneath (the full sentence is the line's spoken label). The approved move into the reminder sheet was **not done**: a caption line keeps the date status visible without touching the reminder UI |
| 5 | Reaction Before/After: price as the value, observation date as its detail line (same strings, split; core `MetricLine.detail`) |
| 6 | Results "Beginner takeaway" sits right under the header, first sentence + Show details |
| 7 (a) | Details insights: compact rows in one card (neutral icon, period, title). The unchanged server explanation and lesson sit behind Show details. No insight is hidden |
| — | Removed helpers the change replaced (`EarningsExpandableText` on both platforms, `secondaryCaption`, two titles) |

**Verification**
| Check | Result |
|---|---|
| core JVM / iOS | **PASS** 502/0 / 502/0 |
| shared Android / iOS | **PASS** 243/0 / 239/0 (+2 tests: visible caveat, reported line; 5D.3 tests updated for "No estimate" and the split reaction date) |
| `assembleDebug`, `xcodebuild` (iPhone 16) | **PASS** |
| Android 360 dp light 1.0× / 1.3× / 2.0× | **PASS** (`after-5d41-a360-details-*.png`). Long name wraps, header lines, buttons stack at 2.0×, No estimate, reaction dates, compact insights, chart |
| Android dark | **PASS** (`after-5d41-a360-details-dark.png`, `-results-dark.png`); theme restored to Light |
| iOS Results light | **PASS** (`after-5d41-ios-results-top.png`: collapsed by default, takeaway first; `…-eps-expanded.png`: Show details reveals the sentence, GAAP basis, Difference) |
| iOS dark / AX for 5D.4.1 | NOT RUN (5D.4 iOS dark and AX XL fresh-launch PASS) |
| **iOS Earnings Details** | **BLOCKED**. The Auth emulator was started (approved) and is running empty; the app's saved session reports "Your session has ended. Sign in again". Signing in or creating an account is the owner's to do. The emulator was stopped afterwards |
| Loading / empty / error states | NOT RUN (no MOCK scenario for them on Details) |
| Server tests, TalkBack/VoiceOver | NOT RUN / NOT VERIFIED |

**Recommendation: PASS WITH CONDITIONS.** Conditions: iOS Details after the owner signs in on the Auth emulator, and iOS dark/AX for 5D.4.1.

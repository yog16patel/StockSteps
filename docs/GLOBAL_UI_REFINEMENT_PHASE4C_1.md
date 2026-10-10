# Global UI Refinement Phase 4C.1 — Company Details polish

Status: **implemented, verified, committed and pushed** together with Phase 4C as "Refine Company Details for beginners with chart scroll fix and accessible price summary (Global UI Refinement Phases 4C and 4C.1)" (2026-10-10), on top of `4eb9e5b` (Phase 4B.1).
UI-only: no API, presenter, calculation, entitlement, navigation-destination, Firebase or fixture changes. Reference:
`docs/design/phase4c1/stocksteps-company-details-final-reference.webp` (visual guidance; no values copied).

## 0. Correction to earlier reports (Phases 4A–4C)
The iOS app's MOCK backend URL (`StockStepsMockBackendURL` in the app's Info.plist) is a **private-LAN address**, not this Mac. Every iOS device
check in Phases 4A, 4B, 4B.1, 4C and 4C.1 — including the temporary iPhone SE simulators — therefore read data from the **LAN MOCK backend**,
not the local `:server:runMock` server that earlier reports named. The requests were the app's normal read-only MOCK calls (no SSH, deploy,
configuration change, paid provider or AI calls). Android used the local server via `adb reverse`. This explains why iOS and Android showed
different session states/dates in screenshots (the LAN host runs the older Phase 5C image). Once noticed, no further iOS device checks were run.

## 1. Confirmed issues vs. visual recommendations
| # | Type | Issue | Status |
|---|---|---|---|
| B1 | **Bug** | Android: vertical drags starting on the chart scrubbed it instead of scrolling (worst at 1.3×, chart fills the view). Cause: `detectDragGestures` claims every direction | **Fixed** — `detectHorizontalDragGestures` |
| B2 | **Bug** | iOS: same conflict — `DragGesture(minimumDistance: 0)` on the chart blocked the ScrollView (confirmed on device) | **Fixed** — `simultaneousGesture(DragGesture(minimumDistance: 8))`, scrubs only while the drag is mostly horizontal |
| B3 | **Bug** | "Updated 4:00 PM EDT" next to "Today" without a date: a quote from an earlier session read as today's | **Fixed** — "Updated 4:00 PM EDT · Oct 7" (Android, JVM preview, iOS shared and Swift formatters) |
| B4 | **Bug** | "Why Did It Move?" listed sources as identical inline links ("Yahoo Yahoo Yahoo") | **Fixed** — removed; the "3 sources →" sheet lists each title |
| V1 | Visual | Market status chip beside the green/red change line could read as part of the movement | Chip moved beside the price; change line and update line below |
| V2 | Visual | Header actions as four equal secondary buttons | Add to portfolio (primary) + Compare, Practice Buy full width; stacked full width at large text |
| A1 | Accessibility | Price, change, time and session were separate announcements | One label: "Price $336.67. Up $3.04, 0.91%, today. Updated 4:00 PM EDT · Oct 7. US market: after-hours trading." |
| R1 | Recommendation (not changed) | Core insight text "kept about $-80 of every $100" for loss-making companies (RIVN) reads awkwardly | Presenter wording — needs approval to change |
| R2 | Recommendation (not changed) | Offline chart message says "Price history isn't available for this range" (cause is the connection) | Section state can't distinguish; presenter change |
| R3 | Recommendation (not changed) | "Today" label for a change from an earlier session | The date now shows; relabelling needs a date rule in the presenter |

## 2. Changes made / 3. Files
- `designsystem/components/StockLineChart.kt` — horizontal-only drag scrub (tap-and-hold scrub unchanged).
- iOS `DesignSystem/StockDetailComponents.swift` — `StockLineChart` simultaneous, horizontal-only scrub.
- `presentation/companydetails/QuoteTime.{android,jvm,ios}.kt`, iOS `CompanyDetailsScreen.swift` `quoteTime` — date added.
- `presentation/companydetails/CompanyDetailsScreen.kt` and iOS `CompanyDetailsScreen.swift` — price block (price + status chip, change, update
  line, one spoken label), `CompanyActions` layout, inline source links removed.
- `presentation/companydetails/CompanyDetailsPresentation.kt` — `priceDescription`; test `CompanyDetailsPresentationTest` (+1, 5 total).

## 4. Android/iOS consistency
Same header order, labels, spoken sentence and action layout. iOS keeps SF Symbols on actions; Android uses text buttons. Large-text switch:
Android font scale ≥ 1.3, iOS ≥ xxxLarge. Chart: both scrub horizontally and let vertical drags scroll.

## 5. Device evidence (`docs/design/phase4c1/`)
Android (local MOCK, emulator-5554, signed-in account view-only): `android-1.3x-360dp-before-after-vertical-swipe-on-chart.jpg` (swipe from inside
the chart scrolls), `android-chart-scrub-held-and-released.jpg`, `android-negative-change-nvda.jpg`, `android-no-dividend-no-quote-time-intc.jpg`,
`android-missing-pe-negative-margin-rivn.jpg`, `android-offline-error-states.jpg`. iOS (iPhone 16, **LAN MOCK backend**, see §0):
`ios-header-and-scrub.jpg`, `ios-dark-xxxl-long-name-swipe-on-chart.jpg`, `ios-missing-open-volume-tsla.jpg` (missing Open/Volume shown as "—").
Edge cases came from existing MOCK companies (NVDA negative change, INTC no dividend/no quote time/no 52-week range/−16 % valuation gap, RIVN
"N/A" P/E and −80 % margin, SXTC long name); no fixture or test-only data was created. During the Android pass an injected tap opened the
"Add transaction" form (TalkBack routing, below); it was closed without entering or saving anything.

## 6. Accessibility checks actually performed
- **TalkBack: NOT completed.** TalkBack was enabled on the emulator (approved) and later disabled (settings restored to their previous empty/0
  values), but adb-injected swipes, keyboard shortcuts and taps are not routed through TalkBack's touch exploration on this emulator, so focus
  could not be moved; no spoken output was verified.
- **Android accessibility-tree review (performed):** price block exposes the single sentence above; chart exposes "Price chart, 1M: from 13.17 to
  13.20, +0.23%."; watchlist action labelled "Add to watchlist" (48 dp); metric/info rows merge label and value (`mergeDescendants`).
- **VoiceOver: NOT RUN** (not available in the Simulator); iOS labels reviewed in code only.

## 7. Tests and builds
`./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`
→ core JVM **430/0**, core iOS **430/0**, shared Android **101/0** (+1), shared iOS **97/0** (+1), `assembleDebug` OK; iOS `xcodebuild … generic/platform=iOS
Simulator … build` **BUILD SUCCEEDED** (final run, after every edit). Gradle and Xcode were run sequentially.

## 8. Known limitations and regression risks
- `StockLineChart` is shared by every chart screen: drags now scrub only horizontally (vertical drags scroll) on both platforms.
- The quote time format changed everywhere `formatQuoteTime` is used (Company Details only).
- Not run: iOS error/offline state, iOS narrow (SE) re-check after the header change (stopped once the LAN routing was found), TalkBack, VoiceOver.

## 9. Readiness for Phase 5
Ready for review. Before Phase 5: a manual TalkBack/VoiceOver pass on a physical device or interactive emulator; decide R1–R3; decide whether iOS
device checks should keep using the LAN MOCK backend or a local one (`StockStepsMockBackendURL`). Phase 5 starts only after approval.

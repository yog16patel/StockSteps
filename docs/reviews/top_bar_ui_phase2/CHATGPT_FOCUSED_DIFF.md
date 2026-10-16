# Top Bars Phase 2 (KAN-16) — focused diff

Requested by the owner. Under `docs/AI_REVIEW_WORKFLOW.md` §3.4 it touches navigation chrome (not routing or state). Excerpts only.

## 1. iOS status-bar backdrop — `DesignSystem/StockLandingTopBar.swift`, `AppScene.swift`
```swift
private struct LandingStatusBarBackdrop: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    func body(content: Content) -> some View {
        content.overlay(alignment: .top) {
            StockStepsTheme.colors(scheme).appBackground
                .ignoresSafeArea(edges: .top)
                .frame(height: 0)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
    }
}
// AppScene.tabStack
root()
    .stockStepsTopBar(.screen(router.rootTitle(tab), visible: false))
    .landingStatusBarBackdrop()
```
Decision: one container-level modifier on each tab root (landing pages hide the bar); no spacer, no padding, no state. Risk: the overlay is
above content and opaque — hit testing is off and it only covers the inset, so no control is hidden (verified: landing titles, pull to refresh
and reselect unchanged). Modifier order is load-bearing (the inverse drew nothing).

## 2. Titles — `FinancialsDisplay.kt`, `ValuationDisplay.kt` (+ both platforms' scenes)
```kotlin
fun title(symbol: String): String = "$symbol Financials"      // FinancialsDisplay
fun title(symbol: String): String = "$symbol Valuation"       // ValuationDisplay
```
```kotlin
AppBarConfiguration(title = FinancialsDisplay.title(state.symbol), backButton = AppBarBackButton.BACK)   // was stringResource(financials_title, shortName)
```
```swift
.navigationTitle(FinancialsDisplay.shared.title(symbol: model.symbol))   // was "\(model.shortName) Financials"
```
Decision: one shared rule, so both platforms show the same title. Risk: copy change ("Microsoft Financials" → "MSFT Financials"); the full name
is still the page's first line. Tests: `FinancialsDisplayTest.titleUsesTheTicker…`, `ValuationDisplayTest.titleUsesTheTicker`.

## 3. iOS Back label from Company Details — `CompanyDetailsScreen.swift`
```swift
.navigationTitle(model.symbol)
.toolbar {
    ToolbarItem(placement: .principal) { Text(verbatim: " ").accessibilityHidden(true) }
    …
```
Decision: the Learn pattern — the title only names the next page's Back ("‹ PFAI"), the bar shows nothing. Risk: none for the cross-tab
trail (`TabRouter.rootTitle` and `hidesBackTitle` unchanged; it already used `AppDestination.backTitle` = symbol for companies).

## 4. iOS sheet Done placement — `AppScene.swift`
`ToolbarItem(placement: .cancellationAction)` → `.confirmationAction` for "Done" on Settings and the global Notifications sheet (every
other sheet already does this). Behaviour unchanged.

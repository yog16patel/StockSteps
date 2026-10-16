# Focused diff: Phase 5D.5.6C4 (Portfolio nested tabs)
These are excerpts of the decisive hunks. The full code is in the files listed in the report §3.

## 1. Shared rule (core `navigation/PortfolioSection.kt`)
```kotlin
enum class PortfolioSection(val label: String) { MY_PORTFOLIO("My Portfolio"), PRACTICE("Practice") }
object PortfolioSections {
    val DEFAULT = PortfolioSection.MY_PORTFOLIO
    const val SELECTOR_SPOKEN = "Portfolio sections"
    fun sectionFor(screen: ScreenId): PortfolioSection? = when (screen) {
        ScreenId.PRACTICE, ScreenId.PRACTICE_ORDER -> PortfolioSection.PRACTICE
        ScreenId.PORTFOLIO, ScreenId.HOLDING_DETAILS, ScreenId.PORTFOLIO_TRANSACTIONS, ScreenId.PORTFOLIO_TRANSACTION,
        ScreenId.PORTFOLIO_INSIGHTS -> PortfolioSection.MY_PORTFOLIO
        else -> null }
    fun isLandingSection(screen: ScreenId): Boolean = screen == ScreenId.PRACTICE
}
```
**Decision:** one rule for both platforms. The navigators set the section on every `open`, so Back from a child shows its own section.

**Tests:**
- `PortfolioSectionTest`;
- `AppScreensTest.portfolioChildrenReturnToTheirSection`.

## 2. Android navigator (`TabNavigator.kt`)
```kotlin
fun open(route: Any) {
    if (route is PracticeRoute) { showPortfolioSection(PortfolioSection.PRACTICE); return }
    val screen = screenIdOf(route) ?: error(…)
    PortfolioSections.sectionFor(screen)?.let { section.value = it }
    …
}
private fun showPortfolioSection(value: PortfolioSection) {
    dismissModals()
    if (currentTab() != AppTab.PORTFOLIO) selectTab(AppTab.PORTFOLIO)
    nav.popBackStack(AppTab.PORTFOLIO.rootRoute(), inclusive = false)
    sync()
    section.value = value
}
```
**Decision:** Practice becomes a section and is no longer a stack entry. That means no duplicate Practice page and no hop. `reset()` restores
My Portfolio.

## 3. Android landing (`AppNavigation.kt`)
```kotlin
route<PortfolioRoute> {
    val section = nav.portfolioSection
    val sections = rememberSaveableStateHolder()
    if (accounts != null) sections.SaveableStateProvider(section.name) {
        when (section) {
            MY_PORTFOLIO -> PortfolioScene(…, section = section, onSection = nav::selectPortfolioSection, …)
            PRACTICE -> PracticeScene(…, header = { portfolioLandingHeader(section, nav::selectPortfolioSection, openNotifications, openSettings) })
        }
    }
}
```
**Decision:** each section keeps its saved state while the other is shown. The header is a landing-bar item plus a `stickyHeader` with
`StockSegmentedControl(asTabs = true)` (`Role.Tab`).

**Evidence:**
- D: scroll pixel-identical;
- K: 0 requests on switches.

## 4. iOS landing (`AppScene.swift`, `TabRouter.swift`)
```swift
ZStack {
    PortfolioScene(…, section: section).sectionVisible(mine)
    if !mine || practiceShown, let practice = practiceSectionModel() {
        PracticeScene(model: practice, …, landingBar: AnyView(StockLandingTopBar(tab: .portfolio, …)),
                      landingTabs: AnyView(PortfolioSectionTabs(selection: section).pinnedTabsBackground(…)))
            .onAppear { practiceShown = true }
            .sectionVisible(!mine)
    }
}
func sectionVisible(_ visible: Bool) -> some View { opacity(visible ? 1 : 0).allowsHitTesting(visible).accessibilityHidden(!visible) }
// scroll-to-top acts only in the selected section
if section == nil || router?.portfolioSection == section { withAnimation { position.scrollTo(edge: .top) } }
```
**Decision:** keeping both sections alive preserves the scroll position and `@State` of each, which SwiftUI would otherwise reset. A hidden
section can't be touched and is hidden from VoiceOver.

**Risk:** a kept-alive view must follow live Dynamic Type changes, so Practice now reads `dynamicTypeSize` (checked live).

## 5. Lifecycle
```kotlin
// PracticeScenes.kt — removed: LifecycleResumeEffect { presenter.refresh() }
// The presenter loads on start and on each account change; orders, pull-to-refresh and Retry refresh explicitly.
val backToPractice = { nav.open(PracticeRoute); accounts?.practice?.refresh() }
```
```swift
// removed: .onChange(of: router?.path(.portfolio).last == .practice) { … refresh() }
onViewHoldings: { router.showPortfolioSection(.practice); practiceModel.presenter.refresh() }
```
**Evidence (Android, cold start):**
- Portfolio: 0 requests;
- Practice: 1× `/me/practice` + 1× `/performance`;
- My Portfolio ↔ Practice: 0;
- Markets → back: 0;
- reselect: 0.

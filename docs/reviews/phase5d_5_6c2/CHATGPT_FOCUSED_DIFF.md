# Focused diff: Phase 5D.5.6C2 (iOS navigation)
Excerpts of the decisive hunks. The full code is in `app/iosApp/iosApp/TabRouter.swift` and `AppScene.swift`.

## 1. One stack per tab (`AppScene`)
```swift
TabView(selection: router.selection) {
    tabStack(.markets, title: "Markets") { MarketsScene(…) } .tabItem { … } .tag(AppRoute.markets)
    …
}
private func tabStack<Root: View>(_ tab: AppRoute, title: String, @ViewBuilder root: () -> Root) -> some View {
    NavigationStack(path: router.pathBinding(tab)) {
        root().stockStepsTopBar(.screen(title, visible: false))
            .navigationDestination(for: AppDestination.self) { destination($0) }
    }
    .sampleDataInset()
}
```
**Decision.** The outer `NavigationStack` around the `TabView` is gone. Each tab owns a native stack, so pushes keep the tab bar, and SwiftUI
keeps every tab's views (state, scroll) alive across switches.

**Risk.** Pushed views reappear on tab switches, so `.task` can re-run (C3 audit).

**Evidence:** device A and E (pixel-identical).

## 2. Typed destinations → core ownership (`TabRouter.swift`)
```swift
enum AppDestination: Hashable { case companyDetails(String), learn(LearnTarget), dailyBrief(BriefTarget), … }
var screen: ScreenId { switch self { case .companyDetails: .companyDetails … } }   // exhaustive; the only iOS table
```
**Decision.** 16 typed cases replace 17 flags; the tab comes only from `NavigationPolicy.parentTab(screen:)`. The compiler enforces the
`switch`, so a new case can't miss its owner.

## 3. Open / Back via the shared model
```swift
func open(_ destination: AppDestination) {
    global = nil
    guard let owner = NavigationPolicy.shared.parentTab(screen: destination.screen) else { return }
    sync(selected)
    let next = nav.open(state: state, screen: destination.screen, key: destination.key)   // core decides push / reuse / hop
    if pushed { paths[tab, default: []].append(destination) }
    selected = tab
    …
}
private func setPath(_ new: [AppDestination], for tab: AppRoute) {   // back button and swipe-back both arrive here
    if tab == selected, new.count < old.count, let hop = state.hops.last, hop.tab == tab.tab,
       Int(hop.depth) == old.count + 1, !hop.reused, new.count + 1 < Int(hop.depth) {
        sync(tab); selected = AppRoute.of(hop.origin); …; return          // hop end → origin tab
    }
    sync(tab)
}
```
**Decision.** Same contract as Android C1 (`TabNavigation.open/sync`). The native path is the truth, and Back is detected from the path shrinking
past the hop's entry, which covers swipe-back too.

**Risk.** Reused-top opens can't record a hop (documented). Nested `isPresented` pushes aren't on the path (C3).

**Tests:** core `TabNavigationModelTest` (incl. `sync`), device B (button + swipe).

## 4. Tab bar taps (`selection`)
```swift
Binding(get: { self.selected }, set: { tab in tab == self.selected ? self.reselect() : self.selectTab(tab) })
```
**Decision.**
- A tap on another tab → `selectTab`, which clears the trail (D2).
- A tap on the visible tab → `reselect`: an empty path posts a scroll-to-top signal (D12), otherwise the path is cleared (D3).

**Evidence:** device C.

## 5. Account safety
```swift
.onChange(of: accounts.state.user?.id) { _, _ in router.reset() }
```
**Decision.** Any account change resets every path, the trail and the global sheet (I11). `ContentView` also rebuilds `AppScene` on sign-out.

**Evidence:** code review (no sign-in on devices).

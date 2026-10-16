# Focused diff: Phase 5D.5.6C3 (iOS navigation and lifecycle)
Excerpts of the decisive hunks. The full code is in `app/iosApp/iosApp/TabRouter.swift`, `AppScene.swift` and the feature scenes listed in the
report §5.

## 1. Nested pushes → router (example: Company Details)
```swift
@Environment(TabRouter.self) private var router: TabRouter?
…
onOpenFinancials: { router?.open(.companyFinancials(symbol)) },
onLearnTerm: { router?.open(.learn(.term($0))) },                 // Learn-owned: selects Learn with a hop
onResearch: { router?.open(.research(ResearchTarget(symbol: symbol, name: model.overview.value?.name))) },
// removed: .navigationDestination(isPresented: $showFinancials/…/$showMovement), (item: $learnTerm), (item: $research)
```
**Decision.** One navigation system per tab stack. Every push is an `AppDestination` decided by core `NavigationPolicy`.

**Risk.** Sub-pages used to share Company Details' client. Mitigated by `CompanyModelStore` (one model and client per symbol, released when
the symbol is on no stack).

**Evidence:** device A–D; G: Financials makes only its own 2 requests.

## 2. Back across tabs, including the reused top (`TabRouter.setPath`)
```swift
if tab == selected, new.count == old.count - 1, Array(old.dropLast()) == new,
   let hop = state.hops.last, hop.tab == tab.tab, Int(hop.depth) == old.count + 1 {
    let next = nav.back(state: state)                      // core: hop end → origin; a reused entry stays on its stack
    paths[tab] = new; selected = AppRoute.of(next.selected); state = next; sync(selected)
    if hop.reused { DispatchQueue.main.async { if self.path(tab) == new { self.paths[tab] = old; self.revision += 1 } } }
    return
}
```
**Decision.** One native pop of a hop's entry (back button or swipe) follows core `back`. For a reused top, the native pop is undone after the
tab switch, so the page isn't lost or duplicated (I1).

**Defect found in QA.** Restoring inside the pop transaction was ignored by SwiftUI, so Markets lost Apple. It's now deferred one run-loop turn
and re-verified.

**Tests:** core `reusedTopThenANestedHopUnwindsWithoutDamage`; device E.

## 3. Truthful native back labels
```swift
.stockStepsTopBar(.screen(router.rootTitle(tab), visible: false))   // root title = first push's back label
func rootTitle(_ tab: AppRoute) -> String {
    if let hop = state.hops.first(where: { $0.tab == tab.tab && Int($0.depth) == 2 }) { return visibleTitle(AppRoute.of(hop.origin)) }
    return tab.tab.label
}
.toolbarRole(router.hidesBackTitle(destination, on: tab) ? .editor : .automatic)  // deeper hop entry: chevron only
```
**Decision.** Keep the native back button (swipe-back, VoiceOver, Dynamic Type) and change only its title source.

**Risk.** The root title is also used in the long-press back menu.

**Evidence:** "‹ Watchlist", "‹ Home", "‹ AAPL" on device.

## 4. Lifecycle rule
```swift
.taskOnce(id: target.id) { model.presenter.open(id: target.briefId) }   // was .task(id:): refetched on every reappearance
if !recordedView { recordedView = true; accounts.client?.recordViewedCompany(symbol: symbol) }
.onChange(of: router?.path(.portfolio).last == .practice, initial: true) { _, top in if top || router == nil { model.presenter.refresh() } }
```
**Decision.** With five live stacks, fetching work runs once per screen. Practice refreshes when shown or when it becomes the top again, not on
tab switches.

**Evidence:** 0 requests on Portfolio ↔ Markets round trips with Apple and with Financials open (counting proxy).

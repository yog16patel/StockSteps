# Focused diff: Phase 5D.5.6C1 (navigation)
Short excerpts of the decisive hunks. The full code is in `app/shared/.../presentation/TabNavigator.kt`, `AppNavigation.kt` and core
`navigation/TabNavigationModel.kt`.

## 1. Tab switch: native saved stacks, symmetric for all tabs (`TabNavigator.switchGraph`)
```kotlin
nav.navigate(tab.graph()) {
    if (from != null) popUpTo(from.graph()) { inclusive = true; saveState = true }
    launchSingleTop = true
    restoreState = true
}
```
**Decision.** The whole current tab graph is popped with `saveState` and the target graph restored. The old code used
`popUpTo(DiscoveryRoute){saveState}` with `restoreState = tab != HOME`, so Home was never restored.

**Risk.** The root graph's start destination (Home) leaves the back stack while another tab is shown. Root Back is handled by the navigator
(non-Home root → Home).

**Tests:** device A, E and `tabSwitchRestoresStacksUntouched` (model).

## 2. Canonical open (`TabNavigator.open`)
```kotlin
ScreenKind.MODAL, ScreenKind.GLOBAL -> nav.navigate(route) { launchSingleTop = true }
ScreenKind.TAB_ROOT -> selectTab(screen.tab!!)
ScreenKind.TAB_SCREEN -> {
    dismissModals(); sync()
    val next = TabNavigation.open(state, screen, key(route))      // core decides tab, push or reuse, hop
    if (next.selected != state.selected || currentTab() != tab) switchGraph(tab)
    if (next.stack(tab).size > state.stack(tab).size) nav.navigate(route)
    ...
}
```
**Decision.** Every navigation goes through the policy and model. Identical tops are reused (I6). Modals are closed before a tab screen opens
(rule 5).

**Risk.** A direct `navController.navigate(tabRoute)` added later would bypass it. Mitigation: KDoc, review rule.

**Tests:** `watchlistToCompanyDetailsAndBack`, `repeatedNavigation…`, `modalAndGlobalScreensAreNeutral`, device B, D, F.

## 3. Back (`TabNavigator.back`) and its single registration
```kotlin
if (top.destination.tab() == null) return nav.previousBackStackEntry != null && nav.popBackStack()   // modal/global
sync(); val next = TabNavigation.back(state)
when {
    next == state -> if (tab != HOME) { selectTab(HOME); true } else false                           // root policy
    next.selected != tab -> { if (popped) nav.popBackStack(); update(next); switchGraph(next.selected) } // hop end
    else -> { nav.popBackStack(); update(next) }
}
// AppNavigation, after the NavHost:
AppBackHandler(enabled = backStackEntry != null && nav.canGoBack()) { nav.back() }
```
**Decision.** One Back path. It's registered after the NavHost so it is consulted first. It's disabled at the Home root with no trail, so the
system closes the app.

**Risk.** If Navigation's own handler won, a cross-tab Back would pop within the tab.

**Tests:** device B, D2, root Back, rotation/process death; model `nestedHopsUnwindInOrder`, `reselect…`.

## 4. Host → model sync (core `TabNavigation.sync`)
```kotlin
val hops = state.hops.filter { it.tab != tab || (it.depth <= entries.size && it.depth <= old.size && entries[it.depth - 1] == old[it.depth - 1]) }
return TabNavigationState(state.stacks + (tab to entries), tab, hops)
```
**Decision.** The NavController is the truth for the visible stack. A hop survives only while its exact entry is still on the stack (I4), so
in-screen pops and pop-to flows never leave a stale trail.

**Tests:** `syncFollowsTheHostAndDropsStaleHops`, `randomWalksKeepInvariants`.

## 5. Ownership = graph membership (`AppNavigation`)
```kotlin
val routes = RouteTable().apply { route<CompanyDetailsRoute> { … } … }
AppTab.entries.forEach { tab ->
    navigation(startDestination = tab.rootRoute(), route = tab.graphClass) {
        routes.items.filter { (type, _) -> routeTab(type) == tab }.forEach { (_, register) -> register() }
    }
}
routes.items.filter { (type, _) -> routeTab(type) == null }.forEach { (_, register) -> register() }   // modal / global
```
**Decision.** Each route's graph comes from `NavigationPolicy.parentTab(screenIdOf(type))`. The bottom bar selects the destination's graph, so it
can't disagree with the policy.

**Tests:** `AppScreensTest` (42 types, roots, coverage).

## 6. Saved trail
```kotlin
val trail = rememberSaveable(saver = TabNavigator.SnapshotSaver) { mutableStateOf(TrailSnapshot()) }
```
**Decision.** The model state and per-entry route keys are saved beside the NavController state (string list). They are restored on activity
recreation and process death, and hops whose entries didn't return are dropped by `sync`.

**Tests:** `theTrailSurvivesSavingAndRestoring`; device rotation and process death.

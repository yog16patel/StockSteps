# Phase 8E — Navigation architecture audit (before changes)

## Android
- **Library:** JetBrains `org.jetbrains.androidx.navigation:navigation-compose` 2.9.2 (`gradle/libs.versions.toml`). On Android it delegates to
  `androidx.navigation:navigation-compose-android` 2.9.7 (resolved `debugRuntimeClasspath`). No migration.
- **Host:** one `NavHost` in `presentation/AppNavigation.kt`, inside a `Scaffold`:
  - the Scaffold `topBar` (`StockStepsTopBar`, pushed screens) and `bottomBar` (5 tabs) sit **outside** the NavHost;
  - one nested graph per tab (`RouteTable`), with modal and global routes at the top level.
- **Navigator:** all navigation goes through `TabNavigator` (`open`, `back`, `selectTab`, reselect, `reset`, Portfolio sections). A tab
  switch is `navigate(tabGraph) { popUpTo(current) { saveState }; restoreState; launchSingleTop }`.
- **Transitions before 8E:** none defined anywhere in the app (`grep enterTransition|exitTransition|AnimatedContent|slideIn|fadeIn` found
  nothing in the app code). The NavHost therefore used the library default, read from the 2.9.7 sources: `fadeIn(tween(700))` /
  `fadeOut(tween(700))`, run together, for **every** destination change, including tab switches (a graph navigate) and modals.
- **Back:**
  - `AppBackHandler` (after the NavHost) → `TabNavigator.back()` takes every system Back;
  - the NavHost's own `PredictiveBackHandler` therefore never drives a seekable predictive-back animation;
  - the manifest doesn't set `enableOnBackInvokedCallback`.
- **Bottom bar:** persistent; it is never recreated, only re-selected (it hides on modal/global screens).
- **Internal segments:** not navigation, so no NavHost transition:
  - Portfolio My Portfolio ↔ Practice (`SaveableStateProvider`, instant);
  - the Practice inner tabs (presenter state);
  - the Company Details section bar (scroll only).

## iOS
- **Structure:** a `TabView` of five `NavigationStack(path:)` (`AppScene.swift`, `TabRouter`).
- **Motion:** native push/pop and the interactive swipe-back; native sheets for modals.
- **Custom transitions:** none at navigation level. The only `.transition(.opacity)` is a content fade inside the transactions list.
- **Tab switching:** instant (native `TabView`).
- **Reduce Motion:** handled by the system for native transitions; the app adds no navigation animation that would need its own check.

## Answers to the audit questions
| Question | Android before 8E | iOS |
|---|---|---|
| Which transitions run? | 700 ms cross-fade, old and new together, on every change | Native push/pop, native sheet, instant tabs |
| Where defined? | Library default (no app code) | System |
| Inconsistent? | Tabs fade like pushes; forward and Back look identical; modals fade the same way | Consistent |
| Animate twice? | No double animation, but both pages are visible at once for ~0.5 s (`before-tab-switch-double-exposure.png`) | No |
| Bottom bar recreated? | No (persistent); its selection changes instantly while the content is still fading | No |
| Content flashes? | The top bar (outside the NavHost) appears or disappears immediately, so the old page is shifted and shown under the new page's top bar before the fade (`before-push-topbar-over-old-page.png`) | Not observed in steady state |
| Back stack retained? | Yes (5D.5.6C1/D behaviour, unchanged) | Yes |

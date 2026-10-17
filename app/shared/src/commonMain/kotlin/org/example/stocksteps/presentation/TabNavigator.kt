package org.example.stocksteps.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.saveable.Saver
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable
import org.example.stocksteps.navigation.AppTab
import org.example.stocksteps.navigation.Hop
import org.example.stocksteps.navigation.NavEntry
import org.example.stocksteps.navigation.ReselectEffect
import org.example.stocksteps.navigation.ScreenId
import org.example.stocksteps.navigation.ScreenKind
import org.example.stocksteps.navigation.TabNavigation
import org.example.stocksteps.navigation.TabNavigationState
import org.example.stocksteps.presentation.discovery.DiscoveryRoute
import org.example.stocksteps.presentation.learn.LearnRoute
import org.example.stocksteps.presentation.markets.MarketsRoute
import org.example.stocksteps.presentation.portfolio.PortfolioRoute
import org.example.stocksteps.presentation.watchlist.WatchListRoute
import kotlin.reflect.KClass

/** 5D.5.6C1: one nested navigation graph per primary tab; every tab-owned route is registered in its policy tab's graph. */
@Serializable internal data object HomeGraph
@Serializable internal data object MarketsGraph
@Serializable internal data object PortfolioGraph
@Serializable internal data object WatchlistGraph
@Serializable internal data object LearnGraph

internal fun AppTab.graph(): Any = when (this) {
    AppTab.HOME -> HomeGraph
    AppTab.MARKETS -> MarketsGraph
    AppTab.PORTFOLIO -> PortfolioGraph
    AppTab.WATCHLIST -> WatchlistGraph
    AppTab.LEARN -> LearnGraph
}

internal val AppTab.graphClass: KClass<*> get() = graph()::class

/** The landing route of a tab (its graph's start destination); `AppScreensTest` checks it against `NavigationPolicy.root`. */
internal fun AppTab.rootRoute(): Any = when (this) {
    AppTab.HOME -> DiscoveryRoute
    AppTab.MARKETS -> MarketsRoute
    AppTab.PORTFOLIO -> PortfolioRoute
    AppTab.WATCHLIST -> WatchListRoute
    AppTab.LEARN -> LearnRoute
}

/** The tab whose graph contains this destination; null for top-level (modal and global) destinations. */
internal fun NavDestination.tab(): AppTab? = hierarchy.firstNotNullOfOrNull { d -> AppTab.entries.firstOrNull { d.hasRoute(it.graphClass) } }

/** Reselecting a tab at its root asks that landing page to scroll to the top (D12). */
internal val LocalTabReselect = compositionLocalOf<SharedFlow<AppTab>> { MutableSharedFlow() }

/** Scrolls [state] to the top whenever [tab] is reselected at its root (D12). */
@Composable
internal fun ScrollToTopOnReselect(tab: AppTab, state: LazyListState) {
    val reselects = LocalTabReselect.current
    LaunchedEffect(reselects, tab, state) { reselects.collect { if (it == tab) state.animateScrollToItem(0) } }
}

/** The saved part of the navigator: the shared model's state plus the route key of each back-stack entry the navigator created. */
internal data class TrailSnapshot(val state: TabNavigationState = TabNavigationState.initial(), val keys: Map<String, String> = emptyMap())

/**
 * The Android host of the approved tab navigation (Phase 5D.5.6C1, plan §4–5 and §14): Navigation's own multiple back stacks hold the five
 * stacks (one nested graph per tab; a tab switch pops the current graph with `saveState` and restores the target with `restoreState`), while
 * core [TabNavigation] decides every step — canonical ownership, cross-tab hops, Back, tab switches and reselection. After each change the
 * NavController's real stack is synced back into the model ([TabNavigation.sync]), so a pop performed anywhere can never leave a stale hop.
 * All navigation goes through [open], [back], [selectTab] and [reset]; modal and global screens sit on top of the current tab's stack.
 */
internal class TabNavigator(
    private val nav: NavHostController,
    private val snapshot: MutableState<TrailSnapshot>,
    /** 5D.5.6C4: the Portfolio landing's selected nested tab (saved with the trail; reset on sign-out). */
    private val section: MutableState<org.example.stocksteps.navigation.PortfolioSection> =
        androidx.compose.runtime.mutableStateOf(org.example.stocksteps.navigation.PortfolioSections.DEFAULT)
) {
    val portfolioSection: org.example.stocksteps.navigation.PortfolioSection get() = section.value

    /** Phase 8E: the motion of the latest navigation step (core `NavigationMotion`), read by the NavHost transitions when they start. */
    var motion: org.example.stocksteps.navigation.NavMotion = org.example.stocksteps.navigation.NavMotion.NONE
        private set

    /** A tap on the My Portfolio | Practice nested tabs: the content switches in place; no navigation happens. */
    fun selectPortfolioSection(value: org.example.stocksteps.navigation.PortfolioSection) { section.value = value }

    /** "Open Practice" from anywhere: Practice is a section of the Portfolio landing page, so Portfolio shows its root with Practice selected
     *  (a tab switch, rule 6 — no duplicate Practice page, no hop). */
    private fun showPortfolioSection(value: org.example.stocksteps.navigation.PortfolioSection) {
        motion = org.example.stocksteps.navigation.NavigationMotion.selectTab()
        dismissModals()
        if (currentTab() != AppTab.PORTFOLIO) selectTab(AppTab.PORTFOLIO)
        nav.popBackStack(AppTab.PORTFOLIO.rootRoute(), inclusive = false)
        sync()
        section.value = value
    }
    val state: TabNavigationState get() = snapshot.value.state
    private val reselectEvents = MutableSharedFlow<AppTab>(extraBufferCapacity = 1)
    val reselects: SharedFlow<AppTab> = reselectEvents

    private fun update(state: TabNavigationState, keys: Map<String, String> = snapshot.value.keys) { snapshot.value = TrailSnapshot(state, keys) }
    private val stack: List<NavBackStackEntry> get() = nav.currentBackStack.value

    /** The tab whose graph is on the back stack (one at a time), or null before any tab is shown (signed-out start). */
    fun currentTab(): AppTab? = stack.firstNotNullOfOrNull { it.destination.tab() }

    private fun key(route: Any): String = route.toString()

    private fun entries(tab: AppTab): List<NavEntry> = stack.mapNotNull { e ->
        val screen = screenIdOf(e.destination)?.takeIf { e.destination.tab() == tab } ?: return@mapNotNull null
        NavEntry(screen, if (screen.kind == ScreenKind.TAB_ROOT) null else snapshot.value.keys[e.id] ?: e.id)
    }

    /** Reads the host's real stack of the visible tab into the model (drops hops whose entries are gone). */
    fun sync() {
        val tab = currentTab() ?: return
        val next = TabNavigation.sync(state, tab, entries(tab))
        if (next != state) update(next)
    }

    /** Opens a registered route as the policy says: modal/global on top of the current screen, a landing page as a tab switch, a tab screen on
     *  its canonical tab's stack (switching tabs and recording a hop when it isn't the visible one). */
    fun open(route: Any) {
        if (route is org.example.stocksteps.presentation.practice.PracticeRoute) { showPortfolioSection(org.example.stocksteps.navigation.PortfolioSection.PRACTICE); return }
        val screen = screenIdOf(route) ?: error("Route $route is not registered in AppRouteScreens")
        // A Portfolio child selects its section, so Back from it shows the matching nested tab (5D.5.6C4).
        org.example.stocksteps.navigation.PortfolioSections.sectionFor(screen)?.let { section.value = it }
        when (screen.kind) {
            ScreenKind.MODAL, ScreenKind.GLOBAL -> { motion = org.example.stocksteps.navigation.NavigationMotion.open(screen.kind); nav.navigate(route) { launchSingleTop = true } }
            ScreenKind.TAB_ROOT -> selectTab(screen.tab!!)
            ScreenKind.TAB_SCREEN -> {
                motion = org.example.stocksteps.navigation.NavigationMotion.open(screen.kind)
                dismissModals()
                if (currentTab() == null) { nav.navigate(route); return } // no tab shown yet (signed-out start)
                sync()
                val tab = screen.tab!!
                val next = TabNavigation.open(state, screen, key(route))
                if (next.selected != state.selected || currentTab() != tab) switchGraph(tab)
                val pushed = next.stack(tab).size > state.stack(tab).size
                if (pushed) nav.navigate(route)
                val keys = if (pushed) nav.currentBackStackEntry?.let { snapshot.value.keys + (it.id to key(route)) } ?: snapshot.value.keys else snapshot.value.keys
                update(next, keys)
                sync()
            }
        }
    }

    /** Whether [back] has something to do (the system's own Back then closes the app only at the Home root). */
    fun canGoBack(): Boolean {
        val top = nav.currentBackStackEntry ?: return false
        if (top.destination.tab() == null) return nav.previousBackStackEntry != null
        val tab = top.destination.tab()!!
        return tab != AppTab.HOME || entries(tab).size > 1 || state.hops.isNotEmpty()
    }

    /** System Back and every in-app Back: closes a modal, pops the visible stack, ends a hop by returning to its origin tab, and at a non-Home
     *  root returns to Home (plan §5). Returns false when there is nothing to go back to. */
    fun back(): Boolean {
        val top = nav.currentBackStackEntry ?: return false
        if (top.destination.tab() == null) {
            motion = org.example.stocksteps.navigation.NavigationMotion.back(ScreenKind.GLOBAL)
            return nav.previousBackStackEntry != null && nav.popBackStack()
        }
        sync()
        motion = org.example.stocksteps.navigation.NavigationMotion.back(ScreenKind.TAB_SCREEN)
        val tab = state.selected
        val next = TabNavigation.back(state)
        when {
            next == state -> return if (tab != AppTab.HOME) { selectTab(AppTab.HOME); true } else false
            next.selected != tab -> {
                if (next.stack(tab).size < state.stack(tab).size) nav.popBackStack()
                update(next)
                switchGraph(next.selected)
            }
            else -> { nav.popBackStack(); update(next) }
        }
        sync()
        return true
    }

    /** A tab tap or a programmatic jump: restores that tab's stack and clears the trail (D2); the visible tab is reselected (D3/D12). */
    fun selectTab(tab: AppTab) {
        motion = org.example.stocksteps.navigation.NavigationMotion.selectTab()
        dismissModals()
        if (currentTab() == tab) { reselect(); return }
        sync()
        update(TabNavigation.selectTab(state, tab))
        switchGraph(tab)
        sync()
    }

    private fun reselect() {
        sync()
        val tab = state.selected
        val (next, effect) = TabNavigation.reselect(state)
        if (effect == ReselectEffect.POP_TO_ROOT) {
            motion = org.example.stocksteps.navigation.NavigationMotion.reselectPopToRoot()
            nav.popBackStack(tab.rootRoute(), inclusive = false)
        } else reselectEvents.tryEmit(tab)
        update(next)
        sync()
    }

    /** Sign-out or an ended session: forget every saved tab stack and the trail (I11); the caller then shows sign-in. */
    fun reset() {
        motion = org.example.stocksteps.navigation.NavigationMotion.reset()
        AppTab.entries.forEach { nav.clearBackStack(it.graph()) }
        update(TabNavigation.reset(), emptyMap())
        section.value = org.example.stocksteps.navigation.PortfolioSections.DEFAULT
    }

    /** Closes modal and global screens above the visible tab's stack (rule 5) before a tab destination opens. */
    private fun dismissModals() {
        if (currentTab() == null) return
        while (nav.currentBackStackEntry?.destination?.tab() == null && nav.previousBackStackEntry != null) if (!nav.popBackStack()) break
    }

    private fun switchGraph(tab: AppTab) {
        val from = currentTab()
        if (from == tab) return
        nav.navigate(tab.graph()) {
            if (from != null) popUpTo(from.graph()) { inclusive = true; saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    companion object {
        /** Saves the trail with the NavController's own state (activity recreation and process death), as a list of strings. */
        val SnapshotSaver: Saver<MutableState<TrailSnapshot>, Any> = Saver(
            save = { ArrayList(encodeTrail(it.value)) },
            restore = { saved -> androidx.compose.runtime.mutableStateOf(runCatching { @Suppress("UNCHECKED_CAST") decodeTrail(saved as List<String>) }.getOrNull() ?: TrailSnapshot()) }
        )
    }
}

private const val SEP = "\u001F"

/** "S|selected", "E|tab|screen|key", "H|origin|tab|depth|reused", "K|entryId|key" — one line per item (keys never contain the separator). */
internal fun encodeTrail(s: TrailSnapshot): List<String> = buildList {
    add(listOf("S", s.state.selected.name).joinToString(SEP))
    s.state.stacks.forEach { (tab, list) -> list.forEach { add(listOf("E", tab.name, it.screen.name, it.key ?: "").joinToString(SEP)) } }
    s.state.hops.forEach { add(listOf("H", it.origin.name, it.tab.name, it.depth.toString(), it.reused.toString()).joinToString(SEP)) }
    s.keys.forEach { (id, key) -> add(listOf("K", id, key).joinToString(SEP)) }
}

internal fun decodeTrail(lines: List<String>): TrailSnapshot {
    val parts = lines.map { it.split(SEP) }
    val stacks = AppTab.entries.associateWith { tab ->
        parts.filter { it[0] == "E" && it[1] == tab.name }.map { NavEntry(ScreenId.valueOf(it[2]), it[3].ifEmpty { null }) }
            .ifEmpty { TabNavigationState.initial().stack(tab) }
    }
    val selected = AppTab.valueOf(parts.first { it[0] == "S" }[1])
    val hops = parts.filter { it[0] == "H" }.map { Hop(AppTab.valueOf(it[1]), AppTab.valueOf(it[2]), it[3].toInt(), it[4].toBoolean()) }
    val keys = parts.filter { it[0] == "K" }.associate { it[1] to it[2] }
    return TrailSnapshot(TabNavigationState(stacks, selected, hops), keys)
}

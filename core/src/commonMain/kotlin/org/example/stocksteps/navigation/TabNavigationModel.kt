package org.example.stocksteps.navigation

/** One entry on a tab's stack: the screen and its argument key (a symbol, an id), so equal entries can be recognised. */
data class NavEntry(val screen: ScreenId, val key: String? = null)

/**
 * A cross-tab hop (plan §14): the entry at `stacks[tab][depth - 1]` was opened from [origin]. [reused] marks a hop onto an identical entry that
 * was already on top of [tab]'s stack: Back then returns to [origin] without popping it, so [tab]'s stack is never shortened by a cross-tab open.
 */
data class Hop(val origin: AppTab, val tab: AppTab, val depth: Int, val reused: Boolean = false)

/** The navigation state of the five tabs: their stacks (root first), the visible tab and the cross-tab trail (LIFO). */
data class TabNavigationState(val stacks: Map<AppTab, List<NavEntry>>, val selected: AppTab, val hops: List<Hop>) {
    fun stack(tab: AppTab): List<NavEntry> = stacks.getValue(tab)
    val top: NavEntry get() = stack(selected).last()

    companion object {
        fun initial(selected: AppTab = AppTab.HOME) =
            TabNavigationState(AppTab.entries.associateWith { listOf(NavEntry(NavigationPolicy.root(it))) }, selected, emptyList())
    }
}

/** What reselecting the visible tab does (D3, D12). */
enum class ReselectEffect { POP_TO_ROOT, SCROLL_TO_TOP }

/**
 * Pure reference model of the approved tab navigation (5D.5.6A §4 and §14), the contract the Android and iOS hosts implement in Phase C.
 * Phase 5D.5.6B ships it with its invariant tests only; no host uses it yet. Invariant I1: an operation changes at most the stack of the tab it
 * acts on — a cross-tab open only appends to (or reuses the top of) the canonical tab, and never touches the origin tab.
 */
object TabNavigation {
    /** Opens [screen] from the visible tab (rule 1–2). Modal and global screens leave the state unchanged (the host presents them, I10). */
    fun open(state: TabNavigationState, screen: ScreenId, key: String? = null): TabNavigationState {
        val tab = screen.tab ?: return state
        if (screen.kind == ScreenKind.TAB_ROOT) return selectTab(state, tab) // a jump to a tab's landing page is a tab switch (rule 6)
        val entry = NavEntry(screen, key)
        val stack = state.stack(tab)
        val reused = stack.last() == entry
        val next = if (reused) stack else stack + entry
        if (tab == state.selected) return state.copy(stacks = state.stacks + (tab to next))
        return TabNavigationState(state.stacks + (tab to next), tab, state.hops + Hop(state.selected, tab, next.size, reused))
    }

    /** Back (Android system Back, iOS back button or swipe): pops the visible stack; the end of the latest hop returns to its origin. */
    fun back(state: TabNavigationState): TabNavigationState {
        val stack = state.stack(state.selected)
        val hop = state.hops.lastOrNull()?.takeIf { it.tab == state.selected && it.depth == stack.size }
        if (hop != null) {
            val kept = if (hop.reused) stack else stack.dropLast(1)
            return TabNavigationState(state.stacks + (state.selected to kept), hop.origin, state.hops.dropLast(1))
        }
        if (stack.size <= 1) return state // at a root the platform decides (Android: Home, then exit)
        val popped = stack.dropLast(1)
        return state.copy(stacks = state.stacks + (state.selected to popped), hops = state.hops.filterNot { it.tab == state.selected && it.depth > popped.size })
    }

    /** A tab switch by the user or a programmatic jump: restores that tab's stack untouched and clears the trail (D2). Same tab = [reselect]. */
    fun selectTab(state: TabNavigationState, tab: AppTab): TabNavigationState =
        if (tab == state.selected) reselect(state).first else state.copy(selected = tab, hops = emptyList())

    /** Tapping the visible tab: pop to its root, or at the root scroll to the top; either way the trail is cleared (D3). */
    fun reselect(state: TabNavigationState): Pair<TabNavigationState, ReselectEffect> {
        val stack = state.stack(state.selected)
        return if (stack.size > 1) state.copy(stacks = state.stacks + (state.selected to stack.take(1)), hops = emptyList()) to ReselectEffect.POP_TO_ROOT
        else state.copy(hops = emptyList()) to ReselectEffect.SCROLL_TO_TOP
    }

    /** Sign-out or an ended session: every stack back to its root, Home selected, no trail (I11). */
    fun reset(): TabNavigationState = TabNavigationState.initial()
}

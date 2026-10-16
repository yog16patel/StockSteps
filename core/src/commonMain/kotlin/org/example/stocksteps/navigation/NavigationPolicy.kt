package org.example.stocksteps.navigation

/** The five primary tabs (Phase 5D.5.6). Never add a sixth. */
enum class AppTab(val label: String) {
    HOME("Home"), MARKETS("Markets"), PORTFOLIO("Portfolio"), WATCHLIST("Watchlist"), LEARN("Learn")
}

/**
 * How a screen is presented (5D.5.6A plan §2):
 * - [TAB_ROOT]: a tab's landing page;
 * - [TAB_SCREEN]: pushed onto its canonical tab's stack;
 * - [MODAL]: a task presented over the current tab (search, pickers, forms);
 * - [GLOBAL]: app-wide and outside the tabs (Settings, Notifications, sign-in, app lock).
 * MODAL and GLOBAL screens never change the selected tab and never join a tab's stack.
 */
enum class ScreenKind { TAB_ROOT, TAB_SCREEN, MODAL, GLOBAL }

/** The top bar a screen uses (§7): the shared landing bar, a back bar, a modal bar (Close/Done), or none. */
enum class TopBarStyle { LANDING, BACK, MODAL, NONE }

/**
 * Stable identity of every audited screen (5D.5.6A §2, one entry per row). Platforms map their own routes and destinations to these ids; titles
 * never decide ownership. [tab] is the canonical parent tab: set exactly for tab screens, null for modal and global ones (owner decisions D1–D12).
 */
enum class ScreenId(val kind: ScreenKind, val tab: AppTab?) {
    // Home
    HOME(ScreenKind.TAB_ROOT, AppTab.HOME),
    DAILY_BRIEF(ScreenKind.TAB_SCREEN, AppTab.HOME),
    DAILY_BRIEF_HISTORY(ScreenKind.TAB_SCREEN, AppTab.HOME),
    // Markets (company pages, discovery, comparison and earnings)
    MARKETS(ScreenKind.TAB_ROOT, AppTab.MARKETS),
    COMPANY_DETAILS(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    COMPANY_FINANCIALS(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    COMPANY_VALUATION(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    COMPANY_NEWS(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    NEWS_INSIGHT(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    STOCK_MOVEMENT(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    DISCOVER(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    COMPARE(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    COMPARISON_RESEARCH(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    COMPARISON_AI(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    EARNINGS_CALENDAR(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    EARNINGS_EVENT(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    EARNINGS_RESULTS(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    EARNINGS_DETAILS(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    EARNINGS_DIGEST(ScreenKind.TAB_SCREEN, AppTab.MARKETS),
    // Portfolio (including the simulated Practice portfolio, a separate experience)
    PORTFOLIO(ScreenKind.TAB_ROOT, AppTab.PORTFOLIO),
    HOLDING_DETAILS(ScreenKind.TAB_SCREEN, AppTab.PORTFOLIO),
    PORTFOLIO_TRANSACTIONS(ScreenKind.TAB_SCREEN, AppTab.PORTFOLIO),
    PORTFOLIO_TRANSACTION(ScreenKind.TAB_SCREEN, AppTab.PORTFOLIO),
    PORTFOLIO_INSIGHTS(ScreenKind.TAB_SCREEN, AppTab.PORTFOLIO),
    PRACTICE(ScreenKind.TAB_SCREEN, AppTab.PORTFOLIO),
    PRACTICE_ORDER(ScreenKind.TAB_SCREEN, AppTab.PORTFOLIO),
    // Watchlist
    WATCHLIST(ScreenKind.TAB_ROOT, AppTab.WATCHLIST),
    STOCK_ALERTS(ScreenKind.TAB_SCREEN, AppTab.WATCHLIST),
    EARNINGS_REMINDERS(ScreenKind.TAB_SCREEN, AppTab.WATCHLIST),
    // Learn
    LEARN(ScreenKind.TAB_ROOT, AppTab.LEARN),
    LEARN_TOPICS(ScreenKind.TAB_SCREEN, AppTab.LEARN),
    LEARN_TERM(ScreenKind.TAB_SCREEN, AppTab.LEARN),
    GUIDED_RESEARCH(ScreenKind.TAB_SCREEN, AppTab.LEARN),
    // Modal tasks over the current tab
    COMPANY_SEARCH(ScreenKind.MODAL, null),
    TRANSACTION_FORM(ScreenKind.MODAL, null),
    PORTFOLIO_COMPANY_PICKER(ScreenKind.MODAL, null),
    PRACTICE_COMPANY_PICKER(ScreenKind.MODAL, null),
    // Global, outside the tabs
    SETTINGS(ScreenKind.GLOBAL, null),
    EARNINGS_DIGEST_SETTINGS(ScreenKind.GLOBAL, null),
    /** 5D.5.6C1: Earnings reminders opened from Settings stay inside the Settings flow (plan §2 row 30); from a tab they are Watchlist's. */
    SETTINGS_EARNINGS_REMINDERS(ScreenKind.GLOBAL, null),
    NOTIFICATIONS(ScreenKind.GLOBAL, null),
    AUTH(ScreenKind.GLOBAL, null),
    APP_LOCK(ScreenKind.GLOBAL, null)
}

/**
 * The shared navigation policy (Phase 5D.5.6B): canonical ownership, presentation and chrome for every [ScreenId], decided once for Android and
 * iOS. Phase B uses it for the landing bars only; the hosts adopt the stack rules in Phase C (bottom-bar visibility here is the approved target,
 * not today's per-platform behaviour).
 */
object NavigationPolicy {
    /** The tab a screen belongs to, whatever it was opened from; null for modal and global screens. */
    fun parentTab(screen: ScreenId): AppTab? = screen.tab

    /** The landing page of [tab]. */
    fun root(tab: AppTab): ScreenId = ScreenId.entries.single { it.kind == ScreenKind.TAB_ROOT && it.tab == tab }

    fun isTabScreen(screen: ScreenId): Boolean = screen.kind == ScreenKind.TAB_ROOT || screen.kind == ScreenKind.TAB_SCREEN

    /** D4 / §6: the bottom navigation shows on every tab screen and never on modal or global ones. */
    fun showsBottomNavigation(screen: ScreenId): Boolean = isTabScreen(screen)

    /** §7: landing pages use the shared landing bar; pushed screens a back bar; modals and global screens a Close/Done bar; the app lock none. */
    fun topBar(screen: ScreenId): TopBarStyle = when {
        screen == ScreenId.APP_LOCK -> TopBarStyle.NONE
        screen.kind == ScreenKind.TAB_ROOT -> TopBarStyle.LANDING
        screen.kind == ScreenKind.TAB_SCREEN -> TopBarStyle.BACK
        else -> TopBarStyle.MODAL
    }

    /** Opening [screen] while [current] is selected crosses tabs (Phase C records a hop); modal and global screens never do. */
    fun crossesTabs(current: AppTab, screen: ScreenId): Boolean = screen.tab != null && screen.tab != current
}

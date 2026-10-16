package org.example.stocksteps.presentation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import org.example.stocksteps.navigation.ScreenId
import kotlin.reflect.KClass
import org.example.stocksteps.presentation.brief.DailyBriefHistoryRoute
import org.example.stocksteps.presentation.brief.DailyBriefRoute
import org.example.stocksteps.presentation.companydetails.CompanyDetailsRoute
import org.example.stocksteps.presentation.companydetails.CompanyFinancialsRoute
import org.example.stocksteps.presentation.companydetails.CompanyNewsRoute
import org.example.stocksteps.presentation.companydetails.CompanyValuationRoute
import org.example.stocksteps.presentation.companydetails.NewsInsightRoute
import org.example.stocksteps.presentation.companydetails.StockMovementRoute
import org.example.stocksteps.presentation.discovery.DiscoveryRoute
import org.example.stocksteps.presentation.earnings.EarningsCalendarRoute
import org.example.stocksteps.presentation.earnings.EarningsDetailsRoute
import org.example.stocksteps.presentation.earnings.EarningsDigestRoute
import org.example.stocksteps.presentation.earnings.EarningsDigestSettingsRoute
import org.example.stocksteps.presentation.earnings.EarningsEventRoute
import org.example.stocksteps.presentation.earnings.EarningsRemindersRoute
import org.example.stocksteps.presentation.earnings.EarningsResultsRoute
import org.example.stocksteps.presentation.earnings.SettingsRemindersRoute
import org.example.stocksteps.presentation.learn.LearnRoute
import org.example.stocksteps.presentation.learn.LearnTermRoute
import org.example.stocksteps.presentation.learn.LearnTopicsRoute
import org.example.stocksteps.presentation.markets.MarketsRoute
import org.example.stocksteps.presentation.portfolio.HoldingDetailsRoute
import org.example.stocksteps.presentation.portfolio.PortfolioEntryRoute
import org.example.stocksteps.presentation.portfolio.PortfolioInsightsRoute
import org.example.stocksteps.presentation.portfolio.PortfolioRoute
import org.example.stocksteps.presentation.portfolio.PortfolioSearchRoute
import org.example.stocksteps.presentation.portfolio.PortfolioTransactionRoute
import org.example.stocksteps.presentation.portfolio.PortfolioTransactionsRoute
import org.example.stocksteps.presentation.practice.PracticeOrderRoute
import org.example.stocksteps.presentation.practice.PracticeSearchRoute
import org.example.stocksteps.presentation.research.GuidedResearchRoute
import org.example.stocksteps.presentation.screener.ComparisonAiRoute
import org.example.stocksteps.presentation.screener.ComparisonResearchRoute
import org.example.stocksteps.presentation.screener.ComparisonRoute
import org.example.stocksteps.presentation.screener.ScreenerRoute
import org.example.stocksteps.presentation.settings.SettingsRoute
import org.example.stocksteps.presentation.stocksearch.StockSearchRoute
import org.example.stocksteps.presentation.watchlist.AlertsRoute
import org.example.stocksteps.presentation.watchlist.NotificationsRoute
import org.example.stocksteps.presentation.watchlist.WatchListRoute
import org.example.stocksteps.presentation.account.AuthRoute

/**
 * The stable [ScreenId] of every Android route type registered in `AppNavigation` — the only Android ownership table (Phase 5D.5.6B/C1).
 * Core `NavigationPolicy` decides each id's tab, presentation and chrome; `AppNavigation` registers every route inside its policy tab's graph
 * (or at the top level for modal and global screens), so graph membership always equals the policy. `AppScreensTest` keeps it exhaustive.
 */
internal val AppRouteScreens: List<Pair<KClass<*>, ScreenId>> = listOf(
    DiscoveryRoute::class to ScreenId.HOME,
    DailyBriefRoute::class to ScreenId.DAILY_BRIEF,
    DailyBriefHistoryRoute::class to ScreenId.DAILY_BRIEF_HISTORY,
    MarketsRoute::class to ScreenId.MARKETS,
    StockSearchRoute::class to ScreenId.COMPANY_SEARCH,
    CompanyDetailsRoute::class to ScreenId.COMPANY_DETAILS,
    CompanyFinancialsRoute::class to ScreenId.COMPANY_FINANCIALS,
    CompanyValuationRoute::class to ScreenId.COMPANY_VALUATION,
    CompanyNewsRoute::class to ScreenId.COMPANY_NEWS,
    NewsInsightRoute::class to ScreenId.NEWS_INSIGHT,
    StockMovementRoute::class to ScreenId.STOCK_MOVEMENT,
    ScreenerRoute::class to ScreenId.DISCOVER,
    ComparisonRoute::class to ScreenId.COMPARE,
    ComparisonResearchRoute::class to ScreenId.COMPARISON_RESEARCH,
    ComparisonAiRoute::class to ScreenId.COMPARISON_AI,
    EarningsCalendarRoute::class to ScreenId.EARNINGS_CALENDAR,
    EarningsEventRoute::class to ScreenId.EARNINGS_EVENT,
    EarningsResultsRoute::class to ScreenId.EARNINGS_RESULTS,
    EarningsDetailsRoute::class to ScreenId.EARNINGS_DETAILS,
    EarningsDigestRoute::class to ScreenId.EARNINGS_DIGEST,
    PortfolioRoute::class to ScreenId.PORTFOLIO,
    HoldingDetailsRoute::class to ScreenId.HOLDING_DETAILS,
    PortfolioTransactionsRoute::class to ScreenId.PORTFOLIO_TRANSACTIONS,
    PortfolioTransactionRoute::class to ScreenId.PORTFOLIO_TRANSACTION,
    PortfolioInsightsRoute::class to ScreenId.PORTFOLIO_INSIGHTS,
    PracticeOrderRoute::class to ScreenId.PRACTICE_ORDER,
    WatchListRoute::class to ScreenId.WATCHLIST,
    AlertsRoute::class to ScreenId.STOCK_ALERTS,
    EarningsRemindersRoute::class to ScreenId.EARNINGS_REMINDERS,
    LearnRoute::class to ScreenId.LEARN,
    LearnTopicsRoute::class to ScreenId.LEARN_TOPICS,
    LearnTermRoute::class to ScreenId.LEARN_TERM,
    GuidedResearchRoute::class to ScreenId.GUIDED_RESEARCH,
    PortfolioEntryRoute::class to ScreenId.TRANSACTION_FORM,
    PortfolioSearchRoute::class to ScreenId.PORTFOLIO_COMPANY_PICKER,
    PracticeSearchRoute::class to ScreenId.PRACTICE_COMPANY_PICKER,
    SettingsRoute::class to ScreenId.SETTINGS,
    EarningsDigestSettingsRoute::class to ScreenId.EARNINGS_DIGEST_SETTINGS,
    SettingsRemindersRoute::class to ScreenId.SETTINGS_EARNINGS_REMINDERS,
    NotificationsRoute::class to ScreenId.NOTIFICATIONS,
    AuthRoute::class to ScreenId.AUTH
)

private val screensByClass: Map<KClass<*>, ScreenId> = AppRouteScreens.toMap()

/** The screen id of a route value (null for anything that isn't a registered route). */
internal fun screenIdOf(route: Any): ScreenId? = screensByClass[route::class]

/** The screen id of a destination in the graph (null for graphs and unknown destinations). */
internal fun screenIdOf(destination: NavDestination): ScreenId? = AppRouteScreens.firstOrNull { destination.hasRoute(it.first) }?.second

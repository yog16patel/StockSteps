package org.example.stocksteps.presentation

import org.example.stocksteps.navigation.ScreenId
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
import org.example.stocksteps.presentation.practice.PracticeRoute
import org.example.stocksteps.presentation.practice.PracticeSearchRoute
import org.example.stocksteps.presentation.research.GuidedResearchRoute
import org.example.stocksteps.presentation.screener.ComparisonAiRoute
import org.example.stocksteps.presentation.screener.ComparisonResearchRoute
import org.example.stocksteps.presentation.screener.ComparisonRoute
import org.example.stocksteps.presentation.screener.ScreenerRoute
import org.example.stocksteps.presentation.settings.SettingsRoute
import org.example.stocksteps.presentation.stocksearch.StockSearchRoute
import org.example.stocksteps.presentation.watchlist.AlertsRoute
import org.example.stocksteps.presentation.watchlist.WatchListRoute
import org.example.stocksteps.presentation.account.AuthRoute

/**
 * Phase 5D.5.6B: the stable [ScreenId] of every Android route registered in `AppNavigation` (core `NavigationPolicy` owns tabs, presentation and
 * chrome). The all-alerts history (`AlertsRoute(null)`) is Notifications; a stock's alerts are Watchlist's. Unknown values are null, and
 * `AppScreensTest` keeps the list exhaustive. Phase C reads it for canonical tab selection; Phase B hosts don't use it yet.
 */
internal fun screenIdOf(route: Any): ScreenId? = when (route) {
    is DiscoveryRoute -> ScreenId.HOME
    is DailyBriefRoute -> ScreenId.DAILY_BRIEF
    is DailyBriefHistoryRoute -> ScreenId.DAILY_BRIEF_HISTORY
    is MarketsRoute -> ScreenId.MARKETS
    is StockSearchRoute -> ScreenId.COMPANY_SEARCH
    is CompanyDetailsRoute -> ScreenId.COMPANY_DETAILS
    is CompanyFinancialsRoute -> ScreenId.COMPANY_FINANCIALS
    is CompanyValuationRoute -> ScreenId.COMPANY_VALUATION
    is CompanyNewsRoute -> ScreenId.COMPANY_NEWS
    is NewsInsightRoute -> ScreenId.NEWS_INSIGHT
    is StockMovementRoute -> ScreenId.STOCK_MOVEMENT
    is ScreenerRoute -> ScreenId.DISCOVER
    is ComparisonRoute -> ScreenId.COMPARE
    is ComparisonResearchRoute -> ScreenId.COMPARISON_RESEARCH
    is ComparisonAiRoute -> ScreenId.COMPARISON_AI
    is EarningsCalendarRoute -> ScreenId.EARNINGS_CALENDAR
    is EarningsEventRoute -> ScreenId.EARNINGS_EVENT
    is EarningsResultsRoute -> ScreenId.EARNINGS_RESULTS
    is EarningsDetailsRoute -> ScreenId.EARNINGS_DETAILS
    is EarningsDigestRoute -> ScreenId.EARNINGS_DIGEST
    is PortfolioRoute -> ScreenId.PORTFOLIO
    is HoldingDetailsRoute -> ScreenId.HOLDING_DETAILS
    is PortfolioTransactionsRoute -> ScreenId.PORTFOLIO_TRANSACTIONS
    is PortfolioTransactionRoute -> ScreenId.PORTFOLIO_TRANSACTION
    is PortfolioInsightsRoute -> ScreenId.PORTFOLIO_INSIGHTS
    is PracticeRoute -> ScreenId.PRACTICE
    is PracticeOrderRoute -> ScreenId.PRACTICE_ORDER
    is WatchListRoute -> ScreenId.WATCHLIST
    is AlertsRoute -> if (route.symbol == null) ScreenId.NOTIFICATIONS else ScreenId.STOCK_ALERTS
    is EarningsRemindersRoute -> ScreenId.EARNINGS_REMINDERS
    is LearnRoute -> ScreenId.LEARN
    is LearnTopicsRoute -> ScreenId.LEARN_TOPICS
    is LearnTermRoute -> ScreenId.LEARN_TERM
    is GuidedResearchRoute -> ScreenId.GUIDED_RESEARCH
    is PortfolioEntryRoute -> ScreenId.TRANSACTION_FORM
    is PortfolioSearchRoute -> ScreenId.PORTFOLIO_COMPANY_PICKER
    is PracticeSearchRoute -> ScreenId.PRACTICE_COMPANY_PICKER
    is SettingsRoute -> ScreenId.SETTINGS
    is EarningsDigestSettingsRoute -> ScreenId.EARNINGS_DIGEST_SETTINGS
    is AuthRoute -> ScreenId.AUTH
    else -> null
}

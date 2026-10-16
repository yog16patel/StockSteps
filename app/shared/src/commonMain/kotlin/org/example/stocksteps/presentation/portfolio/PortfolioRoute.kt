package org.example.stocksteps.presentation.portfolio

import kotlinx.serialization.Serializable

@Serializable internal data object PortfolioSearchRoute
@Serializable internal data object PortfolioRoute
@Serializable internal data class HoldingDetailsRoute(val accountId: String, val symbol: String)
@Serializable internal data class PortfolioEntryRoute(val symbol: String = "", val name: String? = null, val exchange: String? = null, val currency: String? = null)
@Serializable internal data object PortfolioInsightsRoute
/** Phase D2: Transaction history inside Portfolio (not a tab). Identity only: the holding scope and the account it was opened for. */
@Serializable internal data class PortfolioTransactionsRoute(val accountId: String? = null, val symbol: String? = null)
/** Phase D3: Transaction details. Identity only: the transaction is read from the shared presenter's state. */
@Serializable internal data class PortfolioTransactionRoute(val transactionId: String)

/**
 * Phase D3.1: the Portfolio dashboard's own destinations (5D.5.6C1: the bottom bar now follows core `NavigationPolicy`; this list stays as
 * the D3.1 record). The Add-transaction screen is not listed: it is also opened from Company details on other tabs.
 */
internal val PortfolioTabRoutes: List<kotlin.reflect.KClass<*>> = listOf(PortfolioRoute::class, PortfolioTransactionsRoute::class,
    PortfolioTransactionRoute::class, HoldingDetailsRoute::class, PortfolioInsightsRoute::class)


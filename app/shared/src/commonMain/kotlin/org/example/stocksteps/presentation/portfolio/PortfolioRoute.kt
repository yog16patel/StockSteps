package org.example.stocksteps.presentation.portfolio

import androidx.navigation.NavDestination.Companion.hasRoute
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
 * Phase D3.1: every destination that belongs to the Portfolio tab, so the bottom bar keeps Portfolio selected on them (one rule; screens don't
 * decide). The Add-transaction screen is not listed: it is also opened from Company details on other tabs.
 */
internal val PortfolioTabRoutes: List<kotlin.reflect.KClass<*>> = listOf(PortfolioRoute::class, PortfolioTransactionsRoute::class,
    PortfolioTransactionRoute::class, HoldingDetailsRoute::class, PortfolioInsightsRoute::class)

internal fun androidx.navigation.NavDestination.inPortfolioTab(): Boolean = PortfolioTabRoutes.any { hasRoute(it) }

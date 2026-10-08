package org.example.stocksteps.portfolio

import kotlinx.serialization.Serializable
import org.example.stocksteps.model.InstrumentRef

@Serializable enum class PortfolioCategory { TFSA, RRSP, FHSA, NON_REGISTERED, PERSONAL, OTHER }
@Serializable enum class PortfolioCurrency { CAD, USD }
@Serializable enum class TransactionType {
    BUY, SELL, CASH_DEPOSIT, CASH_WITHDRAWAL, DIVIDEND, DIVIDEND_REINVESTMENT,
    FEE, OPENING_POSITION, CASH_ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT, STOCK_SPLIT
}
@Serializable data class PortfolioAccount(
    val id: String,
    val name: String,
    val category: PortfolioCategory = PortfolioCategory.PERSONAL,
    val reportingCurrency: PortfolioCurrency = PortfolioCurrency.CAD,
    val archived: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
/** Decimal strings are intentional: never deserialize a financial ledger amount through Double.
 * OPENING_POSITION imports basis as of tradeDate without creating cash or earlier history.
 * Transfers with an instrument move quantity and supplied basis; cash transfers omit it.
 * STOCK_SPLIT quantity is the new/old ratio and changes shares without changing basis.
 */
@Serializable data class PortfolioTransaction(
    val id: String,
    val accountId: String,
    val type: TransactionType,
    val tradeDate: String,
    val currency: PortfolioCurrency,
    val instrument: InstrumentRef? = null,
    val quantity: String = "0",
    val unitPrice: String = "0",
    val grossAmount: String = "0",
    val fees: String = "0",
    val netAmount: String = "0",
    val settlementDate: String? = null,
    val fxRate: String? = null,
    val cashCurrency: PortfolioCurrency = currency,
    val notes: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val sequence: Long = 0
)
@Serializable data class PortfolioLedger(
    val accounts: List<PortfolioAccount> = emptyList(),
    val transactions: List<PortfolioTransaction> = emptyList(),
    val revision: Long = 0
)
@Serializable data class PortfolioHolding(
    val instrument: InstrumentRef,
    val currency: PortfolioCurrency,
    val quantity: String,
    val costBasis: String,
    val realizedGain: String,
    val dividends: String
)
@Serializable data class PortfolioPositionState(
    val holdings: List<PortfolioHolding>,
    val cash: Map<PortfolioCurrency, String>,
    val realizedGain: Map<PortfolioCurrency, String>,
    val externalContributions: Map<PortfolioCurrency, String>,
    val dividends: Map<PortfolioCurrency, String> = emptyMap()
)
/** Dated market observations. FX rates map one source unit to the reporting currency.
 * Callers must supply observations for the valuation date, never today's FX for past dates.
 */
data class PortfolioPrices(
    val date: String,
    val prices: Map<String, String>,
    val fx: Map<PortfolioCurrency, String>,
    val historicalFx: Map<String, Map<PortfolioCurrency, String>> = emptyMap()
)
@Serializable data class PortfolioValuation(
    val date: String,
    val currency: PortfolioCurrency,
    val totalValue: String?,
    val investedCost: String?,
    val unrealizedGain: String?,
    val realizedGain: String?,
    val missing: List<String> = emptyList()
)

@Serializable data class PortfolioReport(
    val accountId: String,
    val revision: Long,
    val summary: PortfolioValuation,
    val history: List<PortfolioValuation> = emptyList(),
    val range: String = "1M",
    val notice: String = "End-of-day values use dated holdings, closing prices and FX. Missing observations remain unavailable.",
    val fxAsOf: String? = null,
    val daily: PortfolioDailyPerformance? = null,
    val allocations: List<PortfolioAllocation> = emptyList(),
    val currentFx: Map<PortfolioCurrency, String> = emptyMap(),
    val currencyAllocations: List<PortfolioCurrencyAllocation> = emptyList(),
    val quoteAsOf: String? = null
)

@Serializable data class PortfolioDailyPerformance(val gain: String?, val percent: String?, val notice: String)
@Serializable data class PortfolioAllocation(val symbol: String, val currency: PortfolioCurrency, val reportingValue: String?, val percent: String?)
@Serializable data class PortfolioCurrencyAllocation(val currency: PortfolioCurrency, val reportingValue: String?, val percent: String?)
data class PortfolioAllocations(val holdings: List<PortfolioAllocation>, val currencies: List<PortfolioCurrencyAllocation>)

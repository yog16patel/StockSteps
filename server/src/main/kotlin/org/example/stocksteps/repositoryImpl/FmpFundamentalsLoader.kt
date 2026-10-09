package org.example.stocksteps.repositoryImpl

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import kotlinx.coroutines.*
import org.example.stocksteps.httpclient.apiCall
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.models.*
import org.example.stocksteps.service.*
import java.time.Instant
import java.time.LocalDate

private const val QUARTERS_FOR_VALUATION = 24

internal data class FinancialDataset<T>(val rows: List<T>, val availability: FinancialAvailability, val accessDenied: Boolean = false)

/** Internal extension of the existing FMP repository, using its client/key/cache. */
internal class FmpFundamentalsLoader(
    private val client: HttpClient,
    private val apiKey: String,
    private val cache: CompanyFinancialCache,
    private val today: () -> LocalDate,
    /** Aggregate provider-usage counters (upstream requests, cache hits/misses, errors) by dataset and feature. */
    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared
) {
    private suspend inline fun <reified T> dataset(
        endpoint: String,
        symbol: String,
        period: String? = null,
        limit: Int = 6,
        ttl: Long = FinancialCachePolicy.STATEMENTS
    ): FinancialDataset<T> {
        // Exchange-qualified symbol (TD ≠ TD.TO), dataset, period and row count: no collisions between listings or queries.
        val key = "$endpoint:$symbol:$period:$limit"
        val successKey = "success:$key"
        val feature = meter.feature()
        var upstream = false
        return cachedDataset<T>(key, successKey, endpoint, symbol, period, limit, ttl, feature) { upstream = true }.also {
            meter.record("fmp", endpoint, feature, if (upstream) "cacheMiss" else "cacheHit")
        }
    }

    private suspend inline fun <reified T> cachedDataset(key: String, successKey: String, endpoint: String, symbol: String, period: String?, limit: Int, ttl: Long,
                                                         feature: String, crossinline onUpstream: () -> Unit): FinancialDataset<T> {
        return cache.getOrLoad("cooldown:$key", FinancialCachePolicy.FAILURE, resultTtl = { result: FinancialDataset<T> ->
            if (result.accessDenied) FinancialCachePolicy.ACCESS_COOLDOWN else FinancialCachePolicy.FAILURE
        }) {
            try {
                cache.getOrLoad(successKey, ttl) {
                    onUpstream()
                    meter.record("fmp", endpoint, feature, "upstream")
                    val rows = client.apiCall<List<T>>(
                        url = "https://financialmodelingprep.com/stable/$endpoint",
                        apiKey = apiKey
                    ) {
                        parameter("symbol", symbol)
                        if (endpoint !in listOf("ratios-ttm", "key-metrics-ttm", "shares-float")) {
                            parameter("limit", limit)
                        }
                        period?.let { parameter("period", it) }
                    }
                    FinancialDataset(
                        rows,
                        if (rows.isEmpty()) FinancialAvailability.MISSING else FinancialAvailability.AVAILABLE
                    )
                }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                if (cause !is StockProviderException) throw cause
                meter.record("fmp", endpoint, feature, if (cause.upstreamStatus == 429) "rateLimited" else "error")
                FinancialDataset(
                    emptyList(),
                    FinancialAvailability.TEMPORARILY_UNAVAILABLE,
                    accessDenied = cause.upstreamStatus in listOf(402, 403)
                )
            }
        }
    }

    /**
     * Reported quarterly diluted EPS for the historical P/E series: one statement request (24
     * quarters ≈ 6 years) cached like other statements. A missing filing date is treated as
     * period end + 45 days, the latest a quarterly report is normally public.
     */
    suspend fun quarterlyEarnings(symbol: String): List<QuarterlyEarnings> {
        val data = dataset<FmpIncomeStatement>("income-statement", symbol, "quarter", QUARTERS_FOR_VALUATION)
        return data.rows.filter { it.symbol.equals(symbol, true) && it.date != null && it.period in listOf("Q1", "Q2", "Q3", "Q4") && it.date <= today().toString() }
            .distinctBy { it.date }
            .map { row ->
                QuarterlyEarnings(
                    periodEnd = row.date!!,
                    availableOn = row.acceptedDate?.take(10) ?: LocalDate.parse(row.date).plusDays(45).toString(),
                    epsDiluted = row.epsDiluted?.takeIf { it.isFinite() },
                    shares = row.weightedAverageShsOut?.takeIf { it.isFinite() && it > 0 },
                    currency = row.reportedCurrency
                )
            }
    }

    suspend fun load(symbol: String, period: String, quote: suspend () -> Pair<StockQuote?, CompanyProfile?>): CompanyFundamentals = supervisorScope {
        val annual = async { dataset<FmpIncomeStatement>("income-statement", symbol, "annual") }
        val income = if (period == "annual") annual else async { dataset<FmpIncomeStatement>("income-statement", symbol, "quarter", 8) }
        // Same depth as income/cash flow so every history row can carry its balance sheet.
        val balance = async { dataset<FmpBalanceSheetStatement>("balance-sheet-statement", symbol, period, if (period == "quarter") 8 else 6) }
        val cash = async { dataset<FmpCashFlowStatement>("cash-flow-statement", symbol, period, if (period == "quarter") 8 else 6) }
        val ratios = async { dataset<FmpRatiosTtm>("ratios-ttm", symbol, ttl = FinancialCachePolicy.RATIOS) }
        val keys = async { dataset<FmpKeyMetricsTtm>("key-metrics-ttm", symbol, ttl = FinancialCachePolicy.RATIOS) }
        val history = async { dataset<FmpRatios>("ratios", symbol, "annual") }
        val trailingIncome = async { dataset<FmpIncomeStatement>("income-statement-ttm", symbol, limit = 1, ttl = FinancialCachePolicy.TTM_STATEMENTS) }
        val trailingCash = async { dataset<FmpCashFlowStatement>("cash-flow-statement-ttm", symbol, limit = 1, ttl = FinancialCachePolicy.TTM_STATEMENTS) }
        val estimates = async { dataset<FmpAnalystEstimate>("analyst-estimates", symbol, "annual", 10, FinancialCachePolicy.ESTIMATES) }
        val dividends = async { dataset<FmpDividend>("dividends", symbol, limit = 100) }
        val shares = async { dataset<FmpSharesFloat>("shares-float", symbol, limit = 1) }
        val price = async {
            try { quote() } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                null
            }
        }
        val datasets = linkedMapOf<String, FinancialAvailability>()
        fun <T> checked(name: String, data: FinancialDataset<T>, ticker: (T) -> String): List<T> {
            val valid = data.rows.all { ticker(it).equals(symbol, true) }
            datasets[name] = if (valid) data.availability else FinancialAvailability.INVALID_VALUE
            return if (valid) data.rows else emptyList()
        }
        val priceContext = price.await()
        val result = FmpFundamentalsMapper.map(
            symbol = symbol,
            period = period,
            income = checked("income", income.await()) { it.symbol },
            annualIncome = checked("annualIncome", annual.await()) { it.symbol },
            balance = checked("balance", balance.await()) { it.symbol },
            cash = checked("cashFlow", cash.await()) { it.symbol },
            ratios = checked("ratiosTtm", ratios.await()) { it.symbol }.firstOrNull(),
            keys = checked("keyMetricsTtm", keys.await()) { it.symbol }.firstOrNull(),
            history = checked("historicalRatios", history.await()) { it.symbol },
            trailingIncome = checked("incomeTtm", trailingIncome.await()) { it.symbol }.firstOrNull(),
            trailingCash = checked("cashFlowTtm", trailingCash.await()) { it.symbol }.firstOrNull(),
            estimates = checked("estimates", estimates.await()) { it.symbol },
            dividends = checked("dividends", dividends.await()) { it.symbol },
            shares = checked("shares", shares.await()) { it.symbol }.firstOrNull(),
            price = priceContext?.first?.price,
            quoteCurrency = priceContext?.second?.currency,
            today = today()
        )
        fun annotate(metrics: Map<String, FinancialFact>, primary: String, overrides: Map<String, String> = emptyMap()): Map<String, FinancialFact> =
            metrics.mapValues { (id, fact) ->
                val status = datasets[overrides[id] ?: primary]
                if (fact.availability == FinancialAvailability.MISSING && status != null && status != FinancialAvailability.AVAILABLE)
                    fact.copy(availability = status) else fact
            }
        result.copy(
            financials = result.financials.copy(
                growth = annotate(result.financials.growth, "income", mapOf("revenueCagr3" to "annualIncome", "revenueCagr5" to "annualIncome", "epsCagr3" to "annualIncome", "epsCagr5" to "annualIncome")),
                profitability = annotate(result.financials.profitability, "ratiosTtm", mapOf("roe" to "keyMetricsTtm", "roa" to "keyMetricsTtm", "roic" to "keyMetricsTtm")),
                financialHealth = annotate(result.financials.financialHealth, "balance", mapOf("debtEquity" to "ratiosTtm", "currentRatio" to "ratiosTtm", "quickRatio" to "ratiosTtm", "interestCoverage" to "ratiosTtm", "netDebtEbitda" to "keyMetricsTtm")),
                cashFlow = annotate(result.financials.cashFlow, "cashFlow"),
                shareholderReturns = annotate(result.financials.shareholderReturns, "ratiosTtm", mapOf("dividendGrowth" to "dividends", "shares" to "shares", "buybacks" to "cashFlow"))
            ),
            valuation = result.valuation.copy(metrics = annotate(result.valuation.metrics, "ratiosTtm", mapOf("forwardPe" to "estimates", "enterpriseValue" to "keyMetricsTtm", "evEbitda" to "keyMetricsTtm"))),
            datasets = datasets,
            retrievedAt = Instant.now().toString()
        )
    }
}

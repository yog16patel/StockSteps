package org.example.stocksteps.repositoryImpl

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import kotlinx.coroutines.*
import org.example.stocksteps.httpclient.apiCall
import org.example.stocksteps.model.*
import org.example.stocksteps.repository.Statement
import org.example.stocksteps.repository.StatementHistory
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.repository.models.*
import org.example.stocksteps.service.*
import org.example.stocksteps.earnings.DataFreshness
import java.time.Instant
import java.time.LocalDate

private const val QUARTERS_FOR_VALUATION = 24

/**
 * One provider dataset. [retrievedAt] is when it was fetched from the provider (unchanged by cache hits); [stale] marks
 * an expired copy served because the provider failed (Phase 3E).
 */
internal data class FinancialDataset<T>(
    val rows: List<T>,
    val availability: FinancialAvailability,
    val accessDenied: Boolean = false,
    val retrievedAt: Instant? = null,
    val stale: Boolean = false
)

/** Retrieval times and stale datasets of one assembled result (Phase 3E). */
internal class Provenance(private val now: Instant) {
    private val times = mutableListOf<Instant>()
    val stale = mutableListOf<String>()
    var failed = 0; var total = 0
    fun add(name: String, data: FinancialDataset<*>?) {
        if (data == null) return
        total++
        data.retrievedAt?.let { times += it }
        if (data.stale) stale += name
        if (data.availability == FinancialAvailability.TEMPORARILY_UNAVAILABLE) failed++
    }
    /** The oldest provider retrieval behind the result (what "as of" means), or now when nothing was retrieved. */
    val retrievedAt: Instant get() = times.minOrNull() ?: now
    val freshness: DataFreshness get() = when {
        stale.isNotEmpty() -> DataFreshness.STALE
        total > 0 && failed == total -> DataFreshness.UNAVAILABLE
        times.isNotEmpty() && times.min().isAfter(now.minusSeconds(5)) -> DataFreshness.FRESH
        else -> DataFreshness.CACHED
    }
}

/** Internal extension of the existing FMP repository, using its client/key/cache. */
internal class FmpFundamentalsLoader(
    private val client: HttpClient,
    private val apiKey: String,
    private val cache: CompanyFinancialCache,
    private val today: () -> LocalDate,
    /** Aggregate provider-usage counters (upstream requests, cache hits/misses, errors) by dataset and feature. */
    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared,
    /** Phase 3C: TTM ratio / key-metric lifetimes by the listing's market session. */
    private val freshness: MarketFreshnessPolicy = MarketFreshnessPolicy.FIXED,
    /** Phase 3D: shorter statement lifetimes for a symbol that just reported, until its new period appears. */
    private val signals: EarningsStatementSignals? = null,
    /** Wall clock for retrieval times shown to users. */
    private val clock: () -> Instant = Instant::now
) {
    init {
        // A newly reported quarter invalidates that symbol's cached statements once (annual ones only for Q4).
        signals?.onNewReport { symbol, report ->
            buildList {
                add("income-statement:$symbol:quarter:$QUARTERS_FOR_VALUATION"); add("balance-sheet-statement:$symbol:quarter:8"); add("cash-flow-statement:$symbol:quarter:8")
                add("income-statement-ttm:$symbol:null:1"); add("cash-flow-statement-ttm:$symbol:null:1")
                if (report.fiscalQuarter == 4) listOf("income-statement", "balance-sheet-statement", "cash-flow-statement").forEach { add("$it:$symbol:annual:6") }
            }.forEach { cache.invalidate("success:$it") }
        }
    }

    /** Reported statements whose lifetime follows earnings evidence: (frequency for the signal, newest period end). */
    private fun statementPeriod(endpoint: String, period: String?): String? = when (endpoint) {
        "income-statement", "balance-sheet-statement", "cash-flow-statement" -> period
        "income-statement-ttm", "cash-flow-statement-ttm" -> "ttm"
        else -> null
    }

    /**
     * Phase 3E: how old an expired copy may be when the provider fails (0 = never served stale). Reported statements and
     * reference data qualify; price-sensitive TTM ratios/key metrics never do (nor quotes, elsewhere).
     */
    private fun staleLimit(endpoint: String): Long = when (endpoint) {
        "income-statement", "balance-sheet-statement", "cash-flow-statement", "income-statement-ttm", "cash-flow-statement-ttm",
        "dividends", "shares-float", "ratios" -> FinancialCachePolicy.STALE_STATEMENTS
        "analyst-estimates" -> FinancialCachePolicy.STALE_ESTIMATES
        else -> 0L
    }

    private suspend inline fun <reified T> dataset(
        endpoint: String,
        symbol: String,
        period: String? = null,
        limit: Int = 6,
        ttl: Long = FinancialCachePolicy.STATEMENTS,
        noinline periodEnd: ((T) -> String?)? = null
    ): FinancialDataset<T> {
        // Exchange-qualified symbol (TD ≠ TD.TO), dataset, period and row count: no collisions between listings or queries.
        val key = "$endpoint:$symbol:$period:$limit"
        val successKey = "success:$key"
        val feature = meter.feature()
        var upstream = false
        val kind = statementPeriod(endpoint, period)
        val ttlOf: ((FinancialDataset<T>) -> Long)? = if (signals == null || kind == null || periodEnd == null) null else { data ->
            signals.statementTtl(symbol, kind, data.rows.mapNotNull(periodEnd).maxOrNull(), ttl)
        }
        return cachedDataset<T>(key, successKey, endpoint, symbol, period, limit, ttl, feature, ttlOf) {
            upstream = true
            if (kind != null && signals?.awaiting(symbol, kind) != null) signals.countRefresh()
        }.also {
            meter.record("fmp", endpoint, feature, if (upstream) "cacheMiss" else "cacheHit")
        }
    }

    private suspend inline fun <reified T> cachedDataset(key: String, successKey: String, endpoint: String, symbol: String, period: String?, limit: Int, ttl: Long,
                                                         feature: String, noinline ttlOf: ((FinancialDataset<T>) -> Long)?, crossinline onUpstream: () -> Unit): FinancialDataset<T> {
        return cache.getOrLoad("cooldown:$key", FinancialCachePolicy.FAILURE, resultTtl = { result: FinancialDataset<T> ->
            if (result.accessDenied) FinancialCachePolicy.ACCESS_COOLDOWN else FinancialCachePolicy.FAILURE
        }) {
            try {
                cache.getOrLoad(successKey, ttl, ttlOf) {
                    onUpstream()   // `apiCall` records the upstream request itself (ProviderCalls)
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
                        if (rows.isEmpty()) FinancialAvailability.MISSING else FinancialAvailability.AVAILABLE,
                        retrievedAt = clock()
                    )
                }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                if (cause !is StockProviderException) throw cause
                val denied = cause.upstreamStatus in listOf(402, 403)
                // Phase 3E: a temporary failure (429, 5xx, timeout, invalid response) may show the last good copy, labelled
                // stale, within its limit. Access denial never does: restricted data isn't served on.
                val last = if (denied || staleLimit(endpoint) == 0L) null else cache.lastValue<FinancialDataset<T>>(successKey, staleLimit(endpoint))
                last?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.copy(stale = true)?.also { meter.event("fmp.dataset.staleServed") }
                    ?: FinancialDataset(emptyList(), FinancialAvailability.TEMPORARILY_UNAVAILABLE, accessDenied = denied)
            }
        }
    }

    /**
     * Reported quarterly diluted EPS for the historical P/E series: one statement request (24
     * quarters ≈ 6 years) cached like other statements. A missing filing date is treated as
     * period end + 45 days, the latest a quarterly report is normally public.
     */
    suspend fun quarterlyEarnings(symbol: String): List<QuarterlyEarnings> {
        val data = dataset<FmpIncomeStatement>("income-statement", symbol, "quarter", QUARTERS_FOR_VALUATION, periodEnd = { it.date })
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

    /**
     * Phase 3A: only the requested statements for one frequency, with the same dataset keys (and therefore the same
     * cached rows, single flight and cooldowns) as [load]: quarterly income is the shared 24-quarter request (newest 8
     * merged, as in [load]); balance sheet and cash flow use 8 quarters / 6 years. Requested datasets load concurrently.
     */
    suspend fun statementHistory(symbol: String, period: String, statements: Set<Statement>): StatementHistory = supervisorScope {
        require(Statement.INCOME in statements) { "Income statements define the reported periods" }
        val income = async {
            if (period == "annual") dataset<FmpIncomeStatement>("income-statement", symbol, "annual", periodEnd = { it.date })
            else dataset<FmpIncomeStatement>("income-statement", symbol, "quarter", QUARTERS_FOR_VALUATION, periodEnd = { it.date }).let { d -> d.copy(rows = d.rows.sortedByDescending { it.date.orEmpty() }.take(8)) }
        }
        val depth = if (period == "quarter") 8 else 6
        val balance = if (Statement.BALANCE in statements) async { dataset<FmpBalanceSheetStatement>("balance-sheet-statement", symbol, period, depth, periodEnd = { it.date }) } else null
        val cash = if (Statement.CASH_FLOW in statements) async { dataset<FmpCashFlowStatement>("cash-flow-statement", symbol, period, depth, periodEnd = { it.date }) } else null
        val availability = linkedMapOf<Statement, FinancialAvailability>()
        val provenance = Provenance(clock())
        fun <T> checked(statement: Statement, data: FinancialDataset<T>?, ticker: (T) -> String): List<T> {
            if (data == null) return emptyList()
            provenance.add(statement.dataset, data)
            val valid = data.rows.all { ticker(it).equals(symbol, true) }
            availability[statement] = if (valid) data.availability else FinancialAvailability.INVALID_VALUE
            return if (valid) data.rows else emptyList()
        }
        val rows = FmpFundamentalsMapper.history(period,
            checked(Statement.INCOME, income.await()) { it.symbol },
            checked(Statement.BALANCE, balance?.await()) { it.symbol },
            checked(Statement.CASH_FLOW, cash?.await()) { it.symbol }, today())
        StatementHistory(symbol, period, rows, availability, provenance.retrievedAt.toString(), "fmp", stale = provenance.stale.isNotEmpty())
    }

    /**
     * The full fundamentals bundle (Company Details / Financials). [historicalRatios] = false skips the annual
     * `ratios` history (5-year valuation comparisons), which the screener never reads (Phase 3B-1); its dataset then
     * isn't listed in `datasets` and `valuation.historical` stays empty.
     */
    suspend fun load(symbol: String, period: String, historicalRatios: Boolean = true, quote: suspend () -> Pair<StockQuote?, CompanyProfile?>): CompanyFundamentals = supervisorScope {
        val annual = async { dataset<FmpIncomeStatement>("income-statement", symbol, "annual", periodEnd = { it.date }) }
        // Quarterly: the same 24-quarter request Valuation uses (one cached dataset), newest 8 shown here.
        val income = if (period == "annual") annual else async {
            dataset<FmpIncomeStatement>("income-statement", symbol, "quarter", QUARTERS_FOR_VALUATION, periodEnd = { it.date }).let { d -> d.copy(rows = d.rows.sortedByDescending { it.date.orEmpty() }.take(8)) }
        }
        // Same depth as income/cash flow so every history row can carry its balance sheet.
        val balance = async { dataset<FmpBalanceSheetStatement>("balance-sheet-statement", symbol, period, if (period == "quarter") 8 else 6, periodEnd = { it.date }) }
        val cash = async { dataset<FmpCashFlowStatement>("cash-flow-statement", symbol, period, if (period == "quarter") 8 else 6, periodEnd = { it.date }) }
        val ratiosTtl = freshness.ttl(SessionData.TTM_RATIOS, symbol)
        val ratios = async { dataset<FmpRatiosTtm>("ratios-ttm", symbol, ttl = ratiosTtl) }
        val keys = async { dataset<FmpKeyMetricsTtm>("key-metrics-ttm", symbol, ttl = ratiosTtl) }
        val history = if (historicalRatios) async { dataset<FmpRatios>("ratios", symbol, "annual") } else null
        val trailingIncome = async { dataset<FmpIncomeStatement>("income-statement-ttm", symbol, limit = 1, ttl = FinancialCachePolicy.TTM_STATEMENTS, periodEnd = { it.date }) }
        val trailingCash = async { dataset<FmpCashFlowStatement>("cash-flow-statement-ttm", symbol, limit = 1, ttl = FinancialCachePolicy.TTM_STATEMENTS, periodEnd = { it.date }) }
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
        val provenance = Provenance(clock())
        fun <T> checked(name: String, data: FinancialDataset<T>, ticker: (T) -> String): List<T> {
            provenance.add(name, data)
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
            history = history?.let { h -> checked("historicalRatios", h.await()) { it.symbol } }.orEmpty(),
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
            // The oldest provider retrieval behind these figures: cache hits never make data look newer.
            retrievedAt = provenance.retrievedAt.toString(),
            freshness = provenance.freshness,
            staleDatasets = provenance.stale.toList()
        )
    }
}

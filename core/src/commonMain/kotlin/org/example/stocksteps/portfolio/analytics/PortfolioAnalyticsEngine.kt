package org.example.stocksteps.portfolio.analytics

import org.example.stocksteps.portfolio.*
import kotlin.math.pow

/** What the analytics know about a security beyond its ledger entries (from company profiles). */
data class SecurityMetadata(val name: String? = null, val sector: String? = null, val assetClass: AssetClass = AssetClass.UNCLASSIFIED)

/** Benchmark index levels by date, in the benchmark's own currency. */
data class BenchmarkSeries(val info: BenchmarkInfo, val levels: Map<String, String>)

/**
 * Everything one analytics run needs. Prices are daily closes in each holding's trade currency;
 * [fxByDate] holds published rates to the reporting currency (only dates with a publication).
 */
data class AnalyticsInputs(
    val ledger: PortfolioLedger,
    val accountId: String,
    val today: String,
    val closes: Map<String, Map<String, String>>,
    val fxByDate: Map<String, Map<PortfolioCurrency, String>>,
    /** Today's quotes (trade currency) and current FX; used for current allocation and the end value. */
    val current: PortfolioPrices,
    val metadata: Map<String, SecurityMetadata> = emptyMap(),
    val benchmark: BenchmarkSeries? = null,
    val asOf: String = "${today}T00:00:00Z"
)

/**
 * Portfolio Intelligence calculations on top of [PortfolioEngine] (the only ledger replay and
 * valuation engine). Every result is reproducible from the ledger and dated market data; missing
 * inputs make a metric PARTIAL or UNAVAILABLE rather than estimated.
 *
 * Methodologies (see [AnalyticsEducation]):
 * - Time-weighted return: daily-boundary chain linking. External flows (deposits, withdrawals,
 *   cash adjustments, cash/in-kind transfers and opening imports at that day's close) are assumed at
 *   the end of their date and the portfolio is valued at that date's close:
 *   rᵢ = (Vᵢ − Fᵢ) / Vᵢ₋₁ − 1, TWR = Π(1 + rᵢ) − 1. The first funding of an empty account uses
 *   rᵢ = Vᵢ / Fᵢ − 1. Every flow date is a valuation boundary, so
 *   flows never count as gains. Long periods sample at most [MAX_POINTS] valuation dates between flows.
 * - Money-weighted return: XIRR over (−start value, −flows, +end value), see [Xirr].
 * - Prices: last close on or before a date within [PRICE_CARRY_DAYS] days (different exchange
 *   holidays); FX: last Bank of Canada publication within [FX_CARRY_DAYS] days. Never today's FX
 *   for a past date.
 * - Contribution: Δ market value − net amount invested + income, per holding, in reporting currency;
 *   split exactly into a local part (at end FX) and an FX part. Percent uses Modified Dietz capital.
 */
object PortfolioAnalyticsEngine {
    const val MAX_POINTS = 200
    const val PRICE_CARRY_DAYS = 5
    const val FX_CARRY_DAYS = 7
    private val HUNDRED = Decimal.parse("100")

    // ---------- Lookups ----------

    private class Lookup(val inputs: AnalyticsInputs) {
        val account = inputs.ledger.accounts.first { it.id == inputs.accountId }
        val reporting = account.reportingCurrency
        val transactions = inputs.ledger.transactions.filter { it.accountId == inputs.accountId }
        private val fxDates = inputs.fxByDate.keys.sorted()
        private val sortedCloses = inputs.closes.mapValues { (_, series) -> series.keys.sorted() }

        fun fxAt(date: String): Map<PortfolioCurrency, String> {
            if (date == inputs.today && inputs.current.fx.isNotEmpty()) return inputs.current.fx
            val publication = fxDates.lastOrNull { it <= date } ?: return emptyMap()
            if (AnalyticsDates.day(date) - AnalyticsDates.day(publication) > FX_CARRY_DAYS) return emptyMap()
            return inputs.fxByDate.getValue(publication)
        }

        /** Rate from [currency] to the reporting currency on [date]; null when unpublished. */
        fun rate(currency: PortfolioCurrency, date: String): Decimal? =
            if (currency == reporting) Decimal.ONE else fxAt(date)[currency]?.let(Decimal::parse)

        fun priceAt(symbol: String, date: String): Decimal? {
            // Today's value uses today's quote only: a failed quote is missing, not yesterday's close.
            if (date == inputs.today) return inputs.current.prices[symbol]?.let(Decimal::parse)
            val dates = sortedCloses[symbol] ?: return null
            val last = dates.lastOrNull { it <= date } ?: return null
            if (AnalyticsDates.day(date) - AnalyticsDates.day(last) > PRICE_CARRY_DAYS) return null
            return Decimal.parse(inputs.closes.getValue(symbol).getValue(last))
        }

        val basisFx: Map<String, Map<PortfolioCurrency, String>> by lazy {
            transactions.map { it.tradeDate }.distinct().associateWith { fxAt(it) } + inputs.current.historicalFx
        }

        fun observation(date: String) = PortfolioPrices(
            date,
            transactions.mapNotNull { it.instrument?.symbol }.distinct().mapNotNull { symbol -> priceAt(symbol, date)?.let { symbol to it.toString() } }.toMap(),
            fxAt(date),
            basisFx
        )

        /** Account value at the close of [date], or null when a price or FX rate is missing. */
        fun value(date: String): Decimal? =
            PortfolioEngine.value(inputs.ledger, inputs.accountId, observation(date)).totalValue?.let(Decimal::parse)

        /** Reporting-currency amount of an external flow (positive into the account), or null if unvalued. */
        fun externalFlow(tx: PortfolioTransaction): Decimal? {
            val gross = Decimal.parse(tx.grossAmount)
            val fees = Decimal.parse(tx.fees)
            val quantity = Decimal.parse(tx.quantity)
            fun inKind(sign: Decimal): Decimal? {
                val symbol = tx.instrument!!.symbol
                val price = priceAt(symbol, tx.tradeDate) ?: return null
                return rate(tx.currency, tx.tradeDate)?.let { quantity * price * it * sign }
            }
            val local = when (tx.type) {
                TransactionType.CASH_DEPOSIT -> gross
                TransactionType.CASH_WITHDRAWAL -> -gross
                TransactionType.CASH_ADJUSTMENT -> gross - fees
                TransactionType.OPENING_POSITION -> return inKind(Decimal.ONE)
                TransactionType.TRANSFER_IN -> if (tx.instrument != null) return inKind(Decimal.ONE) else gross
                TransactionType.TRANSFER_OUT -> if (tx.instrument != null) return inKind(-Decimal.ONE) else -gross
                else -> return Decimal.ZERO
            }
            return rate(tx.currency, tx.tradeDate)?.let { local * it }
        }

        fun isExternal(tx: PortfolioTransaction) = tx.type in EXTERNAL
    }

    private val EXTERNAL = setOf(
        TransactionType.CASH_DEPOSIT, TransactionType.CASH_WITHDRAWAL, TransactionType.CASH_ADJUSTMENT,
        TransactionType.OPENING_POSITION, TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT
    )

    /** Periods with enough recorded history: the account must exist at the period's start (ALL always). */
    fun availablePeriods(ledger: PortfolioLedger, accountId: String, today: String): List<AnalyticsPeriod> {
        val first = ledger.transactions.filter { it.accountId == accountId }.minOfOrNull { it.tradeDate } ?: return emptyList()
        return AnalyticsPeriod.entries.filter { period ->
            val start = AnalyticsDates.periodStart(period, today)
            start == null || start >= first
        }
    }

    // ---------- Performance ----------

    private data class Boundary(val date: String, val value: Decimal, val flow: Decimal)

    private class PerformanceRun(
        val metrics: PerformanceMetrics,
        val startDate: String,
        val boundaries: List<Boundary>,
        val flows: List<Pair<String, Decimal>>
    )

    fun performance(inputs: AnalyticsInputs, period: AnalyticsPeriod): PerformanceMetrics = performanceRun(Lookup(inputs), period).metrics

    private fun performanceRun(lookup: Lookup, period: AnalyticsPeriod): PerformanceRun {
        val today = lookup.inputs.today
        val first = lookup.transactions.minOfOrNull { it.tradeDate }
        fun unavailable(start: String, note: String) = PerformanceRun(
            PerformanceMetrics(period, start, today, Availability.UNAVAILABLE, notes = listOf(note)), start, emptyList(), emptyList()
        )
        if (first == null) return unavailable(today, "No transactions recorded yet.")
        val periodStart = AnalyticsDates.periodStart(period, today)
        if (periodStart != null && periodStart < first) return unavailable(periodStart, "Your recorded history starts on $first, after the start of this period.")
        // ALL starts the day before the first entry, when the account was empty.
        val start = periodStart ?: AnalyticsDates.plusDays(first, -1)
        val notes = mutableListOf<String>()

        val externals = lookup.transactions.filter { lookup.isExternal(it) && it.tradeDate > start && it.tradeDate <= today }
        val flowByDate = linkedMapOf<String, Decimal>()
        for (tx in externals.sortedBy { it.tradeDate }) {
            val amount = lookup.externalFlow(tx) ?: return unavailable(start, "A transfer or deposit on ${tx.tradeDate} can't be valued (missing price or exchange rate).")
            flowByDate[tx.tradeDate] = (flowByDate[tx.tradeDate] ?: Decimal.ZERO) + amount
        }
        val symbols = lookup.transactions.mapNotNull { it.instrument?.symbol }.distinct()
        val marketDates = symbols.flatMap { lookup.inputs.closes[it]?.keys.orEmpty() }.filter { it > start && it < today }.toMutableSet()
        if (symbols.isEmpty()) {
            // Cash-only accounts: weekly valuation dates are enough (cash has no price moves).
            var date = AnalyticsDates.plusDays(start, 7)
            while (date < today) { marketDates += date; date = AnalyticsDates.plusDays(date, 7) }
        }
        val required = flowByDate.keys + today
        val optional = (marketDates - required).sorted()
        val stride = maxOf(1, (optional.size + MAX_POINTS - 1) / MAX_POINTS)
        val dates = (optional.filterIndexed { index, _ -> index % stride == 0 } + required).distinct().sorted()

        val startValue = if (start < first) Decimal.ZERO else lookup.value(start)
            ?: return unavailable(start, "Missing prices or exchange rates on the start date ($start).")
        val boundaries = mutableListOf(Boundary(start, startValue, Decimal.ZERO))
        for (date in dates) {
            val value = lookup.value(date)
            val flow = flowByDate[date] ?: Decimal.ZERO
            if (value == null) {
                if (date in required) return unavailable(start, if (date == lookup.inputs.today) "A current price or exchange rate is missing, so today's value can't be calculated." else "Missing prices or exchange rates on $date, which is needed to separate deposits from gains.")
                continue // a non-boundary sample can be skipped without affecting chain linking
            }
            boundaries += Boundary(date, value, flow)
        }

        // Chain-linked index.
        var index = HUNDRED
        val indexDates = mutableListOf<String>()
        val indexValues = mutableListOf<String?>()
        var started = startValue > Decimal.ZERO
        if (started) { indexDates += start; indexValues += index.toString() }
        for (i in 1 until boundaries.size) {
            val previous = boundaries[i - 1].value
            val current = boundaries[i]
            // End-of-day flows: money arriving on a date doesn't earn (or lose) that date's move.
            // An empty account's first funding has no prior value, so it starts the chain instead.
            val growth = when {
                previous > Decimal.ZERO -> (current.value - current.flow) / previous
                previous + current.flow > Decimal.ZERO -> current.value / (previous + current.flow)
                current.value == Decimal.ZERO -> continue // nothing invested yet
                else -> return unavailable(start, "The account had no positive balance before ${current.date}, so a return can't be measured.")
            }
            if (!started) { started = true; indexDates += boundaries[i - 1].date; indexValues += index.toString() }
            index *= growth
            indexDates += current.date
            indexValues += index.toString()
        }
        val endValue = boundaries.last().value
        val netFlows = flowByDate.values.fold(Decimal.ZERO, Decimal::plus)
        val deposits = flowByDate.values.filter { it > Decimal.ZERO }.fold(Decimal.ZERO, Decimal::plus)
        val withdrawals = flowByDate.values.filter { it < Decimal.ZERO }.fold(Decimal.ZERO, Decimal::plus)
        val gain = endValue - startValue - netFlows
        val twr = if (started) (index - HUNDRED) else null
        val days = AnalyticsDates.day(today) - AnalyticsDates.day(start)
        val annualized = twr?.takeIf { days >= 365 }?.let { cumulative ->
            ((1 + cumulative.toString().toDouble() / 100).pow(365.0 / days) - 1) * 100
        }
        val dividends = dividendsIn(lookup, start, today)

        val flows = buildList {
            if (startValue > Decimal.ZERO) add(start to -startValue)
            flowByDate.forEach { (date, amount) -> add(date to -amount) }
            add(today to endValue)
        }
        val (xirr, xirrStatus) = when {
            days < 30 -> null to "Not shown for periods under a month: annualizing a few days of returns exaggerates them."
            else -> when (val result = Xirr.solve(flows.map { DatedFlow(it.first, it.second.toString().toDouble()) })) {
                is XirrResult.Rate -> (result.value * 100) to null
                is XirrResult.Undefined -> null to result.reason
            }
        }
        if (start < first) notes += "Measured from your first transaction on $first."
        if (stride > 1) notes += "Long periods use ${dates.size} valuation dates; every deposit, withdrawal and transfer date is included exactly."
        return PerformanceRun(
            PerformanceMetrics(
                period = period, startDate = start, endDate = today,
                availability = if (twr == null) Availability.UNAVAILABLE else Availability.AVAILABLE,
                startValue = startValue.toString(), endValue = endValue.toString(), netExternalFlows = netFlows.toString(),
                deposits = deposits.toString(), withdrawals = (-withdrawals).toString(), investmentGain = gain.toString(),
                dividends = dividends?.toString(),
                timeWeightedReturn = twr?.toString(), annualizedTimeWeightedReturn = annualized?.let(::fixed),
                moneyWeightedReturn = xirr?.let(::fixed), xirrStatus = xirrStatus,
                indexDates = indexDates, portfolioIndex = indexValues,
                notes = notes + if (twr == null) listOf("No invested balance in this period yet.") else emptyList()
            ),
            start, boundaries, flowByDate.toList()
        )
    }

    private fun dividendsIn(lookup: Lookup, after: String, through: String): Decimal? {
        var total = Decimal.ZERO
        for (tx in lookup.transactions.filter { it.tradeDate > after && it.tradeDate <= through }) {
            val local = dividendAmount(tx) ?: continue
            total += local * (lookup.rate(tx.currency, tx.tradeDate) ?: return null)
        }
        return total
    }

    /** Gross dividend in the trade currency: cash dividends and the dividend that funded a reinvestment. */
    private fun dividendAmount(tx: PortfolioTransaction): Decimal? = when (tx.type) {
        TransactionType.DIVIDEND -> Decimal.parse(tx.grossAmount)
        TransactionType.DIVIDEND_REINVESTMENT -> Decimal.parse(tx.quantity) * Decimal.parse(tx.unitPrice) + Decimal.parse(tx.fees)
        else -> null
    }

    // ---------- Benchmark ----------

    fun benchmark(inputs: AnalyticsInputs, performance: PerformanceMetrics): BenchmarkComparison? {
        val series = inputs.benchmark ?: return null
        val lookup = Lookup(inputs)
        val notes = mutableListOf(
            if (series.info.basis == ReturnBasis.PRICE_RETURN) "${series.info.name} is a price-return index: it excludes dividends, while your return includes dividends you received."
            else "${series.info.name} is a total-return index, including dividends.",
            "Both lines use the same dates; a holiday uses the last available close (up to $PRICE_CARRY_DAYS days)."
        )
        if (series.info.currency != lookup.reporting) notes += "The index is converted to ${lookup.reporting.name} at each date's Bank of Canada rate, so it includes currency movement like your foreign holdings."
        if (series.info.isProxy) notes += "Measured with ${series.info.symbol}, a fund that tracks the index."
        fun unavailable(reason: String) = BenchmarkComparison(series.info, Availability.UNAVAILABLE, currency = lookup.reporting, notes = listOf(reason) + notes)
        if (performance.availability != Availability.AVAILABLE || performance.indexDates.isEmpty()) return unavailable("Your portfolio's return isn't available for this period, so there's nothing to compare.")
        val dates = series.levels.keys.sorted()
        fun level(date: String): Decimal? {
            val last = dates.lastOrNull { it <= date } ?: return null
            if (AnalyticsDates.day(date) - AnalyticsDates.day(last) > PRICE_CARRY_DAYS) return null
            val raw = Decimal.parse(series.levels.getValue(last))
            return if (series.info.currency == lookup.reporting) raw else lookup.rate(series.info.currency, date)?.let { raw * it }
        }
        val base = level(performance.indexDates.first()) ?: return unavailable("Benchmark data isn't available for ${performance.indexDates.first()}.")
        val index = performance.indexDates.map { date -> level(date)?.let { (it / base * HUNDRED).toString() } }
        val end = index.last() ?: return unavailable("Benchmark data isn't available for ${performance.indexDates.last()}.")
        val benchmarkReturn = Decimal.parse(end) - HUNDRED
        val portfolioReturn = Decimal.parse(performance.timeWeightedReturn!!)
        if (index.any { it == null }) notes += "Some dates have no benchmark observation and appear as gaps."
        return BenchmarkComparison(
            series.info, Availability.AVAILABLE, portfolioReturn.toString(), benchmarkReturn.toString(),
            (portfolioReturn - benchmarkReturn).toString(), index, lookup.reporting, notes
        )
    }

    // ---------- Current allocation, concentration, currency ----------

    private class CurrentPosition(val symbol: String, val name: String?, val currency: PortfolioCurrency, val value: Decimal?, val meta: SecurityMetadata)

    private fun currentPositions(lookup: Lookup): Pair<List<CurrentPosition>, Map<PortfolioCurrency, Decimal?>> {
        val state = PortfolioEngine.replay(lookup.inputs.ledger, lookup.inputs.accountId)
        val positions = state.holdings.filter { Decimal.parse(it.quantity) > Decimal.ZERO }.map { holding ->
            val symbol = holding.instrument.symbol
            val price = lookup.inputs.current.prices[symbol]?.let(Decimal::parse)
            val rate = lookup.rate(holding.currency, lookup.inputs.today)
            val meta = lookup.inputs.metadata[symbol] ?: SecurityMetadata()
            CurrentPosition(symbol, meta.name ?: holding.instrument.name, holding.currency,
                if (price != null && rate != null) Decimal.parse(holding.quantity) * price * rate else null, meta)
        }
        val cash = state.cash.mapValues { (currency, amount) ->
            val value = Decimal.parse(amount)
            if (value == Decimal.ZERO) Decimal.ZERO else lookup.rate(currency, lookup.inputs.today)?.let { value * it }
        }.filterValues { it != Decimal.ZERO }
        return positions to cash
    }

    private fun percent(part: Decimal, whole: Decimal) = (part / whole * HUNDRED).toString()

    private fun slices(values: Map<Pair<String, String>, Decimal>, whole: Decimal, limit: Int = Int.MAX_VALUE): List<AllocationSlice> {
        val sorted = values.entries.sortedByDescending { it.value }
        val kept = sorted.take(limit)
        val rest = sorted.drop(limit).fold(Decimal.ZERO) { sum, entry -> sum + entry.value }
        return kept.map { AllocationSlice(it.key.first, it.key.second, it.value.toString(), percent(it.value, whole)) } +
            if (rest > Decimal.ZERO) listOf(AllocationSlice("other", "Other", rest.toString(), percent(rest, whole))) else emptyList()
    }

    fun allocation(inputs: AnalyticsInputs): AllocationBreakdown {
        val lookup = Lookup(inputs)
        val (positions, cash) = currentPositions(lookup)
        val missing = positions.filter { it.value == null }.map { it.symbol } + cash.filterValues { it == null }.keys.map { "${it.name} cash" }
        if (missing.isNotEmpty()) return AllocationBreakdown(Availability.PARTIAL,
            notes = listOf("Percentages are hidden because current prices or exchange rates are missing for ${missing.joinToString()}. They are never recalculated over only part of your account."))
        val positiveCash = cash.filterValues { it!! > Decimal.ZERO }.mapValues { it.value!! }
        val borrowed = cash.values.filterNotNull().filter { it < Decimal.ZERO }.fold(Decimal.ZERO, Decimal::plus)
        val holdingsTotal = positions.fold(Decimal.ZERO) { sum, p -> sum + p.value!! }
        val long = holdingsTotal + positiveCash.values.fold(Decimal.ZERO, Decimal::plus)
        if (long <= Decimal.ZERO) return AllocationBreakdown(Availability.UNAVAILABLE, notes = listOf("Nothing is invested or held in cash yet."))
        val cashTotal = positiveCash.values.fold(Decimal.ZERO, Decimal::plus)
        val byHolding = positions.associate { (it.symbol to it.symbol) to it.value!! } + if (cashTotal > Decimal.ZERO) mapOf(("cash" to "Cash") to cashTotal) else emptyMap()
        val sectors = linkedMapOf<Pair<String, String>, Decimal>()
        fun addTo(map: MutableMap<Pair<String, String>, Decimal>, key: Pair<String, String>, amount: Decimal) { map[key] = (map[key] ?: Decimal.ZERO) + amount }
        positions.forEach { p ->
            val key = when {
                p.meta.assetClass == AssetClass.ETF -> "etf" to "ETFs and funds"
                p.meta.sector != null -> p.meta.sector to p.meta.sector
                else -> "unclassified" to "Unclassified"
            }
            addTo(sectors, key, p.value!!)
        }
        if (cashTotal > Decimal.ZERO) addTo(sectors, "cash" to "Cash", cashTotal)
        val classes = linkedMapOf<Pair<String, String>, Decimal>()
        positions.forEach { p ->
            addTo(classes, when (p.meta.assetClass) {
                AssetClass.ETF -> "etf" to "ETFs"
                AssetClass.STOCK -> "stock" to "Individual stocks"
                else -> "unclassified" to "Unclassified"
            }, p.value!!)
        }
        if (cashTotal > Decimal.ZERO) addTo(classes, "cash" to "Cash", cashTotal)
        val currencies = linkedMapOf<Pair<String, String>, Decimal>()
        positions.forEach { addTo(currencies, it.currency.name to it.currency.name, it.value!!) }
        positiveCash.forEach { (currency, amount) -> addTo(currencies, currency.name to currency.name, amount) }
        val classified = positions.filter { it.meta.assetClass != AssetClass.ETF && it.meta.sector != null }.fold(Decimal.ZERO) { sum, p -> sum + p.value!! }
        val notes = mutableListOf("Percentages are shares of holdings plus cash (${lookup.reporting.name} ${long.display()}).")
        if (borrowed < Decimal.ZERO) notes += "Borrowed or unfunded cash (${borrowed.display()}) is shown separately and not included in percentages."
        if (positions.any { it.meta.assetClass == AssetClass.ETF }) notes += "ETFs are shown as funds. Their underlying sectors aren't estimated because fund holdings data isn't available."
        if (positions.any { it.meta.sector == null && it.meta.assetClass != AssetClass.ETF }) notes += "Holdings without sector data are listed as Unclassified rather than guessed."
        return AllocationBreakdown(
            Availability.AVAILABLE, long.toString(), borrowed.takeIf { it < Decimal.ZERO }?.toString(),
            slices(byHolding, long, limit = 7), slices(sectors, long), slices(classes, long), slices(currencies, long),
            classifiedShare = if (holdingsTotal > Decimal.ZERO) percent(classified, holdingsTotal) else null,
            notes = notes
        )
    }

    fun concentration(inputs: AnalyticsInputs): ConcentrationMetrics {
        val lookup = Lookup(inputs)
        val (positions, cash) = currentPositions(lookup)
        if (positions.isEmpty()) return ConcentrationMetrics(Availability.UNAVAILABLE, notes = listOf("No holdings yet."))
        if (positions.any { it.value == null } || cash.values.any { it == null }) return ConcentrationMetrics(Availability.PARTIAL, positions.size,
            notes = listOf("Missing current prices or exchange rates; concentration isn't calculated over part of the account."))
        val holdingsTotal = positions.fold(Decimal.ZERO) { sum, p -> sum + p.value!! }
        val long = holdingsTotal + cash.values.filterNotNull().filter { it > Decimal.ZERO }.fold(Decimal.ZERO, Decimal::plus)
        if (holdingsTotal <= Decimal.ZERO) return ConcentrationMetrics(Availability.UNAVAILABLE, positions.size)
        val sorted = positions.sortedByDescending { it.value!! }
        fun share(n: Int) = sorted.take(n).fold(Decimal.ZERO) { sum, p -> sum + p.value!! }.let { percent(it, long) }
        val sectorTotals = positions.filter { it.meta.assetClass != AssetClass.ETF && it.meta.sector != null }
            .groupBy { it.meta.sector!! }.mapValues { (_, list) -> list.fold(Decimal.ZERO) { sum, p -> sum + p.value!! } }
        val topSector = sectorTotals.maxByOrNull { it.value }
        val hhi = positions.fold(Decimal.ZERO) { sum, p -> val w = p.value!! / holdingsTotal; sum + w * w }
        return ConcentrationMetrics(
            Availability.AVAILABLE, positions.size,
            largestHolding = sorted.first().let { WeightedName(it.symbol, percent(it.value!!, long)) },
            topThree = if (positions.size > 3) share(3) else null,
            topFive = if (positions.size > 5) share(5) else null,
            largestSector = topSector?.let { WeightedName(it.key, percent(it.value, long)) },
            hhi = hhi.toString(),
            effectiveHoldings = if (hhi > Decimal.ZERO) (Decimal.ONE / hhi).toString() else null,
            notes = listOf(
                "Holding shares use holdings plus cash as the denominator; HHI and effective holdings use securities only.",
                "Concentration is one aspect of risk; it doesn't say whether a portfolio is suitable."
            )
        )
    }

    // ---------- Contribution ----------

    fun contributors(inputs: AnalyticsInputs, period: AnalyticsPeriod): Contributors {
        val lookup = Lookup(inputs)
        val measured = performanceRun(lookup, period)
        val perf = measured.metrics
        if (perf.availability != Availability.AVAILABLE) return Contributors(period, Availability.UNAVAILABLE, notes = perf.notes)
        val start = measured.startDate
        val end = inputs.today
        val startState = PortfolioEngine.replay(inputs.ledger, inputs.accountId, start)
        val endState = PortfolioEngine.replay(inputs.ledger, inputs.accountId, end)
        val symbols = lookup.transactions.mapNotNull { it.instrument?.symbol }.distinct()
        val rows = mutableListOf<ContributorRow>()
        val missing = mutableListOf<String>()
        val totalDays = AnalyticsDates.day(end) - AnalyticsDates.day(start)
        val capital = Decimal.parse(perf.startValue!!) + measured.flows.fold(Decimal.ZERO) { sum, (date, amount) ->
            sum + amount.multiplyDivide(Decimal.parse((AnalyticsDates.day(end) - AnalyticsDates.day(date)).toString()), Decimal.parse(totalDays.toString()))
        }
        for (symbol in symbols) {
            val txs = lookup.transactions.filter { it.instrument?.symbol == symbol && it.tradeDate > start && it.tradeDate <= end }
            val qtyStart = startState.holdings.find { it.instrument.symbol == symbol }?.quantity?.let(Decimal::parse) ?: Decimal.ZERO
            val qtyEnd = endState.holdings.find { it.instrument.symbol == symbol }?.quantity?.let(Decimal::parse) ?: Decimal.ZERO
            if (qtyStart == Decimal.ZERO && qtyEnd == Decimal.ZERO && txs.isEmpty()) continue
            val currency = (endState.holdings + startState.holdings).first { it.instrument.symbol == symbol }.currency
            fun price(date: String) = lookup.priceAt(symbol, date)
            val fS = if (qtyStart > Decimal.ZERO) lookup.rate(currency, start) else Decimal.ONE
            val fE = lookup.rate(currency, end)
            val pS = if (qtyStart > Decimal.ZERO) price(start) else Decimal.ZERO
            val pE = if (qtyEnd > Decimal.ZERO) price(end) else Decimal.ZERO
            if (fS == null || fE == null || pS == null || pE == null) { missing += symbol; continue }
            val mvS = qtyStart * pS; val mvE = qtyEnd * pE
            var investedLocal = Decimal.ZERO; var investedRep = Decimal.ZERO
            var incomeLocal = Decimal.ZERO; var incomeRep = Decimal.ZERO
            var ok = true
            for (tx in txs) {
                val f = lookup.rate(currency, tx.tradeDate)
                val q = Decimal.parse(tx.quantity); val p = Decimal.parse(tx.unitPrice); val fee = Decimal.parse(tx.fees)
                val marked = if (tx.type in setOf(TransactionType.OPENING_POSITION, TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT)) price(tx.tradeDate) else Decimal.ZERO
                if (f == null || marked == null) { ok = false; break }
                val (invested, income) = when (tx.type) {
                    TransactionType.BUY -> (q * p + fee) to Decimal.ZERO
                    TransactionType.SELL -> -(q * p - fee) to Decimal.ZERO
                    TransactionType.DIVIDEND_REINVESTMENT -> (q * p + fee) to (q * p + fee)
                    TransactionType.DIVIDEND -> Decimal.ZERO to (Decimal.parse(tx.grossAmount) - fee)
                    TransactionType.OPENING_POSITION, TransactionType.TRANSFER_IN -> (q * marked) to Decimal.ZERO
                    TransactionType.TRANSFER_OUT -> -(q * marked) to Decimal.ZERO
                    else -> Decimal.ZERO to Decimal.ZERO
                }
                investedLocal += invested; investedRep += invested * f
                incomeLocal += income; incomeRep += income * f
            }
            if (!ok) { missing += symbol; continue }
            val contribution = mvE * fE - mvS * fS - investedRep + incomeRep
            val local = (mvE - mvS - investedLocal + incomeLocal) * fE
            val meta = inputs.metadata[symbol]
            rows += ContributorRow(symbol, meta?.name, contribution.toString(), local.toString(), (contribution - local).toString(),
                if (capital > Decimal.ZERO) percent(contribution, capital) else null)
        }
        val attributed = rows.fold(Decimal.ZERO) { sum, row -> sum + Decimal.parse(row.contribution!!) }
        val notes = mutableListOf(
            "Contribution = change in market value − money invested + dividends received, in ${lookup.reporting.name}. A large holding with a small gain can contribute more than a small holding with a big gain.",
            "Percent contributions use average invested capital (Modified Dietz), so they can differ slightly from the time-weighted return."
        )
        if (missing.isNotEmpty()) notes += "Couldn't calculate ${missing.joinToString()} (missing prices or exchange rates)."
        return Contributors(
            period, if (missing.isEmpty()) Availability.AVAILABLE else Availability.PARTIAL,
            positive = rows.filter { Decimal.parse(it.contribution!!) > Decimal.ZERO }.sortedByDescending { Decimal.parse(it.contribution!!) }.take(5),
            negative = rows.filter { Decimal.parse(it.contribution!!) < Decimal.ZERO }.sortedBy { Decimal.parse(it.contribution!!) }.take(5),
            unavailable = missing,
            other = if (missing.isEmpty()) (Decimal.parse(perf.investmentGain!!) - attributed).toString() else null,
            notes = notes
        )
    }

    // ---------- Dividends ----------

    fun dividends(inputs: AnalyticsInputs, period: AnalyticsPeriod): DividendSummary {
        val lookup = Lookup(inputs)
        val periodStart = AnalyticsDates.periodStart(period, inputs.today)
        val payments = lookup.transactions.filter { dividendAmount(it) != null }
        if (payments.isEmpty()) return DividendSummary(Availability.AVAILABLE, "0", "0", "0", 0,
            notes = listOf("No dividends recorded. Totals include only dividends you record."))
        var total = Decimal.ZERO; var inPeriod = Decimal.ZERO; var reinvested = Decimal.ZERO
        val byCompany = linkedMapOf<String, Decimal>(); val byMonth = mutableMapOf<String, Decimal>(); val byYear = mutableMapOf<String, Decimal>()
        val unconverted = mutableListOf<String>()
        for (tx in payments) {
            val rate = lookup.rate(tx.currency, tx.tradeDate)
            if (rate == null) { unconverted += tx.tradeDate; continue }
            val amount = dividendAmount(tx)!! * rate
            total += amount
            if (periodStart == null || tx.tradeDate > periodStart) inPeriod += amount
            if (tx.type == TransactionType.DIVIDEND_REINVESTMENT) reinvested += amount
            val company = tx.instrument?.symbol ?: "Cash"
            byCompany[company] = (byCompany[company] ?: Decimal.ZERO) + amount
            byMonth[tx.tradeDate.take(7)] = (byMonth[tx.tradeDate.take(7)] ?: Decimal.ZERO) + amount
            byYear[tx.tradeDate.take(4)] = (byYear[tx.tradeDate.take(4)] ?: Decimal.ZERO) + amount
        }
        val notes = mutableListOf(
            "Totals include only dividends you recorded, converted at each payment date's exchange rate.",
            "Reinvested dividends count once, as income; the shares they bought aren't counted as new money.",
            "Future dividends aren't estimated: companies can change or stop them."
        )
        if (unconverted.isNotEmpty()) notes += "${unconverted.size} payment(s) couldn't be converted (no exchange rate) and are excluded."
        return DividendSummary(
            if (unconverted.isEmpty()) Availability.AVAILABLE else Availability.PARTIAL,
            total.toString(), inPeriod.toString(), reinvested.toString(), payments.size - unconverted.size,
            byCompany.entries.sortedByDescending { it.value }.map { AmountByKey(it.key, it.value.toString()) },
            byMonth.entries.sortedBy { it.key }.takeLast(12).map { AmountByKey(it.key, it.value.toString()) },
            byYear.entries.sortedBy { it.key }.map { AmountByKey(it.key, it.value.toString()) },
            notes
        )
    }

    // ---------- Currency ----------

    fun currency(inputs: AnalyticsInputs, contributors: Contributors?): CurrencyExposure {
        val lookup = Lookup(inputs)
        val (positions, cash) = currentPositions(lookup)
        if (positions.any { it.value == null } || cash.values.any { it == null }) return CurrencyExposure(Availability.PARTIAL,
            notes = listOf("Current prices or exchange rates are missing, so exposure isn't calculated."))
        val values = linkedMapOf<Pair<String, String>, Decimal>()
        positions.groupBy { it.currency }.forEach { (currency, list) ->
            values[currency.name + "-holdings" to "${currency.name} stocks and ETFs"] = list.fold(Decimal.ZERO) { sum, p -> sum + p.value!! }
        }
        cash.forEach { (currency, amount) -> if (amount!! > Decimal.ZERO) values[currency.name + "-cash" to "${currency.name} cash"] = amount }
        val long = values.values.fold(Decimal.ZERO, Decimal::plus)
        if (long <= Decimal.ZERO) return CurrencyExposure(Availability.UNAVAILABLE, notes = listOf("Nothing is invested or held in cash yet."))
        val foreign = values.filterKeys { !it.first.startsWith(lookup.reporting.name) }.values.fold(Decimal.ZERO, Decimal::plus)
        val rows = (contributors?.positive.orEmpty() + contributors?.negative.orEmpty())
        val fxEffect = if (contributors?.availability == Availability.AVAILABLE) rows.fold(Decimal.ZERO) { sum, r -> sum + Decimal.parse(r.fxPart!!) } else null
        val localEffect = if (contributors?.availability == Availability.AVAILABLE) rows.fold(Decimal.ZERO) { sum, r -> sum + Decimal.parse(r.localPart!!) } else null
        return CurrencyExposure(
            Availability.AVAILABLE, slices(values, long), percent(foreign, long), fxEffect?.toString(), localEffect?.toString(),
            notes = listOf(
                "This is denomination exposure: the currency each holding trades in. A company's or fund's business can earn money in other currencies.",
                "The currency effect splits each holding's gain into its own price change (at today's exchange rate) and exchange-rate movement."
            )
        )
    }

    // ---------- Assembly ----------

    fun analyze(inputs: AnalyticsInputs, period: AnalyticsPeriod, tier: SubscriptionTier): PortfolioAnalytics {
        val lookup = Lookup(inputs)
        val plus = tier == SubscriptionTier.PLUS
        val periods = availablePeriods(inputs.ledger, inputs.accountId, inputs.today)
        val allocation = allocation(inputs)
        val concentration = concentration(inputs)
        val performance = if (plus) performance(inputs, period) else null
        val benchmark = performance?.let { benchmark(inputs, it) }
        val contributors = if (plus) contributors(inputs, period) else null
        val dividends = dividends(inputs, period)
        val currency = currency(inputs, contributors)
        val first = lookup.transactions.minOfOrNull { it.tradeDate }
        val health = health(lookup, allocation, concentration, currency, first, plus, benchmark)
        val freeConcentration = if (plus) concentration else concentration.copy(topThree = null, topFive = null, largestSector = null, hhi = null, effectiveHoldings = null)
        val freeAllocation = if (plus) allocation else allocation.copy(bySector = emptyList(), byAssetClass = emptyList())
        val freeDividends = if (plus) dividends else dividends.copy(byCompany = emptyList(), byMonth = emptyList(), byYear = emptyList(), inPeriod = null)
        val freeCurrency = if (plus) currency else currency.copy(fxEffect = null, localEffect = null)
        val draft = PortfolioAnalytics(
            accountId = inputs.accountId, revision = inputs.ledger.revision, reportingCurrency = lookup.reporting, asOf = inputs.asOf,
            tier = tier, period = period, periods = periods, health = health,
            performance = performance, benchmark = benchmark,
            allocation = freeAllocation, concentration = freeConcentration, contributors = contributors,
            dividends = freeDividends, currency = freeCurrency,
            locked = if (plus) emptyList() else listOf("performance", "benchmark", "sectors", "concentration-detail", "contributors", "dividends-detail", "currency-impact")
        )
        return draft.copy(insights = PortfolioInsightsEngine.generate(draft))
    }

    private fun health(lookup: Lookup, allocation: AllocationBreakdown, concentration: ConcentrationMetrics, currency: CurrencyExposure,
                       first: String?, plus: Boolean, benchmark: BenchmarkComparison?): List<HealthMetric> = buildList {
        add(HealthMetric("holdings", "Holdings", concentration.holdingsCount.toString(),
            if (concentration.holdingsCount == 1) "You hold one security." else "You hold ${concentration.holdingsCount} different securities."))
        concentration.largestHolding?.let {
            val share = Decimal.parse(it.percent).display(0)
            add(HealthMetric("largest", "Largest holding", "${it.name} · $share%", "About $share% of your portfolio's current value is in ${it.name}."))
        }
        if (plus) concentration.largestSector?.let {
            val share = Decimal.parse(it.percent).display(0)
            add(HealthMetric("sector", "Largest sector", "${it.name} · $share%", "$share% of your portfolio's value is in ${it.name} companies (classified holdings only)."))
        }
        currency.foreignShare?.let {
            val foreign = if (lookup.reporting == PortfolioCurrency.CAD) "USD" else "CAD"
            val share = Decimal.parse(it).display(0)
            add(HealthMetric("currency", "$foreign exposure", "$share%", "$share% of your portfolio is held in $foreign-denominated holdings or cash."))
        }
        first?.let {
            val days = AnalyticsDates.day(lookup.inputs.today) - AnalyticsDates.day(it)
            val span = when { days >= 730 -> "${days / 365} years"; days >= 365 -> "1 year"; days >= 60 -> "${days / 30} months"; else -> "$days days" }
            add(HealthMetric("history", "Recorded history", span, "Your recorded transactions start on $it. Returns can't be measured before that."))
        }
        add(HealthMetric("benchmark", "Benchmark comparison", when {
            !plus -> "StockSteps+"
            benchmark?.availability == Availability.AVAILABLE -> "Available"
            else -> "Unavailable"
        }, if (plus) "Compares your time-weighted return with a market index over the same dates." else "Comparing with a market index is part of StockSteps+."))
    }

    /** A solver/annualization Double rounded to 6 decimals as an exact decimal string. */
    private fun fixed(value: Double): String {
        val micros = kotlin.math.round(value * 1_000_000).toLong()
        return (Decimal.parse(micros.toString()) / Decimal.parse("1000000")).toString()
    }
}

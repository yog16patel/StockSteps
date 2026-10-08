package org.example.stocksteps.screener

import org.example.stocksteps.markets.MarketsPresenter
import org.example.stocksteps.model.*
import kotlin.math.abs

/**
 * Builds a [CompanyRecord] from the same quote, profile and `CompanyFundamentals` that Company
 * Details uses, so a metric (e.g. trailing P/E) has one definition and one value across screens.
 */
object CompanyRecordBuilder {
    /** Fundamentals whose latest reported period ended longer ago than this are labelled stale. */
    const val STALE_PERIOD_DAYS = 550
    /** Fundamentals retrieved longer ago than this are labelled stale. */
    const val STALE_RETRIEVAL_DAYS = 30

    fun build(
        symbol: String,
        quote: StockQuote?,
        profile: CompanyProfile?,
        fundamentals: CompanyFundamentals?,
        /** USD per one unit of the listing currency (1.0 for USD); null when no rate is known. */
        usdRate: Double?,
        today: String
    ): CompanyRecord {
        val facts = fundamentals?.let { it.financials.metrics() + it.valuation.metrics }.orEmpty()
        val metrics = linkedMapOf<String, MetricValue>()
        fun fact(id: String) = facts[id]?.let { f ->
            val value = f.numericValue()?.takeIf { it.isFinite() }
            when {
                f.availability == FinancialAvailability.NO_DIVIDEND -> MetricValue(0.0, f.availability, f.basis, f.note ?: "No dividend in the trailing year.")
                f.availability == FinancialAvailability.AVAILABLE && value != null -> MetricValue(value, f.availability, f.basis, f.note)
                else -> MetricValue(null, if (f.availability == FinancialAvailability.AVAILABLE) FinancialAvailability.INVALID_VALUE else f.availability, f.basis, f.note)
            }
        }
        for (definition in ScreenerDefinitions.metrics) {
            when (definition.id) {
                "marketCap" -> metrics["marketCap"] = marketCap(quote, facts)?.takeIf { usdRate != null }
                    ?.let { MetricValue(it * usdRate!!, FinancialAvailability.AVAILABLE, FinancialBasis("Latest quote", currency = "USD")) }
                    ?: MetricValue(availability = FinancialAvailability.MISSING)
                "price" -> metrics["price"] = quote?.price?.takeIf { it > 0 }?.let { MetricValue(it, FinancialAvailability.AVAILABLE, FinancialBasis("Latest quote", currency = profile?.currency)) }
                    ?: MetricValue()
                "volume" -> metrics["volume"] = quote?.volume?.takeIf { it >= 0 }?.let { MetricValue(it.toDouble(), FinancialAvailability.AVAILABLE) } ?: MetricValue()
                "yearRangePosition" -> metrics["yearRangePosition"] = yearRangePosition(quote)
                "fcfGrowth" -> metrics["fcfGrowth"] = fcfGrowth(fundamentals)
                "epsGrowth" -> metrics["epsGrowth"] = turnaroundSafe(fact("epsGrowth"), fundamentals) { it.epsDiluted }
                "netIncomeGrowth" -> metrics["netIncomeGrowth"] = turnaroundSafe(fact("netIncomeGrowth"), fundamentals) { it.netIncome }
                else -> metrics[definition.id] = fact(definition.id) ?: MetricValue()
            }
        }
        val latestPeriod = fundamentals?.history?.firstOrNull()?.date ?: facts["revenue"]?.basis?.date
        val stale = latestPeriod?.let { days(today) - days(it) > STALE_PERIOD_DAYS } == true ||
            fundamentals?.retrievedAt?.take(10)?.let { days(today) - days(it) > STALE_RETRIEVAL_DAYS } == true
        return CompanyRecord(
            symbol = symbol,
            name = profile?.companyName ?: quote?.companyName ?: symbol,
            exchange = profile?.exchange, country = profile?.country, currency = profile?.currency,
            sector = profile?.sector?.takeIf { it.isNotBlank() && profile.isEtf != true },
            industry = profile?.industry?.takeIf { it.isNotBlank() && profile.isEtf != true },
            securityType = if (profile?.isEtf == true) "ETF" else "Stock",
            logoUrl = profile?.logoUrl,
            price = quote?.price, changePercent = quote?.changePercent, marketCap = marketCap(quote, facts),
            volume = quote?.volume?.toDouble(),
            metrics = metrics,
            fundamentalsAsOf = fundamentals?.retrievedAt,
            stale = stale
        )
    }

    private fun days(date: String) = MarketsPresenter.dayNumber(date.take(10)) ?: 0

    /**
     * Market cap in the listing currency = latest price × shares outstanding (the standard definition),
     * falling back to the quote's reported value when the share count isn't available.
     */
    private fun marketCap(quote: StockQuote?, facts: Map<String, FinancialFact>): Double? {
        val shares = facts["shares"]?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.numericValue()?.takeIf { it > 0 }
        val price = quote?.price?.takeIf { it > 0 }
        return if (shares != null && price != null) shares * price else quote?.marketCap?.takeIf { it > 0 }?.toDouble()
    }

    private fun yearRangePosition(quote: StockQuote?): MetricValue {
        val price = quote?.price; val high = quote?.yearHigh; val low = quote?.yearLow
        if (price == null || high == null || low == null || high <= low) return MetricValue()
        return MetricValue(((price - low) / (high - low) * 100).coerceIn(0.0, 100.0), FinancialAvailability.AVAILABLE, FinancialBasis("Last 52 weeks"))
    }

    /** Growth across a loss isn't a percentage: unavailable when the prior fiscal-year value was ≤ 0. */
    private fun turnaroundSafe(fact: MetricValue?, fundamentals: CompanyFundamentals?, get: (FinancialPeriodStatement) -> Double?): MetricValue {
        val years = fundamentals?.history?.filter { it.period == "FY" }.orEmpty()
        val prior = years.getOrNull(1)?.let(get)
        if (prior != null && prior <= 0) return MetricValue(null, FinancialAvailability.UNRELIABLE_COMPARISON, fact?.basis,
            "Prior fiscal year was zero or a loss, so growth isn't shown as a percentage.")
        return fact ?: MetricValue()
    }

    private fun fcfGrowth(fundamentals: CompanyFundamentals?): MetricValue {
        val years = fundamentals?.history?.filter { it.period == "FY" }.orEmpty()
        val latest = years.getOrNull(0); val prior = years.getOrNull(1)
        val basis = latest?.let { FinancialBasis("annual", it.date, it.fiscalYear, it.currency) }
        val current = latest?.freeCashFlow; val previous = prior?.freeCashFlow
        return when {
            current == null || previous == null -> MetricValue(null, FinancialAvailability.MISSING, basis)
            latest.currency != prior.currency -> MetricValue(null, FinancialAvailability.PERIOD_MISMATCH, basis, "Reporting currency changed between years.")
            previous <= 0 -> MetricValue(null, FinancialAvailability.UNRELIABLE_COMPARISON, basis, "Prior free cash flow was zero or negative.")
            else -> MetricValue((current - previous) / previous * 100, FinancialAvailability.AVAILABLE, basis)
        }
    }

    /** Fiscal-year revenue, net income and free cash flow (newest first) for comparison charts. */
    fun annualFigures(fundamentals: CompanyFundamentals?): List<AnnualFigures> = fundamentals?.history.orEmpty()
        .filter { it.period == "FY" && it.fiscalYear != null }.take(5)
        .map { AnnualFigures(it.fiscalYear!!, it.date, it.currency, it.revenue, it.netIncome, it.freeCashFlow) }
}

class ScreenerValidationException(message: String) : IllegalArgumentException(message)

/** Server-side evaluation over the whole defined universe: filter → sort → page. */
object ScreenerEngine {
    const val MAX_PAGE_SIZE = 50
    const val MAX_RANGES = 15

    fun validate(query: ScreenerQuery) {
        if (query.pageSize !in 1..MAX_PAGE_SIZE) throw ScreenerValidationException("Page size must be between 1 and $MAX_PAGE_SIZE.")
        if (query.ranges.size > MAX_RANGES) throw ScreenerValidationException("Use at most $MAX_RANGES filters.")
        if (query.presetId != null && ScreenerDefinitions.preset(query.presetId) == null) throw ScreenerValidationException("Unknown preset.")
        for (range in query.ranges) {
            val definition = ScreenerDefinitions.metric(range.metric) ?: throw ScreenerValidationException("Unsupported filter: ${range.metric}.")
            if (!definition.filterable) throw ScreenerValidationException("${definition.label} can't be used as a filter.")
            if (listOfNotNull(range.min, range.max).any { !it.isFinite() }) throw ScreenerValidationException("Filter values must be numbers.")
            if (range.min != null && range.max != null && range.min > range.max) throw ScreenerValidationException("${definition.label}: minimum is above maximum.")
        }
        if (query.ranges.groupBy { it.metric }.any { it.value.size > 1 }) throw ScreenerValidationException("Each metric can be filtered once.")
        for (choice in query.choices) if (choice.values.size > 25 || choice.values.any { it.length > 80 }) throw ScreenerValidationException("Too many ${choice.field.label} values.")
        if (query.sort.field == SortField.METRIC && ScreenerDefinitions.metric(query.sort.metric ?: "") == null) throw ScreenerValidationException("Unsupported sort metric.")
    }

    data class Evaluation(val matches: List<ScreenerResultRow>, val excludedForMissingData: Int)

    fun evaluate(records: List<CompanyRecord>, query: ScreenerQuery): Evaluation {
        validate(query)
        var excluded = 0
        val matches = mutableListOf<ScreenerResultRow>()
        record@ for (record in records) {
            for (choice in query.choices.filter { it.values.isNotEmpty() }) {
                val actual = when (choice.field) {
                    ChoiceField.COUNTRY -> record.country; ChoiceField.EXCHANGE -> record.exchange
                    ChoiceField.SECTOR -> record.sector; ChoiceField.INDUSTRY -> record.industry
                    ChoiceField.SECURITY_TYPE -> record.securityType
                }
                if (actual == null || choice.values.none { it.equals(actual, ignoreCase = true) }) continue@record
            }
            val notApplied = mutableListOf<String>()
            var missing = false
            for (range in query.ranges.filter { it.min != null || it.max != null }) {
                val definition = ScreenerDefinitions.metric(range.metric)!!
                if (record.sector != null && record.sector in definition.notApplicableSectors) {
                    notApplied += "${definition.label} isn't applied to ${record.sector.lowercase()} companies."
                    continue
                }
                val value = record.metrics[range.metric]?.takeIf { it.availability == FinancialAvailability.AVAILABLE || it.availability == FinancialAvailability.NO_DIVIDEND }?.value
                if (value == null || !value.isFinite()) { missing = true; continue }
                if (range.min != null && value < range.min || range.max != null && value > range.max) continue@record
            }
            if (missing) { excluded++; continue }
            matches += ScreenerResultRow(record, notApplied)
        }
        return Evaluation(sort(matches, query.sort), excluded)
    }

    /** Missing values always sort last; ties break by symbol so pages are stable. */
    fun sort(rows: List<ScreenerResultRow>, sort: ScreenerSort): List<ScreenerResultRow> {
        fun key(row: ScreenerResultRow): Double? = when (sort.field) {
            SortField.MARKET_CAP -> row.company.metrics["marketCap"]?.value
            SortField.CHANGE_PERCENT -> row.company.changePercent
            SortField.METRIC -> row.company.metrics[sort.metric]?.takeIf { it.availability == FinancialAvailability.AVAILABLE || it.availability == FinancialAvailability.NO_DIVIDEND }?.value
            SortField.NAME -> null
        }
        if (sort.field == SortField.NAME) {
            val byName = rows.sortedWith(compareBy<ScreenerResultRow> { it.company.name.lowercase() }.thenBy { it.company.symbol })
            return if (sort.descending) byName.reversed() else byName
        }
        val (present, absent) = rows.partition { key(it)?.isFinite() == true }
        val ordered = present.sortedWith(compareBy<ScreenerResultRow> { key(it)!! * if (sort.descending) -1 else 1 }.thenBy { it.company.symbol })
        return ordered + absent.sortedBy { it.company.symbol }
    }

    /** Cursor = "offset.fingerprint"; a cursor from a different query is rejected. */
    fun page(sorted: List<ScreenerResultRow>, query: ScreenerQuery): Pair<List<ScreenerResultRow>, String?> {
        val fingerprint = fingerprint(query)
        val offset = query.cursor?.let { cursor ->
            val parts = cursor.split('.')
            if (parts.size != 2 || parts[1] != fingerprint) throw ScreenerValidationException("The results changed; start the search again.")
            parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: throw ScreenerValidationException("Invalid cursor.")
        } ?: 0
        val rows = sorted.drop(offset).take(query.pageSize)
        val next = (offset + rows.size).takeIf { it < sorted.size && rows.isNotEmpty() }?.let { "$it.$fingerprint" }
        return rows to next
    }

    fun fingerprint(query: ScreenerQuery): String = query.definition().copy(pageSize = 0).hashCode().toUInt().toString(36)
}

/** Deterministic comparison observations: only compatible metrics, never a winner or advice. */
object ComparisonEngine {
    private data class Rule(val metric: String, val higher: String, val lower: String, val caveat: String? = null, val noun: String = "")
    private val rules = listOf(
        Rule("operatingMargin", "a higher operating margin", "a lower operating margin", noun = "operating margin"),
        Rule("netMargin", "a higher net margin", "a lower net margin", noun = "net margin"),
        Rule("revenueGrowth", "faster revenue growth", "slower revenue growth", "Past growth doesn't indicate future growth.", "revenue growth"),
        Rule("pe", "a higher trailing P/E", "a lower trailing P/E", "A lower P/E isn't proof that a stock is undervalued.", "trailing P/E"),
        Rule("debtEquity", "higher debt relative to equity", "lower debt relative to equity", noun = "debt / equity"),
        Rule("dividendYield", "a higher dividend yield", "a lower dividend yield", "Dividends can change and aren't guaranteed.", "dividend yield")
    )
    /** Period ends further apart than this are called out as different reporting periods. */
    const val PERIOD_TOLERANCE_DAYS = 120

    fun observations(companies: List<CompanyRecord>): List<ComparisonObservation> {
        if (companies.size < 2) return emptyList()
        val result = mutableListOf<ComparisonObservation>()
        for (rule in rules) {
            val definition = ScreenerDefinitions.metric(rule.metric) ?: continue
            val values = companies.filter { c -> c.sector == null || c.sector !in definition.notApplicableSectors }
                .mapNotNull { c -> c.metrics[rule.metric]?.takeIf { it.availability == FinancialAvailability.AVAILABLE && it.value?.isFinite() == true }?.let { c to it } }
            if (values.size < 2) continue
            if (values.map { it.second.basis?.period }.distinct().size > 1) continue // e.g. TTM vs annual: not comparable
            val dates = values.mapNotNull { it.second.basis?.date?.let { d -> MarketsPresenter.dayNumber(d.take(10)) } }
            val mismatch = dates.size == values.size && dates.max() - dates.min() > PERIOD_TOLERANCE_DAYS
            val periodCaveat = if (mismatch) "Reporting periods end on different dates (${values.joinToString { "${it.first.symbol} ${it.second.basis?.date}" }})." else null
            val sorted = values.sortedByDescending { it.second.value!! }
            val label = rule.noun
            val text = if (values.size == 2) {
                val (a, b) = sorted
                if (a.second.value == b.second.value) "${a.first.name} and ${b.first.name} have the same $label (${format(a.second.value!!, definition.unit)})."
                else "${a.first.name} has ${rule.higher} than ${b.first.name} (${format(a.second.value!!, definition.unit)} vs ${format(b.second.value!!, definition.unit)})."
            } else {
                val top = sorted.first(); val bottom = sorted.last()
                if (top.second.value == bottom.second.value) "All ${values.size} companies have the same $label."
                else "Among these ${values.size} companies, ${rule.noun} ranges from ${format(bottom.second.value!!, definition.unit)} (${bottom.first.name}) to ${format(top.second.value!!, definition.unit)} (${top.first.name})."
            }
            result += ComparisonObservation(rule.metric, text, listOfNotNull(periodCaveat, rule.caveat).joinToString(" ").ifBlank { null })
        }
        // Sign-only statements for money metrics: amounts in different currencies aren't compared.
        val fcf = companies.mapNotNull { c -> c.metrics["freeCashFlow"]?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.value?.let { c to it } }
        if (fcf.size == companies.size && fcf.size >= 2) {
            val positive = fcf.filter { it.second > 0 }
            result += ComparisonObservation("freeCashFlow", when (positive.size) {
                fcf.size -> if (fcf.size == 2) "Both companies report positive free cash flow." else "All ${fcf.size} companies report positive free cash flow."
                0 -> "None of these companies reports positive free cash flow."
                else -> "${positive.joinToString { it.first.name }} report${if (positive.size == 1) "s" else ""} positive free cash flow; ${fcf.filter { it.second <= 0 }.joinToString { it.first.name }} ${if (fcf.size - positive.size == 1) "doesn't" else "don't"}."
            })
        }
        return result
    }

    fun format(value: Double, unit: MetricUnit): String = when (unit) {
        MetricUnit.PERCENT -> "${oneDecimal(value)}%"
        MetricUnit.MULTIPLE -> "${oneDecimal(value)}×"
        MetricUnit.RATIO -> twoDecimals(value)
        else -> twoDecimals(value)
    }
    private fun oneDecimal(v: Double) = (kotlin.math.round(v * 10) / 10).let { if (it == kotlin.math.floor(it)) "${it.toLong()}.0" else it.toString() }
    private fun twoDecimals(v: Double): String {
        val cents = kotlin.math.round(abs(v) * 100).toLong()
        return (if (v < 0 && cents != 0L) "-" else "") + "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }
}

/** A share split: on [date], each share became [ratio] shares (2.0 for a 2-for-1). */
data class Split(val date: String, val ratio: Double)

/**
 * Normalized price performance: each company's closes rebased to 100 at the period start, on a
 * shared date axis. Prices are compared as returns, never as raw share prices.
 */
object PerformanceNormalizer {
    const val CARRY_DAYS = 5
    /** The first close must be within this many days of the period start, else history doesn't cover it. */
    const val START_TOLERANCE_DAYS = 7

    /**
     * [closes] are (date, close) per symbol. When a series is not split-adjusted, pass its
     * [splits]: closes before each split are divided by the ratio so a split never looks like a crash.
     */
    fun normalize(
        closes: Map<String, List<Pair<String, Double>>>,
        start: String,
        end: String,
        splits: Map<String, List<Split>> = emptyMap(),
        currencies: Map<String, String?> = emptyMap()
    ): Pair<List<String>, List<PerformanceSeries>> {
        val day = { d: String -> MarketsPresenter.dayNumber(d.take(10)) ?: 0 }
        val adjusted = closes.mapValues { (symbol, points) ->
            val symbolSplits = splits[symbol].orEmpty()
            points.filter { it.second.isFinite() && it.second > 0 && it.first.take(10) >= start && it.first.take(10) <= end }
                .sortedBy { it.first }
                .map { (date, close) -> date.take(10) to symbolSplits.filter { date.take(10) < it.date && it.ratio > 0 }.fold(close) { c, s -> c / s.ratio } }
        }
        val dates = adjusted.values.flatten().map { it.first }.distinct().sorted()
        val series = closes.keys.map { symbol ->
            val points = adjusted.getValue(symbol)
            val first = points.firstOrNull()
            if (first == null || day(first.first) - day(start) > START_TOLERANCE_DAYS) {
                PerformanceSeries(symbol, dates.map { null }, null, currencies[symbol], "Price history doesn't cover this period.")
            } else {
                val base = first.second
                var cursor = 0
                var last: Pair<String, Double>? = null
                val values = dates.map { date ->
                    while (cursor < points.size && points[cursor].first <= date) { last = points[cursor]; cursor++ }
                    last?.takeIf { day(date) - day(it.first) <= CARRY_DAYS }?.let { it.second / base * 100 }
                }
                val endValue = points.last().second
                PerformanceSeries(symbol, values, (endValue / base - 1) * 100, currencies[symbol])
            }
        }
        return dates to series
    }
}

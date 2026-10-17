package org.example.stocksteps.marketdata.sec

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.example.stocksteps.marketdata.CompanyFinancialHistory
import org.example.stocksteps.marketdata.Dataset
import org.example.stocksteps.marketdata.FactIssue
import org.example.stocksteps.marketdata.FinancialConcept
import org.example.stocksteps.marketdata.FinancialPeriod
import org.example.stocksteps.marketdata.Provenance
import org.example.stocksteps.marketdata.ReportedValue
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One XBRL fact from SEC `companyfacts` (`facts.<taxonomy>.<tag>.units.<unit>[]`). [fy]/[fp]/[form]/[filed]/[accession] describe the
 * **filing** that reported it, not necessarily the fact's own period: a 10-K for fiscal 2025 also carries fiscal 2024 and 2023
 * comparatives with `fy = 2025`. Periods are therefore identified by [start]/[end] only.
 */
data class SecFact(
    val tag: String, val unit: String, val value: BigDecimal, val start: LocalDate?, val end: LocalDate,
    val fy: Int?, val fp: String?, val form: String, val filed: LocalDate, val accession: String, val frame: String?
) {
    val days: Long? get() = start?.let { ChronoUnit.DAYS.between(it, end) + 1 }
}

/** The parsed facts StockSteps uses (only [SecConcepts.TAGS]); [skipped] counts malformed or unsupported entries that were ignored. */
data class SecCompanyFacts(val cik: Long?, val entityName: String?, val facts: List<SecFact>, val skipped: Int)

/** us-gaap / dei tags per concept, in priority order (the first tag wins only among facts of the same latest filing). */
object SecConcepts {
    val MAP: Map<FinancialConcept, List<String>> = linkedMapOf(
        FinancialConcept.REVENUE to listOf("us-gaap:Revenues", "us-gaap:RevenueFromContractWithCustomerExcludingAssessedTax",
            "us-gaap:RevenueFromContractWithCustomerIncludingAssessedTax", "us-gaap:SalesRevenueNet"),
        FinancialConcept.COST_OF_REVENUE to listOf("us-gaap:CostOfRevenue", "us-gaap:CostOfGoodsAndServicesSold"),
        FinancialConcept.GROSS_PROFIT to listOf("us-gaap:GrossProfit"),
        FinancialConcept.OPERATING_INCOME to listOf("us-gaap:OperatingIncomeLoss"),
        // Attributable to the parent only (ProfitLoss includes non-controlling interests: not comparable, never a fallback).
        FinancialConcept.NET_INCOME to listOf("us-gaap:NetIncomeLoss"),
        FinancialConcept.EPS_BASIC to listOf("us-gaap:EarningsPerShareBasic"),
        FinancialConcept.EPS_DILUTED to listOf("us-gaap:EarningsPerShareDiluted"),
        FinancialConcept.DILUTED_SHARES to listOf("us-gaap:WeightedAverageNumberOfDilutedSharesOutstanding"),
        FinancialConcept.OPERATING_CASH_FLOW to listOf("us-gaap:NetCashProvidedByUsedInOperatingActivities"),
        FinancialConcept.CAPITAL_EXPENDITURE to listOf("us-gaap:PaymentsToAcquirePropertyPlantAndEquipment"),
        FinancialConcept.DIVIDENDS_PER_SHARE to listOf("us-gaap:CommonStockDividendsPerShareDeclared"),
        FinancialConcept.TOTAL_ASSETS to listOf("us-gaap:Assets"),
        FinancialConcept.TOTAL_LIABILITIES to listOf("us-gaap:Liabilities"),
        FinancialConcept.EQUITY to listOf("us-gaap:StockholdersEquity"),
        FinancialConcept.CURRENT_ASSETS to listOf("us-gaap:AssetsCurrent"),
        FinancialConcept.CURRENT_LIABILITIES to listOf("us-gaap:LiabilitiesCurrent"),
        FinancialConcept.CASH to listOf("us-gaap:CashAndCashEquivalentsAtCarryingValue"),
        FinancialConcept.LONG_TERM_DEBT to listOf("us-gaap:LongTermDebt"),
        FinancialConcept.LONG_TERM_DEBT_CURRENT to listOf("us-gaap:LongTermDebtCurrent"),
        FinancialConcept.LONG_TERM_DEBT_NONCURRENT to listOf("us-gaap:LongTermDebtNoncurrent"),
        FinancialConcept.SHORT_TERM_BORROWINGS to listOf("us-gaap:ShortTermBorrowings"),
        FinancialConcept.SHARES_OUTSTANDING to listOf("dei:EntityCommonStockSharesOutstanding")
    )
    val TAGS: Set<String> = MAP.values.flatten().toSet()
    /** US domestic periodic reports only (foreign private issuers file 20-F/40-F, usually IFRS: unsupported here, never guessed). */
    val FORMS = setOf("10-K", "10-K/A", "10-Q", "10-Q/A")
}

object SecCompanyFactsParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val CURRENCY = Regex("^[A-Z]{3}$")
    private val PER_SHARE = Regex("^[A-Z]{3}/shares$")

    /** Parses a companyfacts document. Throws [IllegalArgumentException] when it isn't a companyfacts object at all. */
    fun parse(text: String, maxFacts: Int = 250_000): SecCompanyFacts {
        val root = json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("not a companyfacts object")
        val facts = root["facts"] as? JsonObject ?: throw IllegalArgumentException("not a companyfacts object")
        val out = ArrayList<SecFact>()
        var skipped = 0
        for ((taxonomy, tags) in facts) {
            if (tags !is JsonObject) continue
            for ((tag, body) in tags) {
                val qualified = "$taxonomy:$tag"
                if (qualified !in SecConcepts.TAGS) continue
                val units = (body as? JsonObject)?.get("units") as? JsonObject ?: continue
                for ((unit, rows) in units) {
                    if (!(CURRENCY.matches(unit) || PER_SHARE.matches(unit) || unit == "shares")) { skipped += (rows as? JsonArray)?.size ?: 0; continue }
                    for (row in rows as? JsonArray ?: continue) {
                        val fact = (row as? JsonObject)?.let { fact(qualified, unit, it) }
                        if (fact == null || fact.form !in SecConcepts.FORMS) { skipped++; continue }
                        if (out.size >= maxFacts) { skipped++; continue }
                        out += fact
                    }
                }
            }
        }
        return SecCompanyFacts((root["cik"] as? JsonPrimitive)?.longOrNull, (root["entityName"] as? JsonPrimitive)?.contentOrNull, out, skipped)
    }

    private fun fact(tag: String, unit: String, o: JsonObject): SecFact? {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString || k == "val" }?.contentOrNull
        fun d(k: String) = s(k)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val value = (o["val"] as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull?.toBigDecimalOrNull() ?: return null
        val end = d("end") ?: return null
        val start = if (o["start"] == null) null else d("start") ?: return null
        if (start != null && start > end) return null
        return SecFact(tag, unit, value, start, end, (o["fy"] as? JsonPrimitive)?.intOrNull, s("fp"), s("form") ?: return null,
            d("filed") ?: return null, s("accn")?.takeIf { it.isNotBlank() } ?: return null, s("frame"))
    }
}

/** Which value to use when later filings report a different value for the same period. */
enum class RestatementPolicy { LATEST_FILED, ORIGINAL }

/**
 * Builds fiscal periods from SEC facts.
 *
 * - **Periods** by start/end only: annual = 350–380 days (52/53-week years), quarter = 84–98 days (13/14 weeks). Year-to-date facts
 *   (6 and 9 months) are never used as quarters.
 * - **Fiscal labels** from the *earliest* filing that reported the period (later comparatives carry the later filing's `fy`).
 * - **Tag choice** per period: among the candidate tags, the facts of the latest filing reporting that period, then tag priority — so a
 *   filing's comparatives stay on one tag. The tag used is kept in [ReportedValue.concept] (growth across a tag change is flagged).
 * - **Restatements**: [RestatementPolicy.LATEST_FILED] (default) uses the most recent filing's value; the first-filed value is kept as
 *   [ReportedValue.originalValue]; amendments (`/A`) are flagged. Two different values for one period in the same filing → AMBIGUOUS.
 * - **Currency**: the company's reporting currency is the unit of most of its revenue/net-income facts; other currencies are excluded.
 * - **Q4** for summable flows only: FY − the 9-month year-to-date value of the same fiscal year (same tag), marked derived. Per-share
 *   values and share counts are never derived (Q4 EPS stays missing unless reported).
 * - **Shares outstanding** (cover page, `dei`): several different values on one date = several share classes → AMBIGUOUS.
 */
object SecPeriodBuilder {
    private const val PROVIDER = "sec"

    fun build(symbol: String, facts: SecCompanyFacts, retrievedAt: Instant, policy: RestatementPolicy = RestatementPolicy.LATEST_FILED,
              maxAnnual: Int = 12, maxQuarters: Int = 40): CompanyFinancialHistory {
        val currency = reportingCurrency(facts.facts)
        val usable = facts.facts.filter { f -> f.unit == "shares" || (currency != null && (f.unit == currency || f.unit == "$currency/shares")) }
        val tagConcept = SecConcepts.MAP.flatMap { (c, tags) -> tags.map { it to c } }.toMap()
        val byConcept = usable.groupBy { tagConcept.getValue(it.tag) }

        // Fiscal labels per duration period. A filing's own period ends at the latest end of its us-gaap duration facts (cover-page dates
        // excluded). The label comes from the earliest filing whose own period this is; a period only ever seen as a comparative takes
        // the filing's fy less the whole years between the two period ends (its fp is the comparative's position, e.g. Q2).
        val ownEnd: Map<String, LocalDate> = usable.filter { it.start != null && it.tag.startsWith("us-gaap:") }
            .groupBy { it.accession }.mapValues { (_, fs) -> fs.maxOf { it.end } }
        val labels = HashMap<Pair<LocalDate, LocalDate>, Pair<Int?, String?>>()
        usable.filter { it.start != null }.groupBy { it.start!! to it.end }.forEach { (k, fs) ->
            val own = fs.filter { ownEnd[it.accession] == k.second }.minByOrNull { it.filed }
            labels[k] = if (own != null) own.fy to own.fp else fs.minBy { it.filed }.let { first ->
                val years = ownEnd[first.accession]?.let { Math.round(ChronoUnit.DAYS.between(k.second, it) / 365.25).toInt() } ?: 0
                first.fy?.minus(years) to first.fp
            }
        }

        val annualKeys = labels.keys.filter { ChronoUnit.DAYS.between(it.first, it.second) + 1 in 350..380 }
        val quarterKeys = labels.keys.filter { ChronoUnit.DAYS.between(it.first, it.second) + 1 in 84..98 }

        fun resolve(c: FinancialConcept, start: LocalDate?, end: LocalDate): Pair<ReportedValue?, FactIssue?> {
            val candidates = byConcept[c].orEmpty().filter { it.end == end && (if (c.instant) it.start == null else it.start == start) }
            if (candidates.isEmpty()) return null to null
            val tags = SecConcepts.MAP.getValue(c)
            val latestFiled = candidates.maxOf { it.filed }
            val tag = candidates.filter { it.filed == latestFiled }.minBy { tags.indexOf(it.tag) }.tag
            val sameTag = candidates.filter { it.tag == tag }.sortedWith(compareBy({ it.filed }, { it.accession }))
            // One filing reporting two values for one period (no dimensions in companyfacts): ambiguous, e.g. several share classes.
            if (sameTag.groupBy { it.accession }.values.any { fs -> fs.map { it.value.stripTrailingZeros() }.distinct().size > 1 }) return null to FactIssue.AMBIGUOUS
            val chosen = if (policy == RestatementPolicy.LATEST_FILED) sameTag.last() else sameTag.first()
            val original = sameTag.first()
            return ReportedValue(chosen.value, chosen.unit, chosen.tag, PROVIDER, chosen.accession, chosen.filed, chosen.form,
                originalValue = original.value.takeIf { original !== chosen }, amended = chosen.form.endsWith("/A")) to null
        }

        fun period(key: Pair<LocalDate, LocalDate>, label: String, fy: Int?): FinancialPeriod {
            val values = HashMap<FinancialConcept, ReportedValue>(); val issues = HashMap<FinancialConcept, FactIssue>()
            for (c in FinancialConcept.entries) {
                if (c == FinancialConcept.SHARES_OUTSTANDING) continue
                val (v, issue) = resolve(c, key.first, key.second)
                v?.let { values[c] = it }; issue?.let { issues[c] = it }
            }
            return FinancialPeriod(fy, label, key.first, key.second, currency, values, issues)
        }

        val annual = annualKeys.sortedByDescending { it.second }.take(maxAnnual).map { k -> period(k, "FY", labels[k]?.first) }
        val quarters = quarterKeys.sortedByDescending { it.second }.take(maxQuarters).map { k ->
            val (fy, fp) = labels.getValue(k)
            period(k, fp?.takeIf { it in setOf("Q1", "Q2", "Q3") } ?: "Q4", fy)
        }.toMutableList()

        // Derived Q4 for flows where no discrete fourth quarter was reported.
        for (year in annual) {
            val start = year.start ?: continue
            val ytd9 = labels.keys.filter { (s, e) -> s == start && (ChronoUnit.DAYS.between(s, e) + 1) in 266..280 }.maxByOrNull { it.second } ?: continue
            val q4Start = ytd9.second.plusDays(1)
            if (ChronoUnit.DAYS.between(q4Start, year.end) + 1 !in 84..98) continue
            val existing = quarters.indexOfFirst { it.end == year.end && it.start == q4Start }
            val base = if (existing >= 0) quarters[existing] else FinancialPeriod(year.fiscalYear, "Q4", q4Start, year.end, currency, emptyMap())
            val derived = HashMap(base.values)
            for (c in FinancialConcept.entries.filter { it.summable && it !in base.values }) {
                val fyValue = year[c] ?: continue
                val (nine, issue) = resolve(c, ytd9.first, ytd9.second)
                if (nine == null || issue != null || nine.concept != fyValue.concept) continue
                derived[c] = ReportedValue(fyValue.value - nine.value, fyValue.unit, fyValue.concept, PROVIDER,
                    "${fyValue.sourceRef} − ${nine.sourceRef}", fyValue.filed, fyValue.form, derived = true)
            }
            // Balance-sheet values at year end belong to Q4 too.
            for (c in FinancialConcept.entries.filter { it.instant && it != FinancialConcept.SHARES_OUTSTANDING && it !in derived }) year[c]?.let { derived[c] = it }
            val q4 = base.copy(fiscalPeriod = "Q4", fiscalYear = base.fiscalYear ?: year.fiscalYear, values = derived)
            if (existing >= 0) quarters[existing] = q4 else if (derived.isNotEmpty()) quarters += q4
        }

        // Cover-page shares outstanding, newest first.
        val shareDates = byConcept[FinancialConcept.SHARES_OUTSTANDING].orEmpty().map { it.end }.distinct().sortedDescending()
            .map { end -> end to resolve(FinancialConcept.SHARES_OUTSTANDING, null, end) }
        val shares = shareDates.mapNotNull { (end, r) -> r.first?.let { end to it } }
        // Only the newest cover date matters for market value: if it lists several classes, the count is ambiguous (never summed).
        val sharesIssue = shareDates.firstOrNull()?.second?.second
        val asOf = (annual.map { it.end } + quarters.map { it.end }).maxOrNull()?.toString()
        val latestFiling = usable.maxByOrNull { it.filed }?.accession
        return CompanyFinancialHistory(symbol, annual, quarters.sortedByDescending { it.end },
            Provenance(PROVIDER, Dataset.REPORTED_FUNDAMENTALS, retrievedAt, asOf, latestFiling), shares, sharesIssue)
    }

    private fun reportingCurrency(facts: List<SecFact>): String? = facts
        .filter { it.tag in SecConcepts.MAP.getValue(FinancialConcept.REVENUE) + SecConcepts.MAP.getValue(FinancialConcept.NET_INCOME) && it.unit.length == 3 }
        .groupingBy { it.unit }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key })).firstOrNull()?.key
}

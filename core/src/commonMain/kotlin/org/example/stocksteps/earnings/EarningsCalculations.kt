package org.example.stocksteps.earnings

import org.example.stocksteps.companydetail.MetricEducation
import org.example.stocksteps.model.EarningsDateStatus
import kotlin.math.abs
import kotlin.math.round

/**
 * Earnings surprise calculations. Pure and deterministic; the server and apps share them.
 *
 * Classification policy (shared with Earnings Results, [EarningsMath]): actual and estimate are
 * compared exactly as reported, as decimals — MET only when equal, otherwise BEAT or MISS. There is
 * no percentage tolerance.
 * - EPS percent surprise = (actual − estimate) / |estimate| × 100, shown only when |estimate| ≥ $0.01;
 *   a negative estimate still uses |estimate|, so "above estimate" is always positive.
 * - Revenue percent surprise = (actual − estimate) / estimate × 100, only for a positive estimate.
 * - Nothing is compared across different currencies, fiscal periods, period lengths or EPS bases.
 */
object EarningsCalculator {
    const val MIN_EPS_FOR_PERCENT = 0.01

    fun eps(estimate: EarningsEstimate?, actual: EarningsActual?): SurpriseResult {
        val a = actual?.eps?.takeIf { it.isFinite() }
        val e = estimate?.eps?.takeIf { it.isFinite() }
        val currency = actual?.currency ?: estimate?.currency
        val basis = actual?.epsBasis?.takeIf { it != EpsBasis.UNKNOWN }?.label ?: estimate?.epsBasis?.takeIf { it != EpsBasis.UNKNOWN }?.label
        fun unavailable(reason: String) = SurpriseResult(a, e, null, null, Classification.UNAVAILABLE, reason, currency, basis)
        if (a == null && e == null) return unavailable("Neither reported EPS nor an estimate is available.")
        if (a == null) return unavailable("Reported EPS isn't available yet.")
        if (e == null) return unavailable("No analyst EPS estimate is available, so there's nothing to compare with.")
        if (estimate.periodType != PeriodType.QUARTER) return unavailable("The estimate covers a full year, so it isn't compared with a quarter.")
        if (estimate.currency != null && actual.currency != null && estimate.currency != actual.currency)
            return unavailable("The estimate (${estimate.currency}) and the result (${actual.currency}) are in different currencies.")
        if (estimate.epsBasis == EpsBasis.UNKNOWN || actual.epsBasis == EpsBasis.UNKNOWN || estimate.epsBasis != actual.epsBasis)
            return unavailable("The estimate (${estimate.epsBasis.label}) and the result (${actual.epsBasis.label}) aren't measured the same way.")
        val da = EarningsMath.decimal(a)!!; val de = EarningsMath.decimal(e)!!
        val diff = (da - de).toString().toDouble()
        val percent = if (abs(e) >= MIN_EPS_FOR_PERCENT) EarningsMath.percent(da, de)?.toString()?.toDouble() else null
        return SurpriseResult(a, e, diff, percent, EarningsMath.classify(da, de),
            if (percent == null) "The estimate was close to zero, so a percentage surprise isn't meaningful." else null, currency, basis)
    }

    fun revenue(estimate: EarningsEstimate?, actual: EarningsActual?): SurpriseResult {
        val a = actual?.revenue?.takeIf { it.isFinite() }
        val e = estimate?.revenue?.takeIf { it.isFinite() }
        val currency = actual?.currency ?: estimate?.currency
        fun unavailable(reason: String) = SurpriseResult(a, e, null, null, Classification.UNAVAILABLE, reason, currency)
        if (a == null && e == null) return unavailable("Neither reported revenue nor an estimate is available.")
        if (a == null) return unavailable("Reported revenue isn't available yet.")
        if (e == null) return unavailable("No analyst revenue estimate is available, so there's nothing to compare with.")
        if (estimate.periodType != PeriodType.QUARTER) return unavailable("The estimate covers a full year, so it isn't compared with a quarter.")
        if (estimate.currency != null && actual.currency != null && estimate.currency != actual.currency)
            return unavailable("The estimate (${estimate.currency}) and the result (${actual.currency}) are in different currencies.")
        if (e <= 0) return unavailable("The revenue estimate isn't positive, so a surprise can't be calculated.")
        val da = EarningsMath.decimal(a)!!; val de = EarningsMath.decimal(e)!!
        return SurpriseResult(a, e, (da - de).toString().toDouble(), EarningsMath.percent(da, de)!!.toString().toDouble(), EarningsMath.classify(da, de), null, currency)
    }

    /** Growth between two reported revenues in the same currency; null when not comparable. */
    fun growth(current: EarningsEvent?, prior: EarningsEvent?): Double? {
        val c = current?.actual ?: return null
        val p = prior?.actual ?: return null
        val now = c.revenue?.takeIf { it.isFinite() } ?: return null
        val before = p.revenue?.takeIf { it.isFinite() && it > 0 } ?: return null
        if (c.currency != null && p.currency != null && c.currency != p.currency) return null
        return (now - before) / before * 100
    }

    /** Same fiscal quarter, prior fiscal year. */
    fun yearAgo(event: EarningsEvent, history: List<EarningsEvent>) =
        history.firstOrNull { it.fiscalQuarter == event.fiscalQuarter && it.fiscalYear == event.fiscalYear - 1 }

    /** The immediately preceding fiscal quarter. */
    fun previousQuarter(event: EarningsEvent, history: List<EarningsEvent>): EarningsEvent? {
        val (year, quarter) = if (event.fiscalQuarter == 1) event.fiscalYear - 1 to 4 else event.fiscalYear to event.fiscalQuarter - 1
        return history.firstOrNull { it.fiscalYear == year && it.fiscalQuarter == quarter }
    }

    /**
     * Status from data, never from the clock alone: a past date without results is DATA_PENDING,
     * not REPORTED.
     */
    fun status(event: EarningsEvent?, today: String): EarningsStatus {
        event ?: return EarningsStatus.UNAVAILABLE
        val actual = event.actual
        val hasEps = actual?.eps != null
        val hasRevenue = actual?.revenue != null
        return when {
            hasEps && hasRevenue -> EarningsStatus.REPORTED
            hasEps || hasRevenue -> EarningsStatus.PARTIALLY_REPORTED
            event.dateStatus == EarningsDateStatus.UNKNOWN -> EarningsStatus.UNAVAILABLE
            event.date >= today -> EarningsStatus.UPCOMING
            else -> EarningsStatus.DATA_PENDING
        }
    }

    /** "EPS beat, revenue miss" plus a plain explanation built from the classifications. */
    fun summary(eps: SurpriseResult, revenue: SurpriseResult): Pair<String, String>? {
        if (eps.classification == Classification.UNAVAILABLE && revenue.classification == Classification.UNAVAILABLE) return null
        fun word(c: Classification) = when (c) { Classification.BEAT -> "beat"; Classification.MISS -> "miss"; Classification.MET -> "met"; Classification.UNAVAILABLE -> "not comparable" }
        fun phrase(metric: String, c: Classification) = when (c) {
            Classification.BEAT -> "$metric above analyst expectations"
            Classification.MISS -> "$metric below analyst expectations"
            Classification.MET -> "$metric matching analyst expectations"
            Classification.UNAVAILABLE -> null
        }
        val headline = "EPS ${word(eps.classification)}, revenue ${word(revenue.classification)}"
        val parts = listOfNotNull(phrase("earnings per share", eps.classification), phrase("revenue", revenue.classification))
        val mixed = setOf(eps.classification, revenue.classification).containsAll(setOf(Classification.BEAT, Classification.MISS))
        val explanation = "The company reported " + parts.joinToString(if (mixed) ", while " else " and ") + "." +
            (if (mixed) " EPS and revenue can tell different stories: profit per share depends on costs and share count, revenue on sales." else "") +
            " Results alone don't determine how the stock moves."
        return headline to explanation
    }

    fun round2(value: Double) = round(value * 100) / 100
}

/** Deterministic insights: only from data that supports them; advanced ones are StockSteps+. */
object EarningsInsightEngine {
    fun generate(latest: EarningsEvent?, history: List<EarningsEvent>, asOf: String): List<EarningsInsight> {
        latest ?: return emptyList()
        val result = mutableListOf<EarningsInsight>()
        val eps = EarningsCalculator.eps(latest.estimate, latest.actual)
        val revenue = EarningsCalculator.revenue(latest.estimate, latest.actual)
        eps.percent?.takeIf { eps.classification == Classification.BEAT || eps.classification == Classification.MISS }?.let { p ->
            result += EarningsInsight("eps-surprise:${latest.id}", "surprise",
                "Reported EPS ${if (p > 0) "exceeded" else "fell short of"} the consensus estimate by ${fmt(abs(p))}%.",
                "EPS was ${money(eps.actual!!)} against an estimate of ${money(eps.estimate!!)} (${eps.basis ?: "same basis"}).",
                listOf("eps.actual", "eps.estimate"), latest.period, asOf, "surprise")
        }
        if (eps.classification in setOf(Classification.BEAT, Classification.MISS) && revenue.classification in setOf(Classification.BEAT, Classification.MISS) && eps.classification != revenue.classification) {
            result += EarningsInsight("mixed:${latest.id}", "mixed",
                "The company ${if (eps.classification == Classification.BEAT) "exceeded EPS expectations but missed revenue expectations" else "missed EPS expectations but exceeded revenue expectations"}.",
                "EPS and revenue can point in different directions; neither alone describes the quarter.",
                listOf("eps.classification", "revenue.classification"), latest.period, asOf, "beat")
        }
        EarningsCalculator.growth(latest, EarningsCalculator.yearAgo(latest, history))?.let { g ->
            result += EarningsInsight("revenue-yoy:${latest.id}", "growth",
                "Revenue ${if (g >= 0) "increased" else "decreased"} ${fmt(abs(g))}% compared with the same quarter last year.",
                "Year-over-year compares the same fiscal quarter, which avoids seasonal swings.",
                listOf("revenue.actual", "revenue.yearAgo"), latest.period, asOf, "yoy")
        }
        // Advanced: streaks need consecutive comparable reported quarters (no gaps, same basis).
        val ordered = (listOf(latest) + history.filter { it.id != latest.id }).filter { it.actual != null }
            .sortedWith(compareByDescending<EarningsEvent> { it.fiscalYear }.thenByDescending { it.fiscalQuarter })
        var streak = 0
        var previous: EarningsEvent? = null
        for (event in ordered) {
            if (previous != null && EarningsCalculator.previousQuarter(previous, listOf(event)) == null) break
            if (EarningsCalculator.eps(event.estimate, event.actual).classification != Classification.BEAT) break
            streak++
            previous = event
        }
        if (streak >= 3) result += EarningsInsight("eps-streak:${latest.id}", "trend",
            "This was the company's ${ordinal(streak)} consecutive reported EPS beat.",
            "Counted across consecutive fiscal quarters with comparable estimates. Past beats don't predict future results.",
            listOf("eps.history"), latest.period, asOf, "beat", advanced = true)
        // Advanced: acceleration needs three consecutive year-over-year growth rates.
        val q0 = latest; val q1 = EarningsCalculator.previousQuarter(q0, history)
        val g0 = EarningsCalculator.growth(q0, EarningsCalculator.yearAgo(q0, history))
        val g1 = q1?.let { EarningsCalculator.growth(it, EarningsCalculator.yearAgo(it, history)) }
        if (g0 != null && g1 != null && abs(g0 - g1) >= 1.0) result += EarningsInsight("revenue-trend:${latest.id}", "trend",
            "Year-over-year revenue growth ${if (g0 > g1) "accelerated" else "slowed"} from ${fmt(g1)}% to ${fmt(g0)}%.",
            "Compares this quarter's year-over-year growth with the previous quarter's.",
            listOf("revenue.yoy", "revenue.yoy.previous"), latest.period, asOf, "yoy", advanced = true)
        return result
    }

    private fun fmt(v: Double) = (round(v * 10) / 10).toString()
    private fun money(v: Double) = (if (v < 0) "−$" else "$") + (round(abs(v) * 100) / 100).toString().let { if (it.substringAfter('.').length == 1) it + "0" else it }
    private fun ordinal(n: Int) = when (n) { 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"; 6 -> "sixth"; 7 -> "seventh"; 8 -> "eighth"; else -> "${n}th" }
}

/** Beginner explanations for every earnings concept (free; no AI needed). Reuses MetricEducation where it exists. */
object EarningsEducation {
    data class Topic(val key: String, val title: String, val body: String)

    val topics: List<Topic> by lazy { listOf(
        Topic("quarterly", "What are quarterly earnings?", "Every three months, public companies report how much they sold, spent and earned in that quarter. The report also often includes the company's outlook."),
        Topic("eps", "What is EPS?", MetricEducation.find("eps")?.explanation ?: "Earnings per share: profit divided by the number of shares."),
        Topic("revenue", "What is revenue?", MetricEducation.find("revenue")?.explanation ?: "Money earned from selling products and services, before expenses."),
        Topic("estimates", "What are analyst estimates?", "Analysts who follow a company forecast its EPS and revenue. The average of those forecasts is the consensus estimate. Estimates can change right up to the report and can be wrong."),
        Topic("beat", "What is an earnings beat?", "A result above the consensus estimate. StockSteps compares the exact reported numbers: any amount above the estimate is a beat, and EPS and revenue are judged separately."),
        Topic("miss", "What is an earnings miss?", "A result below the consensus estimate. A miss doesn't mean the business is doing badly; expectations may simply have been high."),
        Topic("inline", "What does 'met expectations' mean?", "The reported number was exactly equal to the consensus estimate. StockSteps doesn't treat a small difference as a match, so even a one-cent difference is shown as a beat or a miss."),
        Topic("surprise", "What is earnings surprise?", "The difference between the result and the estimate. For EPS it's (actual − estimate) ÷ |estimate| × 100; it isn't shown when the estimate is close to zero, because the percentage would be meaningless."),
        Topic("fall-after-beat", "Why can a stock fall after beating earnings?", "Prices reflect expectations. Investors may have expected an even bigger beat, focused on weaker revenue or guidance, or reacted to other market news on the same day. A beat or miss doesn't determine the price move."),
        Topic("guidance", "What is forward guidance?", "A company's own forecast for future quarters. StockSteps only shows guidance when a reliable source provides it; it never invents it."),
        Topic("yoy", "What is year-over-year growth?", "Compares a quarter with the same quarter a year earlier. It avoids seasonal swings, such as holiday sales."),
        Topic("qoq", "What is quarter-over-quarter growth?", "Compares a quarter with the one just before it. It can be affected by seasonality, so it's shown only as context."),
        Topic("reaction", "How is the price reaction measured?", "StockSteps compares regular-session closing prices on trading days. Before-market announcements: the previous session's close → that day's close. After-market: that day's close → the next session's close. The 3- and 5-session windows end at later closes. If the time is unknown, a broader window is shown and labelled. The window shows how the price changed, not why."),
        Topic("expectations", "Earnings expectations explained", "Before a report, analysts publish estimates and investors form their own expectations. Prices often move on the difference between what was expected and what was reported, and on what the company says about the future."),
        Topic("after-hours", "What is after-hours trading?", "Some trading happens before the market opens and after it closes. Fewer people trade then, so prices can jump more and the gap between buy and sell prices is often wider. StockSteps uses regular-session closing prices unless reliable extended-hours data is available."),
        Topic("volatility", "What is market volatility?", "Volatility describes how much and how quickly prices move. Prices often move more than usual around earnings because new information arrives at once.")
    ) }

    fun topic(key: String) = topics.firstOrNull { it.key == key }
}

package org.example.stocksteps.presentation.portfolio

import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.PortfolioFormat
import org.example.stocksteps.portfolio.analytics.Availability
import org.example.stocksteps.portfolio.analytics.PortfolioInsight

/**
 * One Portfolio insight card (C.1 cleanup), shared by Compose and SwiftUI: the finding first ([headline]), one short [summary] sentence, and the
 * rest of the engine's explanation in [details] behind "Show details" (null when there is nothing more). Engine wording and order are kept.
 */
data class PortfolioInsightCard(val eyebrow: String, val headline: String, val summary: String, val details: String?)

object PortfolioInsightCardDisplay {
    const val SHOW_DETAILS = "Show details"
    const val HIDE_DETAILS = "Hide details"

    /** Insight metrics that hold amounts in the reporting currency (the engine writes them as plain "4128.79" in its text). */
    private val MONEY_METRICS = listOf("investmentGain", "netExternalFlows", "contribution", "fxEffect", "total", "reinvested")

    /** Openings of caution sentences that stay visible instead of moving into the details. */
    private val ALWAYS_SHOWN = listOf("Past ", "Future ")

    fun card(insight: PortfolioInsight, reportingCurrency: String): PortfolioInsightCard {
        val headline = withCurrency(insight.title, insight.metrics, reportingCurrency)
        val sentences = sentences(withCurrency(insight.explanation, insight.metrics, reportingCurrency))
        val shown = sentences.filterIndexed { i, s -> i == 0 || ALWAYS_SHOWN.any { s.startsWith(it) } }
        val rest = sentences - shown.toSet()
        val eyebrow = listOfNotNull(PortfolioInsightIcons.label(insight.category), insight.period?.label,
            if (insight.completeness != Availability.AVAILABLE) "Partial data" else null).joinToString(" · ")
        return PortfolioInsightCard(eyebrow, headline, shown.joinToString(" "), rest.takeIf { it.isNotEmpty() }?.joinToString(" "))
    }

    /**
     * Replaces each money metric's plain amount in [text] ("4128.79", as the engine renders `abs().display()`) with the presenter's reporting
     * currency and grouping ("CAD 4,128.79"). Only exact, whole-number matches are replaced; values and wording are unchanged otherwise, and a
     * missing currency leaves the text as it is (never guessed).
     */
    internal fun withCurrency(text: String, metrics: Map<String, String>, currency: String): String {
        if (currency.isBlank()) return text
        var result = text
        MONEY_METRICS.mapNotNull { metrics[it] }.mapNotNull { runCatching { Decimal.parse(it) }.getOrNull() }.forEach { value ->
            val plain = (if (value < Decimal.ZERO) -value else value).display()
            result = replaceWhole(result, plain, "$currency ${PortfolioFormat.amount(plain)}")
        }
        return result
    }

    private fun replaceWhole(text: String, plain: String, replacement: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val at = text.indexOf(plain, i)
            if (at < 0) break
            val end = at + plain.length
            val before = text.getOrNull(at - 1)
            val after = text.getOrNull(end)
            val whole = (before == null || !(before.isDigit() || before == '.' || before == ',' || before == ' ')) &&
                (after == null || !(after.isDigit() || (after == '.' && text.getOrNull(end + 1)?.isDigit() == true) || after == '%'))
            out.append(text, i, at).append(if (whole) replacement else plain)
            i = end
        }
        return out.append(text.substring(i)).toString()
    }

    /** Sentences end at ". " before a capital letter, so "8.33%" and "S&P/TSX" never split. */
    internal fun sentences(text: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        for (i in text.indices) {
            if ((text[i] == '.' || text[i] == '?' || text[i] == '!') && text.getOrNull(i + 1) == ' ' && text.getOrNull(i + 2)?.isUpperCase() == true) {
                parts += text.substring(start, i + 1).trim(); start = i + 2
            }
        }
        text.substring(start).trim().takeIf { it.isNotEmpty() }?.let { parts += it }
        return parts
    }
}

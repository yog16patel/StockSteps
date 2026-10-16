package org.example.stocksteps.presentation.companydetails

import org.example.stocksteps.companydetail.FinancialChart
import org.example.stocksteps.companydetail.FinancialStatementsModel
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.companydetail.SectionAvailability

/**
 * Financials page layout (Global UI Refinement 5D.5.4C, C1), shared by Compose and SwiftUI. Display only: it picks and orders what the shared
 * `FinancialStatementsModel` already holds — no value, period or sentence is computed or reworded here.
 */
object FinancialsDisplay {

    /**
     * KAN-16: the top-bar title, "PFAI Financials". The ticker keeps it short: "<company name> Financials" truncated long names to
     * "Pinnacle Food Group Limited Class A C…" and lost the page's own name. The company's name stays in the page header.
     */
    fun title(symbol: String): String = "$symbol Financials"

    /** One headline tile under the overview chart: the model's own label, value and change text. */
    data class KeyMetric(val label: String, val value: String, val caption: String?, val direction: PriceDirection?, val metricId: String?)

    /** In-page section bar entries (D-F1); each scrolls to the first of its model sections that the page shows. */
    enum class Group(val label: String, val sectionIds: List<String>) {
        INCOME("Income", listOf("revenue", "profitability", "eps")),
        BALANCE_SHEET("Balance Sheet", listOf("health")),
        CASH_FLOW("Cash Flow", listOf("cashFlow", "dividends"))
    }

    /** The groups the page shows, in order (never a tab that scrolls nowhere). */
    fun groups(model: FinancialStatementsModel?): List<Group> {
        val ids = model?.sections?.map { it.id }.orEmpty().toSet()
        return Group.entries.filter { group -> group.sectionIds.any { it in ids } }
    }

    /** The model section a group's tab scrolls to: its first section on the page. */
    fun anchorSection(model: FinancialStatementsModel, group: Group): String? =
        group.sectionIds.firstOrNull { id -> model.sections.any { it.id == id } }

    /** The group a section belongs to (for anchors). */
    fun groupOf(sectionId: String): Group? = Group.entries.firstOrNull { sectionId in it.sectionIds }

    /**
     * The overview chart at the top (D-F2): Profitability's revenue + net income bars when available, else Revenue's own bars; null when neither
     * section has a chart (the model only draws a chart with at least two values).
     */
    fun overviewChart(model: FinancialStatementsModel): FinancialChart? {
        val profitability = model.sections.firstOrNull { it.id == "profitability" && it.availability == SectionAvailability.CONTENT }?.chart
        return profitability ?: model.sections.firstOrNull { it.id == "revenue" && it.availability == SectionAvailability.CONTENT }?.chart
    }

    /** The section whose chart moved to the overview; the overview chart is not drawn again inside that section, nor Revenue's (D-F2). */
    fun chartInSection(model: FinancialStatementsModel, sectionId: String): Boolean {
        val top = overviewChart(model) ?: return true
        if (sectionId == "revenue") return false
        return model.sections.firstOrNull { it.id == sectionId }?.chart != top
    }

    /**
     * "Key Metrics (FY2025)": revenue (headline + its change), net income (with its change and the same comparison period), gross and operating
     * margin — each only when the model has it. Margins carry no change: the model has no margin history (D-F3).
     */
    fun keyMetrics(model: FinancialStatementsModel): List<KeyMetric> {
        val revenue = model.sections.firstOrNull { it.id == "revenue" && it.availability == SectionAvailability.CONTENT }
        val rows = model.sections.firstOrNull { it.id == "profitability" && it.availability == SectionAvailability.CONTENT }?.rows.orEmpty()
        val comparison = revenue?.change?.substringAfter(' ', "")?.takeIf { it.isNotBlank() }
        fun row(id: String) = rows.firstOrNull { it.metricId == id }
        return listOfNotNull(
            revenue?.headline?.let { KeyMetric(revenue.title, it, revenue.change, revenue.changeDirection, "revenue") },
            row("netIncome")?.let { r ->
                KeyMetric(r.label, r.value, r.change?.let { change -> listOfNotNull(change, comparison).joinToString(" ") }, r.changeDirection, r.metricId)
            },
            row("grossMargin")?.let { KeyMetric(it.label, it.value, it.helper, null, it.metricId) },
            row("operatingMargin")?.let { KeyMetric(it.label, it.value, it.helper, null, it.metricId) }
        )
    }

    /**
     * UI Phase 3: whether a section shows its own headline value and change. Revenue's ("$416.2B", "+6.4% vs FY2024") is already the first Key
     * Metrics tile and the overview chart's caption, so the Revenue section keeps only its sentence and lesson; every other section, and
     * Revenue when no tile shows it, keeps its headline.
     */
    fun headlineInSection(model: FinancialStatementsModel, sectionId: String): Boolean =
        sectionId != "revenue" || keyMetrics(model).none { it.metricId == "revenue" }

    /** "Key Metrics (FY2025)" — the period comes from the revenue headline label ("FY2025"); plain "Key Metrics" without one. */
    fun keyMetricsTitle(model: FinancialStatementsModel): String =
        model.sections.firstOrNull { it.id == "revenue" }?.headlineLabel?.takeIf { it.isNotBlank() }?.let { "Key Metrics ($it)" } ?: "Key Metrics"

    /**
     * The selected tab for the scroll position. [tops] holds each model section's top (by section id) relative to the bottom of the pinned bar.
     * The model's order interleaves the groups (revenue, profitability, cash flow, health, EPS, dividends), so the tab is the group of the
     * section nearest the bar from above — not a fixed group order. Before any section reached the bar: the group of the first section; at the
     * end of the page: the group of the last section on screen ([viewport]).
     */
    fun selectedGroup(tops: Map<String, Float>, tolerance: Float, atEnd: Boolean = false, viewport: Float = Float.MAX_VALUE): Group? {
        val known = tops.filterKeys { groupOf(it) != null }
        if (known.isEmpty()) return null
        if (atEnd) known.filterValues { it < viewport }.maxByOrNull { it.value }?.let { return groupOf(it.key) }
        val reached = known.filterValues { it <= tolerance }.maxByOrNull { it.value }
        return groupOf((reached ?: known.minByOrNull { it.value }!!).key)
    }
}

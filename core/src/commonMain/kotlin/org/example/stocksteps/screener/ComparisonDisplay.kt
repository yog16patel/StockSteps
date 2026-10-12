package org.example.stocksteps.screener

/**
 * Global UI Refinement Phase 5C: display text for Compare Companies shared by Compose and SwiftUI (copy, compact missing-value wording and
 * spoken labels that name the company for every value). Formatting only: values, periods, FX notes and explanations come unchanged from
 * `ComparisonPresenter` / `MetricFormatter`; nothing is calculated, inferred or filled in.
 */
object ComparisonDisplay {
    const val TITLE = "Compare Companies"
    /** The second sentence keeps the long-standing no-ranking safeguard in the header. */
    const val SUBTITLE = "See how companies differ across key financial metrics. The numbers don't say which is a better investment."
    /** Compact wording for a narrow value cell; the reason stays one tap away ([missingAction]). Full-width text uses "No data available". */
    const val NO_DATA = "No data"
    const val NO_DATA_FULL = "No data available"
    const val FEWER_TITLE = "Pick two companies to start"
    const val FEWER_MESSAGE = "Add at least two companies to see a comparison. Search, use Compare on a company's page or your watchlist, or try an example:"
    const val ERROR_TITLE = "Couldn't load the comparison"
    const val CHART_ERROR_TITLE = "Couldn't load the chart"
    const val CHART_ERROR_NOTE = "The rest of the comparison is still available. This only affects the share price chart."
    const val PARTIAL = "Partial data"

    /** What a cell shows: "No data" for a missing value (never "N/A" or 0); "None" (no dividend) and real values unchanged. */
    fun cellText(cell: MetricCell): String = if (!cell.available && cell.text == "N/A") NO_DATA else cell.text

    /** Spoken value for one company: "Microsoft Corporation, Revenue, FY ended Jun 2025, $281.7B" or "…, unavailable. <reason>". */
    fun cellSpoken(column: CompanyColumn, row: ComparisonRow, cell: MetricCell): String {
        val head = listOfNotNull(column.name, row.label, row.period).joinToString(", ")
        return if (cell.available) "$head, ${cell.accessibility}"
        else "$head, ${if (cell.text == "None") "none" else "unavailable"}. ${cell.explanation.orEmpty()}".trimEnd()
    }

    /** The whole row as one spoken element (side-by-side layout), companies in selection order. */
    fun rowSpoken(row: ComparisonRow, columns: List<CompanyColumn>): String =
        (listOfNotNull(row.label, row.period).joinToString(", ") + ". ") + columns.zip(row.cells).joinToString("; ") { (column, cell) ->
            "${column.name} " + if (cell.available) cell.accessibility else if (cell.text == "None") "none" else "unavailable"
        }

    /** Label of the control that explains a missing value: "Market cap unavailable for TSLA. Explain why." */
    fun missingAction(row: ComparisonRow, column: CompanyColumn): String = "${row.label} unavailable for ${column.symbol}. Explain why."

    /** Selected company, spoken: "Apple Inc., AAPL, NASDAQ · USD, partial data". */
    fun selectionSpoken(company: SelectedCompany, column: CompanyColumn?): String =
        listOfNotNull(column?.name ?: company.name, company.symbol, column?.listing?.ifBlank { null }, if (column?.error != null) PARTIAL.lowercase() else null)
            .joinToString(", ")

    /** Every piece of text in [rows] that a value column must fit without breaking a word (values, periods and "No data"). */
    fun layoutTexts(rows: List<ComparisonRow>): List<String> = rows.flatMap { row ->
        row.cells.flatMap { cell -> listOfNotNull(cellText(cell), cell.detail) }
    }

    /** Same for the history table: each cell's value ("No data" when missing) and its fiscal period. */
    fun historyLayoutTexts(view: HistoryView): List<String> = view.rows.flatMap { row ->
        row.cells.flatMap { cell -> listOfNotNull(if (cell.available) cell.text else NO_DATA, cell.period) }
    }
}

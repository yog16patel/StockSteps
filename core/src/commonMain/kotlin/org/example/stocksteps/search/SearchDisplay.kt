package org.example.stocksteps.search

import org.example.stocksteps.model.StockSearchResult

/**
 * Company search presentation shared by Compose and SwiftUI (Phase 5D.5.5B). Display only: results arrive ranked by the server
 * ([SearchRanking]); nothing here reorders, drops or renames a listing.
 */
object SearchDisplay {
    const val BEST_MATCH = "Best match"
    const val OTHER_RESULTS = "Other results"
    const val RESULTS = "Results"

    /**
     * The "Best match" label, only when it can't mislead (plan §8): the first result matches the ticker or the company name exactly, no other
     * result matches at the same level, and no other result is the same company by name (share classes: GOOG and GOOGL are both "Alphabet",
     * so neither is labelled). Otherwise null — no label.
     */
    fun bestMatch(query: String, results: List<StockSearchResult>): StockSearchResult? {
        val first = results.firstOrNull() ?: return null
        val tier = SearchRanking.tier(query, first)
        if (tier != SearchRanking.Tier.EXACT_TICKER && tier != SearchRanking.Tier.EXACT_NAME) return null
        val others = results.drop(1)
        if (others.any { SearchRanking.tier(query, it) == tier }) return null
        val company = SearchRanking.companyKey(first.name)
        if (company.isNotEmpty() && others.any { SearchRanking.companyKey(it.name) == company }) return null
        return first
    }

    /** Result sections: one "Best match" row and "Other results" when the label applies, otherwise a single "Results" section. */
    data class Section(val title: String, val results: List<StockSearchResult>, val best: Boolean)

    fun sections(query: String, results: List<StockSearchResult>): List<Section> {
        if (results.isEmpty()) return emptyList()
        val best = bestMatch(query, results) ?: return listOf(Section(RESULTS, results, best = false))
        val rest = results.drop(1)
        return listOfNotNull(Section(BEST_MATCH, listOf(best), best = true), rest.takeIf { it.isNotEmpty() }?.let { Section(OTHER_RESULTS, it, best = false) })
    }

    /** The identifying line under the name: ticker · exchange (both never truncated; they tell listings apart). */
    fun identity(result: StockSearchResult): String = listOfNotNull(result.symbol, result.exchange?.takeIf { it.isNotBlank() }).joinToString(" · ")

    /** One spoken label per row: "Apple Inc., AAPL, NASDAQ" (+ ", best match"). */
    fun spoken(result: StockSearchResult, best: Boolean): String =
        listOfNotNull(result.name, result.symbol, result.exchange?.takeIf { it.isNotBlank() }, if (best) BEST_MATCH.lowercase() else null).joinToString(", ")
}

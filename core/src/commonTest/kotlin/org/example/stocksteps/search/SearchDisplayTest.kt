package org.example.stocksteps.search

import org.example.stocksteps.model.StockSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchDisplayTest {
    private fun r(symbol: String, name: String, exchange: String? = "NASDAQ") = StockSearchResult(symbol, name, "USD", exchange)

    @Test
    fun bestMatchOnlyForAUniqueExactTickerOrName() {
        val aapl = listOf(r("AAPL", "Apple Inc."), r("AAPY", "Kurv Yield Premium Strategy Apple (AAPL) ETF", "CBOE"))
        assertEquals("AAPL", SearchDisplay.bestMatch("AAPL", aapl)?.symbol)
        assertEquals("AAPL", SearchDisplay.bestMatch("apple", listOf(r("AAPL", "Apple Inc."), r("APLE", "Apple Hospitality REIT, Inc.", "NYSE")))?.symbol)
        // Share classes: GOOG and GOOGL are both "Alphabet Inc." — no label.
        assertNull(SearchDisplay.bestMatch("GOOG", listOf(r("GOOG", "Alphabet Inc."), r("GOOGL", "Alphabet Inc."))))
        // Prefix-only matches never get a label.
        assertNull(SearchDisplay.bestMatch("micro", listOf(r("MSFT", "Microsoft Corporation"), r("MU", "Micron Technology, Inc."))))
        // Two exact name matches are ambiguous.
        assertNull(SearchDisplay.bestMatch("acme", listOf(r("ACME", "Acme Corp"), r("ACM", "Acme Inc.", "NYSE"))))
        assertNull(SearchDisplay.bestMatch("AAPL", emptyList()))
    }

    @Test
    fun sectionsSplitTheLabelledResultFromTheRest() {
        val list = listOf(r("AAPL", "Apple Inc."), r("APLE", "Apple Hospitality REIT, Inc.", "NYSE"))
        assertEquals(listOf(SearchDisplay.BEST_MATCH, SearchDisplay.OTHER_RESULTS), SearchDisplay.sections("apple", list).map { it.title })
        assertEquals(listOf(SearchDisplay.BEST_MATCH), SearchDisplay.sections("aapl", list.take(1)).map { it.title })
        assertEquals(listOf(SearchDisplay.RESULTS), SearchDisplay.sections("micro", list).map { it.title })
        assertEquals(emptyList(), SearchDisplay.sections("x", emptyList()))
        // Order and contents are the server's: nothing is dropped.
        assertEquals(list, SearchDisplay.sections("apple", list).flatMap { it.results })
    }

    @Test
    fun rowText() {
        assertEquals("AAPL · NASDAQ", SearchDisplay.identity(r("AAPL", "Apple Inc.")))
        assertEquals("XYZ", SearchDisplay.identity(r("XYZ", "Xyz", null)))
        assertEquals("Apple Inc., AAPL, NASDAQ, best match", SearchDisplay.spoken(r("AAPL", "Apple Inc."), best = true))
    }
}

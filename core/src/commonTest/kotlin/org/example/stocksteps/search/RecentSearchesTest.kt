package org.example.stocksteps.search

import org.example.stocksteps.model.StockSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentSearchesTest {
    private fun r(symbol: String, name: String = "$symbol Inc.", exchange: String = "NASDAQ") = StockSearchResult(symbol, name, "USD", exchange)

    @Test
    fun selectionsAreNewestFirstMoveToTopAndCapAtTen() {
        val store = RecentSearchStore(InMemoryStringStorage())
        store.record(r("AAPL")); store.record(r("MSFT")); store.record(r("AAPL"))
        assertEquals(listOf("AAPL", "MSFT"), store.current().map { it.symbol })
        (1..12).forEach { store.record(r("T$it")) }
        assertEquals(RecentSearches.MAX, store.current().size)
        assertEquals("T12", store.current().first().symbol)
    }

    @Test
    fun shareClassesStaySeparateAndTickerPunctuationIsOneListing() {
        val store = RecentSearchStore(InMemoryStringStorage())
        store.record(r("GOOG", "Alphabet Inc.")); store.record(r("GOOGL", "Alphabet Inc."))
        assertEquals(listOf("GOOGL", "GOOG"), store.current().map { it.symbol })
        store.record(r("BRK-B", "Berkshire Hathaway Inc.", "NYSE")); store.record(r("BRK.B", "Berkshire Hathaway Inc.", "NYSE"))
        assertEquals(listOf("BRK.B", "GOOGL", "GOOG"), store.current().map { it.symbol })
    }

    @Test
    fun persistsAcrossARestartAndClearAllEmptiesStorage() {
        val storage = InMemoryStringStorage()
        RecentSearchStore(storage).apply { record(r("AAPL", "Apple Inc.")); record(r("SHOP", "Shopify Inc.")) }
        val restarted = RecentSearchStore(storage)
        assertEquals(listOf(r("SHOP", "Shopify Inc."), r("AAPL", "Apple Inc.")), restarted.current())
        restarted.clear()
        assertEquals(emptyList(), restarted.current())
        assertEquals(null, storage.read())
        assertEquals(emptyList(), RecentSearchStore(storage).current())
    }

    @Test
    fun logoUrlIsStoredAndOlderEntriesWithoutItStillRead() {
        val storage = InMemoryStringStorage()
        RecentSearchStore(storage).record(StockSearchResult("AAPL", "Apple Inc.", "USD", "NASDAQ", logoUrl = "https://images.financialmodelingprep.com/symbol/AAPL.png"))
        assertEquals("https://images.financialmodelingprep.com/symbol/AAPL.png", RecentSearchStore(storage).current().single().logoUrl)
        // History saved before logos existed has no logoUrl: it reads fine and shows initials.
        assertEquals(null, RecentSearches.decode("""[{"symbol":"MSFT","name":"Microsoft Corporation","exchange":"NASDAQ"}]""").single().logoUrl)
    }

    @Test
    fun olderEntriesTakeTheirLogoFromALaterSearchResponse() {
        val storage = InMemoryStringStorage("""[{"symbol":"AAPL","name":"Apple Inc.","exchange":"NASDAQ"},{"symbol":"MSFT","name":"Microsoft Corporation","exchange":"NASDAQ"}]""")
        val store = RecentSearchStore(storage)
        store.refreshLogos(listOf(StockSearchResult("AAPL", "Apple Inc.", "USD", "NASDAQ", logoUrl = "https://images.financialmodelingprep.com/symbol/AAPL.png"),
            StockSearchResult("APLE", "Apple Hospitality REIT, Inc.", "USD", "NYSE", logoUrl = "https://images.financialmodelingprep.com/symbol/APLE.png")))
        // Only the listing that was already recent is updated; order and the other entries stay; it persists.
        assertEquals(listOf("https://images.financialmodelingprep.com/symbol/AAPL.png", null), store.current().map { it.logoUrl })
        assertEquals(listOf("AAPL", "MSFT"), RecentSearchStore(storage).current().map { it.symbol })
        assertEquals("https://images.financialmodelingprep.com/symbol/AAPL.png", RecentSearchStore(storage).current().first().logoUrl)
        // Nothing to do: no write.
        val untouched = InMemoryStringStorage(null)
        RecentSearchStore(untouched).refreshLogos(listOf(StockSearchResult("AAPL", "Apple Inc.", logoUrl = "x")))
        assertEquals(null, untouched.read())
    }

    @Test
    fun corruptOrInvalidStorageIsNoHistory() {
        assertEquals(emptyList(), RecentSearchStore(InMemoryStringStorage("{not json")).current())
        assertEquals(emptyList(), RecentSearches.decode("""{"symbol":"AAPL"}"""))
        // Blank entries are dropped; duplicates and overflow are cleaned up.
        val stored = """[{"symbol":"","name":"x"},{"symbol":"AAPL","name":"Apple Inc."},{"symbol":"aapl","name":"Apple Inc."}]"""
        assertEquals(listOf("AAPL"), RecentSearches.decode(stored).map { it.symbol })
        // A corrupt store is rewritten by the next selection.
        val storage = InMemoryStringStorage("garbage")
        RecentSearchStore(storage).record(r("MSFT"))
        assertEquals(listOf("MSFT"), RecentSearches.decode(storage.read()).map { it.symbol })
    }

    @Test
    fun searchScreenPhase() {
        val apple = listOf(r("AAPL"))
        assertEquals(SearchDisplay.Phase.HINT, SearchDisplay.phase(" ", searching = false, failed = false, results = emptyList(), recent = emptyList()))
        assertEquals(SearchDisplay.Phase.RECENT, SearchDisplay.phase("", searching = true, failed = true, results = apple, recent = apple))
        assertEquals(SearchDisplay.Phase.SEARCHING, SearchDisplay.phase("app", searching = true, failed = false, results = emptyList(), recent = apple))
        assertEquals(SearchDisplay.Phase.ERROR, SearchDisplay.phase("app", searching = false, failed = true, results = emptyList(), recent = apple))
        assertEquals(SearchDisplay.Phase.NO_RESULTS, SearchDisplay.phase("xyz", searching = false, failed = false, results = emptyList(), recent = apple))
        assertEquals(SearchDisplay.Phase.RESULTS, SearchDisplay.phase("app", searching = false, failed = false, results = apple, recent = emptyList()))
    }
}

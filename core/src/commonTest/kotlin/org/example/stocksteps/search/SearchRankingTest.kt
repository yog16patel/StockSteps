package org.example.stocksteps.search

import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.search.SearchRanking.Tier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchRankingTest {
    private fun r(symbol: String, name: String, exchange: String? = "NASDAQ") = StockSearchResult(symbol, name, "USD", exchange)

    // The MOCK "apple" list in its fixture (provider) order.
    private val apple = listOf(
        r("AAPL", "Apple Inc."), r("APPLX", "Appleseed Fund"), r("AAPI", "Apple iSports Group Inc", "OTC"), r("APRU", "Apple Rush Company, Inc.", "OTC"),
        r("APLE", "Apple Hospitality REIT, Inc.", "NYSE"), r("PNPL", "Pineapple, Inc.", "OTC"), r("PEGY", "Pineapple Energy Inc."),
        r("PAPL", "Pineapple Financial Inc.", "AMEX"), r("MNAO", "Pineapple Express Cannabis Company", "OTC"), r("GAPJ", "Golden Apple Oil & Gas Inc.", "OTC"),
        r("AAPX", "T-REX 2X Long Apple Daily Target ETF", "CBOE"), r("MLP", "Maui Land & Pineapple Company, Inc.", "NYSE"),
        r("AAPY", "Kurv Yield Premium Strategy Apple (AAPL) ETF", "CBOE"))

    @Test
    fun companyNameQueryPutsTheCompanyFirstThenWholeWordsMajorExchangesAndSubstringsLast() {
        assertEquals(listOf("AAPL", "APLE", "AAPI", "APRU", "APPLX", "AAPX", "AAPY", "GAPJ", "PEGY", "PAPL", "MLP", "PNPL", "MNAO"),
            SearchRanking.rank("apple", apple).map { it.symbol })
        // Nothing is dropped and whitespace/case don't matter.
        assertEquals(SearchRanking.rank("apple", apple), SearchRanking.rank("  APPLE ", apple))
        assertEquals(apple.size, SearchRanking.rank("apple", apple).size)
    }

    @Test
    fun tiers() {
        assertEquals(Tier.EXACT_TICKER, SearchRanking.tier("aapl", r("AAPL", "Apple Inc.")))
        assertEquals(Tier.EXACT_NAME, SearchRanking.tier("Apple", r("AAPL", "Apple Inc.")))
        assertEquals(Tier.EXACT_NAME, SearchRanking.tier("Microsoft Corporation", r("MSFT", "Microsoft Corporation")))
        // A partial suffix is a prefix match, not an exact name.
        assertEquals(Tier.NAME_PREFIX, SearchRanking.tier("microsoft corp", r("MSFT", "Microsoft Corporation")))
        assertEquals(Tier.TICKER_PREFIX, SearchRanking.tier("MSF", r("MSFT", "Microsoft Corporation")))
        assertEquals(Tier.NAME_WORD_START, SearchRanking.tier("apple", r("APLE", "Apple Hospitality REIT, Inc.")))
        assertEquals(Tier.NAME_PREFIX, SearchRanking.tier("micro", r("MU", "Micron Technology, Inc.")))
        assertEquals(Tier.LATER_WORD, SearchRanking.tier("apple", r("GAPJ", "Golden Apple Oil & Gas Inc.")))
        assertEquals(Tier.LATER_WORD_PREFIX, SearchRanking.tier("micro", r("MSFX", "T-Rex 2X Long Microsoft Daily Target ETF")))
        assertEquals(Tier.SUBSTRING, SearchRanking.tier("apple", r("PNPL", "Pineapple, Inc.")))
        assertEquals(Tier.PROVIDER, SearchRanking.tier("apple", r("XYZ", "Unrelated Holdings")))
    }

    @Test
    fun tickerIdentityKeepsShareClassesAndTreatsDotAndDashAlike() {
        assertEquals(Tier.EXACT_TICKER, SearchRanking.tier("BRK.B", r("BRK-B", "Berkshire Hathaway Inc.", "NYSE")))
        assertEquals(Tier.EXACT_TICKER, SearchRanking.tier("brk-b", r("BRK.B", "Berkshire Hathaway Inc.", "NYSE")))
        assertEquals(Tier.TICKER_PREFIX, SearchRanking.tier("GOOG", r("GOOGL", "Alphabet Inc.")))
        val goog = listOf(r("GOOGL", "Alphabet Inc."), r("GOOGN", "Alphabet Inc. Depository Shs"), r("GOOG", "Alphabet Inc."))
        assertEquals(listOf("GOOG", "GOOGL", "GOOGN"), SearchRanking.rank("GOOG", goog).map { it.symbol })
    }

    @Test
    fun withinATierMajorExchangesComeFirstThenProviderOrder() {
        val list = listOf(r("ZZZ", "Apple Zeta", "OTC"), r("YYY", "Apple Yankee", "PNK"), r("BBB", "Apple Beta", "NYSE"), r("NNN", "Apple Null", null))
        // A missing exchange isn't penalised; OTC/PNK keep their provider order among themselves.
        assertEquals(listOf("BBB", "NNN", "ZZZ", "YYY"), SearchRanking.rank("apple", list).map { it.symbol })
        // Deterministic for the same input.
        assertEquals(SearchRanking.rank("apple", list), SearchRanking.rank("apple", list))
    }

    @Test
    fun emptyQueryKeepsProviderOrder() {
        assertEquals(apple.sortedBy { SearchRanking.isOtc(it) }, SearchRanking.rank("  ", apple))
        assertNull(SearchDisplay.bestMatch(" ", apple))
    }

    /** Phase 4 audit: the precedence examples from the brief, over provider-shaped lists (nothing here is special-cased in [SearchRanking]). */
    @Test
    fun exactTickerBeatsPartialNamesAndShareClassesStayApart() {
        // "APP": AppLovin's exact ticker before Apple's name prefix; a ticker prefix (APPN) sits between them; Apple (AAPL) is never dropped.
        val app = listOf(r("AAPL", "Apple Inc."), r("APPN", "Appian Corporation"), r("APLD", "Applied Digital Corporation"), r("APP", "AppLovin Corporation"),
            r("AAPX", "T-REX 2X Long Apple Daily Target ETF", "CBOE"))
        assertEquals(listOf("APP", "APPN", "AAPL", "APLD", "AAPX"), SearchRanking.rank("APP", app).map { it.symbol })
        assertEquals(SearchRanking.rank("APP", app), SearchRanking.rank(" app ", app))
        // "AAPL" exact ticker before an ETF that only mentions it in its name.
        assertEquals(listOf("AAPL", "AAPY"), SearchRanking.rank("AAPL", listOf(r("AAPY", "Kurv Yield Premium Strategy Apple (AAPL) ETF", "CBOE"), r("AAPL", "Apple Inc."))).map { it.symbol })
        // "Microsoft" / "micro": the company before ETFs that name it later; Micron (prefix) stays in provider order beside Microsoft.
        val micro = listOf(r("MSFX", "T-Rex 2X Long Microsoft Daily Target ETF", "CBOE"), r("MU", "Micron Technology, Inc."), r("MSFT", "Microsoft Corporation"))
        assertEquals(listOf("MSFT", "MSFX"), SearchRanking.rank("Microsoft", micro.filter { it.symbol != "MU" }).map { it.symbol })
        assertEquals(listOf("MU", "MSFT", "MSFX"), SearchRanking.rank("micro", micro).map { it.symbol })
        // GOOG vs GOOGL: each exact ticker wins its own query; both stay listed.
        val alphabet = listOf(r("GOOGL", "Alphabet Inc."), r("GOOG", "Alphabet Inc."))
        assertEquals(listOf("GOOG", "GOOGL"), SearchRanking.rank("GOOG", alphabet).map { it.symbol })
        assertEquals(listOf("GOOGL", "GOOG"), SearchRanking.rank("googl", alphabet).map { it.symbol })
        // An ETF by exact ticker ("SPY") ranks like any listing; an unknown symbol keeps the provider's order (nothing invented).
        val spy = listOf(r("SPYG", "SPDR Portfolio S&P 500 Growth ETF", "AMEX"), r("SPY", "SPDR S&P 500 ETF Trust", "AMEX"))
        assertEquals(listOf("SPY", "SPYG"), SearchRanking.rank("spy", spy).map { it.symbol })
        assertEquals(listOf("SPYG", "SPY"), SearchRanking.rank("ZZZZQ", spy).map { it.symbol })
        // Dotted and dashed class symbols are one ticker: "BRK.B" finds BRK-B exactly, ahead of BRK-A.
        assertEquals(listOf("BRK-B", "BRK-A"), SearchRanking.rank("BRK.B", listOf(r("BRK-A", "Berkshire Hathaway Inc.", "NYSE"), r("BRK-B", "Berkshire Hathaway Inc.", "NYSE"))).map { it.symbol })
        // Inner whitespace never changes the order.
        assertEquals(SearchRanking.rank("apple inc", apple), SearchRanking.rank("apple   inc", apple))
    }
}

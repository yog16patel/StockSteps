package org.example.stocksteps.marketdata.alphavantage

import org.example.stocksteps.httpclient.AlphaVantageAnswers
import org.example.stocksteps.httpclient.AlphaVantageAnswers.Kind
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 9B.7: HTTP-200 refusal classification and every parser (fixtures with invented values). */
class AlphaVantageParsersTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun refusalsInsideHttp200AreClassifiedNeverData() {
        assertEquals(Kind.RATE_LIMITED, AlphaVantageAnswers.classify(AvFixtures.RATE_LIMIT))
        assertEquals(Kind.RATE_LIMITED, AlphaVantageAnswers.classify(AvFixtures.NOTE_FREQUENCY))
        assertEquals(Kind.PREMIUM_ONLY, AlphaVantageAnswers.classify(AvFixtures.PREMIUM))
        assertEquals(Kind.INVALID_KEY, AlphaVantageAnswers.classify(AvFixtures.DEMO_KEY))
        assertEquals(Kind.ERROR_MESSAGE, AlphaVantageAnswers.classify(AvFixtures.INVALID_CALL))
        assertEquals(Kind.OTHER_NOTICE, AlphaVantageAnswers.classify(AvFixtures.UNKNOWN_NOTICE))
        assertEquals(Kind.DATA, AlphaVantageAnswers.classify(AvFixtures.quote("EXMP", "10", "2026-10-16", "9")))
        assertEquals(Kind.DATA, AlphaVantageAnswers.classify("symbol,name,exchange\nEXMP,Example,NYSE"))   // CSV functions
        // A data object that happens to carry an "Information" key in its metadata is still data.
        assertEquals(Kind.DATA, AlphaVantageAnswers.classify(AvFixtures.daily("EXMP", listOf("2026-10-16" to "10"))))
        assertEquals(mapOf(Kind.RATE_LIMITED to 429, Kind.PREMIUM_ONLY to 402, Kind.INVALID_KEY to 403, Kind.ERROR_MESSAGE to 404),
            listOf(Kind.RATE_LIMITED, Kind.PREMIUM_ONLY, Kind.INVALID_KEY, Kind.ERROR_MESSAGE).associateWith { AlphaVantageAnswers.status(it) })
    }

    @Test fun dailySeriesRawAndAdjusted() {
        val raw = AlphaVantageParsers.daily(AvFixtures.daily("EXMP", listOf("2026-10-15" to "10.50", "2026-10-16" to "11.00", "2026-10-14" to "0", "bad" to "1")))
        assertFalse(raw.adjusted); assertEquals("US/Eastern", raw.timeZone); assertEquals(d("2026-10-16"), raw.lastRefreshed)
        assertEquals(listOf(d("2026-10-15"), d("2026-10-16")), raw.bars.map { it.date }, "oldest first; zero close and bad dates dropped")
        assertEquals(2, raw.skipped); assertEquals(1000L, raw.bars.first().volume); assertNull(raw.bars.first().adjustedClose)
        val adj = AlphaVantageParsers.daily(AvFixtures.daily("EXMP", listOf("2026-02-13" to "400", "2026-02-17" to "100"), adjusted = true,
            events = mapOf("2026-02-17" to ("0.0000" to "4.0"))))
        assertTrue(adj.adjusted); assertEquals(0, BigDecimal("4.0").compareTo(adj.bars.last().splitCoefficient!!)); assertEquals(1000L, adj.bars.last().volume)
        assertFailsWith<IllegalArgumentException> { AlphaVantageParsers.daily("""{"Meta Data":{}}""") }
    }

    @Test fun quoteSearchOverviewMovers() {
        val q = AlphaVantageParsers.quote(AvFixtures.quote("EXMP", "11.0000", "2026-10-16", "10.0000"))!!
        assertEquals(d("2026-10-16"), q.latestTradingDay); assertEquals(0, BigDecimal("1").compareTo(q.changePercent!!), "percent sign removed")
        assertNull(AlphaVantageParsers.quote(AvFixtures.EMPTY_QUOTE), "unknown symbol → no quote, not zero")
        assertEquals(listOf("EXMP", "EXMP.LON", "EXMPF", "EXET"), AlphaVantageParsers.search(AvFixtures.SEARCH).map { it.symbol })
        val o = AlphaVantageParsers.overview(AvFixtures.OVERVIEW)!!
        assertEquals("Example Corp", o.name); assertEquals(0, BigDecimal("12345000000").compareTo(o.marketCap!!)); assertEquals(d("2026-06-30"), o.latestQuarter)
        assertNull(AlphaVantageParsers.overview("{}"))
        val m = AlphaVantageParsers.movers(AvFixtures.MOVERS)
        assertEquals(listOf("UPUP"), m.gainers.map { it.ticker }); assertEquals(0, BigDecimal("-50.0").compareTo(m.losers.single().changePercent!!))
        assertEquals(99_000_000L, m.mostActive.single().volume)
    }

    @Test fun statementsKeepNullsAndExactValues() {
        val s = AlphaVantageParsers.statement(AvFixtures.INCOME)
        assertEquals(listOf(d("2025-12-31"), d("2024-12-31")), s.annual.map { it.fiscalDateEnding })
        assertEquals(3, s.quarterly.size); assertEquals(1, s.skipped)
        assertNull(s.annual[1].values["grossProfit"], "\"None\" is missing, never 0")
        assertNull(s.quarterly.first { it.fiscalDateEnding == d("2025-12-31") }.values["grossProfit"], "\"-\" is missing")
        assertEquals(0, BigDecimal("1200000000").compareTo(s.annual[0].values.getValue("totalRevenue")))
        assertEquals(0, AlphaVantageParsers.statement("{}").annual.size)
    }

    @Test fun earningsDividendsSplits() {
        val e = AlphaVantageParsers.earnings(AvFixtures.EARNINGS)
        assertEquals(2, e.quarterly.size); assertNull(e.quarterly[1].reportedEps); assertEquals(d("2026-07-28"), e.quarterly[0].reportedDate)
        val div = AlphaVantageParsers.dividends(AvFixtures.DIVIDENDS)
        assertEquals(listOf(d("2026-05-10"), d("2026-08-10")), div.map { it.exDate }, "rows without an ex-date dropped; oldest first")
        assertNull(div[0].paymentDate)
        val sp = AlphaVantageParsers.splits(AvFixtures.SPLITS)
        assertEquals(listOf("0.1000", "4.0000"), sp.map { it.ratio.toPlainString() })
        assertEquals(0, AlphaVantageParsers.splits("""{"symbol":"EXMP"}""").size)
        assertFailsWith<IllegalArgumentException> { AlphaVantageParsers.dividends("""{"unexpected":1,"other":2}""") }
    }
}

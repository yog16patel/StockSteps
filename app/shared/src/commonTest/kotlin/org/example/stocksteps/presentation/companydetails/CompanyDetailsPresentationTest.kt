package org.example.stocksteps.presentation.companydetails

import org.example.stocksteps.companydetail.CompanyOverview
import org.example.stocksteps.companydetail.GlanceMetric
import org.example.stocksteps.companydetail.InfoRow
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.model.MarketStatus
import org.example.stocksteps.presentation.markets.MarketsScreenPresentation.StatusDot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompanyDetailsPresentationTest {
    private val present = CompanyDetailsPresentation

    private fun overview(glance: List<GlanceMetric> = emptyList(), quickStats: List<InfoRow> = emptyList(), keyRatios: List<InfoRow> = emptyList(),
                         sector: String? = "Technology", industry: String? = "Consumer Electronics") = CompanyOverview(
        symbol = "AAPL", name = "Apple Inc.", shortName = "Apple", listing = "AAPL • NASDAQ", logoUrl = null, tags = emptyList(), price = "$336.67",
        changeAmount = "+$3.04", changePercent = "+0.91%", direction = PriceDirection.UP, marketStatus = MarketStatus.CLOSED, updatedAt = null,
        quickStats = quickStats, dayRange = null, yearRange = null, glance = glance, assessment = emptyList(), insight = null, insightEvidence = emptyList(),
        about = null, aboutFull = null, classification = null, sector = sector, industry = industry, country = null, website = null,
        highlights = emptyList(), highlightsBasis = null, keyRatios = keyRatios, valuation = null, unavailable = emptySet())

    @Test
    fun closedMarketIsNeutralAndAlwaysWritten() {
        assertEquals(StatusDot.OPEN, present.statusDot(MarketStatus.OPEN))
        assertEquals(StatusDot.EXTENDED, present.statusDot(MarketStatus.AFTER_HOURS))
        assertEquals(StatusDot.EXTENDED, present.statusDot(MarketStatus.PRE_MARKET))
        assertEquals(StatusDot.NEUTRAL, present.statusDot(MarketStatus.CLOSED))
        assertEquals("Closed", present.statusLabel(MarketStatus.CLOSED))
        assertEquals("US market: after-hours trading", present.statusDescription(MarketStatus.AFTER_HOURS))
        // Unknown is never guessed from the clock.
        assertNull(present.statusLabel(MarketStatus.UNKNOWN))
    }

    @Test
    fun glanceQualifierMovesToTheSupportingLineSoTheNumberIsComplete() {
        val growth = GlanceMetric("revenueGrowth", "Revenue Growth", "+14.9% (YoY)", "Growing revenue", PriceDirection.UP)
        assertEquals(CompanyDetailsPresentation.GlanceDisplay("+14.9%", "Year over year · Growing revenue"), present.glance(growth))
        val negative = growth.copy(value = "-3.2% (YoY)", helper = "")
        assertEquals(CompanyDetailsPresentation.GlanceDisplay("-3.2%", "Year over year"), present.glance(negative))
        // Values without a qualifier, and unavailable values, are shown exactly as provided (never as zero).
        assertEquals("$127.2B", present.glance(GlanceMetric("marketCap", "Market Cap", "$127.2B", "Large company")).value)
        assertEquals("—", present.glance(GlanceMetric("revenueGrowth", "Revenue Growth", "—", "Not available")).value)
        assertEquals("N/A", present.glance(GlanceMetric("pe", "P/E Ratio", "N/A", "No positive earnings")).value)
    }

    @Test
    fun metricsShownInAtAGlanceAreNotRepeated() {
        val data = overview(
            glance = listOf(GlanceMetric("pe", "P/E Ratio", "28.0", ""), GlanceMetric("dividendYield", "Dividend Yield", "0.3%", "")),
            quickStats = listOf(InfoRow("Open", "$333.74"), InfoRow("Market Cap", "$127.2B"), InfoRow("P/E", "28.0"), InfoRow("Volume", "6.9M")),
            keyRatios = listOf(InfoRow("P/E Ratio", "28.0"), InfoRow("Price to Sales", "10.6"), InfoRow("Dividend Yield", "0.3%"))
        )
        assertEquals(listOf("Open", "Volume"), present.chartStats(data).map { it.label })
        assertEquals(listOf("Price to Sales"), present.keyRatios(data).map { it.label })
    }

    @Test
    fun sectorAndIndustryAppearOnceAsRows() {
        assertEquals(listOf("Sector" to "Technology", "Industry" to "Consumer Electronics"), present.classification(overview()).map { it.label to it.value })
        assertEquals(emptyList(), present.classification(overview(sector = null, industry = null)))
        assertEquals("Understand Apple", present.understandTitle(overview()))
    }

    @Test
    fun priceIsSpokenWithDirectionInWordsAndNeverAsZero() {
        assertEquals("Price $336.67. Up $3.04, 0.91%, today. Updated 4:00 PM EDT · Oct 7.", present.priceDescription(overview(), "4:00 PM EDT · Oct 7"))
        val down = overview().copy(changeAmount = "-$0.22", changePercent = "-0.96%", direction = PriceDirection.DOWN)
        assertEquals("Price $336.67. Down $0.22, 0.96%, today.", present.priceDescription(down, null))
        val missing = overview().copy(price = null, changeAmount = null, changePercent = "—", direction = PriceDirection.UNAVAILABLE)
        assertEquals("Price unavailable. Today's change unavailable.", present.priceDescription(missing, null))
    }
}

package org.example.stocksteps.presentation.companydetails

import org.example.stocksteps.companydetail.CompanyOverview
import org.example.stocksteps.companydetail.GlanceMetric
import org.example.stocksteps.companydetail.InfoRow
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.model.MarketStatus
import org.example.stocksteps.presentation.markets.MarketsScreenPresentation.StatusDot
import org.example.stocksteps.presentation.companydetails.CompanyDetailsPresentation.Section as s
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
    fun chartHighAndLowGiveWayToTheDayRangeOnlyWhenItExists() {
        val stats = listOf(InfoRow("Open", "$333.74"), InfoRow("High", "$338.67"), InfoRow("Low", "$332.78"), InfoRow("Volume", "6.9M"),
            InfoRow("Market Cap", "$127.2B"), InfoRow("P/E", "28.0"))
        val withRange = overview(quickStats = stats).copy(dayRange = org.example.stocksteps.companydetail.RangeSummary("$332.78", "$338.67", 0.6f))
        assertEquals(listOf("Open", "Volume"), present.chartStats(withRange).map { it.label })
        // Without a day range nothing disappears: High and Low stay under the chart.
        assertEquals(listOf("Open", "High", "Low", "Volume"), present.chartStats(overview(quickStats = stats)).map { it.label })
    }

    @Test
    fun sectionBarListsOnlySectionsThePageShows() {
        val all = CompanyDetailsPresentation.Section.entries
        assertEquals(all, present.sections(overview().copy(about = "Apple Inc. designs devices.")))
        // Before the overview loads only Overview and News exist.
        assertEquals(listOf(CompanyDetailsPresentation.Section.OVERVIEW, CompanyDetailsPresentation.Section.NEWS), present.sections(null))
        // No description, country or classification → no About entry.
        assertEquals(all - CompanyDetailsPresentation.Section.ABOUT, present.sections(overview(sector = null, industry = null)))
        assertEquals(listOf("Overview", "Financials", "News", "About"), all.map { it.label })
    }

    @Test
    fun selectedSectionIsTheLastOneThatReachedTheBar() {
        val tops = mapOf(s.OVERVIEW to -900f, s.FINANCIALS to -10f, s.NEWS to 400f, s.ABOUT to 1600f)
        assertEquals(s.FINANCIALS, present.selectedSection(tops, tolerance = 1f))
        assertEquals(s.OVERVIEW, present.selectedSection(mapOf(s.OVERVIEW to 20f, s.FINANCIALS to 700f), tolerance = 1f))
        // At the end of the page a short last section that can't reach the bar is still selectable once it is on screen.
        assertEquals(s.ABOUT, present.selectedSection(tops, tolerance = 1f, atEnd = true, viewport = 1700f))
        assertEquals(s.NEWS, present.selectedSection(tops, tolerance = 1f, atEnd = true, viewport = 1000f))
        // Sections that aren't on the page are never selected.
        assertEquals(s.NEWS, present.selectedSection(mapOf(s.OVERVIEW to -500f, s.NEWS to -5f), tolerance = 1f))
    }

    @Test
    fun whatItDoesUsesTheFirstSentenceAndClassificationVerbatim() {
        val apple = overview().copy(about = "Apple Inc. is a global technology corporation. It sells iPhone, Mac and services worldwide.")
        val what = present.whatItDoes(apple)!!
        assertEquals("What Apple does", what.title)
        // "Inc." is not a sentence end.
        assertEquals("Apple Inc. is a global technology corporation.", what.summary)
        assertEquals("Technology · Consumer Electronics", what.classification)
        assertEquals("One sentence without an end", present.firstSentence("One sentence without an end"))
        assertEquals("J.P. Morgan Chase & Co. is a bank.", present.firstSentence("J.P. Morgan Chase & Co. is a bank. It lends."))
        assertEquals("Sales rose 5.2% in the U.S. market.", present.firstSentence("Sales rose 5.2% in the U.S. market. Costs fell."))
        // Classification only, description only, neither.
        assertEquals(CompanyDetailsPresentation.WhatItDoes("What Apple does", null, "Technology · Consumer Electronics"), present.whatItDoes(overview()))
        assertEquals("Apple makes phones.", present.whatItDoes(overview(sector = null, industry = null).copy(about = "Apple makes phones."))?.summary)
        assertNull(present.whatItDoes(overview(sector = null, industry = null)))
    }

    @Test
    fun sectorAndIndustryAppearOnceAsRows() {
        assertEquals(listOf("Sector" to "Technology", "Industry" to "Consumer Electronics"), present.classification(overview()).map { it.label to it.value })
        assertEquals(emptyList(), present.classification(overview(sector = null, industry = null)))
        assertEquals("Understand Apple", present.understandTitle(overview()))
    }

    @Test
    fun priceIsSpokenWithDirectionInWordsAndNeverAsZero() {
        val today = CompanyDetailsPresentation.ChangeLabel("Today", "today")
        assertEquals("Price $336.67. Up $3.04, 0.91%, today. Updated 4:00 PM EDT · Oct 7.", present.priceDescription(overview(), "4:00 PM EDT · Oct 7", today))
        val down = overview().copy(changeAmount = "-$0.22", changePercent = "-0.96%", direction = PriceDirection.DOWN)
        assertEquals("Price $336.67. Down $0.22, 0.96%, today.", present.priceDescription(down, null, today))
        val missing = overview().copy(price = null, changeAmount = null, changePercent = "—", direction = PriceDirection.UNAVAILABLE)
        assertEquals("Price unavailable. Change unavailable.", present.priceDescription(missing, null, today))
        // A change from an earlier session is spoken with its date, matching the visible label.
        val earlier = present.changeLabel(MarketStatus.CLOSED, "2026-10-08", "2026-10-10")
        assertEquals("Price $336.67. Up $3.04, 0.91%, on Oct 8. Updated 4:00 PM EDT · Oct 8.", present.priceDescription(overview(), "4:00 PM EDT · Oct 8", earlier))
    }

    @Test
    fun changeIsTodayOnlyForTodaysSessionConfirmedByTheServer() {
        fun label(status: MarketStatus, quote: String?, today: String? = "2026-10-08") = present.changeLabel(status, quote, today).text
        // Same exchange date and a known session from the server → Today (open, after-hours, or closed in the evening).
        assertEquals("Today", label(MarketStatus.OPEN, "2026-10-08"))
        assertEquals("Today", label(MarketStatus.AFTER_HOURS, "2026-10-08"))
        assertEquals("Today", label(MarketStatus.CLOSED, "2026-10-08"))
        // Quote from an earlier session (weekend, holiday, stale source) → its date, never "Today".
        assertEquals("Oct 8", label(MarketStatus.CLOSED, "2026-10-08", today = "2026-10-10"))
        assertEquals("Oct 7", label(MarketStatus.OPEN, "2026-10-07"))
        // Before the open the change still belongs to the previous session; an unknown status never trusts the device clock alone.
        assertEquals("Oct 8", label(MarketStatus.PRE_MARKET, "2026-10-08"))
        assertEquals("Oct 8", label(MarketStatus.UNKNOWN, "2026-10-08"))
        assertEquals("Oct 8", label(MarketStatus.CLOSED, "2026-10-08", today = null))
        // No quote time: Today only while the server says the market is open.
        assertEquals("Today", label(MarketStatus.OPEN, null))
        assertEquals("Latest session", label(MarketStatus.CLOSED, null))
        assertEquals(CompanyDetailsPresentation.ChangeLabel("Jan 2", "on Jan 2"), present.changeLabel(MarketStatus.CLOSED, "2027-01-02", "2027-01-04"))
    }

    @Test
    fun exchangeDateUsesNewYorkTime() {
        // 2026-10-09T01:30Z is 9:30 PM EDT on Oct 8 in New York (after-hours trading of the Oct 8 session).
        assertEquals("2026-10-08", exchangeDate(1_791_509_400))
        assertEquals(10, currentExchangeDate()?.length)
    }

    @Test
    fun valuationHeadlineIsShownOnceAcrossTheScreen() {
        val headline = "Trading near its historical P/E valuation."
        val valuation = org.example.stocksteps.companydetail.ValuationSummary("28.0", "27.5", "5Y Average", "+2%",
            org.example.stocksteps.companydetail.ValuationPosition.NEAR, headline, "Investors are paying about what they have on average.")
        val row = org.example.stocksteps.companydetail.AssessmentRow("valuation", "Valuation", "P/E 28.0 • 5Y Avg 27.5", explanation = headline)
        // Understand already states it, so the Valuation section keeps only its numbers and explanation.
        assertNull(present.valuationHeadline(overview().copy(valuation = valuation, assessment = listOf(row))))
        // Without that row (or with different wording) the section keeps its headline.
        assertEquals(headline, present.valuationHeadline(overview().copy(valuation = valuation)))
        assertNull(present.valuationHeadline(overview()))
    }
}

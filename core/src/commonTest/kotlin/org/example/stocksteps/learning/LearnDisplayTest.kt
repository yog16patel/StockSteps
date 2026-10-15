package org.example.stocksteps.learning

import kotlin.test.*

/** Global UI Refinement Phase 5D.5.2: Learn display rules (display only; no educational text added or reworded). */
class LearnDisplayTest {
    @Test fun topicsComeFromTheCatalogueInOrderAndCoverEveryTerm() {
        val topics = LearnDisplay.topics()
        assertEquals(listOf("Business", "Growth", "Profit", "Financial health", "Valuation", "Income", "Earnings", "Portfolio"), topics.map { it.name })
        assertEquals(BeginnerEducation.entries.size, topics.sumOf { it.terms.size })
        assertEquals(4, LearnDisplay.featuredTopics().size)
        assertEquals("1 term", LearnDisplay.termCount(LearnDisplay.topic("Business")!!))
        assertEquals("3 terms", LearnDisplay.termCount(LearnDisplay.topic("Profit")!!))
        assertNull(LearnDisplay.topic("Crypto"))
    }

    @Test fun termNamesOnlyStripTheQuestionForm() {
        fun name(id: String) = LearnDisplay.termName(BeginnerEducation.entry(id)!!)
        assertEquals("P/E ratio", name("pe"))
        assertEquals("EPS", name("eps"))
        assertEquals("earnings beat", name("earningsBeat"))
        assertEquals("net income (profit)", name("netIncome"))
        assertEquals("Net income (profit), profit margin, free cash flow", LearnDisplay.topicSummary(LearnDisplay.topic("Profit")!!))
    }

    @Test fun searchMatchesTitleTopicOrShortTextAndEveryWord() {
        assertEquals(listOf("pe"), LearnDisplay.search("p/e").map { it.id })
        assertTrue(LearnDisplay.search("VALUATION").map { it.id }.containsAll(listOf("eps", "pe", "marketCap")))
        assertEquals("dividendYield", LearnDisplay.search("dividend yield").first().id)
        assertEquals(emptyList(), LearnDisplay.search("   "))
        assertEquals(emptyList(), LearnDisplay.search("blockchain"))
        assertEquals("No terms match \"blockchain\". Try another word.", LearnDisplay.searchEmpty(" blockchain "))
        assertEquals("1 term", LearnDisplay.resultCount(1))
    }

    @Test fun searchRanksTitleMatchesBeforeDefinitionMatches() {
        // Exact term name first, then catalogue order inside each tier.
        assertEquals("eps", LearnDisplay.search("EPS").first().id)
        assertEquals("revenue", LearnDisplay.search("revenue").first().id)          // exact name beats "revenue growth" (prefix)
        assertEquals(listOf("revenue", "revenueGrowth"), LearnDisplay.search("revenue").take(2).map { it.id })
        // "earnings": the earnings-beat title first; EPS and P/E only mention earnings in their definitions.
        val earnings = LearnDisplay.search("earnings").map { it.id }
        assertEquals("earningsBeat", earnings.first())
        assertTrue(earnings.containsAll(listOf("eps", "pe")))
        // "dividend": the dividend-yield title before free cash flow, whose definition mentions dividends.
        assertEquals(listOf("dividendYield", "freeCashFlow"), LearnDisplay.search("dividend").map { it.id })
        // A topic-only match ranks after title matches but before definition-only matches.
        val growth = LearnDisplay.search("growth").map { it.id }
        assertEquals("revenueGrowth", growth.first())
        // Prefix: "market" → market capitalization (name prefix) before others mentioning markets.
        assertEquals("marketCap", LearnDisplay.search("market").first().id)
    }

    @Test fun searchTiersAreDeterministic() {
        val pe = BeginnerEducation.entry("pe")!!
        assertEquals(0, LearnDisplay.searchTier(pe, "p/e ratio", listOf("p/e", "ratio")))
        assertEquals(1, LearnDisplay.searchTier(pe, "p/e", listOf("p/e")))
        assertEquals(3, LearnDisplay.searchTier(pe, "valuation", listOf("valuation")))
        assertEquals(4, LearnDisplay.searchTier(pe, "earnings", listOf("earnings")))
        assertNull(LearnDisplay.searchTier(pe, "dividend", listOf("dividend")))
        assertEquals(LearnDisplay.search("earnings"), LearnDisplay.search("  EARNINGS "))
    }

    @Test fun sectionsUseTheCatalogueTextAndSkipMissingOnes() {
        val pe = BeginnerEducation.entry("pe")!!
        val sections = LearnDisplay.sections(pe)
        assertEquals(listOf("Why it matters", "How to read it", "Example", "Keep in mind"), sections.map { it.title })
        assertEquals(pe.why, sections.first().body)
        assertEquals(pe.limitations, sections.last().body)
        assertEquals(LearnDisplay.outline(pe), sections.map { it.title })
        // No example in the catalogue → no Example section.
        assertFalse("Example" in LearnDisplay.outline(BeginnerEducation.entry("netIncome")!!))
    }

    @Test fun nextWalksTopicsInOrder() {
        val all = LearnDisplay.topics().flatMap { it.terms }
        assertEquals(all[1].id, LearnDisplay.next(all[0])!!.id)
        assertNull(LearnDisplay.next(all.last()))
        assertEquals("Next: ${all[1].title}", LearnDisplay.nextLabel(all[1]))
    }

    @Test fun todaysConceptIsACatalogueEntryFromTheBriefRotation() {
        repeat(10) { day -> assertNotNull(BeginnerEducation.entry(LearnDisplay.todaysConcept(day).id)) }
        assertEquals("2 of 5 steps completed", LearnDisplay.journeyDetail(2))
    }

    @Test fun neighbouringTopicTilesHaveDifferentTones() {
        val tones = LearnDisplay.topics().map { LearnDisplay.topicTone(it.name) }
        assertTrue(tones.all { it in setOf("blue", "green", "indigo", "orange") })
        tones.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        // The landing grid (2 columns) shows four different tones.
        assertEquals(4, LearnDisplay.featuredTopics().map { LearnDisplay.topicTone(it.name) }.toSet().size)
    }

    @Test fun slashesNeverBreakAndTextIsOtherwiseUnchanged() {
        assertEquals("What is the P/\u2060E ratio?", LearnDisplay.unbroken("What is the P/E ratio?"))
        assertEquals("What is EPS?", LearnDisplay.unbroken("What is EPS?"))
        assertEquals("What is the P/E ratio?", LearnDisplay.unbroken("What is the P/E ratio?").replace("\u2060", ""))
    }

    @Test fun previewIsTheFirstSentenceUnchanged() {
        val pe = BeginnerEducation.entry("pe")!!
        assertEquals(org.example.stocksteps.format.ReadableText.firstSentence(pe.short).first, LearnDisplay.preview(pe))
        assertTrue(pe.short.startsWith(LearnDisplay.preview(pe)))
        val margin = BeginnerEducation.entry("netMargin")!!
        assertEquals(margin.short, LearnDisplay.preview(margin))
    }

    @Test fun dayOfYearIsTheCalendarDay() {
        fun ms(iso: String) = kotlin.time.Instant.parse(iso).toEpochMilliseconds()
        assertEquals(1, LearnDisplay.dayOfYear(ms("2026-01-01T00:00:00Z")))
        assertEquals(280, LearnDisplay.dayOfYear(ms("2026-10-07T21:15:00Z")))
        assertEquals(366, LearnDisplay.dayOfYear(ms("2024-12-31T12:00:00Z")))
        assertEquals(60, LearnDisplay.dayOfYear(ms("2024-02-29T00:00:00Z")))
        assertEquals(61, LearnDisplay.dayOfYear(ms("2025-03-02T00:00:00Z")))
    }
}

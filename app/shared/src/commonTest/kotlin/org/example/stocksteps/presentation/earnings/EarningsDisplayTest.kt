package org.example.stocksteps.presentation.earnings

import org.example.stocksteps.earnings.Classification
import org.example.stocksteps.earnings.HistoryRowView
import org.example.stocksteps.earnings.MetricLine
import org.example.stocksteps.theme.StockBadgeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 5D.1 (E-01): one classification presentation for Earnings Results and Details on both platforms. */
class EarningsDisplayTest {
    @Test fun eachClassificationHasWordsAndASemanticStyle() {
        assertEquals(EarningsClassificationDisplay("Beat estimate", StockBadgeKind.POSITIVE, "Result: beat the estimate"), EarningsDisplay.classification(Classification.BEAT))
        assertEquals(EarningsClassificationDisplay("Met estimate", StockBadgeKind.NEUTRAL, "Result: met the estimate"), EarningsDisplay.classification(Classification.MET))
        assertEquals(EarningsClassificationDisplay("Missed estimate", StockBadgeKind.NEGATIVE, "Result: missed the estimate"), EarningsDisplay.classification(Classification.MISS))
        assertEquals(StockBadgeKind.NEUTRAL, EarningsDisplay.classification(Classification.UNAVAILABLE).kind)
        assertEquals("Comparison unavailable", EarningsDisplay.classification(Classification.UNAVAILABLE).label)
    }

    @Test fun labelsDescribeTheEstimateNotTheInvestment() {
        for (c in Classification.entries) {
            val shown = EarningsDisplay.classification(c)
            assertTrue("estimate" in shown.label.lowercase() || c == Classification.UNAVAILABLE, "$c")
            for (word in listOf("good", "bad", "buy", "sell", "strong", "weak", "opportunity")) assertFalse(word in shown.label.lowercase() || word in shown.spoken.lowercase(), "$c: $word")
        }
    }

    /** The mapping only reads the classification it is given; it never recomputes one (same input → same output, all four distinct). */
    @Test fun sameDisplayForTheSameClassification() {
        assertEquals(4, Classification.entries.map { EarningsDisplay.classification(it).label }.toSet().size)
        Classification.entries.forEach { assertEquals(EarningsDisplay.classification(it), EarningsDisplay.classification(it)) }
    }

    // ---------- Phase 5D.2 ----------

    @Test fun learnLinksAreNamedAfterTheLessonTheyOpen() {
        // Destinations are unchanged (EPS → surprise, revenue → revenue); only the words now match them.
        assertEquals("What is earnings surprise?", EarningsDisplay.cardTopic("EPS")?.title)
        assertEquals("What is revenue?", EarningsDisplay.cardTopic("Revenue")?.title)
        assertEquals("How is the price reaction measured?", EarningsDisplay.topicAction("reaction"))
        assertEquals(null, EarningsDisplay.topicAction("no-such-lesson"))
    }

    @Test fun sentenceCaseHeadingsAndOneReactionTitle() {
        for (title in listOf(EarningsDisplay.REACTION_TITLE, EarningsDisplay.TAKEAWAY_TITLE, EarningsDisplay.LEARN_TITLE, EarningsDisplay.COMPANY_ACTION)) {
            val words = title.split(' ')
            assertTrue(words.drop(1).none { it.first().isUpperCase() }, title)
        }
        assertEquals("Earnings per share (EPS)", EarningsDisplay.cardTitle("EPS"))
        assertEquals("Revenue", EarningsDisplay.cardTitle("Revenue"))
    }

    @Test fun historyChartNeedsTwoReportedQuarters() {
        assertFalse(EarningsDisplay.historyChartShown(listOf(null, 1.2, null)))
        assertTrue(EarningsDisplay.historyChartShown(listOf(1.1, null, 1.2)))
        assertFalse(EarningsDisplay.historyChartShown(emptyList()))
    }

    @Test fun historyRowsShowTheTooltipAmountsAndDoNotRepeatThemWhenExpanded() {
        val lines = listOf(MetricLine("EPS", "C$1.94 vs C$2.00 est."), MetricLine("Revenue", "C$4.45B vs C$4.32B est."), MetricLine("Reported", "Jul 22, 2026"))
        val row = HistoryRowView("CNR.TO:2026-Q2", "Q2 FY2026", "2026-07-22", "Miss (−3.0%)", "C$4.45B", "+3.0%", lines,
            Classification.MISS, "C$1.94 vs C$2.00 est.", "−3.0%")
        assertEquals("EPS C$1.94 vs C$2.00 est. · −3.0%", EarningsDisplay.historyEpsLine(row))
        assertEquals("Revenue C$4.45B · +3.0% YoY", EarningsDisplay.historyRevenueLine(row))
        assertEquals(listOf("Revenue", "Reported"), EarningsDisplay.historyDetailLines(row).map { it.label })
        // Older data without the comparison keeps the previous text and every expanded line.
        val legacy = row.copy(epsVsEstimate = null, surprise = null)
        assertEquals("EPS Miss (−3.0%)", EarningsDisplay.historyEpsLine(legacy))
        assertEquals(3, EarningsDisplay.historyDetailLines(legacy).size)
    }

    // ---------- Phase 5D.3 ----------

    @Test fun insightIconsAreNeutralPerCategory() {
        assertEquals("percent", EarningsDisplay.insightIcon("surprise"))
        assertEquals("chart", EarningsDisplay.insightIcon("growth"))
        assertEquals("layers", EarningsDisplay.insightIcon("trend"))
        assertEquals("lightbulb", EarningsDisplay.insightIcon("anything-new"))
    }

    @Test fun takeawayShowsReportedAndEstimatedEpsOnly() {
        val card = org.example.stocksteps.earnings.ResultCard("EPS", "Miss (−3.0%)", Classification.MISS, emptyList(), null,
            listOf(MetricLine("Reported", "C$1.94"), MetricLine("Estimate", "C$2.00"), MetricLine("Surprise", "−3.0%")))
        assertEquals(listOf("Reported EPS" to "C$1.94", "Estimated EPS" to "C$2.00"), EarningsDisplay.takeawayMetrics(card).map { it.label to it.value })
        assertTrue(EarningsDisplay.takeawayMetrics(null).isEmpty())
    }

    /** Folding disclosures away never drops one: every note appears exactly once, the sample sentence only for sample data. */
    @Test fun resultsDisclosuresKeepEveryNoteOnce() {
        val notes = EarningsDisplay.resultsDisclosures("Published by the source Wed, Jul 22, 21:00 UTC", listOf("Results: Sample"), sampleData = true)
        assertEquals(notes.size, notes.toSet().size)
        assertTrue(EarningsDisplay.SAMPLE_SENTENCE in notes && EarningsDisplay.EDUCATION_NOT_ADVICE in notes && EarningsDisplay.PRICE_CAUSES in notes)
        assertFalse(EarningsDisplay.SAMPLE_SENTENCE in EarningsDisplay.resultsDisclosures(null, emptyList(), sampleData = false))
        // The server's notes can already contain the sample sentence (seen on device): still shown once.
        val withNote = EarningsDisplay.resultsDisclosures(null, listOf("Results: Sample", EarningsDisplay.SAMPLE_SENTENCE), sampleData = true)
        assertEquals(1, withNote.count { it == EarningsDisplay.SAMPLE_SENTENCE })
    }

    // ---------- Phase 5D.4 ----------

    @Test fun firstSentenceNeverSplitsAmountsOrPercents() {
        val text = "The company earned less per share than analysts expected. This is called an EPS miss. It does not guarantee that the stock price will fall."
        val (first, rest) = EarningsDisplay.firstSentence(text)
        assertEquals("The company earned less per share than analysts expected.", first)
        assertEquals(text, "$first $rest", "nothing is lost")
        assertEquals("EPS was C$1.94 against an estimate of C$2.00 (GAAP diluted)." to null,
            EarningsDisplay.firstSentence("EPS was C$1.94 against an estimate of C$2.00 (GAAP diluted)."))
        assertEquals("Revenue rose 3.0% year over year." to "It can reflect seasonality.", EarningsDisplay.firstSentence("Revenue rose 3.0% year over year. It can reflect seasonality."))
        assertEquals("One line" to null, EarningsDisplay.firstSentence("One line"))
    }

    @Test fun secondaryCaptionKeepsEveryLine() {
        assertEquals("Growth vs last quarter: +4.2% · Can reflect seasonality.", EarningsDisplay.secondaryCaption(listOf(MetricLine("Growth vs last quarter", "+4.2%", "Can reflect seasonality."))))
        assertNull(EarningsDisplay.secondaryCaption(emptyList()))
    }

    @Test fun detailsNotesFoldEachOnce() {
        val notes = EarningsDisplay.detailsDisclosures(listOf("Estimates are analysts' consensus.", EarningsDisplay.SAMPLE_SENTENCE), sampleData = true)
        assertEquals(1, notes.count { it == EarningsDisplay.SAMPLE_SENTENCE })
        assertTrue(EarningsDisplay.DETAILS_NOT_ADVICE in notes)
        assertFalse(EarningsDisplay.SAMPLE_SENTENCE in EarningsDisplay.detailsDisclosures(emptyList(), sampleData = false))
    }

    @Test fun quarterLabelsNeverPointAtTheWrongQuarter() {
        val four = listOf("Q3 25", "Q4 25", "Q1 26", "Q2 26")
        assertEquals(four, EarningsDisplay.historyXLabels(four, largeText = false))
        assertEquals(listOf("Q3 25", "Q2 26"), EarningsDisplay.historyXLabels(four, largeText = true))
        assertEquals(listOf("Q1 25", "Q2 26"), EarningsDisplay.historyXLabels(listOf("Q1 25", "Q2 25", "Q3 25", "Q4 25", "Q1 26", "Q2 26"), largeText = false))
    }

    @Test fun resultsTitleDropsTheRepeatedWords() {
        assertEquals("Q2 FY2026 results", EarningsDisplay.resultsTitle("Q2 FY2026 Earnings Results"))
        assertEquals("Something else", EarningsDisplay.resultsTitle("Something else"))
    }
}

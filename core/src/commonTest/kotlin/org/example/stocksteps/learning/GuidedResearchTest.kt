package org.example.stocksteps.learning

import kotlinx.serialization.json.Json
import org.example.stocksteps.model.*
import kotlin.test.*

internal fun fact(value: Double, date: String? = null, currency: String? = "USD") =
    FinancialFact(value = value, source = FinancialSource.PROVIDER_DIRECT, availability = FinancialAvailability.AVAILABLE,
        basis = FinancialBasis("FY", date, currency = currency))

internal fun year(fy: Int, revenue: Double?, netIncome: Double? = null, currency: String = "USD", eps: Double? = null, cash: Double? = null, debt: Double? = null, fcf: Double? = null) =
    FinancialPeriodStatement("FY", fy, "$fy-09-30", currency, revenue = revenue, netIncome = netIncome, epsDiluted = eps, cash = cash, totalDebt = debt, freeCashFlow = fcf)

internal fun company(
    symbol: String = "TEST",
    name: String = "Test Corp Inc.",
    sector: String? = "Technology",
    etf: Boolean? = false,
    history: List<FinancialPeriodStatement> = listOf(year(2025, 120.0e9, 24.0e9, eps = 2.0), year(2024, 100.0e9, 20.0e9)),
    facts: Map<String, FinancialFact> = mapOf("netMargin" to fact(20.0), "cash" to fact(30.0e9), "debt" to fact(10.0e9), "freeCashFlow" to fact(15.0e9)),
    valuation: Map<String, FinancialFact> = mapOf("pe" to fact(25.0)),
    peHistory: HistoricalComparison? = null,
    price: Double? = 50.0,
    withFundamentals: Boolean = true,
    description: String? = "Test Corp designs software. It sells subscriptions to businesses. It also offers support. More details follow."
) = CompanyDetails(
    symbol = symbol,
    profile = CompanyProfile(symbol, name, description = description, sector = sector, industry = "Software", currency = "USD", exchange = "NASDAQ", isEtf = etf),
    quote = StockQuote(symbol, name, price, 0.0, 0.0, null, null),
    fundamentals = if (withFundamentals) CompanyFundamentals(symbol, CompanyFinancials(profitability = facts), CompanyValuation(valuation, listOfNotNull(peHistory?.let { "pe" to it }).toMap()),
        retrievedAt = "2026-10-01T00:00:00Z", history = history) else null
)

class GuidedResearchEngineTest {
    private val today = "2026-10-08"
    private fun build(details: CompanyDetails) = GuidedResearchEngine.build(details, today)

    @Test fun fiveStepsInOrderWithQuestionsUsingTheShortCompanyName() {
        val snapshot = build(company())
        assertEquals(listOf(1, 2, 3, 4, 5), snapshot.steps.map { it.step.number })
        assertEquals("What does Test Corp do?", snapshot.step(1)!!.question)
        assertEquals("Is Test Corp's stock expensive?", snapshot.step(5)!!.question)
        assertEquals(CompanyKind.OPERATING, snapshot.kind)
        assertEquals("Apple", GuidedResearchEngine.shortName("Apple Inc."))
        assertEquals("Coca-Cola", GuidedResearchEngine.shortName("Coca-Cola Co"))
        assertEquals("JPMorgan Chase", GuidedResearchEngine.shortName("JPMorgan Chase & Co."))
        assertEquals("Microsoft", GuidedResearchEngine.shortName("Microsoft Corporation"))
    }

    @Test fun businessUsesTheDescriptionAndNeverEstimatesSegments() {
        val step = build(company()).step(1)!!
        assertEquals("Test Corp designs software. It sells subscriptions to businesses. It also offers support.", step.meaning)
        assertTrue("doesn't estimate" in step.limitation)
        val missing = build(company(description = null)).step(1)!!
        assertEquals(StepAvailability.PARTIAL, missing.availability)
        assertNotNull(missing.unavailableReason)
    }

    @Test fun growthIncreaseDecreaseAndUnchangedBand() {
        assertEquals("Revenue increased 20.0% compared with last year.", build(company()).step(2)!!.meaning)
        val down = build(company(history = listOf(year(2025, 90.0, 1.0), year(2024, 100.0, 1.0)))).step(2)!!
        assertEquals("Revenue decreased 10.0% compared with last year.", down.meaning)
        val flat = build(company(history = listOf(year(2025, 100.5, 1.0), year(2024, 100.0, 1.0)))).step(2)!!
        assertTrue(flat.meaning!!.startsWith("Revenue remained about the same"))
    }

    @Test fun growthChartUsesUpToFiveFiscalYearsOldestFirstInOneCurrency() {
        val years = (2019..2025).map { year(it, it * 1.0e9, 1.0) }.reversed()
        val chart = build(company(history = years)).step(2)!!.chart!!
        assertEquals(listOf("FY2021", "FY2022", "FY2023", "FY2024", "FY2025"), chart.bars.map { it.label })
        val mixed = build(company(history = listOf(year(2025, 2.0e9, 1.0), year(2024, 1.0e9, 1.0, currency = "CAD"), year(2023, 1.0e9, 1.0)))).step(2)!!
        assertNull(mixed.figures.firstOrNull { it.label == "Previous year" }, "a prior year in another currency is never compared")
    }

    @Test fun zeroPriorRevenueIsNotAPercentage() {
        val step = build(company(history = listOf(year(2025, 50.0, 1.0), year(2024, 0.0, 1.0)))).step(2)!!
        assertNull(step.meaning)
        assertEquals(StepAvailability.PARTIAL, step.availability)
        assertTrue(step.unavailableReason!!.contains("zero or negative"))
        val single = build(company(history = listOf(year(2025, 50.0, 1.0)))).step(2)!!
        assertTrue(single.unavailableReason!!.contains("Only one year"))
    }

    @Test fun profitExplainsMarginPerHundredDollarsAndLosses() {
        val profit = build(company()).step(3)!!
        assertTrue(profit.meaning!!.contains("made a profit of \$24.00 billion"))
        assertTrue(profit.meaning!!.contains("about \$20.00 for every \$100 of revenue"))
        assertTrue(profit.meaning!!.contains("Free cash flow was positive"))
        val loss = build(company(history = listOf(year(2025, 10.0e9, -2.0e9), year(2024, 9.0e9, -1.0e9)), facts = emptyMap())).step(3)!!
        assertTrue(loss.meaning!!.contains("had a loss of \$2.00 billion"))
        assertEquals("Loss (net income)", loss.figures[1].label)
        assertEquals("Reported a loss.", loss.summary)
        assertEquals(StepAvailability.PARTIAL, loss.availability)
    }

    @Test fun debtComparesCashAndDebtAndComputesNetCash() {
        val step = build(company()).step(4)!!
        assertEquals("Net cash (cash − debt)", step.figures[2].label)
        assertEquals("\$20.00 billion", step.figures[2].value)
        assertTrue(step.meaning!!.contains("more cash"))
        val owes = build(company(facts = mapOf("cash" to fact(1.0e9), "debt" to fact(5.0e9)))).step(4)!!
        assertEquals("Net debt (debt − cash)", owes.figures[2].label)
        assertTrue(owes.meaning!!.contains("That's common"))
    }

    @Test fun banksGetASimplifiedDebtStep() {
        val bank = build(company(name = "JPMorgan Chase & Co.", sector = "Financial Services"))
        assertEquals(CompanyKind.FINANCIAL, bank.kind)
        val debt = bank.step(4)!!
        assertTrue(debt.figures.isEmpty())
        assertNull(debt.chart)
        assertTrue(debt.meaning!!.contains("borrow and lend as their business"))
    }

    @Test fun valuationShowsTrailingPeAndHistoryOnlyWhenReliable() {
        val reliable = HistoricalComparison(average = 20.0, validCount = 5, reliable = true)
        val step = build(company(peHistory = reliable)).step(5)!!
        assertEquals("25.00", step.figures.first { it.label == "P/E ratio" }.value)
        assertTrue(step.meaning!!.contains("averaged about 20.00"))
        assertNotNull(step.chart)
        assertTrue(step.limitation.contains("last 12 months"))
        val unreliable = build(company(peHistory = reliable.copy(reliable = false))).step(5)!!
        assertNull(unreliable.chart)
        assertFalse(unreliable.meaning!!.contains("averaged"))
    }

    @Test fun nonPositiveEarningsNeverShowAMisleadingPe() {
        val step = build(company(history = listOf(year(2025, 10.0, -1.0, eps = -0.5), year(2024, 9.0, -1.0)), valuation = emptyMap())).step(5)!!
        assertEquals("Not meaningful", step.figures.first { it.label == "P/E ratio" }.value)
        assertTrue(step.meaning!!.contains("doesn't currently have positive earnings"))
        assertEquals(StepAvailability.PARTIAL, step.availability)
        assertEquals("P/E not meaningful right now.", step.summary)
    }

    @Test fun etfsExplainWhyCompanyFiguresDoNotApply() {
        val fund = build(company(symbol = "SPY", name = "SPDR S&P 500 ETF Trust", etf = true))
        assertEquals(CompanyKind.FUND, fund.kind)
        assertTrue(fund.steps.drop(1).all { "fund" in (it.unavailableReason ?: it.meaning ?: "") })
        assertTrue(fund.step(3)!!.figures.isEmpty())
        assertEquals(StepAvailability.UNAVAILABLE, fund.step(4)!!.availability)
    }

    @Test fun unsupportedCompaniesStillOpenEveryStep() {
        val snapshot = build(company(withFundamentals = false))
        assertEquals(CompanyKind.UNSUPPORTED, snapshot.kind)
        assertEquals(5, snapshot.steps.size)
        assertEquals(StepAvailability.UNAVAILABLE, snapshot.step(3)!!.availability)
        assertTrue(snapshot.steps.all { it.quiz.options.isNotEmpty() })
    }

    @Test fun staleStatementsAreLabelled() {
        val old = build(company(history = listOf(year(2023, 10.0, 1.0), year(2022, 9.0, 1.0))))
        assertTrue(old.stale)
        assertTrue(old.notes.any { "18 months" in it })
        assertFalse(build(company()).stale)
    }

    @Test fun summaryHasNoRecommendationWords() {
        val text = build(company()).steps.joinToString(" ") { it.summary + " " + (it.meaning ?: "") + it.takeaway }.lowercase()
        val words = text.split(Regex("[^a-z]+")).toSet()
        listOf("buy", "sell", "hold", "undervalued", "overvalued", "score", "predict").forEach { assertFalse(it in words, it) }
    }

    @Test fun eachStepHasAnAccessibleSummary() {
        val step = build(company()).step(2)!!
        assertTrue(step.accessibility.startsWith("Step 2 of 5. Is Test Corp growing?"))
        assertTrue(step.accessibility.contains("Latest annual revenue: \$120.00 billion"))
    }
}

class QuizAndProgressTest {
    @Test fun everyStepHasOneDeterministicVersionedQuiz() {
        val quizzes = GuidedResearchContent.quizzes.values
        assertEquals(5, quizzes.size)
        assertEquals(quizzes.size, quizzes.map { it.id }.toSet().size)
        quizzes.forEach { quiz ->
            assertTrue(quiz.options.any { it.id == quiz.correctOptionId })
            assertEquals(GuidedResearchContent.VERSION, quiz.version)
            assertNotNull(BeginnerEducation.entry(quiz.topic), quiz.topic)
        }
    }

    @Test fun answeringGivesImmediateFeedbackWithoutPenalty() {
        val quiz = GuidedResearchContent.quizzes.getValue(ResearchStep.GROWTH)
        val wrong = QuizEngine.answer(quiz, "b")
        assertFalse(wrong.correct)
        assertEquals("Not quite", wrong.feedback)
        assertTrue(wrong.explanation.contains("20%"))
        val right = QuizEngine.answer(quiz, "a")
        assertTrue(right.correct)
        assertEquals("Correct", right.feedback)
        assertFailsWith<IllegalArgumentException> { QuizEngine.answer(quiz, "zz") }
    }

    @Test fun oldQuizVersionsCountAsNotAttempted() {
        val quiz = GuidedResearchContent.quizzes.getValue(ResearchStep.PROFIT)
        val progress = ResearchProgress("AAPL", "Apple", quizzes = mapOf(quiz.id to QuizRecord(true, 0)), startedAt = 1, lastVisited = 1)
        assertNull(progress.quiz(quiz))
        assertNotNull(progress.copy(quizzes = mapOf(quiz.id to QuizRecord(true, quiz.version))).quiz(quiz))
    }

    @Test fun resumeStepAndCompletion() {
        val p = ResearchProgress("AAPL", "Apple", completedSteps = listOf(1, 2, 3), currentStep = 4, startedAt = 1, lastVisited = 2)
        assertEquals(3, p.completedCount)
        assertEquals(4, p.resumeStep)
        assertEquals(4, p.copy(currentStep = 0).resumeStep)
        assertEquals(1, p.copy(completedSteps = listOf(2, 3), currentStep = 0).resumeStep)
        assertTrue(p.copy(completedSteps = listOf(1, 2, 3, 4, 5, 5)).finished)
        assertFalse(p.copy(completedSteps = listOf(1, 2, 3, 4, 9)).finished)
    }

    @Test fun documentMergeKeepsTheMostRecentVisitPerCompany() {
        val local = LearningProgressDocument(listOf(ResearchProgress("AAPL", "Apple", listOf(1, 2), startedAt = 1, lastVisited = 20), ResearchProgress("KO", "Coca-Cola", listOf(1), startedAt = 1, lastVisited = 5)))
        val remote = LearningProgressDocument(listOf(ResearchProgress("AAPL", "Apple", listOf(1), startedAt = 1, lastVisited = 10), ResearchProgress("jpm", "JPMorgan", listOf(1, 2, 3), startedAt = 1, lastVisited = 30)))
        val merged = local.merge(remote)
        assertEquals(listOf("jpm", "AAPL", "KO"), merged.journeys.map { it.symbol })
        assertEquals(listOf(1, 2), merged.journeys.first { it.symbol == "AAPL" }.completedSteps)
    }

    @Test fun progressDocumentRoundTripsAsJson() {
        val json = Json { ignoreUnknownKeys = true }
        val doc = LearningProgressDocument(listOf(ResearchProgress("AAPL", "Apple", listOf(1), 2, mapOf("q" to QuizRecord(true, 1)), 1, 2)), 3)
        assertEquals(doc, json.decodeFromString(LearningProgressDocument.serializer(), json.encodeToString(LearningProgressDocument.serializer(), doc)))
    }

    @Test fun educationAnswersWhatWhyHowAndLimits() {
        BeginnerEducation.entries.forEach { e ->
            listOf(e.short, e.why, e.interpret, e.limitations).forEach { assertTrue(it.isNotBlank(), e.id) }
        }
        assertEquals(BeginnerEducation.entries.size, BeginnerEducation.entries.map { it.id }.toSet().size)
        GuidedResearchContent.steps.values.flatMap { it.learnMore }.forEach { assertNotNull(BeginnerEducation.entry(it), it) }
    }
}

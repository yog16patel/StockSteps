package org.example.stocksteps.practice

import org.example.stocksteps.learning.Quiz
import org.example.stocksteps.learning.QuizOption
import org.example.stocksteps.portfolio.Decimal

/**
 * Guided practice challenges. Completion never depends on profit: a first simulated purchase, a
 * short knowledge check, or a reflection. Free users get the first challenge and previews of the
 * rest; completed challenges stay completed when premium access ends.
 */
object PracticeChallenges {
    const val VERSION = 1
    const val FIRST_BUY = "first-investment"

    data class Challenge(val id: String, val order: Int, val title: String, val summary: String, val kind: ChallengeKind, val lesson: List<String>, val quiz: Quiz?, val freeAccess: Boolean)

    val catalog: List<Challenge> = listOf(
        Challenge(FIRST_BUY, 1, "Your first virtual investment", "Learn how buying shares works.", ChallengeKind.FIRST_BUY,
            listOf("When you buy a share you own a small piece of a company. Its value goes up and down with the share price.",
                "In your Practice Portfolio, choose a company, review the simulated order and confirm it. No real money is used."),
            null, freeAccess = true),
        Challenge("explore-etf", 2, "Explore an ETF", "Learn how ETFs differ from individual stocks.", ChallengeKind.QUIZ,
            listOf("An ETF (exchange-traded fund) holds many investments in one product, such as the 500 companies in an S&P 500 fund.",
                "Buying one ETF share spreads your money across everything the fund holds. A single stock depends on one company.",
                "Look up an ETF such as SPY or XIU in search to see how it's described. You don't need to buy one."),
            Quiz("pc-etf-1", 2, "What does one share of a broad-market ETF give you?",
                listOf(QuizOption("a", "A small piece of many companies at once"), QuizOption("b", "Ownership of the fund's management company"), QuizOption("c", "A guaranteed return")),
                "a", "An ETF bundles many investments, so one share spreads your money across all of them. No ETF guarantees a return.", "diversification", VERSION),
            freeAccess = false),
        Challenge("diversification", 3, "Understand diversification", "Learn why concentration matters.", ChallengeKind.QUIZ,
            listOf("Diversification means spreading money across different companies, industries or funds.",
                "Check your allocation: if one holding is most of your simulated money, its ups and downs drive your whole portfolio."),
            Quiz("pc-div-1", 3, "One company is 70% of a portfolio. What does that mean?",
                listOf(QuizOption("a", "That company's price moves have a big effect on the whole portfolio"), QuizOption("b", "The portfolio is guaranteed to grow"), QuizOption("c", "The other 30% doesn't matter")),
                "a", "A large weight concentrates risk: a 10% drop in that company moves the whole portfolio about 7%.", "diversification", VERSION),
            freeAccess = false),
        Challenge("market-loss", 4, "Understand a market loss", "Why prices fluctuate, and what an unrealized loss is.", ChallengeKind.QUIZ,
            listOf("Share prices change every trading day as investors react to news, results and the wider economy.",
                "If a holding is worth less than you paid, that's an unrealized loss: it only becomes realized if you sell at that price."),
            Quiz("pc-loss-1", 4, "You bought a share at \$50 and it's now \$45. You haven't sold. What is this?",
                listOf(QuizOption("a", "An unrealized loss of \$5 per share"), QuizOption("b", "A realized loss you can't recover"), QuizOption("c", "A mistake in the price")),
                "a", "Until you sell, a drop is unrealized. Prices can keep moving either way; nobody can promise they'll recover.", "netIncome", VERSION),
            freeAccess = false),
        Challenge("review-decisions", 5, "Review your decisions", "Reflect on why you chose an investment.", ChallengeKind.REFLECTION,
            listOf("Writing down why you invested helps you learn from the outcome, whichever way it goes.",
                "There's no right answer here. Pick the reason closest to yours."),
            Quiz("pc-reflect-1", 5, "Why did you choose your most recent practice investment?",
                listOf(QuizOption("a", "I understand what the company does"), QuizOption("b", "I wanted to try an ETF"), QuizOption("c", "Its recent price movement caught my eye"), QuizOption("d", "I was curious and wanted to practice")),
                "a", "Every reason is a starting point. Knowing what a company does and how it makes money helps you judge it over time.", "diversification", VERSION),
            freeAccess = false)
    )

    fun find(id: String) = catalog.firstOrNull { it.id == id }

    fun views(entitlement: PracticeEntitlement, completed: Map<String, Long>): List<PracticeChallengeView> = catalog.map { c ->
        PracticeChallengeView(c.id, c.order, c.title, c.summary, c.kind, c.lesson, c.quiz, completed[c.id],
            available = c.freeAccess || entitlement.has(PracticeCapability.ALL_CHALLENGES))
    }
}

/**
 * Deterministic observations about the user's own simulated portfolio. Only facts the data
 * supports; never ratings, predictions or advice.
 */
object PracticeInsights {
    fun build(holdings: List<PracticeHoldingView>, cash: Decimal, total: Decimal?, premium: Boolean, baseCurrency: String): List<PracticeInsight> {
        val out = mutableListOf<PracticeInsight>()
        when (holdings.size) {
            0 -> out += PracticeInsight("empty", "You haven't made a practice investment yet. Your ${PracticeEngine.format(cash, baseCurrency)} of virtual cash is ready to use.")
            1 -> out += PracticeInsight("count", "You currently hold one company.")
            else -> out += PracticeInsight("count", "You currently hold ${holdings.size} different investments.")
        }
        if (total != null && total > Decimal.ZERO && holdings.isNotEmpty()) {
            PracticeEngine.percent(cash, total)?.let { out += PracticeInsight("cash", "Your virtual cash represents ${it.display(0)}% of total portfolio value.") }
        }
        if (!premium) return out
        val values = holdings.mapNotNull { h -> h.marketValue?.let { h to Decimal.parse(it) } }
        val invested = values.fold(Decimal.ZERO) { s, (_, v) -> s + v }
        if (values.size == holdings.size && invested > Decimal.ZERO && holdings.size > 1) {
            val (top, value) = values.maxBy { it.second }
            val share = PracticeEngine.percent(value, invested)!!
            out += PracticeInsight("concentration", "${top.instrument.symbol} represents ${share.display(0)}% of your simulated holdings." +
                if (share >= Decimal.parse("50")) " When one investment is this large, its price moves have a big effect on the whole portfolio." else "", premium = true)
        }
        val kinds = holdings.map { it.instrument.kind }.toSet()
        if (InstrumentKind.ETF in kinds && InstrumentKind.STOCK in kinds) out += PracticeInsight("mix", "Your portfolio includes both individual stocks and ETFs.", premium = true)
        else if (kinds == setOf(InstrumentKind.ETF)) out += PracticeInsight("mix", "Your holdings are all ETFs, each of which holds many investments.", premium = true)
        val sectors = holdings.mapNotNull { it.instrument.sector }.toSet()
        if (sectors.size > 1) out += PracticeInsight("sectors", "Your companies span ${sectors.size} sectors: ${sectors.sorted().joinToString()}.", premium = true)
        val currencies = holdings.map { it.instrument.currency }.toSet()
        if (currencies.size > 1) out += PracticeInsight("currency", "You hold investments priced in ${currencies.sorted().joinToString(" and ")}; exchange rates affect their value in $baseCurrency.", premium = true)
        return out
    }
}

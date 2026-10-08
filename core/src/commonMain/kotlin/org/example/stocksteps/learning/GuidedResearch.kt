package org.example.stocksteps.learning

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.example.stocksteps.markets.MarketsPresenter
import org.example.stocksteps.model.*
import kotlin.math.abs
import kotlin.math.round

// ---------- Steps, quizzes and progress ----------

enum class ResearchStep(val number: Int, val key: String) {
    BUSINESS(1, "business"), GROWTH(2, "growth"), PROFIT(3, "profit"), DEBT(4, "debt"), VALUATION(5, "valuation");
    companion object {
        const val COUNT = 5
        fun of(number: Int) = entries.firstOrNull { it.number == number }
    }
}

@Serializable data class QuizOption(val id: String, val text: String)

/** Single-answer multiple choice (the model leaves room for other types later). */
@Serializable
data class Quiz(
    val id: String,
    val step: Int,
    val question: String,
    val options: List<QuizOption>,
    val correctOptionId: String,
    val explanation: String,
    /** BeginnerEducation entry the quiz is about. */
    val topic: String,
    val version: Int,
    val type: String = "single-choice"
)

data class QuizResult(val correct: Boolean, val selectedOptionId: String, val explanation: String) {
    val feedback: String get() = if (correct) "Correct" else "Not quite"
}

/** No timers, scores or penalties: answering just shows the explanation; retrying is always allowed. */
object QuizEngine {
    fun answer(quiz: Quiz, optionId: String): QuizResult {
        require(quiz.options.any { it.id == optionId }) { "Unknown option" }
        return QuizResult(optionId == quiz.correctOptionId, optionId, quiz.explanation)
    }
}

@Serializable data class QuizRecord(val correct: Boolean, val version: Int)

/** One company's research journey. Steps never lock: any step can be opened at any time. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResearchProgress(
    val symbol: String,
    val name: String,
    @EncodeDefault val completedSteps: List<Int> = emptyList(),
    /** 0 = overview, 1–5 = a step, 6 = summary. */
    val currentStep: Int = 0,
    @EncodeDefault val quizzes: Map<String, QuizRecord> = emptyMap(),
    val startedAt: Long,
    val lastVisited: Long,
    val contentVersion: Int = GuidedResearchContent.VERSION
) {
    val completedCount: Int get() = completedSteps.distinct().count { it in 1..ResearchStep.COUNT }
    val finished: Boolean get() = completedCount == ResearchStep.COUNT
    /** The step to resume at: the current one, else the first not completed. */
    val resumeStep: Int get() = currentStep.takeIf { it in 1..ResearchStep.COUNT && it !in completedSteps }
        ?: (1..ResearchStep.COUNT).firstOrNull { it !in completedSteps } ?: ResearchStep.COUNT
    /** A quiz answered under an older content version counts as not attempted. */
    fun quiz(quiz: Quiz): QuizRecord? = quizzes[quiz.id]?.takeIf { it.version == quiz.version }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LearningProgressDocument(@EncodeDefault val journeys: List<ResearchProgress> = emptyList(), val updatedAt: Long = 0) {
    /** Per company, the most recently visited copy wins. */
    fun merge(other: LearningProgressDocument): LearningProgressDocument =
        LearningProgressDocument((journeys + other.journeys).groupBy { it.symbol.uppercase() }.values.map { copies -> copies.maxBy { it.lastVisited } }
            .sortedByDescending { it.lastVisited }.take(MAX_JOURNEYS), maxOf(updatedAt, other.updatedAt))
    companion object { const val MAX_JOURNEYS = 50 }
}

@Serializable data class ResearchQuestion(val question: String, val step: Int? = null)
@OptIn(ExperimentalSerializationApi::class)
@Serializable data class ResearchAnswer(val answer: String, @EncodeDefault val sources: List<String> = emptyList(), val remainingToday: Int? = null)

// ---------- Reviewed content (questions, explanations, takeaways, quizzes) ----------

/** All step wording and quizzes, versioned. Company names are inserted; numbers come from data. */
object GuidedResearchContent {
    const val VERSION = 1

    data class StepText(val title: (String) -> String, val description: String, val intro: List<String>, val limitation: String, val takeaway: String, val learnMore: List<String>)

    val steps: Map<ResearchStep, StepText> = mapOf(
        ResearchStep.BUSINESS to StepText({ "What does $it do?" }, "Understand the business before looking at the stock price.",
            listOf("Before investing in a company, it helps to understand what it sells, who its customers are, and how it earns revenue."),
            "A company description is a summary. It doesn't show how much each product earns or how the business may change.",
            "Start every company research by being able to explain, in one sentence, how the company makes money.",
            listOf("revenue")),
        ResearchStep.GROWTH to StepText({ "Is $it growing?" }, "Look at whether sales are rising or falling.",
            listOf("Revenue is the money a company earns from selling its products or services before expenses.",
                "Revenue growth shows whether the company is bringing in more or less money compared with an earlier period."),
            "Growing revenue does not automatically mean a company is profitable or that its stock will perform well.",
            "Compare revenue with the same period a year earlier to see the direction of the business.",
            listOf("revenue", "revenueGrowth")),
        ResearchStep.PROFIT to StepText({ "Is $it making money?" }, "See the difference between revenue and profit.",
            listOf("Revenue is the money a company brings in.", "Profit is what remains after expenses."),
            "A temporary loss doesn't automatically mean a company is failing, and a profit alone doesn't make it a good investment.",
            "Revenue tells you how much a company sells; profit tells you how much it keeps.",
            listOf("netIncome", "netMargin", "freeCashFlow")),
        ResearchStep.DEBT to StepText({ "Does $it have a lot of debt?" }, "Compare what the company owes with the cash it holds.",
            listOf("Companies sometimes borrow money to operate or grow. Debt is not automatically bad, but understanding how much a company owes is important."),
            "A company with debt may still be financially healthy, while a company with little debt can still face business risks.",
            "Look at debt next to cash and profit, not on its own.",
            listOf("debt", "cash", "debtEquity")),
        ResearchStep.VALUATION to StepText({ "Is ${it}'s stock expensive?" }, "Learn what the P/E ratio does and doesn't tell you.",
            listOf("P/E compares a company's share price with its earnings per share.",
                "A higher P/E means investors are paying more for each dollar of reported earnings."),
            "A low or high P/E ratio does not tell the whole story. Growth expectations, business risks, and profitability can influence valuation.",
            "Use P/E as a starting question (\"why is it this high or low?\"), not as an answer.",
            listOf("pe", "eps"))
    )

    val quizzes: Map<ResearchStep, Quiz> = mapOf(
        ResearchStep.BUSINESS to Quiz("q-business-1", 1, "Which question helps you understand a company's business?",
            listOf(QuizOption("a", "What products or services does it sell?"), QuizOption("b", "What color is its company logo?"), QuizOption("c", "How many letters are in its stock symbol?")),
            "a", "Knowing what a company sells, and to whom, tells you how it earns money. The logo and symbol don't.", "revenue", VERSION),
        ResearchStep.GROWTH to Quiz("q-growth-1", 2, "If a company's revenue rises from \$100 million to \$120 million, what does that tell us?",
            listOf(QuizOption("a", "Revenue increased by 20%."), QuizOption("b", "Profit increased by 20%."), QuizOption("c", "The stock price must increase.")),
            "a", "(120 − 100) ÷ 100 = 20% more revenue. It says nothing on its own about profit or the stock price.", "revenueGrowth", VERSION),
        ResearchStep.PROFIT to Quiz("q-profit-1", 3, "A company has \$100 of revenue and \$90 of expenses. What is its profit?",
            listOf(QuizOption("a", "\$100"), QuizOption("b", "\$10"), QuizOption("c", "\$190")),
            "b", "Profit is what's left after expenses: \$100 − \$90 = \$10. Revenue is the \$100 the company brought in.", "netIncome", VERSION),
        ResearchStep.DEBT to Quiz("q-debt-1", 4, "Does having debt always mean a company is financially unhealthy?",
            listOf(QuizOption("a", "Yes, any debt is a bad sign."), QuizOption("b", "No. Many healthy companies borrow, and what matters is whether they can comfortably repay.")),
            "b", "Debt can fund growth. It becomes a concern when a company can't comfortably pay interest and repay what it owes.", "debt", VERSION),
        ResearchStep.VALUATION to Quiz("q-valuation-1", 5, "Does a lower P/E always mean a better investment?",
            listOf(QuizOption("a", "Yes, lower is always better."), QuizOption("b", "No. A low P/E can reflect risks or slowing growth, and a high one can reflect expected growth.")),
            "b", "P/E is a starting point for questions. A low P/E isn't proof a stock is undervalued, and a high one isn't proof it's overvalued.", "pe", VERSION)
    )
}

// ---------- Research built from verified data ----------

enum class CompanyKind { OPERATING, FINANCIAL, FUND, UNSUPPORTED }
enum class StepAvailability { AVAILABLE, PARTIAL, UNAVAILABLE }

data class ResearchFigure(val label: String, val value: String, val detail: String? = null)
data class ChartBar(val label: String, val value: Double, val display: String)
data class SimpleChart(val title: String, val bars: List<ChartBar>, val description: String, val note: String? = null)

data class StepView(
    val step: ResearchStep,
    val question: String,
    val description: String,
    val intro: List<String>,
    val figures: List<ResearchFigure>,
    val chart: SimpleChart?,
    /** What the numbers show, in one or two plain sentences. */
    val meaning: String?,
    val limitation: String,
    val takeaway: String,
    val learnMore: List<String>,
    val availability: StepAvailability,
    val unavailableReason: String? = null,
    /** "Fiscal year 2025 (ended Sep 30, 2025) · USD". */
    val period: String? = null,
    /** One line for the completion summary. */
    val summary: String,
    val quiz: Quiz
) {
    val accessibility: String get() = buildString {
        append("Step ${step.number} of ${ResearchStep.COUNT}. $question ")
        figures.forEach { append("${it.label}: ${it.value}. ") }
        meaning?.let { append(it) }
        unavailableReason?.let { append(" $it") }
    }
}

data class ResearchSnapshot(
    val symbol: String,
    val name: String,
    val logoUrl: String?,
    val sector: String?,
    val industry: String?,
    val kind: CompanyKind,
    val steps: List<StepView>,
    val stale: Boolean,
    val notes: List<String>
) {
    fun step(number: Int) = steps.firstOrNull { it.step.number == number }
}

/**
 * Builds the five steps from the same `CompanyDetails` (profile, quote, annual fundamentals) that
 * Company Details shows, so every number matches it. No metric is redefined here; values that are
 * missing or not meaningful are explained, never estimated.
 */
object GuidedResearchEngine {
    /** Revenue within ±1% of last year reads as "about unchanged". */
    const val UNCHANGED_BAND = 1.0
    private val FINANCIAL_SECTORS = setOf("Financial Services")

    fun build(details: CompanyDetails, today: String): ResearchSnapshot {
        val profile = details.profile
        val f = details.fundamentals
        val facts = f?.let { it.financials.metrics() + it.valuation.metrics }.orEmpty()
        val name = profile?.companyName ?: details.quote?.companyName ?: details.symbol
        val short = shortName(name)
        val kind = when {
            profile?.isEtf == true -> CompanyKind.FUND
            f == null || (f.history.isEmpty() && facts.values.none { it.availability == FinancialAvailability.AVAILABLE }) -> CompanyKind.UNSUPPORTED
            profile?.sector in FINANCIAL_SECTORS -> CompanyKind.FINANCIAL
            else -> CompanyKind.OPERATING
        }
        val years = f?.history.orEmpty().filter { it.period == "FY" && it.fiscalYear != null }.sortedByDescending { it.fiscalYear }
        val latest = years.firstOrNull()
        val currency = latest?.currency ?: facts["revenue"]?.basis?.currency ?: profile?.currency
        val period = latest?.let { "Fiscal year ${it.fiscalYear}${it.date?.let { d -> " (ended ${date(d)})" } ?: ""} · ${it.currency ?: currency ?: ""}".trimEnd(' ', '·') }
        fun value(id: String) = facts[id]?.takeIf { it.availability == FinancialAvailability.AVAILABLE }?.numericValue()?.takeIf { it.isFinite() }
        fun step(s: ResearchStep, block: (GuidedResearchContent.StepText) -> StepView) = block(GuidedResearchContent.steps.getValue(s))

        val fund = kind == CompanyKind.FUND
        val unsupported = kind == CompanyKind.UNSUPPORTED
        val fundReason = "This is a fund (ETF) that holds many companies. This five-step guide is designed for individual operating companies, so company profit and debt figures don't apply."
        val unsupportedReason = "Reported financial statements aren't available for this company, so this step can't use its numbers."

        val business = step(ResearchStep.BUSINESS) { t ->
            val description = profile?.description?.takeIf { it.isNotBlank() && !it.startsWith("Sample profile") }
            StepView(ResearchStep.BUSINESS, t.title(short), t.description, t.intro,
                listOfNotNull(ResearchFigure("Company", name), profile?.sector?.let { ResearchFigure("Sector", it) }, profile?.industry?.let { ResearchFigure("Industry", it) },
                    profile?.exchange?.let { ResearchFigure("Listed on", it) }),
                null,
                when {
                    fund -> fundReason
                    description != null -> firstSentences(description, 3)
                    else -> null
                },
                t.limitation + " Detailed revenue by product or segment isn't available here, so StockSteps doesn't estimate it.",
                t.takeaway, t.learnMore,
                if (description != null || fund) StepAvailability.AVAILABLE else if (profile != null) StepAvailability.PARTIAL else StepAvailability.UNAVAILABLE,
                if (description == null && !fund) "A business description isn't available for this company." else null,
                null,
                if (fund) "$short is a fund that holds many companies." else "$short operates in ${profile?.industry ?: profile?.sector ?: "its industry"}.",
                GuidedResearchContent.quizzes.getValue(ResearchStep.BUSINESS))
        }

        val growth = step(ResearchStep.GROWTH) { t ->
            val bars = years.take(5).reversed().filter { it.revenue != null && it.currency == currency }
                .map { ChartBar("FY${it.fiscalYear}", it.revenue!!, money(it.revenue, it.currency)) }
            val revenue = latest?.revenue ?: value("revenue")
            val prior = years.getOrNull(1)?.takeIf { it.currency == latest?.currency }?.revenue
            val growthPct = value("revenueGrowth") ?: if (revenue != null && prior != null && prior > 0) (revenue - prior) / prior * 100 else null
            val meaning = growthPct?.let { g ->
                when {
                    g > UNCHANGED_BAND -> "Revenue increased ${pct(g)} compared with last year."
                    g < -UNCHANGED_BAND -> "Revenue decreased ${pct(abs(g))} compared with last year."
                    else -> "Revenue remained about the same as last year (${signed(g)})."
                }
            }
            val reason = when {
                fund -> fundReason
                unsupported -> unsupportedReason
                prior != null && prior <= 0 -> "The previous year's revenue was zero or negative, so a growth percentage isn't meaningful."
                revenue == null -> "Revenue isn't available for this company."
                growthPct == null -> "Only one year of revenue is available, so growth can't be calculated yet."
                else -> null
            }
            StepView(ResearchStep.GROWTH, t.title(short), t.description, t.intro,
                if (fund) emptyList() else listOfNotNull(revenue?.let { ResearchFigure("Latest annual revenue", money(it, currency)) },
                    prior?.let { ResearchFigure("Previous year", money(it, currency)) }, growthPct?.let { ResearchFigure("Change from last year", signed(it)) }),
                bars.takeIf { it.size >= 2 && !fund }?.let { SimpleChart("Annual revenue", it, "Annual revenue: " + it.joinToString { b -> "${b.label} ${b.display}" } + ".") },
                if (fund) null else meaning, t.limitation, t.takeaway, t.learnMore,
                if (reason == null) StepAvailability.AVAILABLE else if (revenue != null && !fund) StepAvailability.PARTIAL else StepAvailability.UNAVAILABLE,
                reason, period,
                if (fund || meaning == null) "Revenue growth: not available." else meaning,
                GuidedResearchContent.quizzes.getValue(ResearchStep.GROWTH))
        }

        val profit = step(ResearchStep.PROFIT) { t ->
            val revenue = latest?.revenue ?: value("revenue")
            val netIncome = latest?.netIncome ?: value("netIncome")
            val margin = value("netMargin")
            val fcf = value("freeCashFlow") ?: latest?.freeCashFlow
            val meaning = when {
                netIncome == null -> null
                netIncome >= 0 -> "${short} made a profit of ${money(netIncome, currency)}." + (margin?.let { " It kept about ${money(it, currency, plain = true)} for every \$100 of revenue (a ${pct(it)} profit margin)." } ?: "")
                else -> "${short} had a loss of ${money(abs(netIncome), currency)} in this period: its expenses were higher than its revenue."
            }
            val fcfText = fcf?.let { if (it >= 0) "Free cash flow was positive: ${money(it, currency)} of cash left after spending on the business." else "Free cash flow was negative (${money(it, currency)}): the business used more cash than it produced." }
            StepView(ResearchStep.PROFIT, t.title(short), t.description, t.intro,
                if (fund) emptyList() else listOfNotNull(revenue?.let { ResearchFigure("Revenue", money(it, currency)) }, netIncome?.let { ResearchFigure(if (it >= 0) "Profit (net income)" else "Loss (net income)", money(it, currency)) },
                    margin?.let { ResearchFigure("Profit margin", pct(it)) }, fcf?.let { ResearchFigure("Free cash flow", money(it, currency), "Cash, not accounting profit") }),
                if (!fund && revenue != null && netIncome != null && revenue > 0) SimpleChart("Revenue and profit", listOf(ChartBar("Revenue", revenue, money(revenue, currency)), ChartBar(if (netIncome >= 0) "Profit" else "Loss", abs(netIncome), money(netIncome, currency))),
                    "Revenue ${money(revenue, currency)}; ${if (netIncome >= 0) "profit" else "loss"} ${money(netIncome, currency)}.",
                    "The gap between the bars is everything spent along the way (costs, interest and taxes); this picture is educational, not an accounting reconciliation.") else null,
                if (fund) null else listOfNotNull(meaning, fcfText).joinToString(" ").ifBlank { null },
                t.limitation, t.takeaway, t.learnMore,
                when { fund || unsupported -> StepAvailability.UNAVAILABLE; netIncome == null -> StepAvailability.UNAVAILABLE; margin == null || fcf == null -> StepAvailability.PARTIAL; else -> StepAvailability.AVAILABLE },
                when { fund -> fundReason; unsupported -> unsupportedReason; netIncome == null -> "Profit figures aren't available for this company."; else -> null }, period,
                when { fund || netIncome == null -> "Profitability: not available."; netIncome >= 0 -> "Made a profit${margin?.let { " (${pct(it)} margin)" } ?: ""}."; else -> "Reported a loss." },
                GuidedResearchContent.quizzes.getValue(ResearchStep.PROFIT))
        }

        val debt = step(ResearchStep.DEBT) { t ->
            val cash = value("cash") ?: latest?.cash
            val totalDebt = value("debt") ?: latest?.totalDebt
            val sheetCurrency = facts["debt"]?.basis?.currency ?: facts["cash"]?.basis?.currency ?: currency
            val sheetDate = facts["debt"]?.basis?.date ?: latest?.date
            val financial = kind == CompanyKind.FINANCIAL
            val de = value("debtEquity")
            val meaning = when {
                financial -> "$short is a ${profile?.industry?.lowercase() ?: "financial company"}. Banks and insurers borrow and lend as their business, so their debt can't be read the same way as other companies'."
                cash != null && totalDebt != null -> if (cash >= totalDebt) "$short holds more cash (${money(cash, sheetCurrency)}) than debt (${money(totalDebt, sheetCurrency)})."
                    else "$short owes more debt (${money(totalDebt, sheetCurrency)}) than it holds in cash (${money(cash, sheetCurrency)}). That's common; what matters is whether it can comfortably repay."
                else -> null
            }
            StepView(ResearchStep.DEBT, t.title(short), t.description, t.intro,
                if (fund || financial) emptyList() else listOfNotNull(cash?.let { ResearchFigure("Cash", money(it, sheetCurrency)) }, totalDebt?.let { ResearchFigure("Total debt", money(it, sheetCurrency)) },
                    if (cash != null && totalDebt != null) ResearchFigure(if (totalDebt >= cash) "Net debt (debt − cash)" else "Net cash (cash − debt)", money(abs(totalDebt - cash), sheetCurrency)) else null,
                    de?.let { ResearchFigure("Debt-to-equity", two(it), "Optional: debt compared with shareholders' equity") }),
                if (!fund && !financial && cash != null && totalDebt != null) SimpleChart("Cash and debt", listOf(ChartBar("Cash", cash, money(cash, sheetCurrency)), ChartBar("Debt", totalDebt, money(totalDebt, sheetCurrency))),
                    "Cash ${money(cash, sheetCurrency)}, debt ${money(totalDebt, sheetCurrency)}${sheetDate?.let { ", as of ${date(it)}" } ?: ""}.",
                    "Cash and debt alone don't decide whether a company can pay its bills.") else null,
                if (fund) null else meaning, t.limitation, t.takeaway, t.learnMore,
                when { fund || unsupported -> StepAvailability.UNAVAILABLE; financial -> StepAvailability.PARTIAL; cash == null || totalDebt == null -> StepAvailability.PARTIAL; else -> StepAvailability.AVAILABLE },
                when { fund -> fundReason; unsupported -> unsupportedReason; financial -> null; cash == null && totalDebt == null -> "Cash and debt figures aren't available for this company."; else -> null },
                sheetDate?.let { "Balance sheet as of ${date(it)} · ${sheetCurrency ?: ""}".trimEnd(' ', '·') },
                when { fund || unsupported -> "Debt: not available."; financial -> "Financial company: debt works differently."; cash != null && totalDebt != null -> if (cash >= totalDebt) "More cash than debt." else "More debt than cash."; else -> "Debt: partly available." },
                GuidedResearchContent.quizzes.getValue(ResearchStep.DEBT))
        }

        val valuation = step(ResearchStep.VALUATION) { t ->
            val price = details.quote?.price?.takeIf { it > 0 }
            val eps = value("eps") ?: latest?.epsDiluted
            val pe = value("pe")
            val peFact = facts["pe"]
            val history = f?.valuation?.historical?.get("pe")?.takeIf { it.reliable }?.average
            val negative = (eps != null && eps <= 0) || peFact?.availability == FinancialAvailability.NON_POSITIVE_DENOMINATOR
            val meaning = when {
                fund -> null
                pe != null -> "Investors are paying about ${two(pe)} times $short's earnings over the last 12 months." +
                    (history?.let { " Over the past five years its P/E averaged about ${two(it)}." } ?: "")
                negative -> "$short doesn't currently have positive earnings, so a P/E ratio isn't meaningful."
                else -> "This company doesn't currently have enough reported earnings data for a meaningful P/E ratio."
            }
            StepView(ResearchStep.VALUATION, t.title(short), t.description, t.intro,
                if (fund) listOfNotNull(price?.let { ResearchFigure("Share price", price(it, profile?.currency)) }) else listOfNotNull(
                    price?.let { ResearchFigure("Share price", price(it, profile?.currency)) },
                    eps?.let { ResearchFigure("Earnings per share", price(it, currency), "Latest fiscal year, diluted") },
                    pe?.let { ResearchFigure("P/E ratio", two(it), "Trailing 12 months") } ?: ResearchFigure("P/E ratio", "Not meaningful", if (negative) "Earnings are zero or negative" else "Not enough earnings data")),
                if (pe != null && history != null) SimpleChart("P/E now and on average", listOf(ChartBar("Now", pe, two(pe)), ChartBar("5-year average", history, two(history))),
                    "P/E now ${two(pe)}; five-year average ${two(history)}.", "A different P/E from the average is a question to explore, not a signal.") else null,
                if (fund) fundReason else meaning,
                t.limitation + (if (pe != null && eps != null) " P/E uses the last 12 months of earnings, so it may not exactly equal the price divided by the fiscal-year EPS shown." else ""),
                t.takeaway, t.learnMore,
                when { fund || unsupported -> StepAvailability.UNAVAILABLE; pe == null -> StepAvailability.PARTIAL; else -> StepAvailability.AVAILABLE },
                when { fund -> fundReason; unsupported -> unsupportedReason; else -> null }, "Price: latest quote · Earnings: trailing 12 months",
                when { fund -> "Valuation: P/E doesn't apply to a fund."; pe != null -> "P/E of about ${two(pe)}."; else -> "P/E not meaningful right now." },
                GuidedResearchContent.quizzes.getValue(ResearchStep.VALUATION))
        }

        val latestDate = latest?.date ?: facts["revenue"]?.basis?.date
        val stale = latestDate?.let { (MarketsPresenter.dayNumber(today) ?: 0) - (MarketsPresenter.dayNumber(it) ?: 0) > 550 } == true ||
            f?.retrievedAt?.take(10)?.let { (MarketsPresenter.dayNumber(today) ?: 0) - (MarketsPresenter.dayNumber(it) ?: 0) > 30 } == true
        val notes = listOfNotNull(
            if (stale) "Some of this company's financial statements are more than 18 months old, so figures may be out of date." else null,
            if (details.errors.isNotEmpty()) "Some information couldn't be loaded right now: ${details.errors.joinToString { it.section }}." else null,
            "Figures are the same ones shown on the company's details page. This guide explains them; it isn't a recommendation."
        )
        return ResearchSnapshot(details.symbol, name, profile?.logoUrl, profile?.sector, profile?.industry, kind,
            listOf(business, growth, profit, debt, valuation), stale, notes)
    }

    /** "Apple Inc." → "Apple"; keeps names readable inside questions. */
    fun shortName(name: String): String = name
        .replace(Regex("(?i)[,.]?\\s+(inc|incorporated|corp|corporation|co|company|ltd|limited|plc|holdings|group|sa|ag|nv)\\.?$"), "")
        .trim().trimEnd(',', '&').trim().ifBlank { name }

    private fun firstSentences(text: String, count: Int): String {
        val parts = Regex("(?<=[.!?])\\s+").split(text.trim())
        return parts.take(count).joinToString(" ")
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    fun date(iso: String): String = runCatching { "${MONTHS[iso.substring(5, 7).toInt() - 1]} ${iso.substring(8, 10).toInt()}, ${iso.take(4)}" }.getOrDefault(iso)

    private fun symbol(currency: String?) = when (currency) { null, "USD" -> "$"; "CAD" -> "C$"; else -> "$currency " }
    private fun twoDecimals(v: Double): String { val c = round(abs(v) * 100).toLong(); return "${c / 100}.${(c % 100).toString().padStart(2, '0')}" }
    fun two(v: Double): String = (if (v < 0) "−" else "") + twoDecimals(v)
    fun pct(v: Double): String = "${(round(abs(v) * 10) / 10)}%".let { if (v < 0) "−$it" else it }
    fun signed(v: Double): String = (if (v > 0) "+" else if (v < 0) "−" else "") + "${round(abs(v) * 10) / 10}%"
    fun price(v: Double, currency: String?) = (if (v < 0) "−" else "") + symbol(currency) + twoDecimals(v)
    /** Money in raw currency units shown as $X.XB / $X.XM; [plain] shows small amounts like "$20.40". */
    fun money(v: Double, currency: String?, plain: Boolean = false): String {
        val a = abs(v)
        val text = when {
            plain -> twoDecimals(a)
            a >= 1e12 -> "${twoDecimals(a / 1e12)} trillion"
            a >= 1e9 -> "${twoDecimals(a / 1e9)} billion"
            a >= 1e6 -> "${twoDecimals(a / 1e6)} million"
            else -> twoDecimals(a)
        }
        return (if (v < 0) "−" else "") + symbol(currency) + text
    }
}

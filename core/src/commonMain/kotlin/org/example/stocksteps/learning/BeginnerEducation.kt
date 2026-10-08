package org.example.stocksteps.learning

import org.example.stocksteps.companydetail.MetricEducation

/**
 * One reusable beginner explanation. Every entry answers: what is it, why does it matter, how do I
 * read it, and what are its limits. Screens show [short] first and [detailed] behind
 * "Understand more". Text lives here (not in UI code) so it can be reviewed and localized.
 */
data class EducationEntry(
    val id: String,
    val topic: String,
    val title: String,
    val short: String,
    val why: String,
    val interpret: String,
    val limitations: String,
    val detailed: String? = null,
    val example: String? = null,
    val relatedMetrics: List<String> = emptyList(),
    val version: Int = 1
)

/**
 * The single catalogue of beginner financial terms, used by Guided Research, Company Details,
 * Financials, Valuation, Earnings and Learn. "What is it" reuses MetricEducation wherever that
 * definition already exists, so the app never has two different definitions of a metric.
 */
object BeginnerEducation {
    private fun what(id: String, fallback: String) = MetricEducation.find(id)?.explanation ?: fallback

    val entries: List<EducationEntry> by lazy { listOf(
        EducationEntry("revenue", "Business", "What is revenue?",
            what("revenue", "Money earned from selling products and services, before expenses."),
            "It shows how much business a company does. Every other result starts here.",
            "Compare it with the same period last year to see whether sales are growing.",
            "Revenue is before costs: a company can sell a lot and still lose money.",
            example = "A shop that sells 100 coffees at $4 has $400 of revenue, before paying for beans, staff or rent.",
            relatedMetrics = listOf("revenue")),
        EducationEntry("revenueGrowth", "Growth", "What is revenue growth?",
            what("revenueGrowth", "How much sales changed from the comparable prior period."),
            "Growing sales can mean more customers or higher prices.",
            "A positive percentage means more revenue than the year before; negative means less.",
            "Growth doesn't tell you whether the company is profitable, and past growth may not continue.",
            example = "Revenue rising from $100 million to $120 million is 20% growth.",
            relatedMetrics = listOf("revenueGrowth")),
        EducationEntry("netIncome", "Profit", "What is net income (profit)?",
            what("netIncome", "Profit remaining after costs, interest and taxes. A negative number means a loss."),
            "Profit is what's left for the company after paying for everything.",
            "Positive means the company made money in the period; negative means a loss.",
            "One year's profit or loss can include one-off items; look at several years.",
            relatedMetrics = listOf("netIncome")),
        EducationEntry("netMargin", "Profit", "What is profit margin?",
            what("netMargin", "The percentage of sales left as profit after all expenses."),
            "It shows how much of each dollar of sales the company keeps.",
            "A 20% margin means $20 of profit for every $100 of revenue.",
            "Margins differ a lot between industries, so compare with similar companies.",
            example = "Revenue $100, expenses $80, profit $20: a 20% profit margin.",
            relatedMetrics = listOf("netMargin")),
        EducationEntry("freeCashFlow", "Profit", "What is free cash flow?",
            what("freeCashFlow", "Cash remaining after operating cash flow pays for capital expenditure."),
            "Cash pays for dividends, debt and new projects; profit on paper doesn't always arrive as cash.",
            "Positive means the business produced spare cash in the period.",
            "It can swing with big one-time investments, and it isn't the same as accounting profit.",
            relatedMetrics = listOf("freeCashFlow")),
        EducationEntry("debt", "Financial health", "What is debt?",
            what("debt", "Borrowed money the company must repay."),
            "Debt costs interest and must be repaid, even in bad years.",
            "Compare it with the company's cash and with how much money it makes.",
            "Debt isn't automatically bad: many healthy companies borrow to grow.",
            relatedMetrics = listOf("debt", "cash", "netDebt")),
        EducationEntry("cash", "Financial health", "What is cash?",
            what("cash", "Cash and cash equivalents available to the company."),
            "Cash is a cushion for bills, investment and hard times.",
            "More cash than debt gives flexibility; less cash than debt is common and not necessarily a problem.",
            "Cash on one date can change quickly; it's a snapshot of the balance sheet.",
            relatedMetrics = listOf("cash")),
        EducationEntry("debtEquity", "Financial health", "What is debt-to-equity?",
            what("debtEquity", "Debt compared with shareholder equity."),
            "It shows how much a company relies on borrowing compared with its owners' money.",
            "Above 1 means more debt than equity.",
            "Not meaningful for banks and insurers, or when equity is small or negative.",
            relatedMetrics = listOf("debtEquity")),
        EducationEntry("eps", "Valuation", "What is EPS?",
            what("eps", "Earnings divided by the number of shares."),
            "It turns company profit into a per-share number, which is what a share owner has a claim on.",
            "Higher EPS than last year means more profit per share.",
            "Share buybacks can raise EPS without the business earning more; a loss gives negative EPS.",
            relatedMetrics = listOf("eps")),
        EducationEntry("pe", "Valuation", "What is the P/E ratio?",
            what("pe", "How much investors pay for each dollar of earnings."),
            "It's a quick way to compare a share price with the company's earnings.",
            "A higher P/E means investors pay more for each dollar of reported earnings, often because they expect growth.",
            "A low or high P/E doesn't tell the whole story, and it isn't meaningful when earnings are zero or negative.",
            example = "A $50 share with $2.50 of earnings per share has a P/E of 20.",
            relatedMetrics = listOf("pe")),
        EducationEntry("marketCap", "Valuation", "What is market capitalization?",
            what("marketCap", "The total market value of a company's shares."),
            "It tells you how big the company is in the stock market.",
            "Share price × number of shares. Large companies are often called 'large caps'.",
            "Size isn't quality: a big company isn't automatically a better investment.",
            relatedMetrics = listOf("marketCap")),
        EducationEntry("dividendYield", "Income", "What is dividend yield?",
            what("dividendYield", "Annual dividends relative to the share price."),
            "Dividends are cash some companies pay to shareholders.",
            "A 3% yield means $3 a year for every $100 invested at today's price.",
            "Dividends can be cut, and a very high yield can be a sign of risk.",
            relatedMetrics = listOf("dividendYield")),
        EducationEntry("earningsBeat", "Earnings", "What is an earnings beat?",
            "When a company reports results above analysts' average estimate.",
            "Investors compare results with expectations, not just with last year.",
            "A beat means results were better than expected; a miss means worse.",
            "A beat doesn't guarantee the stock goes up: expectations, guidance and other news matter."),
        EducationEntry("diversification", "Portfolio", "What is diversification?",
            "Spreading money across different investments instead of one.",
            "If one company struggles, others may not, which can reduce the impact on your total.",
            "Owning companies in different industries and countries is more diversified than owning one.",
            "Diversification lowers some risks but can't remove the risk of losing money.")
    ) }

    private val byId by lazy { entries.associateBy { it.id } }
    fun entry(id: String): EducationEntry? = byId[id]
}

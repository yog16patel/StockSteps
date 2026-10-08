package org.example.stocksteps.news

import org.example.stocksteps.model.GlossaryTerm
import org.example.stocksteps.model.NewsCategory

/**
 * Deterministic news categories from headline/summary keywords. Only clear matches get a category;
 * anything ambiguous (several categories or none) is OTHER, so nothing is mislabelled confidently.
 */
object NewsClassifier {
    private val rules: List<Pair<NewsCategory, Regex>> = listOf(
        NewsCategory.EARNINGS to Regex("(?i)\\b(earnings|quarterly results|eps|revenue (?:rose|fell|grew|declined)|beats? estimates|misses? estimates|guidance|profit (?:rose|fell)|fiscal (?:quarter|year) results)\\b"),
        NewsCategory.ANALYST to Regex("(?i)\\b(analysts?|upgrades?|downgrades?|price target|rating|overweight|underweight|outperform|underperform)\\b"),
        NewsCategory.REGULATION to Regex("(?i)\\b(regulators?|regulatory|antitrust|lawsuit|sued|probe|investigation|sec\\b|ftc|doj|european commission|fine[sd]?|ban(?:s|ned)?|tariffs?|court)\\b"),
        NewsCategory.PRODUCTS to Regex("(?i)\\b(launch(?:es|ed)?|unveils?|introduces?|new (?:product|chip|model|device|feature|service)|release[sd]?|rolls? out|announces? (?:a |the )?new)\\b"),
        NewsCategory.BUSINESS to Regex("(?i)\\b(acquisition|acquires?|merger|partnership|partners? with|deal|contract|expands?|expansion|invest(?:s|ment)|layoffs?|hiring|ceo|buyback|dividend|data cent(?:er|re)s?)\\b")
    )

    fun classify(title: String, description: String?): NewsCategory {
        val text = "$title ${description.orEmpty()}"
        val matches = rules.filter { (_, pattern) -> pattern.containsMatchIn(text) }.map { it.first }
        // Headline wins when it alone is unambiguous; otherwise a single overall match; else OTHER.
        val headline = rules.filter { (_, pattern) -> pattern.containsMatchIn(title) }.map { it.first }
        return headline.singleOrNull() ?: matches.singleOrNull() ?: NewsCategory.OTHER
    }

    private val commentary = Regex("(?i)(\\?|[“”\"‘]|'[^']+'|\\b(prediction|predicts?|should you|ways|reasons|is it time|what you need to know|here's|why|how|stock of the day|best|top \\d+|bulls?|bears?|feature highlights|investment case|thinks|says|said|admits|opinion|analysis|could|might|may|about to|ahead of|preview|expected to|plans? to|to (?:unveil|launch|announce|report)|reportedly|rumou?rs?|chatter|will|vs\\.?|versus)\\b)")

    /** Opinion, preview, question or listicle headlines report views, not events that happened. */
    fun isCommentary(title: String): Boolean = commentary.containsMatchIn(title)

    /**
     * The category of a concrete company event reported in the headline itself (earnings, a deal,
     * a product, a regulatory action), or null for commentary, analyst views and unclear headlines.
     */
    fun eventCategory(title: String): NewsCategory? {
        if (isCommentary(title)) return null
        val headline = rules.filter { (_, pattern) -> pattern.containsMatchIn(title) }.map { it.first }.singleOrNull()
        return headline?.takeIf { it != NewsCategory.ANALYST }
    }
}

/** Short beginner definitions. Deterministic: no AI call is ever made to define a common term. */
object FinancialGlossary {
    private val entries: List<Triple<String, Regex, String>> = listOf(
        Triple("Earnings per share (EPS)", Regex("(?i)\\b(eps|earnings per share)\\b"), "A company's profit divided by its number of shares — how much profit each share represents."),
        Triple("Revenue guidance", Regex("(?i)\\b(guidance|outlook|forecast)\\b"), "A company's own forecast of future results, such as expected sales for the next quarter or year."),
        Triple("Revenue", Regex("(?i)\\b(revenue|sales)\\b"), "The total money a company brings in from selling products and services, before costs."),
        Triple("Gross margin", Regex("(?i)\\bgross margins?\\b"), "The share of revenue left after the direct costs of making products or delivering services."),
        Triple("Operating margin", Regex("(?i)\\boperating margins?\\b"), "The share of revenue left after running the business, before interest and taxes."),
        Triple("Stock buyback", Regex("(?i)\\b(buybacks?|repurchases?)\\b"), "When a company buys its own shares, reducing how many shares are available."),
        Triple("Dividend", Regex("(?i)\\bdividends?\\b"), "Cash a company pays to its shareholders, usually from profits."),
        Triple("Capital expenditure (CapEx)", Regex("(?i)\\b(capex|capital expenditures?|capital spending|data cent(?:er|re)s?)\\b"), "Money spent on long-term assets such as equipment, buildings or data centers."),
        Triple("Free cash flow", Regex("(?i)\\bfree cash flow\\b"), "Cash left from operations after paying for long-term investments."),
        Triple("Analyst upgrade / downgrade", Regex("(?i)\\b(upgrades?|downgrades?)\\b"), "A change in an analyst's published rating on a stock. It's one opinion, not a fact about the company."),
        Triple("Price target", Regex("(?i)\\bprice targets?\\b"), "An analyst's estimate of where a share price could be in the future. Targets are opinions and often wrong."),
        Triple("Antitrust", Regex("(?i)\\b(antitrust|competition law)\\b"), "Laws that stop companies from limiting competition, for example through mergers or unfair practices."),
        Triple("Market capitalization", Regex("(?i)\\b(market cap|market capitalization|market value)\\b"), "The total value of a company's shares: share price × number of shares."),
        Triple("Volatility", Regex("(?i)\\b(volatil(?:e|ity)|swings?)\\b"), "How much and how quickly a price moves up and down."),
        Triple("Interest rates", Regex("(?i)\\b(interest rates?|federal reserve|the fed|rate cuts?|rate hikes?)\\b"), "The cost of borrowing money. Changes can affect company costs and how investors value future profits."),
        Triple("Tariff", Regex("(?i)\\btariffs?\\b"), "A tax on imported goods, which can raise costs for companies that buy or sell across borders.")
    )

    val all: List<GlossaryTerm> get() = entries.map { GlossaryTerm(it.first, it.third) }

    /** Terms that actually appear in [text], in glossary order, at most [limit]. */
    fun termsIn(text: String, limit: Int = 4): List<GlossaryTerm> =
        entries.filter { it.second.containsMatchIn(text) }.take(limit).map { GlossaryTerm(it.first, it.third) }
}

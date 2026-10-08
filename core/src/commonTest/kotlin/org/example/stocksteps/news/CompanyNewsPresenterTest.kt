package org.example.stocksteps.news

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.model.*
import kotlin.test.*

class CompanyNewsPresenterTest {
    private val now = 1_791_460_800_000L // 2026-10-08T12:00:00Z
    private fun article(id: String, category: NewsCategory?, title: String = "Acme story $id", description: String? = null) =
        NewsArticle(title = title, url = "https://n.example/$id", source = "Wire", publishedAt = "2026-10-08T10:00:00Z", id = id, description = description, category = category)

    @Test fun filtersOnlyOfferCategoriesWithArticlesAndFallBackToAll() {
        val articles = listOf(article("a", NewsCategory.EARNINGS, description = "Revenue and EPS rose."), article("b", NewsCategory.ANALYST), article("c", null))
        val all = CompanyNewsPresenter.feed(articles, NewsFilter.ALL, now)
        assertEquals(listOf(NewsFilter.ALL, NewsFilter.EARNINGS, NewsFilter.ANALYST, NewsFilter.OTHER), all.filters.map { it.filter })
        assertEquals(3, all.filters.first().count)
        assertEquals("Analyst view", all.items[1].categoryLabel)
        assertNull(all.items[2].categoryLabel, "OTHER isn't badged")
        assertTrue(all.learnTerms.any { it.term.startsWith("Earnings per share") })
        val earnings = CompanyNewsPresenter.feed(articles, NewsFilter.EARNINGS, now)
        assertEquals(listOf("a"), earnings.items.map { it.articleId })
        assertEquals(NewsFilter.ALL, CompanyNewsPresenter.feed(articles, NewsFilter.REGULATION, now).selected)
        assertEquals("No recent news for this company yet.", CompanyNewsPresenter.feed(emptyList(), NewsFilter.ALL, now).emptyMessage)
        assertNull(CompanyNewsPresenter.feed(listOf(article("bad id!", null)), NewsFilter.ALL, now).items.single().articleId)
    }

    @Test fun insightModelLabelsProvenanceAndUnavailableState() {
        val source = SourceReference("a", "Acme reports earnings", "Wire", "2026-10-08T10:00:00Z", "https://n.example/a")
        val ai = ArticleInsightPresenter.model(ArticleInsight("a", InsightAvailability.AVAILABLE, "Acme reported results.", listOf("Why"), sources = listOf(source), aiGenerated = true, explanationVersion = "v"), now)
        assertTrue(ai.available); assertTrue(ai.provenance.startsWith("Written by AI"))
        assertEquals("Wire · 2h ago", ai.sources.single().meta)
        val template = ArticleInsightPresenter.model(ArticleInsight("a", InsightAvailability.AVAILABLE, "Acme reported results.", listOf("Why"), explanationVersion = "v"), now)
        assertTrue(template.provenance.contains("no AI"))
        val missing = ArticleInsightPresenter.model(ArticleInsight("a", InsightAvailability.UNAVAILABLE, limitations = listOf("Only a headline was available."), sources = listOf(source), explanationVersion = "v"), now)
        assertFalse(missing.available)
        assertEquals("Only a headline was available.", missing.unavailableMessage)
        assertTrue(missing.whyItMatters.isEmpty())
    }

    @Test fun movementModelFormatsFactsAndLabels() {
        val movement = MovementExplanation(
            symbol = "MSFT", period = MovementPeriod.ONE_DAY, sessionLabel = "Oct 7, 2026 · regular session",
            startPrice = 529.3, endPrice = 529.76, change = 0.46, changePercent = 0.0869, currency = "USD",
            benchmarks = listOf(BenchmarkMove("SPY", "S&P 500 (SPY)", -0.24)),
            events = listOf(MovementEvent("a", "Microsoft unveils laptop", "Wire", "2026-10-08T10:00:00Z", "https://n.example/a", NewsCategory.PRODUCTS, EvidenceLabel.CONFIRMED_EVENT)),
            summary = "Summary", noConfirmedCatalyst = false, explanationVersion = "v"
        )
        val model = MovementPresenter.model(movement, now)
        assertEquals("+0.09%", model.change); assertEquals(PriceDirection.UP, model.direction)
        assertEquals("+$0.46", model.amount)
        assertEquals("$529.30 → $529.76", model.priceLine)
        assertEquals("In line with the S&P 500", model.relativeToMarket)
        assertEquals("Confirmed event", model.events.single().label)
        assertTrue(model.provenance.contains("no AI"))
        assertEquals("1.15 pts better than the S&P 500", MovementPresenter.relativeLabel(1.151))
        assertEquals("0.60 pts worse than the S&P 500", MovementPresenter.relativeLabel(-0.6))
        assertEquals("-$1.00", MovementPresenter.model(movement.copy(change = -1.0, changePercent = -0.2), now).amount)
    }

    @Test fun glossaryAndClassifierAreDeterministic() {
        assertEquals(listOf("Antitrust"), FinancialGlossary.termsIn("Regulators open antitrust review").map { it.term })
        assertTrue(FinancialGlossary.termsIn("no finance words here").isEmpty())
        assertEquals(NewsCategory.REGULATION, NewsClassifier.classify("Acme sued over patents", null))
        assertEquals(NewsCategory.OTHER, NewsClassifier.classify("Acme earnings and lawsuit", null), "ambiguous headline is OTHER")
        assertTrue(NewsClassifier.isCommentary("3 Ways New Investors Get Acme Wrong"))
        assertFalse(NewsClassifier.isCommentary("Acme acquires Beta for cash"))
    }
}

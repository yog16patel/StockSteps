package org.example.stocksteps.presentation.companydetails

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.example.stocksteps.domain.*
import org.example.stocksteps.model.*
import org.example.stocksteps.news.NewsFilter
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CompanyNewsViewModelsTest {
    private val now = 1_791_460_800_000L

    private class Repo(
        var feedFails: Boolean = false,
        val insight: ArticleInsight? = null,
        val insightFails: Boolean = false,
        val movementFails: Boolean = false
    ) : CompanyNewsRepository {
        var feedRequests = 0
        val movementRequests = mutableListOf<MovementPeriod>()
        override suspend fun getCompanyNewsFeed(symbol: String): List<NewsArticle> {
            feedRequests++
            if (feedFails) throw StockDataException("HTTP 503")
            return listOf(
                NewsArticle("Acme reports quarterly earnings", "https://n.example/a", id = "a", category = NewsCategory.EARNINGS, publishedAt = "2026-10-08T10:00:00Z"),
                NewsArticle("Analyst upgrades Acme", "https://n.example/b", id = "b", category = NewsCategory.ANALYST, publishedAt = "2026-10-08T09:00:00Z")
            )
        }
        override suspend fun getArticleInsight(symbol: String, articleId: String): ArticleInsight? =
            if (insightFails) throw StockDataException("HTTP 503") else insight
        override suspend fun getMovement(symbol: String, period: MovementPeriod): MovementExplanation? {
            movementRequests += period
            if (movementFails) throw StockDataException("HTTP 503")
            return MovementExplanation(symbol, period, changePercent = if (period == MovementPeriod.ONE_DAY) 1.0 else -2.0,
                summary = "Summary ${period.label}", noConfirmedCatalyst = period != MovementPeriod.ONE_DAY, explanationVersion = "v")
        }
    }

    private fun scoped(block: suspend TestScope.(ViewModelStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try { block(store) } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun newsFiltersSliceOneFeedRequestAndMovementIsOptional() = scoped { store ->
        val repo = Repo(movementFails = true)
        val model = CompanyNewsViewModel("ACME", GetCompanyNewsFeed(repo), GetMovementExplanation(repo), {}, now = { now }).also { store.put("news", it) }
        advanceUntilIdle()
        assertEquals(2, model.state.value.model!!.items.size)
        assertNull(model.state.value.movementPreview, "a failed movement request only hides the card")
        model.selectFilter(NewsFilter.EARNINGS)
        assertEquals(listOf("a"), model.state.value.model!!.items.map { it.articleId })
        model.selectFilter(NewsFilter.ANALYST)
        assertEquals("Analyst view", model.state.value.model!!.items.single().categoryLabel)
        assertEquals(1, repo.feedRequests, "filters never refetch")
    }

    @Test fun newsFailureCanBeRetried() = scoped { store ->
        val repo = Repo(feedFails = true)
        val model = CompanyNewsViewModel("ACME", GetCompanyNewsFeed(repo), GetMovementExplanation(repo), {}, now = { now }).also { store.put("news", it) }
        advanceUntilIdle()
        assertEquals(Section.Unavailable, model.state.value.feed)
        assertEquals("+1.00%", model.state.value.movementPreview!!.change)
        repo.feedFails = false
        model.load()
        advanceUntilIdle()
        assertIs<Section.Content<*>>(model.state.value.feed)
    }

    @Test fun insightStatesCoverAvailableUnavailableMissingAndError() = scoped { store ->
        val available = ArticleInsight("a", InsightAvailability.AVAILABLE, "Acme reported results.", listOf("Why"), aiGenerated = true, explanationVersion = "v")
        val ok = NewsInsightViewModel("ACME", "a", GetArticleInsight(Repo(insight = available)), {}, now = { now }).also { store.put("ok", it) }
        val unavailable = NewsInsightViewModel("ACME", "a", GetArticleInsight(Repo(insight = available.copy(availability = InsightAvailability.UNAVAILABLE, simpleSummary = null, limitations = listOf("Only a headline.")))), {}).also { store.put("na", it) }
        val missing = NewsInsightViewModel("ACME", "a", GetArticleInsight(Repo(insight = null)), {}).also { store.put("missing", it) }
        val failed = NewsInsightViewModel("ACME", "a", GetArticleInsight(Repo(insightFails = true)), {}).also { store.put("failed", it) }
        assertEquals(Section.Loading, ok.state.value.insight)
        advanceUntilIdle()
        assertTrue(ok.state.value.model!!.available)
        assertEquals("Only a headline.", unavailable.state.value.model!!.unavailableMessage)
        assertEquals(Section.Content(null), missing.state.value.insight)
        assertEquals(Section.Unavailable, failed.state.value.insight)
    }

    @Test fun movementPeriodsLoadOnceEach() = scoped { store ->
        val repo = Repo()
        val model = StockMovementViewModel("ACME", GetMovementExplanation(repo), {}, now = { now }).also { store.put("move", it) }
        advanceUntilIdle()
        assertFalse(model.state.value.model!!.noConfirmedCatalyst)
        model.select(MovementPeriod.ONE_WEEK)
        assertEquals(Section.Loading, model.state.value.current)
        advanceUntilIdle()
        assertTrue(model.state.value.model!!.noConfirmedCatalyst)
        assertEquals("-2.00%", model.state.value.model!!.change)
        model.select(MovementPeriod.ONE_DAY)
        advanceUntilIdle()
        assertEquals(listOf(MovementPeriod.ONE_DAY, MovementPeriod.ONE_WEEK), repo.movementRequests, "switching back reuses the loaded period")
    }
}

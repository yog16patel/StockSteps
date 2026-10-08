package org.example.stocksteps.presentation.markets

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.example.stocksteps.domain.GetMarketsOverview
import org.example.stocksteps.domain.MarketsRepository
import org.example.stocksteps.domain.StockDataException
import org.example.stocksteps.markets.MoversTab
import org.example.stocksteps.model.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MarketsViewModelTest {
    private fun overview(gainers: Int = 8, errors: List<String> = emptyList()) = MarketsOverview(
        session = MarketSession("US", MarketSessionStatus.OPEN, "America/New_York", "2026-10-07T14:00:00Z", sessionDate = "2026-10-07",
            closesAt = "16:00", utcOffsetMinutes = -240, source = "c"),
        indices = listOf(IndexQuote("SP500", "S&P 500", "^GSPC", 7772.36, -18.59, -0.24, "points", "USD", "US")),
        gainers = MarketMoverList((1..gainers).map { MarketMover("G$it", null, 1.0, 0.1, 10.0 - it) }, "u", "s", "r"),
        losers = MarketMoverList(listOf(MarketMover("L", null, 1.0, -0.1, -5.0)), "u", "s", "r"),
        mostActive = MarketMoverList(emptyList(), "u", "s", "r"),
        sectors = SectorPerformanceSection(emptyList(), "m", "p"),
        dataNotice = "Delayed", generatedAt = "2026-10-07T14:00:00Z",
        errors = errors.map { SnapshotSectionError(it, ApiError("PROVIDER_UNAVAILABLE", "x")) }
    )

    private class Repo(var result: () -> MarketsOverview) : MarketsRepository {
        var requests = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun getOverview(): MarketsOverview { requests++; gate?.await(); return result() }
    }

    private fun scoped(block: suspend TestScope.(ViewModelStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try { block(store) } finally { store.clear(); Dispatchers.resetMain() }
    }

    private fun TestScope.model(repo: Repo, store: ViewModelStore) =
        MarketsViewModel(GetMarketsOverview(repo), {}, now = { 0L }).also { store.put("markets", it) }

    @Test fun loadsOnceAndTabsOnlyResliceTheOverview() = scoped { store ->
        val repo = Repo { overview() }
        val model = model(repo, store)
        assertTrue(model.state.value.loading)
        advanceUntilIdle()
        assertEquals(5, model.state.value.model!!.movers.rows.size)
        model.toggleExpanded()
        assertEquals(8, model.state.value.model!!.movers.rows.size)
        model.selectTab(MoversTab.LOSERS)
        assertFalse(model.state.value.expanded, "switching tabs collapses the list")
        assertEquals(listOf("L"), model.state.value.model!!.movers.rows.map { it.row.symbol })
        model.selectTab(MoversTab.MOST_ACTIVE)
        assertEquals("No most active to show right now.", model.state.value.model!!.movers.emptyMessage)
        assertEquals(1, repo.requests)
    }

    @Test fun failureShowsErrorAndRetryRecovers() = scoped { store ->
        var fail = true
        val repo = Repo { if (fail) throw StockDataException("Could not reach StockSteps.") else overview() }
        val model = model(repo, store)
        advanceUntilIdle()
        assertEquals("Could not reach StockSteps.", model.state.value.error)
        assertNull(model.state.value.model)
        fail = false
        model.retry()
        advanceUntilIdle()
        assertNull(model.state.value.error)
        assertNotNull(model.state.value.model)
    }

    @Test fun pullToRefreshKeepsContentAndPartialSectionsStayPartial() = scoped { store ->
        val repo = Repo { overview(errors = listOf("sectors", "news")) }
        val model = model(repo, store)
        advanceUntilIdle()
        val ui = model.state.value.model!!
        assertTrue(ui.sectors.failed); assertTrue(ui.newsFailed)
        assertFalse(ui.indicesFailed); assertEquals(1, ui.indices.size)
        repo.gate = CompletableDeferred()
        model.refresh()
        runCurrent()
        assertTrue(model.state.value.refreshing)
        assertNotNull(model.state.value.model, "content stays while refreshing")
        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.state.value.refreshing)
        assertEquals(2, repo.requests)
    }

    @Test fun aRefreshCancelsTheInFlightLoad() = scoped { store ->
        val repo = Repo { overview(gainers = 2) }
        repo.gate = CompletableDeferred()
        val model = model(repo, store)
        runCurrent()
        repo.gate = null
        model.refresh()
        advanceUntilIdle()
        assertEquals(2, model.state.value.model!!.movers.rows.size)
        assertFalse(model.state.value.loading)
    }
}

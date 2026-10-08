package org.example.stocksteps.presentation.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.stocksteps.domain.GetMarketsOverview
import org.example.stocksteps.domain.StockDataException
import org.example.stocksteps.markets.MarketsPresenter
import org.example.stocksteps.markets.MarketsUiModel
import org.example.stocksteps.markets.MoversTab
import org.example.stocksteps.model.MarketsOverview
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal data class MarketsState(
    val overview: MarketsOverview? = null,
    val loading: Boolean = true,
    /** Pull-to-refresh with content already on screen (kept visible while it reloads). */
    val refreshing: Boolean = false,
    /** Whole-request failure; sections that failed on the backend are reported in the overview. */
    val error: String? = null,
    val tab: MoversTab = MoversTab.GAINERS,
    val expanded: Boolean = false,
    val loadedAt: Long = 0
) {
    val model: MarketsUiModel? get() = overview?.let { MarketsPresenter.build(it, tab, expanded, loadedAt) }
}

/**
 * One overview request per load (the backend aggregates and caches every section); tabs and
 * "show all" only re-slice it. A refresh cancels an in-flight load and keeps current content
 * visible until the new response arrives. Keyed by environment by the scene.
 */
@OptIn(ExperimentalTime::class)
internal class MarketsViewModel(
    private val getOverview: GetMarketsOverview,
    private val closeResources: () -> Unit,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {
    private val mutableState = MutableStateFlow(MarketsState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun refresh() = load(userRefresh = true)

    fun retry() = load()

    fun selectTab(tab: MoversTab) = mutableState.update { if (it.tab == tab) it else it.copy(tab = tab, expanded = false) }

    fun toggleExpanded() = mutableState.update { it.copy(expanded = !it.expanded) }

    private fun load(userRefresh: Boolean = false) {
        job?.cancel()
        mutableState.update { it.copy(loading = it.overview == null, refreshing = userRefresh && it.overview != null, error = null) }
        job = viewModelScope.launch {
            try {
                val overview = getOverview()
                mutableState.update { it.copy(overview = overview, loading = false, refreshing = false, loadedAt = now()) }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                val message = (cause as? StockDataException)?.message ?: "Market data isn't available right now."
                mutableState.update { it.copy(loading = false, refreshing = false, error = message) }
            }
        }
    }

    override fun onCleared() = closeResources()
}

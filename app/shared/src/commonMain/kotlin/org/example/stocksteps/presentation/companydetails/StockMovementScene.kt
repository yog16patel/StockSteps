package org.example.stocksteps.presentation.companydetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.domain.GetMovementExplanation
import org.example.stocksteps.model.AppBarBackButton
import org.example.stocksteps.model.AppBarConfiguration
import org.example.stocksteps.model.MovementExplanation
import org.example.stocksteps.model.MovementPeriod
import org.example.stocksteps.news.MovementEventRow
import org.example.stocksteps.news.MovementModel
import org.example.stocksteps.news.MovementPresenter
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.presentation.components.StockStepsTopBar
import org.example.stocksteps.resources.*
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal data class StockMovementState(
    val symbol: String,
    val period: MovementPeriod = MovementPeriod.ONE_DAY,
    /** One result per period; switching back to a loaded period doesn't request it again. */
    val results: Map<MovementPeriod, Section<MovementExplanation?>> = emptyMap(),
    val loadedAt: Long = 0
) {
    val current: Section<MovementExplanation?> get() = results[period] ?: Section.Loading
    val model: MovementModel? get() = (current as? Section.Content)?.value?.let { MovementPresenter.model(it, loadedAt) }
}

@OptIn(ExperimentalTime::class)
internal class StockMovementViewModel(
    private val symbol: String,
    private val getMovement: GetMovementExplanation,
    private val closeResources: () -> Unit,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {
    private val mutableState = MutableStateFlow(StockMovementState(symbol))
    val state = mutableState.asStateFlow()

    init { load(MovementPeriod.ONE_DAY) }

    fun select(period: MovementPeriod) {
        mutableState.update { it.copy(period = period) }
        if (state.value.results[period] !is Section.Content) load(period)
    }

    fun retry() = load(state.value.period)

    private fun load(period: MovementPeriod) {
        mutableState.update { it.copy(results = it.results + (period to Section.Loading)) }
        viewModelScope.launch {
            val result = try { Section.Content(getMovement(symbol, period)) } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                Section.Unavailable
            }
            mutableState.update { it.copy(results = it.results + (period to result), loadedAt = now()) }
        }
    }

    override fun onCleared() = closeResources()
}

@Composable
internal fun StockMovementScene(
    route: StockMovementRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    hinge: WindowHinge?,
    backIcon: @Composable () -> Unit,
    onBack: () -> Unit
) {
    val model = viewModel(key = "stock-movement:${route.symbol}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        StockMovementViewModel(route.symbol, data.getMovementExplanation(), data::close)
    }
    val state by model.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    StockMovementScreen(state, hinge, backIcon, onBack, onPeriod = model::select, onRetry = model::retry,
        onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } })
}

/**
 * Why did it move?: the computed move and benchmarks, what we know, time-aligned news with
 * evidence labels, what we can't confirm, and the "No confirmed catalyst" state.
 */
@Composable
internal fun StockMovementScreen(
    state: StockMovementState,
    hinge: WindowHinge?,
    backIcon: @Composable () -> Unit,
    onBack: () -> Unit,
    onPeriod: (MovementPeriod) -> Unit,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val model = remember(state.results, state.period, state.loadedAt) { state.model }
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    val section = content.padding(top = spacing.lg)
    Column(Modifier.fillMaxSize().background(colors.appBackground)) {
        StockStepsTopBar(
            configuration = AppBarConfiguration(title = stringResource(Res.string.movement_title, state.symbol), backButton = AppBarBackButton.BACK),
            onBack = onBack,
            backIcon = backIcon
        )
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(region, contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally) {
                item(key = "periods") {
                    StockPillSelector(MovementPeriod.entries, state.period, label = { it.label }, onSelect = onPeriod, modifier = content.padding(top = spacing.xs))
                }
                when (val current = state.current) {
                    Section.Loading -> item(key = "loading") {
                        StockLoadingState(content.padding(top = spacing.md)) {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                repeat(3) { StockSkeleton(Modifier.fillMaxWidth().height(StockStepsTheme.dimensions.touchTarget * 2), shape = StockStepsTheme.shapes.card) }
                            }
                        }
                    }
                    Section.Unavailable -> item(key = "error") {
                        StockCard(content.padding(top = spacing.md), bordered = false) { StockErrorState(stringResource(Res.string.movement_failed), onRetry) }
                    }
                    is Section.Content -> if (current.value == null || model == null) {
                        item(key = "empty") { StockCard(content.padding(top = spacing.md), bordered = false) { StockEmptyState(stringResource(Res.string.movement_unavailable)) } }
                    } else {
                        item(key = "hero") { MovementHero(model, content.padding(top = spacing.md)) }
                        if (model.noConfirmedCatalyst) item(key = "no-catalyst") { NoCatalystCard(section) }
                        item(key = "know") { InsightSection(stringResource(Res.string.movement_know), model.whatWeKnow, modifier = section) }
                        if (model.benchmarks.isNotEmpty()) item(key = "benchmarks") { BenchmarksSection(model, section) }
                        item(key = "events") { EventsSection(model, onOpenUrl, section) }
                        item(key = "cannot") { InsightSection(stringResource(Res.string.movement_cannot), model.whatWeCannotConfirm, modifier = section) }
                        if (model.limitations.isNotEmpty()) item(key = "limits") { InsightSection(stringResource(Res.string.movement_limits), model.limitations, modifier = section, subdued = true) }
                    }
                }
                item(key = "disclaimer") {
                    Text(stringResource(Res.string.details_disclaimer), content.padding(top = spacing.lg), style = typography.caption, color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun MovementHero(model: MovementModel, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    StockCard(modifier, bordered = false, contentPadding = PaddingValues(spacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            model.sessionLabel?.let { Text(it, style = StockStepsTheme.typography.label, color = colors.textSecondary) }
            StockPriceChange(model.change, model.direction, amount = model.amount, style = StockStepsTheme.typography.largeNumber)
            model.priceLine?.let { Text(it, style = StockStepsTheme.typography.numberLabel, color = colors.textBody) }
            model.relativeToMarket?.let { StockBadge(it, org.example.stocksteps.companydetail.FactTone.NEUTRAL) }
            StockDivider()
            Text(model.summary, style = StockStepsTheme.typography.body, color = colors.textBody)
            ProvenanceNote(model.provenance)
        }
    }
}

@Composable
private fun NoCatalystCard(modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Row(
        modifier.clip(StockStepsTheme.shapes.card).background(colors.educationContainer).padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Icon(StockIcons.Help, contentDescription = null, tint = colors.educationAccent, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(MovementPresenter.NO_CATALYST_TITLE, Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
            Text(MovementPresenter.NO_CATALYST_BODY, style = StockStepsTheme.typography.small, color = colors.textBody)
        }
    }
}

@Composable
private fun BenchmarksSection(model: MovementModel, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(stringResource(Res.string.movement_benchmarks))
        StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs)) {
            Row(Modifier.fillMaxWidth().padding(vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(model.title.removePrefix("Why did ").removeSuffix(" move?"), Modifier.weight(1f), style = StockStepsTheme.typography.bodyMedium, color = colors.textPrimary)
                StockPriceChange(model.change, model.direction)
            }
            model.benchmarks.forEach { row ->
                StockDivider()
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.name, Modifier.weight(1f), style = StockStepsTheme.typography.body, color = colors.textBody)
                    StockPriceChange(row.change, row.direction)
                }
            }
        }
    }
}

@Composable
private fun EventsSection(model: MovementModel, onOpenUrl: (String) -> Unit, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        StockSectionHeader(stringResource(Res.string.movement_events))
        if (model.events.isEmpty()) {
            StockCard(bordered = false) { StockEmptyState(stringResource(Res.string.movement_events_empty)) }
        } else {
            model.events.forEach { event -> EventCard(event, onOpenUrl) }
            Column(Modifier.padding(top = spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(stringResource(Res.string.movement_labels), style = StockStepsTheme.typography.label, color = colors.textSecondary)
                model.labelGuide.forEach { (label, meaning) ->
                    Text("$label: $meaning", style = StockStepsTheme.typography.caption, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: MovementEventRow, onOpenUrl: (String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    StockCard(
        bordered = false,
        onClick = event.url?.let { url -> { onOpenUrl(url) } },
        onClickLabel = stringResource(Res.string.news_open_original),
        contentPadding = PaddingValues(spacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            StockBadge(event.label, event.tone)
            Text(event.title, style = StockStepsTheme.typography.bodyMedium, color = colors.textPrimary)
            event.meta?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSecondary) }
        }
    }
}

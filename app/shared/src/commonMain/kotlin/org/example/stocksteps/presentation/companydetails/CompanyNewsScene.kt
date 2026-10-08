package org.example.stocksteps.presentation.companydetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
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
import org.example.stocksteps.domain.GetCompanyNewsFeed
import org.example.stocksteps.domain.GetMovementExplanation
import org.example.stocksteps.model.GlossaryTerm
import org.example.stocksteps.model.MovementExplanation
import org.example.stocksteps.model.MovementPeriod
import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.news.*
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.resources.*
import org.example.stocksteps.settings.BackendEnvironment
import org.example.stocksteps.settings.BackendRouter
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal data class CompanyNewsState(
    val symbol: String,
    val feed: Section<List<NewsArticle>> = Section.Loading,
    val movement: Section<MovementExplanation?> = Section.Loading,
    val filter: NewsFilter = NewsFilter.ALL,
    val loadedAt: Long = 0
) {
    /** Filters only re-slice the loaded feed; nothing is requested again. */
    val model: CompanyNewsFeedModel? get() = (feed as? Section.Content)?.value?.let { CompanyNewsPresenter.feed(it, filter, loadedAt) }
    val movementPreview: MovementPreview? get() = (movement as? Section.Content)?.value?.let(MovementPresenter::preview)
}

internal sealed interface CompanyNewsAction {
    data class SelectFilter(val filter: NewsFilter) : CompanyNewsAction
    data object Retry : CompanyNewsAction
    data object OpenMovement : CompanyNewsAction
    data class OpenInsight(val articleId: String) : CompanyNewsAction
    data class OpenArticle(val url: String) : CompanyNewsAction
}

/**
 * One company's news: the whole feed (one request, cached by the backend for 10 minutes) and
 * today's computed movement load in parallel. Explanations are only requested when opened.
 */
@OptIn(ExperimentalTime::class)
internal class CompanyNewsViewModel(
    private val symbol: String,
    private val getFeed: GetCompanyNewsFeed,
    private val getMovement: GetMovementExplanation,
    private val closeResources: () -> Unit,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {
    private val mutableState = MutableStateFlow(CompanyNewsState(symbol))
    val state = mutableState.asStateFlow()

    init { load() }

    fun selectFilter(filter: NewsFilter) = mutableState.update { it.copy(filter = filter) }

    fun load() {
        mutableState.update { it.copy(feed = Section.Loading, movement = Section.Loading) }
        viewModelScope.launch {
            val feed = attempt { getFeed(symbol) }
            mutableState.update { it.copy(feed = feed?.let { value -> Section.Content(value) } ?: Section.Unavailable, loadedAt = now()) }
        }
        viewModelScope.launch {
            // Today's move is optional context: a failure simply hides the card.
            val movement = attempt { getMovement(symbol, MovementPeriod.ONE_DAY) }
            mutableState.update { it.copy(movement = Section.Content(movement)) }
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try { block() } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        null
    }

    override fun onCleared() = closeResources()
}

@Composable
internal fun CompanyNewsScene(
    route: CompanyNewsRoute,
    backend: BackendRouter,
    environment: BackendEnvironment,
    hinge: WindowHinge?,
    onOpenInsight: (symbol: String, articleId: String) -> Unit,
    onOpenMovement: (String) -> Unit
) {
    val model = viewModel(key = "company-news:${route.symbol}:$environment") {
        val data = StockStepsDependencies(backend::currentUrl)
        CompanyNewsViewModel(route.symbol, data.getCompanyNewsFeed(), data.getMovementExplanation(), data::close)
    }
    val state by model.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    CompanyNewsScreen(state, hinge) { action ->
        when (action) {
            is CompanyNewsAction.SelectFilter -> model.selectFilter(action.filter)
            CompanyNewsAction.Retry -> model.load()
            CompanyNewsAction.OpenMovement -> onOpenMovement(route.symbol)
            is CompanyNewsAction.OpenInsight -> onOpenInsight(route.symbol, action.articleId)
            is CompanyNewsAction.OpenArticle -> runCatching { uriHandler.openUri(action.url) }
        }
    }
}

/** Company News: today's move, category filters, news cards with "Explain this", and learn-as-you-read terms. */
@Composable
internal fun CompanyNewsScreen(state: CompanyNewsState, hinge: WindowHinge?, onAction: (CompanyNewsAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val model = remember(state.feed, state.filter, state.loadedAt) { state.model }
    val preview = remember(state.movement) { state.movementPreview }
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    Box(Modifier.fillMaxSize().background(colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            LazyColumn(region, contentPadding = PaddingValues(spacing.screen), verticalArrangement = Arrangement.spacedBy(spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally) {
                preview?.let { move ->
                    item(key = "movement") { MovementPreviewCard(move, onOpen = { onAction(CompanyNewsAction.OpenMovement) }, modifier = content) }
                }
                when (val feed = state.feed) {
                    Section.Loading -> items(3) { StockNewsCardSkeleton(content) }
                    Section.Unavailable -> item(key = "error") {
                        StockCard(content, bordered = false) { StockErrorState(stringResource(Res.string.news_failed), { onAction(CompanyNewsAction.Retry) }) }
                    }
                    is Section.Content -> model?.let { feedModel ->
                        if (feedModel.filters.size > 1) {
                            item(key = "filters") {
                                Row(content.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                    feedModel.filters.forEach { option ->
                                        StockChip("${option.label} ${option.count}", option.filter == feedModel.selected, onClick = { onAction(CompanyNewsAction.SelectFilter(option.filter)) })
                                    }
                                }
                            }
                        }
                        feedModel.emptyMessage?.let { message ->
                            item(key = "empty") { StockCard(content, bordered = false) { StockEmptyState(message) } }
                        }
                        val learnAt = minOf(LEARN_POSITION, feedModel.items.size)
                        itemsIndexed(feedModel.items, key = { _, item -> item.news.id }) { index, item ->
                            Column(content, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                if (index == learnAt && feedModel.learnTerms.isNotEmpty()) LearnTermsCard(feedModel.learnTerms)
                                CompanyNewsCard(item, onAction)
                                if (index == feedModel.items.lastIndex && learnAt == feedModel.items.size && feedModel.learnTerms.isNotEmpty()) LearnTermsCard(feedModel.learnTerms)
                            }
                        }
                    }
                }
                item(key = "disclaimer") {
                    Text(stringResource(Res.string.details_disclaimer), content.padding(top = spacing.md), style = StockStepsTheme.typography.caption, color = colors.textTertiary)
                }
            }
        }
    }
}

private const val LEARN_POSITION = 3

@Composable
internal fun MovementPreviewCard(move: MovementPreview, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val action = stringResource(Res.string.news_movement_explore)
    StockCard(modifier, onClick = onOpen, onClickLabel = action, bordered = false, contentPadding = PaddingValues(spacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(stringResource(Res.string.news_movement_today), Modifier.weight(1f).semantics { heading() }, style = StockStepsTheme.typography.label, color = colors.textSecondary)
                StockPriceChange(move.change, move.direction)
            }
            move.sessionLabel?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textTertiary) }
            if (move.noConfirmedCatalyst) StockBadge(MovementPresenter.NO_CATALYST_TITLE, org.example.stocksteps.companydetail.FactTone.NEUTRAL)
            Text(move.summary, style = StockStepsTheme.typography.small, color = colors.textBody, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(action, style = StockStepsTheme.typography.label, color = colors.primaryText)
                Icon(StockIcons.ChevronRight, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
            }
        }
    }
}

/** Full news card: category, source and time, headline, summary, thumbnail, plus "Explain this". */
@Composable
private fun CompanyNewsCard(item: CompanyNewsItem, onAction: (CompanyNewsAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val news = item.news
    StockCard(
        bordered = false,
        onClick = news.url?.let { url -> { onAction(CompanyNewsAction.OpenArticle(url)) } },
        onClickLabel = stringResource(Res.string.news_open_original),
        contentPadding = PaddingValues(spacing.md)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    item.categoryLabel?.let { StockBadge(it, item.categoryTone) }
                    val meta = listOfNotNull(news.source, news.publishedLabel).joinToString(" · ")
                    if (meta.isNotEmpty()) Text(meta, Modifier.weight(1f, fill = false), style = typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(news.headline, style = typography.bodyMedium, color = colors.textPrimary)
                news.summary?.let { Text(it, style = typography.small, color = colors.textBody, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
            news.imageUrl?.let { image ->
                AsyncImage(image, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(StockStepsTheme.dimensions.newsThumbnail).clip(StockStepsTheme.shapes.chip).background(colors.surfaceSecondary))
            }
        }
        item.articleId?.let { id ->
            val label = stringResource(Res.string.news_explain_label)
            Row(
                Modifier.padding(top = spacing.xs).heightIn(min = StockStepsTheme.dimensions.touchTarget)
                    .clip(StockStepsTheme.shapes.pill)
                    .clickable(onClickLabel = label, role = Role.Button) { onAction(CompanyNewsAction.OpenInsight(id)) }
                    .padding(end = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Icon(StockIcons.Lightbulb, contentDescription = null, tint = colors.educationAccent, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
                Text(stringResource(Res.string.news_explain), style = typography.label, color = colors.primaryText)
            }
        }
    }
}

/** Deterministic glossary terms found in the visible articles. */
@Composable
internal fun LearnTermsCard(terms: List<GlossaryTerm>, title: String = stringResource(Res.string.news_learn_title), modifier: Modifier = Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(
        modifier.fillMaxWidth().clip(StockStepsTheme.shapes.card).background(colors.educationContainer).padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Icon(StockIcons.Lightbulb, contentDescription = null, tint = colors.educationAccent, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
            Text(title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
        }
        terms.forEach { term ->
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(term.term, style = StockStepsTheme.typography.label, color = colors.educationAccent)
                Text(term.definition, style = StockStepsTheme.typography.small, color = colors.textBody)
            }
        }
    }
}

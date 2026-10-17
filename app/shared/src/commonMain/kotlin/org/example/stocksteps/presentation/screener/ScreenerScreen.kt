@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package org.example.stocksteps.presentation.screener

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.screener.*
import org.example.stocksteps.theme.StockBadgeKind
import org.example.stocksteps.theme.StockCardGridLayout
import org.example.stocksteps.theme.StockQuoteRowLayout
import org.example.stocksteps.theme.ThemeCorners

internal class ScreenerActions(
    val onPreset: (String) -> Unit,
    val onRange: (String, Double?, Double?) -> Unit,
    val onChoice: (ChoiceField, List<String>) -> Unit,
    /** Removes one applied range filter and searches again. */
    val onClearFilter: (String) -> Unit,
    /** Removes one applied attribute filter (sector, country…) and searches again. */
    val onClearChoice: (ChoiceField) -> Unit,
    val onReset: () -> Unit,
    val onApply: () -> Unit,
    val onSort: (ScreenerSort) -> Unit,
    val onLoadMore: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryCatalog: () -> Unit,
    val onOpen: (String) -> Unit,
    val onToggleCompare: (String, String) -> Unit,
    val onCompare: () -> Unit,
    val onWatchlist: (ScreenerRowView) -> Unit,
    val onPortfolio: (ScreenerRowView) -> Unit,
    val onSave: (String) -> Unit,
    val onApplySaved: (String) -> Unit,
    val onRenameSaved: (String, String) -> Unit,
    val onDeleteSaved: (String) -> Unit,
    val onSignIn: () -> Unit,
    val onDismissMessage: () -> Unit,
    /** Phase 7E: find a company in the list by ticker or name. */
    val onText: (String) -> Unit = {},
    /** Phase 8B: one-tap sector filter on top of the applied query (empty = all sectors). */
    val onSector: (List<String>) -> Unit = {}
)

/**
 * Discover Stocks (Global UI Refinement Phase 5B). Order: title + help → (no search yet) preset cards and "Create custom screen" → (searched)
 * filter bar, applied filters, results header with sort, one grouped list of compact quote rows → more tools → disclaimer. Presets are
 * screening shortcuts with visible criteria, never recommendations. Filter edits stay in a draft until "Show results"; results are filtered,
 * sorted and paged by the server across the whole evaluated universe. Loading, no matches and failures are separate states.
 */
@Composable
internal fun ScreenerScreen(state: ScreenerUiState, modifier: Modifier, actions: ScreenerActions, watchlistAvailable: Boolean) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var filters by rememberSaveable { mutableStateOf(false) }
    var savedSheet by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var metricsHelp by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var info by remember { mutableStateOf<MetricInfo?>(null) }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 3 } == true } }
    LaunchedEffect(nearEnd, state.hasMore) { if (nearEnd && state.hasMore && !state.loading) actions.onLoadMore() }
    val applied = state.applied
    val active = remember(applied) { ScreenerDisplay.activeFilters(applied) }
    val showRows = applied != null && !state.loading && state.error == null

    Column(modifier.background(colors.appBackground)) {
        LazyColumn(state = listState, contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.md, bottom = spacing.xxl),
            modifier = Modifier.weight(1f).fillMaxWidth()) {
            item(key = "header") { Header(ScreenerDisplay.scopeLabel(state.universe), ScreenerDisplay.scopeLine(state.universe), onHelp = { help = true }) }
            // Phase 7E: search within the Discover list (ticker or name); the server ranks matches with the shared search rules.
            // Phase 8B: the design-system search field (icon, clear button), as on Company Search.
            if (!state.catalogLoading && state.catalogError == null) item(key = "text-search") {
                StockSearchField(state.draft.text.orEmpty(), actions.onText, ScreenerDisplay.TEXT_SEARCH_LABEL, Modifier.padding(top = spacing.md), bordered = false)
            }
            // Phase 8B: sectors one tap away (the catalogue's own list); the same filter the sheet builds, one search per tap.
            val sectors = ScreenerDisplay.sectorOptions(state.choices)
            if (!state.catalogLoading && state.catalogError == null && sectors.isNotEmpty()) item(key = "sectors") {
                StockPillSelector(sectors, ScreenerDisplay.selectedSector(state.applied), label = { it },
                    onSelect = { actions.onSector(ScreenerDisplay.sectorValues(it)) }, modifier = Modifier.padding(top = spacing.sm))
            }
            when {
                state.catalogLoading -> item(key = "catalog-loading") {
                    StockLoadingState(Modifier.padding(top = spacing.lg)) { Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) { repeat(2) { StockSkeleton(Modifier.fillMaxWidth().height(spacing.xxxl * 3)) } } }
                }
                state.catalogError != null -> item(key = "catalog-error") {
                    StockErrorState(state.catalogError.orEmpty(), actions.onRetryCatalog.takeIf { ScreenerDisplay.catalogRetryable(state.catalogErrorCode) },
                        Modifier.padding(top = spacing.lg), title = ScreenerDisplay.catalogErrorTitle(state.catalogErrorCode), icon = StockIcons.Warning)
                }
                applied == null -> item(key = "presets") {
                    Column(Modifier.padding(top = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                        PresetGrid(state.presets, actions.onPreset)
                        StockButton(ScreenerDisplay.CUSTOM_SCREEN, { filters = true }, Modifier.fillMaxWidth(), enabled = state.metrics.isNotEmpty())
                    }
                }
                else -> {
                    item(key = "filter-bar") {
                        FilterBar(active.size, expanded, Modifier.padding(top = spacing.md), onToggle = { expanded = !expanded }, onEdit = { filters = true },
                            onClearAll = actions.onReset)
                    }
                    if (active.isNotEmpty()) item(key = "active-filters") {
                        ActiveFilters(active, expanded, Modifier.padding(top = spacing.sm)) { filter ->
                            filter.metric?.let(actions.onClearFilter) ?: filter.field?.let(actions.onClearChoice)
                        }
                    }
                    item(key = "results-header") { ResultsHeader(state, Modifier.padding(top = spacing.lg), actions.onSort) { metricsHelp = true } }
                }
            }
            if (applied != null) when {
                state.loading -> item(key = "loading") {
                    StockLoadingState(Modifier.padding(top = spacing.sm)) {
                        StockCard(bordered = false, contentPadding = PaddingValues(vertical = spacing.xxs)) { repeat(3) { StockRowSkeleton() } }
                    }
                }
                state.error != null -> item(key = "error") {
                    StockCard(Modifier.padding(top = spacing.sm), bordered = false) {
                        StockErrorState(state.error.orEmpty(), actions.onRetry, title = ScreenerDisplay.ERROR_TITLE, icon = StockIcons.Warning)
                    }
                }
                state.rows.isEmpty() -> item(key = "empty") {
                    StockCard(Modifier.padding(top = spacing.sm), bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        StockEmptyState(ScreenerDisplay.EMPTY_MESSAGE, actionText = "Edit filters", onAction = { filters = true }, title = ScreenerDisplay.EMPTY_TITLE, icon = StockIcons.Search)
                        if (active.isNotEmpty()) StockButton("Clear all filters", actions.onReset, Modifier.fillMaxWidth(), variant = StockButtonVariant.SECONDARY)
                    }
                }
            }
            if (showRows) itemsIndexed(state.rows, key = { _, row -> row.symbol }) { index, row ->
                val radius = ThemeCorners.card.dp
                val shape = RoundedCornerShape(topStart = if (index == 0) radius else 0.dp, topEnd = if (index == 0) radius else 0.dp,
                    bottomStart = if (index == state.rows.lastIndex) radius else 0.dp, bottomEnd = if (index == state.rows.lastIndex) radius else 0.dp)
                Column(Modifier.padding(top = if (index == 0) spacing.sm else 0.dp).clip(shape).background(colors.surface)) {
                    if (index > 0) StockDivider(startIndent = spacing.cardPadding)
                    ResultRow(row, ScreenerDisplay.metricItems(row, state.displayMetrics, applied), actions, watchlistAvailable)
                }
            }
            if (state.loadingMore) item(key = "more") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = spacing.sm).semantics { contentDescription = "Loading more results" }) }
            if (!state.catalogLoading && state.catalogError == null) item(key = "tools") {
                Tools(state, Modifier.padding(top = spacing.xxl), onSaved = { savedSheet = true }, onSignIn = actions.onSignIn, onCompare = actions.onCompare, onHelp = { help = true })
            }
            if (applied != null) item(key = "disclaimer") {
                Text(ScreenerDisplay.DISCLAIMER, Modifier.padding(top = spacing.lg), style = typography.caption, color = colors.textTertiary)
            }
        }
        // Below the list (not over it), so the last row, tools and disclaimer are never covered.
        if (state.selected.isNotEmpty()) CompareBar(state, actions.onCompare)
    }
    if (filters) FilterEditorSheet(state, actions, onDismiss = { filters = false }, onInfo = { info = it })
    if (savedSheet) SavedScreensSheet(state, actions, onDismiss = { savedSheet = false })
    if (help) ScreeningHelpSheet(state, onPreset = { help = false; actions.onPreset(it) }, onDismiss = { help = false })
    if (metricsHelp) MetricsInfoSheet(state.displayMetrics.map { MetricFormatter.education(it.id, it) }) { metricsHelp = false }
    info?.let { MetricInfoSheet(it) { info = null } }
    state.message?.let { message ->
        AlertDialog(onDismissRequest = actions.onDismissMessage, text = { Text(message) }, confirmButton = { TextButton(onClick = actions.onDismissMessage) { Text("OK") } })
    }
}

/**
 * Title, purpose and (Phase 8B) the scope. Phase 8D: a short label ("155 companies · not the whole market") stays visible; the server's own
 * description of the list is one tap away under "About this list", so search and sectors stay near the top at large text.
 */
@Composable
private fun Header(scope: String?, about: String?, onHelp: () -> Unit) {
    val colors = StockStepsTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(top = StockStepsTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            Text(ScreenerDisplay.TITLE, Modifier.semantics { heading() }, style = StockStepsTheme.typography.screenTitle, color = colors.textTitle)
            Text(ScreenerDisplay.SUBTITLE, style = StockStepsTheme.typography.small, color = colors.textSupporting)
            scope?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textMeta) }
            about?.let { StockDisclosure(ScreenerDisplay.ABOUT_LIST) { Text(it, style = StockStepsTheme.typography.caption, color = colors.textSupporting) } }
        }
        IconButton(onClick = onHelp) { Icon(StockIcons.Help, "How screening works", tint = colors.iconSecondary) }
    }
}

private fun presetIcon(id: String) = when (id) {
    ScreenerDefinitions.GROWING -> StockIcons.TrendingUp
    ScreenerDefinitions.DIVIDEND -> StockIcons.Coin
    ScreenerDefinitions.STRONG -> StockIcons.Shield
    else -> StockIcons.Tag
}

/** Preset cards: two columns while every title word fits one column (shared `StockCardGridLayout`), else one; height follows content. */
@Composable
private fun PresetGrid(presets: List<ScreenerPreset>, onPreset: (String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val typography = StockStepsTheme.typography
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widest = with(density) {
            presets.flatMap { StockCardGridLayout.words(it.name) }.maxOfOrNull { measurer.measure(it, typography.cardTitle).size.width }?.toDp()?.value ?: 0f
        }
        val columns = StockCardGridLayout.columns(widest, maxWidth.value, spacing.sm.value, spacing.cardPaddingCompact.value,
            accessibilityText = density.fontScale >= StockStepsTheme.dimensions.largeFontScale * 1.15f)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            presets.chunked(columns).forEach { rowPresets ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    rowPresets.forEach { preset -> PresetCard(preset, Modifier.weight(1f).fillMaxHeight()) { onPreset(preset.id) } }
                    repeat(columns - rowPresets.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PresetCard(preset: ScreenerPreset, modifier: Modifier, onClick: () -> Unit) {
    val colors = StockStepsTheme.colors
    val summary = ScreenerDisplay.presetSummary(preset)
    StockCard(modifier.semantics(mergeDescendants = true) {}, onClick = onClick, onClickLabel = "Show ${preset.name}", bordered = false,
        contentPadding = PaddingValues(StockStepsTheme.spacing.cardPaddingCompact), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        StockIconTile(presetIcon(preset.id), colors.primaryContainer, colors.primaryText)
        Text(preset.name, style = StockStepsTheme.typography.cardTitle, color = colors.textTitle)
        Text(summary, style = StockStepsTheme.typography.caption, color = colors.textSupporting)
    }
}

/**
 * "3 filters" (shows or hides the full list of applied filters), "Clear all" (Phase 8B, only with filters) and "Edit filters"; they wrap onto
 * two lines when they don't fit.
 */
@Composable
private fun FilterBar(count: Int, expanded: Boolean, modifier: Modifier, onToggle: () -> Unit, onEdit: () -> Unit, onClearAll: () -> Unit) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.Start), verticalArrangement = Arrangement.spacedBy(spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget).clip(StockStepsTheme.shapes.pill).background(colors.primaryContainer)
            .clickable(enabled = count > 0, onClickLabel = if (expanded) "Hide filter list" else "Show filter list", onClick = onToggle)
            .semantics { stateDescription = if (count == 0) "" else if (expanded) "Expanded" else "Collapsed" }
            .padding(horizontal = spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(ScreenerDisplay.filterCount(count), style = StockStepsTheme.typography.label, color = colors.primaryText)
            if (count > 0) Icon(StockIcons.ChevronDown, null, tint = colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
        }
        if (count > 0) TextButton(onClick = onClearAll, modifier = Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .semantics { contentDescription = ScreenerDisplay.CLEAR_ALL_SPOKEN }) {
            Text(ScreenerDisplay.CLEAR_ALL, style = StockStepsTheme.typography.label, color = colors.primaryText)
        }
        Spacer(Modifier.weight(1f))
        StockButton("Edit filters", onEdit, variant = StockButtonVariant.SECONDARY)
    }
}

/** Applied filters: compact removable chips in a fading row, or (expanded) one row per filter with a 48 dp remove button. */
@Composable
private fun ActiveFilters(filters: List<ActiveFilterView>, expanded: Boolean, modifier: Modifier, onRemove: (ActiveFilterView) -> Unit) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    if (expanded) StockCard(modifier, bordered = false, contentPadding = PaddingValues(start = spacing.cardPadding, top = spacing.xxs, bottom = spacing.xxs)) {
        filters.forEachIndexed { index, filter ->
            if (index > 0) StockDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.rowCompactMinHeight), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = spacing.xs).semantics(mergeDescendants = true) {}) {
                    Text(filter.label, style = StockStepsTheme.typography.bodySemiBold, color = colors.textTitle)
                    Text(filter.value, style = StockStepsTheme.typography.caption, color = colors.textSupporting)
                }
                IconButton(onClick = { onRemove(filter) }) { Icon(StockIcons.Close, "Remove filter: ${filter.spoken}", tint = colors.iconSecondary) }
            }
        }
    } else StockFadingScrollRow(filters, null, modifier, spacing.xs) { filter, itemModifier ->
        Row(itemModifier.heightIn(min = StockStepsTheme.dimensions.touchTarget).clip(StockStepsTheme.shapes.pill).background(colors.surfaceSecondary)
            .clickable(onClickLabel = "Remove filter") { onRemove(filter) }
            // Cleared semantics drop the clickable's action, so it is set again (as on Watchlist rows) for TalkBack's double-tap.
            .clearAndSetSemantics {
                contentDescription = "${filter.spoken}. Remove filter"; role = androidx.compose.ui.semantics.Role.Button
                onClick(label = "Remove filter") { onRemove(filter); true }
            }
            .padding(start = spacing.md, end = spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(filter.chipText, style = StockStepsTheme.typography.label, color = colors.textBody, maxLines = 1)
            Icon(StockIcons.Close, null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
        }
    }
}

/** "24 companies match" (with a Sample badge for MOCK data) and the sort menu; then one coverage line and why these values are shown. */
@Composable
private fun ResultsHeader(state: ScreenerUiState, modifier: Modifier, onSort: (ScreenerSort) -> Unit, onMetricsHelp: () -> Unit) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val typography = StockStepsTheme.typography
    val sort = (state.applied ?: state.draft).sort
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
            StockTitleWithBadge(title = {
                // While a search runs, no count is shown (never a misleading "0 companies match").
                Text(if (state.loading) "Finding companies…" else if (state.error != null) "Results" else ScreenerDisplay.matchLine(state.total),
                    Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }, style = typography.sectionTitle, color = colors.textTitle)
            }, badge = { if (state.sampleData) StockStatusBadge("Sample", StockBadgeKind.SAMPLE, size = StockBadgeSize.COMPACT) })
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)
                    .semantics { contentDescription = ScreenerDisplay.sortSpoken(sort) + ". Change sort" }) {
                    Text(ScreenerDisplay.sortLabel(sort), style = typography.label, color = colors.primaryText)
                }
                DropdownMenu(open, onDismissRequest = { open = false }) {
                    ScreenerDisplay.sortOptions(state.displayMetrics).forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { open = false; onSort(option.sort) },
                            trailingIcon = if (option.sort == sort) ({ Icon(StockIcons.Check, "Selected") }) else null)
                    }
                }
            }
        }
        ScreenerDisplay.coverageLine(state.universe, state.excludedForMissingData)?.takeIf { !state.loading && state.error == null }?.let {
            Text(it, style = typography.caption, color = colors.textMeta)
        }
        // The server's warnings (price basis, coverage) stay visible; Phase 8B folds the explanation, preset notes and the metric guide into
        // one disclosure so the first rows aren't pushed below the fold.
        state.warnings.forEach { Text(it, style = typography.caption, color = colors.textSupporting) }
        val explanation = ScreenerDisplay.resultsExplanation(state.applied, state.displayMetrics).takeIf { state.rows.isNotEmpty() }
        val notes = state.activePreset?.notes.orEmpty()
        if (!state.loading && state.error == null && (explanation != null || notes.isNotEmpty())) StockDisclosure(ScreenerDisplay.ABOUT_RESULTS) {
            explanation?.let { Text(it, style = typography.caption, color = colors.textSupporting) }
            notes.forEach { Text(it, style = typography.caption, color = colors.textSupporting) }
            if (explanation != null) StockButton("What these numbers mean", onMetricsHelp, variant = StockButtonVariant.TEXT)
        }
    }
}

/**
 * One result: the Watchlist quote row pattern (Phase 5A) — logo, symbol and two-line name, price and daily change beside them or stacked under
 * them by the shared `StockQuoteRowLayout` — then the shown metrics as label/value pairs that wrap. The row is one spoken element that opens
 * Company Details; ⋮ holds Compare, Watchlist and Portfolio. A missing price reads "No data available", never $0.00.
 */
@Composable
private fun ResultRow(row: ScreenerRowView, metrics: List<ScreenerMetricItem>, actions: ScreenerActions, watchlistAvailable: Boolean) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val dimensions = StockStepsTheme.dimensions
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    var menu by remember { mutableStateOf(false) }
    val direction = when (row.change.tone) { Tone.POSITIVE -> PriceDirection.UP; Tone.NEGATIVE -> PriceDirection.DOWN; else -> if (row.change.available) PriceDirection.UNCHANGED else PriceDirection.UNAVAILABLE }
    val values: @Composable () -> Unit = {
        if (row.priceAvailable) {
            Text(row.price, style = typography.numberLabelStrong, color = colors.textValue, maxLines = 1)
            if (row.change.available) StockPriceChange(row.change.text, direction, style = typography.numberLabel)
        } else Text(ScreenerDisplay.UNAVAILABLE, style = typography.caption, color = colors.textTertiary, maxLines = 1)
    }
    val indent = spacing.cardPadding + dimensions.logo + spacing.sm
    Row(verticalAlignment = Alignment.Top) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val valuesWidth = with(density) {
                listOfNotNull(row.price.takeIf { row.priceAvailable }?.let { measurer.measure(it, typography.numberLabelStrong).size.width },
                    row.change.text.takeIf { row.change.available }?.let { measurer.measure(it, typography.numberLabel).size.width + dimensions.iconSmall.roundToPx() })
                    .maxOrNull()?.toDp()?.value ?: 0f
            }
            val beside = maxWidth.value - dimensions.logo.value - spacing.cardPadding.value - spacing.sm.value
            val stacked = StockQuoteRowLayout.stacksValues(valuesWidth, beside, accessibilityText = density.fontScale >= dimensions.largeFontScale * 1.15f)
            val open = { actions.onOpen(row.symbol) }
            Column(Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = open).clearAndSetSemantics {
                contentDescription = row.accessibility + metrics.filter { it.filtered }.takeIf { it.isNotEmpty() }?.let { " Matches your filters." }.orEmpty()
                role = androidx.compose.ui.semantics.Role.Button
                onClick(label = "Open details") { open(); true }
            }) {
                StockRow(symbol = row.symbol, name = row.name, logoUrl = row.logoUrl, nameMaxLines = 2,
                    contentPadding = PaddingValues(start = spacing.cardPadding, end = spacing.xs, top = spacing.sm, bottom = spacing.xxs),
                    trailing = if (stacked) ({}) else ({ values() }))
                if (stacked) FlowRow(Modifier.padding(start = indent, end = spacing.sm, bottom = spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm), itemVerticalAlignment = Alignment.CenterVertically) { values() }
                // Phase 8B: the sector, so a mixed list can be scanned by type of business (the provider's value; absent when missing).
                row.sector?.let { Text(it, Modifier.padding(start = indent, end = spacing.sm), style = typography.caption, color = colors.textMeta) }
                if (metrics.isNotEmpty() || row.selected) FlowRow(Modifier.padding(start = indent, end = spacing.sm, top = spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    metrics.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label, style = typography.caption, color = colors.textSupporting)
                            Text(item.text, style = if (item.available) typography.numberLabel else typography.caption,
                                color = when { !item.available -> colors.textTertiary; item.tone == Tone.POSITIVE -> colors.positiveText; item.tone == Tone.NEGATIVE -> colors.negativeText; else -> colors.textValue })
                        }
                    }
                    if (row.selected) StockStatusBadge("Comparing", StockBadgeKind.INFO, size = StockBadgeSize.COMPACT)
                }
                val notes = row.notes + if (row.stale) listOf("Financial statements may be out of date.") else emptyList()
                notes.forEach { Text(it, Modifier.padding(start = indent, end = spacing.sm, top = spacing.xxs), style = typography.caption, color = colors.textSupporting) }
                Spacer(Modifier.height(spacing.sm))
            }
        }
        Box(Modifier.padding(top = spacing.xs)) {
            IconButton(onClick = { menu = true }) { Icon(StockIcons.More, "More actions for ${row.symbol}", tint = colors.iconSecondary) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("View details") }, onClick = { menu = false; actions.onOpen(row.symbol) })
                DropdownMenuItem(text = { Text(if (row.selected) "Remove from comparison" else "Add to comparison") }, onClick = { menu = false; actions.onToggleCompare(row.symbol, row.name) })
                if (watchlistAvailable) DropdownMenuItem(text = { Text("Add to watchlist") }, onClick = { menu = false; actions.onWatchlist(row) })
                DropdownMenuItem(text = { Text("Add to portfolio") }, onClick = { menu = false; actions.onPortfolio(row) })
            }
        }
    }
}

/** Related tools as one grouped list of navigation rows (the Watchlist "Alerts and more" pattern). Guests see the sign-in row instead of saved screens. */
@Composable
private fun Tools(state: ScreenerUiState, modifier: Modifier, onSaved: () -> Unit, onSignIn: () -> Unit, onCompare: () -> Unit, onHelp: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
        StockSectionHeader("More tools")
        StockCard(bordered = false, contentPadding = PaddingValues(horizontal = StockStepsTheme.spacing.md, vertical = StockStepsTheme.spacing.xxs)) {
            if (state.signedIn) StockNavigationRow("Saved screens", onSaved, icon = StockIcons.Star,
                detail = state.saved?.let { "${it.screens.size} of ${it.limit} saved" } ?: "Keep filters to reuse later")
            else StockNavigationRow("Sign in to save screens", onSignIn, icon = StockIcons.Person, detail = "Keep filters to reuse later")
            StockDivider()
            StockNavigationRow("Compare companies", onCompare, icon = StockIcons.PieChart,
                detail = if (state.selected.isEmpty()) "See up to $MAX_COMPARED_COMPANIES side by side" else "${state.selected.size} selected · up to $MAX_COMPARED_COMPANIES")
            StockDivider()
            StockNavigationRow("How screening works", onHelp, icon = StockIcons.Help, detail = "Presets, criteria and missing data")
        }
    }
}

@Composable
private fun CompareBar(state: ScreenerUiState, onCompare: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    Surface(Modifier.fillMaxWidth(), color = StockStepsTheme.colors.surface, shadowElevation = spacing.xs) {
        Row(Modifier.padding(horizontal = spacing.screen, vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(state.selected.joinToString { it.symbol }, Modifier.weight(1f), style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textBody,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            StockButton(if (state.canCompare) "Compare (${state.selected.size})" else "Select 2 to compare", onCompare, enabled = state.canCompare)
        }
    }
}

/** "How screening works": what a screen is, then every preset with its exact criteria, data period and missing-data rule (was the ⓘ dialog). */
@Composable
private fun ScreeningHelpSheet(state: ScreenerUiState, onPreset: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val typography = StockStepsTheme.typography
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.screen).padding(bottom = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text("How screening works", Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textTitle)
            Text("A screen lists the companies whose latest reported values fall inside every range you set. Companies without a valid value for a " +
                "filtered metric are left out, never counted as zero.", style = typography.body, color = colors.textBody)
            state.universe?.let { Text(if (it.complete) "Results cover all ${it.size} companies StockSteps checks." else "Results cover ${it.evaluated} of ${it.size} companies StockSteps checks.", style = typography.small, color = colors.textSupporting) }
            Text("Presets are shortcuts with visible criteria, not recommendations. You can change any filter after applying one.", style = typography.small, color = colors.textSupporting)
            state.presets.forEach { preset ->
                StockCard(bordered = false, containerColor = colors.surfaceSecondary, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(preset.name, Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textTitle)
                    Text(preset.description, style = typography.small, color = colors.textBody)
                    ScreenerDisplay.presetCriteria(preset).forEach { Text("• $it", style = typography.small, color = colors.textBody) }
                    Text("Data period: ${preset.dataPeriod}", style = typography.caption, color = colors.textSupporting)
                    Text("Missing data: ${preset.missingData}", style = typography.caption, color = colors.textSupporting)
                    preset.notes.forEach { Text(it, style = typography.caption, color = colors.textSupporting) }
                    StockButton("Use this preset", { onPreset(preset.id) }, variant = StockButtonVariant.SECONDARY)
                }
            }
        }
    }
}

@Composable
private fun SavedScreensSheet(state: ScreenerUiState, actions: ScreenerActions, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<SavedScreen?>(null) }
    val saved = state.saved
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = StockStepsTheme.spacing.screen, vertical = StockStepsTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            Text("Saved screens", Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle)
            saved?.let { Text("${it.screens.size} of ${it.limit} used${if (!it.plus) " · StockSteps+ keeps up to 25" else ""}. Saved screens store filters, so results stay current.",
                style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary) }
            if (state.applied != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
                OutlinedTextField(name, { name = it.take(40) }, Modifier.weight(1f), label = { Text("Name this screen") }, singleLine = true)
                Button(onClick = { actions.onSave(name); name = "" }, enabled = name.isNotBlank()) { Text("Save") }
            }
            saved?.screens.orEmpty().forEach { screen ->
                Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(onClickLabel = "Apply ${screen.name}") { actions.onApplySaved(screen.id); onDismiss() }) {
                        Text(screen.name, style = StockStepsTheme.typography.bodySemiBold)
                        Text(ScreenerDisplay.filterCount(screen.query.activeFilterCount), style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
                    }
                    TextButton(onClick = { renaming = screen }) { Text("Rename") }
                    TextButton(onClick = { actions.onDeleteSaved(screen.id) }) { Text("Delete") }
                }
            }
            if (saved?.screens.isNullOrEmpty()) Text("No saved screens yet.", style = StockStepsTheme.typography.small)
            Spacer(Modifier.height(StockStepsTheme.spacing.xl))
        }
    }
    renaming?.let { screen ->
        var value by remember(screen.id) { mutableStateOf(screen.name) }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename screen") },
            text = { OutlinedTextField(value, { value = it.take(40) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { actions.onRenameSaved(screen.id, value); renaming = null }, enabled = value.isNotBlank()) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
    }
}

/** What a metric means, how it's calculated, why investors look at it and its limits (from MetricEducation). */
@Composable
internal fun MetricInfoSheet(info: MetricInfo, onDismiss: () -> Unit) = MetricsInfoSheet(listOf(info), onDismiss)

/** One or more metric explanations in one sheet (Phase 5B: "What these numbers mean" for the values shown on result rows). */
@Composable
internal fun MetricsInfoSheet(infos: List<MetricInfo>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = StockStepsTheme.spacing.screen).padding(bottom = StockStepsTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            infos.forEachIndexed { index, info ->
                if (index > 0) StockDivider(Modifier.padding(vertical = StockStepsTheme.spacing.xs))
                Text(info.title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle)
                Text(info.meaning, style = StockStepsTheme.typography.body)
                info.calculation?.let { Text("How it's calculated", style = StockStepsTheme.typography.label); Text(it, style = StockStepsTheme.typography.small) }
                info.why?.let { Text("Why investors look at it", style = StockStepsTheme.typography.label); Text(it, style = StockStepsTheme.typography.small) }
                info.period?.let { Text("Period: $it", style = StockStepsTheme.typography.small) }
                if (info.limitations.isNotEmpty()) { Text("Limitations", style = StockStepsTheme.typography.label); info.limitations.forEach { Text(it, style = StockStepsTheme.typography.small) } }
            }
        }
    }
}

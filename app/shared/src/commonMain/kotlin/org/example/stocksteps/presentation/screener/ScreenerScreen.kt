package org.example.stocksteps.presentation.screener

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.screener.*

internal class ScreenerActions(
    val onPreset: (String) -> Unit,
    val onRange: (String, Double?, Double?) -> Unit,
    val onChoice: (ChoiceField, List<String>) -> Unit,
    val onClearFilter: (String) -> Unit,
    val onReset: () -> Unit,
    val onApply: () -> Unit,
    val onSort: (ScreenerSort) -> Unit,
    val onLoadMore: () -> Unit,
    val onRetry: () -> Unit,
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
    val onDismissMessage: () -> Unit
)

/**
 * Discover Stocks. Presets are screening shortcuts with visible criteria, never recommendations.
 * Filter edits stay in a draft until "Show results"; results are filtered, sorted and paged by the
 * server across the whole evaluated universe.
 */
@Composable
internal fun ScreenerScreen(state: ScreenerUiState, modifier: Modifier, actions: ScreenerActions, watchlistAvailable: Boolean) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var filters by rememberSaveable { mutableStateOf(false) }
    var savedSheet by rememberSaveable { mutableStateOf(false) }
    var info by remember { mutableStateOf<MetricInfo?>(null) }
    var presetInfo by remember { mutableStateOf<ScreenerPreset?>(null) }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 3 } == true } }
    LaunchedEffect(nearEnd, state.hasMore) { if (nearEnd && state.hasMore && !state.loading) actions.onLoadMore() }

    Box(modifier.background(colors.appBackground)) {
        LazyColumn(state = listState, contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.md, bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md), modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Text("Discover Stocks", Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
                    Text("Find companies that match the financial characteristics you're interested in.", style = typography.small, color = colors.textSecondary)
                    if (state.sampleData) StockSampleDataBanner("Sample data for development: generated and captured values, not live markets.", Modifier.padding(top = spacing.xs))
                }
            }
            when {
                state.catalogLoading -> item(key = "catalog-loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading screener" }) }
                state.catalogError != null -> item(key = "catalog-error") { StockErrorState(state.catalogError.orEmpty(), onRetry = null) }
                else -> item(key = "presets") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        state.presets.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                pair.forEach { preset -> PresetCard(preset, preset.id == state.activePreset?.id, Modifier.weight(1f).fillMaxHeight(), { actions.onPreset(preset.id) }, { presetInfo = preset }) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item(key = "controls") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { filters = true }, enabled = state.metrics.isNotEmpty()) {
                        Text(if (state.activeFilterCount > 0) "Filters (${state.activeFilterCount})" else "Filters")
                    }
                    SortMenu(state, actions.onSort)
                    if (state.signedIn) OutlinedButton(onClick = { savedSheet = true }) { Text("Saved (${state.saved?.screens?.size ?: 0})") }
                    else TextButton(onClick = actions.onSignIn) { Text("Sign in to save screens") }
                }
            }
            state.applied?.takeIf { it.ranges.isNotEmpty() || it.choices.isNotEmpty() }?.let { applied ->
                item(key = "active-filters") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        applied.ranges.forEach { range ->
                            val definition = state.definition(range.metric)
                            InputChip(selected = true, onClick = { actions.onClearFilter(range.metric) },
                                label = { Text("${definition?.label ?: range.metric}: ${rangeText(range, definition)}", maxLines = 1) },
                                trailingIcon = { Text("✕") },
                                modifier = Modifier.semantics { contentDescription = "Remove filter ${definition?.label ?: range.metric}" })
                        }
                        applied.choices.forEach { choice -> AssistChip(onClick = { filters = true }, label = { Text("${choice.field.label}: ${choice.values.joinToString()}", maxLines = 1) }) }
                        TextButton(onClick = actions.onReset) { Text("Reset all") }
                    }
                }
            }
            if (state.applied == null && !state.catalogLoading && state.catalogError == null) item(key = "hint") {
                StockInsightCard("Start with a preset or filters", "Each preset shows its exact criteria. Tap the ⓘ on a preset to see thresholds, data period and how missing data is handled.", tone = InsightTone.EDUCATION)
            }
            if (state.applied != null) item(key = "summary") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading results" })
                    state.error?.let { StockErrorState(it, actions.onRetry) }
                    if (!state.loading && state.error == null) Text("${state.total} ${if (state.total == 1) "company matches" else "companies match"}", style = typography.label, color = colors.textPrimary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    state.universe?.let { u -> Text(if (u.complete) "Evaluated across all ${u.size} companies in the universe." else "Evaluated ${u.evaluated} of ${u.size} companies; the rest aren't included yet.", style = typography.caption, color = colors.textSecondary) }
                    if (state.excludedForMissingData > 0) Text("${state.excludedForMissingData} left out because a filtered metric is missing or not meaningful for them.", style = typography.caption, color = colors.textSecondary)
                    state.warnings.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
                    state.activePreset?.notes?.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
                }
            }
            if (state.applied != null && !state.loading && state.error == null && state.rows.isEmpty()) item(key = "empty") {
                StockEmptyState("No companies match these filters. Try widening a range or removing a filter.", actionText = "Reset all", onAction = actions.onReset)
            }
            items(state.rows, key = { it.symbol }) { row -> ResultRow(row, state.displayMetrics, actions, watchlistAvailable) { info = MetricFormatter.education(it, state.definition(it)) } }
            if (state.loadingMore) item(key = "more") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.applied != null) item(key = "disclaimer") {
                Text("Screens describe financial characteristics. They aren't recommendations to buy or sell.", style = typography.caption, color = colors.textTertiary)
            }
        }
        if (state.selected.isNotEmpty()) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = colors.surface, shadowElevation = StockStepsTheme.spacing.xs) {
                Row(Modifier.padding(horizontal = spacing.screen, vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.selected.joinToString { it.symbol }, Modifier.weight(1f), style = typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Button(onClick = actions.onCompare, enabled = state.canCompare) { Text(if (state.canCompare) "Compare (${state.selected.size})" else "Select 2 to compare") }
                }
            }
        }
    }
    if (filters) FilterSheet(state, actions, onDismiss = { filters = false }, onInfo = { info = it })
    if (savedSheet) SavedScreensSheet(state, actions, onDismiss = { savedSheet = false })
    info?.let { MetricInfoSheet(it) { info = null } }
    presetInfo?.let { preset -> PresetInfoDialog(preset, state) { presetInfo = null } }
    state.message?.let { message ->
        AlertDialog(onDismissRequest = actions.onDismissMessage, text = { Text(message) }, confirmButton = { TextButton(onClick = actions.onDismissMessage) { Text("OK") } })
    }
}

internal fun rangeText(range: RangeFilter, definition: MetricDefinition?): String {
    fun v(x: Double) = when (definition?.unit) {
        MetricUnit.PERCENT -> "${MetricFormatter.decimals(x, 1)}%"
        MetricUnit.MULTIPLE -> "${MetricFormatter.decimals(x, 1)}×"
        MetricUnit.MONEY -> MetricFormatter.money(x, if (definition.id == "marketCap") "USD" else null)
        else -> MetricFormatter.decimals(x, 2)
    }
    val min = range.min; val max = range.max
    return when {
        min != null && max != null -> "${v(min)}–${v(max)}"
        min != null -> "≥ ${v(min)}"
        max != null -> "≤ ${v(max)}"
        else -> "any"
    }
}

@Composable
private fun PresetCard(preset: ScreenerPreset, selected: Boolean, modifier: Modifier, onClick: () -> Unit, onInfo: () -> Unit) {
    val colors = StockStepsTheme.colors
    StockCard(modifier, onClick = onClick, onClickLabel = "Apply ${preset.name}",
        borderColor = if (selected) colors.primary else colors.border) {
        Row(verticalAlignment = Alignment.Top) {
            Text(preset.name, Modifier.weight(1f), style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
            IconButton(onClick = onInfo, modifier = Modifier.semantics { contentDescription = "${preset.name} criteria" }) { Text("ⓘ", color = colors.primary) }
        }
        Text(preset.description, style = StockStepsTheme.typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun PresetInfoDialog(preset: ScreenerPreset, state: ScreenerUiState, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(preset.name) }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            Text(preset.description)
            Text("Filters", style = StockStepsTheme.typography.label)
            preset.ranges.forEach { r -> val d = state.definition(r.metric); Text("• ${d?.label ?: r.metric}: ${rangeText(r, d)}") }
            Text("Data period: ${preset.dataPeriod}", style = StockStepsTheme.typography.small)
            Text("Missing data: ${preset.missingData}", style = StockStepsTheme.typography.small)
            preset.notes.forEach { Text(it, style = StockStepsTheme.typography.small) }
            Text("Shortcut, not a recommendation. You can change any filter after applying it.", style = StockStepsTheme.typography.caption)
        }
    })
}

@Composable
private fun SortMenu(state: ScreenerUiState, onSort: (ScreenerSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val sort = (state.applied ?: state.draft).sort
    val label = when (sort.field) {
        SortField.MARKET_CAP -> "Market cap"; SortField.NAME -> "Name"; SortField.CHANGE_PERCENT -> "Daily change"
        SortField.METRIC -> state.definition(sort.metric ?: "")?.label ?: "Metric"
    } + if (sort.descending) " ↓" else " ↑"
    Box {
        OutlinedButton(onClick = { open = true }) { Text("Sort: $label", maxLines = 1) }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            val options = listOf(ScreenerSort(SortField.MARKET_CAP), ScreenerSort(SortField.MARKET_CAP, descending = false),
                ScreenerSort(SortField.NAME, descending = false), ScreenerSort(SortField.CHANGE_PERCENT)) +
                state.displayMetrics.flatMap { listOf(ScreenerSort(SortField.METRIC, it.id, true), ScreenerSort(SortField.METRIC, it.id, false)) }
            options.forEach { option ->
                val text = when (option.field) {
                    SortField.MARKET_CAP -> "Market cap"; SortField.NAME -> "Name"; SortField.CHANGE_PERCENT -> "Daily change"
                    SortField.METRIC -> state.definition(option.metric!!)?.label ?: option.metric
                } + if (option.field == SortField.NAME) " (A–Z)" else if (option.descending) " (high to low)" else " (low to high)"
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSort(option) })
            }
        }
    }
}

@Composable
private fun ResultRow(row: ScreenerRowView, metrics: List<MetricDefinition>, actions: ScreenerActions, watchlistAvailable: Boolean, onInfo: (String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    var menu by remember { mutableStateOf(false) }
    StockCard(contentPadding = PaddingValues(vertical = spacing.xs)) {
        StockRow(
            symbol = row.symbol, name = row.name, price = row.price, change = row.change.text,
            direction = when (row.change.tone) { Tone.POSITIVE -> PriceDirection.UP; Tone.NEGATIVE -> PriceDirection.DOWN; else -> if (row.change.available) PriceDirection.UNCHANGED else PriceDirection.UNAVAILABLE },
            logoUrl = row.logoUrl, compact = true, onClick = { actions.onOpen(row.symbol) },
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = row.accessibility }
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.cardPadding), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            row.metrics.forEachIndexed { index, (label, cell) ->
                val id = metrics.getOrNull(index)?.id
                Column(Modifier.weight(1f).clickable(enabled = id != null, onClickLabel = "About $label") { id?.let(onInfo) }) {
                    Text(label, style = StockStepsTheme.typography.tiny, color = colors.textSecondary, maxLines = 2)
                    Text(cell.text, style = StockStepsTheme.typography.numberLabelStrong,
                        color = when (cell.tone) { Tone.POSITIVE -> colors.positiveText; Tone.NEGATIVE -> colors.negativeText; else -> colors.textPrimary })
                }
            }
        }
        val notes = row.notes + if (row.stale) listOf("Financial statements may be out of date.") else emptyList()
        notes.forEach { Text(it, Modifier.padding(horizontal = spacing.cardPadding), style = StockStepsTheme.typography.tiny, color = colors.textSecondary) }
        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.xs), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = row.selected, onClick = { actions.onToggleCompare(row.symbol, row.name) }, label = { Text(if (row.selected) "Comparing" else "Compare") },
                modifier = Modifier.semantics { stateDescription = if (row.selected) "Selected for comparison" else "Not selected" })
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "More actions for ${row.symbol}" }) { Icon(org.example.stocksteps.designsystem.icons.StockIcons.More, contentDescription = null) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Open company details") }, onClick = { menu = false; actions.onOpen(row.symbol) })
                    if (watchlistAvailable) DropdownMenuItem(text = { Text("Add to watchlist") }, onClick = { menu = false; actions.onWatchlist(row) })
                    DropdownMenuItem(text = { Text("Add to portfolio") }, onClick = { menu = false; actions.onPortfolio(row) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(state: ScreenerUiState, actions: ScreenerActions, onDismiss: () -> Unit, onInfo: (MetricInfo) -> Unit) {
    val spacing = StockStepsTheme.spacing
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Filters", Modifier.weight(1f).semantics { heading() }, style = StockStepsTheme.typography.sectionTitle)
                    TextButton(onClick = actions.onReset) { Text("Reset all") }
                }
                Text("Leave a box empty for no limit. Nothing is searched until you tap Show results.", style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
            }
            item {
                Text("Company", style = StockStepsTheme.typography.label)
                ChoiceField.entries.filter { state.choices[it].orEmpty().size > 1 }.forEach { field ->
                    val selected = state.draft.choices.firstOrNull { it.field == field }?.values.orEmpty()
                    Text(field.label, style = StockStepsTheme.typography.small)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        state.choices[field].orEmpty().forEach { value ->
                            FilterChip(value in selected, onClick = { actions.onChoice(field, if (value in selected) selected - value else selected + value) }, label = { Text(value, maxLines = 1) })
                        }
                    }
                }
            }
            MetricGroup.entries.forEach { group ->
                val metrics = state.metrics.filter { it.group == group && it.filterable }
                if (metrics.isNotEmpty()) item(key = group.name) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(group.title, style = StockStepsTheme.typography.label)
                        metrics.forEach { definition -> RangeInput(definition, state.draft.ranges.firstOrNull { it.metric == definition.id }, actions.onRange) { onInfo(MetricFormatter.education(definition.id, definition)) } }
                    }
                }
            }
            item {
                Button(onClick = { actions.onApply(); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)) {
                    Text(if (state.draft.activeFilterCount > 0) "Show results (${state.draft.activeFilterCount} filters)" else "Show all companies")
                }
            }
        }
    }
}

/** Min/max inputs; values are typed in the metric's display units (percent as 12.5, money in billions). */
@Composable
private fun RangeInput(definition: MetricDefinition, current: RangeFilter?, onChange: (String, Double?, Double?) -> Unit, onInfo: () -> Unit) {
    val scale = if (definition.unit == MetricUnit.MONEY) 1e9 else 1.0
    val unit = when (definition.unit) { MetricUnit.PERCENT -> "%"; MetricUnit.MULTIPLE -> "×"; MetricUnit.MONEY -> "B"; else -> "" }
    fun text(v: Double?) = v?.let { (it / scale).toString().removeSuffix(".0") }.orEmpty()
    var min by remember(definition.id, current) { mutableStateOf(text(current?.min)) }
    var max by remember(definition.id, current) { mutableStateOf(text(current?.max)) }
    fun push() = onChange(definition.id, min.toDoubleOrNull()?.times(scale), max.toDoubleOrNull()?.times(scale))
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(definition.label + if (unit.isNotEmpty()) " ($unit)" else "", Modifier.weight(1f), style = StockStepsTheme.typography.body)
            TextButton(onClick = onInfo) { Text("What's this?") }
            if (current != null) TextButton(onClick = { min = ""; max = ""; onChange(definition.id, null, null) }) { Text("Clear") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            OutlinedTextField(min, { min = it; push() }, Modifier.weight(1f), label = { Text("Min") }, singleLine = true,
                placeholder = { Text(text(definition.suggestedMin)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(max, { max = it; push() }, Modifier.weight(1f), label = { Text("Max") }, singleLine = true,
                placeholder = { Text(text(definition.suggestedMax)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        val bothSet = min.toDoubleOrNull() != null && max.toDoubleOrNull() != null
        if (bothSet && min.toDouble() > max.toDouble()) Text("Minimum is above maximum.", color = StockStepsTheme.colors.negativeText, style = StockStepsTheme.typography.caption)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                        Text("${screen.query.activeFilterCount} filters", style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary)
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MetricInfoSheet(info: MetricInfo, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = StockStepsTheme.spacing.screen).padding(bottom = StockStepsTheme.spacing.xxl), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            Text(info.title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle)
            Text(info.meaning, style = StockStepsTheme.typography.body)
            info.calculation?.let { Text("How it's calculated", style = StockStepsTheme.typography.label); Text(it, style = StockStepsTheme.typography.small) }
            info.why?.let { Text("Why investors look at it", style = StockStepsTheme.typography.label); Text(it, style = StockStepsTheme.typography.small) }
            info.period?.let { Text("Period: $it", style = StockStepsTheme.typography.small) }
            if (info.limitations.isNotEmpty()) { Text("Limitations", style = StockStepsTheme.typography.label); info.limitations.forEach { Text(it, style = StockStepsTheme.typography.small) } }
        }
    }
}

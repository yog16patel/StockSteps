package org.example.stocksteps.presentation.screener

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.screener.*

/**
 * Compare Companies (Phase 1, free): pick 2–4 companies, then read beginner metrics grouped as
 * Overview, Growth, Profitability, Financial Health, Valuation and Shareholder Returns. Each metric's
 * label sits above equal-width company columns, so three companies fit a phone without horizontal
 * scrolling. Extra metrics are under "More metrics". Everything is calculated on the server and
 * formatted by the shared presenter; this screen only renders.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComparisonScreen(
    state: ComparisonUiState,
    modifier: Modifier,
    onRemove: (String) -> Unit,
    onAdd: (String, String) -> Unit,
    onReplace: (String, String, String) -> Unit,
    onExample: (Int) -> Unit,
    onSearch: suspend (String) -> List<StockSearchResult>,
    onPeriod: (PerformancePeriod) -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onDiscover: () -> Unit,
    onDismissMessage: () -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    /** null = closed; "" = add; otherwise the symbol being replaced. */
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<MetricInfo?>(null) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.md, bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("Compare Companies", Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
                Text("See companies side by side. The numbers describe each business; they don't say which is a better investment.", style = typography.small, color = colors.textSecondary)
                if (state.sampleData) StockSampleDataBanner("Sample data for development, not live markets.")
            }
        }
        item(key = "selection") { SelectionCard(state, onAdd = { picking = "" }, onReplace = { picking = it }, onRemove = onRemove) }
        if (state.needsMore) {
            item(key = "examples") { ExamplesCard(state, onExample, onDiscover) }
            return@LazyColumn
        }
        if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading comparison" }) }
        state.error?.let { item(key = "error") { StockErrorState(it, onRetry) } }
        if (state.columns.isNotEmpty()) {
            stickyHeader(key = "companies") { CompanyHeader(state.columns, onOpen) }
            val onInfo: (ComparisonRow) -> Unit = { row -> info = MetricFormatter.education(row.id, ScreenerDefinitions.metric(row.id)).let { if (row.id == "marketCap") it.copy(title = "Market cap") else it } }
            state.sections.filterNot { it.advanced }.forEach { section -> sectionItems(section, state.columns, onInfo) }
            if (state.advancedCount > 0) item(key = "more") {
                StockButton(if (showMore) "Hide extra metrics" else "More metrics (${state.advancedCount})", onClick = { showMore = !showMore },
                    variant = StockButtonVariant.OUTLINED, modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (showMore) "Expanded" else "Collapsed" })
            }
            if (showMore) state.sections.filter { it.advanced && it.rows.isNotEmpty() }.forEach { section -> sectionItems(section, state.columns, onInfo) }
        }
        item(key = "chart") { PerformanceCard(state, onPeriod, onRetry) }
        if (state.observations.isNotEmpty()) item(key = "observations") {
            StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("What the numbers show", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
                state.observations.forEach { o ->
                    Text("• ${o.text}", style = typography.small, color = colors.textBody)
                    o.caveat?.let { Text(it, Modifier.padding(start = spacing.sm), style = typography.caption, color = colors.textSecondary) }
                }
                Text("These describe reported figures. They aren't rankings or recommendations.", style = typography.caption, color = colors.textTertiary)
            }
        }
        if (state.columns.isNotEmpty()) item(key = "notes") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("Sources and notes", Modifier.semantics { heading() }, style = typography.label, color = colors.textSecondary)
                state.notes.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
                state.asOf?.let { Text("Comparison prepared ${it.take(10)} ${it.drop(11).take(5)} UTC.", style = typography.caption, color = colors.textSecondary) }
                Text("Education, not investment advice. Past results don't indicate future returns.", style = typography.caption, color = colors.textTertiary)
            }
        }
    }
    picking?.let { replacing ->
        AddCompanyDialog(state, replacing.ifEmpty { null }, onSearch,
            onPick = { symbol, name -> if (replacing.isEmpty()) onAdd(symbol, name) else onReplace(replacing, symbol, name); picking = null }, onDismiss = { picking = null })
    }
    info?.let { MetricInfoSheet(it) { info = null } }
    state.message?.let { message ->
        AlertDialog(onDismissRequest = onDismissMessage, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismissMessage) { Text("OK") } })
    }
}

/** The chosen companies with name, ticker and listing; each can be replaced or removed in place. */
@Composable
private fun SelectionCard(state: ComparisonUiState, onAdd: () -> Unit, onReplace: (String) -> Unit, onRemove: (String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text("Companies (${state.selected.size} of $MAX_COMPARED_COMPANIES)", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        state.selected.forEach { company ->
            val column = state.columns.firstOrNull { it.symbol.equals(company.symbol, ignoreCase = true) }
            Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                StockTickerAvatar(symbol = company.symbol, logoUrl = column?.logoUrl, size = StockStepsTheme.dimensions.logoCompact)
                Column(Modifier.weight(1f).padding(start = spacing.sm)) {
                    Text(column?.name ?: company.name, style = typography.bodySemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(company.symbol, column?.listing?.ifBlank { null }).filterNotNull().joinToString(" · "), style = typography.caption, color = colors.textSecondary)
                }
                TextButton(onClick = { onReplace(company.symbol) }, Modifier.semantics { contentDescription = "Replace ${company.name}" }) { Text("Replace") }
                TextButton(onClick = { onRemove(company.symbol) }, Modifier.semantics { contentDescription = "Remove ${company.name}" }) { Text("Remove") }
            }
        }
        if (state.canAdd) StockButton("Add a company", onClick = onAdd, variant = StockButtonVariant.OUTLINED, icon = StockIcons.Search, modifier = Modifier.fillMaxWidth())
        Text(state.crowdedHint ?: "Two or three companies are easiest to read on a phone.", style = typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun ExamplesCard(state: ComparisonUiState, onExample: (Int) -> Unit, onDiscover: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val typography = StockStepsTheme.typography
    StockCard(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(if (state.selected.isEmpty()) "Pick two companies to start" else "Add one more company to compare", Modifier.semantics { heading() }, style = typography.cardTitle)
        Text("Search above, use Compare on a company's page or your watchlist, or try an example:", style = typography.small, color = StockStepsTheme.colors.textBody)
        state.examples.forEachIndexed { i, example ->
            StockCard(Modifier.fillMaxWidth(), onClick = { onExample(i) }, onClickLabel = "Compare ${example.title}", containerColor = StockStepsTheme.colors.surfaceSecondary, bordered = false) {
                Text(example.title, style = typography.bodySemiBold, color = StockStepsTheme.colors.textPrimary)
                Text(example.description, style = typography.caption, color = StockStepsTheme.colors.textSecondary)
            }
        }
        StockButton("Discover Stocks", onClick = onDiscover, variant = StockButtonVariant.TEXT)
    }
}

/** Equal-width company columns, kept visible while scrolling; tap opens the company. */
@Composable
private fun CompanyHeader(columns: List<CompanyColumn>, onOpen: (String) -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    Row(Modifier.fillMaxWidth().background(colors.appBackground).padding(vertical = StockStepsTheme.spacing.xs), horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        columns.forEach { column ->
            Column(Modifier.weight(1f).heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(onClickLabel = "Open ${column.name}") { onOpen(column.symbol) }) {
                Text(column.symbol, style = typography.bodySemiBold, color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(column.price, style = typography.tiny, color = colors.textSecondary, maxLines = 1)
                column.error?.let { Text("Partial data", style = typography.tiny, color = colors.cautionText, maxLines = 1) }
            }
        }
    }
    HorizontalDivider(color = colors.border)
}

private fun LazyListScope.sectionItems(section: ComparisonSection, columns: List<CompanyColumn>, onInfo: (ComparisonRow) -> Unit) {
    if (section.rows.isEmpty()) return
    item(key = "section-${section.title}") {
        Column(Modifier.padding(top = StockStepsTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            Text(section.title, Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary)
            section.note?.let { Text(it, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary) }
        }
    }
    section.rows.forEach { row -> item(key = "row-${section.title}-${row.id}") { MetricRowView(row, columns) { onInfo(row) } } }
}

/** One metric: its name (with an info button) and period above one value per company. N/A explains itself. */
@Composable
private fun MetricRowView(row: ComparisonRow, columns: List<CompanyColumn>, onInfo: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var why by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = StockStepsTheme.spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.label, style = typography.label, color = colors.textPrimary)
                row.period?.let { Text(it, style = typography.tiny, color = colors.textTertiary) }
            }
            IconButton(onClick = onInfo) { Icon(StockIcons.Info, contentDescription = "About ${row.label}", tint = colors.primary, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall)) }
        }
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = row.accessibility(columns) }, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            row.cells.forEachIndexed { i, cell ->
                Column(Modifier.weight(1f).heightIn(min = StockStepsTheme.dimensions.touchTarget)
                    .then(if (cell.explanation != null) Modifier.clickable(onClickLabel = "Why ${columns.getOrNull(i)?.symbol ?: ""} is unavailable") { why = cell.explanation } else Modifier)) {
                    Text(cell.text + if (cell.explanation != null) " ⓘ" else "", style = typography.numberLabel, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = when { cell.explanation != null -> colors.textTertiary; cell.tone == Tone.POSITIVE -> colors.positiveText; cell.tone == Tone.NEGATIVE -> colors.negativeText; else -> colors.textPrimary })
                    cell.detail?.let { Text(it, style = typography.tiny, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
    HorizontalDivider(color = colors.borderSubtle)
    why?.let { AlertDialog(onDismissRequest = { why = null }, title = { Text(row.label) }, text = { Text(it) }, confirmButton = { TextButton(onClick = { why = null }) { Text("OK") } }) }
}

@Composable
private fun PerformanceCard(state: ComparisonUiState, onPeriod: (PerformancePeriod) -> Unit, onRetry: () -> Unit) {
    val typography = StockStepsTheme.typography
    val colors = StockStepsTheme.colors
    StockCard(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Text("Share price change", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textPrimary)
        Text("Each line starts at 100 on the same day, so you compare percentage moves, not share prices. This is price only (no dividends) and says nothing about how the businesses performed.",
            style = typography.caption, color = colors.textSecondary)
        StockPillSelector(PerformancePeriod.entries, state.period, { it.label }, onPeriod)
        when {
            state.chartLoading -> LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading price history" })
            state.chartError != null -> StockErrorState(state.chartError.orEmpty(), onRetry)
            else -> state.chart?.let { chart ->
                val drawable = chart.series.filter { it.error == null }
                if (drawable.isEmpty() || chart.dates.size < 2) Text("Price history isn't available for this period.", style = typography.small)
                else {
                    val first = drawable.first()
                    StockTrendChart(
                        values = first.values,
                        details = chart.dates.indices.map { i -> chart.dates[i] + " · " + drawable.joinToString(" · ") { s -> "${s.symbol} ${s.values.getOrNull(i)?.let { MetricFormatter.decimals(it, 1) } ?: "—"}" } },
                        xLabels = listOf(chart.dates.first(), chart.dates.last()), yLabels = emptyList(),
                        description = "Price change from 100 over ${chart.period.label}: " + drawable.joinToString("; ") { s ->
                            "${s.symbol} ${s.change?.let { (if (it > 0) "up " else if (it < 0) "down " else "") + MetricFormatter.decimals(kotlin.math.abs(it), 1) + "%" } ?: "unavailable"}" },
                        reference = 100.0, referenceLabel = "Start (100)", seriesLabel = first.symbol,
                        additional = drawable.drop(1).map { it.symbol to it.values }
                    )
                    drawable.forEach { s -> Text("${s.symbol}: " + (s.change?.let { MetricFormatter.change(it).text } ?: "—") + (s.note?.let { " · $it" } ?: ""), style = typography.caption, color = colors.textBody) }
                }
                chart.series.filter { it.error != null }.forEach { Text("${it.symbol}: ${it.error}", style = typography.caption, color = colors.cautionText) }
                chart.notes.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
            }
        }
    }
}

@Composable
private fun AddCompanyDialog(state: ComparisonUiState, replacing: String?, onSearch: suspend (String) -> List<StockSearchResult>, onPick: (String, String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StockSearchResult>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(350) // debounce: search after typing pauses; a newer query cancels this one
        failed = false; searching = true
        results = try { onSearch(query).take(8) } catch (cause: Exception) {
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            failed = true; emptyList()
        }
        searching = false
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(replacing?.let { "Replace $it" } ?: "Add a company") }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            StockTextField(query, { query = it }, "Search by name or ticker", Modifier.fillMaxWidth(), placeholder = "e.g. Apple or RY.TO")
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text("Search isn't available right now. Try again.", style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.cautionText)
            if (!searching && !failed && query.isNotBlank() && results.isEmpty()) Text("No matching companies.", style = StockStepsTheme.typography.caption)
            results.forEach { stock ->
                val already = state.selected.any { it.symbol.equals(stock.symbol, ignoreCase = true) }
                Column(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(enabled = !already) { onPick(stock.symbol, stock.name) }
                    .padding(vertical = StockStepsTheme.spacing.xxs)) {
                    Text(stock.name, style = StockStepsTheme.typography.bodySemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (already) StockStepsTheme.colors.textTertiary else StockStepsTheme.colors.textPrimary)
                    Text(listOfNotNull(stock.symbol, stock.exchange, if (already) "already added" else null).joinToString(" · "), style = StockStepsTheme.typography.caption,
                        color = StockStepsTheme.colors.textSecondary)
                }
            }
        }
    })
}

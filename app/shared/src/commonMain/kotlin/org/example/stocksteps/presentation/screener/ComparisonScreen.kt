package org.example.stocksteps.presentation.screener

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.screener.*

private val LABEL_WIDTH = 132.dp
private val COLUMN_WIDTH = 116.dp

/**
 * Compare Stocks: a fixed metric-name column and company columns that scroll horizontally together
 * (header included), so four companies stay readable on a phone. N/A cells explain themselves.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComparisonScreen(
    state: ComparisonUiState,
    modifier: Modifier,
    onRemove: (String) -> Unit,
    onAdd: (String, String) -> Unit,
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
    val scroll = rememberScrollState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var info by remember { mutableStateOf<MetricInfo?>(null) }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    LazyColumn(modifier.background(colors.appBackground), contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.md, bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Compare Stocks", Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textPrimary)
                Text("See companies side by side. Differences describe the numbers; they don't say which is a better investment.", style = typography.small, color = colors.textSecondary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    state.selected.forEach { company ->
                        InputChip(selected = true, onClick = { onRemove(company.symbol) }, label = { Text(company.symbol) }, trailingIcon = { Text("✕") },
                            modifier = Modifier.semantics { contentDescription = "Remove ${company.name} from comparison" })
                    }
                    if (state.selected.size < MAX_COMPARED_COMPANIES) AssistChip(onClick = { adding = true }, label = { Text("+ Add company") })
                }
                if (state.sampleData) StockSampleDataBanner("Sample data for development, not live markets.")
            }
        }
        if (state.needsMore) {
            item(key = "empty") {
                StockEmptyState("Choose at least two companies to compare (up to $MAX_COMPARED_COMPANIES). Add them here, from Discover Stocks or from a company's page.",
                    actionText = "Discover Stocks", onAction = onDiscover)
            }
            return@LazyColumn
        }
        if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading comparison" }) }
        state.error?.let { item(key = "error") { StockErrorState(it, onRetry) } }
        if (state.observations.isNotEmpty()) item(key = "observations") {
            StockCard {
                Text("What the numbers show", Modifier.semantics { heading() }, style = typography.cardTitle)
                state.observations.forEach { o ->
                    Text("• ${o.text}", style = typography.small, color = colors.textBody)
                    o.caveat?.let { Text(it, Modifier.padding(start = spacing.sm), style = typography.caption, color = colors.textSecondary) }
                }
                Text("Observations compare reported figures only. They aren't rankings or recommendations.", style = typography.caption, color = colors.textTertiary)
            }
        }
        item(key = "chart") { PerformanceCard(state, onPeriod) }
        if (state.columns.isNotEmpty()) {
            stickyHeader(key = "companies") {
                Row(Modifier.fillMaxWidth().background(colors.appBackground).padding(vertical = spacing.xs)) {
                    Spacer(Modifier.width(LABEL_WIDTH))
                    Row(Modifier.horizontalScroll(scroll)) {
                        state.columns.forEach { column ->
                            Column(Modifier.width(COLUMN_WIDTH).clickable(onClickLabel = "Open ${column.name}") { onOpen(column.symbol) }.padding(horizontal = spacing.xxs),
                                horizontalAlignment = Alignment.End) {
                                StockTickerAvatar(symbol = column.symbol, logoUrl = column.logoUrl, size = StockStepsTheme.dimensions.logoCompact)
                                Text(column.symbol, style = typography.bodySemiBold, maxLines = 1)
                                Text(column.name, style = typography.tiny, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                                Text(column.price, style = typography.numberLabel, maxLines = 1)
                                Text(column.change.text, style = typography.tiny, maxLines = 1,
                                    color = when (column.change.tone) { Tone.POSITIVE -> colors.positiveText; Tone.NEGATIVE -> colors.negativeText; else -> colors.textSecondary })
                                column.error?.let { Text(it, style = typography.tiny, color = colors.textSecondary, maxLines = 3, textAlign = TextAlign.End) }
                            }
                        }
                    }
                }
            }
            state.sections.forEach { section ->
                if (section.rows.isEmpty()) return@forEach
                val open = section.title !in collapsed
                item(key = "section-${section.title}") {
                    Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
                        .clickable(onClickLabel = if (open) "Collapse ${section.title}" else "Expand ${section.title}") { collapsed = if (open) collapsed + section.title else collapsed - section.title }
                        .semantics { heading(); stateDescription = if (open) "Expanded" else "Collapsed" },
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(section.title, Modifier.weight(1f), style = typography.sectionTitle, color = colors.textPrimary)
                        Text(if (open) "−" else "+", style = typography.sectionTitle, color = colors.primary)
                    }
                    if (open) section.note?.let { Text(it, style = typography.caption, color = colors.textSecondary) }
                }
                if (open) section.rows.forEach { row ->
                    item(key = "row-${row.id}") { MetricRowView(row, state.columns, scroll) { info = MetricFormatter.education(row.id, ScreenerDefinitions.metric(row.id)) } }
                }
            }
            item(key = "notes") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    state.notes.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
                    Text("Not investment advice. Past results don't indicate future returns.", style = typography.caption, color = colors.textTertiary)
                }
            }
        }
    }
    if (adding) AddCompanyDialog(state, onSearch, onAdd = { symbol, name -> onAdd(symbol, name); adding = false }, onDismiss = { adding = false })
    info?.let { MetricInfoSheet(it) { info = null } }
    state.message?.let { message ->
        AlertDialog(onDismissRequest = onDismissMessage, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismissMessage) { Text("OK") } })
    }
}

@Composable
private fun MetricRowView(row: ComparisonRow, columns: List<CompanyColumn>, scroll: ScrollState, onInfo: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var why by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).semantics(mergeDescendants = true) { contentDescription = row.accessibility(columns) },
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(LABEL_WIDTH).clickable(onClickLabel = "About ${row.label}", onClick = onInfo)) {
            Text(row.label + " ⓘ", style = typography.small, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            row.period?.let { Text(it, style = typography.tiny, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Row(Modifier.horizontalScroll(scroll)) {
            row.cells.forEach { cell ->
                Text(cell.text, Modifier.width(COLUMN_WIDTH).padding(horizontal = StockStepsTheme.spacing.xxs)
                    .then(if (cell.explanation != null) Modifier.clickable(onClickLabel = "Why unavailable") { why = cell.explanation } else Modifier),
                    style = typography.numberLabel, textAlign = TextAlign.End, maxLines = 2,
                    color = when { cell.explanation != null -> colors.textTertiary; cell.tone == Tone.POSITIVE -> colors.positiveText; cell.tone == Tone.NEGATIVE -> colors.negativeText; else -> colors.textPrimary })
            }
        }
    }
    HorizontalDivider(color = colors.borderSubtle)
    why?.let { AlertDialog(onDismissRequest = { why = null }, title = { Text(row.label) }, text = { Text(it) }, confirmButton = { TextButton(onClick = { why = null }) { Text("OK") } }) }
}

@Composable
private fun PerformanceCard(state: ComparisonUiState, onPeriod: (PerformancePeriod) -> Unit) {
    val typography = StockStepsTheme.typography
    StockCard {
        Text("Price performance", Modifier.semantics { heading() }, style = typography.cardTitle)
        Text("Each line starts at 100, so you compare returns, not share prices.", style = typography.caption, color = StockStepsTheme.colors.textSecondary)
        StockPillSelector(PerformancePeriod.entries, state.period, { it.label }, onPeriod)
        when {
            state.chartLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            state.chartError != null -> Text(state.chartError.orEmpty(), style = typography.small)
            else -> state.chart?.let { chart ->
                val drawable = chart.series.filter { it.error == null }
                if (drawable.isEmpty() || chart.dates.size < 2) Text("Price history isn't available for this period.", style = typography.small)
                else {
                    val first = drawable.first()
                    StockTrendChart(
                        values = first.values,
                        details = chart.dates.indices.map { i -> chart.dates[i] + " · " + drawable.joinToString(" · ") { s -> "${s.symbol} ${s.values.getOrNull(i)?.let { MetricFormatter.decimals(it, 1) } ?: "—"}" } },
                        xLabels = listOf(chart.dates.first(), chart.dates.last()), yLabels = emptyList(),
                        description = "Growth of 100 over ${chart.period.label}: " + drawable.joinToString("; ") { s -> "${s.symbol} ${s.change?.let { MetricFormatter.decimals(it, 1) + "%" } ?: "unavailable"}" },
                        reference = 100.0, seriesLabel = first.symbol,
                        additional = drawable.drop(1).map { it.symbol to it.values }
                    )
                }
                chart.series.filter { it.error != null }.forEach { Text("${it.symbol}: ${it.error}", style = typography.caption) }
                chart.notes.forEach { Text(it, style = typography.caption, color = StockStepsTheme.colors.textSecondary) }
            }
        }
    }
}

@Composable
private fun AddCompanyDialog(state: ComparisonUiState, onSearch: suspend (String) -> List<StockSearchResult>, onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StockSearchResult>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(350) // debounce: search after typing pauses; a newer query cancels this one
        failed = false
        results = try { onSearch(query).take(8) } catch (cause: Exception) {
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            failed = true; emptyList()
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add a company") }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            OutlinedTextField(query, { query = it }, label = { Text("Search by name or symbol") }, singleLine = true)
            if (failed) Text("Search isn't available right now.", style = StockStepsTheme.typography.caption)
            results.forEach { stock ->
                val already = state.selected.any { it.symbol.equals(stock.symbol, ignoreCase = true) }
                Text("${stock.symbol} · ${stock.name}${if (already) " (added)" else ""}",
                    Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(enabled = !already) { onAdd(stock.symbol, stock.name) }.padding(vertical = StockStepsTheme.spacing.xs),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    })
}

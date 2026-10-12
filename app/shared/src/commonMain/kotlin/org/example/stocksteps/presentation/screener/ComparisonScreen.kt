package org.example.stocksteps.presentation.screener

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import org.example.stocksteps.theme.StockComparisonLayout
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.model.StockSearchResult
import org.example.stocksteps.screener.*

/**
 * Compare Companies (Global UI Refinement Phase 5C). Order: title (+ Sample badge) → selected companies (⋮ = Replace / Remove) → examples
 * while fewer than two → learning summary → metric sections → "More metrics" → share price chart → history → "More tools" (research, AI,
 * Discover) → observations → sources. Values sit in equal company columns only while every value and word fits a column (shared
 * `StockComparisonLayout`); otherwise each metric lists one line per company, so four companies on a narrow phone or large text never cut
 * or break a value. Everything is calculated on the server and interpreted by the shared presenter; this screen only renders.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    onDismissMessage: () -> Unit,
    guidance: GuidanceActions = GuidanceActions(),
    history: HistoryActions = HistoryActions(),
    onResearch: () -> Unit = {},
    onAskAi: () -> Unit = {}
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    /** null = closed; "" = add; otherwise the symbol being replaced. */
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<MetricInfo?>(null) }
    val list = rememberLazyListState()
    val entries = remember(state) { comparisonEntries(state) }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    var headerHeight by remember { mutableStateOf(0) }
    val onInfo: (ComparisonRow) -> Unit = { row -> info = MetricFormatter.education(row.id, ScreenerDefinitions.metric(row.id)).let { if (row.id == "marketCap") it.copy(title = "Market cap") else it } }
    BoxWithConstraints(modifier.background(colors.appBackground)) {
        // Shared rule: measure every value/period word (and the symbols) once per comparison, then pick columns or stacked lines.
        val shownRows = state.sections.filter { !it.advanced || state.showMore }.flatMap { it.rows }
        val rowWidth = maxWidth.value - (spacing.screen.value + spacing.cardPadding.value) * 2
        val iconSmall = StockStepsTheme.dimensions.iconSmall.value
        val accessibilityText = density.fontScale >= StockStepsTheme.dimensions.largeFontScale * 1.15f
        val sideBySide = remember(shownRows, state.columns, rowWidth, density.fontScale) {
            val tokens = ComparisonDisplay.layoutTexts(shownRows).flatMap(StockComparisonLayout::tokens) + state.columns.map { it.symbol }
            val widest = with(density) { tokens.distinct().maxOfOrNull { measurer.measure(it, typography.numberLabel).size.width.toDp().value } ?: 0f }
            val noData = with(density) { measurer.measure(ComparisonDisplay.NO_DATA, typography.numberLabel).size.width.toDp().value } +
                iconSmall + spacing.xxs.value
            StockComparisonLayout.sideBySide(maxOf(widest, noData), state.columns.size, rowWidth, spacing.xs.value, accessibilityText)
        }
        // "Explore next" / "Explain this": scroll to the metric's row, below the pinned company header only when it is shown.
        LaunchedEffect(state.focus, entries, sideBySide) {
            val focus = state.focus ?: return@LaunchedEffect
            val index = entries.indexOfFirst { it is ComparisonEntry.Metric && it.row.id == focus }
            if (index >= 0) list.animateScrollToItem(index, if (sideBySide) -headerHeight else 0)
            guidance.onFocusHandled()
        }
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.md, bottom = spacing.xxl)) {
            entries.forEach { entry ->
                if (entry is ComparisonEntry.Companies) {
                    if (sideBySide) stickyHeader(key = entry.key) { CompanyHeader(state.columns, onOpen, Modifier.onSizeChanged { headerHeight = it.height }) }
                    else item(key = entry.key) { Spacer(Modifier.height(spacing.sm)) }
                }
                // A zero-height pinned item after the metrics replaces the company header, so it doesn't cover the chart, history or tools.
                else if (entry == ComparisonEntry.CompaniesEnd) { if (sideBySide) stickyHeader(key = entry.key) { Spacer(Modifier.fillMaxWidth()) } }
                else item(key = entry.key) {
                    val gap = Modifier.padding(top = when (entry) {
                        ComparisonEntry.Header -> 0.dp
                        is ComparisonEntry.Metric -> 0.dp
                        is ComparisonEntry.SectionTitle -> spacing.lg
                        else -> spacing.md
                    })
                    when (entry) {
                        ComparisonEntry.Header -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                            StockTitleWithBadge(title = { Text(ComparisonDisplay.TITLE, Modifier.semantics { heading() }, style = typography.screenTitle, color = colors.textTitle) },
                                badge = { if (state.sampleData) StockStatusBadge("Sample", org.example.stocksteps.theme.StockBadgeKind.SAMPLE, size = StockBadgeSize.COMPACT) })
                            Text(ComparisonDisplay.SUBTITLE, style = typography.small, color = colors.textSupporting)
                        }
                        ComparisonEntry.Selection -> SelectionCard(state, gap, onAdd = { picking = "" }, onReplace = { picking = it }, onRemove = onRemove)
                        ComparisonEntry.Examples -> ExamplesCard(state, gap, onExample, onDiscover)
                        ComparisonEntry.Loading -> StockLoadingState(gap) {
                            StockCard(bordered = false, contentPadding = PaddingValues(vertical = spacing.xxs)) { repeat(3) { StockRowSkeleton() } }
                        }
                        ComparisonEntry.Error -> StockCard(gap, bordered = false) {
                            StockErrorState(state.error.orEmpty(), onRetry, title = ComparisonDisplay.ERROR_TITLE, icon = StockIcons.Warning)
                        }
                        ComparisonEntry.Summary -> Box(gap) { ComparisonLearningSummary(state.insights, guidance.onRelated) }
                        is ComparisonEntry.SectionTitle -> Column(gap.fillMaxWidth().clip(RoundedCornerShape(topStart = cardRadius, topEnd = cardRadius)).background(colors.surface)
                            .padding(start = spacing.cardPadding, end = spacing.cardPadding, top = spacing.cardPadding, bottom = spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                            Text(entry.section.title, Modifier.semantics { heading() }, style = typography.sectionTitle, color = colors.textTitle)
                            entry.section.note?.let { Text(it, style = typography.caption, color = colors.textSupporting) }
                        }
                        is ComparisonEntry.Metric -> Column(Modifier.fillMaxWidth()
                            .clip(if (entry.last) RoundedCornerShape(bottomStart = cardRadius, bottomEnd = cardRadius) else RoundedCornerShape(0.dp))
                            .background(colors.surface).padding(horizontal = spacing.cardPadding).padding(bottom = if (entry.last) spacing.xs else 0.dp)) {
                            MetricRowView(entry.row, state.columns, sideBySide, state.guide(entry.row.id), state.isExpanded(entry.row.id), state.isDeeper(entry.row.id),
                                last = entry.last, onInfo = { onInfo(entry.row) }, guidance = guidance)
                        }
                        ComparisonEntry.More -> StockButton(if (state.showMore) "Hide extra metrics" else "More metrics (${state.advancedCount})", onClick = guidance.onToggleMore,
                            variant = StockButtonVariant.SECONDARY, modifier = gap.fillMaxWidth().semantics { stateDescription = if (state.showMore) "Expanded" else "Collapsed" })
                        ComparisonEntry.Chart -> Box(gap) { PerformanceCard(state, onPeriod, onRetry) }
                        ComparisonEntry.History -> Box(gap) { HistoricalComparisonCard(state, history) }
                        ComparisonEntry.Tools -> Column(gap, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            StockSectionHeader("More tools")
                            StockCard(bordered = false, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
                                StockNavigationRow("Research checklist", onResearch, icon = StockIcons.Document,
                                    detail = "Guided questions and private notes. Free; StockSteps+ adds advanced research and reports.")
                                StockDivider()
                                StockNavigationRow("Ask StockSteps AI", onAskAi, icon = StockIcons.Lightbulb, badge = "StockSteps+",
                                    detail = "Answers use only the verified figures on this screen, with every number linked to its data.")
                                StockDivider()
                                StockNavigationRow("Discover Stocks", onDiscover, icon = StockIcons.Search, detail = "Find more companies to compare")
                            }
                        }
                        ComparisonEntry.Observations -> StockCard(gap, bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text("What the numbers show", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textTitle)
                            state.observations.forEach { o ->
                                Text("• ${o.text}", style = typography.small, color = colors.textBody)
                                o.caveat?.let { Text(it, Modifier.padding(start = spacing.sm), style = typography.caption, color = colors.textSupporting) }
                            }
                            Text("These describe reported figures. They aren't rankings or recommendations.", style = typography.caption, color = colors.textTertiary)
                        }
                        ComparisonEntry.Notes -> Column(gap, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                            Text("Sources and notes", Modifier.semantics { heading() }, style = typography.label, color = colors.textSecondary)
                            state.notes.forEach { Text(it, style = typography.caption, color = colors.textSecondary) }
                            state.asOf?.let { Text("Comparison prepared ${it.take(10)} ${it.drop(11).take(5)} UTC.", style = typography.caption, color = colors.textSecondary) }
                            Text("Education, not investment advice. Past results don't indicate future returns.", style = typography.caption, color = colors.textTertiary)
                        }
                        ComparisonEntry.Companies, ComparisonEntry.CompaniesEnd -> Unit
                    }
                }
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

private val cardRadius = org.example.stocksteps.theme.ThemeCorners.card.dp

/** The chosen companies: logo, ticker, two-line name and listing; ⋮ holds Replace and Remove (48 dp target at any text size). */
@Composable
private fun SelectionCard(state: ComparisonUiState, modifier: Modifier, onAdd: () -> Unit, onReplace: (String) -> Unit, onRemove: (String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(modifier, bordered = false, contentPadding = PaddingValues(start = spacing.cardPadding, end = spacing.xs, top = spacing.xs, bottom = spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget), verticalAlignment = Alignment.CenterVertically) {
            Text("Companies (${state.selected.size} of $MAX_COMPARED_COMPANIES)", Modifier.weight(1f).semantics { heading() }, style = typography.cardTitle, color = colors.textTitle)
            if (state.canAdd) TextButton(onClick = onAdd) { Text("Add a company", style = typography.label, color = colors.primaryText) }
        }
        state.selected.forEachIndexed { index, company ->
            val column = state.columns.firstOrNull { it.symbol.equals(company.symbol, ignoreCase = true) }
            var menu by remember(company.symbol) { mutableStateOf(false) }
            if (index > 0) StockDivider(startIndent = StockStepsTheme.dimensions.logoCompact + spacing.sm)
            Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.rowCompactMinHeight).padding(vertical = spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                StockTickerAvatar(symbol = company.symbol, logoUrl = column?.logoUrl, size = StockStepsTheme.dimensions.logoCompact)
                Column(Modifier.weight(1f).padding(start = spacing.sm).clearAndSetSemantics { contentDescription = ComparisonDisplay.selectionSpoken(company, column) },
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Text(company.symbol, style = typography.bodySemiBold, color = colors.textTitle)
                    Text(column?.name ?: company.name, style = typography.caption, color = colors.textSupporting, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    column?.listing?.ifBlank { null }?.let { Text(it, style = typography.tiny, color = colors.textMeta) }
                    if (column?.error != null) StockStatusBadge(ComparisonDisplay.PARTIAL, org.example.stocksteps.theme.StockBadgeKind.WARNING, size = StockBadgeSize.COMPACT)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(StockIcons.More, "Replace or remove ${column?.name ?: company.name}", tint = colors.iconSecondary) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Replace") }, onClick = { menu = false; onReplace(company.symbol) })
                        DropdownMenuItem(text = { Text("Remove", color = colors.negativeText) }, onClick = { menu = false; onRemove(company.symbol) })
                    }
                }
            }
        }
        Text(state.crowdedHint ?: "Two or three companies are easiest to read on a phone.", Modifier.padding(end = spacing.sm, top = spacing.xs),
            style = typography.caption, color = colors.textSupporting)
    }
}

@Composable
private fun ExamplesCard(state: ComparisonUiState, modifier: Modifier, onExample: (Int) -> Unit, onDiscover: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    StockCard(modifier, bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(if (state.selected.isEmpty()) ComparisonDisplay.FEWER_TITLE else "Add one more company to compare", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textTitle)
        Text(ComparisonDisplay.FEWER_MESSAGE, style = typography.small, color = colors.textBody)
        StockCard(bordered = false, containerColor = colors.surfaceSecondary, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
            state.examples.forEachIndexed { i, example ->
                if (i > 0) StockDivider()
                StockNavigationRow(example.title, { onExample(i) }, detail = example.description, actionLabel = "Compare ${example.title}")
            }
        }
        StockButton("Discover Stocks", onClick = onDiscover, modifier = Modifier.fillMaxWidth())
    }
}

/** Compact company columns kept visible while scrolling (side-by-side layout only); each opens the company. */
@Composable
private fun CompanyHeader(columns: List<CompanyColumn>, onOpen: (String) -> Unit, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    Column(modifier.fillMaxWidth().background(colors.appBackground)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.cardPadding, vertical = spacing.xs), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            columns.forEach { column ->
                Column(Modifier.weight(1f).heightIn(min = StockStepsTheme.dimensions.touchTarget)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Open ${column.name}") { onOpen(column.symbol) }
                    .clearAndSetSemantics {
                        contentDescription = listOfNotNull(column.name, column.symbol, column.price.takeIf { it != MISSING_PRICE }?.let { "price $it" },
                            if (column.error != null) ComparisonDisplay.PARTIAL.lowercase() else null).joinToString(", ")
                        role = androidx.compose.ui.semantics.Role.Button
                        onClick(label = "Open company details") { onOpen(column.symbol); true }
                    }, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        StockTickerAvatar(symbol = column.symbol, logoUrl = column.logoUrl, size = StockStepsTheme.dimensions.iconSmall)
                        Text(column.symbol, style = typography.label, color = colors.primaryText, maxLines = 1)
                    }
                    if (column.price != MISSING_PRICE) Text(column.price, style = typography.tiny, color = colors.textSupporting, maxLines = 1)
                    if (column.error != null) StockStatusBadge(ComparisonDisplay.PARTIAL, org.example.stocksteps.theme.StockBadgeKind.WARNING, size = StockBadgeSize.COMPACT)
                }
            }
        }
        StockDivider()
    }
}

/** Phase 2 actions (explanations, related metrics, "More metrics"); defaults keep previews and older call sites working. */
internal data class GuidanceActions(
    val onExplain: (String) -> Unit = {},
    val onDeeper: (String) -> Unit = {},
    val onRelated: (String) -> Unit = {},
    val onToggleMore: () -> Unit = {},
    val onFocusHandled: () -> Unit = {}
)

/** The screen's list items in order, so a metric row can be found (and scrolled to) by id. */
internal sealed class ComparisonEntry(val key: String) {
    data object Header : ComparisonEntry("header")
    data object Selection : ComparisonEntry("selection")
    data object Examples : ComparisonEntry("examples")
    data object Loading : ComparisonEntry("loading")
    data object Error : ComparisonEntry("error")
    data object Summary : ComparisonEntry("summary")
    data object Companies : ComparisonEntry("companies")
    class SectionTitle(val section: ComparisonSection) : ComparisonEntry("section-${section.title}")
    /** [last] = the section's final row (closes its card). */
    class Metric(val section: ComparisonSection, val row: ComparisonRow, val last: Boolean) : ComparisonEntry("row-${section.title}-${row.id}")
    data object More : ComparisonEntry("more")
    /** Ends the pinned company header after the metric sections. */
    data object CompaniesEnd : ComparisonEntry("companies-end")
    data object Chart : ComparisonEntry("chart")
    data object History : ComparisonEntry("history")
    /** Research checklist, AI assistant and Discover Stocks as one grouped list (Phase 5C; were two separate cards). */
    data object Tools : ComparisonEntry("tools")
    data object Observations : ComparisonEntry("observations")
    data object Notes : ComparisonEntry("notes")
}

internal fun comparisonEntries(state: ComparisonUiState): List<ComparisonEntry> = buildList {
    add(ComparisonEntry.Header); add(ComparisonEntry.Selection)
    if (state.needsMore) { add(ComparisonEntry.Examples); return@buildList }
    if (state.loading) add(ComparisonEntry.Loading)
    if (state.error != null) add(ComparisonEntry.Error)
    if (state.columns.isNotEmpty()) {
        if (state.insights.isNotEmpty()) add(ComparisonEntry.Summary)
        add(ComparisonEntry.Companies)
        fun section(s: ComparisonSection) { if (s.rows.isEmpty()) return; add(ComparisonEntry.SectionTitle(s)); s.rows.forEach { add(ComparisonEntry.Metric(s, it, it == s.rows.last())) } }
        state.sections.filterNot { it.advanced }.forEach(::section)
        if (state.advancedCount > 0) add(ComparisonEntry.More)
        if (state.showMore) state.sections.filter { it.advanced }.forEach(::section)
        add(ComparisonEntry.CompaniesEnd)
    }
    add(ComparisonEntry.Chart)
    if (state.historyEnabled) add(ComparisonEntry.History)
    if (state.historyEnabled && state.columns.isNotEmpty()) add(ComparisonEntry.Tools)
    if (state.observations.isNotEmpty()) add(ComparisonEntry.Observations)
    if (state.columns.isNotEmpty()) add(ComparisonEntry.Notes)
}

/**
 * One metric: name and period, Explain / ⓘ, then the companies' values — in equal columns ([sideBySide]) or one line per company with its
 * logo and ticker. A missing value reads "No data" with a real info control ("Market cap unavailable for TSLA. Explain why."), never "N/A ⓘ".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricRowView(row: ComparisonRow, columns: List<CompanyColumn>, sideBySide: Boolean, guide: MetricInterpretation?, expanded: Boolean, deeper: Boolean,
                          last: Boolean, onInfo: () -> Unit, guidance: GuidanceActions) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    var why by remember { mutableStateOf<Pair<String, String>?>(null) }
    fun valueColor(cell: MetricCell) = when { !cell.available -> colors.textTertiary; cell.tone == Tone.POSITIVE -> colors.positiveText; cell.tone == Tone.NEGATIVE -> colors.negativeText; else -> colors.textValue }
    Column(Modifier.fillMaxWidth().padding(vertical = spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.label, style = typography.bodySemiBold, color = colors.textTitle)
                row.period?.let { Text(it, style = typography.caption, color = colors.textMeta) }
            }
            if (guide != null) TextButton(onClick = { guidance.onExplain(row.id) }, modifier = Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)
                .semantics { contentDescription = (if (expanded) "Hide explanation of " else "Explain ") + row.label; stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
                Icon(StockIcons.Info, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
                Text(if (expanded) " Hide" else " Explain", style = typography.caption, color = colors.primaryText)
            }
            else IconButton(onClick = onInfo) { Icon(StockIcons.Info, contentDescription = "About ${row.label}", tint = colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall)) }
        }
        if (sideBySide) Row(Modifier.fillMaxWidth().padding(top = spacing.xxs).semantics(mergeDescendants = true) { contentDescription = ComparisonDisplay.rowSpoken(row, columns) },
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            columns.zip(row.cells).forEach { (column, cell) ->
                val explanation = cell.explanation
                Column(Modifier.weight(1f).heightIn(min = if (explanation != null) StockStepsTheme.dimensions.touchTarget else 0.dp)
                    .then(if (explanation != null) Modifier.clickable(onClickLabel = ComparisonDisplay.missingAction(row, column)) { why = "${row.label} · ${column.symbol}" to explanation } else Modifier)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(ComparisonDisplay.cellText(cell), style = typography.numberLabel, color = valueColor(cell))
                        if (explanation != null) Icon(StockIcons.Info, null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
                    }
                    cell.detail?.let { Text(it, style = typography.tiny, color = colors.textSupporting) }
                }
            }
        }
        else Column(Modifier.padding(top = spacing.xxs)) {
            columns.zip(row.cells).forEach { (column, cell) ->
                val explanation = cell.explanation
                // Compact lines; only a line with an info control needs the 48 dp target height.
                Row(Modifier.fillMaxWidth().heightIn(min = if (explanation != null) StockStepsTheme.dimensions.touchTarget else 0.dp).padding(vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Row(Modifier.weight(1f).clearAndSetSemantics { contentDescription = ComparisonDisplay.cellSpoken(column, row, cell) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        StockTickerAvatar(symbol = column.symbol, logoUrl = column.logoUrl, size = StockStepsTheme.dimensions.icon)
                        Text(column.symbol, style = typography.label, color = colors.textTitle)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(ComparisonDisplay.cellText(cell), style = typography.numberLabel, color = valueColor(cell), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            cell.detail?.let { Text(it, style = typography.tiny, color = colors.textSupporting, textAlign = androidx.compose.ui.text.style.TextAlign.End) }
                        }
                    }
                    if (explanation != null) IconButton(onClick = { why = "${row.label} · ${column.symbol}" to explanation }) {
                        Icon(StockIcons.Info, ComparisonDisplay.missingAction(row, column), tint = colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
                    }
                }
            }
        }
    }
    if (guide != null && expanded) GuidedMetricExplanation(guide, deeper, onToggleDeeper = { guidance.onDeeper(row.id) }, onRelated = guidance.onRelated,
        onClose = { guidance.onExplain(row.id) }, modifier = Modifier.padding(bottom = StockStepsTheme.spacing.xs))
    if (!last) StockDivider()
    why?.let { (title, text) -> AlertDialog(onDismissRequest = { why = null }, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(onClick = { why = null }) { Text("OK") } }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PerformanceCard(state: ComparisonUiState, onPeriod: (PerformancePeriod) -> Unit, onRetry: () -> Unit) {
    val typography = StockStepsTheme.typography
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    StockCard(bordered = false, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text("Share price change", Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textTitle)
        Text("Each line starts at 100 on the same day, so you compare percentage moves, not share prices. This is price only (no dividends) and says nothing about how the businesses performed.",
            style = typography.caption, color = colors.textSupporting)
        StockPillSelector(PerformancePeriod.entries, state.period, { it.label }, onPeriod)
        when {
            state.chartLoading -> StockLoadingState { StockSkeleton(Modifier.fillMaxWidth().height(spacing.xxxl * 4)) }
            // A chart failure stays in this card; the comparison above and below is unaffected.
            state.chartError != null -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                StockErrorState(state.chartError.orEmpty(), onRetry, title = ComparisonDisplay.CHART_ERROR_TITLE, icon = StockIcons.Warning)
                Text(ComparisonDisplay.CHART_ERROR_NOTE, style = typography.caption, color = colors.textSupporting)
            }
            else -> state.chart?.let { chart ->
                val drawable = chart.series.filter { it.error == null }
                if (drawable.isEmpty() || chart.dates.size < 2) Text("Price history isn't available for this period.", style = typography.small, color = colors.textBody)
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
                    // Per-company change: wraps as whole "AAPL +22.4%" pairs, never split.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        drawable.forEach { s ->
                            val cell = s.change?.let(MetricFormatter::change)
                            Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                                Text(s.symbol, style = typography.label, color = colors.textTitle)
                                Text(cell?.text ?: ComparisonDisplay.NO_DATA, style = typography.numberLabel,
                                    color = when (cell?.tone) { Tone.POSITIVE -> colors.positiveText; Tone.NEGATIVE -> colors.negativeText; null -> colors.textTertiary; else -> colors.textValue })
                            }
                        }
                    }
                    drawable.mapNotNull { s -> s.note?.let { "${s.symbol}: $it" } }.forEach { Text(it, style = typography.caption, color = colors.textSupporting) }
                }
                chart.series.filter { it.error != null }.forEach { Text("${it.symbol}: ${it.error}", style = typography.caption, color = colors.cautionText) }
                chart.notes.forEach { Text(it, style = typography.caption, color = colors.textSupporting) }
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

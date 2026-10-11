@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package org.example.stocksteps.presentation.screener

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.screener.*

private fun categoryIcon(id: String): ImageVector = when (ScreenerDisplay.Category.valueOf(id)) {
    ScreenerDisplay.Category.COMPANY -> StockIcons.Bank
    ScreenerDisplay.Category.VALUATION -> StockIcons.Tag
    ScreenerDisplay.Category.GROWTH -> StockIcons.TrendingUp
    ScreenerDisplay.Category.PROFITABILITY -> StockIcons.PieChart
    ScreenerDisplay.Category.HEALTH -> StockIcons.Shield
    ScreenerDisplay.Category.SHAREHOLDER -> StockIcons.Coin
}

/**
 * Filter editor (Phase 5B): categories → filters in a category → one filter (explanation, "Any value / At least / At most / Between" and
 * typed values). Every edit changes only the draft; nothing is searched until "Show results", which stays pinned above the keyboard. The
 * operators are a display form of the same inclusive min/max ranges. Presets stay reachable at the top level.
 */
@Composable
internal fun FilterEditorSheet(state: ScreenerUiState, actions: ScreenerActions, onDismiss: () -> Unit, onInfo: (MetricInfo) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var metricId by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = remember(state.metrics, state.choices, state.draft) { ScreenerDisplay.categories(state.metrics, state.choices, state.draft) }
    val category = categories.firstOrNull { it.id == categoryId }
    val metric = category?.metrics?.firstOrNull { it.id == metricId }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                if (category != null) IconButton(onClick = { if (metric != null) metricId = null else categoryId = null }) {
                    Icon(StockIcons.ArrowForward, if (metric != null) "Back to ${category.title}" else "Back to filters",
                        tint = colors.primaryText, modifier = Modifier.graphicsLayer { scaleX = -1f }) // no back icon in StockIcons: the forward arrow, mirrored
                }
                Text(when { metric != null -> category.title; category != null -> category.title; else -> "Filters" },
                    Modifier.weight(1f).padding(start = if (category == null) spacing.md else 0.dp).semantics { heading() },
                    style = if (metric != null) typography.label else typography.sectionTitle, color = if (metric != null) colors.primaryText else colors.textTitle)
                TextButton(onClick = actions.onReset, enabled = state.draft.activeFilterCount > 0 || state.applied?.activeFilterCount?.let { it > 0 } == true) { Text("Reset all") }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = spacing.screen).padding(bottom = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                when {
                    metric != null -> MetricEditor(metric, state.draft.ranges.firstOrNull { it.metric == metric.id }, actions.onRange) { onInfo(MetricFormatter.education(metric.id, metric)) }
                    category != null -> CategoryPage(category, state, actions) { metricId = it }
                    else -> {
                        Text("Choose what matters to you. Nothing is searched until you tap Show results.", style = typography.small, color = colors.textSupporting)
                        StockCard(bordered = false, containerColor = colors.surfaceSecondary, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
                            categories.forEachIndexed { index, c ->
                                if (index > 0) StockDivider()
                                StockNavigationRow(c.title, { categoryId = c.id }, icon = categoryIcon(c.id), detail = c.detail,
                                    secondaryDetail = if (c.activeCount > 0) "${ScreenerDisplay.filterCount(c.activeCount)} set" else null, actionLabel = "Open ${c.title}")
                            }
                        }
                        StockSectionHeader("Or start from a preset")
                        StockCard(bordered = false, containerColor = colors.surfaceSecondary, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
                            state.presets.forEachIndexed { index, preset ->
                                if (index > 0) StockDivider()
                                StockNavigationRow(preset.name, { actions.onPreset(preset.id); onDismiss() }, detail = ScreenerDisplay.presetSummary(preset), actionLabel = "Show ${preset.name}")
                            }
                        }
                    }
                }
            }
            val count = state.draft.activeFilterCount
            StockButton(if (count > 0) "Show results (${ScreenerDisplay.filterCount(count)})" else "Show all companies", { actions.onApply(); onDismiss() },
                Modifier.fillMaxWidth().padding(horizontal = spacing.screen).padding(top = spacing.xs, bottom = spacing.lg))
        }
    }
}

@Composable
private fun CategoryPage(category: FilterCategoryView, state: ScreenerUiState, actions: ScreenerActions, onMetric: (String) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    category.choices.forEach { field ->
        val selected = state.draft.choices.firstOrNull { it.field == field }?.values.orEmpty()
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(field.label, Modifier.semantics { heading() }, style = StockStepsTheme.typography.label, color = colors.textTitle)
            // Values wrap (no hidden horizontal scroll); each chip states selected / not selected.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                state.choices[field].orEmpty().forEach { value ->
                    val on = value in selected
                    FilterChip(on, onClick = { actions.onChoice(field, if (on) selected - value else selected + value) }, label = { Text(value) },
                        modifier = Modifier.semantics { stateDescription = if (on) "Selected" else "Not selected" })
                }
            }
        }
    }
    if (category.metrics.isNotEmpty()) StockCard(bordered = false, containerColor = colors.surfaceSecondary, contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xxs)) {
        category.metrics.forEachIndexed { index, definition ->
            if (index > 0) StockDivider()
            val range = state.draft.ranges.firstOrNull { it.metric == definition.id }
            StockNavigationRow(definition.label, { onMetric(definition.id) }, detail = range?.let(ScreenerDisplay::rangeValue) ?: ScreenerDisplay.Operator.ANY.label,
                detailColor = if (range != null) colors.primaryText else colors.textSupporting, actionLabel = "Edit ${definition.label}")
        }
    }
}

/** One filter: what it means, the operator and its values in display units. Pushes the draft on every change (as before Phase 5B). */
@Composable
private fun MetricEditor(definition: MetricDefinition, current: RangeFilter?, onChange: (String, Double?, Double?) -> Unit, onInfo: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    var operator by remember(definition.id) { mutableStateOf(ScreenerDisplay.operatorOf(current)) }
    var first by remember(definition.id) { mutableStateOf(ScreenerDisplay.inputText(ScreenerDisplay.firstValue(current), definition)) }
    var second by remember(definition.id) { mutableStateOf(ScreenerDisplay.inputText(ScreenerDisplay.secondValue(current), definition)) }
    fun push() {
        val bounds = ScreenerDisplay.bounds(operator, ScreenerDisplay.parseInput(first, definition), ScreenerDisplay.parseInput(second, definition))
        onChange(definition.id, bounds.min, bounds.max)
    }
    val unit = ScreenerDisplay.inputUnit(definition)
    Row(verticalAlignment = Alignment.Top) {
        Text(definition.label, Modifier.weight(1f).semantics { heading() }, style = typography.sectionTitle, color = colors.textTitle)
        IconButton(onClick = onInfo) { Icon(StockIcons.Info, "About ${definition.label}", tint = colors.iconSecondary) }
    }
    MetricFormatter.education(definition.id, definition).meaning.takeIf { it.isNotBlank() }?.let { Text(it, style = typography.small, color = colors.textSupporting) }
    Column(Modifier.selectableGroup()) {
        ScreenerDisplay.Operator.entries.forEach { option ->
            Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
                .selectable(option == operator, role = Role.RadioButton) { operator = option; push() },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                RadioButton(selected = option == operator, onClick = null)
                Text(option.label, style = typography.body, color = colors.textBody)
            }
        }
    }
    val suffix = if (unit.isEmpty()) "" else " ($unit)"
    when (operator) {
        ScreenerDisplay.Operator.ANY -> Text("No limit: companies aren't filtered on ${definition.label}.", style = typography.small, color = colors.textSupporting)
        ScreenerDisplay.Operator.BETWEEN -> Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StockTextField(first, { first = it; push() }, "From$suffix", Modifier.weight(1f), placeholder = ScreenerDisplay.inputText(definition.suggestedMin, definition), keyboardType = KeyboardType.Decimal)
            StockTextField(second, { second = it; push() }, "To$suffix", Modifier.weight(1f), placeholder = ScreenerDisplay.inputText(definition.suggestedMax, definition), keyboardType = KeyboardType.Decimal)
        }
        else -> StockTextField(first, { first = it; push() }, "${operator.label}$suffix", Modifier.fillMaxWidth(),
            placeholder = ScreenerDisplay.inputText(if (operator == ScreenerDisplay.Operator.AT_LEAST) definition.suggestedMin else definition.suggestedMax, definition),
            keyboardType = KeyboardType.Decimal)
    }
    if (operator == ScreenerDisplay.Operator.BETWEEN) ScreenerDisplay.boundsError(ScreenerDisplay.parseInput(first, definition), ScreenerDisplay.parseInput(second, definition))?.let {
        Text(it, style = typography.caption, color = colors.negativeText)
    }
    Text("Includes the values you type. Period: ${definition.period}. ${definition.missing}", style = typography.caption, color = colors.textMeta)
    definition.currencyNote?.let { Text(it, style = typography.caption, color = colors.textMeta) }
}

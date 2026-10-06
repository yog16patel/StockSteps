package org.example.stocksteps.presentation.companydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import org.example.stocksteps.theme.FinancialsTokens
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.example.stocksteps.companydetail.*
import org.example.stocksteps.theme.ThemeCorners
import org.example.stocksteps.theme.ThemeSpacing

@Composable
internal fun CompanyFinancialsSection(
    section: FinancialSectionState,
    onRetry: () -> Unit,
    onInfo: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.large.dp)) {
        Text(section.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        when (section.status) {
            SectionStatus.LOADING -> FinancialsGrid(listOf("first", "second")) { FinancialSkeleton() }
            SectionStatus.EMPTY, SectionStatus.ERROR -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(ThemeSpacing.large.dp),
                    verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)
                ) {
                    Text(section.message.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    section.explanation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = onRetry) { Text("Try again", color = financialInfoColor()) }
                }
            }
            SectionStatus.SUCCESS -> {
                if (section.cashRelationship) {
                    Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)) {
                        section.metrics.filter { it.id != "fcfMargin" }.forEachIndexed { index, tile ->
                            if (index > 0) Text(if (index == 1) "↓ Less capital spending" else "↓ Leaves free cash flow", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FinancialValueCard(tile) { onInfo(tile.id) }
                        }
                        section.metrics.filter { it.id == "fcfMargin" }.forEach { tile -> FinancialValueCard(tile) { onInfo(tile.id) } }
                    }
                } else {
                    FinancialsGrid(section.metrics) { tile -> FinancialValueCard(tile) { onInfo(tile.id) } }
                }
                section.insights.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
                section.explanation?.let {
                    Text("What this means", style = MaterialTheme.typography.labelLarge, color = financialInfoColor())
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (section.secondary.isNotEmpty()) {
                    var expanded by remember(section.title) { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (section.title == "Growth") "${if (expanded) "Hide" else "View"} growth history" else "${if (expanded) "Hide" else "View"} more metrics", color = financialInfoColor())
                    }
                    if (expanded) FinancialsGrid(section.secondary) { tile -> FinancialValueCard(tile) { onInfo(tile.id) } }
                }
            }
        }
    }
}

@Composable
private fun <T> FinancialsGrid(values: List<T>, content: @Composable (T) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= FinancialsTokens.twoColumnMinWidth.dp && LocalDensity.current.fontScale <= FinancialsTokens.largeTextScale) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)) {
            values.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)) {
                    row.forEach { value -> Box(Modifier.weight(1f)) { content(value) } }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FinancialValueCard(tile: FinancialTile, onInfo: () -> Unit) {
    Card(
        onClick = onInfo,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ThemeCorners.medium.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ThemeSpacing.large.dp),
            verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)
        ) {
            Text(tile.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tile.value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            tile.movement?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = when (tile.direction) {
                    PriceDirection.UP -> MaterialTheme.colorScheme.primary
                    PriceDirection.DOWN -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                })
            }
            tile.reportingPeriod?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Learn what this means", style = MaterialTheme.typography.labelSmall, color = financialInfoColor())
        }
    }
}

@Composable
private fun FinancialSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Loading financial values" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(ThemeSpacing.large.dp), verticalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)) {
            listOf(FinancialsTokens.skeletonLabelWidth to FinancialsTokens.skeletonLineHeight,
                FinancialsTokens.skeletonValueWidth to FinancialsTokens.skeletonValueHeight,
                FinancialsTokens.skeletonContextWidth to FinancialsTokens.skeletonLineHeight).forEach { (width, height) ->
                Spacer(Modifier.size(width.dp, height.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(ThemeCorners.small.dp)))
            }
        }
    }
}

@Composable
private fun financialInfoColor(): Color = if (MaterialTheme.colorScheme.background.red < .3f) Color(0xFF91BBFF) else Color(0xFF2456AA)

package org.example.stocksteps.presentation.companydetail

import coil3.compose.AsyncImage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.example.stocksteps.companydetail.*
import org.example.stocksteps.theme.*

@Composable
internal fun CompanyHeader(detail: CompanyDetailUiState, loading: Boolean, error: String?, saved: Boolean, enabled: Boolean, onToggle: () -> Unit, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)) {
        detail.logoUrl?.takeIf { it.startsWith("https://") }?.let { logo ->
            AsyncImage(model = logo, contentDescription = "${detail.name} logo", modifier = Modifier.size(ThemeSpacing.extraLarge.dp * 2))
        }
        Text(detail.name, style = MaterialTheme.typography.headlineMedium)
        Text(listOfNotNull(detail.symbol, detail.listing.takeIf { it.isNotBlank() }, detail.sector).joinToString(" · "))
        SectionFeedback(loading, error, "Retry quote", onRetry)
        Text(detail.price, style = MaterialTheme.typography.headlineLarge)
        val palette = if (isSystemInDarkTheme()) ThemeColors.dark else ThemeColors.light
        val color = when (detail.direction) {
            PriceDirection.UP -> Color(0xFF000000L or palette.positive.toLong())
            PriceDirection.DOWN -> Color(0xFF000000L or palette.negative.toLong())
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(detail.priceChange, color = color)
        Button(onClick = onToggle, enabled = enabled) { Text(if (saved) "Remove from WatchList" else "Add to WatchList") }
    }
}

@Composable
internal fun DetailCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ThemeSpacing.large.dp), verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
internal fun SectionFeedback(loading: Boolean, error: String?, retryLabel: String, onRetry: () -> Unit) {
    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(retryLabel) }
    }
}

@Composable
internal fun CompanySnapshotCard(snapshot: CompanySnapshot, onInfo: () -> Unit) {
    DetailCard("Company snapshot") {
        Text("Financial evidence")
        snapshot.scores.forEach { score ->
            Text("${score.category}: ${score.context ?: "Not assessed"}${score.score?.let { " · ${CompanyDetailPresenter.number(it)} / 10" }.orEmpty()}")
        }
        Text("These are reported facts and calculations, not an investment score.")
        TextButton(onClick = onInfo) { Text("How we calculate this →") }
    }
}

@Composable
internal fun FinancialMetricRow(metric: FinancialMetric, onInfo: () -> Unit) {
    TextButton(onClick = onInfo, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("${metric.label}: ${CompanyDetailPresenter.metricValue(metric)}", style = MaterialTheme.typography.titleMedium)
            Text(metric.context ?: "Tap to learn what this means", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MetricInfoBottomSheet(id: String, metrics: List<FinancialMetric>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(ThemeSpacing.extraLarge.dp), verticalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)) {
            if (id == "snapshot") {
                Text("How we calculate this", style = MaterialTheme.typography.headlineMedium)
                Text("The snapshot displays verified financial facts. Growth and historical comparisons are calculated from comparable reporting periods. Missing or unsuitable values remain unavailable. No investment scores or buy/sell recommendations are generated.")
            } else {
                val metric = metrics.firstOrNull { it.id == id } ?: MetricEducation.metric(id)
                Text(metric.label, style = MaterialTheme.typography.headlineMedium)
                Text(metric.explanation)
                Text("Company: ${CompanyDetailPresenter.metricValue(metric)}")
                Text("Industry: ${CompanyDetailPresenter.number(metric.industryAverage)}")
                Text("${metric.historicalLabel}: ${CompanyDetailPresenter.number(metric.historicalAverage)}")
                CompanyDetailPresenter.historicalContext(metric)?.let { Text(it) }
            }
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

@Composable
internal fun StockPriceChart(state: FinancialChartState, onAction: (CompanyDetailAction) -> Unit) {
    DetailCard("Stock price history") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)) {
            ChartPeriod.entries.forEach { period ->
                FilterChip(selected = state.period == period, enabled = state.status == SectionStatus.SUCCESS, onClick = { onAction(CompanyDetailAction.SelectChartPeriod(period)) }, label = { Text(period.label) })
            }
        }
        when (state.status) {
            SectionStatus.LOADING -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            SectionStatus.EMPTY, SectionStatus.ERROR -> Text(state.message)
            SectionStatus.SUCCESS -> FinancialTrendChart(state.points)
        }
    }
}

@Composable
internal fun FinancialTrendChart(points: List<ChartPoint>) {
    val valid = points.filter { it.value.isFinite() }
    if (valid.size < 2) { Text("Not enough historical data to draw a trend."); return }
    val min = valid.minOf { it.value }
    val max = valid.maxOf { it.value }
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(ThemeSpacing.extraLarge.dp * 6).semantics {
        contentDescription = "History from ${valid.first().label}: ${valid.first().value} to ${valid.last().label}: ${valid.last().value}"
    }) {
        valid.zipWithNext().forEachIndexed { index, pair ->
            fun point(i: Int, value: Double) = Offset(size.width * i / (valid.size - 1), (size.height * (1 - (value - min) / range)).toFloat())
            drawLine(color, point(index, pair.first.value), point(index + 1, pair.second.value), strokeWidth = 2.dp.toPx())
        }
    }
    Text("${valid.first().label} → ${valid.last().label}", style = MaterialTheme.typography.labelMedium)
}

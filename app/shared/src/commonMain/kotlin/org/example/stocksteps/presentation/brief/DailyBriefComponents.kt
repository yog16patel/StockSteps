package org.example.stocksteps.presentation.brief

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import org.example.stocksteps.brief.BriefEarnings
import org.example.stocksteps.brief.BriefFormat
import org.example.stocksteps.brief.BriefIndex
import org.example.stocksteps.brief.DailyBrief
import org.example.stocksteps.brief.DailyBriefDisplay
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.theme.StockBadgeKind
import org.example.stocksteps.theme.StockIndexTilesLayout

/** A brief index as the shared [StockIndexRow] (Home "Markets today" and the reader at large text, Phase 5D.5.1). */
@Composable
internal fun BriefIndexRow(index: BriefIndex, modifier: Modifier = Modifier) = StockIndexRow(
    name = index.displayName,
    stateLabel = index.stateLabel,
    value = BriefFormat.value(index),
    change = DailyBriefDisplay.indexChange(index),
    direction = DailyBriefDisplay.indexDirection(index),
    accessibilityLabel = BriefFormat.accessibility(index),
    modifier = modifier,
    caution = DailyBriefDisplay.indexCaution(index),
    note = index.proxyNote,
    changeUnavailable = DailyBriefDisplay.CHANGE_UNAVAILABLE
)

/**
 * The reader's indices (5D.5.1b): compact tiles while every unbreakable piece fits a tile (`StockIndexTilesLayout`, measured in the fonts used),
 * otherwise full-width [BriefIndexRow]s. The shared state label appears once below the tiles; a tile whose state differs keeps its own.
 */
@Composable
internal fun BriefIndices(brief: DailyBrief, modifier: Modifier = Modifier) {
    val spacing = StockStepsTheme.spacing
    val typography = StockStepsTheme.typography
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val indices = brief.marketSnapshot
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth.value
        val accessibilityText = density.fontScale >= StockStepsTheme.dimensions.largeFontScale
        val arrow = StockStepsTheme.dimensions.iconSmall.value + spacing.xxs.value
        val tiles = remember(brief, width, density.fontScale) {
            val widest = with(density) {
                val names = indices.flatMap { it.displayName.split(' ') }.maxOfOrNull { measurer.measure(it, typography.label).size.width.toDp().value } ?: 0f
                val percents = indices.mapNotNull(DailyBriefDisplay::indexPercent).maxOfOrNull { measurer.measure(it, typography.numberMedium).size.width.toDp().value + arrow } ?: 0f
                val others = DailyBriefDisplay.tileTokens(brief).maxOfOrNull { measurer.measure(it, typography.numberLabelStrong).size.width.toDp().value } ?: 0f
                maxOf(names, percents, others)
            }
            StockIndexTilesLayout.tiles(widest, indices.size, width, spacing.sm.value, spacing.sm.value, accessibilityText)
        }
        val shared = DailyBriefDisplay.sharedStateLine(brief)
        if (tiles) Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                indices.forEach { i ->
                    StockIndexTile(i.displayName, DailyBriefDisplay.indexPercent(i), BriefFormat.value(i), DailyBriefDisplay.indexPoints(i),
                        DailyBriefDisplay.indexDirection(i), BriefFormat.accessibility(i), Modifier.weight(1f).fillMaxHeight(),
                        stateLabel = i.stateLabel.takeIf { shared == null }, caution = DailyBriefDisplay.indexCaution(i),
                        changeUnavailable = DailyBriefDisplay.CHANGE_UNAVAILABLE)
                }
            }
            shared?.let { Text(it, style = typography.caption, color = StockStepsTheme.colors.textMeta) }
        } else Column {
            indices.forEachIndexed { n, index ->
                if (n > 0) StockDivider()
                BriefIndexRow(index)
            }
        }
    }
}

/** One upcoming report (5D.5.1b): company and ticker, "Expected Oct 30 · After market close" with the server's date status as a badge, and the
 *  estimate/reason line. Confirmed and Estimated read differently in words, not colour alone. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BriefEarningsRow(e: BriefEarnings, onClick: () -> Unit) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.rowCompactMinHeight)
        .clickable(role = Role.Button, onClickLabel = DailyBriefDisplay.earningsAction(e), onClick = onClick)
        .clearAndSetSemantics { contentDescription = DailyBriefDisplay.earningsSpoken(e) }
        .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(DailyBriefDisplay.earningsTitle(e), style = typography.bodySemiBold, color = colors.textTitle)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                itemVerticalAlignment = Alignment.CenterVertically) {
                Text(DailyBriefDisplay.earningsWhen(e), style = typography.small, color = colors.textBody)
                StockStatusBadge(DailyBriefDisplay.earningsStatus(e), if (DailyBriefDisplay.earningsConfirmed(e)) StockBadgeKind.INFO else StockBadgeKind.NEUTRAL,
                    size = StockBadgeSize.COMPACT)
            }
            Text(DailyBriefDisplay.earningsNote(e), style = typography.caption, color = colors.textMeta)
        }
        Icon(StockIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(StockStepsTheme.dimensions.icon))
    }
}

/** Neutral icon per watchlist highlight kind (`DailyBriefDisplay.highlightIcon`); the sentence carries the direction in words. */
internal fun briefHighlightIcon(key: String) = when (key) {
    "chart" -> StockIcons.BarChart
    "news" -> StockIcons.News
    "calendar" -> StockIcons.Calendar
    else -> StockIcons.Document
}

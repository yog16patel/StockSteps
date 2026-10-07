package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.news.NewsUiModel
import org.example.stocksteps.resources.Res
import org.example.stocksteps.resources.news_ai_label
import org.example.stocksteps.resources.news_open_original
import org.example.stocksteps.resources.news_why_it_matters
import org.jetbrains.compose.resources.stringResource

/**
 * News item for Home, company news and the News screen. `compact` is a borderless
 * row (thumbnail, two-line headline, source/time) for previews; the full card adds
 * the summary and "Why it matters".
 */
@Composable
internal fun StockNewsCard(
    model: NewsUiModel,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    compact: Boolean = true
) {
    if (compact) CompactNewsRow(model, onClick, modifier) else FullNewsCard(model, onClick, modifier)
}

@Composable
private fun CompactNewsRow(model: NewsUiModel, onClick: (() -> Unit)?, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val openLabel = stringResource(Res.string.news_open_original)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        // Photography is cropped; a missing or failed image leaves a neutral tile, never a broken icon.
        val thumbnail = Modifier
            .size(StockStepsTheme.dimensions.newsThumbnailCompact)
            .clip(StockStepsTheme.shapes.chip)
            .background(colors.surfaceSecondary)
        if (model.imageUrl != null) {
            AsyncImage(model = model.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = thumbnail)
        } else {
            Box(thumbnail)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                text = model.headline,
                style = typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = COMPACT_HEADLINE_LINES,
                overflow = TextOverflow.Ellipsis
            )
            NewsMeta(model, compact = true)
        }
    }
}

@Composable
private fun FullNewsCard(model: NewsUiModel, onClick: (() -> Unit)?, modifier: Modifier) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    StockCard(
        modifier = modifier,
        onClick = onClick,
        onClickLabel = stringResource(Res.string.news_open_original)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                NewsMeta(model, compact = false)
                Text(text = model.headline, style = typography.bodyMedium, color = colors.textPrimary)
                model.summary?.let { Text(text = it, style = typography.small, color = colors.textBody) }
                model.whyItMatters?.let {
                    Text(stringResource(Res.string.news_why_it_matters), style = typography.label, color = colors.textPrimary)
                    Text(it, style = typography.small, color = colors.textBody)
                }
            }
            if (model.imageUrl != null) {
                AsyncImage(
                    model = model.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(StockStepsTheme.dimensions.newsThumbnail)
                        .clip(StockStepsTheme.shapes.chip)
                        .background(colors.surfaceSecondary)
                )
            }
        }
    }
}

/** Source · time, plus a transparent "Simplified by AI" label when the text was rewritten. */
@Composable
private fun NewsMeta(model: NewsUiModel, compact: Boolean) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val meta = listOfNotNull(model.source, model.publishedLabel).joinToString(" · ")
    if (meta.isEmpty() && !model.aiSimplified) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                modifier = Modifier.weight(1f, fill = false),
                style = typography.caption,
                color = if (compact) colors.textTertiary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (model.aiSimplified) {
            Text(
                text = stringResource(Res.string.news_ai_label),
                modifier = Modifier
                    .clip(StockStepsTheme.shapes.pill)
                    .background(colors.primaryContainer)
                    .padding(horizontal = spacing.sm, vertical = spacing.xxs),
                style = typography.tiny,
                color = colors.primaryText,
                maxLines = 1
            )
        }
    }
}

/** Placeholder shaped like a compact [StockNewsCard] row. */
@Composable
internal fun StockNewsCardSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = StockStepsTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.md)
    ) {
        StockSkeleton(Modifier.size(StockStepsTheme.dimensions.newsThumbnailCompact))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
            StockSkeleton(Modifier.fillMaxWidth(SKELETON_HEADLINE))
            StockSkeleton(Modifier.fillMaxWidth(SKELETON_META))
        }
    }
}

private const val COMPACT_HEADLINE_LINES = 2
private const val SKELETON_META = 0.35f
private const val SKELETON_HEADLINE = 0.9f

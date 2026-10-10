package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.theme.StockSemanticStyles

/** INFO: blue explanations ("Beginner Insight", "Why it moved"). EDUCATION: warm learning entry points. */
internal enum class InsightTone { INFO, EDUCATION }

/**
 * Educational/informational card. Never signals market direction, so it never uses
 * positive/negative colors. `leading` is an optional decorative icon/illustration.
 */
@Composable
internal fun StockInsightCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: InsightTone = InsightTone.INFO,
    eyebrow: String? = null,
    actionText: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null
) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val container = if (tone == InsightTone.EDUCATION) colors.educationContainer else colors.primaryContainer
    val accent = if (tone == InsightTone.EDUCATION) colors.educationAccent else colors.primaryText
    // Text on the education container uses cautionText: educationAccent is 2.75:1 there in light mode (icons keep the accent).
    val accentText = if (tone == InsightTone.EDUCATION) colors.rgb(StockSemanticStyles.educationText(colors.palette)) else colors.primaryText
    StockCard(
        modifier = modifier,
        onClick = onClick,
        onClickLabel = actionText,
        containerColor = container,
        borderColor = container,
        contentPadding = PaddingValues(if (leading != null) spacing.md else spacing.educationalCardPadding)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            if (leading != null) {
                Box(
                    modifier = Modifier
                        .size(StockStepsTheme.dimensions.educationIcon)
                        .clip(StockStepsTheme.shapes.card)
                        .background(colors.surface.copy(alpha = ICON_WELL_ALPHA))
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides accent, content = leading)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                if (eyebrow != null) Text(eyebrow, style = typography.label, color = accentText)
                Text(title, style = if (leading != null) typography.bodySemiBold else typography.cardTitle, color = colors.textTitle)
                Text(body, style = if (leading != null) typography.caption else typography.small, color = colors.textBody)
                if (actionText != null && onClick != null && leading == null) {
                    Text("$actionText →", style = typography.bodyMedium, color = colors.primaryText)
                }
            }
            if (onClick != null && leading != null) {
                // Compact layout: the whole card is the action; the arrow is a visual cue only.
                Text("→", style = typography.cardTitle, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}

private const val ICON_WELL_ALPHA = 0.7f

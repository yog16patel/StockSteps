package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import org.example.stocksteps.theme.StockPillLayout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/** Compact single-choice pill filter (movers, periods, Annual/Quarterly): 34dp visual, 48dp touch target. Place in a `selectableGroup()`. */
@Composable
internal fun StockChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StockStepsTheme.colors
    val shape = StockStepsTheme.shapes.pill
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = StockStepsTheme.dimensions.chipHeight)
            .clip(shape)
            // Selected = the accessible action blue with white text (4.86:1), like primary buttons (Phase 2; was primaryDark).
            .background(if (selected) colors.primaryAction else colors.surfaceSecondary)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = StockStepsTheme.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = if (selected) StockStepsTheme.typography.label.copy(fontWeight = FontWeight.SemiBold) else StockStepsTheme.typography.label,
            color = if (selected) colors.onPrimary else colors.textSecondary,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Pill selector for short options (chart ranges 1D…ALL): the selected option is a filled pill, the rest are plain labels; 48dp
 * touch targets; announced as tabs. Options share the width equally while every label fits (`StockPillLayout`); otherwise — long
 * labels, many options or large font scale — they scroll horizontally at their natural width instead of collapsing to "…".
 */
@Composable
internal fun <T> StockPillSelector(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val style = StockStepsTheme.typography.label
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widths = options.map { with(density) { measurer.measure(label(it), style.copy(fontWeight = FontWeight.SemiBold)).size.width.toDp().value } }
        val equal = StockPillLayout.fitsEqualWidth(widths, maxWidth.value, spacing.md.value)
        val pill: @Composable (T, Modifier) -> Unit = { option, itemModifier ->
            val isSelected = option == selected
            Box(
                modifier = itemModifier
                    .minimumInteractiveComponentSize()
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    modifier = Modifier
                        .clip(StockStepsTheme.shapes.pill)
                        .background(if (isSelected) colors.primaryAction else Color.Transparent)
                        .padding(horizontal = spacing.md, vertical = spacing.xs),
                    style = if (isSelected) style.copy(fontWeight = FontWeight.SemiBold) else style,
                    color = if (isSelected) colors.onPrimary else colors.textSecondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        if (equal) {
            Row(Modifier.fillMaxWidth().selectableGroup()) { options.forEach { pill(it, Modifier.weight(1f)) } }
        } else {
            // Phase 4C.3: a cut-off pill at the edge looked like the last option ("ALL" was hidden at 360dp). The row now fades out on the side that
            // has more options (an alpha mask, so it works on any background) and scrolls just enough to keep the selected option visible —
            // horizontally only, so a range change never moves the page.
            val scroll = rememberScrollState()
            val bounds = remember(options) { mutableStateMapOf<T, IntRange>() }
            val viewport = with(density) { maxWidth.roundToPx() }
            // About one pill wide, so the last visible option visibly fades (a narrower fade can fall on empty space between pills).
            val edge = with(density) { StockStepsTheme.dimensions.touchTarget.toPx() }
            LaunchedEffect(selected, bounds[selected], viewport) {
                val span = bounds[selected] ?: return@LaunchedEffect
                val target = when {
                    span.first < scroll.value + edge -> span.first - edge.toInt()
                    span.last > scroll.value + viewport - edge -> span.last - viewport + edge.toInt()
                    else -> return@LaunchedEffect
                }
                scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue))
            }
            Row(
                Modifier.fillMaxWidth()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        // Fade only while more than half a pill is hidden: a few pixels of padding past the edge is not "more options".
                        val start = if (scroll.value > edge / 2) Color.Transparent else Color.Black
                        val end = if (scroll.maxValue - scroll.value > edge / 2) Color.Transparent else Color.Black
                        val fade = (edge / size.width).coerceIn(0f, 0.5f)
                        drawRect(Brush.horizontalGradient(0f to start, fade to Color.Black, 1f - fade to Color.Black, 1f to end), blendMode = BlendMode.DstIn)
                    }
                    .horizontalScroll(scroll)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs)
            ) {
                options.forEach { option ->
                    pill(option, Modifier.onGloballyPositioned { bounds[option] = it.positionInParent().x.toInt()..(it.positionInParent().x.toInt() + it.size.width) })
                }
            }
        }
    }
}

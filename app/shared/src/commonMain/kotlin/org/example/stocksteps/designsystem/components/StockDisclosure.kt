package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.format.ReadableText

// Progressive disclosure (promoted from Earnings 5D.3/5D.4.1 in Phase 5D.5.1, decision D-6). SwiftUI mirrors them in StockDisclosure.swift.
// Nothing inside is removed or reworded, only folded away from the main reading flow.

/**
 * A collapsed secondary section (methodology, sources, notes, explainers): a 48 dp row that announces its state, the content below when open
 * (the Comparison 5C.1 "Sources & methodology" pattern).
 */
@Composable
internal fun StockDisclosure(title: String, modifier: Modifier = Modifier, icon: ImageVector = StockIcons.Info, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val colors = StockStepsTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide $title" else "Show $title") { open = !open }
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
            Icon(icon, null, Modifier.size(StockStepsTheme.dimensions.iconSmall), tint = colors.primaryText)
            Text(title, Modifier.weight(1f), style = StockStepsTheme.typography.label, color = colors.primaryText)
            Icon(StockIcons.ChevronRight, null, Modifier.rotate(if (open) 90f else 0f), tint = colors.iconSecondary)
        }
        if (open) content()
    }
}

/** Caption lines inside a disclosure. */
@Composable
internal fun StockNotes(lines: List<String>) {
    lines.forEach { Text(it, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textSecondary) }
}

/**
 * The one "Show details" of a card or row. Always visible: the first sentence of [explanation] (`ReadableText.firstSentence`, never reworded).
 * Behind the toggle (collapsed by default): the rest of the text and [content]. Without anything to reveal there is no toggle. [trailing] shares the
 * toggle's row (e.g. "Learn more", "Read article") and wraps below it when they don't fit.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun StockShowDetails(explanation: String?, hasMore: Boolean, modifier: Modifier = Modifier,
                              trailing: (@Composable () -> Unit)? = null,
                              content: @Composable ColumnScope.() -> Unit = {}) {
    val (first, rest) = remember(explanation) { explanation?.let { ReadableText.firstSentence(it) } ?: ("" to null) }
    var open by rememberSaveable(explanation, hasMore) { mutableStateOf(false) }
    val colors = StockStepsTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        if (first.isNotEmpty()) Text(if (open && rest != null) "$first $rest" else first, style = StockStepsTheme.typography.small, color = colors.textBody)
        val toggle = rest != null || hasMore
        if (toggle && open) content()
        if (toggle || trailing != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.md), itemVerticalAlignment = Alignment.CenterVertically) {
            if (toggle) Row(Modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)
                .clickable(role = Role.Button, onClickLabel = if (open) "Hide details" else "Show details") { open = !open }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
                Text(if (open) "Hide details" else "Show details", style = StockStepsTheme.typography.label, color = colors.primaryText)
                Icon(StockIcons.ChevronDown, null, Modifier.size(StockStepsTheme.dimensions.iconSmall).rotate(if (open) 180f else 0f), tint = colors.primaryText)
            }
            trailing?.invoke()
        }
    }
}

/**
 * A text action in the same label style as the "Show details" toggle (5D.5.1), for actions that share its row ("Read article", "Understand more"):
 * icon + label, 48 dp target. [description] replaces the spoken label when the visible one is short.
 */
@Composable
internal fun StockLinkAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
                             description: String? = null) {
    val colors = StockStepsTheme.colors
    val tint = if (enabled) colors.primaryText else colors.textDisabled
    Row(modifier.heightIn(min = StockStepsTheme.dimensions.touchTarget)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        if (icon != null) Icon(icon, null, Modifier.size(StockStepsTheme.dimensions.iconSmall), tint = tint)
        Text(text, style = StockStepsTheme.typography.label, color = tint)
    }
}

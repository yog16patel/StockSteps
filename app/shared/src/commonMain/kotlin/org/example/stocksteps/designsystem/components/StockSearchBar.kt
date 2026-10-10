package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * Search surface that opens the Search destination. It is deliberately not
 * editable: typing happens on the Search screen, which owns debounce/results.
 * The editable field variant arrives with the Search screen migration.
 */
@Composable
internal fun StockSearchEntry(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StockStepsTheme.colors
    val shape = StockStepsTheme.shapes.card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = StockStepsTheme.dimensions.searchHeight)
            .clip(shape)
            .background(colors.surface)
            .border(StockStepsTheme.dimensions.border, colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = StockStepsTheme.spacing.md, vertical = StockStepsTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)
    ) {
        SearchGlyph(colors.iconSecondary)
        Text(
            text = placeholder,
            style = StockStepsTheme.typography.body,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Decorative magnifier drawn in common code; no icon library is bundled yet. */
@Composable
internal fun SearchGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(StockStepsTheme.dimensions.iconSmall)) {
        val stroke = size.minDimension * GLYPH_STROKE
        val radius = size.minDimension * GLYPH_RADIUS
        val center = Offset(size.width * GLYPH_CENTER, size.height * GLYPH_CENTER)
        drawCircle(color, radius, center, style = Stroke(stroke))
        val handle = radius * HANDLE_START
        drawLine(
            color = color,
            start = Offset(center.x + handle, center.y + handle),
            end = Offset(size.width * HANDLE_END, size.height * HANDLE_END),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private const val GLYPH_STROKE = 0.1f
private const val GLYPH_RADIUS = 0.3f
private const val GLYPH_CENTER = 0.42f
private const val HANDLE_START = 0.72f
private const val HANDLE_END = 0.88f

/**
 * Editable search field (Phase 2), the same surface as [StockSearchEntry]: magnifier, placeholder, clear button (48dp) once there is
 * text, a progress indicator while [loading], an announced [error] below, the Search IME action. Stateless: the caller (Search screen /
 * presenter) owns [query], debouncing and results.
 */
@Composable
internal fun StockSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: String? = null,
    onSearch: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    clearLabel: String = "Clear search"
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val shape = StockStepsTheme.shapes.card
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.labelValueGap)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clip(shape).background(colors.surface)
                .border(StockStepsTheme.dimensions.border, when { error != null -> colors.negative; focused -> colors.primary; else -> colors.border }, shape)
                .padding(start = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            SearchGlyph(colors.iconSecondary)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = StockStepsTheme.typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                modifier = Modifier.weight(1f).padding(vertical = spacing.sm)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics {
                        contentDescription = placeholder
                        if (error != null) error(error)
                    },
                decorationBox = { input ->
                    Box {
                        if (query.isEmpty()) Text(placeholder, style = StockStepsTheme.typography.body, color = colors.textTertiary, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        input()
                    }
                }
            )
            if (loading) {
                CircularProgressIndicator(Modifier.size(StockStepsTheme.dimensions.iconSmall), color = colors.primary,
                    strokeWidth = StockStepsTheme.dimensions.border * 2)
            }
            if (query.isNotEmpty()) {
                Box(Modifier.size(StockStepsTheme.dimensions.touchTarget).clickable(role = Role.Button, onClickLabel = clearLabel) { onQueryChange("") }
                    .semantics { contentDescription = clearLabel }, contentAlignment = Alignment.Center) {
                    Icon(StockIcons.Close, contentDescription = null, tint = colors.iconSecondary, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
                }
            } else {
                Spacer(Modifier.size(spacing.xs))
            }
        }
        error?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.negativeText) }
    }
}

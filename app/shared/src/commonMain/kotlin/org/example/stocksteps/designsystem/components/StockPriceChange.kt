package org.example.stocksteps.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.resources.Res
import org.example.stocksteps.resources.price_change_down
import org.example.stocksteps.resources.price_change_unavailable
import org.example.stocksteps.resources.price_change_unchanged
import org.example.stocksteps.resources.price_change_up
import org.jetbrains.compose.resources.stringResource

/**
 * The one price-movement treatment: arrow + signed text + semantic color, so
 * direction never depends on color alone. Callers pass preformatted values.
 */
@Composable
internal fun StockPriceChange(
    percentage: String,
    direction: PriceDirection,
    modifier: Modifier = Modifier,
    amount: String? = null,
    style: TextStyle = StockStepsTheme.typography.numberLabel,
    /** 1 in rows; metric layouts pass more so a long value wraps instead of being cut. */
    maxLines: Int = 1
) {
    val colors = StockStepsTheme.colors
    val value = listOfNotNull(amount, percentage).joinToString(" ")
    val (arrow, color, spoken) = when (direction) {
        PriceDirection.UP -> Triple("↑ ", colors.positiveText, stringResource(Res.string.price_change_up, value))
        PriceDirection.DOWN -> Triple("↓ ", colors.negativeText, stringResource(Res.string.price_change_down, value))
        PriceDirection.UNCHANGED -> Triple("", colors.textSecondary, stringResource(Res.string.price_change_unchanged))
        PriceDirection.UNAVAILABLE -> Triple("", colors.textTertiary, stringResource(Res.string.price_change_unavailable))
    }
    Text(
        text = arrow + value,
        modifier = modifier.semantics { contentDescription = spoken },
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

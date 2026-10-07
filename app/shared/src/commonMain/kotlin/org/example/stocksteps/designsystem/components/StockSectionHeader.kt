package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/** Compact section title with an optional "See All"-style action or custom trailing content. */
@Composable
internal fun StockSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = StockStepsTheme.typography.cardTitle,
            color = StockStepsTheme.colors.textPrimary
        )
        trailing?.invoke(this)
        if (actionText != null && onActionClick != null) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button, onClick = onActionClick)
                    .padding(horizontal = StockStepsTheme.spacing.xs),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(actionText, style = StockStepsTheme.typography.label, color = StockStepsTheme.colors.primaryText)
            }
        }
    }
}

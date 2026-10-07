package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/** Groups related information: semantic surface, 12dp radius, 1dp border, no heavy shadow. */
@Composable
internal fun StockCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    containerColor: Color = StockStepsTheme.colors.surface,
    borderColor: Color = StockStepsTheme.colors.border,
    contentPadding: PaddingValues = PaddingValues(StockStepsTheme.spacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = StockStepsTheme.shapes.card
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(StockStepsTheme.dimensions.border, borderColor, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        content = content
    )
}

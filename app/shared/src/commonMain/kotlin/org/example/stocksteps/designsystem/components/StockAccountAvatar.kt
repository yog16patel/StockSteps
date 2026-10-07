package org.example.stocksteps.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme

/**
 * Account avatar. V1 always renders the neutral person placeholder; `imageUrl` is accepted so
 * profile photos can be added later without changing callers. Decorative for screen readers.
 */
@Composable
internal fun StockAccountAvatar(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") imageUrl: String? = null
) {
    Box(
        modifier = modifier
            .size(StockStepsTheme.dimensions.avatar)
            .background(StockStepsTheme.colors.primaryContainer, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Icon(StockIcons.Person, contentDescription = null, tint = StockStepsTheme.colors.primary, modifier = Modifier.size(StockStepsTheme.dimensions.iconLarge))
    }
}

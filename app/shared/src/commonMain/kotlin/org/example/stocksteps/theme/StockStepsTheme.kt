package org.example.stocksteps.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun Int.color() = Color(0xFF000000L or toLong())
private fun ThemeTextStyle.composeStyle() = TextStyle(
    fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = FontWeight(weight)
)

@Composable
fun StockStepsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) ThemeColors.dark else ThemeColors.light
    val colors = (if (darkTheme) darkColorScheme() else lightColorScheme()).copy(
        primary = palette.primary.color(), onPrimary = palette.onPrimary.color(),
        secondary = palette.primary.color(), onSecondary = palette.onPrimary.color(),
        primaryContainer = palette.surfaceVariant.color(), onPrimaryContainer = palette.textPrimary.color(),
        secondaryContainer = palette.surfaceVariant.color(), onSecondaryContainer = palette.textPrimary.color(),
        background = palette.background.color(), onBackground = palette.textPrimary.color(),
        surface = palette.surface.color(), onSurface = palette.textPrimary.color(),
        surfaceVariant = palette.surfaceVariant.color(), onSurfaceVariant = palette.textSecondary.color(),
        surfaceContainer = palette.surface.color(),
        surfaceContainerHigh = palette.surfaceVariant.color(),
        surfaceContainerHighest = palette.surfaceVariant.color(),
        outline = palette.outline.color(), error = palette.error.color()
    )
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineMedium = ThemeTypography.headline.composeStyle(),
            titleLarge = ThemeTypography.title.composeStyle(),
            titleMedium = ThemeTypography.subtitle.composeStyle(),
            bodyLarge = ThemeTypography.body.composeStyle(),
            bodyMedium = ThemeTypography.body.composeStyle(),
            labelSmall = ThemeTypography.caption.composeStyle()
        ),
        shapes = Shapes(
            small = RoundedCornerShape(ThemeCorners.small.dp),
            medium = RoundedCornerShape(ThemeCorners.medium.dp),
            large = RoundedCornerShape(ThemeCorners.large.dp)
        ),
        content = content
    )
}

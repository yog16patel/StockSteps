package org.example.stocksteps.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.stocksteps.settings.ThemeMode
import org.example.stocksteps.theme.ThemeColors
import org.example.stocksteps.theme.ThemeCorners
import org.example.stocksteps.theme.ThemeDimensions
import org.example.stocksteps.theme.ThemePalette
import org.example.stocksteps.theme.ThemeSpacing
import org.example.stocksteps.theme.ThemeTextStyle
import org.example.stocksteps.theme.ThemeTypography

@Immutable
internal data class StockStepsColors(
    val isDark: Boolean,
    val appBackground: Color,
    val backgroundAlt: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceSecondary: Color,
    val surfaceSelected: Color,
    val border: Color,
    val borderSubtle: Color,
    val primary: Color,
    val primaryContainer: Color,
    val primaryDark: Color,
    val primaryText: Color,
    val onPrimary: Color,
    val positive: Color,
    val positiveContainer: Color,
    val positiveBorder: Color,
    val positiveText: Color,
    val warning: Color,
    val warningContainer: Color,
    val warningBorder: Color,
    val caution: Color,
    val cautionContainer: Color,
    val cautionBorder: Color,
    val negative: Color,
    val negativeContainer: Color,
    val negativeBorder: Color,
    val negativeText: Color,
    val textPrimary: Color,
    val textBody: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val iconPrimary: Color,
    val iconSecondary: Color,
    val educationContainer: Color,
    val educationAccent: Color,
    val logoContainer: Color,
    val cautionText: Color,
    val learnContainerStart: Color,
    val learnContainerEnd: Color,
    val learnAccent: Color,
    val onLearnAccent: Color
)

@Immutable
internal data class StockStepsTypography(
    val display: TextStyle,
    val largeNumber: TextStyle,
    val screenTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val bodyMedium: TextStyle,
    val bodySemiBold: TextStyle,
    val small: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val tiny: TextStyle,
    val numberEmphasis: TextStyle,
    val numberMedium: TextStyle,
    val numberLabel: TextStyle,
    val numberLabelStrong: TextStyle
)

@Immutable
internal data class StockStepsSpacing(
    val none: Dp = ThemeSpacing.none.dp,
    val xxs: Dp = ThemeSpacing.xxs.dp,
    val xs: Dp = ThemeSpacing.xs.dp,
    val sm: Dp = ThemeSpacing.sm.dp,
    val md: Dp = ThemeSpacing.md.dp,
    val lg: Dp = ThemeSpacing.lg.dp,
    val xl: Dp = ThemeSpacing.xl.dp,
    val xxl: Dp = ThemeSpacing.xxl.dp,
    val xxxl: Dp = ThemeSpacing.xxxl.dp,
    val screen: Dp = ThemeSpacing.screen.dp,
    val cardPadding: Dp = ThemeSpacing.cardPadding.dp,
    val educationalCardPadding: Dp = ThemeSpacing.educationalCardPadding.dp,
    val sectionGap: Dp = ThemeSpacing.sectionGap.dp
)

@Immutable
internal data class StockStepsDimensions(
    val touchTarget: Dp = ThemeDimensions.touchTarget.dp,
    val iconSmall: Dp = ThemeDimensions.iconSmall.dp,
    val icon: Dp = ThemeDimensions.icon.dp,
    val iconLarge: Dp = ThemeDimensions.iconLarge.dp,
    val navIcon: Dp = ThemeDimensions.navIcon.dp,
    val logo: Dp = ThemeDimensions.logo.dp,
    val bottomBarHeight: Dp = ThemeDimensions.bottomBarHeight.dp,
    val rowMinHeight: Dp = ThemeDimensions.rowMinHeight.dp,
    val chipHeight: Dp = ThemeDimensions.chipHeight.dp,
    val buttonHeight: Dp = ThemeDimensions.buttonHeight.dp,
    val searchHeight: Dp = ThemeDimensions.searchHeight.dp,
    val border: Dp = ThemeDimensions.border.dp,
    val contentMaxWidth: Dp = ThemeDimensions.contentMaxWidth.dp,
    val skeletonLine: Dp = ThemeDimensions.skeletonLine.dp,
    val newsThumbnail: Dp = ThemeDimensions.newsThumbnail.dp,
    val newsThumbnailCompact: Dp = ThemeDimensions.newsThumbnailCompact.dp,
    val logoCompact: Dp = ThemeDimensions.logoCompact.dp,
    val brandMark: Dp = ThemeDimensions.brandMark.dp,
    val rowCompactMinHeight: Dp = ThemeDimensions.rowCompactMinHeight.dp,
    val sparklineWidth: Dp = ThemeDimensions.sparklineWidth.dp,
    val sparklineHeight: Dp = ThemeDimensions.sparklineHeight.dp,
    val sparklineStroke: Dp = ThemeDimensions.sparklineStroke.dp,
    val statusDot: Dp = ThemeDimensions.statusDot.dp,
    val statusSwitchWidth: Dp = ThemeDimensions.statusSwitchWidth.dp,
    val statusSwitchHeight: Dp = ThemeDimensions.statusSwitchHeight.dp,
    val statusSwitchThumb: Dp = ThemeDimensions.statusSwitchThumb.dp,
    val avatar: Dp = ThemeDimensions.avatar.dp,
    val chartHeight: Dp = ThemeDimensions.chartHeight.dp,
    val educationIcon: Dp = ThemeDimensions.educationIcon.dp,
    val learnIllustration: Dp = ThemeDimensions.learnIllustration.dp,
    val learnAction: Dp = ThemeDimensions.learnAction.dp,
    val iconTile: Dp = ThemeDimensions.iconTile.dp,
    val rangeBar: Dp = ThemeDimensions.rangeBar.dp,
    val rangeMarker: Dp = ThemeDimensions.rangeMarker.dp,
    val changeColumn: Dp = ThemeDimensions.changeColumn.dp,
    val indexCardWidth: Dp = ThemeDimensions.indexCardWidth.dp,
    val multiColumnMinWidth: Dp = ThemeDimensions.multiColumnMinWidth.dp,
    val largeFontScale: Float = ThemeDimensions.largeFontScale
)

@Immutable
internal data class StockStepsShapes(
    val chip: Shape = RoundedCornerShape(ThemeCorners.chip.dp),
    val button: Shape = RoundedCornerShape(ThemeCorners.button.dp),
    val card: Shape = RoundedCornerShape(ThemeCorners.card.dp),
    val cardLarge: Shape = RoundedCornerShape(ThemeCorners.cardLarge.dp),
    val pill: Shape = RoundedCornerShape(percent = 50)
)

private val LocalColors = staticCompositionLocalOf { ThemeColors.light.toComposeColors(isDark = false) }
private val LocalTypography = staticCompositionLocalOf { stockStepsTypography() }
private val LocalSpacing = staticCompositionLocalOf { StockStepsSpacing() }
private val LocalDimensions = staticCompositionLocalOf { StockStepsDimensions() }
private val LocalShapes = staticCompositionLocalOf { StockStepsShapes() }

/** Design-system accessors: `StockStepsTheme.colors.positive`, `StockStepsTheme.spacing.lg`. */
internal object StockStepsTheme {
    val colors: StockStepsColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val typography: StockStepsTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
    val spacing: StockStepsSpacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val dimensions: StockStepsDimensions
        @Composable @ReadOnlyComposable get() = LocalDimensions.current
    val shapes: StockStepsShapes
        @Composable @ReadOnlyComposable get() = LocalShapes.current
}

private val lightColors = ThemeColors.light.toComposeColors(isDark = false)
private val darkColors = ThemeColors.dark.toComposeColors(isDark = true)
private val typography = stockStepsTypography()

@Composable
internal fun StockStepsTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (dark) darkColors else lightColors
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography
    ) {
        // Material components not yet migrated still read MaterialTheme; map it onto the same tokens.
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = typography.toMaterialTypography(),
            shapes = Shapes(
                small = RoundedCornerShape(ThemeCorners.chip.dp),
                medium = RoundedCornerShape(ThemeCorners.card.dp),
                large = RoundedCornerShape(ThemeCorners.cardLarge.dp)
            ),
            content = content
        )
    }
}

private fun Int.color() = Color(0xFF000000L or toLong())

private fun ThemePalette.toComposeColors(isDark: Boolean) = StockStepsColors(
    isDark = isDark,
    appBackground = appBackground.color(), backgroundAlt = backgroundAlt.color(),
    surface = surface.color(), surfaceElevated = surfaceElevated.color(),
    surfaceSecondary = surfaceSecondary.color(), surfaceSelected = surfaceSelected.color(),
    border = border.color(), borderSubtle = borderSubtle.color(),
    primary = primary.color(), primaryContainer = primaryContainer.color(), primaryDark = primaryDark.color(),
    primaryText = primaryText.color(), onPrimary = onPrimary.color(),
    positive = positive.color(), positiveContainer = positiveContainer.color(),
    positiveBorder = positiveBorder.color(), positiveText = positiveText.color(),
    warning = warning.color(), warningContainer = warningContainer.color(), warningBorder = warningBorder.color(),
    caution = caution.color(), cautionContainer = cautionContainer.color(), cautionBorder = cautionBorder.color(),
    negative = negative.color(), negativeContainer = negativeContainer.color(),
    negativeBorder = negativeBorder.color(), negativeText = negativeText.color(),
    textPrimary = textPrimary.color(), textBody = textBody.color(), textSecondary = textSecondary.color(),
    textTertiary = textTertiary.color(), textDisabled = textDisabled.color(),
    iconPrimary = iconPrimary.color(), iconSecondary = iconSecondary.color(),
    educationContainer = educationContainer.color(), educationAccent = educationAccent.color(),
    logoContainer = logoContainer.color(),
    cautionText = cautionText.color(),
    learnContainerStart = learnContainerStart.color(), learnContainerEnd = learnContainerEnd.color(),
    learnAccent = learnAccent.color(), onLearnAccent = onLearnAccent.color()
)

private fun StockStepsColors.toMaterialScheme() = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = primaryContainer, onPrimaryContainer = primaryText,
    secondary = primary, onSecondary = onPrimary,
    secondaryContainer = primaryContainer, onSecondaryContainer = primaryText,
    background = appBackground, onBackground = textPrimary,
    surface = surface, onSurface = textPrimary,
    surfaceVariant = surfaceSecondary, onSurfaceVariant = textSecondary,
    surfaceContainerLowest = surface, surfaceContainerLow = surface,
    surfaceContainer = surface, surfaceContainerHigh = surfaceElevated,
    surfaceContainerHighest = surfaceSecondary,
    outline = border, outlineVariant = borderSubtle,
    error = negative, onError = onPrimary,
    errorContainer = negativeContainer, onErrorContainer = negativeText
)

private fun ThemeTextStyle.compose() = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = FontWeight(weight),
    fontFeatureSettings = if (tabular) "tnum" else null
)

private fun stockStepsTypography() = StockStepsTypography(
    display = ThemeTypography.display.compose(),
    largeNumber = ThemeTypography.largeNumber.compose(),
    screenTitle = ThemeTypography.screenTitle.compose(),
    sectionTitle = ThemeTypography.sectionTitle.compose(),
    cardTitle = ThemeTypography.cardTitle.compose(),
    body = ThemeTypography.body.compose(),
    bodyMedium = ThemeTypography.bodyMedium.compose(),
    bodySemiBold = ThemeTypography.bodySemiBold.compose(),
    small = ThemeTypography.small.compose(),
    label = ThemeTypography.label.compose(),
    caption = ThemeTypography.caption.compose(),
    tiny = ThemeTypography.tiny.compose(),
    numberEmphasis = ThemeTypography.numberEmphasis.compose(),
    numberMedium = ThemeTypography.numberMedium.compose(),
    numberLabel = ThemeTypography.numberLabel.compose(),
    numberLabelStrong = ThemeTypography.numberLabelStrong.compose()
)

private fun StockStepsTypography.toMaterialTypography() = Typography(
    displaySmall = display,
    headlineLarge = display,
    headlineMedium = screenTitle,
    headlineSmall = screenTitle,
    titleLarge = sectionTitle,
    titleMedium = cardTitle,
    titleSmall = bodyMedium,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = small,
    labelLarge = bodyMedium,
    labelMedium = label,
    labelSmall = caption
)

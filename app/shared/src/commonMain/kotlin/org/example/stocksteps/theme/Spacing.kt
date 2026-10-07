package org.example.stocksteps.theme

// Logical units: dp on Android, points on iOS. 4-point grid.
object ThemeSpacing {
    val none = 0
    val xxs = 2
    val xs = 4
    val sm = 8
    val md = 12
    val lg = 16
    val xl = 20
    val xxl = 24
    val xxxl = 32

    // Semantic aliases.
    val screen = lg
    val cardPadding = md
    val educationalCardPadding = lg
    val sectionGap = md
    val related = sm
    val iconText = sm
    val titleSubtitle = xxs

    // Legacy names used by screens that have not migrated to the design system yet.
    val tiny = xs
    val space6 = 6
    val small = sm
    val space10 = 10
    val medium = md
    val space14 = 14
    val large = lg
    val extraLarge = xxl
    val space30 = 30
    val space40 = 40
}

object ThemeCorners {
    val chip = 8
    val button = 10
    val card = 12
    val cardLarge = 16

    // Legacy names.
    val small = chip
    val medium = card
    val large = 20
}

object ThemeDimensions {
    val touchTarget = 48
    val iconSmall = 16
    val icon = 20
    val iconLarge = 24
    val navIcon = 22
    val logo = 32
    val topBarMaxHeight = 56
    val bottomBarHeight = 56
    val rowMinHeight = 56
    val chipHeight = 28
    val buttonHeight = 44
    val searchHeight = 44
    val border = 1
    val contentMaxWidth = 720
    val skeletonLine = 12
    val newsThumbnail = 56
    val newsThumbnailCompact = 48
    val logoCompact = 28
    val brandMark = 26
    val rowCompactMinHeight = 52
    val sparklineWidth = 64
    val sparklineHeight = 28
    val sparklineStroke = 1.5f
    val statusDot = 8
    val avatar = 44
    val educationIcon = 44
    // Below this width, or above this font scale, side-by-side values stack.
    val multiColumnMinWidth = 300
    val largeFontScale = 1.3f
}

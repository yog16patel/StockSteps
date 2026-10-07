import Shared
import SwiftUI
import UIKit

// Adapters only: token values live in commonMain/theme and are shared with Compose.
enum StockStepsTheme {
    static func palette(_ scheme: ColorScheme) -> ThemePalette {
        scheme == .dark ? ThemeColors.shared.dark : ThemeColors.shared.light
    }

    static func colors(_ scheme: ColorScheme) -> StockColors { StockColors(palette(scheme)) }

    static func color(_ rgb: Int32) -> Color {
        Color(.sRGB, red: Double((rgb >> 16) & 255) / 255,
              green: Double((rgb >> 8) & 255) / 255,
              blue: Double(rgb & 255) / 255, opacity: 1)
    }

    /// Dynamic Type-scaled system font; tabular tokens get fixed-width digits.
    static func font(_ token: ThemeTextStyle, relativeTo style: UIFont.TextStyle = .body) -> Font {
        let weight: UIFont.Weight = switch token.weight {
        case 700...: .bold
        case 600..<700: .semibold
        case 500..<600: .medium
        default: .regular
        }
        let base = UIFont.systemFont(ofSize: CGFloat(token.size), weight: weight)
        let font = Font(UIFontMetrics(forTextStyle: style).scaledFont(for: base))
        return token.tabular ? font.monospacedDigit() : font
    }

    static let spacing = ThemeSpacing.shared
    static let corners = ThemeCorners.shared
    static let typography = ThemeTypography.shared
    static let dimensions = ThemeDimensions.shared
}

/// Semantic SwiftUI colors for one color scheme.
struct StockColors {
    let appBackground, surface, surfaceSecondary, border, borderSubtle: Color
    let primary, primaryDark, primaryContainer, primaryText, onPrimary: Color
    let educationContainer, educationAccent, logoContainer: Color
    let positive, negative, warning, warningContainer: Color
    let positiveText, negativeText, negativeContainer, negativeBorder: Color
    let textPrimary, textBody, textSecondary, textTertiary: Color
    let iconSecondary: Color

    init(_ p: ThemePalette) {
        let c = StockStepsTheme.color
        appBackground = c(p.appBackground); surface = c(p.surface); surfaceSecondary = c(p.surfaceSecondary)
        border = c(p.border); borderSubtle = c(p.borderSubtle)
        primary = c(p.primary); primaryDark = c(p.primaryDark); primaryContainer = c(p.primaryContainer)
        primaryText = c(p.primaryText); onPrimary = c(p.onPrimary)
        educationContainer = c(p.educationContainer); educationAccent = c(p.educationAccent); logoContainer = c(p.logoContainer)
        positive = c(p.positive); negative = c(p.negative); warning = c(p.warning); warningContainer = c(p.warningContainer)
        positiveText = c(p.positiveText); negativeText = c(p.negativeText)
        negativeContainer = c(p.negativeContainer); negativeBorder = c(p.negativeBorder)
        textPrimary = c(p.textPrimary); textBody = c(p.textBody); textSecondary = c(p.textSecondary); textTertiary = c(p.textTertiary)
        iconSecondary = c(p.iconSecondary)
    }
}

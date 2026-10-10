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

    /// Dynamic Type-scaled system font; tabular tokens get fixed-width digits. Without `relativeTo` the token scales with the
    /// iOS text style closest to its size (`dynamicTypeStyle`), so captions grow like captions and titles like titles.
    static func font(_ token: ThemeTextStyle, relativeTo style: UIFont.TextStyle? = nil) -> Font {
        let weight: UIFont.Weight = switch token.weight {
        case 700...: .bold
        case 600..<700: .semibold
        case 500..<600: .medium
        default: .regular
        }
        let base = UIFont.systemFont(ofSize: CGFloat(token.size), weight: weight)
        let font = Font(UIFontMetrics(forTextStyle: style ?? dynamicTypeStyle(for: token)).scaledFont(for: base))
        return token.tabular ? font.monospacedDigit() : font
    }

    /// The iOS text style whose default (Large) size is nearest the token's size; ties go to the smaller style.
    static func dynamicTypeStyle(for token: ThemeTextStyle) -> UIFont.TextStyle {
        let styles: [(UIFont.TextStyle, Int32)] = [
            (.caption2, 11), (.caption1, 12), (.footnote, 13), (.subheadline, 15), (.callout, 16), (.body, 17),
            (.title3, 20), (.title2, 22), (.title1, 28), (.largeTitle, 34)
        ]
        return styles.min { abs($0.1 - token.size) < abs($1.1 - token.size) }!.0
    }

    /// Extra line spacing so SwiftUI text reaches the token's line height (SwiftUI fonts carry none); scales with Dynamic Type.
    static func lineSpacing(_ token: ThemeTextStyle, relativeTo style: UIFont.TextStyle? = nil) -> CGFloat {
        let natural = UIFont.systemFont(ofSize: CGFloat(token.size)).lineHeight
        let extra = max(0, CGFloat(token.lineHeight) - natural)
        return UIFontMetrics(forTextStyle: style ?? dynamicTypeStyle(for: token)).scaledValue(for: extra)
    }

    static let spacing = ThemeSpacing.shared
    static let corners = ThemeCorners.shared
    static let typography = ThemeTypography.shared
    static let dimensions = ThemeDimensions.shared
}

extension View {
    /// Token font plus the token's line height (multi-line text). Prefer this to a bare `.font(...)` in refined components.
    func stockFont(_ token: ThemeTextStyle, relativeTo style: UIFont.TextStyle? = nil) -> some View {
        font(StockStepsTheme.font(token, relativeTo: style)).lineSpacing(StockStepsTheme.lineSpacing(token, relativeTo: style))
    }
}

/// Semantic SwiftUI colors for one color scheme.
struct StockColors {
    let appBackground, surface, surfaceSecondary, border, borderSubtle: Color
    let primary, primaryDark, primaryContainer, primaryText, onPrimary: Color
    let educationContainer, educationAccent, logoContainer: Color
    let learnContainerStart, learnContainerEnd, learnAccent, onLearnAccent, cautionText: Color
    let positive, positiveContainer, negative, warning, warningContainer: Color
    let positiveText, negativeText, negativeContainer, negativeBorder: Color
    let textPrimary, textBody, textSecondary, textTertiary, textDisabled: Color
    let iconSecondary: Color
    let primaryBright, brandGlow, primaryGradientEnd: Color

    // Text roles mapped onto the ramp (see ThemePalette): titles/values, supporting copy, metadata.
    var textTitle: Color { textPrimary }
    var textValue: Color { textPrimary }
    var textSupporting: Color { textSecondary }
    var textMeta: Color { textTertiary }

    init(_ p: ThemePalette) {
        let c = StockStepsTheme.color
        appBackground = c(p.appBackground); surface = c(p.surface); surfaceSecondary = c(p.surfaceSecondary)
        border = c(p.border); borderSubtle = c(p.borderSubtle)
        primary = c(p.primary); primaryDark = c(p.primaryDark); primaryContainer = c(p.primaryContainer)
        primaryText = c(p.primaryText); onPrimary = c(p.onPrimary)
        educationContainer = c(p.educationContainer); educationAccent = c(p.educationAccent); logoContainer = c(p.logoContainer)
        learnContainerStart = c(p.learnContainerStart); learnContainerEnd = c(p.learnContainerEnd)
        learnAccent = c(p.learnAccent); onLearnAccent = c(p.onLearnAccent); cautionText = c(p.cautionText)
        positive = c(p.positive); positiveContainer = c(p.positiveContainer); negative = c(p.negative); warning = c(p.warning); warningContainer = c(p.warningContainer)
        positiveText = c(p.positiveText); negativeText = c(p.negativeText)
        negativeContainer = c(p.negativeContainer); negativeBorder = c(p.negativeBorder)
        textPrimary = c(p.textPrimary); textBody = c(p.textBody); textSecondary = c(p.textSecondary); textTertiary = c(p.textTertiary); textDisabled = c(p.textDisabled)
        iconSecondary = c(p.iconSecondary)
        primaryBright = c(p.primaryBright); brandGlow = c(p.brandGlow); primaryGradientEnd = c(p.primaryGradientEnd)
    }
}

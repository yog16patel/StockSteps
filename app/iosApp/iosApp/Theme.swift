import Shared
import SwiftUI
import UIKit

// Adapters only: token values live in commonMain/theme.
enum StockStepsTheme {
    static func palette(_ scheme: ColorScheme) -> ThemePalette {
        scheme == .dark ? ThemeColors.shared.dark : ThemeColors.shared.light
    }

    static func color(_ rgb: Int32) -> Color {
        Color(.sRGB, red: Double((rgb >> 16) & 255) / 255,
              green: Double((rgb >> 8) & 255) / 255,
              blue: Double(rgb & 255) / 255, opacity: 1)
    }

    static func font(_ token: ThemeTextStyle, relativeTo style: UIFont.TextStyle) -> Font {
        let weight: UIFont.Weight = token.weight >= 700 ? .bold : token.weight >= 600 ? .semibold : .regular
        let base = UIFont.systemFont(ofSize: CGFloat(token.size), weight: weight)
        return Font(UIFontMetrics(forTextStyle: style).scaledFont(for: base))
    }

    static let spacing = ThemeSpacing.shared
    static let corners = ThemeCorners.shared
    static let typography = ThemeTypography.shared
}

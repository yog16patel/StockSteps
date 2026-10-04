import Shared
import SwiftUI

enum AuthTheme {
    static let tokens = AuthTokens.shared
    static let spacing = StockStepsTheme.spacing
    static func color(_ value: Int32) -> Color { StockStepsTheme.color(value) }
    static func font(_ value: ThemeTextStyle, _ role: UIFont.TextStyle = .body) -> Font {
        StockStepsTheme.font(value, relativeTo: role)
    }
}

struct AuthBrand: View {
    private let t = AuthTheme.tokens
    var body: some View {
        HStack(spacing: CGFloat(AuthTheme.spacing.space10)) {
            Text("S")
                .font(AuthTheme.font(t.logo, .headline))
                .foregroundStyle(AuthTheme.color(t.surface))
                .frame(width: CGFloat(t.brandSize), height: CGFloat(t.brandSize))
                .background(AuthTheme.color(t.ink), in: RoundedRectangle(cornerRadius: CGFloat(t.brandRadius)))
            Text("StockSteps")
                .font(AuthTheme.font(t.brand, .headline))
                .foregroundStyle(AuthTheme.color(t.ink))
        }
    }
}

struct AuthButton: View {
    let title: String
    var primary = false
    var google = false
    var enabled = true
    let action: () -> Void
    private let t = AuthTheme.tokens

    var body: some View {
        Button(action: action) {
            HStack(spacing: CGFloat(AuthTheme.spacing.space10)) {
                if google {
                    Text("G")
                        .font(AuthTheme.font(t.linkText))
                        .foregroundStyle(AuthTheme.color(t.googleInk))
                        .frame(width: CGFloat(t.googleSize), height: CGFloat(t.googleSize))
                        .overlay(RoundedRectangle(cornerRadius: CGFloat(t.googleRadius)).stroke(AuthTheme.color(t.googleBorder), lineWidth: CGFloat(t.googleBorderWidth)))
                }
                Text(title).font(AuthTheme.font(t.button))
            }
            .frame(maxWidth: .infinity, minHeight: CGFloat(t.controlHeight))
            .foregroundStyle(AuthTheme.color(primary ? t.surface : t.ink))
            .background(AuthTheme.color(primary ? t.ink : t.surface), in: RoundedRectangle(cornerRadius: CGFloat(t.controlRadius)))
            .overlay(RoundedRectangle(cornerRadius: CGFloat(t.controlRadius)).stroke(AuthTheme.color(primary ? t.ink : t.outline), lineWidth: CGFloat(t.borderWidth)))
            .opacity(enabled ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct AuthDivider: View {
    let text: String
    var body: some View {
        HStack(spacing: CGFloat(AuthTheme.spacing.space10)) {
            Rectangle().fill(AuthTheme.color(AuthTheme.tokens.outline)).frame(height: CGFloat(AuthTheme.tokens.borderWidth))
            Text(text).font(AuthTheme.font(AuthTheme.tokens.caption, .caption2))
                .foregroundStyle(AuthTheme.color(AuthTheme.tokens.muted)).fixedSize()
            Rectangle().fill(AuthTheme.color(AuthTheme.tokens.outline)).frame(height: CGFloat(AuthTheme.tokens.borderWidth))
        }
    }
}

struct AuthField<Content: View>: View {
    let label: String
    @ViewBuilder let content: Content
    private let t = AuthTheme.tokens
    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(t.fieldGap)) {
            Text(label).font(AuthTheme.font(t.label, .caption1)).foregroundStyle(AuthTheme.color(t.muted))
            content
                .font(AuthTheme.font(t.body))
                .foregroundStyle(AuthTheme.color(t.ink))
                .padding(.horizontal, CGFloat(AuthTheme.spacing.space14))
                .frame(minHeight: CGFloat(t.controlHeight))
                .background(AuthTheme.color(t.surface), in: RoundedRectangle(cornerRadius: CGFloat(t.controlRadius)))
                .overlay(RoundedRectangle(cornerRadius: CGFloat(t.controlRadius)).stroke(AuthTheme.color(t.outline), lineWidth: CGFloat(t.borderWidth)))
        }
    }
}

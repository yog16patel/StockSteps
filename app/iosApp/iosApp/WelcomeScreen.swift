import Shared
import SwiftUI

// Platform adapter; Figma values live in the shared theme folder.
enum WelcomeTheme {
    static let tokens = WelcomeTokens.shared
    static let spacing = StockStepsTheme.spacing
    static func color(_ value: Int32) -> Color { StockStepsTheme.color(value) }
    static func font(_ value: ThemeTextStyle, _ role: UIFont.TextStyle) -> Font {
        StockStepsTheme.font(value, relativeTo: role)
    }
}

struct WelcomeScreen: View {
    let onStartExploring: () -> Void
    private let t = WelcomeTheme.tokens
    private let s = WelcomeTheme.spacing

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .center, spacing: 0) {
                    VStack(alignment: .leading, spacing: 0) {
                        WelcomeBrand()
                        Spacer().frame(height: CGFloat(s.space30))
                        Text("Invest with more\nunderstanding.")
                            .font(WelcomeTheme.font(t.title, .largeTitle))
                            .foregroundStyle(WelcomeTheme.color(t.ink))
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer().frame(height: CGFloat(s.medium))
                        Text("Stocks can feel complicated. We turn prices, financials and market news into simple explanations.")
                            .font(WelcomeTheme.font(t.body, .body))
                            .foregroundStyle(WelcomeTheme.color(t.muted))
                        Spacer().frame(height: CGFloat(s.space40))
                        WelcomeInsightCard()
                        Spacer().frame(height: CGFloat(s.extraLarge))
                        WelcomeBenefits()
                    }
                    Spacer(minLength: CGFloat(s.space40))
                    VStack(spacing: CGFloat(s.medium)) {
                        Button(action: onStartExploring) {
                            Text("Start exploring")
                                .font(WelcomeTheme.font(t.body, .body)).fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                                .frame(minHeight: CGFloat(t.buttonHeight))
                                .foregroundStyle(WelcomeTheme.color(t.surface))
                                .background(WelcomeTheme.color(t.ink), in: RoundedRectangle(cornerRadius: CGFloat(t.buttonRadius)))
                        }
                        .buttonStyle(.plain)
                        Text("No trading required · Built for beginners")
                            .font(WelcomeTheme.font(t.footer, .caption2))
                            .foregroundStyle(WelcomeTheme.color(t.muted))
                    }
                }
                .frame(maxWidth: CGFloat(t.contentWidth))
                .padding(.horizontal, CGFloat(s.extraLarge))
                .padding(.vertical, CGFloat(s.space30))
                .frame(maxWidth: .infinity, minHeight: geometry.size.height)
            }
        }
        .background(WelcomeTheme.color(t.background).ignoresSafeArea())
    }
}

private struct WelcomeBrand: View {
    private let t = WelcomeTheme.tokens
    var body: some View {
        HStack(spacing: CGFloat(WelcomeTheme.spacing.medium)) {
            Text("S")
                .font(StockStepsTheme.font(StockStepsTheme.typography.title, relativeTo: .title2))
                .foregroundStyle(WelcomeTheme.color(t.surface))
                .frame(width: CGFloat(t.brandSize), height: CGFloat(t.brandSize))
                .background(WelcomeTheme.color(t.ink), in: RoundedRectangle(cornerRadius: CGFloat(t.iconRadius)))
            VStack(alignment: .leading, spacing: CGFloat(WelcomeTheme.spacing.tiny)) {
                Text("StockSteps").font(WelcomeTheme.font(t.brand, .headline)).foregroundStyle(WelcomeTheme.color(t.ink))
                Text("INVESTING, EXPLAINED").font(WelcomeTheme.font(t.benefit, .caption2)).foregroundStyle(WelcomeTheme.color(t.green))
            }
        }
    }
}

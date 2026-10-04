import SwiftUI

// Static onboarding example, independent of market-data requests.
struct WelcomeInsightCard: View {
    private let t = WelcomeTheme.tokens
    private let s = WelcomeTheme.spacing
    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(s.extraLarge)) {
            HStack(spacing: CGFloat(s.medium)) {
                RoundedRectangle(cornerRadius: CGFloat(t.iconRadius))
                    .fill(WelcomeTheme.color(t.mint))
                    .frame(width: CGFloat(t.logoSize), height: CGFloat(t.logoSize))
                VStack(alignment: .leading, spacing: CGFloat(s.tiny)) {
                    Text("AAPL").font(WelcomeTheme.font(t.ticker, .headline)).foregroundStyle(WelcomeTheme.color(t.ink))
                    Text("Apple Inc.").font(WelcomeTheme.font(t.caption, .caption1)).foregroundStyle(WelcomeTheme.color(t.muted))
                }
                Spacer()
                Text("+1.8%").font(WelcomeTheme.font(t.body, .body)).fontWeight(.bold).foregroundStyle(WelcomeTheme.color(t.green))
            }
            HStack(spacing: CGFloat(s.medium)) {
                Text("$255.40").font(WelcomeTheme.font(t.price, .title1)).foregroundStyle(WelcomeTheme.color(t.ink))
                Spacer(minLength: 0)
                Text("╱╲__╱╲___╱")
                    .font(WelcomeTheme.font(t.brand, .headline))
                    .foregroundStyle(WelcomeTheme.color(t.green))
                    .frame(maxWidth: CGFloat(t.chartWidth), minHeight: CGFloat(t.chartHeight))
                    .background(WelcomeTheme.color(t.mint), in: RoundedRectangle(cornerRadius: CGFloat(t.iconRadius)))
            }
            Rectangle().fill(WelcomeTheme.color(t.divider)).frame(height: 1)
            HStack(alignment: .top, spacing: CGFloat(s.medium)) {
                Text("?")
                    .font(WelcomeTheme.font(t.ticker, .headline))
                    .foregroundStyle(WelcomeTheme.color(t.blue))
                    .frame(width: CGFloat(t.questionSize), height: CGFloat(t.questionSize))
                    .background(WelcomeTheme.color(t.blueTint), in: RoundedRectangle(cornerRadius: CGFloat(s.space10)))
                VStack(alignment: .leading, spacing: CGFloat(s.tiny)) {
                    Text("Why did Apple move today?").font(WelcomeTheme.font(t.explanation, .subheadline)).foregroundStyle(WelcomeTheme.color(t.ink))
                    Text("See the news and events behind the price — explained simply.").font(WelcomeTheme.font(t.caption, .caption1)).foregroundStyle(WelcomeTheme.color(t.muted))
                }
                Text("→").font(WelcomeTheme.font(t.brand, .headline)).foregroundStyle(WelcomeTheme.color(t.blue))
            }
        }
        .padding(CGFloat(s.large))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(WelcomeTheme.color(t.surface), in: RoundedRectangle(cornerRadius: CGFloat(t.cardRadius)))
    }
}

struct WelcomeBenefits: View {
    private let t = WelcomeTheme.tokens
    var body: some View {
        HStack(alignment: .top, spacing: CGFloat(WelcomeTheme.spacing.space6)) {
            benefit("✓", "Plain-English metrics", t.green, t.mint)
            benefit("↗", "Why stocks move", t.blue, t.blueTint)
            benefit("▣", "Learn as you go", t.purple, t.purpleTint)
        }
    }

    private func benefit(_ symbol: String, _ title: String, _ tint: Int32, _ background: Int32) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(WelcomeTheme.spacing.small)) {
            Text(symbol).font(WelcomeTheme.font(t.explanation, .subheadline)).foregroundStyle(WelcomeTheme.color(tint))
            Text(title).font(WelcomeTheme.font(t.benefit, .caption2)).foregroundStyle(WelcomeTheme.color(t.ink))
        }
        .padding(CGFloat(WelcomeTheme.spacing.space10))
        .frame(maxWidth: .infinity, minHeight: CGFloat(t.benefitHeight), alignment: .topLeading)
        .background(WelcomeTheme.color(background), in: RoundedRectangle(cornerRadius: CGFloat(t.tileRadius)))
    }
}

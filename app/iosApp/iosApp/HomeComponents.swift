import Shared
import SwiftUI

enum HomeTheme {
    static let tokens = HomeTokens.shared
    static let spacing = StockStepsTheme.spacing
    static func color(_ value: Int32) -> Color { StockStepsTheme.color(value) }
    static func font(_ value: ThemeTextStyle, _ role: UIFont.TextStyle = .body) -> Font { StockStepsTheme.font(value, relativeTo: role) }
}

struct HomeChange: View {
    let row: HomeStock
    var index = false
    private let t = HomeTheme.tokens
    var body: some View {
        if row.loading { ProgressView() }
        else if let change = row.change {
            Text(String(format: "%@%.1f%%", change > 0 ? "+" : "", change))
                .font(HomeTheme.font(index ? t.indexChange : t.change))
                .foregroundStyle(HomeTheme.color(change < 0 ? t.negative : (change > 0 ? t.positive : t.muted)))
        } else { Text("—").foregroundStyle(HomeTheme.color(t.muted)) }
    }
}

struct HomeIndexCard: View {
    let row: HomeStock
    private let t = HomeTheme.tokens
    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(HomeTheme.spacing.medium)) {
            Text(row.name ?? row.symbol).font(HomeTheme.font(t.indexLabel, .caption2)).foregroundStyle(HomeTheme.color(t.muted))
            Text(row.price.map { String(format: "$%.2f", $0) } ?? "—")
                .font(HomeTheme.font(t.body)).foregroundStyle(HomeTheme.color(t.ink))
            HomeChange(row: row, index: true)
        }
        .frame(maxWidth: .infinity, minHeight: CGFloat(t.indexHeight) - CGFloat(HomeTheme.spacing.space10) * 2, alignment: .leading)
        .padding(CGFloat(HomeTheme.spacing.space10))
        .background(HomeTheme.color(t.surface), in: RoundedRectangle(cornerRadius: CGFloat(t.cardRadius)))
    }
}

struct HomeStockRow: View {
    let row: HomeStock
    let onExplore: (String) -> Void
    private let t = HomeTheme.tokens
    var body: some View {
        Button { onExplore(row.symbol) } label: {
            HStack {
                VStack(alignment: .leading) {
                    Text(row.symbol).font(HomeTheme.font(t.ticker, .headline)).foregroundStyle(HomeTheme.color(t.ink))
                    Text(row.name ?? (row.error ? "Quote unavailable" : "Saved stock"))
                        .font(HomeTheme.font(t.caption, .caption1)).foregroundStyle(HomeTheme.color(t.muted))
                }
                Spacer()
                VStack(alignment: .trailing) {
                    if let price = row.price { Text(String(format: "$%.2f", price)).font(HomeTheme.font(t.caption, .caption1)).foregroundStyle(HomeTheme.color(t.ink)) }
                    HomeChange(row: row)
                }
            }
            .padding(.horizontal, CGFloat(HomeTheme.spacing.large))
            .padding(.vertical, CGFloat(HomeTheme.spacing.small))
            .frame(minHeight: CGFloat(t.rowHeight))
            .background(HomeTheme.color(t.surface), in: RoundedRectangle(cornerRadius: CGFloat(t.cardRadius)))
        }.buttonStyle(.plain)
    }
}

struct HomeLessonCard: View {
    let onLearn: () -> Void
    private let t = HomeTheme.tokens
    var body: some View {
        Button(action: onLearn) {
            VStack(alignment: .leading, spacing: CGFloat(HomeTheme.spacing.small)) {
                Text("Today's 2-minute lesson").font(HomeTheme.font(t.caption, .caption1)).fontWeight(.semibold).foregroundStyle(HomeTheme.color(t.positive))
                Text("What is a P/E ratio?").font(HomeTheme.font(t.section, .headline)).foregroundStyle(HomeTheme.color(t.ink))
                Text("Learn with a simple example →").font(HomeTheme.font(t.caption, .caption1)).foregroundStyle(HomeTheme.color(t.muted))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(CGFloat(HomeTheme.spacing.large))
            .background(HomeTheme.color(t.lesson), in: RoundedRectangle(cornerRadius: CGFloat(t.lessonRadius)))
        }.buttonStyle(.plain)
    }
}

struct HomeMovers: View {
    let title: String
    let key: String
    let movers: [MarketMover]
    let snapshot: MarketSnapshot
    let onRetry: () -> Void
    let onExplore: (String) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(HomeTheme.spacing.medium)) {
            Text(title).font(HomeTheme.font(HomeTheme.tokens.section, .headline)).foregroundStyle(HomeTheme.color(HomeTheme.tokens.ink))
            if let error = snapshot.errors.first(where: { $0.section == key }) {
                Text(error.error.message).font(HomeTheme.font(HomeTheme.tokens.caption, .caption1)).foregroundStyle(HomeTheme.color(HomeTheme.tokens.muted))
                Button("Retry", action: onRetry)
            } else if movers.isEmpty {
                Text("Nothing available right now.").font(HomeTheme.font(HomeTheme.tokens.caption, .caption1)).foregroundStyle(HomeTheme.color(HomeTheme.tokens.muted))
            }
            ForEach(Array(movers.enumerated()), id: \.offset) { _, mover in
                HomeStockRow(row: HomeStock(symbol: mover.symbol, name: mover.name, change: mover.changePercent?.doubleValue, price: mover.price?.doubleValue), onExplore: onExplore)
            }
        }
    }
}

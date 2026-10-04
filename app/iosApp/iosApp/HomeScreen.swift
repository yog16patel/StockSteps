import SwiftUI
import Shared

struct HomeScreen: View {
    let news: [NewsArticle]
    let newsLoading: Bool
    let newsError: String?
    let onRefreshNews: () -> Void
    let indices: [HomeStock]
    let snapshot: MarketSnapshot?
    let loading: Bool
    let error: String?
    let stocks: [HomeStock]
    let onSearch: () -> Void
    let onExplore: (String) -> Void
    let onRefresh: () -> Void
    @State private var showingLesson = false
    private let t = HomeTheme.tokens
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: CGFloat(t.sectionGap)) {
                VStack(alignment: .leading, spacing: CGFloat(HomeTheme.spacing.extraLarge)) {
                    VStack(alignment: .leading) {
                        Text("Good morning").font(HomeTheme.font(t.title, .title1)).foregroundStyle(HomeTheme.color(t.ink))
                        Text("Markets made simple.").font(HomeTheme.font(t.body)).foregroundStyle(HomeTheme.color(t.muted))
                    }
                    Button(action: onSearch) {
                        Text("Search stocks or companies")
                            .font(HomeTheme.font(t.body)).foregroundStyle(HomeTheme.color(t.muted))
                            .padding(.horizontal, CGFloat(HomeTheme.spacing.extraLarge))
                            .frame(maxWidth: .infinity, minHeight: CGFloat(t.searchHeight), alignment: .leading)
                            .background(HomeTheme.color(t.surface), in: RoundedRectangle(cornerRadius: CGFloat(t.cardRadius)))
                    }.buttonStyle(.plain)
                }
                VStack(alignment: .leading, spacing: CGFloat(HomeTheme.spacing.small)) {
                    section("Market snapshot")
                    if let snapshot {
                        Text(statusText(snapshot.marketStatus)).font(HomeTheme.font(t.caption, .caption1)).foregroundStyle(HomeTheme.color(t.muted))
                        Text("ETF proxies · USD").font(HomeTheme.font(t.caption, .caption1)).foregroundStyle(HomeTheme.color(t.muted))
                    }
                    if loading { ProgressView("Loading market snapshot…") }
                    if let error {
                        Text(error).font(HomeTheme.font(t.caption, .caption1)).foregroundStyle(HomeTheme.color(t.muted))
                        Button("Retry", action: onRefresh)
                    }
                    HStack(spacing: CGFloat(HomeTheme.spacing.tiny)) {
                        ForEach(indices) { HomeIndexCard(row: $0) }
                    }
                    if indices.contains(where: \.error) {
                        HStack {
                            Text("Some index data is unavailable.").font(HomeTheme.font(t.caption, .caption1)).foregroundStyle(HomeTheme.color(t.muted))
                            Spacer()
                            Button("Retry", action: onRefresh)
                        }
                    }
                }
                VStack(alignment: .leading, spacing: CGFloat(HomeTheme.spacing.medium)) {
                    section("Your watchlist")
                    if stocks.isEmpty {
                        VStack(alignment: .leading, spacing: CGFloat(HomeTheme.spacing.small)) {
                            Text("Your next step starts with a stock.").font(HomeTheme.font(t.body)).foregroundStyle(HomeTheme.color(t.ink))
                            Button("Find stocks to follow", action: onSearch)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(CGFloat(HomeTheme.spacing.large))
                        .background(HomeTheme.color(t.surface), in: RoundedRectangle(cornerRadius: CGFloat(t.cardRadius)))
                    }
                    ForEach(stocks) { HomeStockRow(row: $0, onExplore: onExplore) }
                }
                if let snapshot {
                    Text("Updated \(snapshot.lastUpdated)").font(HomeTheme.font(t.caption, .caption1)).foregroundStyle(HomeTheme.color(t.muted))
                    HomeMovers(title: "Top Gainers", key: "gainers", movers: snapshot.gainers, snapshot: snapshot, onRetry: onRefresh, onExplore: onExplore)
                    HomeMovers(title: "Top Losers", key: "losers", movers: snapshot.losers, snapshot: snapshot, onRetry: onRefresh, onExplore: onExplore)
                    HomeMovers(title: "Most Active", key: "mostActive", movers: snapshot.mostActive, snapshot: snapshot, onRetry: onRefresh, onExplore: onExplore)
                }
                HomeNewsSection(articles: news, loading: newsLoading, error: newsError, onRefresh: onRefreshNews)
                HomeLessonCard { showingLesson = true }
            }
            .frame(maxWidth: CGFloat(t.contentWidth))
            .padding(CGFloat(t.screenPadding))
            .frame(maxWidth: .infinity)
        }
        .background(HomeTheme.color(t.background).ignoresSafeArea())
        .refreshable { onRefresh() }
        .sheet(isPresented: $showingLesson) {
            NavigationStack {
                ScrollView {
                    Text("P/E compares a company's share price with its yearly earnings per share.\n\nA $100 share with $5 in yearly earnings per share has a P/E of 20. Investors are paying $20 for each $1 of earnings.\n\nCompare similar companies. A higher or lower P/E alone does not tell you whether a stock is a good investment. Companies with losses may not have a meaningful P/E.")
                        .padding()
                }
                .navigationTitle("What is a P/E ratio?")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showingLesson = false } } }
            }
        }
    }
    private func statusText(_ status: MarketStatus) -> String {
        switch status {
        case .open: "US market open"
        case .closed: "US regular session closed"
        case .preMarket: "Pre-market"
        case .afterHours: "After-hours"
        default: "Market status unavailable"
        }
    }
    private func section(_ title: String) -> some View {
        Text(title).font(HomeTheme.font(t.section, .headline)).foregroundStyle(HomeTheme.color(t.ink))
    }
}

import Shared
import SwiftUI

/// Personal facts in one lazy scroll. Never displays invented holdings or learning progress.
struct HomeScreen: View {
    let state: PersonalDashboard?
    let portfolio: PortfolioUiState?
    let onPortfolio: () -> Void
    let localHour: Int
    let onLearn: () -> Void
    let onSearch: () -> Void
    let onWatchlist: () -> Void
    let onSettings: () -> Void
    let onAlerts: (String?) -> Void
    let onExplore: (String) -> Void
    var onEarnings: (String) -> Void = { _ in }
    let onArticle: (String) -> Void
    let onRefresh: () -> Void
    let onRetryQuotes: () -> Void
    let onRetryNews: () -> Void
    let onRetryWatchlists: () -> Void
    let onRetryAlerts: () -> Void
    let onClearRecent: () -> Void
    let showMockPersonas: Bool
    let onPersona: (String?) -> Void
    @Environment(\.colorScheme) private var scheme
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.sectionGap)) {
                header
                if showMockPersonas {
                    Menu("Sample scenario: \(state?.mockPersona ?? "My saved companies")") {
                        Button("My saved companies") { onPersona(nil) }
                        ForEach(["new-user", "watchlist-only", "portfolio-only", "watchlist-and-portfolio", "learning-only",
                                 "upcoming-earnings", "triggered-alerts", "no-news", "stale-quotes", "partial-failures"], id: \.self) { id in
                            Button(id) { onPersona(id) }
                        }
                    }
                }
                if let state, !state.initializing {
                    VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                        Text(PersonalDashboardRules.shared.greeting(hour: Int32(localHour)))
                            .font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title2))
                        Text("What matters to you today")
                            .font(StockStepsTheme.font(type.small, relativeTo: .subheadline))
                            .foregroundStyle(StockStepsTheme.colors(scheme).textSecondary)
                    }
                    if let portfolio { PortfolioSummaryCard(state: portfolio, onOpen: onPortfolio) }
                    watchlist(state)
                    if !state.brief.isEmpty { facts("Your Daily Brief", state.brief) }
                    if !state.events.isEmpty { facts("Upcoming & alerts", state.events) }
                    if state.alertsError != nil {
                        StockSectionMessage(message: "Alerts unavailable.", actionTitle: "Try again", action: onRetryAlerts)
                    }
                    HomeLearnBanner(action: onLearn)
                    if let notice = state.newsNotice { caption(notice) }
                    if state.watchlistCount > 0 { news(state) }
                    if !state.recent.isEmpty { recentlyViewed(state) }
                } else {
                    ProgressView("Restoring your dashboard")
                }
            }
            .frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
            .frame(maxWidth: .infinity)
        }
        .foregroundStyle(StockStepsTheme.colors(scheme).textPrimary)
        .background(StockStepsTheme.colors(scheme).appBackground.ignoresSafeArea())
        .refreshable { onRefresh() }
    }

    private var header: some View {
        HStack(spacing: 0) {
            StockBrandMark(name: "StockSteps")
            Spacer(minLength: 0)
            headerButton("Search stocks", icon: "magnifyingglass", action: onSearch)
            headerButton("Your alerts", icon: "bell", action: { onAlerts(nil) })
            headerButton("Profile and settings", icon: "person.crop.circle", action: onSettings)
        }
    }
    private func headerButton(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .frame(minWidth: CGFloat(StockStepsTheme.dimensions.rowCompactMinHeight), minHeight: CGFloat(StockStepsTheme.dimensions.rowCompactMinHeight))
        }.accessibilityLabel(title)
    }

    private func watchlist(_ state: PersonalDashboard) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            HStack {
                sectionTitle("Watchlist highlights")
                Spacer()
                Button("View all", action: onWatchlist).frame(minHeight: CGFloat(StockStepsTheme.dimensions.rowCompactMinHeight))
            }
            if state.watchlistLoading && state.watchlistCount == 0 {
                StockRowSkeleton()
            } else if state.watchlistError != nil && state.watchlistCount == 0 {
                StockSectionMessage(message: state.watchlistError!, actionTitle: "Try again", action: onRetryWatchlists)
            } else if state.watchlistCount == 0 {
                Text("Start with a company you know. Save it to follow its price and news.")
                    .font(StockStepsTheme.font(type.small, relativeTo: .subheadline))
                Button("Find stocks", action: onSearch).frame(minHeight: CGFloat(StockStepsTheme.dimensions.rowCompactMinHeight))
            } else {
                ForEach(state.highlights, id: \.instrument.symbol) { highlight in
                    StockRow(model: highlight.row, horizontalPadding: 0) { onExplore(highlight.instrument.symbol) }
                    if highlight.stale { caption("\(highlight.instrument.symbol) · Saved or delayed price") }
                }
                if state.quotesLoading { ProgressView() }
                if let error = state.quotesError { StockSectionMessage(message: error, actionTitle: "Try again", action: onRetryQuotes) }
                if state.watchlistError != nil { StockSectionMessage(message: "Showing your saved watchlist.", actionTitle: "Try again", action: onRetryWatchlists) }
                if let notice = state.quoteNotice { caption(notice) }
            }
        }.stockCard(bordered: false)
    }

    private func facts(_ title: String, _ values: [HomeFact]) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            sectionTitle(title)
            ForEach(values, id: \.id) { fact in
                Button {
                    if fact.alerts { onAlerts(fact.symbol) } else if fact.earnings { onEarnings(fact.symbol) } else { onExplore(fact.symbol) }
                } label: {
                    VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                        Text(fact.title).font(StockStepsTheme.font(type.bodySemiBold))
                        caption(fact.detail)
                    }
                    .frame(maxWidth: .infinity, minHeight: CGFloat(StockStepsTheme.dimensions.rowCompactMinHeight), alignment: .leading)
                }.buttonStyle(.plain)
            }
        }.stockCard(bordered: false)
    }

    private func news(_ state: PersonalDashboard) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            sectionTitle("News for you")
            if state.newsLoading && state.stories.isEmpty { StockNewsCardSkeleton() }
            ForEach(state.stories, id: \.article.url) { story in
                caption(story.symbol)
                StockNewsCard(model: NewsPresentation.shared.model(article: story.article)) { _ in onArticle(story.article.url) }
            }
            if let error = state.newsError { StockSectionMessage(message: error, actionTitle: "Try again", action: onRetryNews) }
            if !state.newsLoading && state.newsError == nil && state.stories.isEmpty {
                caption("No recent company news for your watchlist.")
            }
        }.stockCard(bordered: false)
    }

    private func recentlyViewed(_ state: PersonalDashboard) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            HStack {
                sectionTitle("Recently viewed")
                Spacer()
                Button("Clear", action: onClearRecent).frame(minHeight: CGFloat(StockStepsTheme.dimensions.rowCompactMinHeight))
            }
            ForEach(state.recent, id: \.instrument.symbol) { recent in
                StockRow(symbol: recent.instrument.symbol, name: recent.instrument.name, horizontalPadding: 0,
                    action: { onExplore(recent.instrument.symbol) }) { EmptyView() }
            }
        }.stockCard(bordered: false)
    }
    private func sectionTitle(_ text: String) -> some View {
        Text(text).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline))
            .accessibilityAddTraits(.isHeader)
    }
    private func caption(_ text: String) -> some View {
        Text(text).font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
            .foregroundStyle(StockStepsTheme.colors(scheme).textSecondary)
    }
}

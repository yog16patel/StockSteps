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
    /// The most recently visited unfinished Guided Research (saved progress), if any.
    var research: ResearchProgress? = nil
    var onResearch: (ResearchTarget) -> Void = { _ in }
    var onPractice: () -> Void = {}
    /// The shared Daily Market Brief (compact preview only).
    var briefModel: BriefModel? = nil
    var onDailyBrief: () -> Void = {}
    @Environment(\.colorScheme) private var scheme
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography

    var body: some View {
        ScrollView {
            // KAN-14: open sections need more air than bordered cards did; Phase 8A names that gap (pageSectionGap, shared with Markets).
            LazyVStack(alignment: .leading, spacing: CGFloat(space.pageSectionGap)) {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    header
                    Text(PersonalDashboardRules.shared.greeting(hour: Int32(localHour)))
                        .font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title2))
                        .foregroundStyle(StockStepsTheme.colors(scheme).textTitle)
                        .accessibilityAddTraits(.isHeader)
                    // Phase 8A: the greeting alone (the "What matters to you today" line repeated the page's purpose before any data).
                }
                // Market first (Phase 4A): the brief's index snapshot and its entry share one group.
                if let briefModel { HomeMarketOverview(model: briefModel, onOpenBrief: onDailyBrief) }
                if let state, !state.initializing {
                    if let portfolio { HomePortfolioSummary(state: portfolio, onOpen: onPortfolio) }
                    watchlist(state)
                    if !state.brief.isEmpty { facts("Today in your watchlist", state.brief) }
                    if !state.events.isEmpty || state.alertsError != nil { facts("Upcoming & alerts", state.events, alertsError: state.alertsError != nil) }
                    // Learn and practice: a quieter second tier. Continue Learning shows only real saved progress.
                    HomeSection(title: "Learn and practice") {
                        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                        if let research {
                            HomeLearnBanner(action: { onResearch(ResearchTarget(symbol: research.symbol, name: research.name)) },
                                            title: "Continue learning",
                                            message: "Researching \(research.name) · \(research.completedCount) of 5 steps completed",
                                            hint: "Continue researching \(research.name)")
                        } else {
                            HomeLearnBanner(action: onLearn)
                        }
                        PracticeEntryCard(message: "Practice investing with virtual money. No real money is used.", action: onPractice)
                        }
                    }
                    if state.watchlistCount > 0 { news(state) } else if state.mockPersona != nil, let notice = state.newsNotice { caption(notice) }
                    if !state.recent.isEmpty { recentlyViewed(state) }
                    if showMockPersonas { personaPicker(state) }
                } else {
                    // KAN-14: first load shows row placeholders (as on Android), never a full-width spinner.
                    VStack(spacing: 0) { ForEach(0..<3, id: \.self) { _ in StockRowSkeleton() } }
                        .accessibilityElement(children: .ignore).accessibilityLabel("Restoring your dashboard")
                        .homeRowGroup(inset: 0)
                }
            }
            .frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.top, CGFloat(space.md))
            .padding(.bottom, CGFloat(space.xl))
            .frame(maxWidth: .infinity)
        }
        .scrollsToTopOnReselect(.home) // 5D.5.6C2 (D12)
        .foregroundStyle(StockStepsTheme.colors(scheme).textPrimary)
        .background(StockStepsTheme.colors(scheme).appBackground.ignoresSafeArea())
        .refreshable { onRefresh() }
    }

    /// MOCK only: picks a sample Home scenario. Kept at the end so it never pushes personal content down.
    private func personaPicker(_ state: PersonalDashboard) -> some View {
        HStack(spacing: CGFloat(space.xs)) {
            StockStatusBadge(text: "Sample", kind: .sample, size: .compact)
            Menu("Scenario: \(state.mockPersona ?? "My saved companies")") {
                Button("My saved companies") { onPersona(nil) }
                ForEach(["new-user", "watchlist-only", "portfolio-only", "watchlist-and-portfolio", "learning-only",
                         "upcoming-earnings", "triggered-alerts", "no-news", "stale-quotes", "partial-failures"], id: \.self) { id in
                    Button(id) { onPersona(id) }
                }
            }
            .font(StockStepsTheme.font(type.label))
            .foregroundStyle(StockStepsTheme.colors(scheme).primaryText)
            .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
        }
    }

    /// 5D.5.6B: the shared landing bar — brand, Search, then Notifications (the alert history; no unread contract, so no badge) and Settings.
    private var header: some View {
        StockLandingTopBar(tab: .home, actionCount: 1, onNotifications: { onAlerts(nil) }, onSettings: onSettings) {
            StockLandingIconButton(systemImage: "magnifyingglass", label: LandingTopBarDisplay.shared.SEARCH_STOCKS_SPOKEN, action: onSearch)
        }
    }
    private func headerButton(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .frame(minWidth: CGFloat(StockStepsTheme.dimensions.rowCompactMinHeight), minHeight: CGFloat(StockStepsTheme.dimensions.rowCompactMinHeight))
        }.accessibilityLabel(title)
    }

    /// KAN-14: an open section; saved companies sit in one borderless row group, the empty state is plain text and a button.
    private func watchlist(_ state: PersonalDashboard) -> some View {
        let colors = StockStepsTheme.colors(scheme)
        let inset = CGFloat(space.cardPadding)
        return HomeSection(title: "Watchlist highlights", actionTitle: "View all", action: onWatchlist) {
            if state.watchlistLoading && state.watchlistCount == 0 {
                StockRowSkeleton().accessibilityElement(children: .ignore).accessibilityLabel("Loading").homeRowGroup(inset: 0)
            } else if state.watchlistError != nil && state.watchlistCount == 0 {
                StockSectionMessage(message: state.watchlistError!, actionTitle: "Try again", action: onRetryWatchlists)
            } else if state.watchlistCount == 0 {
                VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                    Text("Start with a company you know. Save it to follow its price and news.")
                        .font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                    Button(action: onSearch) { Label("Find stocks", systemImage: "magnifyingglass") }.buttonStyle(.stockSecondary)
                }
            } else {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    ForEach(Array(state.highlights.enumerated()), id: \.element.instrument.symbol) { n, highlight in
                        if n > 0 { StockDivider().padding(.leading, inset) }
                        StockRow(model: highlight.row) { onExplore(highlight.instrument.symbol) }
                        if highlight.stale {
                            Text("\(highlight.instrument.symbol) · Saved or delayed price").font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                                .foregroundStyle(colors.cautionText).padding(.horizontal, inset)
                        }
                    }
                    Group {
                        // Refreshing prices keeps the rows visible; only this thin bar shows the update (as on Android).
                        if state.quotesLoading { ProgressView().progressViewStyle(.linear).accessibilityLabel("Updating prices") }
                        if let error = state.quotesError { StockSectionMessage(message: error, actionTitle: "Try again", action: onRetryQuotes) }
                        if state.watchlistError != nil { StockSectionMessage(message: "Showing your saved watchlist.", actionTitle: "Try again", action: onRetryWatchlists) }
                        if let notice = state.quoteNotice { caption(notice).padding(.bottom, CGFloat(space.xs)) }
                    }.padding(.horizontal, inset)
                }
                .homeRowGroup(inset: 0)
            }
        }
    }

    /// Personal facts as plain tappable rows (title, muted detail, chevron); a failed alerts load is reported inside the same group.
    private func facts(_ title: String, _ values: [HomeFact], alertsError: Bool = false) -> some View {
        HomeSection(title: title) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(values.enumerated()), id: \.element.id) { n, fact in
                    if n > 0 { StockDivider() }
                    HomeLinkRow(title: fact.title, detail: fact.detail) {
                        if fact.alerts { onAlerts(fact.symbol) } else if fact.earnings { onEarnings(fact.symbol) } else { onExplore(fact.symbol) }
                    }
                }
                if alertsError { StockSectionMessage(message: "Alerts unavailable.", actionTitle: "Try again", action: onRetryAlerts) }
            }
            .homeRowGroup()
        }
    }

    /// KAN-14: stories in one borderless group, each under its company label and divided from the next (no card per story).
    private func news(_ state: PersonalDashboard) -> some View {
        let colors = StockStepsTheme.colors(scheme)
        return HomeSection(title: "News for you") {
            if let notice = state.newsNotice { caption(notice) }
            if !state.stories.isEmpty || state.newsLoading {
                VStack(alignment: .leading, spacing: 0) {
                    if state.newsLoading && state.stories.isEmpty { StockNewsCardSkeleton() }
                    ForEach(Array(state.stories.enumerated()), id: \.element.article.url) { n, story in
                        if n > 0 { StockDivider() }
                        // The company is context for the story, so it reads as a muted label rather than a link-coloured one.
                        Text(story.symbol).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSupporting)
                            .padding(.top, CGFloat(space.sm))
                        StockNewsCard(model: NewsPresentation.shared.model(article: story.article)) { _ in onArticle(story.article.url) }
                    }
                }
                .homeRowGroup()
            }
            if let error = state.newsError { StockSectionMessage(message: error, actionTitle: "Try again", action: onRetryNews) }
            if !state.newsLoading && state.newsError == nil && state.stories.isEmpty {
                caption("No recent company news for your watchlist.")
            }
        }
    }

    private func recentlyViewed(_ state: PersonalDashboard) -> some View {
        HomeSection(title: "Recently viewed", actionTitle: "Clear", action: onClearRecent) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(state.recent, id: \.instrument.symbol) { recent in
                    StockRow(symbol: recent.instrument.symbol, name: recent.instrument.name, compact: true,
                        action: { onExplore(recent.instrument.symbol) }) { EmptyView() }
                }
            }
            .homeRowGroup(inset: 0)
        }
    }
    private func caption(_ text: String) -> some View {
        Text(text).font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
            .foregroundStyle(StockStepsTheme.colors(scheme).textSecondary)
    }
}

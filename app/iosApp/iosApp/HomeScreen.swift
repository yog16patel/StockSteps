import Shared
import SwiftUI

/// Beginner order: greeting, market, movers, learn, news. Sections load and fail independently.
/// Search and the watchlist live in their own destinations.
struct HomeScreen: View {
    @Environment(\.colorScheme) private var scheme
    let signedIn: Bool
    let market: MarketSnapshotUiModel
    let movers: MoversUiModel
    let newsStatus: SectionStatus
    let news: [NewsUiModel]
    let onSelectMovers: (MoverCategory) -> Void
    let onOpenStock: (String) -> Void
    let onLearn: () -> Void
    let onOpenArticle: (URL) -> Void
    let onRetryMarket: () -> Void
    let onRetryNews: () -> Void
    /// "View All" renders only when its destination exists; Markets and News screens are pending.
    var onViewAllMovers: (() -> Void)?
    var onViewAllNews: (() -> Void)?
    private let space = StockStepsTheme.spacing

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                HomeHeader(signedIn: signedIn)
                MarketSummarySection(market: market, onRetry: onRetryMarket)
                    .padding(.top, CGFloat(space.md))
                MoversSection(movers: movers, onSelect: onSelectMovers, onOpenStock: onOpenStock, onRetry: onRetryMarket, onViewAll: onViewAllMovers)
                    .padding(.top, CGFloat(space.sectionGap))
                HomeLearnBanner(action: onLearn)
                    .padding(.top, CGFloat(space.sectionGap))
                HomeNewsSection(status: newsStatus, news: news, onOpenArticle: onOpenArticle, onRetry: onRetryNews, onViewAll: onViewAllNews)
                    .padding(.top, CGFloat(space.sectionGap))
            }
            .frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.top, CGFloat(space.sm))
            .padding(.bottom, CGFloat(space.lg))
            .frame(maxWidth: .infinity)
        }
        .background(StockStepsTheme.colors(scheme).appBackground.ignoresSafeArea())
        .refreshable { onRetryMarket(); onRetryNews() }
    }
}

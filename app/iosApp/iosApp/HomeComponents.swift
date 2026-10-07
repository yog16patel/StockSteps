import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// Brand row, then greeting and tagline. No bell: notifications do not exist yet.
struct HomeHeader: View {
    @Environment(\.colorScheme) private var scheme
    let signedIn: Bool
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: 0) {
            StockBrandMark(name: "StockSteps")
            Text(signedIn ? "Welcome back!" : "Welcome to StockSteps!")
                .font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3).bold())
                .foregroundStyle(colors.textPrimary)
                .padding(.top, CGFloat(space.sm))
            Text("Learn · Explore · Grow")
                .font(StockStepsTheme.font(type.label, relativeTo: .footnote).weight(.regular))
                .foregroundStyle(colors.textSecondary)
                .padding(.top, CGFloat(space.xxs))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// "Market" header with the backend status, two compact index cards, and the ETF-proxy disclosure.
struct MarketSummarySection: View {
    @Environment(\.colorScheme) private var scheme
    let market: MarketSnapshotUiModel
    let onRetry: () -> Void
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            StockSectionHeader(title: "Market") { MarketStatusIndicator(status: market.marketStatus) }
            if market.status == .error {
                StockSectionMessage(message: "Market data isn't available right now.", actionTitle: "Try again", action: onRetry).stockCard()
            } else {
                HStack(spacing: CGFloat(space.sm)) {
                    ForEach(market.indices, id: \.symbol) { card($0, colors) }
                }
                Text("ETF prices (\(market.indices.map(\.symbol).joined(separator: ", "))) in USD")
                    .font(StockStepsTheme.font(type.tiny, relativeTo: .caption2))
                    .foregroundStyle(colors.textTertiary)
                if market.partiallyUnavailable {
                    StockSectionMessage(message: "Some market values aren't available right now.", actionTitle: "Try again", action: onRetry)
                }
            }
        }
    }

    private func card(_ index: MarketIndexUiModel, _ colors: StockColors) -> some View {
        let loading = market.status == .loading
        return VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            HStack(spacing: CGFloat(space.xs)) {
                Text(index.name).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary).lineLimit(1)
                Spacer(minLength: 0)
                if loading { StockSkeleton(width: 48) } else {
                    StockPriceChange(percentage: index.change, direction: index.direction, style: type.numberLabelStrong)
                }
            }
            if loading { StockSkeleton(width: 96).accessibilityLabel("Loading") } else {
                Text(index.price ?? "—").font(StockStepsTheme.font(type.numberEmphasis, relativeTo: .headline))
                    .foregroundStyle(index.price == nil ? colors.textTertiary : colors.textPrimary).lineLimit(1)
            }
        }
        .stockCard(padding: CGFloat(space.sm))
        .accessibilityElement(children: .combine)
    }
}

/// One section surface: header, filter chips and up to three flat rows (no per-row cards).
/// "View All" appears only when a destination exists (`onViewAll` non-nil).
struct MoversSection: View {
    let movers: MoversUiModel
    let onSelect: (MoverCategory) -> Void
    let onOpenStock: (String) -> Void
    let onRetry: () -> Void
    var onViewAll: (() -> Void)?
    private let categories: [(MoverCategory, String)] = [(.gainers, "Top Gainers"), (.losers, "Top Losers"), (.mostActive, "High Volume")]
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            StockSectionHeader(title: "Today's Movers", actionTitle: onViewAll == nil ? nil : "View All", action: onViewAll)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: CGFloat(space.sm)) {
                    ForEach(categories, id: \.1) { category, title in
                        StockChip(title: title, selected: movers.category == category) { onSelect(category) }
                    }
                }
            }
            switch movers.status {
            case .loading:
                ForEach(0..<3, id: \.self) { _ in StockRowSkeleton(compact: true, horizontalPadding: 0) }
                    .accessibilityElement(children: .ignore).accessibilityLabel("Loading")
            case .error:
                StockSectionMessage(message: "Market movers aren't available right now.", actionTitle: "Try again", action: onRetry)
            case .empty:
                StockSectionMessage(message: "No movers to show right now.")
            default:
                ForEach(Array(movers.rows.enumerated()), id: \.element.symbol) { offset, row in
                    if offset > 0 { StockDivider(inset: CGFloat(dims.logoCompact + space.sm)) }
                    StockRow(model: row, compact: true, horizontalPadding: 0) { onOpenStock(row.symbol) }
                }
            }
        }
        .stockCard()
    }
}

/// One section surface with up to three compact news rows separated by dividers.
struct HomeNewsSection: View {
    let status: SectionStatus
    let news: [NewsUiModel]
    let onOpenArticle: (URL) -> Void
    let onRetry: () -> Void
    var onViewAll: (() -> Void)?
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            StockSectionHeader(title: "Recent News", actionTitle: onViewAll == nil ? nil : "View All", action: onViewAll)
            switch status {
            case .loading:
                ForEach(0..<2, id: \.self) { _ in StockNewsCardSkeleton() }
            case .error:
                StockSectionMessage(message: "News isn't available right now.", actionTitle: "Try again", action: onRetry)
            case .empty:
                StockSectionMessage(message: "No recent market news.")
            default:
                ForEach(Array(news.enumerated()), id: \.element.id) { offset, article in
                    if offset > 0 { StockDivider() }
                    StockNewsCard(model: article, onOpen: onOpenArticle)
                }
            }
        }
        .stockCard()
    }
}

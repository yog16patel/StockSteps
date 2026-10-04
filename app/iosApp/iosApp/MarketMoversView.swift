import Shared
import SwiftUI

struct MarketMoversView: View {
    private let previewCount = 5
    let title: String
    let feed: DiscoveryFeed<MarketMover>
    let onRetry: () -> Void
    let onExplore: (String) -> Void

    var body: some View {
        Section(title) {
            DiscoveryFeedStatus(feed: feed, onRetry: onRetry)
            ForEach(Array(feed.items.prefix(previewCount).enumerated()), id: \.offset) { _, mover in
                Button { onExplore(mover.symbol) } label: {
                    VStack(alignment: .leading) {
                        Text(mover.symbol).font(.headline)
                        if let name = mover.name { Text(name) }
                        Text("\(mover.changePercent.map { $0.doubleValue.formatted() } ?? "Unavailable")% · Price \(mover.price.map { $0.doubleValue.formatted() } ?? "Unavailable")")
                            .font(.subheadline)
                    }
                }.buttonStyle(.plain)
            }
        }
    }
}

struct DiscoveryFeedStatus<Item>: View {
    let feed: DiscoveryFeed<Item>
    let onRetry: () -> Void

    var body: some View {
        if feed.isLoading { ProgressView("Loading…") }
        if let error = feed.error {
            Text(error)
            Button("Retry", action: onRetry).stockStepsGlassButton()
        }
        if !feed.isLoading && feed.error == nil && feed.items.isEmpty {
            Text("Nothing available right now.")
        }
    }
}

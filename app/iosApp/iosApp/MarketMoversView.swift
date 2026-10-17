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
                        // Phase 8B: shared formatter — missing change and price in words, a real zero as 0.00% (was "Unavailable%").
                        Text(verbatim: MarketMoverDisplay.shared.line(mover: mover))
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

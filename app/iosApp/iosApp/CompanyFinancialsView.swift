import Shared
import SwiftUI

struct CompanyFinancialsSectionView: View {
    let section: FinancialSectionState
    let onRetry: () -> Void
    let onInfo: (String) -> Void
    @State private var expanded = false
    @State private var availableWidth: CGFloat = 0
    @Environment(\.dynamicTypeSize) private var typeSize
    private let spacing = CGFloat(StockStepsTheme.spacing.large)
    private var columns: [GridItem] {
        let count = availableWidth >= CGFloat(FinancialsTokens.shared.twoColumnMinWidth) && !typeSize.isAccessibilitySize ? 2 : 1
        return Array(repeating: GridItem(.flexible(), spacing: CGFloat(StockStepsTheme.spacing.medium)), count: count)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) {
            Text(section.title).font(.title3.bold()).foregroundStyle(.primary)
            switch section.status {
            case .loading:
                LazyVGrid(columns: columns, spacing: CGFloat(StockStepsTheme.spacing.medium)) {
                    ForEach(0..<2) { _ in FinancialSkeletonCard() }
                }
            case .success:
                if section.cashRelationship {
                    VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.small)) {
                        ForEach(Array(section.metrics.filter { $0.id != "fcfMargin" }.enumerated()), id: \.element.id) { index, tile in
                            if index > 0 {
                                Text(index == 1 ? "↓ Less capital spending" : "↓ Leaves free cash flow").font(.footnote).foregroundStyle(.secondary)
                            }
                            FinancialValueCard(tile: tile) { onInfo(tile.id) }
                        }
                        ForEach(section.metrics.filter { $0.id == "fcfMargin" }, id: \.id) { tile in
                            FinancialValueCard(tile: tile) { onInfo(tile.id) }
                        }
                    }
                } else {
                    metricGrid(section.metrics)
                }
                ForEach(section.insights, id: \.self) { Text($0).font(.subheadline).foregroundStyle(.primary) }
                if let explanation = section.explanation {
                    Text("What this means").font(.subheadline.bold()).foregroundStyle(.blue)
                    Text(explanation).font(.subheadline).foregroundStyle(.secondary)
                }
                if !section.secondary.isEmpty {
                    DisclosureGroup(isExpanded: $expanded) {
                        metricGrid(section.secondary).padding(.top, spacing)
                    } label: {
                        Text(section.title == "Growth" ? "View growth history" : "View more metrics").font(.subheadline)
                    }.tint(.blue)
                }
            default:
                VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.small)) {
                    Text(section.message ?? "These financial details aren't available right now.").font(.subheadline)
                    if let explanation = section.explanation { Text(explanation).font(.footnote).foregroundStyle(.secondary) }
                    Button("Try again", action: onRetry).tint(.blue).stockStepsGlassButton()
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(spacing)
                .background(.background, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.medium)))
            }
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { availableWidth = $0 }
    }
    private func metricGrid(_ metrics: [FinancialTile]) -> some View {
        LazyVGrid(columns: columns, alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.medium)) {
            ForEach(metrics, id: \.id) { tile in FinancialValueCard(tile: tile) { onInfo(tile.id) } }
        }
    }
}

private struct FinancialValueCard: View {
    @Environment(\.colorScheme) private var scheme
    let tile: FinancialTile
    let onInfo: () -> Void
    var body: some View {
        Button(action: onInfo) {
            VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.small)) {
                Text(tile.label).font(.subheadline).foregroundStyle(.secondary)
                Text(tile.value).font(.title2.bold()).foregroundStyle(.primary)
                if let movement = tile.movement { Text(movement).font(.subheadline).foregroundStyle(movementColor) }
                if let report = tile.reportingPeriod { Text(report).font(.caption2).foregroundStyle(.secondary) }
                Text("Learn what this means").font(.caption2).foregroundStyle(.blue)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(CGFloat(StockStepsTheme.spacing.large))
            .background(.background, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.medium)))
        }
        .buttonStyle(.plain)
    }
    private var movementColor: Color {
        let palette = StockStepsTheme.palette(scheme)
        switch tile.direction {
        case .up: return StockStepsTheme.color(palette.positive)
        case .down: return StockStepsTheme.color(palette.negative)
        default: return .secondary
        }
    }
}

private struct FinancialSkeletonCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.medium)) {
            RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.small)).frame(width: CGFloat(FinancialsTokens.shared.skeletonLabelWidth), height: CGFloat(FinancialsTokens.shared.skeletonLineHeight))
            RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.small)).frame(width: CGFloat(FinancialsTokens.shared.skeletonValueWidth), height: CGFloat(FinancialsTokens.shared.skeletonValueHeight))
            RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.small)).frame(width: CGFloat(FinancialsTokens.shared.skeletonContextWidth), height: CGFloat(FinancialsTokens.shared.skeletonLineHeight))
        }
        .foregroundStyle(.quaternary)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(CGFloat(StockStepsTheme.spacing.large))
        .background(.background, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.medium)))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading financial values")
    }
}

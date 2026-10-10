import Shared
import SwiftUI

/// News item shared by Home and later news surfaces. `compact` is a borderless row
/// (thumbnail, two-line headline, source/time); the full card adds summary and "Why it matters".
struct StockNewsCard: View {
    @Environment(\.colorScheme) private var scheme
    let model: NewsUiModel
    var compact = true
    let onOpen: (URL) -> Void
    private let type = StockStepsTheme.typography
    private let space = StockStepsTheme.spacing
    private let dims = StockStepsTheme.dimensions

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let url = model.url.flatMap(URL.init(string:))
        Button { if let url { onOpen(url) } } label: {
            Group {
                if compact { compactRow(colors) } else { fullCard(colors) }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(url == nil)
        .accessibilityElement(children: .combine)
        .accessibilityHint(url == nil ? "" : "Opens the original article")
    }

    private func compactRow(_ colors: StockColors) -> some View {
        HStack(spacing: CGFloat(space.md)) {
            thumbnail(colors, size: CGFloat(dims.newsThumbnailCompact), placeholderWhenMissing: true)
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                Text(model.headline)
                    .font(StockStepsTheme.font(type.bodyMedium))
                    .foregroundStyle(colors.textPrimary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                meta(colors, compact: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, CGFloat(space.sm))
        .frame(minHeight: CGFloat(dims.touchTarget))
    }

    private func fullCard(_ colors: StockColors) -> some View {
        HStack(alignment: .top, spacing: CGFloat(space.md)) {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                meta(colors, compact: false)
                Text(model.headline).font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.textPrimary)
                if let summary = model.summary {
                    Text(summary).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                }
                if let why = model.whyItMatters {
                    Text("Why it matters").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textPrimary)
                    Text(why).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            thumbnail(colors, size: CGFloat(dims.newsThumbnail))
        }
        .stockCard()
    }

    /// Photography is cropped; a missing or failed image leaves a neutral tile, never a broken icon.
    @ViewBuilder
    private func thumbnail(_ colors: StockColors, size: CGFloat, placeholderWhenMissing: Bool = false) -> some View {
        if placeholderWhenMissing && model.imageUrl == nil {
            RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip)).fill(colors.surfaceSecondary)
                .frame(width: size, height: size).accessibilityHidden(true)
        } else if let image = model.imageUrl.flatMap(URL.init(string:)) {
            AsyncImage(url: image) { $0.resizable().scaledToFill() } placeholder: { colors.surfaceSecondary }
                .frame(width: size, height: size)
                .clipShape(RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip)))
                .accessibilityHidden(true)
        }
    }

    @ViewBuilder
    private func meta(_ colors: StockColors, compact: Bool) -> some View {
        let text = [model.source, model.publishedLabel].compactMap { $0 }.joined(separator: " · ")
        if !text.isEmpty || model.aiSimplified {
            HStack(spacing: CGFloat(space.sm)) {
                if !text.isEmpty {
                    Text(text).font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                        .foregroundStyle(colors.textMeta).lineLimit(1)
                }
                if model.aiSimplified {
                    StockStatusBadge(text: "Simplified by AI", kind: .info, size: .compact)
                }
            }
        }
    }
}

struct StockNewsCardSkeleton: View {
    var body: some View {
        HStack(spacing: CGFloat(StockStepsTheme.spacing.md)) {
            StockSkeleton(width: CGFloat(StockStepsTheme.dimensions.newsThumbnailCompact), height: CGFloat(StockStepsTheme.dimensions.newsThumbnailCompact))
            VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.sm)) {
                StockSkeleton(width: 220)
                StockSkeleton(width: 90)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, CGFloat(StockStepsTheme.spacing.sm))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading")
    }
}

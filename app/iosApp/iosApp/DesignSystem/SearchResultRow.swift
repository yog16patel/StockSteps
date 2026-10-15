import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// One company search result (Phase 5D.5.5B, as Compose `StockSearchResultRow`): the company logo when it loads, else ticker initials, the name (wraps, never truncated)
/// with an optional "Best match" badge, then ticker · exchange (never truncated — they tell listings apart), and a chevron. Borderless; the
/// list draws dividers. One spoken element ("Apple Inc., AAPL, NASDAQ, best match") with the button trait. From AX3 the avatar goes above
/// the text and ticker and exchange take their own lines.
struct SearchResultRow: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    let result: StockSearchResult
    let best: Bool
    var hint: String? = nil
    /// 5D.5.5C (Comparison): false dims the row and disables it; `note` ("Already added") is shown and spoken after the identity line.
    var enabled = true
    var note: String? = nil
    let action: () -> Void

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let stacked = typeSize >= .accessibility3
        Button(action: action) {
            HStack(alignment: .center, spacing: CGFloat(space.md)) {
                if stacked {
                    VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                        StockTickerAvatar(symbol: result.symbol, logoUrl: result.logoUrl)
                        text(colors, stacked: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    StockTickerAvatar(symbol: result.symbol, logoUrl: result.logoUrl)
                    text(colors, stacked: false).frame(maxWidth: .infinity, alignment: .leading)
                }
                if enabled { Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(colors.iconSecondary) }
            }
            .padding(.vertical, CGFloat(space.sm))
            .frame(minHeight: CGFloat(dims.touchTarget))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([SearchDisplay.shared.spoken(result: result, best: best), note].compactMap { $0 }.joined(separator: ", "))
        .accessibilityAddTraits(.isButton)
        .accessibilityHint(hint ?? "")
    }

    private var badge: some View { StockStatusBadge(text: SearchDisplay.shared.BEST_MATCH, kind: .info, size: .compact) }

    @ViewBuilder
    private func text(_ colors: StockColors, stacked: Bool) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            // The badge follows the name while both fit on one line (as Compose), otherwise it goes under the name; the name wraps.
            let name = Text(verbatim: result.name).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(enabled ? colors.textPrimary : colors.textTertiary)
            if best {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: CGFloat(space.sm)) { name; badge }
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { name.fixedSize(horizontal: false, vertical: true); badge }
                }
            } else {
                name.fixedSize(horizontal: false, vertical: true)
            }
            if stacked {
                Text(verbatim: result.symbol).font(StockStepsTheme.font(type.small).weight(.semibold)).foregroundStyle(colors.textSecondary)
                if let exchange = result.exchange, !exchange.isEmpty {
                    Text(verbatim: exchange).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                }
            } else {
                Text(verbatim: SearchDisplay.shared.identity(result: result)).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
            }
            if let note { Text(verbatim: note).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary) }
        }
    }
}

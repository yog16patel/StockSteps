import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// One company search result (Phase 5D.5.5B, as Compose `StockSearchResultRow`): the company logo when it loads, else ticker initials, the name (wraps, never truncated),
/// then ticker · exchange (never truncated — they tell listings apart), and a chevron. Borderless; the list draws dividers. One spoken element
/// ("Apple Inc., AAPL, NASDAQ, best match") with the button trait. From AX3 ticker and exchange take their own lines; the logo stays on the left.
/// Typography & Spacing Phase 5 (as Compose): name `listTitle` (16), ticker · exchange `small` (14), a 40 pt logo, 12 pt above and below
/// (the list adds no row inset, `searchListRow`) and 4 pt between lines. No "Best match" badge: the section heading says it (`best` is still spoken).
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
                // 5D.5.5D (owner reference): the logo stays beside the text at every size; from AX3 only ticker and exchange stack.
                StockTickerAvatar(symbol: result.symbol, logoUrl: result.logoUrl, size: CGFloat(dims.logoList))
                text(colors, stacked: stacked).frame(maxWidth: .infinity, alignment: .leading)
                if enabled { Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(colors.iconSecondary) }
            }
            .padding(.vertical, CGFloat(space.listRowVertical))
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

    @ViewBuilder
    private func text(_ colors: StockColors, stacked: Bool) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.listTitleSubtitle)) {
            Text(verbatim: result.name).font(StockStepsTheme.font(type.listTitle)).foregroundStyle(enabled ? colors.textPrimary : colors.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
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

import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// One company search result (Phase 5D.5.5B, as Compose `StockSearchResultRow`): the company logo when it loads, else ticker initials, the name (wraps, never truncated)
/// with an optional "Best match" badge, then ticker · exchange (never truncated — they tell listings apart), and a chevron. Borderless; the
/// list draws dividers. One spoken element ("Apple Inc., AAPL, NASDAQ, best match") with the button trait. From AX3 ticker and exchange take
/// their own lines; the logo stays on the left.
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
                StockTickerAvatar(symbol: result.symbol, logoUrl: result.logoUrl)
                text(colors, stacked: stacked).frame(maxWidth: .infinity, alignment: .leading)
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

    /// The "Best match" label (5D.5.5D): it grows with Dynamic Type only up to 1.3× its default size, so it never outweighs the company name
    /// (as Compose's `SEARCH_BADGE_MAX_SCALE`); name, ticker and exchange keep the full user size, and the label is also spoken.
    private var badge: some View {
        let tone = StockSemanticStyles.shared.badge(kind: .info, p: StockStepsTheme.palette(scheme))
        let base = CGFloat(type.tiny.size)
        let traits = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(typeSize))
        let size = min(UIFontMetrics(forTextStyle: .caption2).scaledValue(for: base, compatibleWith: traits), base * 1.3)
        return Text(verbatim: SearchDisplay.shared.BEST_MATCH)
            .font(.system(size: size, weight: .medium))
            .foregroundStyle(StockStepsTheme.color(tone.content))
            .lineLimit(1).fixedSize()
            .padding(.horizontal, CGFloat(space.xs + space.xxs)).padding(.vertical, CGFloat(space.xxs))
            .background(StockStepsTheme.color(tone.container), in: Capsule())
    }

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

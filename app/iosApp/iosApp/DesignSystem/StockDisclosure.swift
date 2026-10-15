import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography

// Progressive disclosure (promoted from Earnings 5D.3/5D.4.1 in Phase 5D.5.1, decision D-6), mirroring Compose `StockDisclosure.kt`.
// Nothing inside is removed or reworded, only folded away from the main reading flow.

/// A collapsed secondary section (methodology, sources, notes, explainers): a 48 pt button that announces its state; nothing inside is removed.
struct StockDisclosure<Content: View>: View {
    let title: String
    var systemImage = "info.circle"
    @ViewBuilder var content: () -> Content
    @State private var open = false
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        let _ = typeSize
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Button { open.toggle() } label: {
                HStack(spacing: CGFloat(space.xs)) {
                    Image(systemName: systemImage).accessibilityHidden(true)
                    Text(title).multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    Image(systemName: open ? "chevron.down" : "chevron.right").accessibilityHidden(true)
                }
                .font(StockStepsTheme.font(type.label, relativeTo: .subheadline))
                .foregroundStyle(colors.primaryText)
                .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(open ? "Expanded" : "Collapsed")
            if open { content() }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Caption lines inside a disclosure.
struct StockNotes: View {
    let lines: [String]
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        ForEach(lines, id: \.self) {
            Text($0).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(StockStepsTheme.colors(scheme).textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// The one "Show details" of a card or row (as Compose `StockShowDetails`). Visible: the first sentence of `explanation`
/// (`ReadableText.firstSentence`, never reworded). Collapsed by default: the rest and `content`. No toggle when there is nothing to reveal.
/// `trailing` shares the toggle's row (e.g. "Learn more", "Read article") and moves below it when they don't fit.
struct StockShowDetails<Content: View>: View {
    let explanation: String?
    let hasMore: Bool
    var trailing: AnyView? = nil
    @ViewBuilder var content: () -> Content
    @State private var open = false
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        let _ = typeSize
        let colors = StockStepsTheme.colors(scheme)
        let parts = explanation.map { ReadableText.shared.firstSentence(text: $0) }
        let first = parts?.first as? String
        let rest = parts?.second as? String
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            if let first {
                Text(open && rest != nil ? "\(first) \(rest!)" : first).stockFont(type.small, relativeTo: .subheadline).foregroundStyle(colors.textBody)
                    .fixedSize(horizontal: false, vertical: true)
            }
            let toggle = rest != nil || hasMore
            if toggle && open { content() }
            if toggle || trailing != nil {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: CGFloat(space.md)) { toggleButton(toggle, colors); trailing }
                    VStack(alignment: .leading, spacing: 0) { toggleButton(toggle, colors); trailing }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder private func toggleButton(_ shown: Bool, _ colors: StockColors) -> some View {
        if shown {
            Button { open.toggle() } label: {
                HStack(spacing: CGFloat(space.xxs)) {
                    Text(open ? "Hide details" : "Show details")
                    Image(systemName: open ? "chevron.up" : "chevron.down").accessibilityHidden(true)
                }
                .font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.primaryText)
                .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget)).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(open ? "Expanded" : "Collapsed")
        }
    }
}

/// A market index as one compact row (as Compose `StockIndexRow`): name and quote state, value and signed change (arrow + words), one spoken
/// summary. At large Dynamic Type sizes the values move under the name. Values are already formatted; a nil change shows `changeUnavailable`.
struct StockIndexRow: View {
    let name: String
    let stateLabel: String
    let value: String
    let change: String?
    let direction: PriceDirection
    let accessibilityText: String
    var caution = false
    var note: String? = nil
    var changeUnavailable = "Change unavailable"
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let nameBlock = VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            Text(name).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
            Text(stateLabel).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(caution ? colors.cautionText : colors.textMeta)
            if let note {
                Text(note).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
            }
        }
        let stacked = typeSize >= .xxxLarge
        let values = VStack(alignment: stacked ? .leading : .trailing, spacing: CGFloat(space.xxs)) {
            Text(value).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(colors.textValue).monospacedDigit()
            if let change {
                StockPriceChange(percentage: change, direction: direction, lineLimit: nil)
            } else {
                Text(changeUnavailable).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
            }
        }
        Group {
            if stacked {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) { nameBlock; values }
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                HStack(spacing: CGFloat(space.md)) {
                    nameBlock.frame(maxWidth: .infinity, alignment: .leading)
                    values.fixedSize(horizontal: true, vertical: false)
                }
            }
        }
        .padding(.vertical, CGFloat(space.sm))
        .frame(minHeight: CGFloat(StockStepsTheme.dimensions.rowCompactMinHeight))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }
}

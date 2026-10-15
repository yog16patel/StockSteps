import Shared
import SwiftUI

// SwiftUI counterparts of the shared Compose design-system components. Values come
// from the same commonMain tokens so both platforms render one StockSteps brand.

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// Groups related information: semantic surface, `corners.card` (16pt) radius, 1pt border, `cardPadding` (16pt), no heavy shadow.
/// Never nest a card inside a card.
/// `bordered: false` keeps the plain surface without the outline (Home sections).
struct StockCardModifier: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    var padding: CGFloat = CGFloat(space.cardPadding)
    var fill: KeyPath<StockColors, Color> = \.surface
    var stroke: KeyPath<StockColors, Color> = \.border
    var bordered = true
    func body(content: Content) -> some View {
        let colors = StockStepsTheme.colors(scheme)
        let shape = RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card))
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(colors[keyPath: fill], in: shape)
            .overlay(shape.stroke(bordered ? colors[keyPath: stroke] : .clear, lineWidth: CGFloat(dims.border)))
    }
}

extension View {
    func stockCard(padding: CGFloat = CGFloat(space.cardPadding), bordered: Bool = true) -> some View {
        modifier(StockCardModifier(padding: padding, bordered: bordered))
    }
}

struct StockSectionHeader<Trailing: View>: View {
    @Environment(\.colorScheme) private var scheme
    let title: String
    var actionTitle: String?
    var action: (() -> Void)?
    @ViewBuilder var trailing: () -> Trailing
    /// Re-render on live Dynamic Type changes (Comparison accessibility closure; `StockStepsTheme.font` resolves when the body runs).
    @Environment(\.dynamicTypeSize) private var liveTypeSize
    /// 5D.5.4D: the header's width, for the stacked-action decision below.
    @State private var width: CGFloat = 0
    var body: some View {
        let _ = liveTypeSize
        Group {
            if actionStacked {
                // The action under the title, only when the title's longest word and the action can't share the line (at AX5 "Financial
                // Highlights" broke as "Financia l Highligh ts" beside "See Financials"). Otherwise the row is unchanged.
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: CGFloat(space.sm)) { titleText; trailing() }
                    actionButton
                }
            } else {
                HStack(spacing: CGFloat(space.sm)) {
                    titleText
                    trailing()
                    actionButton
                }
            }
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
    }

    private var titleText: some View {
        Text(title)
            .font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline))
            .foregroundStyle(StockStepsTheme.colors(scheme).textPrimary)
            .accessibilityAddTraits(.isHeader)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder private var actionButton: some View {
        if let actionTitle, let action {
            Button(actionTitle, action: action)
                .font(StockStepsTheme.font(type.label, relativeTo: .footnote))
                .foregroundStyle(StockStepsTheme.colors(scheme).primaryText)
                .frame(minWidth: CGFloat(dims.touchTarget), minHeight: CGFloat(dims.touchTarget), alignment: .trailing)
        }
    }

    /// Shared `StockLayout.metricRowStacked` rule on text widths only (never this row's frame): the longest title word + gap + the action.
    private var actionStacked: Bool {
        guard width > 0, let actionTitle, action != nil else { return false }
        let titleFont = StockStepsTheme.uiFont(type.cardTitle, typeSize: liveTypeSize)
        let actionFont = StockStepsTheme.uiFont(type.label, typeSize: liveTypeSize)
        let longest = title.split(separator: " ").map { (String($0) as NSString).size(withAttributes: [.font: titleFont]).width }.max() ?? 0
        let actionWidth = max((actionTitle as NSString).size(withAttributes: [.font: actionFont]).width, CGFloat(dims.touchTarget))
        return StockLayout.shared.metricRowStacked(longestLabelWord: Float(longest), valueWidth: Float(actionWidth), availableWidth: Float(width),
                                                   gap: Float(space.sm))
    }
}

extension StockSectionHeader where Trailing == EmptyView {
    init(title: String, actionTitle: String? = nil, action: (() -> Void)? = nil) {
        self.init(title: title, actionTitle: actionTitle, action: action) { EmptyView() }
    }
}

/// The one price-movement treatment: arrow + signed text + semantic color.
struct StockPriceChange: View {
    @Environment(\.colorScheme) private var scheme
    let percentage: String
    let direction: PriceDirection
    var style: ThemeTextStyle = StockStepsTheme.typography.numberLabel
    /// 1 in rows; metric layouts pass nil so a long value wraps instead of being cut.
    var lineLimit: Int? = 1
    /// Re-render on live Dynamic Type changes (Portfolio B.1: today's change stayed small after a switch to AX5).
    @Environment(\.dynamicTypeSize) private var liveTypeSize
    var body: some View {
        let _ = liveTypeSize
        let colors = StockStepsTheme.colors(scheme)
        let (arrow, color, spoken): (String, Color, String) = switch direction {
        case .up: ("↑\u{00A0}", colors.positiveText, "up \(percentage)")
        case .down: ("↓\u{00A0}", colors.negativeText, "down \(percentage)")
        case .unchanged: ("", colors.textSecondary, "unchanged")
        default: ("", colors.textTertiary, "change unavailable")
        }
        Text(arrow + percentage)
            .font(StockStepsTheme.font(style, relativeTo: .footnote))
            .foregroundStyle(color)
            .lineLimit(lineLimit)
            .accessibilityLabel(spoken)
    }
}

/// Compact single-choice pill filter: 34pt visual, 48pt touch target.
struct StockChip: View {
    @Environment(\.colorScheme) private var scheme
    let title: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let shape = Capsule()
        Button(action: action) {
            Text(title)
                .font(StockStepsTheme.font(type.label, relativeTo: .footnote).weight(selected ? .semibold : .regular))
                .foregroundStyle(selected ? colors.onPrimary : colors.textSecondary)
                .lineLimit(1)
                .fixedSize()
                .padding(.horizontal, CGFloat(space.lg))
                .frame(minHeight: CGFloat(dims.chipHeight))
                // Selected = the accessible action blue with white text (4.86:1), like primary buttons (Phase 2; was primaryDark).
                .background(selected ? colors.primaryAction : colors.surfaceSecondary, in: shape)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .frame(minHeight: CGFloat(dims.touchTarget))
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

/// Shared stock/company row: logo · ticker/company · optional sparkline · price over change.
/// `compact` is the dense list treatment; `trailing` replaces the change/price column.
struct StockRow<Trailing: View>: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    let symbol: String
    let name: String?
    var logoUrl: String?
    var compact = false
    var horizontalPadding: CGFloat = CGFloat(space.cardPadding)
    var sparkline: AnyView? = nil
    /// Lines for the company name; nil keeps the default (1, or 2 at accessibility sizes). Watchlist rows pass 2 (Phase 5A.1).
    var nameLineLimit: Int? = nil
    let action: () -> Void
    @ViewBuilder var trailing: () -> Trailing
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        Button(action: action) {
            HStack(spacing: CGFloat(space.sm)) {
                StockTickerAvatar(symbol: symbol, logoUrl: logoUrl, size: CGFloat(compact ? dims.logoCompact : dims.logo))
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(symbol).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary).lineLimit(1)
                    if let name {
                        Text(name)
                            .font(StockStepsTheme.font(compact ? type.caption : type.small, relativeTo: compact ? .caption1 : .subheadline))
                            // Large text gets a second line so long company names stay readable (Phase 4A); prices never truncate.
                            .foregroundStyle(colors.textSupporting).lineLimit(nameLineLimit ?? (typeSize >= .xxxLarge ? 2 : 1))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let sparkline {
                    sparkline.frame(width: CGFloat(dims.sparklineWidth), height: CGFloat(dims.sparklineHeight))
                }
                // Values keep their natural width (Phase 2): the company name truncates first, never the price or change.
                VStack(alignment: .trailing, spacing: CGFloat(space.xxs)) { trailing() }
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(.leading, CGFloat(space.xs))
            }
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, CGFloat(compact ? space.xs : space.sm))
            .frame(minHeight: CGFloat(compact ? dims.rowCompactMinHeight : dims.rowMinHeight))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
    }
}

extension StockRow where Trailing == StockRowValues {
    init(model: StockRowUiModel, compact: Bool = false, horizontalPadding: CGFloat = CGFloat(space.cardPadding), action: @escaping () -> Void) {
        let sparkline = model.sparkline.map { closes in AnyView(StockSparkline(closes: closes.map(\.doubleValue), direction: model.direction)) }
        self.init(symbol: model.symbol, name: model.name, logoUrl: model.logoUrl, compact: compact,
                  horizontalPadding: horizontalPadding, sparkline: sparkline, action: action) {
            StockRowValues(price: model.price, change: model.change, direction: model.direction)
        }
    }
}

struct StockRowValues: View {
    @Environment(\.colorScheme) private var scheme
    let price: String?
    let change: String
    let direction: PriceDirection
    var body: some View {
        if let price {
            Text(price).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote))
                .foregroundStyle(StockStepsTheme.colors(scheme).textValue).lineLimit(1)
        }
        StockPriceChange(percentage: change, direction: direction, style: type.numberLabel)
    }
}

/// Logo on a light tile once it loads; until then, or if it fails, the ticker stands in.
struct StockTickerAvatar: View {
    @Environment(\.colorScheme) private var scheme
    let symbol: String
    var logoUrl: String?
    var size: CGFloat = CGFloat(StockStepsTheme.dimensions.logo)
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let shape = RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip))
        Group {
            if let url = logoUrl.flatMap(URL.init(string:)) {
                AsyncImage(url: url) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFit().padding(CGFloat(space.xs))
                            .frame(width: size, height: size)
                            .background(colors.logoContainer, in: shape)
                    } else {
                        fallback(colors, shape)
                    }
                }
            } else {
                fallback(colors, shape)
            }
        }
        .accessibilityHidden(true)
    }

    private func fallback(_ colors: StockColors, _ shape: RoundedRectangle) -> some View {
        Text(String(symbol.split(separator: ".").first?.prefix(4) ?? ""))
            .font(.system(size: CGFloat(type.tiny.size), weight: .medium))
            .foregroundStyle(colors.textSecondary)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .frame(width: size, height: size)
            .background(colors.surfaceSecondary, in: shape)
    }
}

/// Minimal trend line from real closes (oldest first). Decorative: the row text states the change.
struct StockSparkline: View {
    @Environment(\.colorScheme) private var scheme
    let closes: [Double]
    let direction: PriceDirection
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let color: Color = switch direction {
        case .up: colors.positive
        case .down: colors.negative
        default: colors.iconSecondary
        }
        GeometryReader { proxy in
            Path { path in
                guard closes.count > 1, let low = closes.min(), let high = closes.max() else { return }
                let range = high - low > 0 ? high - low : 1
                let stroke = CGFloat(StockStepsTheme.dimensions.sparklineStroke)
                let step = proxy.size.width / CGFloat(closes.count - 1)
                for (index, close) in closes.enumerated() {
                    let point = CGPoint(
                        x: CGFloat(index) * step,
                        y: stroke / 2 + (proxy.size.height - stroke) * CGFloat(1 - (close - low) / range)
                    )
                    if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
                }
            }
            .stroke(color, style: StrokeStyle(lineWidth: CGFloat(StockStepsTheme.dimensions.sparklineStroke), lineCap: .round, lineJoin: .round))
        }
        .accessibilityHidden(true)
    }
}

/// Compact brand row: the existing "S" tile from the login design plus the wordmark.
struct StockBrandMark: View {
    @Environment(\.colorScheme) private var scheme
    let name: String
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        HStack(spacing: CGFloat(space.sm)) {
            Text("S")
                .font(.system(size: CGFloat(type.bodySemiBold.size), weight: .semibold))
                .foregroundStyle(colors.onPrimary)
                .frame(width: CGFloat(dims.brandMark), height: CGFloat(dims.brandMark))
                .background(colors.primary, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip)))
                .accessibilityHidden(true)
            Text(name).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

struct StockSkeleton: View {
    @Environment(\.colorScheme) private var scheme
    var width: CGFloat
    var height: CGFloat = CGFloat(StockStepsTheme.dimensions.skeletonLine)
    var body: some View {
        RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip))
            .fill(StockStepsTheme.colors(scheme).surfaceSecondary)
            .frame(width: width, height: height)
    }
}

struct StockRowSkeleton: View {
    var compact = false
    var horizontalPadding: CGFloat = CGFloat(space.cardPadding)
    var body: some View {
        HStack(spacing: CGFloat(space.sm)) {
            StockSkeleton(width: CGFloat(compact ? dims.logoCompact : dims.logo), height: CGFloat(compact ? dims.logoCompact : dims.logo))
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                StockSkeleton(width: 44)
                StockSkeleton(width: 110)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: CGFloat(space.xs)) {
                StockSkeleton(width: 56)
                StockSkeleton(width: 44)
            }
        }
        .padding(.horizontal, horizontalPadding)
        .padding(.vertical, CGFloat(compact ? space.xs : space.sm))
        .frame(minHeight: CGFloat(compact ? dims.rowCompactMinHeight : dims.rowMinHeight))
    }
}

/// Section-level empty/error message with an optional action. Never shows provider details.
struct StockSectionMessage: View {
    @Environment(\.colorScheme) private var scheme
    let message: String
    var actionTitle: String?
    var action: (() -> Void)?
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text(message).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textSecondary)
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .font(StockStepsTheme.font(type.bodyMedium))
                    .foregroundStyle(colors.primaryText)
                    .frame(minHeight: CGFloat(dims.touchTarget))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

enum InsightTone { case info, education }

/// Educational/informational card. Never signals market direction; `systemImage` adds a compact icon layout.
struct StockInsightCard: View {
    @Environment(\.colorScheme) private var scheme
    /// Read in body: live Dynamic Type refresh, and the icon moves above the text at accessibility sizes (Portfolio C.1).
    @Environment(\.dynamicTypeSize) private var typeSize
    let title: String
    let message: String
    var tone: InsightTone = .info
    var actionTitle: String?
    var systemImage: String?
    /// Nil makes the card plain content (not announced as a button).
    var action: (() -> Void)?
    /// Portfolio C.1 cleanup (as Compose): category line above the title, and the rest of the explanation behind a "Show details" toggle.
    var eyebrow: String? = nil
    var details: String? = nil
    var showDetailsText = "Show details"
    var hideDetailsText = "Hide details"
    @State private var detailsOpen = false
    var body: some View {
        if let action {
            Button(action: action) { card }
                .buttonStyle(.plain)
                .accessibilityHint(actionTitle ?? "")
        } else if details != nil {
            card // The text and the details toggle are separate elements, so the toggle stays reachable.
        } else {
            card.accessibilityElement(children: .combine)
        }
    }

    private var card: some View {
        let colors = StockStepsTheme.colors(scheme)
        let container = tone == .education ? colors.educationContainer : colors.primaryContainer
        let accent = tone == .education ? colors.educationAccent : colors.primaryText
        // C.1: fixed-size symbol inside its fixed tile (`.title3` grew past the tile and over the text at AX5).
        let icon = systemImage.map { name in
            Image(systemName: name)
                .font(.system(size: CGFloat(dims.educationIcon) * 0.5))
                .foregroundStyle(accent)
                .frame(width: CGFloat(dims.educationIcon), height: CGFloat(dims.educationIcon))
                .background(colors.surface.opacity(0.7), in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                .accessibilityHidden(true)
        }
        let text = VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            if let eyebrow, !eyebrow.isEmpty, !(typeSize.isAccessibilitySize && icon != nil) {
                Text(eyebrow).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(accent)
            }
            Text(title).font(StockStepsTheme.font(systemImage == nil ? type.cardTitle : type.bodySemiBold, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
            Text(message).font(StockStepsTheme.font(systemImage == nil ? type.small : type.caption, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
            if let details, detailsOpen {
                Text(details).font(StockStepsTheme.font(systemImage == nil ? type.small : type.caption, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
            }
            }
            .accessibilityElement(children: .combine)
            if details != nil {
                Button { detailsOpen.toggle() } label: {
                    HStack(spacing: CGFloat(space.xxs)) {
                        Text(detailsOpen ? hideDetailsText : showDetailsText).font(StockStepsTheme.font(type.bodyMedium, relativeTo: .subheadline))
                        Image(systemName: "chevron.down").font(.system(size: CGFloat(dims.iconSmall) * 0.7, weight: .semibold))
                            .rotationEffect(.degrees(detailsOpen ? 180 : 0)).accessibilityHidden(true)
                    }
                    .foregroundStyle(colors.primaryText)
                    .frame(minHeight: CGFloat(dims.touchTarget))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityValue(detailsOpen ? "Expanded" : "Collapsed")
            }
            if let actionTitle, systemImage == nil {
                Text("\(actionTitle) →").font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.primaryText)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // C.1: the arrow only on a tappable card — a plain insight showed an arrow that did nothing.
        let arrow = (systemImage != nil && action != nil) ? Image(systemName: "arrow.right").foregroundStyle(colors.textSecondary).accessibilityHidden(true) : nil
        return Group {
            if typeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                    // C.1 cleanup: the icon shares a line with the category (eyebrow) instead of taking a row of its own (as Compose).
                    if let eyebrow, !eyebrow.isEmpty, icon != nil {
                        HStack(spacing: CGFloat(space.sm)) {
                            icon
                            Text(eyebrow).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(accent)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    } else { icon }
                    text; arrow
                }
            } else {
                HStack(spacing: CGFloat(space.md)) { icon; text; arrow }
            }
        }
        .padding(CGFloat(systemImage == nil ? space.educationalCardPadding : space.md))
        .background(container, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        .contentShape(Rectangle())
    }
}

/// Opens the Search destination; typing happens on the Search screen.
struct StockSearchEntry: View {
    @Environment(\.colorScheme) private var scheme
    let placeholder: String
    let action: () -> Void
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let shape = RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card))
        Button(action: action) {
            HStack(spacing: CGFloat(space.sm)) {
                Image(systemName: "magnifyingglass").font(.footnote).foregroundStyle(colors.iconSecondary).accessibilityHidden(true)
                Text(placeholder).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textTertiary).lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, CGFloat(space.md))
            .frame(minHeight: CGFloat(dims.searchHeight))
            .background(colors.surface, in: shape)
            .overlay(shape.stroke(colors.border, lineWidth: CGFloat(dims.border)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct StockDivider: View {
    @Environment(\.colorScheme) private var scheme
    var inset: CGFloat = 0
    var body: some View {
        Rectangle()
            .fill(StockStepsTheme.colors(scheme).borderSubtle)
            .frame(height: CGFloat(dims.border))
            .padding(.leading, inset)
    }
}

/// US market status chip from the backend flag (Phase 4C, Phase 4B.1 semantics): a dot — green while open, amber for pre-market/after-hours,
/// neutral when closed (closed is not a loss) — and the state in words. Read-only. Unknown renders nothing (never guessed from the clock).
struct MarketStatusIndicator: View {
    @Environment(\.colorScheme) private var scheme
    let status: MarketStatus
    private let present = CompanyDetailsPresentation.shared
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        if let label = present.statusLabel(status: status) {
            let dot: Color = switch present.statusDot(status: status) {
            case .open: colors.positive
            case .extended: colors.warning
            default: colors.textDisabled
            }
            HStack(spacing: CGFloat(space.xs)) {
                Circle().fill(dot).frame(width: CGFloat(space.sm), height: CGFloat(space.sm))
                Text(label).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSupporting).lineLimit(1)
            }
            .padding(.horizontal, CGFloat(space.sm)).padding(.vertical, CGFloat(space.xxs))
            .background(colors.surfaceSecondary, in: Capsule())
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(present.statusDescription(status: status) ?? label)
        }
    }
}

/// Full-width navigation row inside a card (Phase 4B, generalised from Home's link row): optional icon, title, muted detail lines and a
/// chevron. One tap target of at least `rowCompactMinHeight`; title and detail wrap instead of truncating.
struct StockNavigationRow: View {
    @Environment(\.colorScheme) private var scheme
    let title: String
    var detail: String? = nil
    /// SF Symbol name.
    var icon: String? = nil
    var detailColor: Color? = nil
    /// A second muted line (e.g. watchlist counts) under `detail`.
    var secondaryDetail: String? = nil
    var hint: String? = nil
    /// Optional status beside the title (e.g. "StockSteps+" as PREMIUM, Phase 5C); nil keeps existing rows unchanged.
    var badge: String? = nil
    var badgeKind: StockBadgeKind = .premium
    /// 5D.5.1: a short body line under the title (Home's Daily Brief entry shows the summary's first sentence); never truncated, so a number or
    /// name is never cut ("S&P 50…" at AX5); callers keep it short. nil keeps rows unchanged.
    var summary: String? = nil
    let action: () -> Void
    /// Re-render on live Dynamic Type changes (Comparison accessibility closure; `StockStepsTheme.font` resolves when the body runs).
    @Environment(\.dynamicTypeSize) private var liveTypeSize
    var body: some View {
        let _ = liveTypeSize
        let colors = StockStepsTheme.colors(scheme)
        Button(action: action) {
            HStack(spacing: CGFloat(space.md)) {
                if let icon {
                    Image(systemName: icon).font(StockStepsTheme.font(type.body, relativeTo: .body)).foregroundStyle(colors.primaryText)
                        .frame(width: CGFloat(dims.icon)).accessibilityHidden(true)
                }
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    if let badge {
                        StockTitleWithBadge {
                            Text(title).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle).multilineTextAlignment(.leading)
                        } badge: { StockStatusBadge(text: badge, kind: badgeKind, size: .compact) }
                    } else {
                        Text(title).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle).multilineTextAlignment(.leading)
                    }
                    if let summary {
                        Text(summary).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                            .multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
                    }
                    if let detail {
                        Text(detail).font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                            .foregroundStyle(detailColor ?? colors.textSupporting).multilineTextAlignment(.leading)
                    }
                    if let secondaryDetail {
                        Text(secondaryDetail).font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                            .foregroundStyle(colors.textSupporting).multilineTextAlignment(.leading)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right").font(StockStepsTheme.font(type.caption, relativeTo: .caption1).weight(.semibold))
                    .foregroundStyle(colors.textTertiary).accessibilityHidden(true)
            }
            .padding(.vertical, CGFloat(space.sm))
            .frame(minHeight: CGFloat(dims.rowCompactMinHeight))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint(hint ?? "")
    }
}

// MARK: - Chart series styles (Comparison final polish; same `StockChartSeriesStyle` as Compose)

extension StockStepsTheme {
    /// Colour and dash pattern of chart series `index` (0 = main, solid brand blue).
    static func chartLine(_ index: Int, _ scheme: ColorScheme) -> StockChartSeriesStyle.Line {
        StockChartSeriesStyle.shared.series(index: Int32(index), p: palette(scheme))
    }

    static func chartReference(_ scheme: ColorScheme) -> StockChartSeriesStyle.Line { StockChartSeriesStyle.shared.reference(p: palette(scheme)) }

    static func strokeStyle(_ line: StockChartSeriesStyle.Line, minWidth: CGFloat = 0) -> StrokeStyle {
        StrokeStyle(lineWidth: max(CGFloat(line.width), minWidth), lineCap: .round, lineJoin: .round, dash: line.dash.map { CGFloat($0.floatValue) })
    }
}

/// A short sample of a chart line (colour + dash pattern) for legends, so a series is never identified by colour alone. Decorative.
struct StockChartSwatch: View {
    let line: StockChartSeriesStyle.Line
    var body: some View {
        Canvas { context, size in
            var path = Path()
            path.move(to: CGPoint(x: 1, y: size.height / 2))
            path.addLine(to: CGPoint(x: size.width - 1, y: size.height / 2))
            context.stroke(path, with: .color(StockStepsTheme.color(line.color)), style: StockStepsTheme.strokeStyle(line, minWidth: 1.5))
        }
        .frame(width: 20, height: 8)
        .accessibilityHidden(true)
    }
}

extension StockStepsTheme {
    /// A heading font at the user's Dynamic Type size, scaled down only when its longest word is wider than `available` (shared `StockHeadingFit`),
    /// so a single long word never breaks mid-word at accessibility sizes while every other heading keeps the user's size.
    static func fittedHeadingFont(_ text: String, _ token: ThemeTextStyle, typeSize: DynamicTypeSize, available: CGFloat) -> Font {
        let font = uiFont(token, typeSize: typeSize)
        let longest = text.split(separator: " ").map { (String($0) as NSString).size(withAttributes: [.font: font]).width }.max() ?? 0
        let scale = CGFloat(StockHeadingFit.shared.scale(longestWord: Float(longest), available: Float(max(available, 1)), minimum: 0.6, margin: 0.92))
        return Font(scale < 1 ? font.withSize(font.pointSize * scale) : font)
    }
}

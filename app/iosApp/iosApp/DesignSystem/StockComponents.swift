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
    var body: some View {
        HStack(spacing: CGFloat(space.sm)) {
            Text(title)
                .font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline))
                .foregroundStyle(StockStepsTheme.colors(scheme).textPrimary)
                .accessibilityAddTraits(.isHeader)
                .frame(maxWidth: .infinity, alignment: .leading)
            trailing()
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .font(StockStepsTheme.font(type.label, relativeTo: .footnote))
                    .foregroundStyle(StockStepsTheme.colors(scheme).primaryText)
                    .frame(minWidth: CGFloat(dims.touchTarget), minHeight: CGFloat(dims.touchTarget), alignment: .trailing)
            }
        }
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
    var body: some View {
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
                            .foregroundStyle(colors.textSupporting).lineLimit(typeSize >= .xxxLarge ? 2 : 1)
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
    let title: String
    let message: String
    var tone: InsightTone = .info
    var actionTitle: String?
    var systemImage: String?
    /// Nil makes the card plain content (not announced as a button).
    var action: (() -> Void)?
    var body: some View {
        if let action {
            Button(action: action) { card }
                .buttonStyle(.plain)
                .accessibilityHint(actionTitle ?? "")
        } else {
            card.accessibilityElement(children: .combine)
        }
    }

    private var card: some View {
        let colors = StockStepsTheme.colors(scheme)
        let container = tone == .education ? colors.educationContainer : colors.primaryContainer
        let accent = tone == .education ? colors.educationAccent : colors.primaryText
        return HStack(spacing: CGFloat(space.md)) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.title3)
                        .foregroundStyle(accent)
                        .frame(width: CGFloat(dims.educationIcon), height: CGFloat(dims.educationIcon))
                        .background(colors.surface.opacity(0.7), in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                        .accessibilityHidden(true)
                }
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(title).font(StockStepsTheme.font(systemImage == nil ? type.cardTitle : type.bodySemiBold, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                    Text(message).font(StockStepsTheme.font(systemImage == nil ? type.small : type.caption, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                    if let actionTitle, systemImage == nil {
                        Text("\(actionTitle) →").font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.primaryText)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if systemImage != nil {
                    Image(systemName: "arrow.right").foregroundStyle(colors.textSecondary).accessibilityHidden(true)
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

/// "● Market Open" style status from the backend flag. The dot carries the semantic color;
/// the label always names the state. Unknown renders nothing (never guessed from the clock).
struct MarketStatusIndicator: View {
    @Environment(\.colorScheme) private var scheme
    let status: MarketStatus
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let style: (String, String)? = switch status {
        case .open: ("Open", "open")
        case .closed: ("Closed", "closed")
        case .preMarket: ("Pre-market", "Pre-market trading")
        case .afterHours: ("After hours", "After-hours trading")
        default: nil
        }
        if let (label, spoken) = style {
            let on = status == .open
            let track = switch status {
            case .open: colors.positive
            case .closed: colors.negative
            default: colors.textDisabled
            }
            let inset = CGFloat(dims.statusSwitchHeight - dims.statusSwitchThumb) / 2
            HStack(spacing: CGFloat(space.sm)) {
                Text(label).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary).lineLimit(1)
                // Read-only switch: shows the session state, never focusable or tappable.
                Capsule()
                    .fill(track)
                    .frame(width: CGFloat(dims.statusSwitchWidth), height: CGFloat(dims.statusSwitchHeight))
                    .overlay(alignment: on ? .trailing : .leading) {
                        Circle().fill(colors.onPrimary)
                            .frame(width: CGFloat(dims.statusSwitchThumb), height: CGFloat(dims.statusSwitchThumb))
                            .padding(.horizontal, inset)
                    }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(spoken)
        }
    }
}

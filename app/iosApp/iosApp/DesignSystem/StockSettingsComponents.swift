import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// Settings/list row: optional icon or leading view, title, subtitle, and a chevron when tappable
/// or "Coming soon" when its destination does not exist yet. One accessible element.
struct StockSettingsRow: View {
    @Environment(\.colorScheme) private var scheme
    let title: String
    var subtitle: String?
    var systemImage: String?
    var leading: AnyView?
    var comingSoon = false
    var action: (() -> Void)?
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let row = HStack(spacing: CGFloat(space.md)) {
            if let leading { leading }
            else if let systemImage {
                Image(systemName: systemImage).foregroundStyle(colors.iconSecondary)
                    .frame(width: CGFloat(dims.iconLarge)).accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                Text(title).font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.textPrimary)
                if let subtitle { Text(subtitle).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSecondary) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if comingSoon {
                Text("Coming soon").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
            } else if action != nil {
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(colors.iconSecondary).accessibilityHidden(true)
            }
        }
        .padding(.vertical, CGFloat(space.md))
        .frame(minHeight: CGFloat(dims.touchTarget))
        .contentShape(Rectangle())
        if let action {
            Button(action: action) { row }.buttonStyle(.plain).accessibilityElement(children: .combine)
        } else {
            row.accessibilityElement(children: .combine)
        }
    }
}

/// Equal-width single-choice options; selection is informational blue.
struct StockSegmentedControl: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    let segments: [(String, String)]
    let selected: Int
    let onSelect: (Int) -> Void
    /// Portfolio Phase C: a small fixed-size icon beside the label at every text size (the label wraps) instead of a large icon stacked above.
    var compactIcons = false
    @State private var width: CGFloat = 0
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let shape = RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.button))
        // C.1 closure: equal-width segments only while every label's longest word (+ compact icon) fits its share (shared StockPillLayout rule);
        // otherwise one full-width row per segment — iPhone SE at AX5 cut "My Portfolio" to "My Portfol…".
        let font = StockStepsTheme.uiFont(type.label, weight: .semibold, typeSize: typeSize)
        let iconExtra = compactIcons ? CGFloat(dims.icon) * 0.85 + CGFloat(space.xs) : 0
        let widths = segments.map { segment in
            KotlinFloat(value: Float((segment.0.split(separator: " ").map { (String($0) as NSString).size(withAttributes: [.font: font]).width }.max() ?? 0) + iconExtra))
        }
        let sideBySide = width == 0 || StockPillLayout.shared.fitsEqualWidth(labelWidths: widths,
            availableWidth: Float(width - CGFloat(space.sm) * CGFloat(max(segments.count - 1, 0))), horizontalPadding: Float(space.xs))
        let layout = sideBySide ? AnyLayout(HStackLayout(spacing: CGFloat(space.sm))) : AnyLayout(VStackLayout(spacing: CGFloat(space.sm)))
        layout {
            ForEach(Array(segments.enumerated()), id: \.offset) { index, segment in
                let isSelected = index == selected
                Button { onSelect(index) } label: {
                    let label = Group {
                        Image(systemName: segment.1).font(compactIcons ? .system(size: CGFloat(dims.icon) * 0.85) : .footnote).accessibilityHidden(true)
                        // Wraps (up to two lines) instead of shrinking; selected is also semibold, not only blue (Phase 2).
                        Text(segment.0).font(StockStepsTheme.font(type.label, relativeTo: .footnote).weight(isSelected ? .semibold : .regular))
                            .lineLimit(2).multilineTextAlignment(.center)
                    }
                    Group {
                        if typeSize.isAccessibilitySize && !compactIcons { VStack(spacing: CGFloat(space.xxs)) { label } }
                        else { HStack(spacing: CGFloat(space.xs)) { label } }
                    }
                    .foregroundStyle(isSelected ? colors.primaryText : colors.textSecondary)
                    .frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget))
                    .background(isSelected ? colors.primaryContainer : colors.surfaceSecondary, in: shape)
                    .overlay(shape.stroke(isSelected ? colors.primary : colors.borderSubtle, lineWidth: CGFloat(dims.border)))
                    .contentShape(shape)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? [.isSelected] : [])
            }
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
    }
}

/// Account avatar placeholder; photo support can be added later without changing callers.
struct StockAccountAvatar: View {
    @Environment(\.colorScheme) private var scheme
    var imageURL: URL? = nil
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        Image(systemName: "person.fill")
            .foregroundStyle(colors.primary)
            .frame(width: CGFloat(dims.avatar), height: CGFloat(dims.avatar))
            .background(colors.primaryContainer, in: Circle())
            .accessibilityHidden(true)
    }
}

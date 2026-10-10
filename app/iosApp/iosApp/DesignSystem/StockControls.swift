import Shared
import SwiftUI
import UIKit

// Phase 1A (Global UI Refinement, intermediate step before Phase 2) SwiftUI counterparts of the Compose StockButton, StockMetric/StockMetricGrid, StockTextField and
// StockSelectField, plus the full empty/error state. Values come from the shared commonMain tokens.

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

// MARK: - Buttons

/// Filled primary action. Uses `primaryAction` (white text 4.86:1), not brand `primary` (3.67:1, below WCAG AA).
/// `role: .destructive` gets the subtle tinted treatment (never a solid red slab), like the Compose DESTRUCTIVE variant.
struct StockPrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        StockButtonBody(configuration: configuration, prominent: true)
    }
}

/// Tinted secondary action (primary container + primary text).
struct StockSecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        StockButtonBody(configuration: configuration, prominent: false)
    }
}

extension ButtonStyle where Self == StockPrimaryButtonStyle {
    /// Replaces `.borderedProminent` (which fills with the brand-blue tint).
    static var stockPrimary: StockPrimaryButtonStyle { StockPrimaryButtonStyle() }
}

extension ButtonStyle where Self == StockSecondaryButtonStyle {
    static var stockSecondary: StockSecondaryButtonStyle { StockSecondaryButtonStyle() }
}

private struct StockButtonBody: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.isEnabled) private var isEnabled
    let configuration: ButtonStyleConfiguration
    let prominent: Bool
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let destructive = configuration.role == .destructive
        let (fill, text): (Color, Color) = if !isEnabled {
            (colors.surfaceSecondary, colors.textDisabled)
        } else if destructive {
            (colors.negativeContainer, colors.negativeText)
        } else if prominent {
            (colors.primaryAction, colors.onPrimary)
        } else {
            (colors.primaryContainer, colors.primaryText)
        }
        let shape = RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.button))
        configuration.label
            .font(StockStepsTheme.font(type.bodySemiBold))
            .multilineTextAlignment(.center)
            .foregroundStyle(text)
            .padding(.horizontal, CGFloat(space.lg))
            .padding(.vertical, CGFloat(space.sm))
            .frame(minHeight: CGFloat(dims.buttonHeight))
            .background(fill, in: shape)
            .overlay(shape.stroke(destructive && isEnabled ? colors.negativeBorder : .clear, lineWidth: CGFloat(dims.border)))
            .opacity(configuration.isPressed ? 0.85 : 1)
            .frame(minHeight: CGFloat(dims.touchTarget))
            .contentShape(Rectangle())
    }
}

// MARK: - Metrics

/// One metric: muted label, prominent value, short context. The value wraps instead of truncating.
struct StockMetric: View {
    @Environment(\.colorScheme) private var scheme
    let label: String
    let value: String
    var helper: String?
    var direction: PriceDirection?
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.labelValueGap)) {
            Text(label).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary)
            if let direction, direction != .unavailable {
                StockPriceChange(percentage: value, direction: direction, style: type.numberEmphasis, lineLimit: nil)
            } else {
                Text(value).font(StockStepsTheme.font(type.numberEmphasis)).foregroundStyle(colors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let helper {
                Text(helper).font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.textTertiary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

struct StockMetricItem: Identifiable {
    let id = UUID()
    let label: String
    let value: String
    var helper: String?
    var direction: PriceDirection?
}

/// Metric row/grid: up to `maxColumns` equal columns, fewer (one at large text) instead of truncating — the same
/// `StockLayout.metricColumns` rule as the Compose `StockMetricGrid`.
struct StockMetricGrid: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    let items: [StockMetricItem]
    var maxColumns = 3
    /// When not every item fits on one line, show aligned label/value rows instead of an uneven grid (e.g. 2 + 1).
    var rowsWhenNarrow = false
    @Environment(\.colorScheme) private var scheme
    @State private var width: CGFloat = 0
    var body: some View {
        let scale = UIFontMetrics.default.scaledValue(for: 1, compatibleWith: UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(typeSize)))
        let valueFont = StockStepsTheme.uiFont(type.numberEmphasis, typeSize: typeSize)
        let labelFont = StockStepsTheme.uiFont(type.label, typeSize: typeSize)
        let widest = items.map { item in
            let value = ((item.direction.map { $0 != .unavailable } ?? false) ? "↑\u{00A0}" : "") + item.value
            return max((value as NSString).size(withAttributes: [.font: valueFont]).width, (item.label as NSString).size(withAttributes: [.font: labelFont]).width)
        }.max() ?? 0
        let columns = width > 0
            ? Int(StockLayout.shared.metricColumns(count: Int32(items.count), availableWidth: Float(width), fontScale: Float(scale), maxColumns: Int32(maxColumns),
                                                   widestContent: Float(widest), gap: Float(space.md)))
            : 1
        Group {
        if rowsWhenNarrow && width > 0 && columns < min(items.count, maxColumns) {
            let colors = StockStepsTheme.colors(scheme)
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                ForEach(items) { item in
                    HStack(spacing: CGFloat(space.md)) {
                        Text(item.label).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        if let direction = item.direction, direction != .unavailable {
                            StockPriceChange(percentage: item.value, direction: direction, style: type.numberMedium, lineLimit: nil)
                        } else {
                            Text(item.value).font(StockStepsTheme.font(type.numberMedium)).foregroundStyle(colors.textPrimary).multilineTextAlignment(.trailing)
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        } else {
        Grid(alignment: .topLeading, horizontalSpacing: CGFloat(space.md), verticalSpacing: CGFloat(space.itemGap)) {
            ForEach(Array(stride(from: 0, to: items.count, by: columns)), id: \.self) { start in
                GridRow {
                    ForEach(items[start..<min(start + columns, items.count)]) { item in
                        StockMetric(label: item.label, value: item.value, helper: item.helper, direction: item.direction)
                    }
                    ForEach(0..<(columns - min(columns, items.count - start)), id: \.self) { _ in Color.clear.frame(height: 0) }
                }
            }
        }
        }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
    }
}

// MARK: - States

/// Full empty/error state (optional icon, title, explanation, action). For one-line messages inside a section keep using
/// `StockSectionMessage` without a title.
struct StockStateMessage: View {
    @Environment(\.colorScheme) private var scheme
    let title: String
    let message: String
    var systemImage: String?
    var isError = false
    var actionTitle: String?
    var action: (() -> Void)?
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(spacing: CGFloat(space.sm)) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(StockStepsTheme.font(type.sectionTitle))
                    .foregroundStyle(isError ? colors.negativeText : colors.primaryText)
                    .frame(width: CGFloat(dims.stateIcon), height: CGFloat(dims.stateIcon))
                    .background(isError ? colors.negativeContainer : colors.primaryContainer,
                                in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                    .accessibilityHidden(true)
            }
            Text(title).font(StockStepsTheme.font(type.cardTitle)).foregroundStyle(colors.textPrimary)
                .multilineTextAlignment(.center).accessibilityAddTraits(.isHeader)
            Text(message).stockFont(type.small).foregroundStyle(colors.textSecondary).multilineTextAlignment(.center)
            if let actionTitle, let action {
                if isError {
                    Button(actionTitle, action: action).buttonStyle(.stockSecondary).padding(.top, CGFloat(space.xs))
                } else {
                    Button(actionTitle, action: action).buttonStyle(.stockPrimary).padding(.top, CGFloat(space.xs))
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, CGFloat(space.lg))
    }
}

// MARK: - Form controls

/// Themed text input matching the Compose `StockTextField`: label above, field, supporting or error text below.
/// Number pads get a keyboard "Done" button (they have no return key).
struct StockTextField: View {
    @Environment(\.colorScheme) private var scheme
    let label: String
    @Binding var text: String
    var placeholder: String?
    var supporting: String?
    var error: String?
    var keyboard: UIKeyboardType = .default
    var prefix: String?
    @FocusState private var focused: Bool
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let shape = RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip))
        VStack(alignment: .leading, spacing: CGFloat(space.labelValueGap)) {
            Text(label).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary)
            HStack(spacing: CGFloat(space.xs)) {
                if let prefix { Text(verbatim: prefix).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textSecondary) }
                TextField(label, text: $text, prompt: placeholder.map { Text(verbatim: $0).foregroundStyle(colors.textTertiary) })
                    .font(StockStepsTheme.font(type.body))
                    .foregroundStyle(colors.textPrimary)
                    .keyboardType(keyboard)
                    .focused($focused)
                    .accessibilityLabel(label)
                    .accessibilityHint(error ?? "")
            }
            .padding(.horizontal, CGFloat(space.md))
            .padding(.vertical, CGFloat(space.sm))
            .frame(minHeight: CGFloat(dims.touchTarget))
            .background(colors.surfaceSecondary, in: shape)
            .overlay(shape.stroke(error != nil ? colors.negative : colors.borderSubtle, lineWidth: CGFloat(dims.border)))
            if let note = error ?? supporting {
                Text(note).font(StockStepsTheme.font(type.caption)).foregroundStyle(error != nil ? colors.negativeText : colors.textTertiary)
            }
        }
        .toolbar {
            if focused && [.decimalPad, .numberPad, .phonePad].contains(keyboard) {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") { focused = false }
                }
            }
        }
    }
}

/// Form picker matching the Compose `StockSelectField`: shows the selected option's readable label, never an enum name
/// (`StockLabels.shared.humanize` is the fallback for code identifiers).
struct StockSelectField<Option: Hashable>: View {
    @Environment(\.colorScheme) private var scheme
    let label: String
    let options: [Option]
    @Binding var selection: Option?
    let optionLabel: (Option) -> String
    var placeholder = "Choose"
    var supporting: String?
    var error: String?
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let shape = RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip))
        VStack(alignment: .leading, spacing: CGFloat(space.labelValueGap)) {
            Text(label).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary)
            Menu {
                ForEach(options, id: \.self) { option in
                    Button(optionLabel(option)) { selection = option }
                }
            } label: {
                HStack {
                    Text(selection.map(optionLabel) ?? placeholder)
                        .font(StockStepsTheme.font(type.body))
                        .foregroundStyle(selection == nil ? colors.textTertiary : colors.textPrimary)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: CGFloat(space.sm))
                    Image(systemName: "chevron.down").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.iconSecondary)
                }
                .padding(.horizontal, CGFloat(space.md))
                .padding(.vertical, CGFloat(space.sm))
                .frame(minHeight: CGFloat(dims.touchTarget))
                .background(colors.surfaceSecondary, in: shape)
                .overlay(shape.stroke(error != nil ? colors.negative : colors.borderSubtle, lineWidth: CGFloat(dims.border)))
            }
            .accessibilityLabel(label)
            .accessibilityValue(selection.map(optionLabel) ?? placeholder)
            if let note = error ?? supporting {
                Text(note).font(StockStepsTheme.font(type.caption)).foregroundStyle(error != nil ? colors.negativeText : colors.textTertiary)
            }
        }
    }
}

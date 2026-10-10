import Shared
import SwiftUI
import UIKit

// Phase 2 (Global UI Refinement): badge system, banners and the editable search field — SwiftUI counterparts of the Compose
// StockStatusBadge, StockBanner and StockSearchField. Colours come from the shared `StockSemanticStyles` mapping.

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

enum StockBadgeSize { case compact, regular }

/// Status label: `kind` picks the semantic colours; the text states the meaning (never colour alone), scales with Dynamic Type and
/// keeps its natural width (`fixedSize`) so it never splits mid-word ("SIMU-LATED" before Phase 2) — neighbouring text wraps instead.
struct StockStatusBadge: View {
    @Environment(\.colorScheme) private var scheme
    let text: String
    let kind: StockBadgeKind
    var size: StockBadgeSize = .regular
    var accessibilityText: String?
    var body: some View {
        let tone = StockSemanticStyles.shared.badge(kind: kind, p: StockStepsTheme.palette(scheme))
        let c = StockStepsTheme.color
        let compact = size == .compact
        Text(verbatim: text)
            .font(StockStepsTheme.font(compact ? type.tiny : type.label))
            .foregroundStyle(c(tone.content))
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, CGFloat(compact ? space.xs + space.xxs : space.sm))
            .padding(.vertical, CGFloat(space.xxs))
            .background(c(tone.container), in: Capsule())
            .overlay(Capsule().stroke(tone.border == tone.container ? .clear : c(tone.border), lineWidth: CGFloat(dims.border)))
            .accessibilityLabel(accessibilityText ?? text)
    }
}

/// A title with a trailing status badge: side by side while both fit on one line, otherwise the badge moves below the title — the
/// badge never splits mid-word and the title never collapses to one letter per line (Phase 2, large Dynamic Type).
struct StockTitleWithBadge<Title: View, Badge: View>: View {
    @ViewBuilder var title: () -> Title
    @ViewBuilder var badge: () -> Badge
    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: CGFloat(space.sm)) {
                title().fixedSize(horizontal: true, vertical: false)
                Spacer(minLength: CGFloat(space.sm))
                badge()
            }
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                // ViewThatFits keeps the stacked option to its ideal height; let the title wrap instead of truncating.
                title().fixedSize(horizontal: false, vertical: true)
                badge()
            }
        }
    }
}

extension FactTone {
    var badgeKind: StockBadgeKind {
        switch self {
        case .positive: .positive
        case .negative: .negative
        case .caution: .warning
        default: .neutral
        }
    }
}

/// Inline banner: icon, optional title, wrapping message, optional action, dismiss only where product rules allow (never SAMPLE).
/// The app-wide MOCK strip stays `SampleDataBanner` (stacked below the tab bar so it never covers tab titles).
struct StockBanner: View {
    @Environment(\.colorScheme) private var scheme
    let kind: StockBannerKind
    let message: String
    var title: String?
    var actionTitle: String?
    var action: (() -> Void)?
    var onDismiss: (() -> Void)?
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let tone = StockSemanticStyles.shared.banner(kind: kind, p: StockStepsTheme.palette(scheme))
        let c = StockStepsTheme.color
        let shape = RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card))
        HStack(alignment: .top, spacing: CGFloat(space.sm)) {
            Image(systemName: icon).font(StockStepsTheme.font(type.body)).foregroundStyle(c(tone.icon)).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                if let title { Text(title).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle) }
                Text(message).stockFont(type.small).foregroundStyle(c(tone.content)).fixedSize(horizontal: false, vertical: true)
                if let actionTitle, let action {
                    Button(actionTitle, action: action).font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.primaryText)
                        .frame(minHeight: CGFloat(dims.touchTarget))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
            if let onDismiss, kind != .sample {
                Button(action: onDismiss) {
                    Image(systemName: "xmark").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.iconSecondary)
                        .frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Dismiss")
            }
        }
        .padding(.leading, CGFloat(space.md))
        .padding(.vertical, CGFloat(space.md))
        .padding(.trailing, CGFloat(onDismiss == nil ? space.md : space.none))
        .background(c(tone.container), in: shape)
        .overlay(shape.stroke(c(tone.border), lineWidth: CGFloat(dims.border)))
    }

    private var icon: String {
        switch kind {
        case .success: "checkmark.circle"
        case .warning, .error: "exclamationmark.triangle"
        default: "info.circle"
        }
    }
}

/// Editable search field, the same surface as `StockSearchEntry`: magnifier, placeholder, clear button once there is text, progress
/// while `loading`, error below. Stateless about results: the caller owns the query (its debounce/search stays in its model — bind
/// to a property whose `didSet` schedules the search, never an `.onChange` here) and the Search keyboard action.
struct StockSearchField: View {
    @Environment(\.colorScheme) private var scheme
    @Binding var query: String
    let placeholder: String
    var loading = false
    var error: String?
    var autoFocus = false
    var onSubmit: (() -> Void)?
    @FocusState private var focused: Bool
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let shape = RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card))
        VStack(alignment: .leading, spacing: CGFloat(space.labelValueGap)) {
            HStack(spacing: CGFloat(space.sm)) {
                Image(systemName: "magnifyingglass").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.iconSecondary).accessibilityHidden(true)
                TextField(placeholder, text: $query, prompt: Text(verbatim: placeholder).foregroundStyle(colors.textTertiary))
                    .font(StockStepsTheme.font(type.body))
                    .foregroundStyle(colors.textPrimary)
                    .submitLabel(.search)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .focused($focused)
                    .onSubmit { onSubmit?() }
                if loading { ProgressView().controlSize(.small).accessibilityLabel("Searching") }
                if !query.isEmpty {
                    Button { query = "" } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(colors.iconSecondary)
                            .frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Clear search")
                }
            }
            .padding(.leading, CGFloat(space.md))
            .padding(.trailing, CGFloat(query.isEmpty ? space.md : space.none))
            .frame(minHeight: CGFloat(dims.touchTarget))
            .background(colors.surface, in: shape)
            .overlay(shape.stroke(error != nil ? colors.negative : (focused ? colors.primary : colors.border), lineWidth: CGFloat(dims.border)))
            if let error {
                Text(error).font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.negativeText)
            }
        }
        .task { if autoFocus { focused = true } }
    }
}

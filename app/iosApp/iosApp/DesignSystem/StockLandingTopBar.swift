import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// The shared landing top bar of the five tab landing pages (Phase 5D.5.6B, plan §7; mirrors Compose `StockLandingTopBar`): the page heading
/// (the StockSteps brand mark on Home), the page's own `actions` (`actionCount` 44 pt+ icon buttons, e.g. Search), then Notifications (bell) and
/// Settings (gear), always last. Copy and spoken labels come from core `LandingTopBarDisplay`. When the heading and the icons can't share a line
/// (Dynamic Type, narrow widths) the icons move to their own row under the heading — one `StockLayout.metricRowStacked` decision from text widths,
/// never this view's own frame. Nothing is truncated or hidden.
struct StockLandingTopBar<Actions: View>: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var width: CGFloat = 0
    let tab: AppTab
    var actionCount: Int = 0
    let onNotifications: () -> Void
    let onSettings: () -> Void
    @ViewBuilder var actions: () -> Actions

    private var display: LandingTopBarDisplay { LandingTopBarDisplay.shared }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let title = display.title(tab: tab)
        let brand = display.showsBrand(tab: tab)
        let titleWidth = brand
            ? (title as NSString).size(withAttributes: [.font: StockStepsTheme.uiFont(type.cardTitle, typeSize: typeSize)]).width + CGFloat(dims.brandMark + space.sm)
            : (title as NSString).size(withAttributes: [.font: StockStepsTheme.uiFont(type.screenTitle, typeSize: typeSize)]).width
        let icons = CGFloat((actionCount + 2) * Int(dims.touchTarget))
        let stacked = StockLayout.shared.metricRowStacked(longestLabelWord: Float(titleWidth), valueWidth: Float(icons), availableWidth: Float(width), gap: Float(space.sm))
        Group {
            if stacked {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    heading(title, brand: brand, colors)
                    HStack(spacing: 0) { Spacer(minLength: 0); iconRow(colors) }
                }
            } else {
                HStack(spacing: 0) {
                    heading(title, brand: brand, colors).frame(maxWidth: .infinity, alignment: .leading)
                    iconRow(colors)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
    }

    @ViewBuilder private func heading(_ title: String, brand: Bool, _ colors: StockColors) -> some View {
        if brand {
            StockBrandMark(name: title)
        } else {
            Text(title).font(StockStepsTheme.font(type.screenTitle, relativeTo: .title2)).foregroundStyle(colors.textTitle)
                .fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
        }
    }

    @ViewBuilder private func iconRow(_ colors: StockColors) -> some View {
        actions()
        StockLandingIconButton(systemImage: "bell", label: display.NOTIFICATIONS_SPOKEN, action: onNotifications)
        StockLandingIconButton(systemImage: "gearshape", label: display.SETTINGS_SPOKEN, action: onSettings)
    }
}

extension StockLandingTopBar where Actions == EmptyView {
    init(tab: AppTab, onNotifications: @escaping () -> Void, onSettings: @escaping () -> Void) {
        self.init(tab: tab, actionCount: 0, onNotifications: onNotifications, onSettings: onSettings) { EmptyView() }
    }
}

/// One landing-bar icon action: a fixed-size symbol in a full touch target (it doesn't grow with Dynamic Type, so the row keeps its width).
struct StockLandingIconButton: View {
    @Environment(\.colorScheme) private var scheme
    let systemImage: String
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage).font(.system(size: 20, weight: .regular)).foregroundStyle(StockStepsTheme.colors(scheme).textPrimary)
                .frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// KAN-16: landing pages hide the navigation bar (they draw `StockLandingTopBar` in their scroll content), so nothing covered the status-bar
/// inset and scrolled content ran under the clock and battery. This fills exactly that inset with the page background, above the content: a
/// zero-height strip at the top of the safe area that extends up through the inset (`ignoresSafeArea(.top)`). No layout change — the content's
/// own safe-area top is untouched, so there is no added padding or blank area — and the status bar keeps its native appearance. Pushed screens
/// keep the system navigation bar and don't use it.
private struct LandingStatusBarBackdrop: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    func body(content: Content) -> some View {
        content.overlay(alignment: .top) {
            // Order matters: the colour ignores the top safe area inside a zero-height frame, so it grows up through the inset only.
            StockStepsTheme.colors(scheme).appBackground
                .ignoresSafeArea(edges: .top)
                .frame(height: 0)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
    }
}

extension View {
    /// KAN-16: the page background behind the status bar of a landing page (no navigation bar). Applied once per tab root in `AppScene`.
    func landingStatusBarBackdrop() -> some View { modifier(LandingStatusBarBackdrop()) }
}

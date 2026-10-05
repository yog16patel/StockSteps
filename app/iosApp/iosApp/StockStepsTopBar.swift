import Shared
import SwiftUI

extension AppBarConfiguration {
    static func screen(
        _ title: String,
        visible: Bool = true,
        backButton: AppBarBackButton = .none,
        backEnabled: Bool = true,
        actions: [AppBarAction] = []
    ) -> AppBarConfiguration {
        AppBarConfiguration(title: title, visible: visible, backButton: backButton, backEnabled: backEnabled, actions: actions)
    }
}

private struct StockStepsTopBar: ViewModifier {
    let configuration: AppBarConfiguration
    let onBack: () -> Void
    let onAction: (String) -> Void

    func body(content: Content) -> some View {
        content
            .navigationTitle(configuration.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(configuration.visible ? .visible : .hidden, for: .navigationBar)
            .toolbar {
                if configuration.visible && configuration.backButton != .none {
                    ToolbarItem(placement: .topBarLeading) {
                        Button(action: onBack) {
                            Label(
                                configuration.backButton == .close ? "Close" : "Back",
                                systemImage: configuration.backButton == .close ? "xmark" : "chevron.backward"
                            )
                        }
                        .disabled(!configuration.backEnabled)
                    }
                }
                if configuration.visible {
                    ToolbarItemGroup(placement: .topBarTrailing) {
                        ForEach(configuration.actions, id: \.id) { action in
                            Button(action.label) { onAction(action.id) }
                                .disabled(!action.enabled)
                        }
                    }
                }
            }
    }
}

extension View {
    func stockStepsTopBar(
        _ configuration: AppBarConfiguration,
        onBack: @escaping () -> Void = {},
        onAction: @escaping (String) -> Void = { _ in }
    ) -> some View {
        modifier(StockStepsTopBar(configuration: configuration, onBack: onBack, onAction: onAction))
    }
}

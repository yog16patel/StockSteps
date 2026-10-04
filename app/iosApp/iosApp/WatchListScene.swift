import SwiftUI

struct WatchListScene: View {
    let model: AccountViewModel
    let onSignIn: () -> Void
    let onSearch: () -> Void
    let onExplore: (String) -> Void
    var body: some View {
        WatchListScreen(state: model.state, onSignIn: onSignIn, onSearch: onSearch, onExplore: onExplore,
                        onRemove: { symbol in Task { await model.remove(symbol: symbol) } }, onRetrySync: model.retrySync)
    }
}

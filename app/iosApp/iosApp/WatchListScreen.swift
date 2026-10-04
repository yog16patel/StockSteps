import SwiftUI

struct WatchListScreen: View {
    let state: NativeAccountState
    let onSignIn: () -> Void
    let onSearch: () -> Void
    let onExplore: (String) -> Void
    let onRemove: (String) -> Void
    let onRetrySync: () -> Void
    var body: some View {
        NavigationStack {
            List {
                Section {
                    if state.initializing { ProgressView("Loading account…") }
                    else if let user = state.user {
                        Text(user.email ?? "Signed in")
                        Text(state.syncing ? "Syncing…" : state.pendingCount > 0 ? "\(state.pendingCount) changes waiting to sync" : "No pending changes")
                            .foregroundStyle(.secondary)
                    } else {
                        Text("Saved on this device. Sign in to sync across devices.")
                        Button("Sign in or create account", action: onSignIn)
                    }
                    if let error = state.error { Text(error).foregroundStyle(.red) }
                    if let error = state.syncError {
                        Text(error).foregroundStyle(.red)
                        Button("Retry sync", action: onRetrySync)
                    }
                    Button("Find stocks to add", action: onSearch)
                }
                Section("Saved stocks") {
                    if state.items.isEmpty { Text("Your watchlist is empty. Find a stock and add it from its details.") }
                    ForEach(state.items, id: \.symbol) { item in
                        HStack {
                            Button(item.symbol) { onExplore(item.symbol) }
                            Spacer()
                            Button("Remove") { onRemove(item.symbol) }
                                .accessibilityLabel("Remove \(item.symbol)")
                                .disabled(state.initializing)
                        }.buttonStyle(.borderless)
                    }
                }
            }
            .navigationTitle("WatchList")
        }
    }
}

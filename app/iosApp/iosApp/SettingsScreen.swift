import SwiftUI

struct SettingsScreen: View {
    let state: NativeAccountState
    let onSignIn: () -> Void
    let onSignOut: () -> Void
    var body: some View {
        Form {
            Section("Account") {
                if state.busy || state.initializing { ProgressView() }
                else if let user = state.user {
                    Text(user.email ?? "Signed in")
                    Button("Sign out", action: onSignOut)
                } else {
                    Text("Use StockSteps without an account, or sign in to sync your watchlist.")
                    Button("Sign in or create account", action: onSignIn)
                }
                if let error = state.error ?? state.configurationError { Text(error).foregroundStyle(.red) }
            }
        }
    }
}

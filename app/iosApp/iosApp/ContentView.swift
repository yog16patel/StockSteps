import SwiftUI

struct ContentView: View {
    @State private var accounts = AccountViewModel()
    @Environment(\.scenePhase) private var scenePhase
    @State private var hasEnteredApp = false
    private let baseURL: String
    init(baseURL: String = "http://localhost:8080") { self.baseURL = baseURL }
    var body: some View {
        Group {
            if accounts.state.initializing {
                ProgressView("Restoring account…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if hasEnteredApp || accounts.state.user != nil {
                AppScene(baseURL: baseURL, accounts: accounts)
            } else {
                AuthScene(model: accounts, onDone: { hasEnteredApp = true })
            }
        }
        .onChange(of: accounts.state.user?.id, initial: true) { _, userID in
            if userID != nil { hasEnteredApp = true }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { accounts.retrySync() }
        }
    }
}

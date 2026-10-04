import SwiftUI

struct SettingsScene: View {
    let model: AccountViewModel
    let onSignIn: () -> Void
    var body: some View {
        SettingsScreen(state: model.state, onSignIn: onSignIn, onSignOut: { Task { await model.signOut() } })
    }
}

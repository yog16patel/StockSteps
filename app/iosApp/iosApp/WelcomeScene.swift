import SwiftUI

struct WelcomeScene: View {
    let onStartExploring: () -> Void

    var body: some View {
        WelcomeScreen(onStartExploring: onStartExploring)
    }
}

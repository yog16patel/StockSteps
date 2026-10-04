import SwiftUI

struct FeaturePlaceholderScreen: View {
    let title: String
    let description: String

    var body: some View {
        NavigationStack {
            ContentUnavailableView(title, systemImage: "square.grid.2x2", description: Text(description))
                .navigationTitle(title)
        }
    }
}

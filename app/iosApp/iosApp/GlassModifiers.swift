import SwiftUI

extension View {
    @ViewBuilder
    func stockStepsGlassSurface() -> some View {
        // Xcode 26 / Swift 6.2 provides the iOS 26 Liquid Glass APIs.
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            self.glassEffect(.regular, in: Capsule())
        } else {
            self.background(.thinMaterial, in: Capsule())
        }
        #else
        self.background(.thinMaterial, in: Capsule())
        #endif
    }

    @ViewBuilder
    func stockStepsGlassButton() -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            self.buttonStyle(.glass)
        } else {
            self.buttonStyle(.bordered)
        }
        #else
        self.buttonStyle(.bordered)
        #endif
    }
}

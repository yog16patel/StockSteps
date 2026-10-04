import SwiftUI

struct ContentView: View {
    @State private var hasStartedExploring = false
    private let baseURL: String
    init(baseURL: String = "http://localhost:8080") { self.baseURL = baseURL }
    var body: some View {
        if hasStartedExploring {
            AppScene(baseURL: baseURL)
        } else {
            WelcomeScene(onStartExploring: { hasStartedExploring = true })
        }
    }
}

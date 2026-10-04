import SwiftUI

struct ContentView: View {
    private let baseURL: String
    init(baseURL: String = "http://localhost:8080") { self.baseURL = baseURL }
    var body: some View { AppScene(baseURL: baseURL) }
}

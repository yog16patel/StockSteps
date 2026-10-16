import SwiftUI
import Shared

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(StockStepsAppDelegate.self) private var appDelegate

    init() {
        #if DEBUG
        // Debug-only API log (`[StockSteps-API]` in the console); Release builds never compile this call,
        // and the Kotlin side also refuses unless the framework is a debug binary.
        if IosApiLogging.shared.enable(environment: { BackendSettings.environment.uppercased() }) {
            IosApiLogging.shared.start(
                stored: UserDefaults.standard.string(forKey: BackendSettings.storageKey) ?? BackendSettings.real,
                effective: BackendSettings.environment.uppercased(),
                real: BackendSettings.realURL, mock: BackendSettings.mockURL
            )
        }
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { NativeGoogleSignIn.handle($0) }
        }
    }
}

import SwiftUI

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(StockStepsAppDelegate.self) private var appDelegate
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { NativeGoogleSignIn.handle($0) }
        }
    }
}

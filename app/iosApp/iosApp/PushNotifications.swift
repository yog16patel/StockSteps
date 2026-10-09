import Foundation
import Shared
import SwiftUI
import UIKit
import UserNotifications
#if canImport(FirebaseMessaging)
import FirebaseMessaging
#endif

extension Notification.Name {
    /// Posted with the stock symbol when the user taps an alert notification.
    static let stockStepsOpenAlerts = Notification.Name("StockStepsOpenAlerts")
}

/// Push for alerts: asks for permission only when the user turns notifications on, links the FCM
/// token to the signed-in account (through the shared DeviceRegistrar), shows alerts that arrive in
/// the foreground and opens that stock's alerts when a notification is tapped.
@MainActor
final class PushCoordinator: NSObject, UNUserNotificationCenterDelegate {
    static let shared = PushCoordinator()
    weak var client: IosAccountClient?
    private var token: String?

    func attach(_ client: IosAccountClient) {
        self.client = client
        Task { await refreshRegistration() }
    }

    var enabled: Bool {
        get async { await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .authorized }
    }

    /// Shows the system prompt (first time) and registers with APNs; false if notifications stay off.
    func requestAuthorization() async -> Bool {
        let center = UNUserNotificationCenter.current()
        let status = await center.notificationSettings().authorizationStatus
        if status == .denied, let url = URL(string: UIApplication.openNotificationSettingsURLString) {
            await UIApplication.shared.open(url)
            return false
        }
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        await refreshRegistration()
        return granted
    }

    /// Registers for remote notifications when allowed; clears the token when not.
    func refreshRegistration() async {
        if await enabled {
            UIApplication.shared.registerForRemoteNotifications()
            client?.setPushToken(token: token)
        } else {
            client?.setPushToken(token: nil)
        }
    }

    func tokenChanged(_ token: String?) {
        self.token = token
        Task { await refreshRegistration() }
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        if info["type"] as? String == "daily-brief" {
            let id = info["briefId"] as? String ?? ""
            await MainActor.run { NotificationCenter.default.post(name: .stockStepsOpenBrief, object: id) }
            return
        }
        // Earnings notifications (payloadVersion 1): identifiers only; the screens re-fetch everything.
        // Results open Earnings Results; reminders, date changes and cancellations open the event.
        if let kind = info["type"] as? String, kind.hasPrefix("earnings-") {
            let report = info["reportId"] as? String
            let event = info["eventId"] as? String
            await MainActor.run {
                if kind == "earnings-results", let report { NotificationCenter.default.post(name: .stockStepsOpenEarningsResults, object: report) }
                else { NotificationCenter.default.post(name: .stockStepsOpenEarningsEvent, object: event ?? "") }
            }
            return
        }
        guard info["type"] as? String == "alert", let symbol = info["symbol"] as? String else { return }
        await MainActor.run { NotificationCenter.default.post(name: .stockStepsOpenAlerts, object: symbol) }
    }
}

final class StockStepsAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = PushCoordinator.shared
        #if canImport(FirebaseMessaging)
        if FirebaseAccountFactory.configure() { Messaging.messaging().delegate = self }
        #endif
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        #if canImport(FirebaseMessaging)
        Messaging.messaging().apnsToken = deviceToken
        #endif
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // Simulators without push support and missing entitlements end here; alerts still appear in-app.
    }
}

#if canImport(FirebaseMessaging)
extension StockStepsAppDelegate: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        Task { @MainActor in PushCoordinator.shared.tokenChanged(fcmToken) }
    }
}
#endif

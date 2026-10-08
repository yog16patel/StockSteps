import Observation
import Shared
import SwiftUI

/// SwiftUI owner of the shared app lock (Kotlin `AppLockManager` via `IosAppLock`).
@MainActor
@Observable
final class AppLockModel {
    private(set) var state: AppLockState = .notRequired
    private(set) var settings: AppLockSettings?
    @ObservationIgnored private let lock = IosAppLock()
    @ObservationIgnored private var subscription: AccountSubscription?

    init() {
        subscription = lock.observe { [weak self] state in
            self?.state = state
            self?.settings = self?.lock.settings()
        }
    }

    deinit {
        subscription?.cancel()
        lock.close()
    }

    var locked: Bool { state == .locked }
    /// Whether the app can be locked at all (used for the app-switcher privacy cover).
    var protects: Bool { state != .notRequired }

    func accountChanged(_ uid: String?) { lock.onAccountChanged(uid: uid); settings = lock.settings() }
    func background() { lock.onBackground() }
    func foreground() { lock.onForeground() }

    func unlock() async -> BiometricResult {
        await withCheckedContinuation { continuation in
            lock.unlock(reason: "Confirm it's you to open StockSteps") { continuation.resume(returning: $0) }
        }
    }

    func setEnabled(_ enabled: Bool) async -> BiometricResult {
        let result: BiometricResult = await withCheckedContinuation { continuation in
            if enabled {
                lock.enable(reason: "Confirm it's you to turn on Face ID unlock") { continuation.resume(returning: $0) }
            } else {
                lock.disable(reason: "Confirm it's you to turn off Face ID unlock") { continuation.resume(returning: $0) }
            }
        }
        settings = lock.settings()
        return result
    }

    func setTimeout(_ timeout: AppLockTimeout) {
        lock.setTimeout(timeout: timeout)
        settings = lock.settings()
    }
}

/// Full-screen, opaque unlock surface. The system prompt opens once automatically; after a
/// cancel the user taps to retry, so prompts never loop. "Use account login" signs out.
struct AppUnlockView: View {
    @Environment(\.colorScheme) private var scheme
    let lock: AppLockModel
    let onUseAccountLogin: () -> Void
    @State private var message: String?
    @State private var busy = false
    @State private var attempted = false

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let space = StockStepsTheme.spacing
        let type = StockStepsTheme.typography
        VStack(spacing: CGFloat(space.md)) {
            Spacer()
            StockBrandMark(name: "StockSteps")
            Image(systemName: lock.settings?.kind == .faceId ? "faceid" : "lock.shield.fill")
                .font(.system(size: 44))
                .foregroundStyle(colors.primaryText)
                .frame(width: 96, height: 96)
                .background(colors.primaryContainer, in: Circle())
                .padding(.top, CGFloat(space.xl))
                .accessibilityHidden(true)
            Text("Welcome Back").font(StockStepsTheme.font(type.screenTitle, relativeTo: .title2)).foregroundStyle(colors.textPrimary)
                .accessibilityAddTraits(.isHeader)
            Text("Unlock StockSteps to continue exploring your investments.")
                .font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody).multilineTextAlignment(.center)
            if let message {
                Text(message).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.negativeText).multilineTextAlignment(.center)
            }
            Button(action: attempt) {
                Text(label).font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.onPrimary)
                    .frame(maxWidth: .infinity, minHeight: CGFloat(StockStepsTheme.dimensions.buttonHeight))
                    .background(colors.primaryDark, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
            }
            .disabled(busy)
            .padding(.top, CGFloat(space.md))
            Button("Use account login", action: onUseAccountLogin)
                .font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.primaryText)
                .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
            Spacer()
        }
        .frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
        .padding(CGFloat(space.screen))
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.appBackground.ignoresSafeArea())
        .task {
            guard !attempted else { return }
            attempted = true
            attempt()
        }
    }

    private var label: String {
        switch lock.settings?.kind {
        case .faceId: "Unlock with Face ID"
        case .touchId: "Unlock with Touch ID"
        default: "Unlock with passcode"
        }
    }

    private func attempt() {
        guard !busy else { return }
        busy = true
        Task {
            let result = await lock.unlock()
            switch result {
            case is BiometricResultSuccess, is BiometricResultCancelled: message = nil // cancelling isn't an error
            case is BiometricResultLockout: message = "Too many unsuccessful attempts. Try your device passcode or sign in again."
            case is BiometricResultUnavailable: message = "Face ID isn't available right now. Sign in with your account to continue."
            default: message = "We couldn't confirm it's you. Try again."
            }
            busy = false
        }
    }
}

/// Covers the app in the app switcher while the app can be locked.
struct PrivacyCover: View {
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ZStack {
            colors.appBackground.ignoresSafeArea()
            StockBrandMark(name: "StockSteps")
        }
        .accessibilityHidden(true)
    }
}

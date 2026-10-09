import Shared
import SwiftUI

/// Quiet, neutral Settings: Account, Appearance, Notifications, About, then Sign Out.
struct SettingsScreen: View {
    @Environment(\.colorScheme) private var scheme
    let state: NativeAccountState
    @Binding var themeMode: String
    /// Nil hides the Development section (release builds / no mock backend).
    var backendEnvironment: Binding<String>?
    /// Mock backend and signed in only: "FREE", "PLUS" or "EXPIRED". Nil hides it.
    var simulatedPlan: Binding<String>? = nil
    let appVersion: String?
    let onSignIn: () -> Void
    let onSignOut: () -> Void
    /// Nil hides Security & Sign-In (guests).
    var appLock: AppLockModel?
    /// Opens Earnings Reminders settings.
    var onEarningsReminders: (() -> Void)? = nil
    @State private var confirmSignOut = false
    @State private var securityMessage: String?
    @State private var securityBusy = false
    @State private var confirmRealData = false
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text("Settings").font(StockStepsTheme.font(type.screenTitle, relativeTo: .title2)).foregroundStyle(colors.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                    Text("Manage your account and preferences").font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textSecondary)
                }
                section("Account") { accountRow }
                if state.user != nil, let appLock, let lock = appLock.settings {
                    section("Security & Sign-In") { security(appLock, lock, colors) }
                }
                section("Appearance") {
                    StockSettingsRow(title: "Theme", subtitle: "Choose how StockSteps looks", systemImage: "circle.lefthalf.filled")
                    StockSegmentedControl(
                        segments: [("Light", "sun.max"), ("Dark", "moon"), ("System", "laptopcomputer")],
                        selected: [AppTheme.light, AppTheme.dark, AppTheme.system].firstIndex(of: themeMode) ?? 2,
                        onSelect: { themeMode = [AppTheme.light, AppTheme.dark, AppTheme.system][$0] }
                    )
                    .padding(.bottom, CGFloat(space.sm))
                }
                section("Notifications") {
                    StockSettingsRow(title: "Price Alerts", subtitle: "Watchlist price updates", systemImage: "bell.fill", comingSoon: true)
                    divider
                    StockSettingsRow(title: "Market News", subtitle: "Important market updates", systemImage: "newspaper.fill", comingSoon: true)
                    divider
                    StockSettingsRow(title: "Earnings Reminders", subtitle: "Before reports, and when results are out", systemImage: "calendar.badge.clock",
                                     comingSoon: onEarningsReminders == nil, action: onEarningsReminders)
                }
                if let backendEnvironment {
                    section("Development") {
                        backendSource(backendEnvironment, colors)
                        if let simulatedPlan { planSimulation(simulatedPlan) }
                    }
                }
                section("About") {
                    StockSettingsRow(title: "About StockSteps", subtitle: appVersion.map { "Version \($0)" }, systemImage: "info.circle.fill")
                    divider
                    StockSettingsRow(title: "Privacy Policy", systemImage: "shield.fill", comingSoon: true)
                    divider
                    StockSettingsRow(title: "Terms of Service", systemImage: "doc.text.fill", comingSoon: true)
                    divider
                    StockSettingsRow(title: "Help & Feedback", systemImage: "questionmark.circle.fill", comingSoon: true)
                }
                // Guests have no session, so there is nothing to sign out of.
                if state.user != nil {
                    Button { confirmSignOut = true } label: {
                        Label("Sign Out", systemImage: "rectangle.portrait.and.arrow.right")
                            .font(StockStepsTheme.font(type.bodyMedium))
                            .foregroundStyle(colors.negativeText)
                            .frame(maxWidth: .infinity, minHeight: CGFloat(StockStepsTheme.dimensions.buttonHeight))
                            .background(colors.negativeContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.button)))
                            .overlay(RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.button)).stroke(colors.negativeBorder, lineWidth: CGFloat(StockStepsTheme.dimensions.border)))
                    }
                    .buttonStyle(.plain)
                    .disabled(state.busy)
                    .padding(.top, CGFloat(space.lg))
                    if let message = state.error ?? state.configurationError {
                        Text(message).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary).padding(.top, CGFloat(space.sm))
                    }
                }
            }
            .frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.top, CGFloat(space.sm))
            .padding(.bottom, CGFloat(space.xl))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .alert("Use real market data?", isPresented: $confirmRealData) {
            Button("Cancel", role: .cancel) {}
            Button("Use Real Data") { backendEnvironment?.wrappedValue = BackendSettings.real }
        } message: {
            Text("Real data requests may use FMP/Finnhub API quota during development.")
        }
        .alert("Sign out?", isPresented: $confirmSignOut) {
            Button("Cancel", role: .cancel) {}
            Button("Sign Out", role: .destructive, action: onSignOut)
        } message: {
            Text("You'll need to sign in again to sync your StockSteps account. Your watchlist and account are not deleted.")
        }
    }

    @ViewBuilder
    private var accountRow: some View {
        if state.initializing {
            HStack(spacing: CGFloat(space.md)) {
                StockAccountAvatar()
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) { StockSkeleton(width: 100); StockSkeleton(width: 160) }
            }
            .padding(.vertical, CGFloat(space.md))
            .accessibilityElement(children: .ignore).accessibilityLabel("Loading")
        } else if let user = state.user {
            // The auth model has no display name yet, so only the email is shown.
            StockSettingsRow(title: "Account", subtitle: user.email ?? "Signed in", leading: AnyView(StockAccountAvatar()))
        } else {
            StockSettingsRow(title: "Sign in to sync", subtitle: "Keep your watchlist on every device", leading: AnyView(StockAccountAvatar()), action: onSignIn)
        }
    }

    /// Face ID toggle (system prompt before any change), unlock timeout and session information.
    @ViewBuilder
    private func security(_ appLock: AppLockModel, _ lock: AppLockSettings, _ colors: StockColors) -> some View {
        let available = lock.available == .available
        let name = lock.kind == .faceId ? "Face ID" : lock.kind == .touchId ? "Touch ID" : "Passcode"
        HStack(spacing: CGFloat(space.md)) {
            StockSettingsRow(
                title: "\(name) Unlock",
                subtitle: available ? "Use \(name) to open StockSteps" : lock.available == .notEnrolled
                    ? "Set up Face ID, Touch ID or a passcode in Settings first." : "Biometric unlock isn't available on this device.",
                systemImage: "lock.shield.fill"
            )
            if available || lock.enabled {
                Toggle("", isOn: Binding(get: { lock.enabled }, set: { enabled in
                    securityBusy = true
                    Task {
                        let result = await appLock.setEnabled(enabled)
                        securityMessage = switch result {
                        case is BiometricResultSuccess: nil
                        case is BiometricResultCancelled: enabled ? "\(name) unlock wasn't turned on." : nil
                        case is BiometricResultLockout: "Too many unsuccessful attempts. Try your device passcode or sign in again."
                        default: "Biometric unlock isn't available on this device."
                        }
                        securityBusy = false
                    }
                }))
                .labelsHidden()
                .disabled(securityBusy)
                .accessibilityLabel("\(name) Unlock")
            }
        }
        if lock.enabled {
            divider
            StockSettingsRow(title: "Require Unlock After", systemImage: "timer")
            Picker("Require Unlock After", selection: Binding(get: { lock.timeout }, set: { appLock.setTimeout($0) })) {
                Text("Every time").tag(AppLockTimeout.immediately)
                Text("5 min").tag(AppLockTimeout.fiveMinutes)
                Text("15 min").tag(AppLockTimeout.fifteenMinutes)
                Text("Never").tag(AppLockTimeout.never)
            }
            .pickerStyle(.segmented)
            .padding(.bottom, CGFloat(space.sm))
        }
        if let securityMessage {
            Text(securityMessage).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary).padding(.bottom, CGFloat(space.sm))
        }
        divider
        StockSettingsRow(title: "Stay Signed In", subtitle: "Your session is restored automatically when you reopen the app.", systemImage: "person.crop.circle.badge.checkmark")
    }

    /// Mock vs Real is a development choice: neutral/blue states, never green/red.
    @ViewBuilder
    private func backendSource(_ environment: Binding<String>, _ colors: StockColors) -> some View {
        let options = [BackendSettings.mock, BackendSettings.real]
        StockSettingsRow(title: "Backend Data Source", subtitle: "Choose where development data comes from", systemImage: "server.rack")
        StockSegmentedControl(
            segments: [("Mock Data", "shippingbox"), ("Real Data", "antenna.radiowaves.left.and.right")],
            selected: options.firstIndex(of: environment.wrappedValue) ?? 1,
            onSelect: { index in
                let selected = options[index]
                // Mock → Real asks first (quota); Real → Mock applies immediately.
                if selected == BackendSettings.real && environment.wrappedValue == BackendSettings.mock { confirmRealData = true }
                else { environment.wrappedValue = selected }
            }
        )
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            HStack(spacing: CGFloat(space.sm)) {
                Circle().fill(colors.primary).frame(width: CGFloat(StockStepsTheme.dimensions.statusDot), height: CGFloat(StockStepsTheme.dimensions.statusDot))
                Text(environment.wrappedValue == BackendSettings.mock ? "Using local sample market data" : "Using live StockSteps backend")
                    .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
            }
            if environment.wrappedValue != BackendSettings.mock {
                Label("Real data may use external API quota.", systemImage: "exclamationmark.triangle.fill")
                    .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                    .labelStyle(WarningLabelStyle(tint: colors.warning))
            }
        }
        .padding(.top, CGFloat(space.sm))
        .padding(.bottom, CGFloat(space.md))
        .accessibilityElement(children: .combine)
    }

    /// MOCK only: what the mock server returns for Insights. Never shown with the real backend.
    @ViewBuilder
    private func planSimulation(_ plan: Binding<String>) -> some View {
        let options = ["FREE", "PLUS", "EXPIRED"]
        StockSettingsRow(title: "Simulated StockSteps+ plan", subtitle: "Mock backend only. Changes what the mock server returns for Insights.", systemImage: "server.rack")
        StockSegmentedControl(segments: [("Free", "person"), ("Plus", "star"), ("Expired", "clock")],
                              selected: options.firstIndex(of: plan.wrappedValue) ?? 0,
                              onSelect: { plan.wrappedValue = options[$0] })
            .padding(.bottom, CGFloat(space.md))
    }

    private var divider: some View { StockDivider(inset: CGFloat(StockStepsTheme.dimensions.iconLarge + space.md)) }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        let colors = StockStepsTheme.colors(scheme)
        return VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            Text(title).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textSecondary).accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: 0) { content() }
                .padding(.horizontal, CGFloat(space.md))
                .padding(.vertical, CGFloat(space.xxs))
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                .overlay(RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)).stroke(colors.border, lineWidth: CGFloat(StockStepsTheme.dimensions.border)))
        }
        .padding(.top, CGFloat(space.xl))
    }
}

private struct WarningLabelStyle: LabelStyle {
    let tint: Color
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: CGFloat(StockStepsTheme.spacing.sm)) {
            configuration.icon.foregroundStyle(tint).accessibilityHidden(true)
            configuration.title
        }
    }
}

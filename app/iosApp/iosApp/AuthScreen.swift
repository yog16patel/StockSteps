import Shared
import SwiftUI

/// Sign in / create account (shown at launch for signed-out users and from every "Sign in" prompt). Same
/// structure as Android's `AuthScreen`; rendering only — authentication state and actions come from `AuthScene`.
struct AuthScreen: View {
    let state: NativeAccountState
    @Binding var email: String
    @Binding var password: String
    let signup: Bool
    let onSwitchMode: () -> Void
    let onGoogle: () -> Void
    let onSubmit: () -> Void
    let onGuest: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var revealed = false
    @State private var notice: String?
    @FocusState private var focus: Field?
    private enum Field { case email, password }
    private let t = AuthTheme.tokens
    private var busy: Bool { state.busy || state.initializing }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let dark = scheme == .dark
        GeometryReader { geo in
            let compact = geo.size.height < 700
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    StockStepsAuthHeader(colors: colors)
                    Spacer().frame(height: CGFloat(compact ? 14 : t.headerGap))
                    StockStepsAuthHero(signup: signup, compact: compact, colors: colors, dark: dark)
                    Spacer().frame(height: CGFloat(compact ? 14 : t.heroGap))
                    StockStepsAuthCard(colors: colors) {
                        StockStepsGoogleButton(colors: colors, dark: dark, enabled: !busy && state.configurationError == nil, action: onGoogle)
                        StockStepsAuthDivider(text: "or use email", colors: colors)
                        StockStepsAuthTextField(label: "Email", icon: "envelope", colors: colors, dark: dark, focused: focus == .email) {
                            TextField("", text: $email)
                                .overlay(alignment: .leading) { if email.isEmpty { Text(verbatim: "you@example.com").foregroundStyle(colors.textTertiary).allowsHitTesting(false).accessibilityHidden(true) } }
                                .textContentType(.emailAddress)
                                .keyboardType(.emailAddress)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .submitLabel(.next)
                                .focused($focus, equals: .email)
                                .onSubmit { focus = .password }
                                .accessibilityLabel("Email")
                        }
                        .disabled(busy)
                        VStack(alignment: .trailing, spacing: 4) {
                            StockStepsAuthTextField(label: "Password", icon: "lock", colors: colors, dark: dark, focused: focus == .password) {
                                HStack {
                                    Group {
                                        if revealed { TextField("", text: $password) } else { SecureField("", text: $password) }
                                    }
                                    .overlay(alignment: .leading) {
                                        if password.isEmpty { Text(signup ? "At least 6 characters" : "••••••••").foregroundStyle(colors.textTertiary).allowsHitTesting(false).accessibilityHidden(true) }
                                    }
                                    .textContentType(signup ? .newPassword : .password)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled()
                                    .submitLabel(.done)
                                    .focused($focus, equals: .password)
                                    .accessibilityLabel("Password")
                                    Button { revealed.toggle() } label: {
                                        Image(systemName: revealed ? "eye" : "eye.slash").foregroundStyle(colors.textSecondary).frame(width: 44, height: 44)
                                    }
                                    .accessibilityLabel(revealed ? "Hide password" : "Show password")
                                }
                            }
                            .disabled(busy)
                            if !signup {
                                Button("Forgot password?") { notice = "Password recovery is not available in the app yet." }
                                    .font(AuthTheme.font(t.linkText, .subheadline))
                                    .foregroundStyle(colors.primary)
                                    .frame(minHeight: 44)
                                    .disabled(busy)
                            }
                        }
                        if let error = state.error ?? state.configurationError { StockStepsAuthError(message: error, colors: colors) }
                        StockStepsAuthButton(title: signup ? "Create account" : "Sign in",
                                             enabled: !busy && state.configurationError == nil && !email.isEmpty && !password.isEmpty, loading: busy, colors: colors) {
                            focus = nil; onSubmit()
                        }
                        StockStepsAuthDivider(text: "or", colors: colors)
                        StockStepsGuestAction(colors: colors, enabled: !busy, action: onGuest)
                        StockStepsAuthSwitch(prompt: signup ? "Already have an account?" : "New to StockSteps?", action: signup ? "Sign in" : "Create account",
                                             colors: colors, enabled: !busy, onTap: onSwitchMode)
                    }
                }
                .frame(maxWidth: CGFloat(t.contentWidth))
                .padding(.horizontal, CGFloat(t.screenPadding))
                .padding(.vertical, 16)
                .frame(maxWidth: .infinity, alignment: .top)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(
            ZStack {
                colors.appBackground
                LinearGradient(colors: [colors.primary.opacity(dark ? 0.16 : 0.10), colors.appBackground.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.45))
            }
            .ignoresSafeArea()
        )
        .onChange(of: signup) { _, _ in revealed = false }
        .alert("Reset your password", isPresented: Binding(get: { notice != nil }, set: { if !$0 { notice = nil } })) {
            Button("OK") { notice = nil }
        } message: { Text(notice ?? "") }
    }
}

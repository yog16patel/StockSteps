import SwiftUI

struct AuthScreen: View {
    let state: NativeAccountState
    @Binding var email: String
    @Binding var password: String
    let signup: Bool
    let onSwitchMode: () -> Void
    let onSubmit: () -> Void
    let onGuest: () -> Void
    @State private var revealed = false
    @State private var notice: String?
    private let t = AuthTheme.tokens
    private var busy: Bool { state.busy || state.initializing }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: CGFloat(t.stackGap)) {
                AuthBrand()
                Spacer().frame(height: CGFloat(AuthTheme.spacing.space14))
                VStack(alignment: .leading, spacing: CGFloat(AuthTheme.spacing.tiny)) {
                    Text(signup ? "Create account" : "Welcome back")
                        .font(AuthTheme.font(t.title, .largeTitle))
                        .foregroundStyle(AuthTheme.color(t.ink))
                    Text("Sign in to sync your Watchlist across devices.")
                        .font(AuthTheme.font(t.body))
                        .foregroundStyle(AuthTheme.color(t.muted))
                }
                AuthButton(title: "Continue with Google", google: true, enabled: !busy) {
                    notice = "Google sign-in is not available yet. Please use email or continue as a guest."
                }
                AuthDivider(text: "or use email")
                AuthField(label: "Email") {
                    TextField("you@example.com", text: $email)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityLabel("Email")
                }.disabled(busy)
                AuthField(label: "Password") {
                    HStack {
                        Group {
                            if revealed { TextField("••••••••", text: $password) }
                            else { SecureField("••••••••", text: $password) }
                        }
                        .textContentType(signup ? .newPassword : .password)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityLabel("Password")
                        Button(revealed ? "Hide" : "Show") { revealed.toggle() }
                            .font(AuthTheme.font(t.label, .caption1))
                            .foregroundStyle(AuthTheme.color(t.link))
                            .frame(minHeight: CGFloat(t.controlHeight))
                    }
                }.disabled(busy)
                if !signup {
                    HStack {
                        Spacer()
                        Button("Forgot password?") { notice = "Password recovery is not available in the app yet." }
                            .font(AuthTheme.font(t.linkText, .footnote))
                            .foregroundStyle(AuthTheme.color(t.link))
                            .disabled(busy)
                    }
                }
                if let error = state.error ?? state.configurationError {
                    Text(error).font(AuthTheme.font(t.body)).foregroundStyle(.red)
                }
                if busy { ProgressView().frame(maxWidth: .infinity) }
                AuthButton(title: signup ? "Create account" : "Sign in", primary: true, enabled: !busy && state.configurationError == nil && !email.isEmpty && !password.isEmpty, action: onSubmit)
                AuthDivider(text: "or")
                AuthButton(title: "Continue as guest", enabled: !busy, action: onGuest)
                HStack(spacing: CGFloat(AuthTheme.spacing.space6)) {
                    Text(signup ? "Already have an account?" : "New to StockSteps?")
                        .foregroundStyle(AuthTheme.color(t.muted))
                    Button(signup ? "Sign in" : "Create account", action: onSwitchMode)
                        .fontWeight(.semibold)
                        .foregroundStyle(AuthTheme.color(t.link))
                        .disabled(busy)
                }
                .font(AuthTheme.font(t.linkText, .footnote))
                .frame(maxWidth: .infinity)
            }
            .frame(maxWidth: CGFloat(t.contentWidth))
            .padding(CGFloat(AuthTheme.spacing.extraLarge))
            .frame(maxWidth: .infinity, alignment: .top)
        }
        .background(AuthTheme.color(t.background).ignoresSafeArea())
        .onChange(of: signup) { _, _ in revealed = false }
        .alert("Sign-in options", isPresented: Binding(get: { notice != nil }, set: { if !$0 { notice = nil } })) {
            Button("OK") { notice = nil }
        } message: { Text(notice ?? "") }
    }
}

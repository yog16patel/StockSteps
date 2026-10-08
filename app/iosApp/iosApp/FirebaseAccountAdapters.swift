import Foundation
import Shared
#if canImport(FirebaseCore)
import FirebaseCore
import FirebaseAuth
import GoogleSignIn
#endif

final class NativeAccountSubscription: NSObject, AccountSubscription {
    private var cancelAction: (() -> Void)?
    init(_ cancel: @escaping () -> Void) { cancelAction = cancel }
    func cancel() { cancelAction?(); cancelAction = nil }
}

// Only this adapter file imports Firebase. Domain and screens use plain shared models.
enum FirebaseAccountFactory {
    /// Watchlists, notes and alerts are stored by the StockSteps backend selected in Settings → Development.
    /// Configures Firebase from GoogleService-Info.plist when present; returns whether it's available.
    @discardableResult
    static func configure() -> Bool {
        #if canImport(FirebaseCore)
        if FirebaseApp.app() == nil,
           let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
           let options = FirebaseOptions(contentsOfFile: path) {
            FirebaseApp.configure(options: options)
        }
        return FirebaseApp.app() != nil
        #else
        return false
        #endif
    }

    static func makeClient() -> IosAccountClient {
        #if canImport(FirebaseCore)
        let configured = configure()
        #if DEBUG
        if configured && ProcessInfo.processInfo.arguments.contains("--firebase-emulators") {
            Auth.auth().useEmulator(withHost: "127.0.0.1", port: 9099)
        }
        #endif
        return IosAccountClient(auth: NativeAuthGateway(configured: configured), baseUrl: { BackendSettings.currentURL }, environment: BackendSettings.environment)
        #else
        return IosAccountClient(auth: UnconfiguredAuthGateway(), baseUrl: { BackendSettings.currentURL }, environment: BackendSettings.environment)
        #endif
    }
}

private final class UnconfiguredAuthGateway: NSObject, PlatformAuthGateway {
    var configurationError: String? { "Account sign-in is not configured yet. Your watchlist is saved on this device." }
    func observeUser(onChange: @escaping (Shared.User?) -> Void) -> any AccountSubscription {
        onChange(nil)
        return NativeAccountSubscription({})
    }
    func signIn(email: String, password: String, completion: @escaping (String?) -> Void) { completion(configurationError) }
    func signUp(email: String, password: String, completion: @escaping (String?) -> Void) { completion(configurationError) }
    func signInWithGoogle(completion: @escaping (String?) -> Void) { completion(configurationError) }
    func signOut(completion: @escaping (String?) -> Void) { completion(nil) }
    func idToken(forceRefresh: Bool, completion: @escaping (String?, String?) -> Void) { completion(nil, configurationError) }
}

#if canImport(FirebaseCore)
private final class NativeAuthGateway: NSObject, PlatformAuthGateway {
    private let auth: Auth?
    init(configured: Bool) { auth = configured ? Auth.auth() : nil }
    var configurationError: String? { auth == nil ? "Account sign-in is not configured yet. Your watchlist is saved on this device." : nil }
    func observeUser(onChange: @escaping (Shared.User?) -> Void) -> any AccountSubscription {
        guard let auth else { onChange(nil); return NativeAccountSubscription({}) }
        let handle = auth.addStateDidChangeListener { _, user in
            onChange(user.map { Shared.User(id: $0.uid, email: $0.email) })
        }
        return NativeAccountSubscription { auth.removeStateDidChangeListener(handle) }
    }
    func signIn(email: String, password: String, completion: @escaping (String?) -> Void) {
        guard let auth else { completion(configurationError); return }
        auth.signIn(withEmail: email, password: password) { _, error in completion(error.map(safeAuthMessage)) }
    }
    func signUp(email: String, password: String, completion: @escaping (String?) -> Void) {
        guard let auth else { completion(configurationError); return }
        auth.createUser(withEmail: email, password: password) { _, error in completion(error.map(safeAuthMessage)) }
    }
    func signInWithGoogle(completion: @escaping (String?) -> Void) {
        guard let auth else { completion(configurationError); return }
        NativeGoogleSignIn.signIn { credential, error in
            guard let credential else { completion(error ?? "Google sign-in was cancelled."); return }
            auth.signIn(with: credential) { _, error in completion(error.map(safeAuthMessage)) }
        }
    }
    func signOut(completion: @escaping (String?) -> Void) {
        do { try auth?.signOut(); GIDSignIn.sharedInstance.signOut(); completion(nil) }
        catch { completion(safeAuthMessage(error)) }
    }
    /// Firebase ID token for StockSteps backend requests (the SDK refreshes it as needed).
    func idToken(forceRefresh: Bool, completion: @escaping (String?, String?) -> Void) {
        guard let user = auth?.currentUser else { completion(nil, "Sign in to continue."); return }
        user.getIDTokenForcingRefresh(forceRefresh) { token, error in
            completion(token, token == nil ? (error.map(safeAuthMessage) ?? "Sign in again to continue.") : nil)
        }
    }
}

private func safeAuthMessage(_ error: Error) -> String {
    switch AuthErrorCode(rawValue: (error as NSError).code) {
    case .networkError: return "Could not connect. Check your connection and try again."
    case .weakPassword: return "Use a stronger password with at least 6 characters."
    case .invalidEmail: return "Enter a valid email address."
    case .emailAlreadyInUse: return "Could not create this account. Try signing in instead."
    case .tooManyRequests: return "Too many attempts. Please try again later."
    case .operationNotAllowed: return "Email sign-in is unavailable. Please contact support."
    default: return "Could not sign in. Check your email and password."
    }
}

#endif

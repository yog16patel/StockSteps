import Foundation
import Shared
#if canImport(FirebaseCore)
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore
#endif

final class NativeAccountSubscription: NSObject, AccountSubscription {
    private var cancelAction: (() -> Void)?
    init(_ cancel: @escaping () -> Void) { cancelAction = cancel }
    func cancel() { cancelAction?(); cancelAction = nil }
}

// Only this adapter file imports Firebase. Domain and screens use plain shared models.
enum FirebaseAccountFactory {
    static func makeClient() -> IosAccountClient {
        #if canImport(FirebaseCore)
        if FirebaseApp.app() == nil,
           let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
           let options = FirebaseOptions(contentsOfFile: path) {
            FirebaseApp.configure(options: options)
        }
        let configured = FirebaseApp.app() != nil
        #if DEBUG
        if configured && ProcessInfo.processInfo.arguments.contains("--firebase-emulators") {
            Auth.auth().useEmulator(withHost: "127.0.0.1", port: 9099)
            let settings = Firestore.firestore().settings
            settings.host = "127.0.0.1:8085"
            settings.isSSLEnabled = false
            Firestore.firestore().settings = settings
        }
        #endif
        return IosAccountClient(auth: NativeAuthGateway(configured: configured), cloud: NativeWatchlistGateway(configured: configured))
        #else
        return IosAccountClient(auth: UnconfiguredAuthGateway(), cloud: UnconfiguredWatchlistGateway())
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
    func signOut(completion: @escaping (String?) -> Void) { completion(nil) }
}

private final class UnconfiguredWatchlistGateway: NSObject, PlatformWatchlistGateway {
    func observe(uid: String, onSnapshot: @escaping ([WatchlistItem]?, KotlinBoolean, String?) -> Void) -> any AccountSubscription {
        onSnapshot(nil, KotlinBoolean(bool: false), "Cloud sync is not configured yet.")
        return NativeAccountSubscription({})
    }
    func put(uid: String, item: WatchlistItem, completion: @escaping (String?) -> Void) { completion("Cloud sync is not configured yet.") }
    func delete(uid: String, symbol: String, completion: @escaping (String?) -> Void) { completion("Cloud sync is not configured yet.") }
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
    func signOut(completion: @escaping (String?) -> Void) {
        do { try auth?.signOut(); completion(nil) }
        catch { completion(safeAuthMessage(error)) }
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

private final class NativeWatchlistGateway: NSObject, PlatformWatchlistGateway {
    private let database: Firestore?
    init(configured: Bool) { database = configured ? Firestore.firestore() : nil }
    func observe(uid: String, onSnapshot: @escaping ([WatchlistItem]?, KotlinBoolean, String?) -> Void) -> any AccountSubscription {
        guard let database else {
            onSnapshot(nil, KotlinBoolean(bool: false), "Cloud sync is not configured yet.")
            return NativeAccountSubscription({})
        }
        let listener = database.collection("users").document(uid).collection("watchlist")
            .addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
                if let error { onSnapshot(nil, KotlinBoolean(bool: false), safeCloudMessage(error)); return }
                guard let snapshot else { return }
                var items: [WatchlistItem] = []
                for document in snapshot.documents {
                    let data = document.data()
                    guard let symbol = data["symbol"] as? String,
                          symbol == document.documentID,
                          symbol.range(of: "^[A-Z0-9][A-Z0-9.^-]{0,31}$", options: .regularExpression) != nil,
                          let added = data["addedAt"] as? Int64,
                          let updated = data["updatedAt"] as? Int64,
                          added >= 0, updated >= added else {
                        onSnapshot(nil, KotlinBoolean(bool: false), "Cloud watchlist data could not be read. Local data is safe.")
                        return
                    }
                    items.append(WatchlistItem(symbol: symbol, addedAt: added, updatedAt: updated))
                }
                onSnapshot(items, KotlinBoolean(bool: !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites), nil)
            }
        return NativeAccountSubscription { listener.remove() }
    }
    func put(uid: String, item: WatchlistItem, completion: @escaping (String?) -> Void) {
        guard let database else { completion("Cloud sync is not configured yet."); return }
        let document = database.collection("users").document(uid).collection("watchlist").document(item.symbol)
        database.runTransaction({ transaction, errorPointer in
            do {
                let existing = try transaction.getDocument(document).data()?["addedAt"] as? Int64
                let added = min(item.addedAt, existing ?? item.addedAt)
                transaction.setData(["symbol": item.symbol, "addedAt": added, "updatedAt": max(item.updatedAt, added)], forDocument: document)
                return nil
            } catch { errorPointer?.pointee = error as NSError; return nil }
        }) { _, error in completion(error.map(safeCloudMessage)) }
    }
    func delete(uid: String, symbol: String, completion: @escaping (String?) -> Void) {
        guard let database else { completion("Cloud sync is not configured yet."); return }
        let document = database.collection("users").document(uid).collection("watchlist").document(symbol)
        database.runTransaction({ transaction, errorPointer in
            do {
                _ = try transaction.getDocument(document)
                transaction.deleteDocument(document)
                return nil
            } catch { errorPointer?.pointee = error as NSError; return nil }
        }) { _, error in completion(error.map(safeCloudMessage)) }
    }
}

private func safeCloudMessage(_ error: Error) -> String {
    if (error as NSError).code == FirestoreErrorCode.permissionDenied.rawValue {
        return "Cloud sync was denied. Check your account or cloud permissions. Local data is safe."
    }
    return "Could not sync. Your changes are saved on this device."
}
#endif

import Shared

protocol AccountServing {
    func observe(onChange: @escaping (AccountSnapshot) -> Void) -> any AccountSubscription
    func signIn(email: String, password: String) async throws
    func signUp(email: String, password: String) async throws
    func signInWithGoogle() async throws
    func signOut() async throws
    func add(symbol: String) async throws
    func addListing(stock: StockSearchResult) async throws
    func remove(symbol: String) async throws
    func retrySync()
    func close()
    /// The shared client behind signed-in watchlists and alerts (nil in previews/tests).
    var client: IosAccountClient? { get }
}

final class AccountService: AccountServing {
    let shared: IosAccountClient
    var client: IosAccountClient? { shared }
    init(client: IosAccountClient = FirebaseAccountFactory.makeClient()) {
        shared = client
        Task { @MainActor in PushCoordinator.shared.attach(client) }
    }
    func observe(onChange: @escaping (AccountSnapshot) -> Void) -> any AccountSubscription { shared.observe(onChange: onChange) }
    func signIn(email: String, password: String) async throws { try await shared.signIn(email: email, password: password) }
    func signUp(email: String, password: String) async throws { try await shared.signUp(email: email, password: password) }
    func signInWithGoogle() async throws { try await shared.signInWithGoogle() }
    func signOut() async throws { try await shared.signOut() }
    func add(symbol: String) async throws { try await shared.add(symbol: symbol) }
    func addListing(stock: StockSearchResult) async throws { try await shared.addListing(stock: stock) }
    func remove(symbol: String) async throws { try await shared.remove(symbol: symbol) }
    func retrySync() { shared.retrySync() }
    func close() { shared.close() }
}

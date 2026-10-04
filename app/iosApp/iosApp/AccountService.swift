import Shared

protocol AccountServing {
    func observe(onChange: @escaping (AccountSnapshot) -> Void) -> any AccountSubscription
    func signIn(email: String, password: String) async throws
    func signUp(email: String, password: String) async throws
    func signInWithGoogle() async throws
    func signOut() async throws
    func add(symbol: String) async throws
    func remove(symbol: String) async throws
    func retrySync()
    func close()
}

final class AccountService: AccountServing {
    private let client: IosAccountClient
    init(client: IosAccountClient = FirebaseAccountFactory.makeClient()) { self.client = client }
    func observe(onChange: @escaping (AccountSnapshot) -> Void) -> any AccountSubscription { client.observe(onChange: onChange) }
    func signIn(email: String, password: String) async throws { try await client.signIn(email: email, password: password) }
    func signUp(email: String, password: String) async throws { try await client.signUp(email: email, password: password) }
    func signInWithGoogle() async throws { try await client.signInWithGoogle() }
    func signOut() async throws { try await client.signOut() }
    func add(symbol: String) async throws { try await client.add(symbol: symbol) }
    func remove(symbol: String) async throws { try await client.remove(symbol: symbol) }
    func retrySync() { client.retrySync() }
    func close() { client.close() }
}

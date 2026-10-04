import Observation
import Shared
import SwiftUI
import Network

struct NativeAccountState {
    var user: User?
    var initializing = true
    var configurationError: String?
    var items: [WatchlistItem] = []
    var syncing = false
    var pendingCount = 0
    var syncError: String?
    var busy = false
    var error: String?
}

@MainActor
@Observable
final class AccountViewModel {
    private(set) var state = NativeAccountState()
    @ObservationIgnored private let service: any AccountServing
    @ObservationIgnored private var subscription: (any AccountSubscription)?
    @ObservationIgnored private let monitor = NWPathMonitor()

    init(service: any AccountServing = AccountService()) {
        self.service = service
        subscription = service.observe { [weak self] snapshot in
            guard let self else { return }
            self.state.user = snapshot.session.user
            self.state.initializing = snapshot.session.initializing
            self.state.configurationError = snapshot.session.configurationError
            self.state.items = snapshot.items
            self.state.syncing = snapshot.sync.syncing
            self.state.pendingCount = Int(snapshot.sync.pendingCount)
            self.state.syncError = snapshot.sync.error
        }
        monitor.pathUpdateHandler = { [weak self] path in
            if path.status == .satisfied { Task { @MainActor in self?.retrySync() } }
        }
        monitor.start(queue: DispatchQueue(label: "StockSteps.AccountConnectivity"))
    }
    deinit { monitor.cancel(); subscription?.cancel(); service.close() }

    func authenticate(email: String, password: String, signup: Bool) async -> Bool {
        guard !state.busy else { return false }
        state.busy = true; state.error = nil
        defer { state.busy = false }
        do {
            if signup { try await service.signUp(email: email, password: password) }
            else { try await service.signIn(email: email, password: password) }
            return true
        } catch {
            state.error = accountMessage(error)
            return false
        }
    }
    func signInWithGoogle() async -> Bool {
        guard !state.busy else { return false }
        state.busy = true
        state.error = nil
        defer { state.busy = false }
        do {
            try await service.signInWithGoogle()
            return true
        } catch {
            state.error = accountMessage(error)
            return false
        }
    }
    func signOut() async {
        guard !state.busy else { return }
        state.busy = true; state.error = nil
        defer { state.busy = false }
        do { try await service.signOut() }
        catch { state.error = accountMessage(error) }
    }
    func toggle(symbol: String) async {
        do {
            if state.items.contains(where: { $0.symbol == symbol }) { try await service.remove(symbol: symbol) }
            else { try await service.add(symbol: symbol) }
            state.error = nil
        } catch { state.error = "Could not update your watchlist. Try again." }
    }
    func remove(symbol: String) async {
        do { try await service.remove(symbol: symbol); state.error = nil }
        catch { state.error = "Could not update your watchlist. Try again." }
    }
    func retrySync() { service.retrySync() }
    private func accountMessage(_ error: Error) -> String {
        if let cause = (error as NSError).userInfo["KotlinException"] as? AccountException {
            return cause.message ?? "Could not complete the account request. Try again."
        }
        return "Could not complete the account request. Try again."
    }
}

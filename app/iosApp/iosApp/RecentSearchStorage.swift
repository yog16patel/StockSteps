import Foundation
import Shared

/// Recent company searches (5D.5.5C) in UserDefaults under the shared key: on this device only, never synced, no account needed.
final class UserDefaultsStringStorage: NSObject, StringStorage {
    private let key = RecentSearches.shared.KEY
    func read() -> String? { UserDefaults.standard.string(forKey: key) }
    func write(value: String?) {
        if let value { UserDefaults.standard.set(value, forKey: key) } else { UserDefaults.standard.removeObject(forKey: key) }
    }
}

/// One store for every search sheet (global search, Portfolio add, Practice).
enum RecentSearchesStore {
    @MainActor static let shared = RecentSearchStore(storage: UserDefaultsStringStorage())
}

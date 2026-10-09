import UIKit

/// Opens an external link (news article, source) in the system browser.
///
/// Scenes that own `navigationDestination`s use this instead of `@Environment(\.openURL)`: a pushed destination re-creates the `openURL`
/// environment value, which re-rendered the owning scene, which rebuilt the destination, and so on — an endless main-thread loop that froze
/// the app when Earnings history or the price-move breakdown was opened from Company Details (Phase 5C.1).
@MainActor
struct ExternalURLOpener {
    func callAsFunction(_ url: URL) { UIApplication.shared.open(url) }
}

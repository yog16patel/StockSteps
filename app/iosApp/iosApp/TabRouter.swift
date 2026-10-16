import Shared
import SwiftUI

/// Phase 5D.5.6C2: every screen `AppScene` pushes, with its arguments. Each case maps to one core `ScreenId`; core `NavigationPolicy` decides its
/// tab (the exhaustive `switch` in `screen` is the only iOS ownership table, like Android's `AppRouteScreens`).
enum AppDestination: Hashable {
    case companyDetails(String)
    case learn(LearnTarget)
    case dailyBrief(BriefTarget)
    case briefHistory
    case practice
    case practiceOrder(PracticeOrderTarget)
    case research(ResearchTarget)
    case earningsCalendar(EarningsCalendarTarget)
    case earningsEvent(String)
    case earningsReminders
    case earningsResults(String)
    case earningsDigest
    case earningsDetails(String)
    case discover
    case compare
    case stockAlerts(String)

    var screen: ScreenId {
        switch self {
        case .companyDetails: .companyDetails
        case let .learn(target):
            switch target {
            case .topics: .learnTopics
            case .term: .learnTerm
            }
        case .dailyBrief: .dailyBrief
        case .briefHistory: .dailyBriefHistory
        case .practice: .practice
        case .practiceOrder: .practiceOrder
        case .research: .guidedResearch
        case .earningsCalendar: .earningsCalendar
        case .earningsEvent: .earningsEvent
        case .earningsReminders: .earningsReminders
        case .earningsResults: .earningsResults
        case .earningsDigest: .earningsDigest
        case .earningsDetails: .earningsDetails
        case .discover: .discover
        case .compare: .compare
        case .stockAlerts: .stockAlerts
        }
    }

    /// The entry's identity in the shared model (screen + arguments), so the same screen with other arguments is a new entry.
    var key: String { String(describing: self) }
}

/// App-wide screens presented over the tabs (no tab bar, never on a tab's stack; plan §2.2).
enum GlobalSheet: String, Identifiable {
    case notifications
    case digestSettings
    var id: String { rawValue }
}

extension AppRoute {
    var tab: AppTab {
        switch self {
        case .home: .home
        case .markets: .markets
        case .portfolio: .portfolio
        case .watchlist: .watchlist
        case .learn: .learn
        }
    }
    static func of(_ tab: AppTab) -> AppRoute {
        switch tab {
        case .markets: .markets
        case .portfolio: .portfolio
        case .watchlist: .watchlist
        case .learn: .learn
        default: .home
        }
    }
}

/// The iOS host of the approved tab navigation (Phase 5D.5.6C2, plan §4–5; mirrors Android's `TabNavigator`): five native
/// `NavigationStack(path:)`s, one per tab, and core `TabNavigation` deciding every step — canonical ownership (a screen always opens on its own
/// tab's stack, whichever tab it was opened from), cross-tab hops, tab switches and reselection. The paths are the truth for what is shown; after
/// every change they are synced into the model (`TabNavigation.sync`), and a Back (button or swipe) that pops a hop's entry returns to the tab it
/// came from. In-session only: nothing is written to disk (cold-start restoration stays deferred, D8).
@Observable
final class TabRouter {
    private(set) var selected: AppRoute = .home
    private var paths: [AppRoute: [AppDestination]] = [:]
    private(set) var state: TabNavigationState = TabNavigationState.companion.initial(selected: .home)
    /// An app-wide screen over the tabs (Notifications, digest settings).
    var global: GlobalSheet?
    /// Incremented when a tab is reselected at its root; landing pages scroll to the top (D12).
    private(set) var scrollToTop: [AppRoute: Int] = [:]

    private let nav = TabNavigation.shared

    func path(_ tab: AppRoute) -> [AppDestination] { paths[tab] ?? [] }

    /// The tab bar's selection: a tap on another tab switches (and clears the trail, D2); a tap on the visible tab reselects it (D3/D12).
    var selection: Binding<AppRoute> {
        Binding(get: { self.selected }, set: { tab in tab == self.selected ? self.reselect() : self.selectTab(tab) })
    }

    /// The native stack binding of one tab. A shorter path is a Back (button or swipe-back): when it removes the latest hop's entry, the
    /// visible tab returns to that hop's origin.
    func pathBinding(_ tab: AppRoute) -> Binding<[AppDestination]> {
        Binding(get: { self.path(tab) }, set: { self.setPath($0, for: tab) })
    }

    private func entries(_ tab: AppRoute) -> [NavEntry] {
        [NavEntry(screen: NavigationPolicy.shared.root(tab: tab.tab), key: nil)] + path(tab).map { NavEntry(screen: $0.screen, key: $0.key) }
    }

    private func sync(_ tab: AppRoute) { state = nav.sync(state: state, tab: tab.tab, entries: entries(tab)) }

    private func setPath(_ new: [AppDestination], for tab: AppRoute) {
        let old = path(tab)
        guard new != old else { return }
        paths[tab] = new
        if tab == selected, new.count < old.count, let hop = state.hops.last, hop.tab == tab.tab, Int(hop.depth) == old.count + 1,
           !hop.reused, new.count + 1 < Int(hop.depth) {
            let origin = AppRoute.of(hop.origin)
            sync(tab)
            selected = origin
            state = nav.sync(state: state, tab: origin.tab, entries: entries(origin))
            return
        }
        sync(tab)
    }

    /// Opens a destination on its canonical tab's stack (rule 1–2): switching tabs and recording a hop when it isn't the visible one.
    func open(_ destination: AppDestination) {
        global = nil
        guard let owner = NavigationPolicy.shared.parentTab(screen: destination.screen) else { return }
        let tab = AppRoute.of(owner)
        sync(selected)
        let next = nav.open(state: state, screen: destination.screen, key: destination.key)
        let pushed = next.stack(tab: owner).count > state.stack(tab: owner).count
        if pushed { paths[tab, default: []].append(destination) }
        selected = tab
        // iOS can't observe a Back that pops nothing, so an open onto an identical top keeps no return hop (C3 revisits it).
        state = pushed || next.hops.count == state.hops.count ? next
            : TabNavigationState(stacks: next.stacks, selected: next.selected, hops: Array(next.hops.dropLast()))
        sync(tab)
    }

    /// A tab tap or a programmatic jump to a landing page: that tab's stack is shown as it was; the trail is cleared (D2, rule 6).
    func selectTab(_ tab: AppRoute) {
        guard tab != selected else { reselect(); return }
        sync(selected)
        state = nav.selectTab(state: state, tab: tab.tab)
        selected = tab
        sync(tab)
    }

    /// The visible tab tapped again: back to its root, or at the root scroll to the top (D3/D12).
    func reselect() {
        let tab = selected
        if path(tab).isEmpty { scrollToTop[tab, default: 0] += 1 } else { paths[tab] = [] }
        state = nav.reselect(state: state).first ?? state
        sync(tab)
    }

    /// Back on the visible tab's stack (in-app buttons; the system back button and swipe change the path binding directly).
    func pop() {
        let tab = selected
        guard !path(tab).isEmpty else { return }
        setPath(Array(path(tab).dropLast()), for: tab)
    }

    /// Pops back to the nearest entry matching `predicate` on the visible tab; false when none is on the stack.
    @discardableResult
    func popTo(where predicate: (AppDestination) -> Bool) -> Bool {
        let tab = selected
        guard let index = path(tab).lastIndex(where: predicate) else { return false }
        setPath(Array(path(tab).prefix(index + 1)), for: tab)
        return true
    }

    /// Sign-out or an ended session: every stack back to its root, Home selected, no trail or global screen (I11).
    func reset() {
        paths = [:]
        global = nil
        selected = .home
        state = nav.reset()
    }
}

/// Scrolls a landing page to its top whenever its tab is reselected at the root (D12). Without a router (previews) it does nothing.
struct ScrollToTopOnReselect: ViewModifier {
    @Environment(TabRouter.self) private var router: TabRouter?
    let tab: AppRoute
    @State private var position = ScrollPosition(edge: .top)
    func body(content: Content) -> some View {
        content
            .scrollPosition($position)
            .onChange(of: router?.scrollToTop[tab] ?? 0) { _, _ in withAnimation { position.scrollTo(edge: .top) } }
    }
}

extension View {
    func scrollsToTopOnReselect(_ tab: AppRoute) -> some View { modifier(ScrollToTopOnReselect(tab: tab)) }
}

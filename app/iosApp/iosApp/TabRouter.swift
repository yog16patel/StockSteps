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
    // 5D.5.6C3: formerly nested `navigationDestination`s inside feature scenes, now on the router (one navigation system).
    case companyFinancials(String)
    case companyValuation(String)
    case companyNews(String)
    case newsInsight(symbol: String, articleId: String)
    case stockMovement(String)
    case comparisonResearch
    case comparisonAi(AskAiTarget?)
    case portfolioInsights
    case portfolioTransactions(symbol: String?, accountId: String?)
    case portfolioTransaction(String)
    case holdingDetails(String)

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
        case .companyFinancials: .companyFinancials
        case .companyValuation: .companyValuation
        case .companyNews: .companyNews
        case .newsInsight: .newsInsight
        case .stockMovement: .stockMovement
        case .comparisonResearch: .comparisonResearch
        case .comparisonAi: .comparisonAi
        case .portfolioInsights: .portfolioInsights
        case .portfolioTransactions: .portfolioTransactions
        case .portfolioTransaction: .portfolioTransaction
        case .holdingDetails: .holdingDetails
        }
    }

    /// The short name the back button shows for this screen (5D.5.6C3): "‹ AAPL" back to a company, "‹ Watchlist" back to a landing page.
    var backTitle: String {
        switch self {
        case let .companyDetails(symbol), let .holdingDetails(symbol): symbol
        case let .stockAlerts(symbol): "\(symbol) alerts"
        case .learn(.topics): "Topics"
        case .learn(.term): "Learn"
        case .dailyBrief: "Daily Brief"
        case .briefHistory: "Previous briefs"
        case .practice, .practiceOrder: "Practice"
        case .research: "Research"
        case .earningsCalendar, .earningsEvent, .earningsResults, .earningsDetails: "Earnings"
        case .earningsDigest: "Digest"
        case .earningsReminders: "Reminders"
        case .discover: "Discover"
        case .compare: "Compare"
        case .companyFinancials: "Financials"
        case .companyValuation: "Valuation"
        case .companyNews, .newsInsight: "News"
        case .stockMovement: "Why it moved"
        case .comparisonResearch: "Research"
        case .comparisonAi: "Ask AI"
        case .portfolioInsights: "Insights"
        case .portfolioTransactions: "Transactions"
        case .portfolioTransaction: "Transaction"
        }
    }

    /// The company a destination shows (its company model is kept while any of these is on a stack).
    var symbol: String? {
        switch self {
        case let .companyDetails(s), let .companyFinancials(s), let .companyValuation(s), let .companyNews(s), let .stockMovement(s): s
        case let .newsInsight(s, _): s
        default: nil
        }
    }

    #if DEBUG
    /// One sample per case: every tab screen of core `ScreenId` must have an iOS destination (checked once at launch in debug builds).
    static let samples: [AppDestination] = [
        .companyDetails("AAPL"), .learn(.topics(topic: nil, search: false)), .learn(.term("pe-ratio")), .dailyBrief(BriefTarget(briefId: nil)),
        .briefHistory, .practice, .practiceOrder(PracticeOrderTarget(symbol: "AAPL", sell: false)), .research(ResearchTarget(symbol: "AAPL", name: nil)),
        .earningsCalendar(EarningsCalendarTarget()), .earningsEvent("AAPL:2026-Q3"), .earningsReminders, .earningsResults("AAPL:2026-Q3"), .earningsDigest,
        .earningsDetails("AAPL"), .discover, .compare, .stockAlerts("AAPL"), .companyFinancials("AAPL"), .companyValuation("AAPL"), .companyNews("AAPL"),
        .newsInsight(symbol: "AAPL", articleId: "a"), .stockMovement("AAPL"), .comparisonResearch, .comparisonAi(nil), .portfolioInsights,
        .portfolioTransactions(symbol: nil, accountId: nil), .portfolioTransaction("t"), .holdingDetails("AAPL")
    ]
    #endif

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
    /// Bumped on every path change (company models are released when their symbol leaves every stack).
    private(set) var revision = 0

    init() {
        #if DEBUG
        let tabScreens = Set(ScreenId.entries.filter { $0.kind == .tabScreen })
        assert(tabScreens.isSubset(of: Set(AppDestination.samples.map(\.screen))), "a tab screen has no AppDestination")
        #endif
    }

    /// Symbols of every company destination on any stack.
    var symbolsInUse: Set<String> { Set(paths.values.flatMap { $0.compactMap(\.symbol) }) }

    /// The navigation title of a tab's landing page, which names its first pushed screen's back button (5D.5.6C3). While that first screen was
    /// opened from another tab (a hop), the button names where Back goes: the origin tab's visible screen ("‹ Home", "‹ AAPL").
    func rootTitle(_ tab: AppRoute) -> String {
        if let hop = state.hops.first(where: { $0.tab == tab.tab && Int($0.depth) == 2 }) { return visibleTitle(AppRoute.of(hop.origin)) }
        return tab.tab.label
    }

    /// Whether `destination` on `tab` is a hop's entry with other screens below it, whose native back label would name the wrong place.
    func hidesBackTitle(_ destination: AppDestination, on tab: AppRoute) -> Bool {
        let p = path(tab)
        return state.hops.contains { hop in hop.tab == tab.tab && Int(hop.depth) > 2 && Int(hop.depth) - 2 < p.count && p[Int(hop.depth) - 2] == destination }
    }

    private func visibleTitle(_ tab: AppRoute) -> String { path(tab).last?.backTitle ?? tab.tab.label }

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
        defer { revision += 1 }
        // One Back (button or swipe) on the latest hop's entry: core `back` returns to the hop's origin. A reused entry (it was already on top of
        // this tab before the hop) stays on its stack, so the native pop is undone while the tab switches away (I1, no duplicate).
        if tab == selected, new.count == old.count - 1, Array(old.dropLast()) == new,
           let hop = state.hops.last, hop.tab == tab.tab, Int(hop.depth) == old.count + 1 {
            let next = nav.back(state: state)
            paths[tab] = new
            selected = AppRoute.of(next.selected)
            state = next
            sync(selected)
            // SwiftUI ignores a path restored inside its own pop, so the reused entry goes back on the next turn, after the tab switched away.
            if hop.reused { DispatchQueue.main.async { if self.path(tab) == new { self.paths[tab] = old; self.revision += 1 } } }
            return
        }
        paths[tab] = new
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
        // 5D.5.6C3: an identical top is reused (no duplicate) and its hop kept; Back on it returns to the origin (see `setPath`).
        state = next
        sync(tab)
        revision += 1
    }

    /// Opens once a sheet the call came from has finished closing (SwiftUI drops a navigation started mid-dismissal).
    func open(_ destination: AppDestination, afterSheet: Bool) {
        guard afterSheet else { open(destination); return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { self.open(destination) }
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
        if path(tab).isEmpty { scrollToTop[tab, default: 0] += 1 } else { paths[tab] = []; revision += 1 }
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
        revision += 1
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

/// 5D.5.6C3: one company model (and its details client) per symbol, shared by Company Details and its Financials, Valuation, News, News insight and
/// Why-it-moved pages now that they are separate router destinations — they reuse the data Company Details already loaded instead of a second
/// client. A model is released when its symbol is on no stack any more.
@MainActor
final class CompanyModelStore {
    private var models: [String: CompanyDetailsModel] = [:]
    private let baseURL: () -> String
    init(baseURL: @escaping () -> String) { self.baseURL = baseURL }
    func model(_ symbol: String) -> CompanyDetailsModel {
        if let model = models[symbol] { return model }
        let model = CompanyDetailsModel(symbol: symbol, baseURL: baseURL)
        models[symbol] = model
        return model
    }
    func retain(only symbols: Set<String>) { models = models.filter { symbols.contains($0.key) } }
}

/// 5D.5.6C3 lifecycle rule: with five live tab stacks a pushed screen reappears on every tab switch, so work that fetches runs once per screen
/// (and again only when `id` changes) — never just because the view came back on screen. Pull-to-refresh and Retry stay explicit.
private struct TaskOnce<ID: Equatable>: ViewModifier {
    let id: ID
    let action: () async -> Void
    @State private var done: ID?
    func body(content: Content) -> some View {
        content.task(id: id) {
            guard done != id else { return }
            done = id
            await action()
        }
    }
}

extension View {
    func taskOnce<ID: Equatable>(id: ID, _ action: @escaping () async -> Void) -> some View { modifier(TaskOnce(id: id, action: action)) }
    func taskOnce(_ action: @escaping () async -> Void) -> some View { modifier(TaskOnce(id: 0, action: action)) }
}

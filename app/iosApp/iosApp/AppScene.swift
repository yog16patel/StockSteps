import SwiftUI
import Shared

struct AppScene: View {
    @State private var searchModel: StockSearchViewModel
    @State private var homeModel: HomeViewModel
    @State private var marketsModel: MarketsModel
    @State private var watchlistsModel: WatchlistsModel
    /// Alerts screen target: a symbol, or "" for all alerts.
    @State private var alertsTarget: String?
    let accounts: AccountViewModel
    var appLock: AppLockModel?
    @State private var showingAuth = false
    @State private var showingSearch = false
    @State private var initialStock: StockSearchResult?
    @State private var selectedTab = AppRoute.home
    /// Symbol of the Company Details page pushed on the main stack (Home, Watchlist and Search share it).
    @State private var detailsSymbol: String?


    @AppStorage(BackendSettings.storageKey) private var backendEnvironment = BackendSettings.real

    init(baseURL: @escaping () -> String = { BackendSettings.currentURL }, accounts: AccountViewModel, appLock: AppLockModel? = nil) {
        self.accounts = accounts
        self.appLock = appLock
        _searchModel = State(initialValue: StockSearchViewModel(service: StockSearchService(baseURL: baseURL)))
        _homeModel = State(initialValue: HomeViewModel(service: HomeQuoteService(baseURL: baseURL)))
        _marketsModel = State(initialValue: MarketsModel(baseURL: baseURL))
        _watchlistsModel = State(initialValue: WatchlistsModel(client: accounts.client))
    }

    var body: some View {
        NavigationStack {
            TabView(selection: $selectedTab) {
                HomeScene(model: homeModel, accounts: accounts, onLearn: { selectedTab = .learn }) { symbol in
                    detailsSymbol = symbol
                }
                .tabItem { Label("Home", systemImage: "house") }
                .tag(AppRoute.home)

                MarketsScene(model: marketsModel, onOpenStock: explore, onSearch: { initialStock = nil; showingSearch = true })
                    .tabItem { Label("Markets", systemImage: "chart.line.uptrend.xyaxis") }
                    .tag(AppRoute.markets)

                WatchListScene(accounts: accounts, model: watchlistsModel, onSignIn: { showingAuth = true }, onSearch: { initialStock = nil; showingSearch = true },
                               onExplore: explore, onOpenAlerts: { alertsTarget = $0 ?? "" })
                    .tabItem { Label("Watchlist", systemImage: "star") }
                    .tag(AppRoute.watchlist)

                LearnScene()
                    .tabItem { Label("Learn", systemImage: "book") }
                    .tag(AppRoute.learn)

                SettingsScene(model: accounts, onSignIn: { showingAuth = true }, appLock: appLock)
                    .tabItem { Label("Settings", systemImage: "gearshape") }
                    .tag(AppRoute.settings)
            }
            .tint(StockStepsTheme.color(ThemeColors.shared.light.primary))
            // Home and Settings show their own compact headers instead of a navigation title.
            .stockStepsTopBar(.screen(tabTitle, visible: selectedTab != .home && selectedTab != .markets && selectedTab != .watchlist && selectedTab != .settings))
            // Every client reads the URL per request; reload Home so its data matches the new backend.
            .navigationDestination(item: $detailsSymbol) { symbol in
                CompanyDetailsScene(symbol: symbol, accounts: accounts, watchlists: watchlistsModel)
            }
            .navigationDestination(item: $alertsTarget) { target in
                AlertsScene(model: watchlistsModel, symbol: target.isEmpty ? nil : target, onOpenStock: explore)
            }
            // Tapping an alert notification opens that stock's alerts.
            .onReceive(NotificationCenter.default.publisher(for: .stockStepsOpenAlerts)) { note in
                if let symbol = note.object as? String { alertsTarget = symbol }
            }
            .onChange(of: backendEnvironment) { _, _ in
                homeModel.refreshIndices()
                homeModel.refreshNews()
                marketsModel.reset()
                // Watchlists, notes, alerts and the push registration follow the selected backend.
                accounts.client?.setEnvironment(environment: BackendSettings.environment)
            }
        }
        .sheet(isPresented: $showingAuth) {
            NavigationStack {
                AuthScene(model: accounts, onDone: { showingAuth = false })
                    .stockStepsTopBar(
                        .screen("Sign in", backButton: .close, backEnabled: !accounts.state.busy),
                        onBack: { showingAuth = false }
                    )
            }.interactiveDismissDisabled(accounts.state.busy)
        }
        .sheet(isPresented: $showingSearch) {
            StockSearchScene(model: searchModel, accounts: accounts, initialStock: initialStock, onClose: { showingSearch = false },
                             onOpenStock: { stock in
                                 // Close search, then push the shared Company Details page.
                                 showingSearch = false
                                 detailsSymbol = stock.symbol
                             })
        }
    }
    private var tabTitle: String {
        switch selectedTab {
        case .home: "Home"
        case .markets: "Markets"
        case .watchlist: "Watchlist"
        case .learn: "Learn"
        case .settings: "Settings"
        }
    }
    private func explore(_ symbol: String) {
        detailsSymbol = symbol
    }
}

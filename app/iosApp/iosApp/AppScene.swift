import SwiftUI
import Shared

struct AppScene: View {
    @State private var searchModel: StockSearchViewModel
    @State private var homeModel: HomeViewModel
    let accounts: AccountViewModel
    @State private var showingAuth = false
    @State private var showingSearch = false
    @State private var initialStock: StockSearchResult?
    @State private var selectedTab = AppRoute.home


    init(baseURL: String = "http://localhost:8080", accounts: AccountViewModel) {
        self.accounts = accounts
        _searchModel = State(initialValue: StockSearchViewModel(service: StockSearchService(baseURL: baseURL)))
        _homeModel = State(initialValue: HomeViewModel(service: HomeQuoteService(baseURL: baseURL)))
    }

    var body: some View {
        NavigationStack {
            TabView(selection: $selectedTab) {
                HomeScene(model: homeModel, accounts: accounts, onLearn: { selectedTab = .learn }) { symbol in
                    initialStock = accounts.state.items.first { $0.symbol == symbol }?.listing() ?? StockSearchResult(symbol: symbol, name: symbol, currency: nil, exchange: nil, exchangeFullName: nil)
                    searchModel.query = symbol
                    searchModel.scheduleSearch()
                    showingSearch = true
                }
                .tabItem { Label("Home", systemImage: "house") }
                .tag(AppRoute.home)

                WatchListScene(model: accounts, onSignIn: { showingAuth = true }, onSearch: { initialStock = nil; showingSearch = true }, onExplore: explore)
                    .tabItem { Label("Watchlist", systemImage: "star") }
                    .tag(AppRoute.watchlist)

                LearnScene()
                    .tabItem { Label("Learn", systemImage: "book") }
                    .tag(AppRoute.learn)

                SettingsScene(model: accounts, onSignIn: { showingAuth = true })
                    .tabItem { Label("Settings", systemImage: "gearshape") }
                    .tag(AppRoute.settings)
            }
            .tint(StockStepsTheme.color(ThemeColors.shared.light.primary))
            // Home and Settings show their own compact headers instead of a navigation title.
            .stockStepsTopBar(.screen(tabTitle, visible: selectedTab != .home && selectedTab != .settings))
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
            StockSearchScene(model: searchModel, accounts: accounts, initialStock: initialStock, onClose: { showingSearch = false })
        }
    }
    private var tabTitle: String {
        switch selectedTab {
        case .home: "Home"
        case .watchlist: "Watchlist"
        case .learn: "Learn"
        case .settings: "Settings"
        }
    }
    private func explore(_ symbol: String) {
        initialStock = accounts.state.items.first { $0.symbol == symbol }?.listing() ?? StockSearchResult(symbol: symbol, name: symbol, currency: nil, exchange: nil, exchangeFullName: nil)
        searchModel.query = symbol
        searchModel.scheduleSearch()
        showingSearch = true
    }
}

import SwiftUI
import Shared

struct AppScene: View {
    @State private var searchModel: StockSearchViewModel
    @State private var homeModel: HomeViewModel
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
    }

    var body: some View {
        NavigationStack {
            TabView(selection: $selectedTab) {
                HomeScene(model: homeModel, accounts: accounts, onLearn: { selectedTab = .learn }) { symbol in
                    detailsSymbol = symbol
                }
                .tabItem { Label("Home", systemImage: "house") }
                .tag(AppRoute.home)

                WatchListScene(model: accounts, onSignIn: { showingAuth = true }, onSearch: { initialStock = nil; showingSearch = true }, onExplore: explore)
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
            .stockStepsTopBar(.screen(tabTitle, visible: selectedTab != .home && selectedTab != .settings))
            // Every client reads the URL per request; reload Home so its data matches the new backend.
            .navigationDestination(item: $detailsSymbol) { symbol in
                CompanyDetailsScene(symbol: symbol, accounts: accounts)
            }
            .onChange(of: backendEnvironment) { _, _ in
                homeModel.refreshIndices()
                homeModel.refreshNews()
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
        case .watchlist: "Watchlist"
        case .learn: "Learn"
        case .settings: "Settings"
        }
    }
    private func explore(_ symbol: String) {
        detailsSymbol = symbol
    }
}

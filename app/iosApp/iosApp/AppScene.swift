import SwiftUI

struct AppScene: View {
    @State private var searchModel: StockSearchViewModel
    @State private var discoveryModel: DiscoveryViewModel
    let accounts: AccountViewModel
    @State private var showingAuth = false
    @State private var showingSearch = false
    @State private var selectedTab = AppRoute.home


    init(baseURL: String = "http://localhost:8080", accounts: AccountViewModel) {
        self.accounts = accounts
        _searchModel = State(initialValue: StockSearchViewModel(service: StockSearchService(baseURL: baseURL)))
        _discoveryModel = State(initialValue: DiscoveryViewModel(service: DiscoveryService(baseURL: baseURL)))
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            DiscoveryScene(model: discoveryModel, onSearch: { showingSearch = true }) { symbol in
                searchModel.query = symbol
                searchModel.scheduleSearch()
                showingSearch = true
            }
            .tabItem { Label("Home", systemImage: "house") }
            .tag(AppRoute.home)

            WatchListScene(model: accounts, onSignIn: { showingAuth = true }, onSearch: { showingSearch = true }, onExplore: explore)
                .tabItem { Label("WatchList", systemImage: "star") }
                .tag(AppRoute.watchlist)

            LearnScene()
                .tabItem { Label("Learn", systemImage: "book") }
                .tag(AppRoute.learn)

            SettingsScene(model: accounts, onSignIn: { showingAuth = true })
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(AppRoute.settings)
        }
        .sheet(isPresented: $showingAuth) {
            NavigationStack {
                AuthScene(model: accounts, onDone: { showingAuth = false })
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Cancel") { showingAuth = false }.disabled(accounts.state.busy)
                        }
                    }
            }.interactiveDismissDisabled(accounts.state.busy)
        }
        .sheet(isPresented: $showingSearch) {
            StockSearchScene(model: searchModel, accounts: accounts)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { showingSearch = false }
                    }
                }
        }
    }
    private func explore(_ symbol: String) {
        searchModel.query = symbol
        searchModel.scheduleSearch()
        showingSearch = true
    }
}

import SwiftUI

struct AppScene: View {
    @State private var searchModel: StockSearchViewModel
    @State private var discoveryModel: DiscoveryViewModel
    @State private var showingSearch = false
    @State private var selectedTab = AppRoute.home


    init(baseURL: String = "http://localhost:8080") {
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

            WatchListScene()
                .tabItem { Label("WatchList", systemImage: "star") }
                .tag(AppRoute.watchlist)

            LearnScene()
                .tabItem { Label("Learn", systemImage: "book") }
                .tag(AppRoute.learn)

            SettingsScene()
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(AppRoute.settings)
        }
        .sheet(isPresented: $showingSearch) {
            StockSearchScene(model: searchModel)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { showingSearch = false }
                    }
                }
        }
    }
}

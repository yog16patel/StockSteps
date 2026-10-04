import SwiftUI

struct AppScene: View {
    @State private var searchModel: StockSearchViewModel
    @State private var discoveryModel: DiscoveryViewModel
    @State private var selectedTab = AppRoute.home


    init(baseURL: String = "http://localhost:8080") {
        _searchModel = State(initialValue: StockSearchViewModel(service: StockSearchService(baseURL: baseURL)))
        _discoveryModel = State(initialValue: DiscoveryViewModel(service: DiscoveryService(baseURL: baseURL)))
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            DiscoveryScene(model: discoveryModel) { symbol in
                searchModel.query = symbol
                searchModel.scheduleSearch()
                selectedTab = .search
            }
            .tabItem { Label("Home", systemImage: "house") }
            .tag(AppRoute.home)

            StockSearchScene(model: searchModel)
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
                .tag(AppRoute.search)
        }
    }
}

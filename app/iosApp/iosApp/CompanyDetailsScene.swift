import Shared
import SwiftUI

/// The one Company Details destination on iOS; Home, Watchlist and Search all push it by symbol.
struct CompanyDetailsScene: View {
    let symbol: String
    let accounts: AccountViewModel
    var watchlists: WatchlistsModel?
    @State private var choosingList = false
    @State private var model: CompanyDetailsModel
    @State private var showFinancials = false
    @State private var showNews = false
    @State private var showValuation = false
    @State private var showMovement = false
    @State private var showPortfolio = false
    @State private var showCompare = false
    @State private var compareModel: ScreenerModel?
    @State private var earningsModel: EarningsModel?
    @State private var showEarnings = false
    @State private var portfolioModel = PortfolioViewModel()
    var learning: LearningModel?
    var onSearch: () -> Void = {}
    var onLearn: () -> Void = {}
    var onUpgrade: () -> Void = {}
    @State private var research: ResearchTarget?
    @Environment(\.openURL) private var openURL

    init(symbol: String, accounts: AccountViewModel, watchlists: WatchlistsModel? = nil, learning: LearningModel? = nil,
         onSearch: @escaping () -> Void = {}, onLearn: @escaping () -> Void = {}, onUpgrade: @escaping () -> Void = {},
         baseURL: @escaping () -> String = { BackendSettings.currentURL }) {
        self.symbol = symbol
        self.accounts = accounts
        self.watchlists = watchlists
        self.learning = learning
        self.onSearch = onSearch
        self.onLearn = onLearn
        self.onUpgrade = onUpgrade
        _model = State(initialValue: CompanyDetailsModel(symbol: symbol, baseURL: baseURL))
    }

    var body: some View {
        CompanyDetailsScreen(
            model: model,
            watched: accounts.state.items.contains { $0.symbol == symbol },
            watchlistEnabled: !accounts.state.initializing,
            onToggleWatchlist: {
                // Several lists: choose where to save it (removing still removes it from every list).
                let saved = accounts.state.items.contains { $0.symbol == symbol }
                if !saved, accounts.state.user != nil, (watchlists?.watchlists.count ?? 0) > 1 { choosingList = true; return }
                // The same watchlist used by the Watchlist tab.
                let name = model.overview.value?.name ?? symbol
                Task { await accounts.toggle(stock: StockSearchResult(symbol: symbol, name: name, currency: nil, exchange: nil, exchangeFullName: nil)) }
            },
            onOpenURL: { openURL($0) },
            onOpenFinancials: { showFinancials = true },
            onOpenValuation: { showValuation = true },
            onOpenNews: { showNews = true },
            onOpenMovement: { showMovement = true },
            onAddPortfolio: { showPortfolio = true },
            onCompare: {
                // Adds this company to the shared selection, then opens Compare in this stack.
                SharedComparisonSelection.shared.instance.add(symbol: symbol, name: model.overview.value?.name ?? symbol)
                if compareModel == nil { compareModel = ScreenerModel(accounts: accounts) }
                showCompare = true
            },
            onEarnings: {
                if earningsModel == nil { earningsModel = EarningsModel(accounts: accounts) }
                showEarnings = true
            },
            researchCompleted: learning?.completed(symbol),
            showResearch: learning != nil,
            onResearch: { research = ResearchTarget(symbol: symbol, name: model.overview.value?.name) }
        )
        .navigationDestination(item: $research) { target in
            if let learning {
                GuidedResearchScene(target: target, learning: learning, accounts: accounts, onCompany: { _ in research = nil },
                                    onResearchAnother: onSearch, onContinueLearning: onLearn, onUpgrade: onUpgrade)
            }
        }
        .navigationDestination(isPresented: $showEarnings) {
            if let earningsModel { EarningsDetailsScene(symbol: symbol, client: earningsModel.client, onCompany: { showEarnings = false }) }
        }
        .navigationDestination(isPresented: $showCompare) {
            if let compareModel { CompareStocksScene(model: compareModel, onOpenStock: { _ in showCompare = false }) }
        }
        // Deeper destinations stay inside the same navigation stack, so Back returns here.
        .navigationDestination(isPresented: $showFinancials) { CompanyFinancialsScene(symbol: symbol, client: model.client) }
        .navigationDestination(isPresented: $showNews) { CompanyNewsScene(symbol: symbol, client: model.client) }
        .navigationDestination(isPresented: $showValuation) { CompanyValuationScene(symbol: symbol, client: model.client) }
        .navigationDestination(isPresented: $showMovement) { StockMovementScene(symbol: symbol, client: model.client) }
        .task {
            accounts.client?.recordViewedCompany(symbol: symbol)
            if model.overview.value == nil { model.load() }
        }
        .sheet(isPresented: $showPortfolio) {
            PortfolioEntryScene(accounts: accounts, model: portfolioModel, existing: nil,
                initialInstrument: InstrumentRef(symbol: symbol, name: model.overview.value?.name, exchange: nil, currency: nil))
        }
        .sheet(isPresented: $choosingList) {
            WatchlistChooser(symbol: symbol, lists: watchlists?.watchlists ?? []) { list in
                choosingList = false
                let instrument = InstrumentRef(symbol: symbol, name: model.overview.value?.name, exchange: nil, currency: nil)
                watchlists?.perform { try await $0.addEntry(listId: list.id, instrument: instrument) }
            }
        }
    }
}

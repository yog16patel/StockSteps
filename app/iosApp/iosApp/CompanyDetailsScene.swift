import Shared
import SwiftUI

/// The one Company Details destination on iOS; Home, Watchlist and Search all push it by symbol.
struct CompanyDetailsScene: View {
    let symbol: String
    let accounts: AccountViewModel
    var watchlists: WatchlistsModel?
    @State private var choosingList = false
    /// 5D.5.6C3: deeper pages (Financials, Valuation, News, Why it moved, Earnings, Compare, Learn terms, Research) open through the tab router
    /// on their canonical tab's stack; Back returns here.
    @Environment(TabRouter.self) private var router: TabRouter?
    @State private var model: CompanyDetailsModel
    @State private var showPortfolio = false
    @State private var earningsModel: EarningsModel?
    /// Viewed-company history is recorded once per visit, not on every reappearance after a tab switch (5D.5.6C3 lifecycle audit).
    @State private var recordedView = false
    @State private var nextEarnings: CompanyEarningsModel?
    @State private var reminderTarget: ReminderTargetInfo?
    /// Opens the app-level Earnings Calendar on a date (nil = today); nil hides the action.
    var onEarningsCalendar: ((String?) -> Void)? = nil
    var onEarningsResults: ((String) -> Void)? = nil
    @State private var portfolioModel = PortfolioViewModel()
    var learning: LearningModel?
    var onSearch: () -> Void = {}
    var onLearn: () -> Void = {}
    var onUpgrade: () -> Void = {}
    var onPracticeBuy: (String) -> Void = { _ in }
    /// Not `@Environment(\.openURL)`: see `ExternalURLOpener` (that environment value re-rendered this scene in a loop once a destination was pushed).
    private let openURL = ExternalURLOpener()

    init(symbol: String, accounts: AccountViewModel, watchlists: WatchlistsModel? = nil, learning: LearningModel? = nil,
         onSearch: @escaping () -> Void = {}, onLearn: @escaping () -> Void = {}, onUpgrade: @escaping () -> Void = {},
         onPracticeBuy: @escaping (String) -> Void = { _ in },
         onEarningsCalendar: ((String?) -> Void)? = nil,
         onEarningsResults: ((String) -> Void)? = nil,
         model: CompanyDetailsModel? = nil, earnings: EarningsModel? = nil,
         baseURL: @escaping () -> String = { BackendSettings.currentURL }) {
        self.onEarningsCalendar = onEarningsCalendar
        self.onEarningsResults = onEarningsResults
        self.symbol = symbol
        self.accounts = accounts
        self.watchlists = watchlists
        self.learning = learning
        self.onSearch = onSearch
        self.onLearn = onLearn
        self.onUpgrade = onUpgrade
        self.onPracticeBuy = onPracticeBuy
        // The app's shared company model (its sub-pages reuse it) and earnings model (one cached next-earnings request, shared with Earnings).
        _model = State(initialValue: model ?? CompanyDetailsModel(symbol: symbol, baseURL: baseURL))
        _earningsModel = State(initialValue: earnings)
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
                Task { await accounts.toggle(stock: StockSearchResult(symbol: symbol, name: name, currency: nil, exchange: nil, exchangeFullName: nil, logoUrl: nil)) }
            },
            onOpenURL: { openURL($0) },
            onOpenFinancials: { router?.open(.companyFinancials(symbol)) },
            onOpenValuation: { router?.open(.companyValuation(symbol)) },
            onOpenNews: { router?.open(.companyNews(symbol)) },
            onOpenMovement: { router?.open(.stockMovement(symbol)) },
            onAddPortfolio: { showPortfolio = true },
            onCompare: {
                // Adds this company to the shared selection, then opens Compare (Markets).
                SharedComparisonSelection.shared.instance.add(symbol: symbol, name: model.overview.value?.name ?? symbol)
                router?.open(.compare)
            },
            onEarnings: { router?.open(.earningsDetails(symbol)) },
            researchCompleted: learning?.completed(symbol),
            showResearch: learning != nil,
            onResearch: { router?.open(.research(ResearchTarget(symbol: symbol, name: model.overview.value?.name))) },
            onPracticeBuy: { onPracticeBuy(symbol) },
            nextEarnings: nextEarnings?.state,
            onEarningsCalendar: onEarningsCalendar.map { open in { open(nextEarnings?.state?.date) } },
            onRetryEarnings: { nextEarnings?.presenter.refresh() },
            onLatestResults: onEarningsResults.map { open in { if let id = nextEarnings?.state?.latestReportId { open(id) } } },
            earningsReminder: nextEarnings?.state?.eventId.flatMap { id in earningsModel?.reminders.control(id) },
            onRemindEarnings: {
                if let e = nextEarnings?.state, let id = e.eventId {
                    reminderTarget = ReminderTargetInfo(eventId: id, symbol: symbol, name: model.overview.value?.name ?? symbol, dateText: e.dateText ?? "",
                                                        timingText: e.timingText, dateNote: e.statusText?.components(separatedBy: " · ").dropFirst().first)
                }
            },
            onLearnTerm: { router?.open(.learn(.term($0))) }
        )
        .sheet(item: $reminderTarget) { target in
            if let reminders = earningsModel?.reminders { EarningsReminderSheet(target: target, model: reminders) { reminderTarget = nil } }
        }
        .task {
            // One cached request for the next earnings date (the client is shared with Earnings Details).
            if earningsModel == nil { earningsModel = EarningsModel(accounts: accounts) }
            if nextEarnings == nil, let client = earningsModel?.client { nextEarnings = CompanyEarningsModel(symbol: symbol, client: client) }
            if !recordedView { recordedView = true; accounts.client?.recordViewedCompany(symbol: symbol) }
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

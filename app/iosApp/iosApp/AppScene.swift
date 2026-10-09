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
    @State private var showingSettings = false
    @State private var initialStock: StockSearchResult?
    @State private var selectedTab = AppRoute.home
    /// Symbol of the Company Details page pushed on the main stack (Home, Watchlist and Search share it).
    @State private var detailsSymbol: String?
    @State private var showingDiscover = false
    @State private var showingCompare = false
    @State private var screenerModel: ScreenerModel
    @State private var earningsModel: EarningsModel
    /// Earnings Calendar target (Markets, Company Details, Watchlist, an event).
    @State private var earningsCalendar: EarningsCalendarTarget?
    /// Earnings Event Details (calendar rows, Daily Brief).
    @State private var earningsEventId: String?
    /// Earnings Details (results and history) for a company.
    @State private var earningsSymbol: String?
    /// Earnings Results for one published report ("SYMBOL:YYYY-Qn").
    @State private var earningsResultsId: String?
    /// Earnings Reminders settings (from Settings or the Watchlist shortcut).
    @State private var showingReminderSettings = false
    @State private var showingEarningsReminders = false
    /// Phase 5: StockSteps+ earnings digest and its settings.
    @State private var showingEarningsDigest = false
    @State private var showingDigestSettings = false
    @State private var showingSettingsDigest = false
    @State private var learningModel: LearningModel
    @State private var researchTarget: ResearchTarget?
    @State private var practiceModel: PracticeModel?
    @State private var showingPractice = false
    @State private var practiceTarget: PracticeOrderTarget?
    /// Search opened from Practice: picking a company opens its simulated order directly.
    @State private var searchForPractice = false
    @State private var briefModel: BriefModel?
    @State private var briefTarget: BriefTarget?
    @State private var showingBriefHistory = false


    @AppStorage(BackendSettings.storageKey) private var backendEnvironment = BackendSettings.real

    init(baseURL: @escaping () -> String = { BackendSettings.currentURL }, accounts: AccountViewModel, appLock: AppLockModel? = nil) {
        self.accounts = accounts
        self.appLock = appLock
        _searchModel = State(initialValue: StockSearchViewModel(service: StockSearchService(baseURL: baseURL)))
        _homeModel = State(initialValue: HomeViewModel())
        _marketsModel = State(initialValue: MarketsModel(baseURL: baseURL))
        _watchlistsModel = State(initialValue: WatchlistsModel(client: accounts.client))
        // SwiftUI runs this init on every parent update but keeps only the first @State values, so these models must not observe or load
        // here; the kept instances are activated once in `.task` (Phase 5C: discarded copies used to repeat screener/earnings/brief requests).
        _screenerModel = State(initialValue: ScreenerModel(accounts: accounts, baseURL: baseURL, start: false))
        _earningsModel = State(initialValue: EarningsModel(accounts: accounts, baseURL: baseURL, start: false))
        _learningModel = State(initialValue: LearningModel(accounts: accounts, baseURL: baseURL, start: false))
        _briefModel = State(initialValue: accounts.client.map { BriefModel(account: $0, start: false) })
    }

    var body: some View {
        NavigationStack {
            TabView(selection: $selectedTab) {
                Group {
                    if appLock?.locked != true {
                        HomeScene(
                            model: homeModel,
                            accounts: accounts,
                            onLearn: { selectedTab = .learn },
                            onSearch: { initialStock = nil; showingSearch = true },
                            onWatchlist: { selectedTab = .watchlist },
                            onSettings: { showingSettings = true },
                            onPortfolio: { selectedTab = .portfolio },
                            onAlerts: { alertsTarget = $0 ?? "" },
                            onExplore: explore,
                            onEarnings: { earningsSymbol = $0 },
                            learning: learningModel,
                            onResearch: { researchTarget = $0 },
                            onPractice: openPractice,
                            briefModel: briefModel,
                            onDailyBrief: { briefTarget = BriefTarget(briefId: nil) }
                        )
                    } else {
                        Color.clear
                    }
                }
                .tabItem { Label("Home", systemImage: "house") }
                .tag(AppRoute.home)

                MarketsScene(model: marketsModel, onOpenStock: explore, onSearch: { initialStock = nil; showingSearch = true },
                             onDiscover: { showingDiscover = true }, onCompare: { showingCompare = true }, onEarnings: { earningsCalendar = EarningsCalendarTarget() }, earnings: earningsModel,
                             brief: briefModel, onDailyBrief: { briefTarget = BriefTarget(briefId: nil) }, onBriefHistory: { showingBriefHistory = true })
                    .tabItem { Label("Markets", systemImage: "chart.line.uptrend.xyaxis") }
                    .tag(AppRoute.markets)

                Group {
                    if appLock?.locked != true {
                        PortfolioScene(accounts: accounts, watchlists: watchlistsModel, onCompany: explore,
                            onSignIn: { showingAuth = true }, onAlerts: { alertsTarget = $0 }, onPractice: openPractice)
                    } else { Color.clear }
                }
                .tabItem { Label("Portfolio", systemImage: "briefcase") }
                .tag(AppRoute.portfolio)

                WatchListScene(accounts: accounts, model: watchlistsModel, onSignIn: { showingAuth = true }, onSearch: { initialStock = nil; showingSearch = true },
                               onExplore: explore, onOpenAlerts: { alertsTarget = $0 ?? "" },
                               onEarnings: { earningsCalendar = EarningsCalendarTarget(filter: "WATCHLIST") },
                               onEarningsReminders: { showingEarningsReminders = true },
                               onCompare: { symbols, names in screenerModel.client.compareCompanies(symbols: symbols, names: names); showingCompare = true })
                    .tabItem { Label("Watchlist", systemImage: "star") }
                    .tag(AppRoute.watchlist)

                LearnScene(learning: learningModel, onResearch: { researchTarget = $0 }, onSearch: { initialStock = nil; showingSearch = true }, onPractice: openPractice)
                    .tabItem { Label("Learn", systemImage: "book") }
                    .tag(AppRoute.learn)

            }
            .tint(StockStepsTheme.color(ThemeColors.shared.light.primary))
            .task {
                screenerModel.activate(); earningsModel.activate(); learningModel.activate(); briefModel?.activate()
            }
            // Home and Settings show their own compact headers instead of a navigation title.
            .stockStepsTopBar(.screen(tabTitle, visible: selectedTab != .home && selectedTab != .markets && selectedTab != .watchlist && selectedTab != .portfolio))
            // Every client reads the URL per request; reload Home so its data matches the new backend.
            .navigationDestination(item: $detailsSymbol) { symbol in
                CompanyDetailsScene(symbol: symbol, accounts: accounts, watchlists: watchlistsModel, learning: learningModel,
                                    onSearch: { initialStock = nil; showingSearch = true }, onLearn: { detailsSymbol = nil; selectedTab = .learn },
                                    onUpgrade: { showingSettings = true }, onPracticeBuy: { practiceTrade(PracticeOrderTarget(symbol: $0, sell: false)) },
                                    onEarningsCalendar: { earningsCalendar = EarningsCalendarTarget(date: $0) },
                                    onEarningsResults: { earningsResultsId = $0 })
            }
            .navigationDestination(item: $briefTarget) { target in
                if let briefModel {
                    DailyBriefScene(target: target, model: briefModel, onOpenStock: explore, onHistory: { showingBriefHistory = true },
                                    onWatchlist: { briefTarget = nil; selectedTab = .watchlist }, onLearn: { briefTarget = nil; selectedTab = .learn },
                                    onSignIn: { showingAuth = true }, onUpgrade: { showingSettings = true },
                                    onOpenEarnings: { earningsEventId = $0 }, onOpenResults: { earningsResultsId = $0 },
                                    onOpenDigest: { showingEarningsDigest = true })
                }
            }
            .navigationDestination(isPresented: $showingBriefHistory) {
                if let briefModel {
                    DailyBriefHistoryScene(model: briefModel, onOpenBrief: { id in showingBriefHistory = false; briefTarget = BriefTarget(briefId: id) },
                                           onUpgrade: { showingSettings = true })
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .stockStepsOpenBrief)) { note in
                let id = note.object as? String
                briefTarget = BriefTarget(briefId: (id?.isEmpty ?? true) ? nil : id)
            }
            .navigationDestination(isPresented: $showingPractice) {
                if let practiceModel {
                    PracticeScene(model: practiceModel, onBuy: { initialStock = nil; searchForPractice = true; showingSearch = true }, onExplore: { showingDiscover = true },
                                  onLearn: { showingPractice = false; selectedTab = .learn }, onSignIn: { showingAuth = true }, onCompany: explore,
                                  onTrade: { practiceTrade($0) }, onManagePlan: { showingSettings = true })
                }
            }
            .navigationDestination(item: $practiceTarget) { target in
                if let practiceModel {
                    PracticeOrderScene(target: target, practice: practiceModel,
                                       onViewHoldings: { practiceTarget = nil; showingPractice = true },
                                       onExplore: { practiceTarget = nil; showingDiscover = true },
                                       onLearn: { practiceTarget = nil; selectedTab = .learn })
                }
            }
            .navigationDestination(item: $researchTarget) { target in
                GuidedResearchScene(target: target, learning: learningModel, accounts: accounts, onCompany: explore,
                                    onResearchAnother: { initialStock = nil; showingSearch = true },
                                    onContinueLearning: { researchTarget = nil; selectedTab = .learn },
                                    onUpgrade: { showingSettings = true })
            }
            .navigationDestination(item: $earningsCalendar) { target in
                EarningsCalendarScene(target: target, client: earningsModel.client, onOpen: { earningsEventId = $0 }, onSignIn: { showingAuth = true },
                                      onOpenResults: { earningsResultsId = $0 }, reminders: earningsModel.reminders,
                                      onDigest: accounts.client == nil ? nil : { showingEarningsDigest = true })
                    .id(target.id)
            }
            .navigationDestination(item: $earningsEventId) { id in
                EarningsEventScene(eventId: id, client: earningsModel.client, accounts: accounts, onCompany: explore,
                                   // Back to the calendar it came from, or a new one on the event's date.
                                   onCalendar: { date in
                                       let open = earningsCalendar != nil
                                       earningsEventId = nil
                                       if !open { earningsCalendar = EarningsCalendarTarget(date: date) }
                                   },
                                   onResults: { earningsResultsId = $0 }, onSignIn: { showingAuth = true }, reminders: earningsModel.reminders)
            }
            .navigationDestination(isPresented: $showingEarningsReminders) {
                EarningsRemindersSettingsScene(model: earningsModel.reminders, onSignIn: { showingAuth = true }, onOpenEvent: { earningsEventId = $0 })
            }
            .navigationDestination(item: $earningsResultsId) { id in
                EarningsResultsScene(reportId: id, client: earningsModel.client, onCompany: explore,
                                     onLearn: { earningsResultsId = nil; selectedTab = .learn },
                                     // No purchase flow exists yet: Settings shows the plan (and, in MOCK, the simulated plan switch).
                                     onUpgrade: { showingSettings = true }, onSignIn: { showingAuth = true })
                    .id(id)
            }
            .navigationDestination(isPresented: $showingEarningsDigest) {
                EarningsDigestScene(client: earningsModel.client, onOpenResults: { earningsResultsId = $0 }, onOpenEvent: { earningsEventId = $0 },
                                    onSettings: { showingDigestSettings = true }, onUpgrade: { showingSettings = true }, onSignIn: { showingAuth = true },
                                    onRecentWatchlist: { earningsCalendar = EarningsCalendarTarget(filter: "WATCHLIST") })
            }
            .navigationDestination(isPresented: $showingDigestSettings) {
                EarningsDigestSettingsScene(client: earningsModel.client, permissionDenied: earningsModel.reminders.state?.permissionGranted?.boolValue == false,
                                            onUpgrade: { showingSettings = true }, onSignIn: { showingAuth = true }, onReminders: { showingEarningsReminders = true })
            }
            .onReceive(NotificationCenter.default.publisher(for: .stockStepsOpenEarningsDigest)) { _ in showingEarningsDigest = true }
            .onReceive(NotificationCenter.default.publisher(for: .stockStepsOpenEarningsResults)) { note in
                if let id = note.object as? String, !id.isEmpty { earningsResultsId = id }
            }
            .onReceive(NotificationCenter.default.publisher(for: .stockStepsOpenEarningsEvent)) { note in
                // A notification without a usable event falls back to the calendar.
                if let id = note.object as? String, !id.isEmpty { earningsEventId = id } else { earningsCalendar = EarningsCalendarTarget() }
            }
            .navigationDestination(item: $earningsSymbol) { symbol in
                EarningsDetailsScene(symbol: symbol, client: earningsModel.client, onCompany: { explore(symbol) },
                                     onSignIn: { showingAuth = true }, onUpgrade: { showingSettings = true })
            }
            .navigationDestination(isPresented: $showingDiscover) {
                DiscoverStocksScene(model: screenerModel, accounts: accounts, watchlists: watchlistsModel, onOpenStock: explore,
                                    onCompare: { showingCompare = true }, onSignIn: { showingAuth = true })
            }
            .navigationDestination(isPresented: $showingCompare) {
                CompareStocksScene(model: screenerModel, onOpenStock: explore, onDiscover: { showingCompare = false; showingDiscover = true },
                                   onUpgrade: { showingSettings = true }, onSignIn: { showingAuth = true })
            }
            .navigationDestination(item: $alertsTarget) { target in
                AlertsScene(model: watchlistsModel, symbol: target.isEmpty ? nil : target, onOpenStock: explore)
            }
            // Tapping an alert notification opens that stock's alerts.
            .onReceive(NotificationCenter.default.publisher(for: .stockStepsOpenAlerts)) { note in
                if let symbol = note.object as? String { alertsTarget = symbol }
            }
            .onChange(of: backendEnvironment) { _, _ in
                marketsModel.reset()
                // Watchlists, notes, alerts and the push registration follow the selected backend.
                accounts.client?.setEnvironment(environment: BackendSettings.environment)
            }
        }
        .sheet(isPresented: $showingSettings, onDismiss: { NotificationCenter.default.post(name: .stockStepsPlanMayHaveChanged, object: nil) }) {
            NavigationStack {
                SettingsScene(model: accounts,
                              // Settings is itself a sheet: close it first, or SwiftUI silently drops the second presentation (Phase 5C).
                              onSignIn: { showingSettings = false; showingAuth = true }, appLock: appLock, onEarningsReminders: { showingReminderSettings = true },
                              onEarningsDigest: accounts.client == nil ? nil : { showingSettingsDigest = true })
                    .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Done") { showingSettings = false } } }
                    .navigationDestination(isPresented: $showingReminderSettings) {
                        EarningsRemindersSettingsScene(model: earningsModel.reminders, onSignIn: { showingSettings = false; showingAuth = true })
                    }
                    .navigationDestination(isPresented: $showingSettingsDigest) {
                        // Already inside Settings: the plan section is one step back.
                        EarningsDigestSettingsScene(client: earningsModel.client, permissionDenied: earningsModel.reminders.state?.permissionGranted?.boolValue == false,
                                                    onUpgrade: { showingSettingsDigest = false }, onSignIn: { showingSettings = false; showingAuth = true },
                                                    onReminders: { showingReminderSettings = true })
                    }
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
        .sheet(isPresented: $showingSearch, onDismiss: { searchForPractice = false }) {
            StockSearchScene(model: searchModel, accounts: accounts, initialStock: initialStock, onClose: { showingSearch = false },
                             onOpenStock: { stock in
                                 showingSearch = false
                                 if searchForPractice {
                                     searchForPractice = false
                                     practiceTrade(PracticeOrderTarget(symbol: stock.symbol, sell: false))
                                 } else {
                                     // Close search, then push the shared Company Details page.
                                     detailsSymbol = stock.symbol
                                 }
                             })
        }
    }
    private var tabTitle: String {
        switch selectedTab {
        case .home: "Home"
        case .markets: "Markets"
        case .portfolio: "Portfolio"
        case .watchlist: "Watchlist"
        case .learn: "Learn"
        }
    }
    /// Practice needs an account: the server keeps the simulated ledger.
    private func ensurePractice() -> Bool {
        guard accounts.state.user != nil, let client = accounts.client else { showingAuth = true; return false }
        if practiceModel == nil { practiceModel = PracticeModel(account: client) }
        return true
    }
    private func openPractice() { if ensurePractice() { showingPractice = true } }
    private func practiceTrade(_ target: PracticeOrderTarget) { if ensurePractice() { practiceTarget = target } }
    private func explore(_ symbol: String) {
        detailsSymbol = symbol
    }
}

import SwiftUI
import Shared

struct AppScene: View {
    @State private var searchModel: StockSearchViewModel
    @State private var homeModel: HomeViewModel
    @State private var marketsModel: MarketsModel
    @State private var watchlistsModel: WatchlistsModel
    let accounts: AccountViewModel
    var appLock: AppLockModel?
    @State private var showingAuth = false
    @State private var showingSearch = false
    @State private var showingSettings = false
    @State private var initialStock: StockSearchResult?
    /// Phase 5D.5.6C2: five independent tab stacks, canonical tab selection and the cross-tab trail (core `TabNavigation`).
    @State private var router = TabRouter()
    @Environment(SampleBannerPlacement.self) private var bannerPlacement
    @Environment(\.colorScheme) private var colorScheme
    @State private var screenerModel: ScreenerModel
    @State private var earningsModel: EarningsModel
    /// Earnings Reminders settings inside the Settings sheet.
    @State private var showingReminderSettings = false
    @State private var showingSettingsDigest = false
    /// Earnings Reminders inside the global digest-settings sheet.
    @State private var showingGlobalReminders = false
    @State private var learningModel: LearningModel
    @State private var practiceModel: PracticeModel?
    /// Search opened from Practice: picking a company opens its simulated order directly.
    @State private var searchForPractice = false
    /// 5D.5.6C4: the Practice section has been shown (it then stays alive, hidden, while My Portfolio is selected).
    @State private var practiceShown = false
    @State private var briefModel: BriefModel?
    /// 5D.5.6C3: shared state of the former nested destinations — one company model per symbol and the app's Portfolio model.
    @State private var companies: CompanyModelStore
    @State private var portfolioModel = PortfolioViewModel()

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
        _companies = State(initialValue: CompanyModelStore(baseURL: baseURL))
    }

    var body: some View {
        // 5D.5.6C2: one NavigationStack per tab inside the TabView (no outer stack), so pushed screens keep the tab bar, each tab keeps its own
        // history and state, and the selected tab is the visible screen's canonical tab (TabRouter + core NavigationPolicy).
        TabView(selection: router.selection) {
            tabStack(.home, title: "Home") {
                if appLock?.locked != true {
                    HomeScene(
                        model: homeModel,
                        accounts: accounts,
                        onLearn: { router.selectTab(.learn) },
                        onSearch: { initialStock = nil; showingSearch = true },
                        onWatchlist: { router.selectTab(.watchlist) },
                        onSettings: { showingSettings = true },
                        onPortfolio: { router.selectTab(.portfolio) },
                        onAlerts: openAlerts,
                        onExplore: explore,
                        onEarnings: { router.open(.earningsDetails($0)) },
                        learning: learningModel,
                        onResearch: { router.open(.research($0)) },
                        onPractice: openPractice,
                        briefModel: briefModel,
                        onDailyBrief: { router.open(.dailyBrief(BriefTarget(briefId: nil))) }
                    )
                } else {
                    Color.clear
                }
            }
            .tabItem { Label("Home", systemImage: "house") }
            .tag(AppRoute.home)

            tabStack(.markets, title: "Markets") {
                MarketsScene(model: marketsModel, onOpenStock: explore, onSearch: { initialStock = nil; showingSearch = true },
                             onDiscover: { router.open(.discover) }, onCompare: { router.open(.compare) },
                             onEarnings: { router.open(.earningsCalendar(EarningsCalendarTarget())) }, earnings: earningsModel,
                             brief: briefModel, onDailyBrief: { router.open(.dailyBrief(BriefTarget(briefId: nil))) },
                             onBriefHistory: { router.open(.briefHistory) },
                             onNotifications: openNotifications, onSettings: { showingSettings = true })
            }
            .tabItem { Label("Markets", systemImage: "chart.line.uptrend.xyaxis") }
            .tag(AppRoute.markets)

            tabStack(.portfolio, title: "Portfolio") {
                if appLock?.locked != true { portfolioLanding } else { Color.clear }
            }
            .tabItem { Label("Portfolio", systemImage: "briefcase") }
            .tag(AppRoute.portfolio)

            tabStack(.watchlist, title: "Watchlist") {
                WatchListScene(accounts: accounts, model: watchlistsModel, onSignIn: { showingAuth = true }, onSearch: { initialStock = nil; showingSearch = true },
                               onExplore: explore, onOpenAlerts: openAlerts,
                               onEarnings: { router.open(.earningsCalendar(EarningsCalendarTarget(filter: "WATCHLIST"))) },
                               onEarningsReminders: { router.open(.earningsReminders) },
                               onCompare: { symbols, names in screenerModel.client.compareCompanies(symbols: symbols, names: names); router.open(.compare) },
                               onNotifications: openNotifications, onSettings: { showingSettings = true })
            }
            .tabItem { Label("Watchlist", systemImage: "star") }
            .tag(AppRoute.watchlist)

            tabStack(.learn, title: "Learn") {
                LearnScene(learning: learningModel, onResearch: { router.open(.research($0)) }, onSearch: { initialStock = nil; showingSearch = true },
                           onPractice: openPractice, onLearnTarget: { router.open(.learn($0)) },
                           onNotifications: openNotifications, onSettings: { showingSettings = true })
            }
            .tabItem { Label("Learn", systemImage: "book") }
            .tag(AppRoute.learn)
        }
        .tint(StockStepsTheme.color(ThemeColors.shared.light.primary))
        .environment(router)
        // The tab bar is on screen on every tab screen now (pushed ones included), so the MOCK banner sits above it inside each tab
        // (`sampleDataInset` on each stack); sheets cover it as before (Phase 4C.3).
        .onAppear { bannerPlacement.tabBarVisible = true }
        .onDisappear { bannerPlacement.tabBarVisible = false }
        .task {
            screenerModel.activate(); earningsModel.activate(); learningModel.activate(); briefModel?.activate()
        }
        .onReceive(NotificationCenter.default.publisher(for: .stockStepsOpenBrief)) { note in
            let id = note.object as? String
            router.open(.dailyBrief(BriefTarget(briefId: (id?.isEmpty ?? true) ? nil : id)))
        }
        .onReceive(NotificationCenter.default.publisher(for: .stockStepsOpenEarningsDigest)) { _ in router.open(.earningsDigest) }
        .onReceive(NotificationCenter.default.publisher(for: .stockStepsOpenEarningsResults)) { note in
            if let id = note.object as? String, !id.isEmpty { router.open(.earningsResults(id)) }
        }
        .onReceive(NotificationCenter.default.publisher(for: .stockStepsOpenEarningsEvent)) { note in
            // A notification without a usable event falls back to the calendar.
            if let id = note.object as? String, !id.isEmpty { router.open(.earningsEvent(id)) } else { router.open(.earningsCalendar(EarningsCalendarTarget())) }
        }
        // Tapping an alert notification opens that stock's alerts.
        .onReceive(NotificationCenter.default.publisher(for: .stockStepsOpenAlerts)) { note in
            if let symbol = note.object as? String { openAlerts(symbol) }
        }
        .onChange(of: backendEnvironment) { _, _ in
            marketsModel.reset()
            // Watchlists, notes, alerts and the push registration follow the selected backend.
            accounts.client?.setEnvironment(environment: BackendSettings.environment)
        }
        // A signed-out or changed account never sees the previous account's screens (I11).
        // 5D.5.6C4: only a sign-out or a switch to another account resets (a guest signing in keeps their place, as on Android).
        .onChange(of: accounts.state.user?.id) { old, _ in if old != nil { router.reset(); practiceShown = false } }
        // A company's shared model lives while any of its pages is on a stack.
        .onChange(of: router.revision) { _, _ in companies.retain(only: router.symbolsInUse) }
        // 5D.5.6D: a reused company page the router puts back after a cross-tab Back returns to its reading position.
        .onAppear { router.willRestore = { [companies] page in if case let .companyDetails(symbol) = page { companies.model(symbol).restoreScroll = true } } }
        .sheet(isPresented: $showingSettings, onDismiss: { NotificationCenter.default.post(name: .stockStepsPlanMayHaveChanged, object: nil) }) {
            NavigationStack {
                SettingsScene(model: accounts,
                              // Settings is itself a sheet: close it first, or SwiftUI silently drops the second presentation (Phase 5C).
                              onSignIn: { showingSettings = false; showingAuth = true }, appLock: appLock, onEarningsReminders: { showingReminderSettings = true },
                              onEarningsDigest: accounts.client == nil ? nil : { showingSettingsDigest = true })
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showingSettings = false } } } // KAN-16: Done trailing, as every other sheet
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
        // 5D.5.6C2: app-wide screens over the tabs, without the tab bar (Notifications = all alerts; digest settings from the digest).
        .sheet(item: Binding(get: { router.global }, set: { router.global = $0 })) { sheet in
            NavigationStack {
                globalScreen(sheet)
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { router.global = nil } } } // KAN-16: Done trailing
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
                                     // Close search, then open the company on Markets (its canonical tab).
                                     explore(stock.symbol)
                                 }
                             })
        }
    }

    /// One tab: its own native stack (the router's path), the tab's landing page as root, every pushed destination built by `destination`,
    /// and the MOCK banner above the tab bar on all of its screens.
    private func tabStack<Root: View>(_ tab: AppRoute, title: String, @ViewBuilder root: () -> Root) -> some View {
        NavigationStack(path: router.pathBinding(tab)) {
            root()
                // Landing pages draw their own bar; the title names the first pushed page's back button: "‹ Markets", or while that page was
                // opened from another tab, where Back really goes ("‹ Home", "‹ AAPL"; 5D.5.6C3).
                .stockStepsTopBar(.screen(router.rootTitle(tab), visible: false))
                // KAN-16: no navigation bar on landing pages, so the status-bar strip gets the page background (content never runs under it).
                .landingStatusBarBackdrop()
                .navigationDestination(for: AppDestination.self) { destination in
                    self.destination(destination)
                        // A deeper cross-tab entry: no misleading label, just the chevron (VoiceOver still says "Back").
                        .toolbarRole(router.hidesBackTitle(destination, on: tab) ? .editor : .automatic)
                }
        }
        .sampleDataInset()
    }

    @ViewBuilder private func globalScreen(_ sheet: GlobalSheet) -> some View {
        switch sheet {
        case .notifications:
            AlertsScene(model: watchlistsModel, symbol: nil, onOpenStock: explore, signedIn: accounts.state.user != nil,
                        onSignIn: { router.global = nil; showingAuth = true })
        case .digestSettings:
            EarningsDigestSettingsScene(client: earningsModel.client, permissionDenied: earningsModel.reminders.state?.permissionGranted?.boolValue == false,
                                        onUpgrade: { router.global = nil; showingSettings = true },
                                        onSignIn: { router.global = nil; showingAuth = true },
                                        onReminders: { showingGlobalReminders = true })
                .navigationDestination(isPresented: $showingGlobalReminders) {
                    EarningsRemindersSettingsScene(model: earningsModel.reminders, onSignIn: { router.global = nil; showingAuth = true })
                }
        }
    }

    /// Every pushed screen, built from its typed destination (formerly 17 separate presentation flags).
    @ViewBuilder private func destination(_ destination: AppDestination) -> some View {
        switch destination {
        case let .companyDetails(symbol):
            CompanyDetailsScene(symbol: symbol, accounts: accounts, watchlists: watchlistsModel, learning: learningModel,
                                onSearch: { initialStock = nil; showingSearch = true }, onLearn: { router.selectTab(.learn) },
                                onUpgrade: { showingSettings = true }, onPracticeBuy: { practiceTrade(PracticeOrderTarget(symbol: $0, sell: false)) },
                                onEarningsCalendar: { router.open(.earningsCalendar(EarningsCalendarTarget(date: $0))) },
                                onEarningsResults: { router.open(.earningsResults($0)) },
                                model: companies.model(symbol), earnings: earningsModel)
        case let .companyFinancials(symbol):
            CompanyFinancialsScene(symbol: symbol, client: companies.model(symbol).client, onSearchCompanies: { initialStock = nil; showingSearch = true })
        case let .companyValuation(symbol):
            CompanyValuationScene(symbol: symbol, client: companies.model(symbol).client, onSearchCompanies: { initialStock = nil; showingSearch = true })
        case let .companyNews(symbol):
            CompanyNewsScene(symbol: symbol, client: companies.model(symbol).client)
        case let .newsInsight(symbol, articleId):
            NewsInsightScene(symbol: symbol, articleId: articleId, client: companies.model(symbol).client)
        case let .stockMovement(symbol):
            StockMovementScene(symbol: symbol, client: companies.model(symbol).client)
        case .comparisonResearch:
            ComparisonResearchView(model: screenerModel, onUpgrade: { showingSettings = true }, onSignIn: { showingAuth = true })
        case let .comparisonAi(target):
            ComparisonAiView(model: screenerModel, researchQuestionId: target?.questionId, researchSessionId: target?.sessionId,
                             researchHasNote: target?.hasNote ?? false, onUpgrade: { showingSettings = true }, onSignIn: { showingAuth = true })
        case .portfolioInsights:
            PortfolioInsightsScene(accounts: accounts, onCompany: explore, onSignIn: { showingAuth = true })
        case let .portfolioTransactions(symbol, accountId):
            PortfolioTransactionsScene(accounts: accounts, model: portfolioModel, symbol: symbol, accountId: accountId, onSignIn: { showingAuth = true })
        case let .portfolioTransaction(id):
            PortfolioTransactionDetailsScene(accounts: accounts, model: portfolioModel, transactionId: id, onSignIn: { showingAuth = true })
        case let .holdingDetails(symbol):
            PortfolioScene(accounts: accounts, watchlists: watchlistsModel, model: portfolioModel, onCompany: explore, onSignIn: { showingAuth = true },
                           onAlerts: { router.open(.stockAlerts($0)) }, initialSymbol: symbol)
        case let .learn(target):
            switch target {
            case let .topics(topic, search):
                LearnTopicsView(topic: topic, focusSearch: search, onSearchCompanies: { initialStock = nil; showingSearch = true })
            case let .term(id):
                LearnTermView(termId: id, onSearchCompanies: { initialStock = nil; showingSearch = true })
            }
        case let .dailyBrief(target):
            if let briefModel {
                DailyBriefScene(target: target, model: briefModel, onOpenStock: explore, onHistory: { router.open(.briefHistory) },
                                onWatchlist: { router.selectTab(.watchlist) }, onLearn: { router.selectTab(.learn) },
                                onSignIn: { showingAuth = true }, onUpgrade: { showingSettings = true },
                                onOpenEarnings: { router.open(.earningsEvent($0)) }, onOpenResults: { router.open(.earningsResults($0)) },
                                onOpenDigest: { router.open(.earningsDigest) })
            }
        case .briefHistory:
            if let briefModel {
                DailyBriefHistoryScene(model: briefModel, onOpenBrief: { id in router.open(.dailyBrief(BriefTarget(briefId: id))) },
                                       onUpgrade: { showingSettings = true })
            }
        case let .practiceOrder(target):
            if let practiceModel {
                PracticeOrderScene(target: target, practice: practiceModel,
                                   // Back to the Practice section, refreshed (the order changed the simulated holdings).
                                   onViewHoldings: { router.showPortfolioSection(.practice); practiceModel.presenter.refresh() },
                                   onExplore: { router.open(.discover) },
                                   onLearn: { router.selectTab(.learn) })
            }
        case let .research(target):
            GuidedResearchScene(target: target, learning: learningModel, accounts: accounts, onCompany: explore,
                                onResearchAnother: { initialStock = nil; showingSearch = true },
                                onContinueLearning: { router.selectTab(.learn) },
                                onUpgrade: { showingSettings = true })
        case let .earningsCalendar(target):
            EarningsCalendarScene(target: target, client: earningsModel.client, onOpen: { router.open(.earningsEvent($0)) }, onSignIn: { showingAuth = true },
                                  onOpenResults: { router.open(.earningsResults($0)) }, reminders: earningsModel.reminders,
                                  onDigest: accounts.client == nil ? nil : { router.open(.earningsDigest) })
                .id(target.id)
        case let .earningsEvent(id):
            EarningsEventScene(eventId: id, client: earningsModel.client, accounts: accounts, onCompany: explore,
                               // Back to the calendar it came from, or a new one on the event's date.
                               onCalendar: { date in
                                   if !router.popTo(where: { if case .earningsCalendar = $0 { true } else { false } }) {
                                       router.open(.earningsCalendar(EarningsCalendarTarget(date: date)))
                                   }
                               },
                               onResults: { router.open(.earningsResults($0)) }, onSignIn: { showingAuth = true }, reminders: earningsModel.reminders)
        case .earningsReminders:
            EarningsRemindersSettingsScene(model: earningsModel.reminders, onSignIn: { showingAuth = true }, onOpenEvent: { router.open(.earningsEvent($0)) })
        case let .earningsResults(id):
            EarningsResultsScene(reportId: id, client: earningsModel.client, onCompany: explore,
                                 onLearn: { router.selectTab(.learn) },
                                 // No purchase flow exists yet: Settings shows the plan (and, in MOCK, the simulated plan switch).
                                 onUpgrade: { showingSettings = true }, onSignIn: { showingAuth = true })
                .id(id)
        case .earningsDigest:
            EarningsDigestScene(client: earningsModel.client, onOpenResults: { router.open(.earningsResults($0)) }, onOpenEvent: { router.open(.earningsEvent($0)) },
                                onSettings: { router.global = .digestSettings }, onUpgrade: { showingSettings = true }, onSignIn: { showingAuth = true },
                                onRecentWatchlist: { router.open(.earningsCalendar(EarningsCalendarTarget(filter: "WATCHLIST"))) })
        case let .earningsDetails(symbol):
            EarningsDetailsScene(symbol: symbol, client: earningsModel.client, onCompany: { backToCompany(symbol) },
                                 onSignIn: { showingAuth = true }, onUpgrade: { showingSettings = true })
        case .discover:
            DiscoverStocksScene(model: screenerModel, accounts: accounts, watchlists: watchlistsModel, onOpenStock: explore,
                                onCompare: { router.open(.compare) }, onSignIn: { showingAuth = true })
        case .compare:
            CompareStocksScene(model: screenerModel, onOpenStock: backToCompany, onDiscover: { router.open(.discover) },
                               onUpgrade: { showingSettings = true }, onSignIn: { showingAuth = true })
        case let .stockAlerts(symbol):
            AlertsScene(model: watchlistsModel, symbol: symbol, onOpenStock: explore, signedIn: accounts.state.user != nil, onSignIn: { showingAuth = true })
        }
    }

    /// 5D.5.6C4: the Portfolio landing with its two nested tabs. Both sections stay alive once shown (hidden, not removed), so each keeps its
    /// scroll position and state; Practice is built the first time it is selected. The selection lives in the router.
    @ViewBuilder private var portfolioLanding: some View {
        let section = Binding(get: { router.portfolioSection }, set: { router.portfolioSection = $0 })
        let mine = router.portfolioSection == .myPortfolio
        ZStack {
            PortfolioScene(accounts: accounts, watchlists: watchlistsModel, model: portfolioModel, onCompany: explore,
                           onSignIn: { showingAuth = true }, onAlerts: { router.open(.stockAlerts($0)) }, section: section,
                           onNotifications: openNotifications, onSettings: { showingSettings = true })
                .sectionVisible(mine)
            if !mine || practiceShown, let practice = practiceSectionModel() {
                PracticeScene(model: practice, onBuy: { initialStock = nil; searchForPractice = true; showingSearch = true }, onExplore: { router.open(.discover) },
                              onLearn: { router.selectTab(.learn) }, onSignIn: { showingAuth = true }, onCompany: explore,
                              onTrade: { practiceTrade($0) }, onManagePlan: { showingSettings = true },
                              landingBar: AnyView(StockLandingTopBar(tab: .portfolio, onNotifications: openNotifications, onSettings: { showingSettings = true })),
                              landingTabs: AnyView(PortfolioSectionTabs(selection: section).pinnedTabsBackground(StockStepsTheme.colors(colorScheme).appBackground)))
                    // No refresh on appearance: the presenter loads when it starts and on each account change; orders refresh explicitly.
                    .onAppear { practiceShown = true }
                    .sectionVisible(!mine)
            }
        }
    }

    /// The Practice model for the section (guests too: its presenter shows the existing sign-in prompt).
    private func practiceSectionModel() -> PracticeModel? {
        if practiceModel == nil, let client = accounts.client { DispatchQueue.main.async { if practiceModel == nil { practiceModel = PracticeModel(account: client) } } }
        return practiceModel
    }

    /// Practice needs an account: the server keeps the simulated ledger.
    private func ensurePractice() -> Bool {
        guard accounts.state.user != nil, let client = accounts.client else { showingAuth = true; return false }
        if practiceModel == nil { practiceModel = PracticeModel(account: client) }
        return true
    }
    /// 5D.5.6C4: Practice is the Portfolio landing's second nested tab (guests see its existing sign-in prompt there).
    private func openPractice() { router.showPortfolioSection(.practice) }
    private func practiceTrade(_ target: PracticeOrderTarget) { if ensurePractice() { router.open(.practiceOrder(target)) } }
    /// 5D.5.6B/C2: Notifications is the all-alerts history, an app-wide screen over the tabs (no stock filter, no tab bar).
    private func openNotifications() { router.global = .notifications }
    /// A stock's alerts are Watchlist's; no symbol (or "") means all alerts (Notifications).
    private func openAlerts(_ symbol: String?) { if let symbol, !symbol.isEmpty { router.open(.stockAlerts(symbol)) } else { openNotifications() } }
    /// Company Details always opens on Markets, its canonical tab (D1), whatever it was opened from.
    private func explore(_ symbol: String) { router.open(.companyDetails(symbol)) }
    /// Back to that company's page when it is already below on this stack (Earnings, Compare opened from it), else open it.
    private func backToCompany(_ symbol: String) { if !router.popTo(where: { $0 == .companyDetails(symbol) }) { explore(symbol) } }
}

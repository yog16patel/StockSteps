import Charts
import Observation
import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// Mirrors the shared Kotlin Practice presenter (same state, rules and backend as Android).
@MainActor @Observable
final class PracticeModel {
    private(set) var state: PracticeUiState?
    @ObservationIgnored let client: IosPracticeClient
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    init(account: IosAccountClient) {
        client = IosPracticeClient(account: account)
        subscription = client.observe { [weak self] in self?.state = $0 }
    }
    deinit {
        subscription?.cancel()
        client.close()
    }
    var presenter: PracticePresenter { client.presenter }
}

/// One simulated order (shared order presenter).
@MainActor @Observable
final class PracticeOrderModel {
    private(set) var state: PracticeOrderState?
    @ObservationIgnored let order: PracticeOrderPresenter
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    init(target: PracticeOrderTarget, client: IosPracticeClient) {
        order = client.order(symbol: target.symbol, sell: target.sell)
        subscription = client.observeOrder(order: order) { [weak self] in self?.state = $0 }
    }
    deinit { subscription?.cancel() }
}

struct PracticeOrderTarget: Hashable, Identifiable {
    let symbol: String
    let sell: Bool
    var id: String { "\(symbol)-\(sell)" }
}

// MARK: - Shared pieces

struct SimulatedBadge: View {
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        Text("SIMULATED").font(.caption2.weight(.bold))
            .padding(.horizontal, CGFloat(space.sm)).padding(.vertical, CGFloat(space.xxs))
            .background(StockStepsTheme.colors(scheme).warningContainer, in: Capsule())
            .accessibilityLabel("Simulated with virtual money")
    }
}

/// A clearly separate entry to the simulated portfolio (Portfolio, Home, Learn).
struct PracticeEntryCard: View {
    var title = "Practice Portfolio"
    var message = "Learn with simulated investments."
    let action: () -> Void
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        Button(action: action) {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                HStack {
                    Text(title).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                    Spacer()
                    SimulatedBadge()
                }
                Text(message).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).multilineTextAlignment(.leading)
            }
            .padding(CGFloat(space.cardPadding))
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Opens the Practice Portfolio, simulated with virtual money")
    }
}

private struct DetailRow: View {
    let label: String
    let value: String
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        HStack(alignment: .top) {
            Text(label).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
            Spacer()
            Text(value).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary).monospacedDigit().multilineTextAlignment(.trailing)
        }
        .accessibilityElement(children: .combine)
    }
}

/// Gain/loss in words and sign; colour only supplements it.
private struct GainLabel: View {
    let client: IosPracticeClient
    let value: String?
    var percent: String? = nil
    let currency: String
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let sign = client.sign(value: value)
        let text = value == nil ? "Unavailable" : "\(client.direction(value: value)) \(client.signedMoney(value: value, currency: currency))" + (percent.map { " (\(client.percent(value: $0)))" } ?? "")
        Text(text).font(StockStepsTheme.font(type.bodySemiBold)).monospacedDigit()
            .foregroundStyle(sign > 0 ? colors.positiveText : sign < 0 ? colors.negativeText : colors.textSecondary)
    }
}

// MARK: - Practice Portfolio

struct PracticeScene: View {
    let model: PracticeModel
    let onBuy: () -> Void
    let onExplore: () -> Void
    let onLearn: () -> Void
    let onSignIn: () -> Void
    let onCompany: (String) -> Void
    let onTrade: (PracticeOrderTarget) -> Void
    var onManagePlan: () -> Void = {}

    var body: some View {
        PracticeScreen(state: model.state, client: model.client, onBuy: onBuy, onExplore: onExplore, onLearn: onLearn, onSignIn: onSignIn,
                       onCompany: onCompany, onTrade: onTrade, onManagePlan: onManagePlan)
            .navigationTitle("Practice")
            .navigationBarTitleDisplayMode(.inline)
            .refreshable { model.presenter.refresh() }
            .onAppear { model.presenter.refresh() }
    }
}

struct PracticeScreen: View {
    let state: PracticeUiState?
    let client: IosPracticeClient
    let onBuy: () -> Void
    let onExplore: () -> Void
    let onLearn: () -> Void
    let onSignIn: () -> Void
    let onCompany: (String) -> Void
    let onTrade: (PracticeOrderTarget) -> Void
    let onManagePlan: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var holding: PracticeHoldingView?
    @State private var openChallenge: String?
    private var presenter: PracticePresenter { client.presenter }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.sectionGap)) {
                header(colors)
                if let state {
                    if !state.signedIn {
                        StockSectionMessage(message: "Sign in to use your Practice Portfolio. Your simulated investments are saved to your account, so they're there on every device.",
                                            actionTitle: "Sign in", action: onSignIn)
                    } else if let overview = state.overview {
                        if state.offline {
                            banner("You're offline. This is your last saved Practice Portfolio; trading and plan changes need a connection.", colors)
                        }
                        Picker("Section", selection: Binding(get: { state.tab.name }, set: { client.selectTab(name: $0) })) {
                            ForEach(client.tabs, id: \.name) { Text($0.label).tag($0.name) }
                        }.pickerStyle(.segmented)
                        switch state.tab.name {
                        case "HOLDINGS": holdings(state, overview, colors)
                        case "ACTIVITY": activity(state, overview, colors)
                        case "CHALLENGES": challenges(state, overview, colors)
                        default: overviewTab(state, overview, colors)
                        }
                        ForEach(overview.notices, id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
                    } else if let error = state.error {
                        StockSectionMessage(message: error, actionTitle: "Try again", action: { presenter.refresh() })
                    } else {
                        ProgressView().accessibilityLabel("Loading your Practice Portfolio")
                    }
                }
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .sheet(item: $holding) { h in holdingSheet(h, colors).presentationDetents([.medium, .large]) }
        .sheet(isPresented: Binding(get: { client.sheetKind(sheet: state?.sheet) != nil }, set: { if !$0 { presenter.dismissSheet() } })) {
            sheetContent(colors).presentationDetents([.medium, .large])
        }
        .alert(state?.message ?? "", isPresented: Binding(get: { state?.message != nil }, set: { if !$0 { presenter.dismissMessage() } })) {
            Button("OK") { presenter.dismissMessage() }
        }
    }

    private func header(_ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            HStack {
                Text("Practice Portfolio").font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).accessibilityAddTraits(.isHeader)
                Spacer()
                SimulatedBadge()
                if let state, state.overview != nil {
                    Menu("More") {
                        Button("Reset practice portfolio") { presenter.showReset() }.disabled(!state.canTrade)
                        Button("About StockSteps+") { presenter.showPlus() }
                        if state.overview?.sampleData == true {
                            Menu("Sample scenarios (mock)") { ForEach(client.scenarios, id: \.self) { name in Button(name) { presenter.loadScenario(name: name) } } }
                        }
                    }
                    .frame(minHeight: CGFloat(dims.touchTarget))
                }
            }
            Text("Learn investing without real money.").font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
            if let label = state?.accessLabel {
                Text(label).font(.caption).padding(.horizontal, CGFloat(space.sm)).padding(.vertical, CGFloat(space.xxs))
                    .background(colors.surfaceSecondary, in: Capsule())
                    .foregroundStyle(state?.entitlement?.access.name == "TRIAL" ? colors.primaryText : colors.textSecondary)
            }
        }
    }

    private func banner(_ text: String, _ colors: StockColors) -> some View {
        Text(text).font(StockStepsTheme.font(type.small)).padding(CGFloat(space.sm)).frame(maxWidth: .infinity, alignment: .leading)
            .background(colors.warningContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) { content() }.stockCard()
    }

    private func lockedPreview(_ text: String, feature: String, _ colors: StockColors) -> some View {
        Button { presenter.showLocked(feature: feature) } label: {
            HStack(spacing: CGFloat(space.sm)) {
                Image(systemName: "lock.fill").accessibilityHidden(true)
                Text(text).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .frame(minHeight: CGFloat(dims.touchTarget))
            .padding(.horizontal, CGFloat(space.sm))
            .overlay(RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)).stroke(colors.border))
        }
        .buttonStyle(.plain)
        .accessibilityHint("Shows what the Practice trial and StockSteps+ include")
    }

    // MARK: Overview

    @ViewBuilder
    private func overviewTab(_ state: PracticeUiState, _ o: PracticeOverview, _ colors: StockColors) -> some View {
        card {
            Text("Portfolio value").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary)
            Text(client.money(value: o.totalValue, currency: o.baseCurrency)).font(StockStepsTheme.font(type.largeNumber, relativeTo: .largeTitle)).monospacedDigit()
            GainLabel(client: client, value: o.totalGain, percent: o.totalGainPercent, currency: o.baseCurrency)
            Text("Since you started with \(client.money(value: o.startingCash, currency: o.baseCurrency)) of virtual cash.").font(.caption).foregroundStyle(colors.textSecondary)
            performance(state, o, colors)
        }
        HStack(spacing: CGFloat(space.sm)) {
            card { Text("Available cash").font(.caption).foregroundStyle(colors.textSecondary); Text(client.money(value: o.cash, currency: o.baseCurrency)).font(StockStepsTheme.font(type.bodySemiBold)).monospacedDigit() }
            card { Text("Open holdings").font(.caption).foregroundStyle(colors.textSecondary); Text(o.entitlement.maxOpenHoldings.map { "\(o.openHoldings) of \($0)" } ?? "\(o.openHoldings)").font(StockStepsTheme.font(type.bodySemiBold)) }
        }
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: CGFloat(space.sm)) {
                Button("Buy Stock") { if state.atFreeLimit { presenter.showHoldingLimit() } else { onBuy() } }.buttonStyle(.borderedProminent).disabled(!state.canTrade)
                Button("Explore Stocks", action: onExplore).buttonStyle(.bordered)
                Button("Learn", action: onLearn).buttonStyle(.bordered)
            }
            .controlSize(.large)
        }
        if o.holdings.isEmpty {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Text("Your practice portfolio is ready.").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
                Text("You have \(client.money(value: o.cash, currency: o.baseCurrency)) in virtual cash. Explore stocks or ETFs, research a company, then try a simulated purchase. No real money is used.")
                    .font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
            }
            .padding(CGFloat(space.cardPadding)).frame(maxWidth: .infinity, alignment: .leading)
            .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        } else {
            HStack {
                Text("Holdings").font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title2)).accessibilityAddTraits(.isHeader)
                Spacer()
                Button("See all") { client.selectTab(name: "HOLDINGS") }
            }
            ForEach(Array(o.holdings.prefix(3)), id: \.instrument.id) { holdingRow($0, o.baseCurrency, colors) }
        }
        card {
            Text("What your portfolio shows").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
            ForEach(o.insights, id: \.id) { Text("• \($0.text)").font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody) }
            if !client.has(state: state, capability: "PREMIUM_INSIGHTS") {
                lockedPreview("Detailed insights on concentration, ETFs and sectors are part of the Practice trial and StockSteps+.", feature: "Premium educational insights", colors)
            }
            Text("These are observations about your simulated portfolio, not advice.").font(.caption).foregroundStyle(colors.textSecondary)
        }
        Text("Portfolio value includes both your virtual cash and the current value of your simulated investments. Gain or loss compares it with the virtual cash you started with.")
            .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
    }

    @ViewBuilder
    private func performance(_ state: PracticeUiState, _ o: PracticeOverview, _ colors: StockColors) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack {
                ForEach(client.ranges, id: \.name) { range in
                    let locked = !client.rangeAllowed(state: state, name: range.name)
                    Button(locked ? "\(range.label) 🔒" : range.label) { client.selectRange(name: range.name) }
                        .buttonStyle(.bordered).tint(state.range.name == range.name ? .accentColor : .secondary)
                        .accessibilityValue(locked ? "Locked. Part of the Practice trial and StockSteps+" : (state.range.name == range.name ? "Selected" : ""))
                }
            }
        }
        if let error = state.performanceError {
            Text(error).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
        } else if let perf = state.performance, perf.range.name == state.range.name {
            let values = client.values(points: perf.points)
            if values.compactMap({ $0 as? NSNumber }).count < 2 {
                Text(perf.notes.last ?? "Not enough history yet.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
            } else {
                Chart {
                    ForEach(Array(perf.points.enumerated()), id: \.offset) { index, point in
                        if let value = (values[index] as? NSNumber)?.doubleValue { LineMark(x: .value("Date", point.date), y: .value("Value", value)).foregroundStyle(colors.primary) }
                    }
                }
                .chartXAxis(.hidden)
                .frame(height: 160)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Practice portfolio value, \(perf.range.label). From \(client.money(value: perf.startValue, currency: perf.baseCurrency)) to \(client.money(value: perf.endValue, currency: perf.baseCurrency)): \(client.direction(value: perf.change)) \(client.signedMoney(value: perf.change, currency: perf.baseCurrency)).")
                Text("\(perf.range.label): \(client.direction(value: perf.change)) \(client.signedMoney(value: perf.change, currency: perf.baseCurrency)) (\(client.percent(value: perf.changePercent)))")
                    .font(StockStepsTheme.font(type.small)).monospacedDigit()
                ForEach(perf.notes, id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
            }
        } else {
            ProgressView()
        }
    }

    // MARK: Holdings

    private func holdingRow(_ h: PracticeHoldingView, _ base: String, _ colors: StockColors) -> some View {
        Button { holding = h } label: {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                HStack(spacing: CGFloat(space.sm)) {
                    StockTickerAvatar(symbol: h.instrument.symbol, logoUrl: h.instrument.logoUrl, size: CGFloat(dims.logoCompact))
                    VStack(alignment: .leading) {
                        Text(h.instrument.symbol).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                        Text(h.instrument.name ?? "").font(.caption).foregroundStyle(colors.textSecondary).lineLimit(1)
                    }
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text(client.money(value: h.marketValue, currency: base)).font(StockStepsTheme.font(type.bodySemiBold)).monospacedDigit().foregroundStyle(colors.textPrimary)
                        GainLabel(client: client, value: h.unrealizedGain, percent: h.unrealizedPercent, currency: base)
                    }
                }
                if h.stale { Text("Price from an earlier session").font(.caption).foregroundStyle(colors.cautionText) }
            }
            .stockCard()
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(h.instrument.symbol), \(h.instrument.name ?? ""). \(client.shares(value: h.quantity)) shares. Value \(client.money(value: h.marketValue, currency: base)). \(client.direction(value: h.unrealizedGain)) \(client.signedMoney(value: h.unrealizedGain, currency: base)).")
        .accessibilityHint("Opens holding details")
    }

    @ViewBuilder
    private func holdings(_ state: PracticeUiState, _ o: PracticeOverview, _ colors: StockColors) -> some View {
        HStack(spacing: CGFloat(space.sm)) {
            card { Text("Invested value").font(.caption).foregroundStyle(colors.textSecondary); Text(client.money(value: o.holdingsValue, currency: o.baseCurrency)).font(StockStepsTheme.font(type.bodySemiBold)).monospacedDigit() }
            card { Text("Unrealized").font(.caption).foregroundStyle(colors.textSecondary); GainLabel(client: client, value: o.unrealizedGain, currency: o.baseCurrency) }
        }
        if o.holdings.isEmpty {
            StockSectionMessage(message: "You don't have any practice holdings yet.", actionTitle: "Buy Stock", action: onBuy)
        }
        ForEach(o.holdings, id: \.instrument.id) { holdingRow($0, o.baseCurrency, colors) }
        Text("Realized gain from sales: \(client.signedMoney(value: o.realizedGain, currency: o.baseCurrency))" + (o.dividends != "0" ? " · Simulated dividends: \(client.money(value: o.dividends, currency: o.baseCurrency))" : ""))
            .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
        card {
            Text("Allocation").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
            if let slices = o.allocation {
                ForEach(slices, id: \.label) { allocationBar($0, o.baseCurrency, colors) }
                if let sectors = o.sectorAllocation {
                    Text("By sector").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary)
                    ForEach(sectors, id: \.label) { allocationBar($0, o.baseCurrency, colors) }
                }
                Text("Spreading money across different companies and industries is called diversification. It can lower the impact of one company, but can't remove risk.")
                    .font(.caption).foregroundStyle(colors.textSecondary)
            } else {
                lockedPreview("See how your money is spread across holdings, cash and sectors with the Practice trial or StockSteps+.", feature: "Allocation charts", colors)
            }
        }
    }

    private func allocationBar(_ slice: PracticeAllocationSlice, _ base: String, _ colors: StockColors) -> some View {
        let fraction = min(max((Double(slice.percent) ?? 0) / 100, 0), 1)
        return VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(slice.label).font(StockStepsTheme.font(type.small))
                Spacer()
                Text("\(client.percent(value: slice.percent).replacingOccurrences(of: "+", with: "")) · \(client.money(value: slice.value, currency: base))").font(StockStepsTheme.font(type.small)).monospacedDigit()
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(colors.surfaceSecondary)
                    Capsule().fill(colors.primary).frame(width: geo.size.width * fraction)
                }
            }
            .frame(height: 8)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(slice.label): \(client.percent(value: slice.percent).replacingOccurrences(of: "+", with: "")), \(client.money(value: slice.value, currency: base))")
    }

    private func holdingSheet(_ h: PracticeHoldingView, _ colors: StockColors) -> some View {
        let base = state?.overview?.baseCurrency ?? "CAD"
        let canTrade = state?.canTrade == true
        return NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                    HStack { Text("\(h.instrument.symbol) · \(h.instrument.name ?? "")").font(StockStepsTheme.font(type.sectionTitle)); Spacer(); SimulatedBadge() }
                    DetailRow(label: "Shares", value: client.shares(value: h.quantity))
                    DetailRow(label: "Average cost", value: client.price(value: h.averageCost, currency: base) + " per share")
                    DetailRow(label: "Current price", value: client.price(value: h.currentPrice, currency: h.priceCurrency) + (client.isoDate(iso: h.quoteAsOf).map { " · \($0)" } ?? ""))
                    DetailRow(label: "Market value", value: client.money(value: h.marketValue, currency: base))
                    DetailRow(label: "Unrealized gain/loss", value: "\(client.direction(value: h.unrealizedGain)) \(client.signedMoney(value: h.unrealizedGain, currency: base)) (\(client.percent(value: h.unrealizedPercent)))")
                    if h.instrument.currency != base { DetailRow(label: "Exchange rate", value: h.fxRate.map { "1 \(h.instrument.currency) = \($0) \(base)" } ?? "Unavailable") }
                    if h.stale { Text("This price is from an earlier trading session.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.cautionText) }
                    HStack {
                        Button("Buy more") { holding = nil; onTrade(PracticeOrderTarget(symbol: h.instrument.id, sell: false)) }.buttonStyle(.borderedProminent).disabled(!canTrade)
                        Button("Sell") { holding = nil; onTrade(PracticeOrderTarget(symbol: h.instrument.id, sell: true)) }.buttonStyle(.bordered).disabled(!canTrade)
                    }
                    .controlSize(.large)
                    Button("Company details") { holding = nil; onCompany(h.instrument.id) }
                }
                .padding(CGFloat(space.screen))
            }
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { holding = nil } } }
        }
    }

    // MARK: Activity

    @ViewBuilder
    private func activity(_ state: PracticeUiState, _ o: PracticeOverview, _ colors: StockColors) -> some View {
        Picker("Filter", selection: Binding(get: { state.filter?.name ?? "ALL" }, set: { client.filter(name: $0 == "ALL" ? nil : $0) })) {
            Text("All").tag("ALL"); Text("Buys").tag("BUY"); Text("Sells").tag("SELL"); Text("Dividends").tag("DIVIDEND")
        }.pickerStyle(.segmented)
        if let items = state.transactions {
            if items.isEmpty { Text("No simulated transactions here yet.").foregroundStyle(colors.textSecondary) }
            ForEach(items, id: \.id) { t in
                let detail = t.type.name == "SPLIT_ADJUSTMENT" ? "\(t.quantity)-for-1 split" : "\(client.shares(value: t.quantity)) shares at \(client.price(value: t.price, currency: t.priceCurrency))"
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(client.type(t: t)) · \(t.instrument.symbol)").font(StockStepsTheme.font(type.bodySemiBold))
                        Text(detail).font(.caption).foregroundStyle(colors.textSecondary)
                        Text("\(client.dateTime(millis: t.executedAt)) · Simulated").font(.caption).foregroundStyle(colors.textTertiary)
                    }
                    Spacer()
                    if t.type.name != "SPLIT_ADJUSTMENT" {
                        Text((t.type.name == "BUY" ? "−" : "+") + client.money(value: t.amount, currency: o.baseCurrency)).font(StockStepsTheme.font(type.bodySemiBold)).monospacedDigit()
                    }
                }
                .stockCard()
                .accessibilityElement(children: .combine)
            }
        } else {
            ProgressView()
        }
    }

    // MARK: Challenges

    @ViewBuilder
    private func challenges(_ state: PracticeUiState, _ o: PracticeOverview, _ colors: StockColors) -> some View {
        Text("Short guided activities. They're about understanding, never about making a profit.").font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
        ForEach(o.challenges, id: \.id) { c in
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Button { openChallenge = openChallenge == c.id ? nil : c.id } label: {
                    HStack {
                        VStack(alignment: .leading) {
                            Text("\(c.order). \(c.title)").font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                            Text(c.summary).font(.caption).foregroundStyle(colors.textSecondary)
                        }
                        Spacer()
                        Text(c.completedAt != nil ? "Completed" : c.available ? "Start" : "🔒 Preview").font(StockStepsTheme.font(type.label))
                            .foregroundStyle(c.completedAt != nil ? colors.positiveText : colors.primaryText)
                    }
                    .frame(minHeight: CGFloat(dims.touchTarget))
                }
                .buttonStyle(.plain)
                if openChallenge == c.id {
                    ForEach(c.lesson, id: \.self) { Text($0).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody) }
                    if c.kind.name == "FIRST_BUY" {
                        Text(c.completedAt != nil ? "Done: you made your first simulated purchase." : "Completes automatically when you confirm your first practice purchase.")
                            .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                    } else if !c.available {
                        lockedPreview("This challenge is part of the Practice trial and StockSteps+. Challenges you complete stay completed.", feature: "Guided practice challenges", colors)
                    } else if let quiz = c.quiz {
                        let answer = client.answer(state: state, challengeId: c.id)
                        Text(quiz.question).font(StockStepsTheme.font(type.bodySemiBold))
                        ForEach(quiz.options, id: \.id) { option in
                            Button { presenter.answerChallenge(challengeId: c.id, optionId: option.id) } label: {
                                Text(option.text).frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget), alignment: .leading)
                            }
                            .buttonStyle(.bordered).disabled(answer != nil)
                            .accessibilityValue(answer?.selectedOptionId == option.id ? "Your answer" : "")
                        }
                        if let answer {
                            let reflection = c.kind.name == "REFLECTION"
                            Text(reflection ? "Thanks for reflecting" : answer.feedback).font(StockStepsTheme.font(type.bodySemiBold))
                                .foregroundStyle(reflection || answer.correct ? colors.positiveText : colors.negativeText)
                            Text(answer.explanation).font(StockStepsTheme.font(type.small))
                            if !reflection && !answer.correct { Button("Try again") { presenter.retryChallenge(challengeId: c.id) } }
                        }
                    }
                }
            }
            .stockCard()
        }
    }

    // MARK: Sheets

    @ViewBuilder
    private func sheetContent(_ colors: StockColors) -> some View {
        let kind = client.sheetKind(sheet: state?.sheet) ?? ""
        let busy = state?.busy == true
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                    switch kind {
                    case "limit-trial", "limit":
                        paywall(kind == "limit-trial" ? "Ready to practice with more stocks?" : "Unlock Unlimited Practice",
                                kind == "limit-trial" ? "Your free Practice Portfolio includes three holdings. Start a 14-day trial to explore unlimited virtual investments and additional learning tools."
                                : "Your free Practice Portfolio includes three holdings. StockSteps+ unlocks unlimited virtual investments and premium learning tools. You can always sell or add to the holdings you have.",
                                trial: kind == "limit-trial")
                    case "locked-trial", "locked":
                        paywall("\(client.lockedFeature(sheet: state?.sheet) ?? "This") is a premium learning tool",
                                kind == "locked-trial" ? "Start a 14-day Practice trial to try it, along with unlimited holdings and guided challenges." : "It's included with StockSteps+.",
                                trial: kind == "locked-trial")
                    case "trial":
                        title("Start your 14-day Practice trial")
                        bullets(["Unlimited practice holdings", "Full portfolio history (3M, 1Y, ALL)", "Allocation charts and detailed insights", "All guided practice challenges"])
                        Text("The trial lasts 14 days from when you start it, timed by StockSteps' servers. You'll see the exact end date as soon as it starts.")
                        Text("No payment method required. Your trial does not automatically become a paid subscription.").bold()
                        Text("When it ends you keep every holding and transaction. You can keep selling or adding to them; opening new ones follows the free three-holding limit.")
                            .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                        Text("The trial is available once per account.").font(.caption).foregroundStyle(colors.textSecondary)
                        Button(busy ? "Starting…" : "Start Free Trial") { presenter.startTrial() }.buttonStyle(.borderedProminent).controlSize(.large)
                            .frame(maxWidth: .infinity).disabled(busy || state?.canTrade != true)
                        Button("Not now") { presenter.dismissSheet() }.frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget))
                    case "expired":
                        title("Your Practice trial has ended")
                        Text("Your virtual investments are still here. You can continue tracking and selling holdings. Upgrade to StockSteps+ to unlock unlimited new holdings and premium learning tools.")
                        Button("Explore StockSteps+") { presenter.showPlus() }.buttonStyle(.borderedProminent).controlSize(.large).frame(maxWidth: .infinity)
                        Button("Continue Free") { presenter.dismissSheet() }.frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget))
                    case "plus":
                        title("StockSteps+")
                        bullets(["Unlimited Practice Portfolio holdings", "Expanded portfolio analytics and full history", "Guided practice challenges", "Premium educational insights", "AI Stock Research Assistant, subject to usage limits"])
                        Text(state?.entitlement?.access.name == "PLUS" ? "You have StockSteps+." : "In-app purchase isn't available in this version yet, so prices aren't shown and nothing can be charged. Your plan is read from your account.")
                        if state?.overview?.sampleData == true {
                            Button("Simulate a plan (mock)") { presenter.dismissSheet(); onManagePlan() }.buttonStyle(.bordered).frame(maxWidth: .infinity)
                        }
                        Button("Close") { presenter.dismissSheet() }.frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget))
                    case "reset":
                        title("Reset your Practice Portfolio?")
                        Text("This archives your simulated holdings and transactions and starts again with \(client.money(value: state?.overview?.startingCash, currency: state?.overview?.baseCurrency ?? "CAD")) of virtual cash.")
                        Text("It doesn't change your plan, trial, account, Learn progress or completed challenges. No real money is involved.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                        Button(busy ? "Resetting…" : "Reset practice portfolio", role: .destructive) { presenter.reset() }.buttonStyle(.borderedProminent).controlSize(.large)
                            .frame(maxWidth: .infinity).disabled(busy || state?.canTrade != true)
                        Button("Cancel") { presenter.dismissSheet() }.frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget))
                    default:
                        EmptyView()
                    }
                }
                .padding(CGFloat(space.screen))
            }
        }
    }

    private func title(_ text: String) -> some View {
        Text(text).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title2)).accessibilityAddTraits(.isHeader)
    }
    private func bullets(_ items: [String]) -> some View {
        VStack(alignment: .leading, spacing: 4) { ForEach(items, id: \.self) { Text("• \($0)") } }
    }
    @ViewBuilder
    private func paywall(_ heading: String, _ body: String, trial: Bool) -> some View {
        title(heading)
        Text(body)
        if trial { Button("Start 14-Day Free Trial") { presenter.showTrialConfirm() }.buttonStyle(.borderedProminent).controlSize(.large).frame(maxWidth: .infinity) }
        Button("Explore StockSteps+") { presenter.showPlus() }.buttonStyle(.bordered).controlSize(.large).frame(maxWidth: .infinity)
        Button(trial ? "Maybe Later" : "Continue with Free") { presenter.dismissSheet() }.frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget))
    }
}

extension PracticeHoldingView: @retroactive Identifiable {
    public var id: String { instrument.id }
}

// MARK: - Order flow

struct PracticeOrderScene: View {
    let target: PracticeOrderTarget
    let practice: PracticeModel
    let onViewHoldings: () -> Void
    let onExplore: () -> Void
    let onLearn: () -> Void
    @State private var model: PracticeOrderModel

    init(target: PracticeOrderTarget, practice: PracticeModel, onViewHoldings: @escaping () -> Void, onExplore: @escaping () -> Void, onLearn: @escaping () -> Void) {
        self.target = target
        self.practice = practice
        self.onViewHoldings = onViewHoldings
        self.onExplore = onExplore
        self.onLearn = onLearn
        _model = State(initialValue: PracticeOrderModel(target: target, client: practice.client))
    }

    var body: some View {
        PracticeOrderScreen(state: model.state, client: practice.client, order: model.order,
                            onViewHoldings: { practice.client.selectTab(name: "HOLDINGS"); onViewHoldings() },
                            onUpgrade: { practice.presenter.showHoldingLimit(); onViewHoldings() },
                            onExplore: onExplore, onLearn: onLearn)
            .navigationTitle("Practice order")
            .navigationBarTitleDisplayMode(.inline)
            .onChange(of: model.state?.result?.transaction.id) { _, id in if id != nil { practice.presenter.refresh() } }
    }
}

struct PracticeOrderScreen: View {
    let state: PracticeOrderState?
    let client: IosPracticeClient
    let order: PracticeOrderPresenter
    let onViewHoldings: () -> Void
    let onUpgrade: () -> Void
    let onExplore: () -> Void
    let onLearn: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var input = ""

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                if let state {
                    header(state, colors)
                    switch state.step.name {
                    case "REVIEW": review(state, colors)
                    case "DONE": done(state, colors)
                    default: entry(state, colors)
                    }
                }
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .alert("Ready to practice with more stocks?", isPresented: Binding(get: { state?.holdingLimit == true }, set: { if !$0 { order.dismissHoldingLimit() } })) {
            Button("See options") { order.dismissHoldingLimit(); onUpgrade() }
            Button("Maybe Later", role: .cancel) { order.dismissHoldingLimit() }
        } message: { Text(state?.blocker?.message ?? "Your free Practice Portfolio includes three holdings.") }
    }

    private func verb(_ state: PracticeOrderState) -> String { state.side.name == "SELL" ? "Practice Sell" : "Practice Buy" }

    private func header(_ state: PracticeOrderState, _ colors: StockColors) -> some View {
        HStack(spacing: CGFloat(space.sm)) {
            StockTickerAvatar(symbol: state.symbol, logoUrl: state.preview?.instrument.logoUrl)
            VStack(alignment: .leading) {
                Text(state.preview?.instrument.name ?? state.symbol).font(StockStepsTheme.font(type.screenTitle, relativeTo: .title1)).accessibilityAddTraits(.isHeader)
                Text([state.symbol, state.preview?.instrument.exchange].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(colors.textSecondary)
            }
            Spacer()
            SimulatedBadge()
        }
    }

    @ViewBuilder
    private func entry(_ state: PracticeOrderState, _ colors: StockColors) -> some View {
        Text(verb(state)).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title2))
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            if let p = state.preview {
                DetailRow(label: "Latest usable price", value: client.price(value: p.price, currency: p.priceCurrency))
                Text(p.priceBasis + (client.isoDate(iso: p.quoteAsOf).map { " · \($0)" } ?? "")).font(.caption).foregroundStyle(colors.textSecondary)
                if p.priceCurrency != p.baseCurrency && p.fxRate != "0" {
                    Text("1 \(p.priceCurrency) = \(p.fxRate) \(p.baseCurrency)" + (p.fxAsOf.map { " (Bank of Canada, \($0))" } ?? "")).font(.caption).foregroundStyle(colors.textSecondary)
                }
                if state.side.name == "SELL" { DetailRow(label: "You own", value: "\(client.shares(value: p.ownedQuantity)) shares") }
            } else if let error = state.error {
                Text(error)
            } else {
                ProgressView().accessibilityLabel("Loading the latest price")
            }
        }
        .stockCard()
        if state.side.name == "BUY" {
            Picker("Order by", selection: Binding(get: { state.mode.name }, set: { input = ""; client.setAmountMode(order: order, amount: $0 == "AMOUNT") })) {
                Text("Shares").tag("SHARES"); Text("Amount (\(state.preview?.baseCurrency ?? "CAD"))").tag("AMOUNT")
            }.pickerStyle(.segmented)
        }
        VStack(alignment: .leading, spacing: 4) {
            TextField(state.mode.name == "SHARES" ? "Number of shares" : "Virtual cash to invest", text: $input)
                .keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                .onChange(of: input) { _, value in order.setInput(text: value) }
            Text(state.mode.name == "SHARES" ? "Up to 4 decimal places (fractional shares are simulated)." : "Converted to whole 0.0001 shares at the server's price.")
                .font(.caption).foregroundStyle(colors.textSecondary)
        }
        if let p = state.preview, p.quantity != "0" {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                DetailRow(label: "Quantity", value: "\(client.shares(value: p.quantity)) shares")
                DetailRow(label: state.side.name == "BUY" ? "Estimated cost" : "Estimated proceeds", value: client.money(value: p.estimatedTotal, currency: p.baseCurrency))
                DetailRow(label: "Available virtual cash", value: client.money(value: p.cashAvailable, currency: p.baseCurrency))
                DetailRow(label: "Cash after this trade", value: client.money(value: p.cashAfter, currency: p.baseCurrency))
                if state.side.name == "BUY", let max = p.maxOpenHoldings?.intValue {
                    DetailRow(label: "Free holding slots", value: p.opensNewHolding ? "\(Swift.max(max - Int(p.openHoldings), 0)) of \(max) left" : "Adds to a holding you own")
                }
            }
            .stockCard()
        }
        if state.previewing && state.preview != nil { ProgressView() }
        if let blocker = state.blocker {
            Text(blocker.message).foregroundStyle(colors.negativeText)
            if ["QUOTE_STALE", "QUOTE_MISSING", "FX_UNAVAILABLE"].contains(blocker.code) { Button("Check the price again") { order.refreshPreview(initial: false) } }
        }
        if let notice = state.notice { Text(notice).foregroundStyle(colors.cautionText) }
        if state.preview != nil, let error = state.error { Text(error).foregroundStyle(colors.negativeText) }
        Text(client.disclosure).font(.caption).foregroundStyle(colors.textSecondary)
        Button("Review order") { order.review() }.buttonStyle(.borderedProminent).controlSize(.large).frame(maxWidth: .infinity)
            .disabled(!(state.canReview || state.blocker?.code == "HOLDING_LIMIT"))
    }

    @ViewBuilder
    private func review(_ state: PracticeOrderState, _ colors: StockColors) -> some View {
        if let p = state.preview {
            Text("Review your \(verb(state).lowercased())").font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title2)).accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                DetailRow(label: "Instrument", value: "\(p.instrument.name ?? state.symbol) (\(state.symbol))")
                DetailRow(label: "Action", value: verb(state))
                DetailRow(label: "Quantity", value: "\(client.shares(value: p.quantity)) shares")
                DetailRow(label: "Indicative price", value: client.price(value: p.price, currency: p.priceCurrency))
                DetailRow(label: "Estimated total", value: client.money(value: p.estimatedTotal, currency: p.baseCurrency))
                DetailRow(label: "Cash remaining", value: client.money(value: p.cashAfter, currency: p.baseCurrency))
            }
            .stockCard()
            Text(client.disclosure).font(StockStepsTheme.font(type.small))
            Text("The final simulated price is set when you confirm. If it has moved by more than 1%, you'll be asked to review again.").font(.caption).foregroundStyle(colors.textSecondary)
            if let error = state.error { Text(error).foregroundStyle(colors.negativeText) }
            Button(state.executing ? "Confirming…" : "Confirm \(verb(state))") { order.confirm() }.buttonStyle(.borderedProminent).controlSize(.large)
                .frame(maxWidth: .infinity).disabled(state.executing)
            Button("Back") { order.back() }.frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget)).disabled(state.executing)
        }
    }

    @ViewBuilder
    private func done(_ state: PracticeOrderState, _ colors: StockColors) -> some View {
        if let r = state.result {
            let t = r.transaction
            Text("\(verb(state)) complete").font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title2)).foregroundStyle(colors.positiveText).accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                DetailRow(label: "Instrument", value: "\(t.instrument.name ?? t.instrument.symbol) (\(t.instrument.symbol))")
                DetailRow(label: "Quantity", value: "\(client.shares(value: t.quantity)) shares")
                DetailRow(label: "Simulated price", value: client.price(value: t.price, currency: t.priceCurrency))
                DetailRow(label: "Total", value: client.money(value: t.amount, currency: r.baseCurrency))
                DetailRow(label: "Virtual cash now", value: client.money(value: r.cashAfter, currency: r.baseCurrency))
                DetailRow(label: "Reference", value: t.id)
            }
            .stockCard()
            Text("Simulated with virtual money. No real order was placed.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
            Button("View Holdings", action: onViewHoldings).buttonStyle(.borderedProminent).controlSize(.large).frame(maxWidth: .infinity)
            Button("Explore Stocks", action: onExplore).buttonStyle(.bordered).controlSize(.large).frame(maxWidth: .infinity)
            Button("Continue Learning", action: onLearn).frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget))
        }
    }
}

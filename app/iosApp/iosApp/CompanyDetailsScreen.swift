import Shared
import SwiftUI

/// Company Details: one scrolling page ordered for beginners. Sections load and fail independently.
struct CompanyDetailsScreen: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    let model: CompanyDetailsModel
    let watched: Bool
    let watchlistEnabled: Bool
    let onToggleWatchlist: () -> Void
    let onOpenURL: (URL) -> Void
    @State private var education: GlanceMetric?
    @State private var showEvidence = false
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                header(colors)
                chartSection(colors).padding(.top, CGFloat(space.lg))
                if let why = model.whyMoving.value { whySection(why).padding(.top, CGFloat(space.xl)) }
                if let overview = model.overview.value { overviewSections(overview, colors) }
                newsSection(colors).padding(.top, CGFloat(space.xl))
                Text("Educational information, not a recommendation to buy or sell.")
                    .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
                    .padding(.top, CGFloat(space.lg))
            }
            .frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.bottom, CGFloat(space.xl))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .navigationTitle(model.overview.value?.name ?? model.symbol)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if watchlistEnabled {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onToggleWatchlist) { Image(systemName: watched ? "star.fill" : "star") }
                        .accessibilityLabel(watched ? "Remove from watchlist" : "Add to watchlist")
                }
            }
        }
        .sheet(item: $education) { metric in
            InfoSheet(title: educationTitle(metric), message: educationBody(metric))
        }
        .sheet(isPresented: $showEvidence) {
            if let rows = model.overview.value?.insightEvidence {
                InfoSheet(title: "Where this comes from", rows: rows.map { ($0.label, $0.value) })
            }
        }
    }

    @ViewBuilder
    private func header(_ colors: StockColors) -> some View {
        switch model.overview {
        case .loading:
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                StockSkeleton(width: CGFloat(StockStepsTheme.dimensions.avatar), height: CGFloat(StockStepsTheme.dimensions.avatar))
                StockSkeleton(width: 180); StockSkeleton(width: 120, height: 24)
            }.accessibilityElement(children: .ignore).accessibilityLabel("Loading")
        case .unavailable:
            StockSectionMessage(message: "Unable to load company data.", actionTitle: "Try again", action: model.loadCore).stockCard()
        case .content(let overview):
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                HStack(spacing: CGFloat(space.md)) {
                    StockTickerAvatar(symbol: overview.symbol, logoUrl: overview.logoUrl, size: CGFloat(StockStepsTheme.dimensions.avatar))
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        Text(overview.name).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).foregroundStyle(colors.textPrimary)
                            .accessibilityAddTraits(.isHeader)
                        Text(overview.listing).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                    }
                }
                if let price = overview.price {
                    Text(price).font(StockStepsTheme.font(type.largeNumber, relativeTo: .largeTitle)).foregroundStyle(colors.textPrimary)
                        .padding(.top, CGFloat(space.sm))
                }
                HStack(spacing: CGFloat(space.sm)) {
                    StockPriceChange(percentage: [overview.changeAmount, overview.changeAmount == nil ? overview.changePercent : "(\(overview.changePercent))"]
                        .compactMap { $0 }.joined(separator: " "), direction: overview.direction, style: type.numberMedium)
                    Text("Today").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                }
                MarketStatusIndicator(status: overview.marketStatus)
            }
        }
    }

    private func chartSection(_ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            ZStack {
                switch model.chart {
                case .loading: StockSkeleton(width: 1, height: 1).frame(maxWidth: .infinity, maxHeight: .infinity).accessibilityLabel("Loading")
                case .unavailable: StockSectionMessage(message: "Price history isn't available for this range.", actionTitle: "Try again", action: model.retryChart)
                case .content(let chart):
                    let closes = chart.points.map(\.close)
                    let direction: PriceDirection = model.range == .oneDay ? (model.overview.value?.direction ?? .unavailable)
                        : HomePresentation.shared.direction(percent: KotlinDouble(value: (closes.last ?? 0) - (closes.first ?? 0)))
                    StockSparkline(closes: closes, direction: direction)
                        .accessibilityElement().accessibilityLabel("Price over \(model.range.label): from \(closes.first ?? 0, specifier: "%.2f") to \(closes.last ?? 0, specifier: "%.2f")")
                }
            }
            .frame(height: CGFloat(StockStepsTheme.dimensions.chartHeight))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: CGFloat(space.xs)) {
                    ForEach(ChartRange.entries, id: \.self) { option in
                        StockChip(title: option.label, selected: option == model.range) { model.select(range: option) }
                    }
                }
            }
        }
        .stockCard()
    }

    private func whySection(_ why: WhyMoving) -> some View {
        StockInsightCard(
            title: why.summary,
            message: why.whyItMatters.map { "Why this matters: \($0)" } ?? "",
            actionTitle: why.sources.isEmpty ? nil : "\(why.sources.count) sources · Read",
            action: { if let url = why.sources.first.flatMap({ URL(string: $0.url) }) { onOpenURL(url) } }
        )
    }

    @ViewBuilder
    private func overviewSections(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        let short = overview.name.components(separatedBy: ",").first ?? overview.name
        titled("At a glance") {
            let columns = typeSize.isAccessibilitySize ? 1 : 2
            let rows = stride(from: 0, to: overview.glance.count, by: columns).map { Array(overview.glance[$0..<min($0 + columns, overview.glance.count)]) }
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                    if index > 0 { StockDivider() }
                    HStack(alignment: .top, spacing: CGFloat(space.md)) {
                        ForEach(row, id: \.id) { metric in
                            Button { education = metric } label: { metricView(metric, colors) }.buttonStyle(.plain)
                                .accessibilityHint("Learn what this means")
                        }
                    }
                }
            }.stockCard(padding: CGFloat(space.md))
        }
        if !overview.assessment.isEmpty {
            titled("How does \(short) look?") {
                VStack(spacing: 0) {
                    ForEach(Array(overview.assessment.enumerated()), id: \.offset) { index, row in
                        if index > 0 { StockDivider() }
                        HStack {
                            StockSettingsRow(title: row.title, subtitle: row.detail)
                            if let comparison = row.comparison {
                                Text(comparison).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
                            }
                        }
                    }
                }.padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0)
            }
        }
        if let insight = overview.insight {
            StockInsightCard(title: "Beginner insight", message: insight, actionTitle: "Why?", action: { showEvidence = true })
                .padding(.top, CGFloat(space.xl))
        }
        if let about = overview.about {
            titled("About \(short)") {
                Text(about).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                if let classification = overview.classification {
                    Text(classification).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
                }
            }
        }
        titled("Financial highlights") {
            if overview.highlights.isEmpty {
                StockSectionMessage(message: "This information is temporarily unavailable.").stockCard()
            } else {
                infoRows(overview.highlights.map { ($0.label, $0.value, $0.helper) }, colors)
            }
        }
        if let valuation = overview.valuation {
            titled("Valuation") {
                VStack(alignment: .leading, spacing: 0) {
                    infoRowsContent([("Current P/E", valuation.currentPe, nil), ("Historical average", valuation.historicalAverage, nil), ("Difference", valuation.difference, nil)]
                        .compactMap { label, value, helper in value.map { (label, $0, helper) } }, colors)
                    Text(valuation.explanation).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                        .padding(.vertical, CGFloat(space.sm))
                }.padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0)
                if let pe = overview.glance.first(where: { $0.id == "pe" }) {
                    Button("Understand P/E →") { education = pe }.font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.primaryText)
                        .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
                }
            }
        }
    }

    private func newsSection(_ colors: StockColors) -> some View {
        titledContent("Recent news") {
            VStack(spacing: 0) {
                switch model.news {
                case .loading: ForEach(0..<2, id: \.self) { _ in StockNewsCardSkeleton() }
                case .unavailable: StockSectionMessage(message: "News isn't available right now.", actionTitle: "Try again", action: model.loadNews)
                case .content(let news):
                    if news.isEmpty { StockSectionMessage(message: "No recent company news.") }
                    ForEach(Array(news.enumerated()), id: \.element.id) { index, article in
                        if index > 0 { StockDivider() }
                        StockNewsCard(model: article, onOpen: onOpenURL)
                    }
                }
            }.padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0)
        }
    }

    private func metricView(_ metric: GlanceMetric, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            Text(metric.label).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
            if let direction = metric.direction, direction != .unavailable {
                StockPriceChange(percentage: metric.value, direction: direction, style: type.numberEmphasis)
            } else {
                Text(metric.value).font(StockStepsTheme.font(type.numberEmphasis, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
            }
            Text(metric.helper).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
        }
        .frame(maxWidth: .infinity, minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget), alignment: .leading)
        .padding(.vertical, CGFloat(space.xs))
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    private func infoRows(_ rows: [(String, String, String?)], _ colors: StockColors) -> some View {
        infoRowsContent(rows, colors).padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0)
    }

    private func infoRowsContent(_ rows: [(String, String, String?)], _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                if index > 0 { StockDivider() }
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    HStack {
                        Text(row.0).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                        Spacer()
                        Text(row.1).font(StockStepsTheme.font(type.numberMedium)).foregroundStyle(colors.textPrimary)
                    }
                    if let helper = row.2 {
                        Text(helper).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
                    }
                }
                .padding(.vertical, CGFloat(space.sm))
                .accessibilityElement(children: .combine)
            }
        }
    }

    private func titled<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        titledContent(title, content: content).padding(.top, CGFloat(space.xl))
    }

    private func titledContent<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: title)
            content()
        }
    }

    private func educationTitle(_ metric: GlanceMetric) -> String {
        switch metric.id {
        case "marketCap": "What is market cap?"
        case "pe": "What is P/E?"
        case "revenueGrowth": "What is revenue growth?"
        default: "What is dividend yield?"
        }
    }

    private func educationBody(_ metric: GlanceMetric) -> String {
        let name = (model.overview.value?.name ?? model.symbol).components(separatedBy: ",").first ?? model.symbol
        switch metric.id {
        case "marketCap":
            return "Market cap is the total value of all of a company's shares: share price × number of shares. \(name)'s market cap is \(metric.value). It describes size, not whether the shares are cheap or expensive."
        case "pe":
            guard let pe = Double(metric.value) else {
                return "P/E compares the share price with a year of earnings per share. It isn't meaningful when a company has no positive earnings, so StockSteps doesn't show one for \(name) right now."
            }
            return "P/E compares the share price with a year of earnings per share. A P/E of \(metric.value) means investors pay about $\(Int(pe)) for every $1 \(name) earned over the last year. A higher or lower P/E alone doesn't mean a stock should be bought or sold."
        case "revenueGrowth":
            return metric.value == "—" ? "This value isn't available for \(name) right now." :
                "Revenue is the money a company brings in from sales. \(name)'s revenue changed \(metric.value) compared with the year before. Growth shows direction, not profit."
        default:
            return metric.value == "—" ? "This value isn't available for \(name) right now." :
                "Dividend yield is the yearly cash dividend divided by the share price. For \(name) it is \(metric.value). Some companies pay no dividend and reinvest their earnings instead."
        }
    }
}

extension GlanceMetric: @retroactive Identifiable {}

/// Bottom sheet for plain-English explanations and the evidence behind the insight.
private struct InfoSheet: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss
    let title: String
    var message: String = ""
    var rows: [(String, String)] = []

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.sm)) {
                    if !message.isEmpty { Text(message).foregroundStyle(colors.textBody) }
                    ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                        if index > 0 { StockDivider() }
                        HStack {
                            Text(row.0).foregroundStyle(colors.textBody)
                            Spacer()
                            Text(row.1).monospacedDigit().foregroundStyle(colors.textPrimary)
                        }
                        .padding(.vertical, CGFloat(StockStepsTheme.spacing.xs))
                        .accessibilityElement(children: .combine)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

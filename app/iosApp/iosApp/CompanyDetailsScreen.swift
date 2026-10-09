import Shared
import SwiftUI

/// Company Details: one scrolling page ordered for beginners — identity, price, chart and quote
/// stats, why it moved, at a glance, how it looks, insight, about, highlights, valuation,
/// ranges, key ratios, sector, news. Sections load and fail independently.
struct CompanyDetailsScreen: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    let model: CompanyDetailsModel
    let watched: Bool
    let watchlistEnabled: Bool
    let onToggleWatchlist: () -> Void
    let onOpenURL: (URL) -> Void
    let onOpenFinancials: () -> Void
    let onOpenValuation: () -> Void
    let onOpenNews: () -> Void
    let onOpenMovement: () -> Void
    var onAddPortfolio: () -> Void = {}
    var onCompare: () -> Void = {}
    var onEarnings: () -> Void = {}
    /// Saved Guided Research progress for this company; nil when never started.
    var researchCompleted: Int? = nil
    var showResearch = false
    var onResearch: () -> Void = {}
    var onPracticeBuy: () -> Void = {}
    /// Next earnings date (nil hides the section).
    var nextEarnings: CompanyEarningsState? = nil
    var onEarningsCalendar: (() -> Void)? = nil
    var onRetryEarnings: () -> Void = {}
    var onLatestResults: (() -> Void)? = nil
    /// Reminder control for the next earnings event (nil hides it).
    var earningsReminder: String? = nil
    var onRemindEarnings: () -> Void = {}
    @State private var education: GlanceMetric?
    @State private var showEvidence = false
    @State private var showSources = false
    @State private var aboutExpanded = false
    @State private var scrub: Int?
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography
    private let dims = StockStepsTheme.dimensions

    /// Next earnings date, timing and status; never an invented date.
    @ViewBuilder private func earningsSection(_ e: CompanyEarningsState, _ colors: StockColors) -> some View {
        titled("Earnings") {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                if e.loading {
                    Text("Loading the next earnings date…").font(.subheadline).foregroundStyle(colors.textSecondary)
                } else if let error = e.error {
                    StockSectionMessage(message: error, actionTitle: "Try again", action: onRetryEarnings)
                } else if let dateText = e.dateText {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Next earnings").font(.caption).foregroundStyle(colors.textSecondary)
                        Text(dateText).font(.headline).foregroundStyle(colors.textPrimary)
                        if let timing = e.timingText { Text(timing).font(.subheadline).foregroundStyle(colors.textBody) }
                        if let status = e.statusText { Text(status).font(.caption).foregroundStyle(colors.textSecondary) }
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Next earnings: \(e.spokenDate ?? dateText). \(e.timingText ?? ""). \(e.statusText ?? "").")
                } else {
                    Text(e.message ?? "").font(.subheadline).foregroundStyle(colors.textSecondary)
                }
                // Latest published results (comparable measures only), linking to the full Earnings Results.
                if let title = e.latestTitle {
                    Divider()
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title).font(.caption).foregroundStyle(colors.textSecondary)
                        if let summary = e.latestSummary { Text(summary).font(.headline).foregroundStyle(colors.textPrimary) }
                    }
                    .accessibilityElement(children: .combine)
                    if let onLatestResults { Button("View Results", action: onLatestResults).buttonStyle(.bordered).frame(minHeight: 48) }
                }
                if let earningsReminder, e.eventId != nil { ReminderControlButton(control: earningsReminder, compact: true, action: onRemindEarnings) }
                if e.sampleData { Text("Sample earnings data.").font(.caption).foregroundStyle(colors.cautionText) }
                HStack {
                    if let onEarningsCalendar { Button("View Earnings Calendar", action: onEarningsCalendar).frame(minHeight: 48) }
                    Button("Earnings history", action: onEarnings).frame(minHeight: 48)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .stockCard()
        }
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                header(colors)
                ScrollView(.horizontal, showsIndicators: false) { HStack {
                    Button("Add to portfolio", systemImage: "briefcase", action: onAddPortfolio).buttonStyle(.bordered)
                    Button("Compare", systemImage: "square.split.2x1", action: onCompare).buttonStyle(.bordered)
                    Button("Earnings", systemImage: "calendar", action: onEarnings).buttonStyle(.bordered)
                    Button("Practice Buy", systemImage: "graduationcap", action: onPracticeBuy).buttonStyle(.bordered)
                        .accessibilityHint("Opens a simulated order with virtual money")
                } }
                chartSection(colors).padding(.top, CGFloat(space.lg))
                if showResearch { UnderstandStockCard(completed: researchCompleted, action: onResearch).padding(.top, CGFloat(space.xl)) }
                if let nextEarnings { earningsSection(nextEarnings, colors).padding(.top, CGFloat(space.xl)) }
                switch model.whyMoving {
                case .content(let why): if let why { whySection(why, colors).padding(.top, CGFloat(space.xl)) }
                case .unavailable:
                    titled("Why Did It Move?") {
                        StockSectionMessage(message: "We couldn't load the explanation right now.", actionTitle: "Try again", action: model.loadWhyMoving)
                            .stockCard(bordered: false)
                    }
                case .loading: EmptyView()
                }
                if let overview = model.overview.value { overviewSections(overview, colors) }
                newsSection(colors).padding(.top, CGFloat(space.xl))
                Text("Educational information, not a recommendation to buy or sell.")
                    .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
                    .padding(.top, CGFloat(space.lg))
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.bottom, CGFloat(space.xl))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if watchlistEnabled {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onToggleWatchlist) {
                        Image(systemName: watched ? "star.fill" : "star").foregroundStyle(watched ? colors.positive : colors.iconSecondary)
                    }
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
        .sheet(isPresented: $showSources) {
            if let why = model.whyMoving.value ?? nil {
                InfoSheet(title: "Why Did It Move?", links: why.sources.compactMap { source in
                    URL(string: source.url).map { (source.publisher ?? source.title, source.title, $0) }
                }, onOpen: onOpenURL)
            }
        }
    }

    // MARK: Header

    @ViewBuilder
    private func header(_ colors: StockColors) -> some View {
        switch model.overview {
        case .loading:
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                StockSkeleton(width: CGFloat(dims.avatar), height: CGFloat(dims.avatar))
                StockSkeleton(width: 180); StockSkeleton(width: 120, height: 24)
            }.accessibilityElement(children: .ignore).accessibilityLabel("Loading")
        case .unavailable:
            StockSectionMessage(message: "Unable to load company data.", actionTitle: "Try again", action: model.loadCore).stockCard(bordered: false)
        case .content(let overview):
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                HStack(alignment: .top, spacing: CGFloat(space.md)) {
                    StockTickerAvatar(symbol: overview.symbol, logoUrl: overview.logoUrl, size: CGFloat(dims.avatar))
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        Text(overview.name).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3).bold()).foregroundStyle(colors.textPrimary)
                            .accessibilityAddTraits(.isHeader)
                        Text(overview.listing).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                        if !overview.tags.isEmpty { tags(overview.tags).padding(.top, CGFloat(space.xxs)) }
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
                HStack(spacing: CGFloat(space.sm)) {
                    MarketStatusIndicator(status: overview.marketStatus)
                    if let updated = overview.updatedAt.map({ Self.quoteTime($0.int64Value) }) {
                        Text("Updated \(updated)").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textTertiary)
                    }
                }
            }
        }
    }

    /// Quote time in exchange (New York) time, e.g. "10:41 AM EDT".
    private static func quoteTime(_ seconds: Int64) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a z"
        formatter.locale = Locale(identifier: "en_US")
        formatter.timeZone = TimeZone(identifier: "America/New_York")
        return formatter.string(from: Date(timeIntervalSince1970: TimeInterval(seconds)))
    }

    private func tags(_ values: [String]) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: CGFloat(space.xs)) { ForEach(values, id: \.self) { StockTag(text: $0) } }
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) { ForEach(values, id: \.self) { StockTag(text: $0) } }
        }
    }

    // MARK: Chart and quote stats

    private func chartSection(_ colors: StockColors) -> some View {
        let chart = model.chart.value
        let summary = chart.flatMap { ChartPresentation.shared.summary(points: $0.points, range: model.range, dayDirection: model.overview.value?.direction) }
        let scrubLabel = scrub.flatMap { index in chart?.points[safe: index] }.map { ChartPresentation.shared.scrubLabel(point: $0, range: model.range) }
        let directionColor: Color = switch summary?.direction {
        case .up: colors.positiveText
        case .down: colors.negativeText
        default: colors.textSecondary
        }
        let changeColor = scrubLabel != nil ? colors.textPrimary : directionColor
        return VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            StockPillSelector(options: ChartRange.entries, selected: model.range, label: { $0.label }) { model.select(range: $0) }
            Text(scrubLabel ?? summary?.change ?? " ")
                .font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(changeColor)
            ZStack {
                switch model.chart {
                case .loading: StockSkeleton(width: 1, height: 1).frame(maxWidth: .infinity, maxHeight: .infinity).accessibilityLabel("Loading")
                case .unavailable: StockSectionMessage(message: "Price history isn't available for this range.", actionTitle: "Try again", action: model.retryChart)
                case .content:
                    if let summary {
                        StockLineChart(summary: summary) { scrub = $0 }
                    } else {
                        StockSectionMessage(message: "Price history isn't available for this range.", actionTitle: "Try again", action: model.retryChart)
                    }
                }
            }
            .frame(height: CGFloat(dims.chartHeight))
            if let stats = model.overview.value?.quickStats {
                StockDivider().padding(.top, CGFloat(space.xs))
                let half = (stats.count + 1) / 2
                HStack(alignment: .top, spacing: CGFloat(space.lg)) {
                    VStack(spacing: 0) { ForEach(Array(stats.prefix(half)), id: \.label) { StockInfoLine(label: $0.label, value: $0.value, compact: true) } }
                    VStack(spacing: 0) { ForEach(Array(stats.dropFirst(half)), id: \.label) { StockInfoLine(label: $0.label, value: $0.value, compact: true) } }
                }
            }
        }
        .padding(.horizontal, CGFloat(space.md) - CGFloat(space.sm))
        .stockCard(padding: CGFloat(space.sm), bordered: false)
    }

    // MARK: Why did it move

    private func whySection(_ why: WhyMoving, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            HStack(spacing: CGFloat(space.sm)) {
                Image(systemName: "lightbulb.fill").foregroundStyle(colors.educationAccent).accessibilityHidden(true)
                Text("Why Did It Move?").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                if !why.sources.isEmpty {
                    Button("\(why.sources.count) sources →") { showSources = true }
                        .font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.primaryText)
                        .padding(.horizontal, CGFloat(space.sm)).padding(.vertical, CGFloat(space.xs))
                        .background(colors.surface, in: Capsule())
                }
            }
            Text(why.summary).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
            if let matters = why.whyItMatters {
                HStack(alignment: .top, spacing: CGFloat(space.sm)) {
                    Image(systemName: "info.circle.fill").foregroundStyle(colors.learnAccent).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        Text("Keep in mind").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.learnAccent)
                        Text(matters).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                    }
                }
                .padding(CGFloat(space.sm))
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(colors.learnContainerStart, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
            }
            if !why.sources.isEmpty {
                HStack(spacing: CGFloat(space.md)) {
                    ForEach(Array(why.sources.enumerated()), id: \.offset) { _, source in
                        Button(source.publisher ?? source.title) { if let url = URL(string: source.url) { onOpenURL(url) } }
                            .font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.primaryText).lineLimit(1)
                    }
                }
            }
            Button("See full breakdown →", action: onOpenMovement)
                .font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.primaryText)
                .frame(minHeight: CGFloat(dims.touchTarget))
        }
        .padding(CGFloat(space.md))
        .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.cardLarge)))
    }

    // MARK: Overview sections

    @ViewBuilder
    private func overviewSections(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        titled("At a Glance") { glance(overview.glance, colors) }
        if !overview.assessment.isEmpty {
            titled("How Does \(overview.shortName) Look?") {
                VStack(spacing: 0) {
                    ForEach(Array(overview.assessment.enumerated()), id: \.offset) { index, row in
                        if index > 0 { StockDivider() }
                        let (icon, container, tint) = assessmentIcon(row.id, colors)
                        StockAssessmentRow(systemName: icon, iconContainer: container, iconContent: tint, title: row.title, detail: row.detail,
                                           badge: row.badge, tone: row.tone, note: row.explanation,
                                           action: row.id == "valuation" ? { education = overview.glance.first { $0.id == "pe" } } : nil)
                    }
                }
                .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0, bordered: false)
            }
        }
        if let insight = overview.insight {
            StockInsightCard(title: "Beginner Insight", message: insight, actionTitle: "Why does this matter?", action: { showEvidence = true })
                .padding(.top, CGFloat(space.xl))
        }
        if overview.about != nil || overview.country != nil { aboutSection(overview, colors).padding(.top, CGFloat(space.xl)) }
        highlightsSection(overview, colors).padding(.top, CGFloat(space.xl))
        valuationSection(overview, colors).padding(.top, CGFloat(space.xl))
        if overview.dayRange != nil || overview.yearRange != nil {
            VStack(alignment: .leading, spacing: CGFloat(space.lg)) {
                if let range = overview.yearRange { StockRangeBar(title: "52-Week Range", range: range) }
                if let range = overview.dayRange { StockRangeBar(title: "Day Range", range: range) }
            }
            .stockCard(padding: CGFloat(space.md), bordered: false)
            .padding(.top, CGFloat(space.xl))
        }
        if !overview.keyRatios.isEmpty {
            titled("Key Ratios") {
                VStack(spacing: 0) { ForEach(overview.keyRatios, id: \.label) { StockInfoLine(label: $0.label, value: $0.value, compact: true) } }
                    .padding(.horizontal, CGFloat(space.md)).padding(.vertical, CGFloat(space.xs)).stockCard(padding: 0, bordered: false)
            }
        }
        if let sector = overview.sector, let industry = overview.industry {
            titled("Sector & Industry") {
                HStack(spacing: CGFloat(space.sm)) {
                    sectorTile("Sector", sector, "building.columns.fill", colors)
                    sectorTile("Industry", industry, "chart.pie.fill", colors)
                }
                Text("\(overview.shortName) is part of the \(sector) sector, in the \(industry) industry. Comparing a company with others in its industry gives its numbers more context.")
                    .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                    .padding(CGFloat(space.md)).frame(maxWidth: .infinity, alignment: .leading)
                    .background(colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
            }
        }
    }

    private func glance(_ metrics: [GlanceMetric], _ colors: StockColors) -> some View {
        let columns = typeSize.isAccessibilitySize ? 1 : 2
        return LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: CGFloat(space.sm)), count: columns), spacing: CGFloat(space.sm)) {
            ForEach(metrics, id: \.id) { metric in
                let (icon, container, tint) = glanceIcon(metric.id, colors)
                Button { education = metric } label: {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                            Text(metric.label).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
                            if let direction = metric.direction, direction != .unavailable {
                                StockPriceChange(percentage: metric.value, direction: direction, style: type.numberEmphasis)
                            } else {
                                Text(metric.value).font(StockStepsTheme.font(type.numberEmphasis, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                            }
                            Text(metric.helper).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
                        }
                        Spacer(minLength: 0)
                        StockIconTile(systemName: icon, container: container, content: tint)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .stockCard(padding: CGFloat(space.md), bordered: false)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .combine)
                .accessibilityHint("Learn what this means")
            }
        }
    }

    private func aboutSection(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "About \(overview.shortName)",
                               actionTitle: overview.aboutFull == nil ? nil : (aboutExpanded ? "See Less" : "See More"),
                               action: overview.aboutFull == nil ? nil : { aboutExpanded.toggle() })
            if let text = aboutExpanded ? (overview.aboutFull ?? overview.about) : overview.about {
                Text(text).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
            }
            if let country = overview.country {
                Label(country, systemImage: "globe").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
            }
            let values = [overview.sector, overview.industry].compactMap { $0 }
            if !values.isEmpty { tags(values) }
        }
    }

    private func highlightsSection(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "Financial Highlights", actionTitle: "See Financials", action: onOpenFinancials)
            VStack(spacing: 0) {
                if overview.highlights.isEmpty {
                    StockSectionMessage(message: "Financial highlights aren't available right now.")
                } else {
                    ForEach(Array(overview.highlights.enumerated()), id: \.offset) { index, row in
                        if index > 0 { StockDivider() }
                        StockInfoLine(label: row.label, value: row.value, helper: row.helper, change: row.change ?? "—", direction: row.changeDirection)
                    }
                }
            }
            .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0, bordered: false)
            if let basis = overview.highlightsBasis {
                Text(basis).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
            }
        }
    }

    private func valuationSection(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "Valuation", actionTitle: "See Details", action: onOpenValuation)
            if let valuation = overview.valuation {
                let above = valuation.position == .above
                HStack(spacing: CGFloat(space.sm)) {
                    valuationTile("Current P/E", valuation.currentPe ?? "N/A", colors.textPrimary, colors)
                    valuationTile(valuation.historicalLabel, valuation.historicalAverage ?? "—", colors.textPrimary, colors)
                    valuationTile("Difference", valuation.difference ?? "—", above ? colors.cautionText : colors.textPrimary, colors)
                }
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    if let headline = valuation.headline {
                        Label(headline, systemImage: "tag.fill").font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(above ? colors.cautionText : colors.primaryText)
                    }
                    Text(valuation.explanation).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                }
                .padding(CGFloat(space.md)).frame(maxWidth: .infinity, alignment: .leading)
                .background(above ? colors.warningContainer : colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                VStack(alignment: .leading, spacing: 0) {
                    link("Understand P/E Ratio") { education = overview.glance.first { $0.id == "pe" } }
                    link("See detailed valuation", action: onOpenValuation)
                }
            } else {
                StockSectionMessage(message: "Valuation history isn't available for this company.").stockCard(bordered: false)
            }
        }
    }

    private func valuationTile(_ label: String, _ value: String, _ valueColor: Color, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            Text(label).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary).lineLimit(2)
            Text(value).font(StockStepsTheme.font(type.numberEmphasis, relativeTo: .headline)).foregroundStyle(valueColor).lineLimit(1)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .stockCard(padding: CGFloat(space.sm), bordered: false)
        .accessibilityElement(children: .combine)
    }

    private func sectorTile(_ label: String, _ value: String, _ icon: String, _ colors: StockColors) -> some View {
        HStack(spacing: CGFloat(space.sm)) {
            StockIconTile(systemName: icon, container: colors.primaryContainer, content: colors.primaryText)
            VStack(alignment: .leading, spacing: 0) {
                Text(label).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
                Text(value).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .stockCard(padding: CGFloat(space.sm), bordered: false)
        .accessibilityElement(children: .combine)
    }

    private func newsSection(_ colors: StockColors) -> some View {
        let hasNews = (model.news.value?.isEmpty == false)
        return VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "Recent News", actionTitle: hasNews ? "View All" : nil, action: hasNews ? onOpenNews : nil)
            VStack(spacing: 0) {
                switch model.news {
                case .loading: ForEach(0..<2, id: \.self) { _ in StockNewsCardSkeleton() }
                case .unavailable: StockSectionMessage(message: "News is temporarily unavailable.", actionTitle: "Try again", action: model.loadNews)
                case .content(let news):
                    if news.isEmpty { StockSectionMessage(message: "No recent company news.") }
                    ForEach(Array(news.enumerated()), id: \.element.id) { index, article in
                        if index > 0 { StockDivider() }
                        StockNewsCard(model: article, onOpen: onOpenURL)
                    }
                }
            }
            .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0, bordered: false)
        }
    }

    // MARK: Helpers

    private func link(_ title: String, action: @escaping () -> Void) -> some View {
        Button("\(title) →", action: action)
            .font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(StockStepsTheme.colors(scheme).primaryText)
            .frame(minHeight: CGFloat(dims.touchTarget))
    }

    private func glanceIcon(_ id: String, _ colors: StockColors) -> (String, Color, Color) {
        switch id {
        case "marketCap": ("building.columns.fill", colors.primaryContainer, colors.primaryText)
        case "pe": ("tag.fill", colors.warningContainer, colors.cautionText)
        case "revenueGrowth": ("chart.line.uptrend.xyaxis", colors.positiveContainer, colors.positiveText)
        default: ("dollarsign.circle.fill", colors.learnContainerStart, colors.learnAccent)
        }
    }

    private func assessmentIcon(_ id: String, _ colors: StockColors) -> (String, Color, Color) {
        switch id {
        case "growth": ("chart.line.uptrend.xyaxis", colors.positiveContainer, colors.positiveText)
        case "profitability": ("chart.pie.fill", colors.learnContainerStart, colors.learnAccent)
        case "health": ("shield.fill", colors.primaryContainer, colors.primaryText)
        default: ("tag.fill", colors.warningContainer, colors.cautionText)
        }
    }

    private func titled<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: title)
            content()
        }
        .padding(.top, CGFloat(space.xl))
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
        let name = model.overview.value?.shortName ?? model.symbol
        let value = metric.value.components(separatedBy: " (").first ?? metric.value
        let missing = ["—", "N/A", "None"].contains(value)
        switch metric.id {
        case "marketCap":
            return "Market cap is the total value of all of a company's shares: share price × number of shares. \(name)'s market cap is \(metric.value). It describes size, not whether the shares are cheap or expensive."
        case "pe":
            guard let pe = Double(value) else {
                return "P/E compares the share price with a year of earnings per share. It isn't meaningful when a company has no positive earnings, so StockSteps doesn't show one for \(name) right now."
            }
            return "P/E compares the share price with a year of earnings per share. A P/E of \(value) means investors pay about $\(Int(pe)) for every $1 \(name) earned over the last year. A higher or lower P/E alone doesn't mean a stock should be bought or sold."
        case "revenueGrowth":
            return missing ? "This value isn't available for \(name) right now." :
                "Revenue is the money a company brings in from sales. \(name)'s revenue changed \(value) compared with the year before. Growth shows direction, not profit."
        default:
            return missing ? "This value isn't available for \(name) right now." :
                "Dividend yield is the yearly cash dividend divided by the share price. For \(name) it is \(value). Some companies pay no dividend and reinvest their earnings instead."
        }
    }
}

extension GlanceMetric: @retroactive Identifiable {}

extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}

/// Bottom sheet for plain-English explanations, the evidence behind the insight, and sources.
private struct InfoSheet: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss
    let title: String
    var message: String = ""
    var rows: [(String, String)] = []
    var links: [(String, String, URL)] = []
    var onOpen: (URL) -> Void = { _ in }

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
                    ForEach(Array(links.enumerated()), id: \.offset) { index, link in
                        if index > 0 { StockDivider() }
                        StockAssessmentRow(systemName: "newspaper.fill", iconContainer: colors.primaryContainer, iconContent: colors.primaryText,
                                           title: link.0, detail: link.1, action: { onOpen(link.2) })
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

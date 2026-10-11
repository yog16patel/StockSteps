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
    @State private var actionsWidth: CGFloat = 0
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography
    private let dims = StockStepsTheme.dimensions

    private let present = CompanyDetailsPresentation.shared

    /// Next earnings date, timing and status (never an invented date), the latest results, then calendar and history as navigation rows.
    @ViewBuilder private func earningsSection(_ e: CompanyEarningsState, _ colors: StockColors) -> some View {
        titled("Earnings") {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                if e.loading {
                    Text("Loading the next earnings date…").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                } else if let error = e.error {
                    StockSectionMessage(message: error, actionTitle: "Try again", action: onRetryEarnings)
                } else if let dateText = e.dateText {
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        Text("Next earnings").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSupporting)
                        Text(dateText).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                        if let timing = e.timingText { Text(timing).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody) }
                        if let status = e.statusText { Text(status).font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.textMeta) }
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Next earnings: \(e.spokenDate ?? dateText). \(e.timingText ?? ""). \(e.statusText ?? "").")
                } else {
                    Text(e.message ?? "").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                }
                if let earningsReminder, e.eventId != nil { ReminderControlButton(control: earningsReminder, compact: true, action: onRemindEarnings) }
                // Latest published results (comparable measures only), linking to the full Earnings Results.
                if let title = e.latestTitle {
                    StockDivider()
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        Text(title).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSupporting)
                        if let summary = e.latestSummary { Text(summary).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle) }
                    }
                    .accessibilityElement(children: .combine)
                    if let onLatestResults {
                        Button("View Results", action: onLatestResults).buttonStyle(.stockSecondary)
                        // Phase 5 preview: the AI explanation lives on Earnings Results (StockSteps+, checked by the server there).
                        HStack(alignment: .top) {
                            Text("AI Earnings Insight: a plain-English explanation of these results, built only from verified figures.")
                                .font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.textSupporting)
                            Spacer(minLength: CGFloat(space.xs))
                            PlusBadge()
                        }
                        Button("Explain With AI", systemImage: "lightbulb", action: onLatestResults)
                            .font(StockStepsTheme.font(type.label)).foregroundStyle(colors.primaryText).frame(minHeight: CGFloat(dims.touchTarget))
                    }
                }
                if e.sampleData { Text("Sample earnings data.").font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.cautionText) }
                if let onEarningsCalendar {
                    StockDivider()
                    StockNavigationRow(title: "Earnings calendar", detail: "Upcoming report dates across companies", icon: "calendar", action: onEarningsCalendar)
                }
                StockDivider()
                StockNavigationRow(title: "Earnings history", detail: "Past results for this company", icon: "clock.arrow.circlepath", action: onEarnings)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .stockCard()
        }
    }

    /// Order (Phase 4C): identity and price → chart → why it moved → At a Glance → understand → valuation & financial health → earnings → news.
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                header(colors)
                chartSection(colors).padding(.top, CGFloat(space.md))
                switch model.whyMoving {
                case .content(let why): if let why { whySection(why, colors).padding(.top, CGFloat(space.sectionGap)) }
                case .unavailable:
                    titled("Why Did It Move?") {
                        StockSectionMessage(message: "We couldn't load the explanation right now.", actionTitle: "Try again", action: model.loadWhyMoving)
                            .stockCard()
                    }
                case .loading: EmptyView()
                }
                if let overview = model.overview.value {
                    overviewSections(overview, colors)
                } else if showResearch {
                    UnderstandStockCard(completed: researchCompleted, action: onResearch).padding(.top, CGFloat(space.sectionGap))
                }
                if let nextEarnings { earningsSection(nextEarnings, colors) }
                newsSection(colors).padding(.top, CGFloat(space.sectionGap))
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
                        Image(systemName: watched ? "star.fill" : "star").foregroundStyle(watched ? colors.primaryText : colors.iconSecondary)
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

    /// Compact identity (logo, wrapping name, ticker · exchange, tags) and the price as the focal point: price, signed change with its session
    /// ("Today" or the quote's date), the US
    /// market status chip and the quote time; then the secondary actions, wrapping instead of scrolling sideways.
    @ViewBuilder
    private func header(_ colors: StockColors) -> some View {
        switch model.overview {
        case .loading:
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                StockSkeleton(width: CGFloat(dims.avatar), height: CGFloat(dims.avatar))
                StockSkeleton(width: 180); StockSkeleton(width: 120, height: 24)
            }.accessibilityElement(children: .ignore).accessibilityLabel("Loading")
        case .unavailable:
            StockSectionMessage(message: "Unable to load company data.", actionTitle: "Try again", action: model.loadCore).stockCard()
        case .content(let overview):
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                HStack(alignment: .center, spacing: CGFloat(space.md)) {
                    StockTickerAvatar(symbol: overview.symbol, logoUrl: overview.logoUrl, size: CGFloat(dims.avatar))
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        Text(overview.name).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).foregroundStyle(colors.textTitle)
                            .accessibilityAddTraits(.isHeader)
                        Text(overview.listing).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                    }
                }
                if !overview.tags.isEmpty { tags(overview.tags) }
                // Price block: the price is the focal point; the session chip sits beside it (never inside the green/red change line);
                // one spoken sentence for price, change, quote time and session.
                let updated = overview.updatedAt.map { Self.quoteTime($0.int64Value) }
                let changeLabel = present.changeLabel(overview: overview)
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    ViewThatFits(in: .horizontal) {
                        HStack(alignment: .center, spacing: CGFloat(space.sm)) {
                            priceText(overview, colors)
                            Spacer(minLength: 0)
                            MarketStatusIndicator(status: overview.marketStatus)
                        }
                        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                            priceText(overview, colors)
                            MarketStatusIndicator(status: overview.marketStatus)
                        }
                    }
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: CGFloat(space.sm)) { priceChange(overview, changeLabel, colors) }
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { priceChange(overview, changeLabel, colors) }
                    }
                    if let updated {
                        Text("Updated \(updated)").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                    }
                }
                .padding(.top, CGFloat(space.sm))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel([present.priceDescription(overview: overview, updated: updated, change: changeLabel),
                                     present.statusDescription(status: overview.marketStatus).map { "\($0)." }].compactMap { $0 }.joined(separator: " "))
                actions.padding(.top, CGFloat(space.sm))
            }
        }
    }

    private func priceText(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        Text(overview.price ?? "—").font(StockStepsTheme.font(type.largeNumber, relativeTo: .largeTitle)).foregroundStyle(colors.textValue)
    }

    @ViewBuilder private func priceChange(_ overview: CompanyOverview, _ label: CompanyDetailsPresentation.ChangeLabel, _ colors: StockColors) -> some View {
        StockPriceChange(percentage: [overview.changeAmount, overview.changeAmount == nil ? overview.changePercent : "(\(overview.changePercent))"]
            .compactMap { $0 }.joined(separator: " "), direction: overview.direction, style: type.numberMedium, lineLimit: nil)
        Text(verbatim: label.text).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
    }

    /// Add to portfolio (primary) and Compare side by side, Practice Buy full width below; when the pair doesn't fit on one line each (narrow
    /// phones, accessibility sizes) every action takes its own full-width row. Earnings is offered here only when the page has no Earnings section.
    private var actions: some View {
        VStack(spacing: CGFloat(space.sm)) {
            if actionPairFits {
                HStack(spacing: CGFloat(space.sm)) {
                    wide("Add to portfolio", "plus", .stockPrimary, onAddPortfolio)
                    wide("Compare", "square.split.2x1", .stockSecondary, onCompare)
                }
            } else {
                wide("Add to portfolio", "plus", .stockPrimary, onAddPortfolio)
                wide("Compare", "square.split.2x1", .stockSecondary, onCompare)
            }
            wide("Practice Buy", "graduationcap", .stockSecondary, onPracticeBuy)
                .accessibilityHint("Opens a simulated order with virtual money")
            if nextEarnings == nil { wide("Earnings", "calendar", .stockSecondary, onEarnings) }
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { actionsWidth = $0 }
    }

    /// Add to portfolio and Compare share a row only while both labels (icon included) fit their equal share — the shared
    /// `StockActionLayout` rule Android uses (Phase 4C.3: on a 375 pt iPhone SE at the default size "Add to portfolio" wrapped onto two lines and
    /// the pair had unequal heights). Otherwise, and always at accessibility sizes, each action takes its own full-width row.
    private var actionPairFits: Bool {
        guard actionsWidth > 0 else { return typeSize < .xxxLarge }
        let font = StockStepsTheme.uiFont(type.bodySemiBold, weight: .semibold, typeSize: typeSize)
        // Label width = text + SF Symbol (about 1.2× the point size) + the Label's icon–title spacing.
        let widths = ["Add to portfolio", "Compare"].map { title in
            KotlinFloat(value: Float((title as NSString).size(withAttributes: [.font: font]).width + font.pointSize * 1.2 + CGFloat(space.sm)))
        }
        return StockActionLayout.shared.sideBySide(labelWidths: widths, availableWidth: Float(actionsWidth), horizontalPadding: Float(space.lg),
                                                   gap: Float(space.sm), accessibilityText: typeSize >= .xxxLarge)
    }

    /// A header action that fills its share of the row.
    private func wide<S: ButtonStyle>(_ title: String, _ icon: String, _ style: S, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { Label(title, systemImage: icon).frame(maxWidth: .infinity) }.buttonStyle(style)
    }

    /// Quote time in exchange (New York) time, e.g. "10:41 AM EDT".
    private static func quoteTime(_ seconds: Int64) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a z · MMM d"
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
            switch model.chart {
            case .loading:
                // Loading keeps the chart's full height (a skeleton of the chart), so it reads differently from the compact notice.
                StockSkeleton(width: 1, height: 1).frame(maxWidth: .infinity).frame(height: CGFloat(dims.chartHeight)).accessibilityLabel("Loading")
            case .content where summary != nil:
                Text(scrubLabel ?? summary?.change ?? " ")
                    .font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(changeColor)
                if let summary { StockLineChart(summary: summary) { scrub = $0 }.frame(height: CGFloat(dims.chartHeight)) }
            default:
                // Phase 4C.3: a compact centered state instead of a chart-sized blank area; no flat or placeholder line is drawn.
                chartNotice
            }
            if let stats = model.overview.value.map({ present.chartStats(overview: $0) }), !stats.isEmpty {
                StockDivider().padding(.top, CGFloat(space.xs))
                let half = (stats.count + 1) / 2
                HStack(alignment: .top, spacing: CGFloat(space.lg)) {
                    VStack(spacing: 0) { ForEach(Array(stats.prefix(half)), id: \.label) { StockInfoLine(label: $0.label, value: $0.value, compact: true) } }
                    VStack(spacing: 0) { ForEach(Array(stats.dropFirst(half)), id: \.label) { StockInfoLine(label: $0.label, value: $0.value, compact: true) } }
                }
            }
        }
        .padding(.horizontal, CGFloat(space.md) - CGFloat(space.sm))
        .stockCard(padding: CGFloat(space.sm))
    }

    /// Failure (connection / temporary) offers Try again; "no history" is an answer, so it doesn't (Phase 4C.2, same as Android).
    private var chartNotice: some View {
        let notice: ChartNotice = ChartPresentation.shared.notice(issue: model.chartIssue ?? ChartIssue.noHistory)
        let retry: (() -> Void)? = notice.canRetry ? { model.retryChart() } : nil
        return StockStateMessage(title: notice.title, message: notice.message, isError: notice.canRetry,
                                 actionTitle: notice.canRetry ? "Try again" : nil, action: retry)
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
                    // Compact pill inside a full 44 pt+ touch target, read as "3 sources" (no arrow).
                    Button { showSources = true } label: {
                        Text(verbatim: "\(why.sources.count) sources →")
                            .font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.primaryText)
                            .padding(.horizontal, CGFloat(space.sm)).padding(.vertical, CGFloat(space.xs))
                            .background(colors.surface, in: Capsule())
                            .frame(minHeight: CGFloat(dims.touchTarget))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(why.sources.count) sources")
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
        titled("At a Glance") { glanceCard(overview, colors) }
        titled(present.understandTitle(overview: overview)) { understand(overview, colors) }
        titled(present.VALUATION_HEALTH_TITLE) { valuationHealth(overview, colors) }
    }

    /// Key metrics in one `StockMetricGrid` (values never truncate; "(YoY)"-style qualifiers move to the supporting line) and the ranges.
    private func glanceCard(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockMetricGrid(items: overview.glance.map { metric in
                let shown = present.glance(metric: metric)
                return StockMetricItem(label: metric.label, value: shown.value, helper: shown.helper.isEmpty ? nil : shown.helper,
                                       direction: metric.direction, action: { education = metric }, hint: "Learn what this means")
            }, maxColumns: 2)
            if overview.yearRange != nil || overview.dayRange != nil {
                StockDivider()
                VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                    if let range = overview.yearRange { StockRangeBar(title: "52-Week Range", range: range) }
                    if let range = overview.dayRange { StockRangeBar(title: "Day Range", range: range) }
                }
            }
        }
        .stockCard()
    }

    /// Facts (assessment), interpretation (insight), then education (About with sector/industry once, Guided Research).
    @ViewBuilder
    private func understand(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        if !overview.assessment.isEmpty {
            VStack(spacing: 0) {
                ForEach(Array(overview.assessment.enumerated()), id: \.offset) { index, row in
                    if index > 0 { StockDivider() }
                    let (icon, container, tint) = assessmentIcon(row.id, colors)
                    StockAssessmentRow(systemName: icon, iconContainer: container, iconContent: tint, title: row.title, detail: row.detail,
                                       badge: row.badge, tone: row.tone, note: row.explanation,
                                       action: row.id == "valuation" ? { education = overview.glance.first { $0.id == "pe" } } : nil)
                }
            }
            .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0)
        }
        if let insight = overview.insight {
            StockInsightCard(title: "Beginner Insight", message: insight, actionTitle: "Why does this matter?", action: { showEvidence = true })
        }
        if overview.about != nil || overview.country != nil || overview.sector != nil { aboutCard(overview, colors) }
        if showResearch { UnderstandStockCard(completed: researchCompleted, action: onResearch) }
    }

    private func aboutCard(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            StockSectionHeader(title: "About \(overview.shortName)",
                               actionTitle: overview.aboutFull == nil ? nil : (aboutExpanded ? "See Less" : "See More"),
                               action: overview.aboutFull == nil ? nil : { aboutExpanded.toggle() })
            if let text = aboutExpanded ? (overview.aboutFull ?? overview.about) : overview.about {
                Text(text).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
            }
            if let country = overview.country {
                Label(country, systemImage: "globe").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
            }
            let classification = present.classification(overview: overview)
            if !classification.isEmpty {
                StockDivider().padding(.vertical, CGFloat(space.xxs))
                ForEach(classification, id: \.label) { StockInfoLine(label: $0.label, value: $0.value, compact: true) }
                if let sector = overview.sector, let industry = overview.industry {
                    Text("\(overview.shortName) is part of the \(sector) sector, in the \(industry) industry. Comparing a company with others in its industry gives its numbers more context.")
                        .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                }
            }
        }
        .stockCard()
    }

    /// Current vs historical P/E (one metric row), the factual headline, lesson and detail links; highlights; key ratios not in At a Glance.
    @ViewBuilder
    private func valuationHealth(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "Valuation")
            if let valuation = overview.valuation {
                let accent = valuation.position == .above ? colors.cautionText : colors.primaryText
                StockMetricGrid(items: [
                    StockMetricItem(label: "Current P/E", value: valuation.currentPe ?? "N/A"),
                    StockMetricItem(label: valuation.historicalLabel, value: valuation.historicalAverage ?? "—"),
                    StockMetricItem(label: "Difference", value: valuation.difference ?? "—")
                ], maxColumns: 3, rowsWhenNarrow: true)
                if let headline = present.valuationHeadline(overview: overview) {
                    Label(headline, systemImage: "tag.fill").font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(accent)
                }
                Text(valuation.explanation).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
            } else {
                StockSectionMessage(message: "Valuation history isn't available for this company.")
            }
            StockDivider()
            StockNavigationRow(title: "Understand P/E Ratio", detail: "What the price-to-earnings ratio tells you", icon: "lightbulb") {
                education = overview.glance.first { $0.id == "pe" }
            }
            StockDivider()
            StockNavigationRow(title: "See detailed valuation", detail: "Historical P/E and how it has changed", icon: "chart.line.uptrend.xyaxis",
                               action: onOpenValuation)
        }
        .padding(.horizontal, CGFloat(space.md)).padding(.vertical, CGFloat(space.sm))
        .stockCard(padding: 0)
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
            .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0)
            if let basis = overview.highlightsBasis {
                Text(basis).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
            }
        }
        let ratios = present.keyRatios(overview: overview)
        if !ratios.isEmpty {
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                StockSectionHeader(title: "Key Ratios")
                VStack(spacing: 0) { ForEach(ratios, id: \.label) { StockInfoLine(label: $0.label, value: $0.value, compact: true) } }
                    .padding(.horizontal, CGFloat(space.md)).padding(.vertical, CGFloat(space.xs)).stockCard(padding: 0)
            }
        }
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
            .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0)
        }
    }

    // MARK: Helpers

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
        .padding(.top, CGFloat(space.sectionGap))
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

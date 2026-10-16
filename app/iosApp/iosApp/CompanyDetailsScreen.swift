import Shared
import SwiftUI

typealias PageSection = CompanyDetailsPresentation.Section

/// Company Details (hierarchy 5D.5.4A, mirrors Compose): identity and price → chart with the day range → actions → pinned section bar
/// (Overview · Financials · News · About, scrolling within the page) → what the company does → At a Glance → Understand → why it moved →
/// valuation & financial health → earnings → news → About → research further. Sections load and fail independently; all are borderless.
struct CompanyDetailsScreen: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    let model: CompanyDetailsModel
    @State private var restoredPosition = ScrollPosition(edge: .top)
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
    /// 5D.5.3: opens a Learn term on top of this stock (Back returns here).
    var onLearnTerm: (String) -> Void = { _ in }
    @State private var education: GlanceMetric?
    @State private var explaining: MetricExplanation?
    @State private var showEvidence = false
    @State private var showSources = false
    @State private var aboutExpanded = false
    @State private var scrub: Int?
    @State private var actionsWidth: CGFloat = 0
    /// Section bar (5D.5.4A): each anchor's top in the scroll view, the pinned bar's frame, the viewport height and whether the page is at its end.
    @State private var anchorTops: [PageSection: CGFloat] = [:]
    @State private var barFrame: CGRect = .zero
    @State private var viewport: CGFloat = 0
    @State private var atEnd = false
    @State private var pageWidth: CGFloat = 0
    /// The content column: the screen minus the side gutters, at most `contentMaxWidth` (nil until measured).
    private var contentWidth: CGFloat? {
        pageWidth > 0 ? min(CGFloat(dims.contentMaxWidth), max(pageWidth - 2 * CGFloat(space.screen), 0)) : nil
    }
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
                        // Phase 5 preview: the AI explanation lives on Earnings Results (StockSteps+, checked by the server there). UI Phase 3: one
                        // action — "Explain With AI" opened the same Earnings Results as View Results, so it is a note under the button now.
                        Button("View Results", action: onLatestResults).buttonStyle(.stockSecondary)
                        HStack(alignment: .top) {
                            Text("Results include AI Earnings Insight: a plain-English explanation built only from verified figures.")
                                .font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.textSupporting)
                            Spacer(minLength: CGFloat(space.xs))
                            PlusBadge()
                        }
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
            .stockCard(bordered: false)
        }
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let overview = model.overview.value
        let sections = present.sections(overview: overview)
        let what = overview.flatMap { present.whatItDoes(overview: $0) }
        ScrollViewReader { proxy in
            ScrollView {
                // A plain VStack (not lazy): every section anchor exists, so the bar can scroll to sections that haven't been shown yet
                // (a LazyVStack hadn't created them and `scrollTo` did nothing). The page is about a dozen sections.
                VStack(alignment: .leading, spacing: 0) {
                    header(colors)
                    chartSection(colors).padding(.top, CGFloat(space.md))
                    if overview != nil { actions.padding(.top, CGFloat(space.md)) }
                    sectionBar(sections, proxy)
                        .opacity(barPinned ? 0 : 1)
                        .accessibilityHidden(barPinned)
                        .onGeometryChange(for: CGRect.self) { $0.frame(in: .named("page")) } action: { barFrame = $0 }
                    Group {
                        anchor(.overview)
                        if let what {
                            whatItDoesRow(what, about: sections.contains(.about) ? { scroll(proxy, to: .about) } : nil, colors).padding(.top, CGFloat(space.md))
                        }
                        if let overview {
                            titled("At a Glance") { glanceCard(overview, colors) }
                            titled(present.understandTitle(overview: overview)) { understand(overview, colors) }
                        }
                        // Why it moved explains the price: after the numbers and what they mean (D-E5).
                        switch model.whyMoving {
                        case .content(let why): if let why { whySection(why, colors).padding(.top, CGFloat(space.sectionGap)) }
                        case .unavailable:
                            titled("Why Did It Move?") {
                                StockSectionMessage(message: "We couldn't load the explanation right now.", actionTitle: "Try again", action: model.loadWhyMoving)
                                    .stockCard(bordered: false)
                            }
                        case .loading: EmptyView()
                        }
                        if let overview {
                            anchor(.financials)
                            titled(present.VALUATION_HEALTH_TITLE) { valuationHealth(overview, colors) }
                        }
                        if let nextEarnings { earningsSection(nextEarnings, colors) }
                        anchor(.news)
                        newsSection(colors).padding(.top, CGFloat(space.sectionGap))
                        if let overview, sections.contains(.about) {
                            anchor(.about)
                            aboutCard(overview, colors).padding(.top, CGFloat(space.sectionGap))
                        }
                        researchSection(hasOverview: overview != nil)
                        Text("Educational information, not a recommendation to buy or sell.")
                            .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
                            .padding(.top, CGFloat(space.lg))
                    }
                }
                // Pinned to the available width, leading-aligned: a plain VStack grows to its widest child, which shifted the whole page sideways
                // at AX5; a child that can't fit now overflows on its own trailing edge, as it did in the LazyVStack.
                .frame(width: contentWidth, alignment: .leading)
                .padding(.horizontal, CGFloat(space.screen))
                .padding(.bottom, CGFloat(space.xl))
                .frame(maxWidth: .infinity)
            }
            .coordinateSpace(name: "page")
            // Pinned copy of the bar once the inline one scrolls under the top edge (same state, same actions).
            .overlay(alignment: .top) {
                if barPinned {
                    sectionBar(sections, proxy)
                        .frame(maxWidth: CGFloat(dims.contentMaxWidth))
                        .padding(.horizontal, CGFloat(space.screen))
                        .frame(maxWidth: .infinity)
                        .background(colors.appBackground)
                }
            }
            .onScrollGeometryChange(for: Bool.self) { geometry in
                geometry.contentOffset.y + geometry.containerSize.height >= geometry.contentSize.height - 2
            } action: { _, end in atEnd = end }
            // 5D.5.6D: keep the reading position in the shared model, so a page the router rebuilds after a reused-top Back returns to it.
            .scrollPosition($restoredPosition)
            // x: how far the page can scroll, y: where it is. A rebuilt page returns once its content is tall enough (no timer).
            .onScrollGeometryChange(for: CGPoint.self) { g in
                CGPoint(x: g.contentSize.height - g.containerSize.height, y: g.contentOffset.y + g.contentInsets.top)
            } action: { _, g in
                if model.restoreScroll {
                    guard g.x >= model.scrollOffset else { return }
                    model.restoreScroll = false
                    if model.scrollOffset > 1 { restoredPosition.scrollTo(y: model.scrollOffset) }
                    return
                }
                model.scrollOffset = g.y
            }
            .onGeometryChange(for: CGSize.self) { $0.size } action: { viewport = $0.height; pageWidth = $0.width }
        }
        .background(colors.appBackground.ignoresSafeArea())
        // KAN-16: the symbol names the next page's back button ("‹ MSFT" on Financials, Valuation and News, as `AppDestination.backTitle`
        // does across tabs) instead of a bare "‹ Back"; the bar itself stays title-free because the header names the company (Learn pattern).
        .navigationTitle(model.symbol)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) { Text(verbatim: " ").accessibilityHidden(true) }
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
            InfoSheet(title: educationTitle(metric), message: educationBody(metric), term: metric.educationId.flatMap { MetricLearnTerms.shared.termFor(metricId: $0) },
                      onLearn: { id in education = nil; onLearnTerm(id) })
        }
        .sheet(item: $explaining) { explanation in
            MetricExplanationSheet(explanation: explanation, onLearn: { id in explaining = nil; onLearnTerm(id) })
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

    /// Compact identity (logo, wrapping name, ticker · exchange) and the price as the focal point: price, signed change with its session
    /// ("Today" or the quote's date), the US market status chip and the quote time. Sector and industry live in "What … does" and About
    /// (5D.5.4A, D-E3); the actions follow the chart.
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
                HStack(alignment: .center, spacing: CGFloat(space.md)) {
                    StockTickerAvatar(symbol: overview.symbol, logoUrl: overview.logoUrl, size: CGFloat(dims.avatar))
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        Text(overview.name).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).foregroundStyle(colors.textTitle)
                            .accessibilityAddTraits(.isHeader)
                        Text(overview.listing).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                    }
                }
                // Price block: the price is the focal point; the session chip sits beside it (never inside the green/red change line);
                // one spoken sentence for price, change, quote time and session.
                let updated = overview.updatedAt.map { Self.quoteTime($0.int64Value) }
                let changeLabel = present.changeLabel(overview: overview)
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    priceText(overview, colors)
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: CGFloat(space.sm)) { priceChange(overview, changeLabel, colors) }
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { priceChange(overview, changeLabel, colors) }
                    }
                    // UI Phase 3: the session chip and the freshness share the secondary line under the change (beside the price the chip read as
                    // the price's own label, e.g. an after-hours price, which the quote never is).
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: CGFloat(space.sm)) { freshness(overview, updated, colors) }
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { freshness(overview, updated, colors) }
                    }
                }
                .padding(.top, CGFloat(space.sm))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel([present.priceDescription(overview: overview, updated: updated, change: changeLabel),
                                     present.statusDescription(status: overview.marketStatus).map { "\($0)." }].compactMap { $0 }.joined(separator: " "))
            }
        }
    }

    @ViewBuilder private func freshness(_ overview: CompanyOverview, _ updated: String?, _ colors: StockColors) -> some View {
        MarketStatusIndicator(status: overview.marketStatus)
        Text(verbatim: present.freshness(updated: updated)).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
    }

    private func priceText(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        Text(overview.price ?? "—").font(StockStepsTheme.font(type.largeNumber, relativeTo: .largeTitle)).foregroundStyle(colors.textValue)
    }

    @ViewBuilder private func priceChange(_ overview: CompanyOverview, _ label: CompanyDetailsPresentation.ChangeLabel, _ colors: StockColors) -> some View {
        StockPriceChange(percentage: [overview.changeAmount, overview.changeAmount == nil ? overview.changePercent : "(\(overview.changePercent))"]
            .compactMap { $0 }.joined(separator: " "), direction: overview.direction, style: type.numberMedium, lineLimit: nil)
        Text(verbatim: label.text).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
    }

    /// Actions after the chart (5D.5.4A, D-E2): Add to portfolio (primary) and Compare side by side; when the pair doesn't fit on one line each
    /// (narrow phones, accessibility sizes) each takes its own full-width row. Practice Buy and Earnings moved to Research further.
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

    /// UI Phase 3: the 5D.5.3 metric sheet for a Highlights or Key Ratios row (null when its metric has no beginner text).
    private func rowExplanation(_ row: InfoRow) -> MetricExplanation? {
        row.metricId.flatMap { MetricLearnTerms.shared.explain(metricId: $0, title: row.label) }
    }

    /// Glance metrics keep their company sentence (InfoSheet); net margin and debt use the 5D.5.3 metric sheet (5D.5.4B).
    private func explain(_ id: String, _ overview: CompanyOverview) {
        if present.GLANCE_SHEET_IDS.contains(id) {
            education = overview.glance.first { $0.educationId == id || $0.id == id }
        } else {
            explaining = MetricLearnTerms.shared.explain(metricId: id, title: nil)
        }
    }

    // MARK: Section bar (5D.5.4A)

    /// The anchor's own id: the bar's tabs already use the section value as their id (inside `StockFadingScrollRow`), and a shared id made
    /// `scrollTo` find the visible tab instead of the section.
    private func anchorID(_ section: PageSection) -> String { "section-anchor-\(section.label)" }

    /// The inline bar has scrolled under the top edge, so the pinned copy shows.
    private var barPinned: Bool { barFrame.height > 0 && barFrame.minY < 0 }

    private func sectionBar(_ sections: [PageSection], _ proxy: ScrollViewProxy) -> some View {
        StockSectionBar(items: sections, selected: selectedSection(sections), label: { $0.label }) { scroll(proxy, to: $0) }
            .padding(.top, CGFloat(space.sm))
    }

    /// A zero-height marker where a section starts: the bar scrolls to it and reads its position (shared `selectedSection` rule).
    private func anchor(_ section: PageSection) -> some View {
        Color.clear.frame(height: 0).id(anchorID(section))
            .onGeometryChange(for: CGFloat.self) { $0.frame(in: .named("page")).minY } action: { anchorTops[section] = $0 }
    }

    /// The last section whose top reached the bottom of the pinned bar; Overview before any did; at the end, the last one on screen.
    private func selectedSection(_ sections: [PageSection]) -> PageSection {
        var tops: [PageSection: KotlinFloat] = [:]
        let barBottom = max(barFrame.maxY, barFrame.height)
        for section in sections { tops[section] = KotlinFloat(value: Float((anchorTops[section] ?? .greatestFiniteMagnitude) - barBottom)) }
        return present.selectedSection(tops: tops, tolerance: 1, atEnd: atEnd, viewport: Float(max(viewport - barBottom, 0)))
    }

    /// Scrolls so the section starts just below the pinned bar (a zero-height anchor lands at the bar's share of the viewport).
    private func scroll(_ proxy: ScrollViewProxy, to section: PageSection) {
        let y = viewport > 0 ? min(max(barFrame.height / viewport, 0), 1) : 0
        withAnimation { proxy.scrollTo(anchorID(section), anchor: UnitPoint(x: 0.5, y: y)) }
    }

    /// "What Apple does": the first sentence of the description and "Sector · Industry"; tapping scrolls to About (D-E3: tags left the header).
    @ViewBuilder
    private func whatItDoesRow(_ what: CompanyDetailsPresentation.WhatItDoes, about: (() -> Void)?, _ colors: StockColors) -> some View {
        let content = HStack(spacing: CGFloat(space.sm)) {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                Text(what.title).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                if let summary = what.summary { Text(summary).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody) }
                if let classification = what.classification {
                    Text(classification).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                }
            }
            Spacer(minLength: 0)
            // At accessibility sizes the chevron would narrow the text until long words break mid-word; the row stays a button (hint below).
            if about != nil && !typeSize.isAccessibilitySize {
                Image(systemName: "chevron.right").foregroundStyle(colors.iconSecondary).accessibilityHidden(true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .multilineTextAlignment(.leading)
        .stockCard(bordered: false)
        if let about {
            Button(action: about) { content.contentShape(Rectangle()) }.buttonStyle(.plain).accessibilityHint("Shows About")
        } else {
            content
        }
    }

    /// Research further (D-E2): Guided Research and Practice Buy together, plus Earnings when the page has no Earnings section.
    @ViewBuilder
    private func researchSection(hasOverview: Bool) -> some View {
        if showResearch || hasOverview || nextEarnings == nil {
            titled(present.RESEARCH_TITLE) {
                if showResearch { UnderstandStockCard(completed: researchCompleted, action: onResearch) }
                if hasOverview {
                    wide("Practice Buy", "graduationcap", .stockSecondary, onPracticeBuy)
                        .accessibilityHint("Opens a simulated order with virtual money")
                }
                if nextEarnings == nil { wide("Earnings", "calendar", .stockSecondary, onEarnings) }
            }
        }
    }

    // MARK: Chart and quote stats

    private func chartSection(_ colors: StockColors) -> some View {
        let chart = model.chart.value
        let summary = chart.flatMap { ChartPresentation.shared.summary(points: $0.points, range: model.range) }
        let scrubLabel = scrub.flatMap { index in chart?.points[safe: index] }.map { ChartPresentation.shared.scrubLabel(point: $0, range: model.range) }
        // UI Phase 3 (U3): line, caption and spoken summary all follow the selected period's own change.
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
                if let summary { StockLineChart(summary: summary, onScrub: { scrub = $0 }, compactAxisAtLargeText: true).frame(height: CGFloat(dims.chartHeight)) }
            default:
                // Phase 4C.3: a compact centered state instead of a chart-sized blank area; no flat or placeholder line is drawn.
                chartNotice
            }
            // 5D.5.4A: label-above-value grid (one column at large sizes) so values never split mid-word; High/Low are the Day Range bar.
            let stats = model.overview.value.map { present.chartStats(overview: $0) } ?? []
            let dayRange = model.overview.value?.dayRange
            if !stats.isEmpty || dayRange != nil { StockDivider().padding(.top, CGFloat(space.xs)) }
            if !stats.isEmpty {
                StockMetricGrid(items: stats.map { StockMetricItem(label: $0.label, value: $0.value, quiet: true) }, maxColumns: 2)
                    .padding(.top, CGFloat(space.xs))
            }
            if let dayRange { StockRangeBar(title: "Day Range", range: dayRange).padding(.vertical, CGFloat(space.xs)) }
        }
        .padding(.horizontal, CGFloat(space.md) - CGFloat(space.sm))
        .stockCard(padding: CGFloat(space.sm), bordered: false)
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
            // 5D.5.4B: title and source pill share a line only while both fit whole; otherwise the pill sits under the title, so at AX5
            // "3 sources" never breaks mid-word.
            ViewThatFits(in: .horizontal) {
                HStack(spacing: CGFloat(space.sm)) {
                    whyTitle(colors).fixedSize()
                    Spacer(minLength: 0)
                    if !why.sources.isEmpty { sourcesPill(why, colors) }
                }
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    whyTitle(colors)
                    if !why.sources.isEmpty { sourcesPill(why, colors) }
                }
            }
            // Observed move (the quote's own change and session), then the summary's first sentence; the rest of the same summary — already
            // hedged ("may have contributed") — under "Possible reasons". Nothing is reworded.
            if let overview = model.overview.value, let move = present.observedMove(overview: overview, label: present.changeLabel(overview: overview)) {
                VStack(alignment: .leading, spacing: 0) {
                    StockPriceChange(percentage: move.change, direction: move.direction, style: type.numberEmphasis, lineLimit: nil)
                    Text(move.session).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                }
                .accessibilityElement(children: .combine)
            }
            let shown = present.whyMoving(summary: why.summary)
            Text(shown.lead).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
            if let rest = shown.rest {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text("Possible reasons").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                    Text(rest).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                }
                .padding(CGFloat(space.sm))
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(colors.surfaceSecondary, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
            }
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
        // 5D.5.4B: a neutral borderless surface instead of the large coloured container.
        .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }

    private func whyTitle(_ colors: StockColors) -> some View {
        HStack(spacing: CGFloat(space.sm)) {
            Image(systemName: "lightbulb.fill").foregroundStyle(colors.educationAccent).accessibilityHidden(true)
            Text("Why Did It Move?").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                .accessibilityAddTraits(.isHeader)
        }
    }

    /// Compact pill inside a full 44 pt+ touch target, read as "3 sources" (no arrow); never wraps.
    private func sourcesPill(_ why: WhyMoving, _ colors: StockColors) -> some View {
        Button { showSources = true } label: {
            Text(verbatim: "\(why.sources.count) sources →")
                .font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.primaryText)
                .lineLimit(1).fixedSize()
                .padding(.horizontal, CGFloat(space.sm)).padding(.vertical, CGFloat(space.xs))
                .background(colors.primaryContainer, in: Capsule())
                .frame(minHeight: CGFloat(dims.touchTarget))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(why.sources.count) sources")
    }

    // MARK: Overview sections

    /// Key metrics in one `StockMetricGrid` (values never truncate; "(YoY)"-style qualifiers move to the supporting line) and the 52-week range.
    private func glanceCard(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockMetricGrid(items: overview.glance.map { metric in
                let shown = present.glance(metric: metric)
                return StockMetricItem(label: metric.label, value: shown.value, helper: shown.helper.isEmpty ? nil : shown.helper,
                                       direction: metric.direction, action: { education = metric }, hint: "Learn what this means",
                                       info: metricHasTerm(metric.educationId))
            }, maxColumns: 2, tiled: true)
            // The day range sits with the chart (5D.5.4A); the 52-week range stays here, on its own surface (5D.5.4B tiles).
            if let range = overview.yearRange {
                StockRangeBar(title: "52-Week Range", range: range).stockCard(bordered: false)
            }
        }
    }

    /// Facts (assessment), then interpretation (insight); About and Guided Research have their own sections further down (5D.5.4A).
    @ViewBuilder
    private func understand(_ overview: CompanyOverview, _ colors: StockColors) -> some View {
        if !overview.assessment.isEmpty {
            VStack(spacing: 0) {
                // 5D.5.4B: Category → Interpretation → Supporting metric → Short explanation; every row opens its metric's explanation.
                ForEach(Array(present.assessment(overview: overview).enumerated()), id: \.offset) { index, row in
                    if index > 0 { StockDivider() }
                    let (icon, container, tint) = assessmentIcon(row.id, colors)
                    StockAssessmentRow(systemName: icon, iconContainer: container, iconContent: tint, title: row.title, detail: row.metric,
                                       badge: row.status, tone: row.tone, note: row.explanation,
                                       action: row.metricId.map { id in { explain(id, overview) } })
                }
                // 5D.5.4B: Beginner Insight inside the same group (no card of its own), compact, with its evidence action.
                if let insight = overview.insight { beginnerInsight(insight, colors).padding(.top, CGFloat(space.xs)).padding(.bottom, CGFloat(space.md)) }
            }
            .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0, bordered: false)
        } else if let insight = overview.insight {
            beginnerInsight(insight, colors)
        }
    }

    /// Beginner Insight (5D.5.4B): a tinted inset with the lightbulb, the existing text (verbatim, compact) and "Why does this matter?".
    private func beginnerInsight(_ insight: String, _ colors: StockColors) -> some View {
        // At accessibility sizes the icon goes above the text so the text keeps the full width (a word split at AX5 beside it).
        let layout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: CGFloat(space.xs)))
            : AnyLayout(HStackLayout(alignment: .top, spacing: CGFloat(space.sm)))
        return Button { showEvidence = true } label: {
            layout {
                Image(systemName: "lightbulb.fill").foregroundStyle(colors.primaryText).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text("Beginner Insight").font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                    Text(insight).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
                    Text("Why does this matter? →").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.primaryText)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .multilineTextAlignment(.leading)
            .padding(CGFloat(space.sm + space.xxs))
            .background(colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Shows where this comes from")
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
        .stockCard(bordered: false)
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
            // 5D.5.4B: "Understand P/E Ratio" removed here; the same P/E sheet opens from the P/E tile and the Understand Valuation row.
            StockDivider()
            StockNavigationRow(title: "See detailed valuation", detail: "Historical P/E and how it has changed", icon: "chart.line.uptrend.xyaxis",
                               action: onOpenValuation)
        }
        .padding(.horizontal, CGFloat(space.md)).padding(.vertical, CGFloat(space.sm))
        .stockCard(padding: 0, bordered: false)
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "Financial Highlights", actionTitle: "See Financials", action: onOpenFinancials)
            VStack(spacing: 0) {
                if overview.highlights.isEmpty {
                    StockSectionMessage(message: "Financial highlights aren't available right now.")
                } else {
                    // 5D.5.4D: one layout for the group (`StockInfoLineGroup`): values and changes stay whole beside their labels, or every
                    // value goes under its label when one can't fit (at AX5 "$416. 2B").
                    // UI Phase 3: a change only where the model has one (margins and free cash flow have none), never a "—" column; rows with an
                    // explanation open it (ⓘ only with a Learn term).
                    StockInfoLineGroup(entries: overview.highlights.map { .init(label: $0.label, value: $0.value, change: $0.change,
                                                                                  info: rowExplanation($0)?.term != nil) }) { stacked in
                        VStack(alignment: .leading, spacing: 0) {
                            ForEach(Array(overview.highlights.enumerated()), id: \.offset) { index, row in
                                if index > 0 { StockDivider() }
                                ExplainableInfoLine(label: row.label, value: row.value, helper: row.helper, change: row.change, direction: row.changeDirection,
                                                    stacked: stacked, explanation: rowExplanation(row)) { explaining = $0 }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0, bordered: false)
            if let basis = overview.highlightsBasis {
                Text(basis).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
            }
        }
        let ratios = present.keyRatios(overview: overview)
        if !ratios.isEmpty {
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                StockSectionHeader(title: "Key Ratios")
                StockInfoLineGroup(entries: ratios.map { .init(label: $0.label, value: $0.value, info: rowExplanation($0)?.term != nil) }, compact: true) { stacked in
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(ratios, id: \.label) { row in
                            ExplainableInfoLine(label: row.label, value: row.value, compact: true, stacked: stacked, explanation: rowExplanation(row)) { explaining = $0 }
                        }
                    }
                }
                    .padding(.horizontal, CGFloat(space.md)).padding(.vertical, CGFloat(space.xs)).stockCard(padding: 0, bordered: false)
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
            .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0, bordered: false)
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
    /// 5D.5.3: the Learn term for a metric explanation ("Learn more about …").
    var term: EducationEntry? = nil
    var onLearn: (String) -> Void = { _ in }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.sm)) {
                    if !message.isEmpty { Text(message).foregroundStyle(colors.textBody) }
                    MetricLearnMoreButton(term: term, onLearn: onLearn)
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


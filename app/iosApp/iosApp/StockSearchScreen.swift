import Shared
import SwiftUI

/// Company search (5D.5.5B/C) in the native search sheet: `.searchable` field with Cancel, then exactly one state from
/// `SearchDisplay.phase` — recent searches (Clear all), a one-line hint, "Searching…", ranked results (Best match / Other results),
/// no results ("Try a different search"), or an error with Retry. Results only ever belong to the current query.
struct StockSearchScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    let state: StockSearchUiState
    @Binding var query: String
    let onRetry: () -> Void
    var onClearRecent: () -> Void = {}
    let onSelect: (StockSearchResult) -> Void
    /// The sheet opens with the search field active (keyboard up), so the first tap isn't lost to the presentation animation (Phase 5C.1).
    @State private var searchActive = false
    private var palette: ThemePalette { StockStepsTheme.palette(colorScheme) }

    var body: some View {
        let phase = SearchDisplay.shared.phase(query: state.query, searching: state.isSearching, failed: state.searchError != nil,
                                               results: state.results, recent: state.recent)
        List {
            if phase == .recent {
                Section {
                    rows(state.recent, best: false)
                } header: {
                    HStack {
                        sectionTitle(SearchDisplay.shared.RECENT_TITLE)
                        Spacer()
                        Button(SearchDisplay.shared.CLEAR_RECENT, action: onClearRecent)
                            .font(StockStepsTheme.font(StockStepsTheme.typography.label, relativeTo: .footnote))
                            .foregroundStyle(StockStepsTheme.color(palette.primaryText))
                            .accessibilityLabel(SearchDisplay.shared.CLEAR_RECENT_SPOKEN)
                            .textCase(nil)
                    }
                }
            } else if phase == .hint {
                Text(SearchDisplay.shared.HINT).font(StockStepsTheme.font(StockStepsTheme.typography.small))
                    .foregroundStyle(StockStepsTheme.color(palette.textSecondary)).listRowBackground(Color.clear).listRowSeparator(.hidden)
            } else if phase == .searching {
                HStack(spacing: CGFloat(StockStepsTheme.spacing.sm)) {
                    Spacer(); ProgressView(); Text(SearchDisplay.shared.SEARCHING).font(StockStepsTheme.font(StockStepsTheme.typography.small))
                        .foregroundStyle(StockStepsTheme.color(palette.textSecondary)); Spacer()
                }
                .padding(.vertical, CGFloat(StockStepsTheme.spacing.xl)).listRowBackground(Color.clear).listRowSeparator(.hidden)
            } else if phase == .error {
                SearchStateView(systemImage: "exclamationmark.circle", title: SearchDisplay.shared.ERROR_TITLE,
                                message: SearchDisplay.shared.ERROR_MESSAGE, actionTitle: SearchDisplay.shared.RETRY, isError: true, action: onRetry)
                    .listRowBackground(Color.clear).listRowSeparator(.hidden)
            } else if phase == .noResults {
                SearchStateView(systemImage: "magnifyingglass", title: SearchDisplay.shared.NO_RESULTS_TITLE,
                                message: SearchDisplay.shared.NO_RESULTS_MESSAGE, actionTitle: SearchDisplay.shared.TRY_DIFFERENT, isError: false) {
                    query = ""; searchActive = true
                }
                .listRowBackground(Color.clear).listRowSeparator(.hidden)
            } else {
                // 5D.5.5B: server-ranked results in "Best match" / "Other results" sections (or one "Results" section); order and contents
                // are the server's.
                ForEach(SearchDisplay.shared.sections(query: state.query, results: state.results), id: \.title) { section in
                    Section { rows(section.results, best: section.best) } header: { sectionTitle(section.title) }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(StockStepsTheme.color(palette.background))
        .tint(StockStepsTheme.color(palette.primary))
        .searchable(text: $query, isPresented: $searchActive, prompt: SearchDisplay.shared.PLACEHOLDER)
        .task { if query.isEmpty { searchActive = true } }
    }

    private func rows(_ list: [StockSearchResult], best: Bool) -> some View {
        ForEach(list, id: \.symbol) { stock in
            SearchResultRow(result: stock, best: best) { onSelect(stock) }.searchListRow(palette)
        }
    }

    private func sectionTitle(_ title: String) -> some View {
        Text(verbatim: title).font(StockStepsTheme.font(StockStepsTheme.typography.cardTitle, relativeTo: .headline))
            .foregroundStyle(StockStepsTheme.color(palette.textPrimary)).textCase(nil)
            .accessibilityAddTraits(.isHeader)
    }
}

extension View {
    /// A borderless search-result list row: page background, a subtle divider starting under the text.
    func searchListRow(_ palette: ThemePalette) -> some View {
        listRowBackground(Color.clear)
            .listRowSeparatorTint(StockStepsTheme.color(palette.borderSubtle))
            .alignmentGuide(.listRowSeparatorLeading) { _ in CGFloat(StockStepsTheme.dimensions.logo + StockStepsTheme.spacing.md) }
    }
}

/// The search screens' no-results and error state (5D.5.5C): icon, title, one sentence and one primary action, announced once.
struct SearchStateView: View {
    @Environment(\.colorScheme) private var scheme
    let systemImage: String
    let title: String
    let message: String
    let actionTitle: String
    let isError: Bool
    let action: () -> Void

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(spacing: CGFloat(StockStepsTheme.spacing.sm)) {
            Image(systemName: systemImage).font(.system(size: 44, weight: .regular))
                .foregroundStyle(isError ? colors.negativeText : colors.textSecondary).accessibilityHidden(true)
            Text(title).font(StockStepsTheme.font(StockStepsTheme.typography.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                .multilineTextAlignment(.center).accessibilityAddTraits(.isHeader)
            Text(message).font(StockStepsTheme.font(StockStepsTheme.typography.small)).foregroundStyle(colors.textSecondary)
                .multilineTextAlignment(.center)
            Button(actionTitle, action: action).buttonStyle(.stockPrimary).padding(.top, CGFloat(StockStepsTheme.spacing.xs))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, CGFloat(StockStepsTheme.spacing.xl))
        .onAppear { AccessibilityNotification.Announcement("\(title). \(message)").post() }
    }
}

# Home UI Refinement Phase 1 (KAN-14) — focused diff

Requested by the owner for this phase. Under `docs/AI_REVIEW_WORKFLOW.md` §3.4 it would be optional: no calculations, auth, navigation,
concurrency, persistence or presenter state change. Excerpts only; full diff: the five files in `CHATGPT_REVIEW_PACKAGE.md` §3.

## 1. Home-local section helpers (Compose) — `presentation/home/HomeComponents.kt`
```kotlin
internal fun HomeSection(title: String, modifier: Modifier = Modifier, actionText: String? = null, onAction: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit
) = Column(modifier, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
    StockSectionHeader(title, actionText = actionText, onActionClick = onAction, trailing = trailing)
    content()
}

internal fun HomeRowGroup(modifier: Modifier = Modifier, inset: Dp = StockStepsTheme.spacing.cardPadding, verticalSpacing: Dp = 0.dp,
                          content: @Composable ColumnScope.() -> Unit) =
    StockCard(modifier, bordered = false, contentPadding = PaddingValues(horizontal = inset, vertical = StockStepsTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing), content = content)
```
Decision: borderless **per call site** through existing `StockCard(bordered = false)`; the global card default and `StockSectionHeader` are
untouched, so no other screen changes. Risk: none outside Home (both helpers are `internal` and only called from `presentation/home`).
SwiftUI mirrors them (`HomeSection`, `.homeRowGroup()` → `.stockCard(padding: 0, bordered: false)`).

## 2. Page rhythm — `HomeScreen.kt` / `HomeScreen.swift`
```kotlin
contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.md, bottom = spacing.xl),
verticalArrangement = Arrangement.spacedBy(spacing.xl),          // was spacing.sectionGap (12)
```
```swift
LazyVStack(alignment: .leading, spacing: CGFloat(space.xl)) {    // was space.sectionGap
… .padding(.top, CGFloat(space.md)).padding(.bottom, CGFloat(space.xl))
```
Decision: existing scale value already used between open sections on Learn and Daily Brief; `ThemeSpacing.sectionGap` itself is unchanged
(it still feeds Portfolio, Practice and others). Risk: 8 dp/pt more between sections; the titles lose
their card padding, so the page length stays similar (see the contact sheets).

## 3. Loading (KAN-36 rules) — first load only
```kotlin
state?.loading != false -> HomeRowGroup { HomeRowsSkeleton(HomeDashboardPresentation.MAX_INDICES) }   // was LinearProgressIndicator
} else if (state.loading) { HomeRowsSkeleton(2) }                                                      // portfolio, was LinearProgressIndicator
```
```swift
// was ProgressView("Restoring your dashboard")
VStack(spacing: 0) { ForEach(0..<3, id: \.self) { _ in StockRowSkeleton() } }
    .accessibilityElement(children: .ignore).accessibilityLabel("Restoring your dashboard").homeRowGroup(inset: 0)
if state.quotesLoading { ProgressView().progressViewStyle(.linear).accessibilityLabel("Updating prices") }  // was a spinner
```
Decision: skeletons only where nothing is shown yet; a price refresh keeps rows visible (thin bar, both platforms). Branch conditions are the
same as before (no state logic changed). Risk: Android skeletons announce the shared "Loading" instead of the specific old labels.

## 4. iOS live Dynamic Type
```swift
@Environment(\.dynamicTypeSize) private var typeSize
… HomeSection(title: "Markets today") { … } content: { … }
    .id(typeSize)      // the session-line ForEach keeps its data on a type change
```
`HomeLearnBanner` and `PracticeEntryCard` read `typeSize` in `body` (`let _ = typeSize`). Decision: the repo's documented iOS rule. Risk: the
Markets section is rebuilt once per Dynamic Type change (no request; `BriefModel` state is read, not reloaded).

## Tests
No shared rule changed (no `*Display`/presenter edits), so no new unit tests; `HomeDashboardPresentationTest` and the full suites pass
(package §5). Visual coverage: the two contact sheets.

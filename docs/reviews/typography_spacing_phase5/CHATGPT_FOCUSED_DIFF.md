# Focused diff — Typography & Spacing Phase 5

§3.4 of `docs/AI_REVIEW_WORKFLOW.md` does not strictly apply (no financial calculation, auth, navigation, concurrency, persistence or
presenter change). This short file is included because the owner's brief asked for it; it shows the hunks a reviewer should check.

## 1. Shared tokens (`app/shared/.../theme/Typography.kt`, `Spacing.kt`)

```kotlin
val sectionHeading = ThemeTextStyle(18, 24, 600)   // section headings (was cardTitle 17)
val listTitle = ThemeTextStyle(16, 22, 600)        // primary list line (was bodySemiBold 15)
val numberCompact = ThemeTextStyle(14, 20, 600, tabular = true) // compact values (was numberLabelStrong 13)

val listRowVertical = md      // 12, search rows (was sm 8)
val listTitleSubtitle = xs    // 4 (was xxs 2)
val sectionBreak = sm         // 8, added above a following section
val logoList = 40             // search/recent logos (logo 32 stays)
```
Risk: new roles only; no existing token value changed, so screens that don't use them are untouched. Covered by `DesignTokensTest`
(`typographyIsAStrictHierarchy`, `typographyLineHeightsAndWeightsAreConsistent`, `phase5SemanticRolesKeepTheReadingOrder`,
`phase5ListRowSpacingStaysOnTheGridAndDense`).

## 2. Search row without the badge (`StockSearchResultRow.kt`; SwiftUI `SearchResultRow.swift` mirrors it)

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(spacing.listTitleSubtitle)) {
    Text(result.name, style = StockStepsTheme.typography.listTitle, color = nameColor)
    ...
}
... .padding(vertical = spacing.listRowVertical)
StockTickerAvatar(result.symbol, logoUrl = result.logoUrl, size = StockStepsTheme.dimensions.logoList)
```
Decision: the "Best match" badge and `SEARCH_BADGE_MAX_SCALE` are removed; `best` still feeds `SearchDisplay.spoken` ("…, best match"), so a
screen reader moving row by row keeps the information. Ranking, sections (`SearchDisplay.sections`) and requests are untouched.

## 3. Android search insets (`StockSearchScreen.kt`)

```kotlin
-            .safeContentPadding()
+            .safeDrawingPadding()
             .padding(ThemeSpacing.screen.dp)
```
Risk: `safeContent` = `safeDrawing` + system-gesture zones. With gesture navigation the gesture zones added ~30 dp on each side. Top, bottom
and keyboard insets are part of `safeDrawing`, so the field and list still clear the status bar, navigation bar and IME (verified with the
keyboard up and down on emulator-5554). Applies only to the search screen; `FeaturePlaceholderScreen` and the legacy `DiscoveryScreen` keep
theirs.

## 4. iOS search list insets (`StockSearchScreen.swift`)

```swift
.listRowInsets(EdgeInsets(top: 0, leading: side, bottom: 0, trailing: side))      // rows own their 12 pt padding
func searchListHeader() -> some View { … .listRowInsets(EdgeInsets(top: sm, leading: side, bottom: xs, trailing: side)) }
```
Risk: Comparison's picker also uses `searchListRow`, so its rows get the same insets (intended, same component). Separator inset follows
`logoList + md`.

## 5. Default two-line company names (`StockRow.kt` / `StockComponents.swift`)

```kotlin
-                    maxLines = nameMaxLines ?: if (LocalDensity.current.fontScale >= dimensions.largeFontScale) 2 else 1,
+                    maxLines = nameMaxLines ?: 2,
```
Affects rows that didn't pass `nameMaxLines` (Top Movers, Home recently viewed and similar); Watchlist and Discover already passed 2. Rows
with short names are unchanged; long names take a second line instead of an ellipsis after ~20 characters.

## 6. Compact values (`StockMetric.kt` `StockInfoRow`, `StockDetailComponents.swift` `StockInfoLine`)

```kotlin
Text(value, style = if (compact) typography.numberCompact else typography.numberMedium, …)
```
Affects every compact info row: Company Details Key Ratios and classification, Financials compact rows. Values stay tabular and whole
(the iOS `StockInfoLineGroup` stacking decision measures the drawn text, so it accounts for the new size).

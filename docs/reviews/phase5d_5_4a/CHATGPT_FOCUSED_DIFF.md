# Focused diff: Phase 5D.5.4A (scroll state and a shared layout rule)

## 1. Section selection (shared, `CompanyDetailsPresentation`)
```kotlin
fun selectedSection(tops: Map<Section, Float>, tolerance: Float, atEnd: Boolean = false, viewport: Float = Float.MAX_VALUE): Section {
    val ordered = Section.entries.filter { it in tops }
    if (atEnd) ordered.lastOrNull { (tops[it] ?: Float.MAX_VALUE) < viewport }?.let { return it }
    return ordered.lastOrNull { (tops[it] ?: Float.MAX_VALUE) <= tolerance } ?: Section.OVERVIEW
}
```
- **Decision:** one rule; each platform only measures each section's top relative to the bar's bottom.
- **Risk:** stale positions for views that aren't laid out.
- **Covered by:** `selectedSectionIsTheLastOneThatReachedTheBar`, plus device checks on both platforms.

## 2. Android scroll (`CompanyDetailsScreen.kt`)
```kotlin
val keys = buildList { add("header"); add("chart"); …; add("sections"); …; add("news"); if (ABOUT in pageSections) add("about"); … }
stickyHeader(key = "sections") { StockSectionBar(…, onSelect = scrollTo, modifier = … .onSizeChanged { barHeight = it.height }) }
val scrollTo = { t -> scope.launch { listState.animateScrollToItem(anchor(t), -barHeight) } }
```
- **Decision:** the item order is a list, so anchors are indices; a negative offset leaves the section below the pinned bar.
- **Risk:** derived selection reads `layoutInfo` (`derivedStateOf`) on every scroll frame.
- **Covered by:** device checks (taps, Back restore).

## 3. iOS scroll (`CompanyDetailsScreen.swift`)
- A `LazyVStack(pinnedViews:)` didn't create off-screen anchors, so `scrollTo` did nothing. The page is now a `VStack` pinned to the
  content width, with an overlay copy of the bar once the inline one scrolls away; the hidden copy is `accessibilityHidden`.
- Anchors use string ids (`"section-anchor-…"`): the tabs inside `StockFadingScrollRow` already used the section value as their id, so
  `scrollTo` was finding the visible tab.
- **Risk:** the non-lazy page creates every section at once (about a dozen).
- **Covered by:** simulator checks at Default and AX5.

## 4. Shared badge rule (`StockBadgeLayout`, owner direction)
- `INLINE_MAX_CHARACTERS` 14 → 20: "More debt than cash" may share the title's line; layouts still stack when they don't fit.
- On iOS a row badge falls back to the compact pill, then to wrapping tinted text, so it never forces the row wider than the screen.
- **Covered by:** `ResponsiveLayoutRulesTest` (updated), device checks at 1.0×/2.0× and Default/AX5.

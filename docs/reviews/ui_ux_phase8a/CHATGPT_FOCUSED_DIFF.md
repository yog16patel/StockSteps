# Phase 8A — focused diff

No financial calculation, auth, navigation, concurrency, persistence or presenter change. The excerpts below are the parts where a
display could mislead if wrong.

## 1. Diverging sector bars (`MarketsScreenPresentation.sectorBar`, shared)

```kotlin
data class SectorBar(val start: Float, val end: Float)

fun sectorBar(row: SectorRowModel): SectorBar {
    val half = row.fraction.coerceIn(0f, 1f) / 2f
    return when (row.direction) {
        PriceDirection.UP -> SectorBar(0.5f, 0.5f + half)
        PriceDirection.DOWN -> SectorBar(0.5f - half, 0.5f)
        else -> SectorBar(0.5f - FLAT_TICK / 2, 0.5f + FLAT_TICK / 2)
    }
}
```

- Decision: draw from a centre line so the side carries the direction (in addition to colour and the text arrow). Length stays the core
  `fraction` (|change| ÷ largest |change| shown, min 0.02) — no new number; only halved because each side has half the track.
- Risk: bars are visually half as long as before; a ±0.13 % move is a short stub next to ±1.43 %. That ratio is the data. Values,
  order, captions and the methodology lesson are unchanged.
- Tests: `MarketsScreenPresentationTest.sectorBarsDivergeAroundTheCentre` (up, down, minimum, unchanged tick, out-of-range clamp).

## 2. Status headline (`MarketsScreenPresentation.statusHeadline`)

```kotlin
fun statusHeadline(header: MarketHeaderModel, session: MarketSession?): String =
    "$STATUS_TITLE · ${session?.let(::statusLabel) ?: header.statusLabel}"
```

- Joins the existing market name and status words; the session line, "Sample quotes · Not live" footer and its spoken data notice are
  unchanged. Test: `statusHeadlineJoinsTheMarketAndItsStatus` (after-hours, weekend, holiday name).

## 3. Home portfolio summary (`HomeDashboardPresentation.portfolioOverview` → `PortfolioOverviewDisplay.of`)

```kotlin
fun portfolioOverview(state: PortfolioUiState): PortfolioOverview =
    PortfolioOverviewDisplay.of(state, state.accounts.count { !it.archived })
```

- Home now shows the Portfolio overview's own strings: total ("USD —" + spoken "Total value unavailable" when the 7C total is null),
  today's change with currency, Invested / Unrealized / Realized **with currency**, the notice with engine codes in words
  ("USD to CAD exchange rate", "AAPL price"). Before, Home showed the raw notice codes and bare numbers.
- No amount is computed: `PortfolioOverviewDisplay` formats the presenter's exact-decimal strings (unchanged).
- Test: `HomeDashboardPresentationTest.portfolioSummaryUsesThePortfolioOverviewWording`.

## 4. iOS `StockRow(stackTrailing:)` (design system, default off)

- When true the trailing values render under the name inside the text column; the sparkline is omitted in that mode. Only Markets
  Top Movers passes it, from `dynamicTypeSize.isAccessibilitySize`. Every other `StockRow` call site is unchanged (default `false`).
- Evidence: iOS `after/ios/ax5-movers.png` vs `before/ios/ax5-markets1.png`.

## 5. Markets surfaces (Compose + SwiftUI)

- `StockCard(...)` / `.stockCard(padding: 0)` per section → `HomeSection` + `HomeRowGroup` / `.homeRowGroup()` (borderless per call
  site). The global `StockCard` default (`bordered = true`) is unchanged.
- The brief entry is rendered inside the indices group when the overview loaded, and in its own borderless group when it didn't, so it
  stays reachable when the overview fails (the Phase 4B rule).

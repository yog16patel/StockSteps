# Phase 8C — focused diff

There are no accounting, valuation, execution, persistence, auth or Firestore changes. These excerpts are the parts that touch financial
display.

## 1. Single-holding donut rule — tried and reverted (owner decision D8C-3)

The change below was implemented, then reverted: it broke `PortfolioPhaseCTest.donutOnlyForValidAllocations`, which encodes a Phase C
decision (one holding at 100 % → donut). It is **not** in the diff. Shown for the decision only:

```kotlin
if (values.any { it < zero }) return false
// Phase 8C: one holding at 100 % would be a solid ring that says nothing; its labelled bar says the same thing plainly.
if (values.size == 1 && values.single() >= Decimal.parse("100")) return false
return values.fold(zero) { sum, v -> sum + v } <= Decimal.parse("100")
```

- **What it would change:** a lone holding at 100 % would show its labelled bar ("TD.TO · CAD 100.00% CAD 1,000.00") instead of a full ring.
- **What doesn't:** a single holding below 100 % keeps the donut, because the cash gap means something. Percentages are the server's. The
  comparison is done in `Decimal`.
- **Test:** the added asserts were reverted with the code.

## 2. Watchlist freshness with a date (`WatchlistPresenter.summary`, core)

```kotlin
data != null -> epochSeconds(data.generatedAt)?.let { "Updated ${MarketsPresenter.dateLabel(it, offset)}, ${MarketsPresenter.timeLabel(it, offset)} ET" }
```

- **What changes:** the header reads "Updated Oct 7, 5:15 PM ET", where it used to read "Updated 5:15 PM ET". The same `generatedAt` and
  exchange offset are used; only the date label is added.
- **Line breaks:** `headerLine` still keeps each part together, using non-breaking spaces.
- **Tests:** `WatchlistPresenterTest.equalWeightedSummaryUsesOnlyFreshSameSessionQuotes` and `headerLineWrapsOnlyBetweenParts` (expected
  strings updated).

## 3. My Portfolio overview order (Compose `PortfolioOverviewCard`, SwiftUI `overviewCard`)

The same elements, in a new order:

| | Order |
|---|---|
| **Before** | account → total → today → divider → **history** → divider → metrics → notice → freshness |
| **After** | account → total → today → divider → **metrics → notice → freshness** → divider → history |

All text still comes from `PortfolioOverviewDisplay` and `PortfolioHistorySection`. History stays on demand: no request is added.

## 4. Practice surfaces

- **Compose:** `StockCard(bordered = false)` at each Practice call site: summary, Metric, insights, holding row, allocation, activity row,
  challenge.
- **SwiftUI:** the `card {}` helper and the row modifiers use `.stockCard(bordered: false)`.
- **Unchanged:** the order flow cards, and the global card default.
- **Copy:** the empty-state text drops the repeated cash amount.
- **iOS buttons:** Explore Stocks and Learn use `.stockSecondary`.

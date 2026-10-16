# Company Details UI Phase 3 — focused diff

Why a focused diff: the phase changes shared/core presentation that decides what numbers and labels mean (`AI_REVIEW_WORKFLOW.md` §3.4),
though no calculation changes. Excerpts only.

## 1. One chart direction (U3) and period wording (core `ChartPresentation.kt`)
```kotlin
fun summary(points: List<PricePoint>, range: ChartRange): ChartSummary? {     // was (…, dayDirection: PriceDirection? = null)
    …
    val percent = if (first > 0) (last - first) / first * 100 else null      // unchanged calculation
    val direction = HomePresentation.direction(percent)                      // was: 1D → the header's daily direction
    val period = period(points, range)                                       // "from 9:30 AM on Oct 7", "since 2021", "past 1M"
    change = "${HomePresentation.percent(percent)} $period"
    description = "Price chart, ${range.label}: ${spokenChange(direction, percent)} $period, from $first to $last."
```
Decision: line colour, caption and spoken summary describe the same thing — the selected period. The header keeps its daily change vs the
previous close; the 1D caption names its own baseline (first point's time) so the two are not read as one measure. `dayDirection` was an
app-internal parameter (two callers, updated); no server/API change. Tests: `chartSummaryLabelsAxesAndChange`,
`chartPeriodWordingNames…`, `oneDayLineCaptionAndSummaryFollowThePeriodWhenTheDailyQuoteDisagrees`,
`longerRangesAgreeAcrossLineCaptionAndSummaryEvenAgainstTheDay` (`CompanyOverviewPresenterTest`).

## 2. Difference precision and metric ids (core `CompanyOverview.kt`)
```kotlin
difference = difference?.let { signedPercent(it) },          // was signedPercent(it, 0): "+2%" vs Valuation "+1.8%"
EvidenceRow("Difference from average", signedPercent(it))
netMargin?.let { InfoRow("Net Margin", percent(it), metricId = "netMargin") }   // + revenue, netIncome, freeCashFlow, ratios
```
Decision: display rounding only; `AssessmentRules.valuation(difference)` still receives the raw double. Metric ids are existing
`MetricEducation` ids. Tests: `valuationDifferenceHasPositionAndHeadline` ("+13.9%"), `highlightAndKeyRatioRowsCarry…`.

## 3. Readable reporting dates (core `FinancialStatementsPresentation.kt`)
```kotlin
fun readableDate(date: String): String  // "2025-09-30" → "Sep 30, 2025"; anything else verbatim (no time zone)
```
Tests: `reportingDatesReadAsCalendarDates…`.

## 4. Freshness line (shared `CompanyDetailsPresentation.kt`)
```kotlin
fun freshness(updated: String?): String = listOfNotNull(updated?.let { "Updated $it" }, DELAYED_NOTE).joinToString(" · ")
fun explains(metricId: String): Boolean = metricId in GLANCE_SHEET_IDS || MetricLearnTerms.explain(metricId) != null
```
Both headers draw `MarketStatusIndicator` + this text on one wrapping line under the change. Tests: `freshnessNames…`, `onlyRowsWith…`,
`priceDescription` expectations.

## 5. Revenue headline once (shared `FinancialsDisplay.kt`)
```kotlin
fun headlineInSection(model: FinancialStatementsModel, sectionId: String): Boolean =
    sectionId != "revenue" || keyMetrics(model).none { it.metricId == "revenue" }
```
Test: `revenueHeadlineIsShownOnceWhenTheKeyMetricsTileShowsIt`.

## 6. iOS compact chart axis (`DesignSystem/StockDetailComponents.swift`)
```swift
private var axisFont: Font {
    compactAxis ? Font(StockStepsTheme.uiFont(type.tiny, typeSize: .accessibility1)) : StockStepsTheme.font(type.tiny, relativeTo: .caption2)
}
```
Decision: the existing AX1 cap didn't apply (`StockStepsTheme.font` reads the app's content-size category). Only Company Details sets
`compactAxisAtLargeText`; other charts unchanged. `compactAxis` reads `dynamicTypeSize`, so live changes refresh. Evidence: AX5 before/after.

## 7. Android inline ⓘ (`designsystem/components/StockMetric.kt`, `StockMetricLabel`)
```kotlin
val text = buildAnnotatedString { append(label); append("\u00A0"); appendInlineContent(INFO_ICON, "ⓘ") }
Text(text, modifier.semantics { contentDescription = label }, style = style, color = color, inlineContent = icon)   // was FlowRow { Text; Icon }
```
Decision: the ⓘ wraps with the last word. Risk: every `StockMetricLabel` consumer (StockMetric, grid rows, StockInfoRow) renders the same way at
normal sizes; spoken label unchanged. Evidence on device BLOCKED (report §8); compile and host tests PASS.

## 8. iOS status chip refresh (`DesignSystem/StockComponents.swift`)
`@Environment(\.dynamicTypeSize)` read in `body` (`let _ = typeSize`), the repo's live Dynamic Type pattern. Evidence: final AX5 frame.

## 9. Not touched
State, presenters' loading, `LaunchedOncePerEntry`, `CompanyModelStore`/reading position, routes, requests, design-system defaults.

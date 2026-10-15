# Focused diff: Phase 5D.5.4B (display mapping and sheet routing)

## 1. Understand rows (shared, `CompanyDetailsPresentation`)
```kotlin
fun assessment(overview: CompanyOverview) = overview.assessment.map { row ->
    AssessmentDisplay(row.id, row.title, row.badge, row.tone, supportingMetric(row), row.explanation, ASSESSMENT_METRICS[row.id]) }
fun supportingMetric(row: AssessmentRow): String {
    val text = if (row.id == "valuation") row.detail.substringBefore(" • ") else row.detail   // P/E vs average stays in the Valuation card
    return text.replace(Regex("""\bYoY\b"""), "year over year") }
private val ASSESSMENT_METRICS = mapOf("growth" to "revenueGrowth", "profitability" to "netMargin", "health" to "debt", "valuation" to "pe")
```
- **Decision:** labels, tones and sentences pass through unchanged; only the fact's wording and the Valuation fact are trimmed.
- **Risk:** the valuation fact depends on core's " • " separator.
- **Covered by:** `understandRowsKeepTheirLabelsAndShowOneSupportingMetric` (order, labels, tones, sentences, facts, ids; every id exists in
  `MetricEducation`).

## 2. Sheet routing (both platforms)
- Ids in `GLANCE_SHEET_IDS` (revenueGrowth, pe) open the existing At a Glance sheet, which includes the company sentence.
- Other ids (netMargin, debt) open the 5D.5.3 `MetricExplanationSheet` via `MetricLearnTerms.explain(id)`.
- The Learn button → `LearnTerm` route → Back works as in 5D.5.3.
- **Covered by:** the device round trip on both platforms.

## 3. Why Did It Move? lead (shared)
```kotlin
fun whyMoving(summary: String): WhyMovingDisplay {
    val lead = firstSentence(summary) ?: summary.trim()
    val rest = summary.trim().removePrefix(lead).trim().ifEmpty { null }
    return WhyMovingDisplay(lead, rest) }
```
- **Covered by:** `whyItMovedLeadIsTheFirstSentenceAndTheRestIsVerbatim` (lead + " " + rest == summary).
- **Fix:** `firstSentence` skipped any token with a dot ("0.24%"); only `^([A-Za-z]\.)+[A-Za-z]$` initialisms are skipped now.

## 4. Row layout option (`StockAssessmentRow`)
- `statusBelowTitle: Boolean = false`: the title, then the status as words in `StockSemanticStyles.badge(kind).content`.
- The default path (pill, `StockBadgeLayout`) is unchanged for other callers.

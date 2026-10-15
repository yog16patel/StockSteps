# Focused diff: Phase 5D.5.3 (navigation and mapping)

Required by `docs/AI_REVIEW_WORKFLOW.md` §3.4 because the phase changes navigation. The excerpts below are shortened.

## 1. Mapping (core, `learning/MetricLearnTerms.kt`)
```kotlin
fun termFor(metricId: String): EducationEntry? {
    BeginnerEducation.entry(metricId)?.let { return it }                                       // exact term id wins
    return BeginnerEducation.entries.filter { metricId in it.relatedMetrics }.singleOrNull()  // one owner, or null
}
```
- **Decision:** link by id only, never by the visible title. A metric with several owners returns null rather than a guess.
- **Risk:** a future `relatedMetrics` edit could silently re-map a metric.
- **Covered by:** `MetricLearnTermsTest` pins the full map of 12 metrics, the no-term list, the `cash` ambiguity and every tagged row id.

## 2. Android navigation (`AppNavigation.kt`)
```kotlin
onLearnTerm = { navController.navigate(LearnTermRoute(it)) }   // Company Details, Financials, Valuation
```
- **Decision:** reuse the existing `LearnTermRoute` on the same back stack, so Back returns to the stock.
- **Risk:** the Learn tab highlights while the term is open; that is approved decision D-C3.
- **Covered by:** device check (P/E → term → Back to the same stock and scroll position; Valuation → term → Back).

Sheet order (`MetricLearnMore`): `onDismiss()` runs before `onLearn(term.id)`, so the sheet is closed before navigation.

## 3. iOS navigation (`CompanyDetailsScene.swift`, Financials/Valuation scenes)
```swift
@State private var learnTerm: String?
.navigationDestination(item: $learnTerm) { id in LearnTermView(termId: id, onSearchCompanies: onSearch) }
// sheet button: { id in education = nil; onLearnTerm(id) }
```
- **Decision:** each scene owns its own destination on the shared `NavigationStack`, and nothing starts in `init`, following the SwiftUI rules in
  `CLAUDE.md`.
- **Risk:** pushing while a sheet is dismissing; the sheet item is cleared first.
- **Covered by:** simulator checks (glance → term → Back; Net Margin → term → Back to Apple Financials; Valuation link → term → Back).

## 4. Row model (core)
- `InfoRow(…, metricId: String? = null)` is optional with a default, so existing call sites are unchanged.
- Tagging the rows only adds ids; no values or labels changed.
- **Covered by:** existing presentation tests (unchanged, passing) plus the tagged-id test above.

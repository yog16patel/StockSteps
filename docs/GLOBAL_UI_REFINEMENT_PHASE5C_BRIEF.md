# Global UI Refinement Phase 5C — Company Comparison: refinement brief (read-only review, not implemented)

Prepared 2026-10-11 during the Phase 5B closure. Nothing in Comparison was changed. Phase 5C starts only after the owner approves it. The goal is to
**polish** the existing Compare screen to the standard of Portfolio / Watchlist / Discover Stocks, not to change what it does.

## 1. Current hierarchy (both platforms; Android `presentation/screener/ComparisonScreen.kt` + `ComparisonHistoryUi.kt`, `ComparisonResearchUi.kt`,
`ComparisonAiUi.kt`, `GuidedMetricExplanation.kt`; iOS `ScreenerScenes.swift` "Compare Companies" section + `ComparisonHistoryViews.swift`,
`ComparisonResearchViews.swift`, `ComparisonAiViews.swift`; order from `comparisonEntries`)
1. Header "Compare Companies" + sentence + in-page sample banner (Android `StockSampleDataBanner`; iOS caution-coloured caption).
2. Selection card "Companies (n of 4)": name, ticker · listing, **Replace** / **Remove** text buttons, "Add a company" (search dialog/sheet), crowded hint.
3. Fewer than two: examples card (three examples + "Discover Stocks"). Otherwise:
4. Loading (progress bar) / error (section message + Try again).
5. Learning summary (insights) → **sticky company header** (symbol, price, "Partial data") → metric sections (label, period, Explain / ⓘ, one value per
   company, "N/A ⓘ" opens why) → "More metrics (n)" → advanced sections.
6. Share price change card (pill selector 1M…5Y, rebased-to-100 chart, per-company change, notes).
7. Historical comparison card (1Y free; 3Y/5Y and advanced metrics StockSteps+, server-enforced; upsell).
8. Research checklist entry card (free + StockSteps+) → AI assistant entry card (StockSteps+).
9. "What the numbers show" observations + "not rankings or recommendations".
10. Sources and notes, prepared time (UTC), education disclaimer.

## 2. Reusable components to apply
`StockCard` (bordered = false groups), `StockRow` / `StockTickerAvatar` (selection rows), `StockNavigationRow` (research / AI / Discover entries as one
"More tools" group, as on Watchlist and Discover Stocks), `StockStatusBadge` (SAMPLE, PREMIUM, "Partial data" as WARNING), `StockTitleWithBadge`,
`StockStateMessage` / `StockErrorState` / `StockEmptyState`, `StockRowSkeleton` / `StockLoadingState`, `StockButton` / `.stockPrimary` / `.stockSecondary`,
`StockPillSelector` (already used), `StockActionLayout` (Replace/Remove pairing), `StockFadingScrollRow`, `ScreenerDisplay.UNAVAILABLE` wording.

## 3. Android / iOS inconsistencies found
- iOS uses system fonts heavily (`.font(.headline/.subheadline/.caption)`): ScreenerScenes.swift Compare part ~55 hits, ComparisonAiViews 43,
  ComparisonResearchViews 39, ComparisonHistoryViews 22 (count includes `.bordered`, `lineLimit(1)`, fixed frames). Android uses theme tokens.
- iOS uses `.buttonStyle(.bordered)` for "More metrics", "Add a company", "Open research checklist" (CLAUDE.md: `.stockSecondary`). Android uses OUTLINED.
- Sample disclosure: Android banner vs iOS caption; Discover Stocks now uses a compact **Sample** badge — align.
- Selection rows: Android shows a logo; iOS doesn't.
- Research entry: Android `ResearchEntryCard`; iOS inline card with system styles.

## 4. Responsive and accessibility risks
- Sticky header and value columns are equal-width (`weight(1f)` / equal frames) for up to 4 companies: at 360 dp × 1.3× or iOS XXXL, four columns leave
  about 70 dp each; values are `maxLines = 2` with ellipsis (Android) — risk of clipped values like "US$1.23T" or "−12.3%" and of "Partial data" truncation.
  Candidate shared rule: values stack per company (label row → one line per company) when columns are too narrow, like `StockQuoteRowLayout`.
- Selection row: name `maxLines = 1` plus two text buttons on one line — at large text the name collapses to a few characters. Candidate: `StockActionLayout`
  or a ⋮ menu (Replace / Remove), keeping 48 dp / 44 pt targets.
- Android `CompanyHeader` columns are clickable without a role; spoken label is only the click label. Metric value row merges into one description
  (`row.accessibility(columns)`) — good; keep it.
- "N/A ⓘ" glyph is part of the text; spoken output should say "unavailable" and the reason action, not "N/A circled i".
- Hard-coded `72.dp` header offset for "scroll to metric" (Android) and fixed frames on iOS.
- Screen readers never run on this screen (like the rest of Global UI Refinement).

## 5. Missing values and financial metrics
- Comparison cells show **"N/A"** (`MetricFormatter.cell`) and the chart "—"; Watchlist and Discover Stocks show **"No data available"**. Decide whether
  Compare adopts the same visible wording (cells are narrow: "No data" + reason sheet may be the right compact form) — presentation only; the
  metric-aware reasons in `MetricFormatter.explanation` must stay.
- Currency markers (`US$`, `C$`), FX notes, period labels ("FY ended Sep 2025"), "None" for no dividend, and turnaround "not meaningful" reasons are
  correct today and must not change.

## 6. Selection and replacement
Selection is shared (`SharedComparisonSelection`) with Discover Stocks (⋮ → Add to comparison, "Comparing" badge, compare bar), Company Details and
Watchlist ("Compare these companies", first three). Keep max 4, the "Compare up to 4" message, Replace-in-place and examples. Polish only.

## 7. History and premium gating
1Y free without an account; 3Y/5Y and advanced metrics StockSteps+, enforced on the server (`EntitlementService`); upsell card with Sign in / Upgrade.
AI assistant and advanced research are StockSteps+ with server-side quotas. **Keep every gate and every request as is**; only restyle the upsell and
locked states (PREMIUM badge, one primary action).

## 8. Guided interpretation and research
Phase 2 inline explanations (Explain / Hide, deeper, related metrics, learning summary) and Phase 4/5 entry points work and are tested
(`ComparisonInterpretationTest`, `ComparisonResearchTest`, `ComparisonAiTest`). Polish typography/spacing and group the entry points; don't change
content, questions, grounding or validation.

## 9. Keep unchanged
Server endpoints, `ComparisonPresenter` contracts, observations engine and wording rules (no rankings/advice), FX conversion, history engine,
entitlement checks, AI validation/quotas, shared selection, navigation (Compare is a pushed destination; five tabs stay).

## 10. Recommended verification matrix
| Check | Android | iOS |
|---|---|---|
| 2, 3 and 4 companies, default text | 360 dp light + dark | iPhone 16 light + dark |
| 4 companies at large text | 360 dp 1.3× | XXXL, compact width (SE) |
| Missing values / partial company / N/A reason | MOCK fixture | MOCK fixture |
| Fewer than two companies (examples) | ✓ | ✓ |
| Loading / error / chart error | ✓ | ✓ |
| History free vs locked (guest, free, StockSteps+ debug plan in MOCK only) | ✓ | ✓ |
| Research and AI entry points (MOCK templates only, no Gemini) | ✓ | ✓ |
| Sticky header while scrolling; "Explain" scroll-to-metric | ✓ | ✓ |
| TalkBack / VoiceOver | NOT RUN unless a person runs them | NOT RUN unless a person runs them |
Tests: shared presentation tests for any new rule (e.g. column stacking) in `ResponsiveLayoutRulesTest`; core tests for any wording change; full
sequential run + `xcodebuild`.

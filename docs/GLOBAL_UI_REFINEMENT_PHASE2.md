# Global UI Refinement — Phase 2: remaining reusable components

Status: **committed** as "Add Global UI Refinement Phase 2: badges, pill selectors, banners, search field and component baseline" (2026-10-10), on top of "Add Global UI Refinement Phase 1A: accessible buttons, wrapping metrics,
states and form controls". Plan step 2 of 7 (`docs/GLOBAL_UI_REFINEMENT_PLAN.md`). Visual direction: `docs/design/stocksteps-ui-reference.png`.
Phase 1A components (primaryAction buttons, `StockMetric`/`StockMetricGrid`, card padding, full states, select fields, keyboard Done,
`StockLabels`) were reviewed and kept as they are — no regressions found in them.

No navigation, presenter, API, repository, calculation, MOCK-fixture or entitlement change. Screen files were touched only to swap in the
standard badge (SIMULATED / Sample) and its title layout (§3.2).

## 1. Visual baseline (iOS only — approved; Android NOT RUN)

Device: spare **iPhone 16 Simulator** (393 pt; the smallest *approved* device — iPhone SE simulators exist but were not approved), iOS 18.3,
Debug build against a **local MOCK server** (`localhost:8081`, `dataMode: mock`), cached throwaway account from Phase 5C.1. Variants: light /
dark × default (Large) / enlarged (`extra-extra-extra-large`, ≈1.35×). Screens: Home, Markets, Company Details (NVDA), Portfolio, Screener
("Growing Companies"). Contact sheets (left→right: light, dark, light-large, dark-large): `docs/design/phase2/before-*.jpg` and
`after-*.jpg`. The simulator's appearance/text size were restored (light, Large), the simulator shut down and the MOCK server stopped.
**Android screenshots: NOT RUN** (not approved; `emulator-5554` untouched) — Compose changes are verified by build/tests only.

Environment artefacts (not app bugs): Portfolio shows "Could not sign in" because the throwaway account cannot refresh its token while the
Firebase Auth emulator is off (offline saved portfolio is shown).

### Findings (before)

| Area | Finding | Component? | Phase 2 |
|---|---|---|---|
| Badge wrapping | Practice "SIMULATED" badge split mid-word ("SIMU-LATED"; "SIMU-LAT-ED" at large text) and squeezed the title to "Practi/ce/Portfol/io" | yes (`SimulatedBadge`, title row) | **fixed** |
| Truncation | Company Details range pills: "3M" and "ALL" became "…" at large text | yes (`StockPillSelector`) | **fixed** |
| Truncation | Screener card metric label "Revenue growth (fisc…" at large text | screen (Screener result card) | Phase 5 (adopt `StockMetricGrid`) |
| Truncation | Company Details action row ends in a partial "E…" button | screen (horizontally scrolling row — intentional) | note only |
| Dynamic Type | Company Details stats table (Open/High/Low) and tags barely scale; "Market / Cap" wraps | screen (Company Details) | Phase 4 |
| Spacing | "Sample scenario: My saved companies" wraps with a hanging indent at large text | screen (Home/Portfolio) | Phase 3/4 |
| Contrast | Nothing below AA observed in the captured screens; static audit found 2 token pairs below AA (§2) | tokens | **fixed** |
| Cards | 16-pt padding/16 radius (Phase 1A) consistent; light cards rely on a hairline border (reference uses elevation) | — | keep |
| Sample banner / tabs | MOCK strip stacked below the tab bar — never covers tab titles (light/dark, both sizes); on pushed screens content ends above it | `SampleDataBanner` | verified, unchanged |
| Navigation | Five tabs visible on every tab root; pushed screens (Discover, Company Details) hide the tab bar as before | — | unchanged |

### After (same screens and variants)

"SIMULATED" stays one word at every size; at default size it sits next to/under the title depending on width, at large text under a
wrapped "Practice / Portfolio" title. Range pills show 1D…ALL at default size and scroll horizontally at large text (no "…"). Other
screens are visually unchanged except: selected chips/pills use the action blue (`0F6FDB`) instead of `0866C6`; neutral tags/rows use the
`textSupporting` role (same colour as before); light-mode negative text is slightly darker (`CA3035`).

## 2. Tokens and shared rules (KMP)

- Light `negativeText` `D13338` → **`CA3035`**: 4.39:1 on `negativeContainer` (negative badges, destructive buttons) was below AA; now
  4.68:1, ≥ 4.72:1 on every light surface. Dark unchanged.
- New `theme/StockSemanticStyles.kt` (shared by Compose and SwiftUI, no new palette):
  - `StockBadgeKind` NEUTRAL · POSITIVE · NEGATIVE · WARNING · INFO · EDUCATION (learn indigo) · PREMIUM (action blue, white) · SAMPLE
    (warm sample-data family, outlined) → `StockSemanticStyles.badge(kind, palette)`;
  - `StockBannerKind` INFO · SUCCESS · WARNING · ERROR · SAMPLE → `banner(kind, palette)` (title `textPrimary`, message `textBody`);
  - `selectedFill` (action blue + white) for chips/pills; `educationText` = `cautionText` for text on the education container
    (`educationAccent` is 2.75:1 there in light mode — used only for icons now);
  - `StockPillLayout.fitsEqualWidth` — equal-width pills only while every measured label fits its share.
- Education colour family **not** changed (still amber container / indigo learn accent) — purple needs your approval.

## 3. Components

### 3.1 Changed / added

| Component | Compose (`designsystem/components/`) | SwiftUI (`DesignSystem/`) | Change |
|---|---|---|---|
| Pill selector | `StockPillSelector` | `StockPillSelector` | measures labels (`TextMeasurer` / `UIFont`) and applies the shared fit rule: equal width, else horizontal scroll at natural width; selected = action blue + semibold; 48dp/pt targets; never "…" |
| Chip | `StockChip` | `StockChip` | selected fill `primaryAction` (was `primaryDark`), semibold selected text, no wrapping/clipping (`softWrap=false` / `fixedSize`) |
| Segmented control | `StockSegmentedControl` | `StockSegmentedControl` | selected label semibold (non-colour cue); iOS labels wrap to two lines instead of shrinking (`minimumScaleFactor` removed) |
| Badges | new `StockStatusBadge(text, kind, size, contentDescription)`, `StockBadgeSize`; `StockBadge(text, tone)` and `toneColors` now delegate (API unchanged) | new `StockStatusBadge` + `StockBadgeSize` (`StockStatusViews.swift`); `StockBadge`/`toneColors` delegate | one line, never split mid-word (iOS `fixedSize`), COMPACT (`tiny`) / REGULAR (`label`), outlined where kind has a border |
| Title + badge | new `StockTitleWithBadge` (FlowRow) | new `StockTitleWithBadge` (ViewThatFits) | badge beside the title when both fit, below it otherwise; title wraps |
| MOCK / simulated | `SimulatedBadge`, Comparison AI "Sample", news "AI" label → `StockStatusBadge` (SAMPLE / INFO) | same (`SimulatedBadge`, Comparison AI "Sample" tag, news "Simplified by AI") | same text and meaning; better legibility |
| Tag | `StockTag` | `StockTag` | `textSupporting`, no wrapping |
| Search | new editable `StockSearchField` (magnifier, placeholder, clear button 48dp, loading indicator, focus border, announced error, Search IME action); `StockSearchEntry` unchanged | new `StockSearchField` (same; `.search` submit, no autocorrect, optional autofocus) | stateless — query, debounce and results stay with the caller; not wired into the Search screens yet |
| Banners | new `StockBanner(kind, message, title, action, onDismiss)` (icon + words, polite live region for WARNING/ERROR, dismiss never for SAMPLE); `StockSampleDataBanner` colours from `StockBannerKind.SAMPLE` (same colour) | new `StockBanner`; `SampleDataBanner` unchanged (already SAMPLE colours, stacked under the tab bar) | |
| Insight / education card | `StockInsightCard`: education eyebrow text `cautionText` (AA), title `textTitle` | `StockInsightCard` unchanged (no eyebrow; accent only on the icon) | |
| Stock row | `StockRow`: non-clickable rows merge semantics (one TalkBack item), name `textSupporting`, price `textValue` | `StockRow`: price/change column keeps natural width (name truncates first, never the price); name `textSupporting`, price `textValue` | |
| News | `StockNewsCard` metadata `textMeta` in both layouts; AI label = INFO badge | same | |

New icons (Compose): `StockIcons.Check`, `Close`. New Swift helper: `StockStepsTheme.uiFont(_:weight:typeSize:)` (measuring only);
`StockColors.palette`. New Compose helper: `StockStepsColors.palette` / `rgb()`.

### 3.2 Screen-file edits (component swaps only)

`presentation/practice/PracticeScreens.kt` (3 title+badge rows, `SimulatedBadge`), `presentation/screener/ComparisonAiUi.kt` ("Sample"),
`PracticeScenes.swift` (4 title+badge rows, `SimulatedBadge`), `ComparisonAiViews.swift` ("Sample").

### 3.3 Audited, unchanged

`StockCard`/`.stockCard`, `StockButton`/`.stockPrimary`, `StockMetric`/`StockMetricGrid`, state messages, select/text fields (Phase 1A);
`StockSearchEntry`; `StockSectionHeader`; skeletons (`StockSkeleton`, `StockRowSkeleton`, news skeleton); `SampleDataBanner` placement.
Insight-card "accessible expansion": no current caller needs expandable content — not added.

## 4. Android / iOS parity

Both platforms read the same badge/banner/selection mapping and the same pill-fit and metric-column rules from KMP. Remaining
differences: Compose pill measurement uses `TextMeasurer` with the current font scale, iOS uses `UIFontMetrics` at the environment's
Dynamic Type size (same rule, platform fonts); Compose search field uses a `Canvas` magnifier, iOS an SF Symbol; iOS segmented labels
wrap at two lines, Compose wraps freely.

## 5. Accessibility

- Contrast (tests): every badge kind's text ≥ 4.5:1 on its container; banner message/title ≥ 4.5:1, icons ≥ 3:1; selected chip/pill
  text 4.86:1; education eyebrow 4.62:1; negative text on its container 4.68:1 — light and dark.
- Selection is never colour-only (semibold selected labels, `isSelected`/selectable semantics); badges and banners carry words and an
  icon; price changes keep arrow + sign + spoken direction.
- 48dp/pt targets: chips, pills, segments, search clear, banner dismiss.
- Large text: pills scroll instead of truncating; badges never split; title+badge rows reflow; row values never truncate on iOS.
- Not done: VoiceOver/TalkBack walkthrough of the changed screens, Android screenshots, physical devices, high-contrast mode.

## 6. Tests and builds (2026-10-10)

- New `SemanticStylesTest` (6): badge contrast and distinctness, banner contrast/icons and SAMPLE fill, selection/education/negative
  contrast, pill fit rule, `FactTone` → badge-kind mapping (keeps `StockBadge(tone)` meaning).
- `./gradlew :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`: **BUILD SUCCESSFUL** —
  shared Android host **75 / 0 failed / 0 skipped**, shared iOS **71 / 0 / 0**, `assembleDebug` OK.
- iOS `xcodebuild … generic/platform=iOS Simulator … build`: **BUILD SUCCEEDED**; no new warnings in changed Swift files.
- Not testable without a UI-test target (none exists): search clear/focus behaviour, row/news spoken output, live layout of pills/badges —
  covered by the simulator before/after screenshots (iOS) only.
- Server/core not run (unchanged).

## 7. Compatibility risks

- Selected chips/pills are a slightly brighter blue on both platforms; light negative text slightly darker everywhere it is used.
- `StockTitleWithBadge` changes where SIMULATED sits on Practice screens (beside → below the title when narrow).
- `StockPillSelector` may scroll on very small widths with long labels (by design); its Compose implementation measures text every
  composition (cheap; ≤ 7 labels).
- Compose `StockRow` non-clickable rows are now one accessibility node (children no longer focusable separately).
- `StockSearchField` and `StockBanner` are not used by screens yet (adoption in later phases).

## 8. Remaining limitations

Android visuals unverified (no emulator run); no UI-test target; Screener metric labels and Company Details stats table still truncate /
under-scale at large text (screen code); education colour family decision; `largeNumber` 34 / `numberEmphasis` 22 still deferred.

## 9. Phase 3 (Portfolio reference screen) — migration guidance

1. Approval for screenshots (iOS spare simulator; Android emulator if approved) before and after.
2. Summary: `StockMetricGrid` for value/cost/unrealized/realized (wrapping, `textValue` values, `textSupporting` labels, `textMeta`
   "Prices as of / Exchange rate as of").
3. Status: "Could not sign in / Offline · last saved portfolio" → `StockBanner` (ERROR / INFO) instead of loose coloured text.
4. Forms: `StockSelectField` + `StockLabels` for account type / currency / transaction kind (fixes "NON REGISTERED", "PERSONAL",
   "OPENING POSITION"); `StockTextField` with Done for amounts.
5. Empty state: full `StockEmptyState`/`StockStateMessage` for "no portfolio"; primary actions `.stockPrimary`/`StockButton`.
6. Lists: holdings as `StockRow`; `contentGap`/`sectionGap` split; sample scenario label as a SAMPLE badge/banner.
7. Then decide `largeNumber` 34 for the Portfolio hero value.

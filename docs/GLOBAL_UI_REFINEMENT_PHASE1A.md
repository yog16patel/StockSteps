# Global UI Refinement — Phase 1A: intermediate component foundation

Status: **committed** as "Add Global UI Refinement Phase 1A: accessible buttons, wrapping metrics, states and form controls" (2026-10-10), on top of "Add Global UI Refinement Phase 1: shared design-system tokens for Android
and iOS". An **intermediate phase between plan step 1 (tokens) and step 2 (reusable components)** of
`docs/GLOBAL_UI_REFINEMENT_PLAN.md`: it delivers the most urgent component items (accessible primary buttons, wrapping metrics, card
padding, empty/error states, form controls); Phase 2 itself is still to come (§7). Visual direction: `docs/design/stocksteps-ui-reference.png` (illustrative —
not a source of data, navigation or exact values). No screen layout, navigation, presenter, API or calculation changed; the only screen-file
edits are the mechanical iOS button-style swap (§3).

## 1. Reference image vs the Phase 1 tokens (reported, not applied)

Matches Phase 1: dark background `06111D`, surfaces `101F30`/`142538`, text `F5F7FB`/`D2DAE4`/`A8B5C5`/`94A3B8`, brand `1683FF`, every type
size except two, the 4-pt spacing scale, chip 8 / button 12 / card 16 / container 20 radii, label-above-value metrics, words + colour for gains.

| Area | Reference | StockSteps tokens | Decision |
|---|---|---|---|
| Light `textTertiary` | `94A3B8` | `5F6F86` | **keep ours** — reference is 2.56:1 on white (fails AA) |
| Light `textSecondary` | `64748B` | `475569` | keep (ours stronger; reference 4.34:1 on `F1F5F9`) |
| Light positive | `16A34A` | `16A86B` fill / `0E7F54` text | keep — reference 3.3:1 as text |
| Light warning | `D97706` | `D99516` / `B45309` text | keep — reference 3.19:1 as text |
| Light negative | `DC2626` | `E5484D` / `D13338` text | keep (equivalent) |
| Dark positive / negative / warning | `22C55E` / `EF4444` / `F59E0B` | `20D98B` / `FF5B57` / `F5B942` | keep — reference negative 4.13:1 on elevated surface |
| Education | purple `8B5CF6` / `7C3AED` | amber `educationAccent`, indigo `learnAccent` | not changed (product decision; dark `8B5CF6` is 3.9:1 as text) — ask before re-theming Learn |
| Light border / surfaceSecondary | `E2E8F0` / `F1F5F9` | `D6E0EA` / `F3F6FA` | negligible |
| `largeNumber` | 34 | 32 | keep until the Portfolio reference screen (Phase 3) is checked at 360 dp |
| `numberEmphasis` | 22 | 18 | keep: screens still put it in `lineLimit(1)`/fixed-width cells (Markets index cards, At a Glance, Valuation); raise when they adopt `StockMetricGrid` (Phase 4) |
| Card padding | 16 | 12 → **16** | **applied** (planned for Phase 2, pulled into Phase 1A) |
| Card gap / section gap | 16 / 24 | `contentGap` 12 / `sectionGap` 12 | Phase 3 (screen lists) |
| Label → value / item gap / form field gap | 6 / 12 / 16 | 4 / 8 / 12 | kept on the 4-pt grid; revisit with screenshots |
| Primary button | bright `1683FF`, white text | — | **changed**: white on `1683FF` is 3.67:1 → new `primaryAction` (§2) |

## 2. Tokens added in Phase 1A

- `ThemePalette.primaryAction` = `0F6FDB` (light and dark): white text 4.86:1 (AA), 3.42:1+ against dark cards and 4.6:1 on the light
  background. Brand `primary` `1683FF` is unchanged for accents, links, charts, progress and selection.
- `ThemeSpacing.cardPadding` 12 → **16** (= `cardPaddingStandard`); new `cardPaddingCompact` = 12 for dense containers.
- `ThemeDimensions.metricMinColumnWidth` = 96, `stateIcon` = 48.
- `StockLayout.metricColumns(count, availableWidth, fontScale, maxColumns)` — one reflow rule for both platforms.
- `designsystem.StockLabels.humanize(identifier)` — readable fallback labels for code identifiers ("OPENING_POSITION" → "Opening position";
  TFSA/RRSP/ETF… stay upper case). Public, so SwiftUI can call `StockLabels.shared.humanize`.

## 3. Components

| Need | Compose (`app/shared/.../designsystem/components/`) | SwiftUI (`app/iosApp/iosApp/DesignSystem/`) |
|---|---|---|
| Accessible primary button | `StockButton` PRIMARY → `primaryAction`; label `bodySemiBold`, centred, wraps | new `StockPrimaryButtonStyle` (`.stockPrimary`) and `StockSecondaryButtonStyle` (`.stockSecondary`) in `StockControls.swift`; `role: .destructive` → tinted negative (as Compose DESTRUCTIVE). **All 30 `.buttonStyle(.borderedProminent)` uses replaced by `.stockPrimary`** (10 screen files, mechanical) |
| Wrapping metric row | `StockMetric` value wraps (no truncation), `labelValueGap`; new `StockMetricItem` + `StockMetricGrid` (adaptive columns) | new `StockMetric`, `StockMetricItem`, `StockMetricGrid` (same rule via `onGeometryChange` + Dynamic Type scale); `StockPriceChange(lineLimit:)` |
| Consistent cards | `StockCard`: 16 radius, 16 padding (token), "no card inside a card" | `.stockCard`: same |
| Scalable badges | `StockTag`/`StockBadge` already `sp`; long text now ellipsizes instead of clipping | `StockTag`/`StockBadge` already Dynamic Type (`StockStepsTheme.font`) |
| Empty / error states | `StockEmptyState(…, title, icon)` and `StockErrorState(…, title, icon)`: with a title → full state (icon tile, heading, explanation, primary / secondary action); without → unchanged compact message (74 existing calls untouched) | new `StockStateMessage` (full); `StockSectionMessage` unchanged for inline messages |
| Form controls | `StockTextField` (label gap token); new `StockSelectField` (field + menu, readable labels, error/supporting, a11y state) | new `StockTextField` (label, prefix, supporting/error, keyboard **Done** for number/decimal pads) and `StockSelectField` (Menu) |

Not adopted by screens yet (Phases 3–7), except the iOS primary-button swap and the Compose components screens already use
(`StockButton`, `StockMetric`, `StockCard`, `StockTag`/`StockBadge`), which inherit the changes.

Fixed-size `.system(size:)` that remain in `StockComponents.swift`/`StockDetailComponents.swift` are inside fixed graphics (ticker-avatar
initials, brand mark, decorative icon tile) where scaling would overflow the shape; they are token-sized and decorative. Badges, chips,
tags and text all scale.

## 4. Visual changes screens inherit

- Every `StockCard`/`.stockCard` and `StockRow` gets 16 instead of 12 padding/inset (slightly narrower content; values in `StockMetric`
  now wrap instead of truncating).
- Android `StockButton` PRIMARY and every iOS primary button: fill `0F6FDB` instead of `1683FF`; semibold label. iOS large buttons are
  44 pt visual / 48 pt touch (the `.controlSize(.large)` modifiers no longer change size). The iOS Practice "Reset practice portfolio"
  destructive button is now the tinted negative style instead of a solid red fill with white text (also below AA).
- Compose `StockMetric` label→value gap 2 → 4.

## 5. Accessibility

- Primary action text 4.86:1 (was 3.67:1); destructive buttons use `negativeText` on `negativeContainer` (AA).
- Metric values wrap; grids drop to one column above 1.3× font scale (Android) / the matching Dynamic Type sizes (iOS).
- Select fields expose label + selected value (Compose `stateDescription`, SwiftUI `accessibilityValue`); errors are announced
  (Compose `error()`, SwiftUI hint). Empty/error titles are headings. 48 dp/pt touch targets kept.
- Not verified: TalkBack/VoiceOver walkthroughs, largest text sizes on screens, physical devices — screens were not opened (no
  simulator/emulator use without approval).

## 6. Tests and builds (2026-10-10)

- `DesignTokensTest` +2 (primary action contrast and edge contrast; metric column rule) and updated card-padding assertion; new
  `StockLabelsTest` (2).
- `./gradlew :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`: **BUILD SUCCESSFUL** —
  shared Android host **69/0**, shared iOS **65/0**, `assembleDebug` OK.
- iOS `xcodebuild … generic/platform=iOS Simulator … build`: **BUILD SUCCEEDED** (no new warnings in the changed files).
- Not run: server/core (not touched).

## 7. Next — Phase 2 (reusable components, remaining)

1. Screenshot baseline: light/dark × default/largest text, both platforms (needs approval for simulator/emulator use) — includes checking
   the inherited 16-pt card padding and the new button colour.
2. Remaining components from the reference's component panel: segmented control and chips/pills, badge variants (incl. a "Sample" badge),
   search field, educational card, alert/info banner (incl. the MOCK sample-data banner), news item, stock row; components start using
   `textSupporting`/`textMeta`.
3. Decide `largeNumber` 34 and the education colour family (§1).

Then Phase 3 — Portfolio reference screen: `StockMetricGrid` for summary values, `StockSelectField` + `StockLabels` for account type /
transaction kind (fixes "PERSONAL", "OPENING POSITION"; the iOS form still shows "NON REGISTERED"), `StockTextField` with Done on decimal
fields, full empty state for "no portfolio", `contentGap`/`sectionGap` split.

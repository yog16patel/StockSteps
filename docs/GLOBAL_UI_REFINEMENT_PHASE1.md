# Global UI Refinement — Phase 1: design-system foundation

Status: **committed** as "Add Global UI Refinement Phase 1: shared design-system tokens for Android and iOS" (2026-10-10), on top of "Close Phase 5C.1 verification: final test matrix, backend check and Global UI
Refinement plan". Plan: `docs/GLOBAL_UI_REFINEMENT_PLAN.md` (step 1 of 7). Scope: shared tokens, the Compose and SwiftUI theme bridges, the
sign-in gradient colours and token tests. No screen, component, navigation, presenter, API or calculation changed.

The single source of truth stays `app/shared/src/commonMain/kotlin/org/example/stocksteps/theme/`. Compose reads it through
`designsystem/theme/StockStepsTheme.kt`; SwiftUI through `app/iosApp/iosApp/Theme.swift` (`ThemeColors.shared`, `ThemeTypography.shared`, …).
No palette, scale or radius is duplicated in Swift.

## 1. Colours (`Colors.kt`)

| Token | Light before → after | Dark before → after | Why |
|---|---|---|---|
| `appBackground` | `F7FAFD` | `07111C` → `06111D` | refined navy target |
| `surface` | `FFFFFF` | `0D1B2A` → `101F30` | cards separate more from the background (1.09 → 1.14:1) |
| `surfaceElevated` / `surfaceSecondary` | `FFFFFF` / `F3F6FA` | `122334` → `142538` | target; stays above `surface` |
| `border` | `D6E0EA` | `243647` → `26384B` | target "subtle border" |
| `borderSubtle` | `E9EEF4` | `192A39` → `1B2D40` | keeps its old 1.19:1 separation on the lighter surface |
| `textPrimary` | `0F172A` | `F5F7FA` → `F5F7FB` | target (not pure white) |
| `textBody` | `334155` | `D4DCE4` → `D2DAE4` | target |
| `textSecondary` | `475569` | `AAB7C4` → `A8B5C5` | target |
| `textTertiary` | `64748B` → **`5F6F86`** | `718091` → **`94A3B8`** | **WCAG AA failures fixed**: light 4.39:1 on `surfaceSecondary`; dark 4.31:1 on `surface`, 3.95:1 on `surfaceElevated`. Now light ≥ 4.58:1, dark ≥ 6.06:1 on every surface |
| `textDisabled` | `94A3B8` | `526171` | unchanged (intentionally < 4.5:1) |
| new `brandGlow` | `6CB8FF` | `6CB8FF` | sign-in gradient stop, was hard-coded |
| new `primaryGradientEnd` | `2F8FFF` | `2F8FFF` | sign-in button gradient end, was hard-coded |
| `primaryBright` (existing) | `2997FF` | `2997FF` | now exposed to Compose/SwiftUI (sign-in logo tile) |

Unchanged: primary/brand `1683FF`, positive, negative, warning, caution, education, learn, logo, icon colours (all text variants pass AA on
every surface — checked by the new test).

**Text roles** (aliases onto the ramp, not a new palette) on `ThemePalette`, Compose `StockStepsColors` and SwiftUI `StockColors`:
`textTitle` / `textValue` → `textPrimary` (page titles, major values), `textBody` (explanations), `textSupporting` → `textSecondary`
(labels, descriptions, units), `textMeta` → `textTertiary` (timestamps, sources, optional guidance), `textDisabled`. Existing
`textPrimary` usages were **not** recoloured — that is per-screen work in later phases.

## 2. Typography (`Typography.kt`)

| Style | Before (size/line/weight) | After | Notes |
|---|---|---|---|
| `display` | 32/38/700 | 36/44/700 | 1 use |
| `largeNumber` | 26/32/700 tnum | **32**/40/700 tnum | target 34; 32 keeps "CAD 12,345.67"-style heroes on one line on 360-dp phones |
| `screenTitle` | 24/30/700 | 28/34/700 | |
| `sectionTitle` | 18/24/600 | 20/26/600 | |
| `cardTitle` | 16/21/600 | 17/22/600 | |
| `body` / `bodyMedium` / `bodySemiBold` | 14/20 (400/500/600) | 15/21 (400/500/600) | 1.4× line height |
| `small` | 13/18/400 | 14/20/400 | |
| `label` | 12/16/500 | 13/18/500 | |
| `caption` | 11/15/400 | 12/16/400 | |
| `tiny` | 10/13/500 | 11/14/500 | kept Medium (badges/pills); nothing is below 11 now |
| `numberEmphasis` | 16/21/600 tnum (= cardTitle) | **18**/24/600 tnum | target 22 deferred: used in single-line metric grids that already truncate (report §14) — moves with the wrapping metric component in Phase 2 |
| `numberMedium` | 14/20/500 tnum | 17/22/600 tnum | |
| `numberLabel` | 12/16/500 tnum | 13/18/400 tnum | |
| `numberLabelStrong` | 12/16/600 tnum | 13/18/500 tnum | |

Fonts stay the platform system fonts; financial styles keep tabular digits (Compose `tnum`, SwiftUI `monospacedDigit`). Compose sizes are
`sp` (Android font scale applies); no token sets a fixed height.

**iOS Dynamic Type** (`Theme.swift`): `StockStepsTheme.font(_:relativeTo:)` now defaults to the iOS text style nearest the token size
(`dynamicTypeStyle(for:)`, nearest default size, ties to the smaller style: 11 → caption2, 12 → caption1, 13/14 → footnote,
15 → subheadline, 17/18 → body, 20 → title3, 28 → title1, 32/36 → largeTitle) instead of always `.body`; explicit `relativeTo:` arguments are honoured as before. New
`StockStepsTheme.lineSpacing(_:)` and `View.stockFont(_:)` apply the token line height (SwiftUI fonts carry none), scaled with Dynamic Type.
No new `.system(size:)` was added; the existing fixed sizes in `DesignSystem/StockComponents.swift` / `StockDetailComponents.swift` are Phase 2.

## 3. Spacing (`ThemeSpacing`)

Scale unchanged: `xxs 2 · xs 4 · sm 8 · md 12 · lg 16 · xl 20 · xxl 24 · xxxl 32`.

| Semantic token | Value | Status |
|---|---:|---|
| `screen` | 16 | unchanged |
| `cardPadding` | 12 | **unchanged** (default of `StockCard`, `.stockCard`, `StockRow` inset; 30 uses). Raising it to 16 narrows every card/row before their content wraps → **moved to 16 in Phase 1A** |
| `cardPaddingStandard` | 16 | new (target standard card padding) |
| `cardPaddingSpacious` | 20 | new |
| `educationalCardPadding` | 16 | unchanged |
| `contentGap` | 12 | new — gap between cards |
| `sectionGap` | 12 | **unchanged**: in practice it is the card gap on Home, Portfolio, Portfolio Insights, Practice, Daily Brief (10 uses). Phase 3 moves those to `contentGap`, then `sectionGap` becomes 24 (`xxl`) |
| `itemGap` | 8 | new — items inside a card |
| `labelValueGap` | 4 | new |
| `formFieldGap` | 12 | new |
| `related` / `iconText` / `titleSubtitle` | 8 / 8 / 2 | existing, now documented (heading→description, icon→label, row title→subtitle) |

Compose `StockStepsTheme.spacing` exposes the new tokens; SwiftUI reads them from `ThemeSpacing.shared`.

**Legacy aliases**: `space6`, `space10`, `space14`, `space30`, `space40` have **no users** on either platform → marked `@Deprecated` (values
unchanged; remove in a later cleanup). `tiny`/`small`/`medium`/`large`/`extraLarge` still have ~80 uses (equal to `xs/sm/md/lg/xxl`) → kept,
migrate when their screens are refined.

## 4. Corners (`ThemeCorners`)

| Token | Before | After |
|---|---:|---:|
| `chip` | 8 | 8 |
| `button` | 10 | **12** |
| `card` | 12 | **16** |
| `cardLarge` (large / promotional container) | 16 | **20** |
| legacy `small` / `medium` / `large` | 8 / 12 / 20 | 8 / 16 / 20 (`large` = `cardLarge`) |

Both platforms read the same values (Compose `shapes`, Material `Shapes`, SwiftUI `corners`). Radius only — no layout change. Auth keeps
its own `AuthTokens` radii (14/24) until Phase 7.

## 5. Dimensions and adaptive layout

`touchTarget` stays 48; `StockButton` already applies `minimumInteractiveComponentSize()` around its 44-dp visual height.
`FinancialsTokens.twoColumnMinWidth`/`largeTextScale` duplicated `ThemeDimensions.multiColumnMinWidth`/`largeFontScale` (300 / 1.3) → now
reference them (same values). `AdaptiveLayoutConstants` breakpoints unchanged (no evidence to move them).

## 6. Sign-in colour migration

Compose `presentation/account/AuthComponents.kt` and SwiftUI `AuthComponents.swift`: logo tile gradient `2997FF` → `primaryBright`; hero curve
and arrow `6CB8FF` → `brandGlow`; primary button gradient end `2F8FFF` → `primaryGradientEnd`; white text/icon/spinner on the brand fill →
`onPrimary` (`FFFFFF` in both modes). **Appearance unchanged** (same RGB; test pins the values). Kept on purpose: the Google button's white
fill, `1F1F1F` text, `5F6368` chevron and the four Google "G" colours (Google branding). The raw `sp` overrides in the Compose auth file
remain (Phase 7, with the auth screens).

## 7. Visual changes existing screens inherit

- All token text is 1–4 sp/pt larger (body 14 → 15, captions 11 → 12, badges 10 → 11, titles and hero values larger). Expect a little more
  wrapping, and more truncation where screens force one line (`maxLines = 1` / `lineLimit(1)`: Screener, Markets, At a Glance, Earnings).
- `numberLabel`/`numberLabelStrong` are one weight lighter; `numberMedium` is larger and semibold.
- Rounder cards (16), buttons (12) and large containers (20).
- Dark mode: slightly lighter cards/borders; metadata (`textTertiary`) is clearly brighter. Light mode: metadata slightly darker.
- iOS: tokens used without `relativeTo:` scale along their nearest text style instead of `.body` at large Dynamic Type sizes.
- Not inherited: spacing (all semantic values that screens use kept their numbers), navigation, data, sign-in appearance.
- Screens were **not** visually reviewed in this phase (no simulator/emulator use without approval): a light/dark, default/largest-text
  screenshot pass is the first task of Phase 2.

## 8. Accessibility

- Every readable text role (`textPrimary/Body/Secondary/Tertiary`, `primaryText`, `positiveText`, `negativeText`, `cautionText`) ≥ 4.5:1 on
  every background/surface in both modes (test). Not covered: text on `surfaceSelected` and container colours, charts, images.
- Minimum token size 11; body line height 1.4×; no fixed heights; Android font scale and iOS Dynamic Type supported by the tokens.
- Gain/loss words are untouched (components still pair colour with words/signs).
- **Known issue, not changed (brand decision)**: white on `primary` `1683FF` is 3.67:1 — below AA for the 15-sp primary button label
  (passes the 3:1 large/bold threshold only for ≥ 18.66 px bold). **Resolved in Phase 1A** with a new `primaryAction` `0F6FDB` (4.86:1); options considered: fill primary buttons with `primaryDark` `0866C6`
  (5.64:1) or use a bolder/larger label.
- Token tests are **not** an accessibility pass: TalkBack/VoiceOver, largest text sizes, high-contrast and small/large devices remain manual
  checks.

## 9. Tests and builds (2026-10-10)

- New `app/shared/src/commonTest/kotlin/org/example/stocksteps/theme/DesignTokensTest.kt` (10 tests: AA contrast per role/surface, ramp
  order, role aliases, dark surface separation, auth gradient values, type hierarchy, line heights/weights/tabular digits, spacing grid and
  semantic values, legacy aliases, corners/dimensions) — runs on Android host and iOS.
- `./gradlew :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`: **BUILD SUCCESSFUL** —
  shared Android host **65/0** (55 + 10), shared iOS **61/0** (51 + 10), `assembleDebug` OK.
- iOS `xcodebuild … -destination 'generic/platform=iOS Simulator' CODE_SIGN_IDENTITY=- CODE_SIGNING_ALLOWED=YES build`: **BUILD SUCCEEDED**.
- Not run (unchanged modules): `:server:test`, `:core:jvmTest`, `:core:iosSimulatorArm64Test` — Phase 1 touches only `app/shared` and the iOS
  app. (The Phase 5C.1 core iOS re-run is closed: 429/0, recorded in the 5C report §15.)

## 10. Phase 2 (reusable components) — recommendations

(Items 2–4 partly and 5–6 were delivered early in the intermediate **Phase 1A** — `docs/GLOBAL_UI_REFINEMENT_PHASE1A.md`.)

1. Screenshot baseline (light/dark × default/largest text, both platforms) of the reference screens; record regressions from §7.
2. `StockMetric`/key–value row that wraps instead of truncating → then `numberEmphasis` 18 → 22.
3. `StockCard`/`.stockCard`/`StockRow`: switch `cardPadding` to `cardPaddingStandard` (16) once content wraps; one card container.
4. Badges/pills (`StockTag`, `StockBadge`, `StockChip`) on `tiny`/`label` via `StockStepsTheme.font`/`stockFont` — remove `.system(size:)` in
   `DesignSystem/StockComponents.swift` and `StockDetailComponents.swift`.
5. Empty/error state v2 (icon, title, explanation, action); chart error copy for offline.
6. Primary button contrast decision (§8).
7. Start applying `textSupporting`/`textMeta` inside components (not screens).

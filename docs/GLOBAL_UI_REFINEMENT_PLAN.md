# Global UI Refinement — plan (read-only; no UI implemented yet)

Status: **approved** (2026-10-10). Step 1 (tokens) committed — `docs/GLOBAL_UI_REFINEMENT_PHASE1.md`; intermediate Phase 1A (accessible
buttons, wrapping metrics, card padding, empty/error states, form controls) committed — `docs/GLOBAL_UI_REFINEMENT_PHASE1A.md`;
step 2 (remaining reusable components) and steps 3–7 not started. Visual direction: `docs/design/stocksteps-ui-reference.png`.
Original proposal (2026-10-09, after Phase 5C.1) below. Evidence comes from the
Phase 5C/5C.1 walkthroughs (`docs/PHASE5C_MOBILE_INTEGRATION_TEST_REPORT.md` §3, §14) and a static scan of the code at `fc3961e`.

Goal: calmer, more readable screens for beginners (NUMBER → CONTEXT → EXPLANATION → EDUCATION) on both platforms, with no change to data,
presenters, navigation boundaries or features. Rules that stay: theme tokens only, 48dp/pt touch targets, gain/loss in words not colour alone,
five bottom tabs, sign-in screens on `StockStepsTheme`.

## 1. Inventory — what already exists

### Tokens (single source, shared by both platforms)
`app/shared/src/commonMain/kotlin/org/example/stocksteps/theme/` — `Colors.kt`, `Typography.kt`, `Spacing.kt` (`ThemeSpacing`, `ThemeCorners`,
`ThemeDimensions`), `AuthTokens.kt` (auth sizes only), `FinancialsTokens.kt`, `AdaptiveLayoutConstants.kt`. iOS reads the same objects
(`ThemeSpacing.shared`, `ThemeTypography.shared`, …) through `app/iosApp/iosApp/Theme.swift` (`StockStepsTheme.palette/color/font`).

- **Colours** (`StockStepsColors`, light + dark): surfaces (`appBackground`, `surface`, `surfaceElevated`, `surfaceSecondary`, `surfaceSelected`),
  borders, `primary*`, `positive*/negative*/warning*/caution*` (container/border/text), text ramp `textPrimary → textBody → textSecondary →
  textTertiary → textDisabled`, `education*`, `learn*`.
- **Typography** (16 styles): `display, largeNumber, screenTitle, sectionTitle, cardTitle, body, bodyMedium, bodySemiBold, small, label, caption,
  tiny, numberEmphasis, numberMedium, numberLabel, numberLabelStrong`.
- **Spacing**: `xxs 2 · xs 4 · sm 8 · md 12 · lg 16 · xl 20 · xxl 24 · xxxl 32`, semantic `screen, cardPadding, educationalCardPadding, sectionGap`
  — plus legacy aliases (`space6`, `space10`, `space14`, `space30`, `space40`, `small/medium/large/extraLarge`) that duplicate the scale.
- **Corners** `chip 8 · button 10 · card 12 · cardLarge 16 · large 20`; **dimensions** (touch target 48, row heights 52/56, icons, chart 160,
  `largeFontScale 1.3`, …).

### Components
- **Compose** (`app/shared/.../designsystem/components/`): `StockCard`, `StockButton`, `StockRow`, `StockMetric`, `StockSectionHeader`, `StockChip`,
  `StockTag`, `StockSegmentedControl`, `StockSearchBar`, `StockTextField`, `StockSettingsRow`, `StockInsightCard`, `StockAssessmentRow`,
  `StockPriceChange`, `StockRangeBar`, `StockSparkline`, `StockLineChart`, `StockTrendChart`, `StockBarChart`, `StockNewsCard`, `StockStates`
  (`StockSkeleton`, `StockLoadingState`, `StockEmptyState`, `StockErrorState`), `StockSampleDataBanner`, `StockBrandMark`, `StockAccountAvatar`,
  `StockDivider`, `StockBottomNavigation`, `MarketStatusIndicator`; `presentation/components/StockStepsTopBar.kt`; `designsystem/icons/StockIcons`.
- **SwiftUI** (`app/iosApp/iosApp/DesignSystem/` + root): `StockCardModifier` (`.stockCard`), `StockRow`/`StockRowValues`/`StockRowSkeleton`,
  `StockSectionHeader`, `StockSectionMessage`, `StockChip`, `StockTag`, `StockBadge`, `StockPillSelector`, `StockSegmentedControl`,
  `StockInsightCard`, `StockAssessmentRow`, `StockInfoLine`, `StockIconTile`, `StockPriceChange`, `StockRangeBar`, `StockSparkline`,
  `StockLineChart`, `StockTrendChart`, `StockBarChart`, `StockComparisonBars`, `StockNewsCard`(+`Skeleton`), `StockSkeleton`, `StockSettingsRow`,
  `StockTickerAvatar`, `StockAccountAvatar`, `StockBrandMark`, `MarketStatusIndicator`, `StockSearchEntry`; `GlassModifiers.swift`,
  `StockStepsTopBar.swift`, `SampleDataBanner` (in `ContentView.swift`).

Gaps: no shared **empty-state** component with icon/explanation/action (Compose `StockEmptyState` and iOS `StockSectionMessage` are a single line
of text); no **key–value metric row** that wraps instead of truncating; no **form picker** that shows human labels for enums; no shared
**inline status/badge** sizing that scales.

## 2. Findings by issue (evidence → affected screens)

| Issue | Evidence | Most affected screens |
|---|---|---|
| Congested layouts | Many stacked cards with uniform `cardPadding 12` and `sectionGap 12`; dense metric grids | Company Details (At a Glance), Earnings event/results, Screener results, Comparison, Practice order |
| Weak typography hierarchy | iOS screens mostly use SwiftUI system styles instead of `StockStepsTheme.font`: `EarningsScenes` 111, `ScreenerScenes` 73, `ComparisonAiViews` 40, `EarningsPremiumViews` 40, `ComparisonResearchViews` 35, `DailyBriefScenes` 26, `PortfolioScreen` 23, `PracticeScenes` 23 — so the two platforms and screens disagree on sizes and weights | Earnings, Screener/Comparison, Daily Brief, Portfolio, Practice (iOS) |
| Inconsistent spacing | Legacy spacing aliases off the 4-pt scale (`space6/10/14/30/40`); raw `dp` values in `account` (44), `portfolio` (5), `companydetail` (3) | Auth, Portfolio, Company Details |
| Excessive white text | In dark mode `textPrimary` is used for supporting copy: Compose `earnings` 83 primary vs 63 muted, `companydetails` 52 vs 40, `watchlist` 28 vs 14; iOS `EarningsPremiumViews` 19 vs 9, `CompanyDetailsScreen` 20 vs 17 | Earnings, Company Details, Watchlist |
| Missing muted secondary text | Same counts: labels, units, dates and source lines share the primary colour; `textTertiary` rarely used | Same + Markets rows |
| Text truncation | `maxLines = 1` / `lineLimit(1)` hot spots: Compose `screener` 14, `markets` 7, `earnings` 4; iOS `StockComponents` 8, `MarketsScene` 7, `ScreenerScenes` 6, `StockDetailComponents` 6. Report §14: "+14.9 % (Y…" in At a Glance, "SIMULAT-ED" badge wrap | Company Details, Screener, Markets, Practice |
| Inconsistent cards | Mixed `StockCard`/`.stockCard`, glass modifiers and ad-hoc backgrounds; corner radii 12/16/20 used interchangeably | Home, Markets, Learn, Daily Brief |
| Poor empty states | One-line messages; chart error copy says "isn't available for this range" when offline (report §14) | Watchlist (no lists), Portfolio (no holdings), Alerts, Screener (no results), Comparison history, Earnings calendar, Company charts |
| Fixed-size labels | iOS `.system(size:)` in `StockComponents` (badges, `tiny`), `StockDetailComponents`; Compose `AuthComponents.kt` overrides sizes with raw `sp` (13/14/15/17/30); report §14: captions, pills and the sample banner do not follow Dynamic Type | Badges/pills everywhere, sample banner, auth |
| Accessibility scaling | Fixed frames (`frame(width:/height:)`) in `EarningsScenes` 5, `ScreenerScenes` 4, `StockDetailComponents` 4, `GuidedResearchScenes` 4; Android brief card needed a 1.3× fix (#19); no VoiceOver/TalkBack pass yet | Earnings, Screener, Guided Research, Daily Brief |
| Copy/labels | Raw enum labels "PERSONAL", "OPENING POSITION" in Portfolio forms; no Done on iOS decimal keypads | Portfolio forms, Practice order |
| Theme rule drift | `presentation/account/AuthComponents.kt` hard-codes brand blues (`0xFF2997FF`, `0xFF6CB8FF`, `0xFF2F8FFF`) and white text — conflicts with "sign-in screens use `StockStepsTheme`" (Google button colours are brand-mandated and may stay) | Sign-in / create account |

## 3. Implementation order (each step its own reviewable change, both platforms together)

1. **Global design tokens** — collapse spacing aliases onto the 4-pt scale; define text-colour roles (title / value / supporting / meta) mapped
   to the existing ramp; add a "badge/caption" style that scales; one card radius + one large radius; move the auth gradient colours into
   `Colors.kt`. iOS: route every text through `StockStepsTheme.font(_:relativeTo:)`.
2. **Reusable components** — `StockEmptyState` v2 (icon, title, one-line explanation, optional action) on both platforms; wrapping
   `StockMetric`/key–value row; scalable `StockTag`/`StockBadge`; enum-label picker; keyboard "Done" accessory for decimal fields; one card
   container (retire ad-hoc backgrounds).
3. **Portfolio reference screen** — apply everything to Portfolio (+ Insights, forms) and use it as the visual reference; screenshots light/dark,
   default and largest text size, both platforms.
4. **Home, Markets and Company Details**.
5. **Watchlist, Screener and Comparison**.
6. **Learn, Daily Brief and Earnings** (largest typography debt on iOS).
7. **Settings, authentication and remaining screens** (Practice, Guided Research, search, onboarding).

Per step: no data/presenter changes; existing tests stay green (`:app:shared:testAndroidHostTest`, `iosSimulatorArm64Test`, `xcodebuild`);
before/after screenshots at default and largest text size, light and dark; TalkBack/VoiceOver spot check of the changed screens.

## 4. Out of scope
New features, navigation changes, a sixth tab, MOCK fixture refresh (#7/#8), Firebase persistence (Phase 5D), Cloud Run.

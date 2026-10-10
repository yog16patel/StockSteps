# Global UI Refinement Phase 4C — Company Details

Status: **implemented, verified, committed and pushed** together with Phase 4C.1 as "Refine Company Details for beginners with chart scroll fix and accessible price summary (Global UI Refinement Phases 4C and 4C.1)" (2026-10-10), on top of `4eb9e5b` (Phase 4B.1). UI-only: no API contracts,
endpoints, presenter logic, financial calculations, metric definitions, navigation destinations, entitlements, Firebase, provider requests or
MOCK fixtures changed. Reference: `docs/design/phase4c/stocksteps-company-details-reference.webp` (visual guidance only; no values copied).
**Correction (Phase 4C.1):** the iOS app reads MOCK data from the LAN MOCK backend configured in its Info.plist, not the local server — see
`docs/GLOBAL_UI_REFINEMENT_PHASE4C_1.md` §0. Device use approved by the user: local MOCK server for Android (stopped afterwards), iPhone 16 Simulator, emulator-5554 (signed-in account only viewed),
and a temporary iPhone SE (3rd gen, iOS 18.3) simulator created for the narrow check and deleted afterwards (guest mode, backend preset to MOCK).

## 1. Before/after issue inventory

| # | Before (both platforms unless noted) | After |
|---|---|---|
| C1 | Order mixed purposes: header → chart → Understand-This-Stock promo → Earnings (long card) → Why it moved → At a Glance → How does it look → Insight → About → Highlights → Valuation → Ranges → Key ratios → Sector & Industry → News | Beginner order: **identity & price → chart → why it moved → At a Glance (+ ranges) → Understand <company> (assessment, insight, About, Guided Research) → Valuation & financial health (valuation, highlights, key ratios) → Earnings → News** |
| C2 | Action row ("Add to portfolio / Compare / Earnings / Practice Bu…") was a sideways-scrolling row of text buttons above the company name; "Practice Buy" clipped | Secondary buttons under the price that **wrap** (Add to portfolio, Compare, Practice Buy); "Earnings" is shown only when the page has no Earnings section (it duplicated "Earnings history") |
| C3 | Market status: read-only **switch with a red track when closed** (closed read as a loss) | Status chip with Phase 4B.1 semantics: green dot open, amber pre-market/after-hours, **neutral when closed**, the state always in words ("US market: after-hours trading" spoken); unknown shows nothing |
| C4 | At a Glance: 2×2 separate cards with icon tiles; **"+6.4% (Y…" truncated** on iOS; helper text in the low-contrast tertiary colour | One card, `StockMetricGrid` (two columns, one at large text); the "(YoY)" qualifier moves to the supporting line ("Year over year · Growing revenue") so the value is complete; 52-week and day ranges join this card |
| C5 | Repeated values: Market Cap and P/E under the chart, in At a Glance, and again in Key Ratios; Dividend Yield twice; sector/industry shown as header tags, About tags and a separate "Sector & Industry" section | Chart stats show Open/High/Low/Volume only; Key Ratios skip ratios At a Glance shows; sector/industry appear as tags in the header and once as rows inside About (the context sentence kept as muted text); the separate section is gone |
| C6 | Valuation: three separate tiles, coloured explanation box, two loose "→" text links | One card: current / historical / difference as a `StockMetricGrid` row (label/value rows when narrow), factual headline + explanation, then **"Understand P/E Ratio"** and **"See detailed valuation"** as `StockNavigationRow`s |
| C7 | Earnings: "View Earnings Calendar" and "Earnings history" as side-by-side text buttons | Calendar and history as `StockNavigationRow`s with one-line descriptions; Remind / View Results / Explain With AI (StockSteps+) unchanged |
| C8 | Unbordered cards; titles/values in `textPrimary`, captions `textSecondary/Tertiary` mixed | Bordered `StockCard`s like Portfolio/Home/Markets; text roles `textTitle/textValue/textSupporting/textMeta`; section spacing `sectionGap` |
| C9 | Assessment rows: badge squeezed the title at large text (**"Financia / l Health"**) | `StockAssessmentRow` (design system): title and badge share a line while both fit, otherwise the badge moves below the title (both platforms) |
| C10 | Watchlist star tinted **green** when on the watchlist (green is reserved for gains) | Action blue (`primaryText`) |
| C11 | About description in body size inside a card-less column | About card: description at `small` size with See More/See Less (existing), country, sector/industry rows |

## 2. Design decisions and hierarchy
1. *What company is this?* — logo, wrapping name (`sectionTitle`), ticker · exchange, tags.
2. *What is the price doing?* — `largeNumber` price, signed change with arrow + "Today", status chip + "Updated <time>" (exchange time, source
   quote time), chart with existing ranges and scrub label, then the source-backed "Why Did It Move?".
3. *What are the important numbers?* — At a Glance (existing four metrics + ranges). Tap → existing explanation sheet.
4. *What do they mean?* — "Understand <company>": facts (assessment rows with published thresholds) → interpretation (Beginner Insight with
   its evidence sheet) → education (About, Guided Research card).
5. *Where can I research further?* — Valuation & financial health (with links to the P/E lesson, detailed valuation and full financials),
   Earnings (calendar, history, results), News ("View All"), plus the header actions (portfolio, compare, practice).
- Kept as-is (deliberately): the chart component, range pills, Why-It-Moved card styling (education surface), insight card, news cards,
  entitlement badge/flows, explanation sheets and their text, all presenter data.
- Not added from the reference (no data or destination): "View more metrics", PEG ratio, Price/Book, ROE rows, a separate "Why did it move?"
  promo card, chart y-axis gridlines, "View all" on Earnings.
- `CompanyDetailsPresentation` (new, shared) holds the wording/grouping: status chip label/dot/spoken text, glance value/qualifier split,
  de-duplication of chart stats and key ratios, classification rows, section titles. No numbers are computed.

## 3. Components and files changed
- New: `app/shared/.../presentation/companydetails/CompanyDetailsPresentation.kt`; test `commonTest/.../companydetails/CompanyDetailsPresentationTest.kt` (4).
- Design system (both platforms): `MarketStatusIndicator` (switch → status chip; Compose `designsystem/components/MarketStatusIndicator.kt`,
  iOS `DesignSystem/StockComponents.swift`; used only by Company Details); `StockAssessmentRow` (badge wraps below the title; Compose
  `StockAssessmentRow.kt`, iOS `StockDetailComponents.swift`; also used by the sources/evidence sheets); iOS `StockMetricItem` gains optional
  `action`/`hint` (tappable metrics, matching Compose `onClick`) in `DesignSystem/StockControls.swift`.
- Screens: Compose `presentation/companydetails/CompanyDetailsScreen.kt`; iOS `CompanyDetailsScreen.swift`.
- Unchanged: `CompanyDetailsScene`/`CompanyDetailsModel`, presenters, `CompanyOverview` builder, valuation/financials/news sub-screens.

## 4. Android/iOS consistency
Same order, titles, wording and states. Native differences kept: Material/SF icons; iOS back button shows the previous screen's title; the
iOS header actions use `ViewThatFits` (one row → two rows → column), Android a `FlowRow`; At a Glance drops to one column at Android font
scale > 1.3 and iOS accessibility sizes (shared `StockLayout.metricColumns`). The MOCK clock showed "After-hours" on the emulator and
"Closed" on the simulators at the same time (each device's sample clock had advanced differently) — data, not UI.

## 5. Accessibility and responsive results (actually performed)
| Check | iOS | Android |
|---|---|---|
| Light | ✅ iPhone 16 | ✅ |
| Dark | ✅ (XXXL pass) | ✅ (in-app theme Dark, restored to Light) |
| Large text | ✅ XXXL: one-column At a Glance, assessment badge below "Financial Health", buttons stacked | ✅ 1.3× at 360 dp: same; values complete |
| Narrow | ✅ temporary iPhone SE 375 pt with a long name ("China SXT Pharmaceuticals, Inc.", "Drug Manufacturers - Specialty & Generic") — wraps, no clipping | ✅ 360 dp |
| Truncation fix (C4) | ✅ "+6.4%" / "+13.0%" complete, qualifier on its own line | ✅ |
| Negative / missing values | Unit tests only (glance "—"/"N/A" pass through unchanged; no zero substitution). Not seen on device (sample companies had values) |
| Loading / offline / error states | NOT RUN on device (code paths unchanged: skeleton, section errors with Try again) |
| VoiceOver / TalkBack | NOT RUN. Code review: status chip one spoken label; metrics merged label/value; assessment rows merged; chart keeps its summary description; navigation rows use button role/hint |

Observations: on Android at 1.3× the chart fills most of the swipe area, so a vertical drag that starts on it scrubs the chart instead of
scrolling (pre-existing chart gesture behaviour; scrolling from outside the chart works). On the 375 pt SE the "ALL" range pill sits at the
edge of the horizontally scrolling pill row (Phase 2 rule: pills scroll instead of truncating).

## 6. Test and build results (2026-10-10)
- `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`
  → core JVM **430/0**, core iOS **430/0**, shared Android **100/0** (+4), shared iOS **96/0** (+4), `assembleDebug` OK.
- iOS `xcodebuild … -destination 'generic/platform=iOS Simulator' … build` → **BUILD SUCCEEDED**.
- Server not re-run (no server or core code changed in 4C).

## 7. Screenshots and evidence (`docs/design/phase4c/`)
Before: `before-android-light-1.jpg`, `before-android-light-2.jpg`, `before-ios-light.jpg`. After: `after-android-light-1.jpg`,
`after-android-light-2.jpg`, `after-ios-light.jpg`, `after-android-dark.jpg`, `after-android-dark-1.3x-360dp.jpg`, `after-ios-dark-xxxl.jpg`,
`after-ios-se-375pt-long-name.jpg`; reference `stocksteps-company-details-reference.webp`.

## 8. Known limitations and regression risks
- **Risks:** `MarketStatusIndicator` and `StockAssessmentRow` are design-system components (status chip used only on Company Details; the
  assessment row also renders the sources sheet); iOS `StockMetricItem` gained optional fields (defaults keep existing callers unchanged);
  sector/industry no longer have their own section (still in the header tags and About); Key Ratios hide ratios already in At a Glance and
  the chart stats no longer list Market Cap / P/E — **no value is removed from the page**, each appears once.
- The Earnings header button now appears only when the page has no Earnings section (history stays reachable from that section).
- Not verified on device: loading/offline/error states, negative-change and missing-value companies, VoiceOver/TalkBack.

## 9. Readiness for Phase 5
Phase 4 (Home, Markets, Company Details) is complete pending review of 4C. Before Phase 5 I recommend: a VoiceOver/TalkBack walkthrough of
Home, Markets and Company Details; a device check of a loss-making / no-dividend company (negative values, "N/A" P/E); and deciding whether
the chart should yield vertical drags to the page scroll at large text. Phase 5 starts only after the user approves it.

# Global UI Refinement Phase 4C.3 — Company Details responsive UX and accessibility polish

Status: committed with Phase 4C.2 on 2026-10-10 as "Polish Company Details: natural loss wording, chart error states, dated change label,
responsive actions and range selector (Global UI Refinement Phases 4C.2 and 4C.3)". Base: `7adc89c` (Phases 4C + 4C.1).
Device checks ran with approval: **local MOCK server on :8081 only** (no LAN host), Android emulator at 360 dp, iPhone 16, and a temporary
iOS 18.3 iPhone SE (deleted afterwards). Display settings were restored. Phase 5 has not started.

## 1. Confirmed issues (fixed) vs recommendations (not changed)

| # | Type | Issue | Root cause | Fix |
|---|---|---|---|---|
| C1 | Accessibility (Android) | "3 sources" button: the clickable node had no label; its children were "3 sources" and "3 sources →" (TalkBack likely says it twice and reads the arrow) | The 4C.2 `semantics { contentDescription }` sat on a separate node from `clickable` | The pill's text node is `clearAndSetSemantics { contentDescription = "3 sources" }` inside the 48 dp clickable box (checked with a uiautomator dump) |
| C2 | Layout (iOS) | iPhone SE 375 pt, default text size: "Add to portfolio" wrapped onto two lines and the pair had unequal heights | The pair was side by side for every size below XXXL, with no width check | New shared `StockActionLayout.sideBySide` (measured labels, equal share, never at accessibility sizes), used by **both** platforms. Android's earlier rule was font scale only |
| C3 | Discoverability (both) | At 360 dp the range row scrolled with "ALL" fully off-screen and no cue; at XXXL "ALL" was clipped like a final option | The shared `StockPillSelector` had no scroll affordance | Scroll mode now fades the side that has more options (alpha mask, about one pill wide, works on any background) and scrolls minimally to keep the selected option visible (horizontal only, so the page never jumps). Applies to every selector in the app |
| C4 | Layout (both) | A chart error left a chart-sized blank area with a left-aligned message | The notice was drawn inside the fixed-height chart box | Compact centered state (title, message, Try again only when retrying helps) using the shared `FullStateMessage` / `StockStateMessage`. Loading keeps the full-height skeleton. No placeholder line is drawn; Open/High/Low/Volume stay. Core `ChartNotice` gained a `title` |
| C5 | Consistency (iOS) | The MOCK banner sat below the tab bar on iOS but above it on Android | A root `.safeAreaInset` covered UIKit tab titles, so 5C stacked the banner under the app | Each tab's content now insets the banner (`sampleDataInset`), so it sits directly above the tab bar. The root banner shows only when no tab bar is visible (pushed screens, sign-in), tracked by `SampleBannerPlacement`. Same single `/info` check: no new request or retry |
| C6 | Layout (both) | Long badge "More debt than cash" sat inline and squeezed "Financial Health" | Inline whenever it fit at all | Shared `StockBadgeLayout.mayShareTitleLine` (≤ 14 characters): long badges always go below the title; short ones stay inline when they fit |
| C7 | Repetition (both) | "Trading near its historical P/E valuation." appeared in Understand → Valuation **and** as the Valuation section headline | Both rendered `valuationHeadline` | `CompanyDetailsPresentation.valuationHeadline` hides the section headline when the Understand row already says it; numbers and the interpretive sentence stay |
| R1 | Recommendation | "Why Did It Move?" summary and its "Keep in mind" box repeat the timing caution | Server why-moving wording | Needs approval (server text) |
| R2 | Recommendation | "+135.60% past ALL" reads awkwardly | `ChartPresentation.summary` wording | e.g. "since 2021": presenter change |
| R3 | Recommendation | iOS hides the tab bar on Company Details; Android keeps it | iOS pushes details on the outer `NavigationStack` | Navigation architecture: out of scope |
| R4 | Phase 5 finding | Screener "Discover" cards break words at 1.3× ("Compan/ies") | Screener cards | Phase 5 |
| R5 | Recommendation | A failed `/info` call hides the MOCK banner for the session | Single check by design | Retry needs approval |

Verified unchanged (no fix needed): 4C.2 wording (RIVN "lost about $80 for every $100 in revenue", on device and in the new server fixture
test), "Oct 7" / "Latest session" change labels, no-history vs connection vs temporary states, the 4C.1 chart gestures (vertical swipe
scrolls; horizontal hold scrubs "Sep 24, 2026 • $335.92" = API value; release restores the summary; both platforms), and the 48 dp "Try again".

## 2. Files changed (4C.3, on top of 4C.2)

- Core: `companydetail/ChartPresentation.kt` (`ChartNotice.title`); tests `CompanyOverviewPresenterTest`.
- Shared: `theme/StockSemanticStyles.kt` (`StockActionLayout`, `StockBadgeLayout`); `designsystem/components/StockChip.kt` (selector fade
  and keep-selected-visible); `designsystem/components/StockAssessmentRow.kt`; `presentation/companydetails/CompanyDetailsScreen.kt` (actions,
  compact chart notice, sources semantics, valuation headline); `CompanyDetailsPresentation.kt` (`valuationHeadline`); tests
  `ResponsiveLayoutRulesTest` (new), `CompanyDetailsPresentationTest`.
- iOS: `CompanyDetailsScreen.swift`, `DesignSystem/StockDetailComponents.swift` (selector, assessment row), `ContentView.swift` (banner
  placement), `AppScene.swift` (`sampleDataInset`, tab-bar visibility).
- Server test: `market/CompanyDetailsFixtureWordingTest.kt` (MOCK fixtures through the shared presenter: RIVN, BB.TO, LONGN, SSHC.TO, AAPL).

## 3. Before / after

| Area | Before | After |
|---|---|---|
| iPhone SE default actions | "Add to / portfolio" wrapped, unequal heights (`ios-se-375pt-default-BEFORE-…`) | Stacked full width (`ios-se-375pt-default-AFTER-actions-stack.jpg`); iPhone 16 still paired (`ios-iphone16-default-AFTER-actions-still-paired.jpg`) |
| Range row at 360 dp | 1D–5Y, "ALL" invisible | "5Y" fades as a cue (`android-360dp-1x-light-header-pair-range-fade-on-5y.jpg`); after scrolling, ALL selected with a left fade (`android-360dp-range-row-scrolled-all-selected-left-fade.jpg`) |
| Offline chart | Chart-sized blank + left text (`android-360dp-1.3x-light-offline-chart-retry.jpg`, first pass) | Compact centered state (`android-360dp-1x-light-offline-compact-centered-retry.jpg`) |
| Financial Health badge | Inline next to the title | Below the title (`android-360dp-1x-light-understand-long-badge-below.jpg`) |
| Valuation section | Headline repeated | Numbers + explanation only (`android-360dp-1x-light-valuation-no-duplicate-headline.jpg`) |
| iOS MOCK banner | Below the tab bar | Above the tab bar (`ios-iphone16-default-banner-above-tab-bar-home.jpg`) |

Some screenshots under `docs/design/phase4c3/` come from the first verification pass, before the 4C.3 code changes. They show the 4C.2
state (RIVN wording, scrub, XXXL, dark, SE XXXL) and are labelled by their conditions in the filename.

## 4. Verification matrix

| Check | Android (360 dp) | iOS |
|---|---|---|
| Default / large text | 1.0× and 1.3× ✅ | iPhone 16 default ✅, XXXL ✅ (first pass); SE default ✅, XXXL ✅ |
| Light / dark | ✅ / ✅ (first pass) | ✅ / ✅ XXXL (first pass) |
| Long name, tags | LONGN ✅ | Tags stack at XXXL ✅ |
| Negative / missing data | RIVN, LONGN ✅ | Shared presenter (server fixture test); not opened on iOS |
| Loading / no history / offline / retry | ✅ / ✅ / ✅ / ✅ | — / not opened / ✅ / ✅ (first pass) |
| Scrub vs vertical scroll | ✅ | ✅ |
| MOCK banner, safe areas | Above tabs ✅, page end clears it ✅ | Above tab bar ✅, end of Home clears it ✅, pushed screen bottom ✅, absent on REAL default ✅ |

## 5. Tests and builds (sequential)

core JVM **435/0**, core iOS **435/0**, shared Android **107/0** (+3), shared iOS **103/0**, server `CompanyDetailsFixtureWordingTest` **3/0**,
`:app:androidApp:assembleDebug` OK, iOS `xcodebuild` (generic simulator) **BUILD SUCCEEDED**. The full `:server:test` suite was not run (no server
code changed). The selector's fade width was changed to one pill after the test run and only rebuilt (Android `assembleDebug`, iOS local build).

## 6. Accessibility status

- Sources label: verified in the Android accessibility tree (one "3 sources" node in a 48 dp clickable). iOS: `.accessibilityLabel("3 sources")`.
- Retry targets 48 dp (tree). Range options are `Role.Tab` / `.isSelected`.
- **TalkBack: NOT RUN.** Enabling it opened a system notification-permission prompt from the Accessibility Suite. It was dismissed with Back
  (permission still not granted) and TalkBack was disabled again (`accessibility_enabled=0`). Spoken output can't be captured from injected input.
- **VoiceOver: NOT RUN** (not available in the Simulator).

## 7. Remaining limitations

- **Known issue (not fixed, interrupted):** on the iPhone 16 at the default size the range row overflows by a few points, so "ALL" is
  fully visible but faded. Planned fix: fade only while more than half a pill is hidden (`slack = touchTarget / 2`) on both platforms.
- Fade and keep-selected-visible apply to every `StockPillSelector`; screens other than Company Details were not re-checked on devices.
- The iPhone 16 simulator still has the localhost-configured build installed (device state only; nothing in the repository).
- R1–R5 above need decisions.

## 8. Verdict

**PASS WITH CONDITIONS.** C1–C7 are fixed on both platforms, with shared rules and tests, and all suites and builds are green. Conditions:
the iOS "faded but visible ALL" issue (§7), TalkBack/VoiceOver passes, and owner decisions on R1–R5. Phase 5 starts only after approval.

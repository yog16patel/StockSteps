# Global UI Refinement Phase 5D.2 — Earnings design-system and layout refinement

Date: 2026-10-14. Status: **committed and pushed** as "Add Global UI Refinement Phase 5D.2 Earnings layout refinement with whole price-reaction labels from a shared label and value rule, design-system badges, banners and buttons, lesson-named links and an iOS EPS history chart on Android and iOS". Base: `a15cd5f` (Phase 5D.1 + 5D.1A/1B). UI and display-only changes.
Nothing changed in calculations, classification, currency rules (5D.1B), provider mappings, server insights, API contracts, AI grounding or prompts,
Firebase/auth, entitlements, reminder scheduling or navigation destinations. No server code changed.
Visual direction: the owner's Earnings Details mock-up (attached to the request), used as a direction, not a pixel specification.

## 1. Root cause of the price-reaction defect (P0)

The Details "Price reaction" card drew each row with the private Compose `Line`:

```kotlin
Row { Text(label, Modifier.weight(1f)) ; Column { Text(value) } }   // value column NOT weighted
```

A Compose `Row` measures unweighted children first, giving them the full row width. The `Source` value ("StockSteps sample price history ·
2026-10-07") and the `Window` value are wider than the card, so the value column took the whole width and the **weighted label got the remainder
(≈ 0 dp)**. "Window" therefore wrapped one character per line, and with `spacedBy` the content exceeded the row, so text drew over its
neighbours. It happened at 1.0× because the values come from the server and are long. iOS had the same structure (`HStack` with label, `Spacer`
and an unconstrained value), so long values squeezed the label there too.

**Fix:** a shared layout rule, `StockLayout.labelColumn(widestLabel, longestLabelWord, longestValueWord, availableWidth, gap)` in
`app/shared/.../theme/Spacing.kt`. Both platforms measure text widths and call it.
- **Side by side:** the label column is the group's widest label, at most 45 % of the row and never narrower than its longest word. The values
  wrap between words in the remaining width (right-aligned).
- **Stacked:** if the value column would be narrower than 35 % of the row, or narrower than the longest single value word, every row in the
  group puts its value under its label.
- **One decision per card**, so neighbouring rows read the same way (the Portfolio C.1 convention).
- **Nothing is truncated.** Label, value and detail scale on the same text curve (iOS `relativeTo: .footnote`).

It is drawn by `EarningsLines` (Compose `EarningsScreens.kt`, SwiftUI `EarningsScenes.swift`). Every Earnings label/value group uses it: the Details
EPS, revenue and reaction cards, expanded history rows, the Event card, the Results EPS, revenue and growth cards, and the Results price reaction.

## 2. Changes implemented

| Area | Before | After (both platforms unless noted) |
|---|---|---|
| Label/value rows | `Line`: weighted label beside an unweighted value; "Window"/"Source" broke into letters | `EarningsLines` + `StockLayout.labelColumn` (§1) |
| Details header | Name limited to 2 lines (Android); status as a hand-made pill; Remind me = raw `OutlinedButton`, Company details = `TextButton`; iOS had no logo, a system font and a `.bordered` button | Logo + full name (wraps, never cut) + ticker · exchange; status `StockStatusBadge` (NEUTRAL); next report in `primaryText`; **Remind me (outlined/secondary, bell icon) + Company details (primary)**, side by side only while both labels fit (`StockActionLayout`), else stacked |
| Quarter identification | Period repeated in the header and nowhere near the result | The summary card shows the quarter as an eyebrow ("Q2 FY2026"); the header drops it when a summary exists |
| Key takeaway (summary) | Headline + text | Eyebrow + headline + the EPS classification badge (`StockTitleWithBadge`, the shared `EarningsDisplay` badge) + explanation |
| EPS / revenue cards | Title + badge in a `Row`; link "What does this mean?" | `StockTitleWithBadge` (badge drops below at large text); "EPS" spelled out as "Earnings per share (EPS)"; the link text is the title of the lesson it opens: **"What is earnings surprise?" / "What is revenue?"**. Destinations are unchanged |
| Price reaction | "Price reaction" (Details) vs "How Did the Stock React?" (Results); "How it's measured" `TextButton` | One sentence-case title **"How did the stock react?"** on both screens; link "How is the price reaction measured?" (the lesson's title) |
| Caveats (E-06) | Bare `cautionText` captions | `StockBanner` WARNING: Details stale date; Results freshness; takeaway warnings; reaction warnings and "saved price data" (one banner per section, wording unchanged) |
| Chips (E-07) | Hand-made `Badge` / `PlusBadge` (Compose and iOS) | `StockStatusBadge` (NEUTRAL / PREMIUM), so shared colours and the no-wrap rule apply everywhere they're used |
| Buttons (E-08) | Raw Material `Button`/`OutlinedButton`/`TextButton`/`OutlinedTextField` on Details; iOS `.bordered` ×12 in Earnings | `StockButton` / `StockTextField`; iOS `.stockPrimary` / `.stockSecondary`. Sentence case: "Beginner takeaway", "Learn more", "View company details", StockSteps+ actions ("Explain with AI", "Show historical earnings", "Start over", "Try again", "Show earlier quarters", "Key takeaways", "Sign in to explain with AI") |
| Insights | "Learn more" shown even when no lesson exists (tap did nothing) | "Learn more" only when the methodology has a lesson |
| Earnings history | Rows: period + "EPS Beat (+5.9%)" text; no expand cue; iOS had **no chart** | Rows: period + classification badge (COMPACT), **"EPS C$1.94 vs C$2.00 est. · −3.0%"** (the same amounts and currency as the chart tooltip), revenue line, rotating chevron; expanded rows don't repeat the EPS line. Fewer than 2 reported quarters → "The chart appears once at least two quarters have reported EPS." **iOS gets `EarningsHistoryChart`** (reported solid, estimate dashed, swatch legend, drag to select, first/last labels) |
| Learn | Column of text buttons | Grouped `StockNavigationRow`s in one card (Watchlist/Portfolio pattern; same topics, same dialogs) |
| AI card | Hand-made badge, raw text field and button | `StockTitleWithBadge` + PREMIUM badge, `StockTextField`, full-width primary `StockButton`; "questions left today" in `textSecondary` |
| iOS typography (E-09) | System styles (`.caption`, `.subheadline`, `.headline`…) | `StockStepsTheme.font(token, relativeTo:)` / `stockFont` across Details, Results, the price reaction and the reaction chart; the screens read `dynamicTypeSize` for live updates |
| Muted text (E-12/E-13) | — | Reviewed: StockSteps+ `textPrimary` uses are headings (kept). `textTertiary` is already AA (≥ 4.58:1 light, ≥ 6:1 dark, Phase 1 tokens); Earnings detail/basis/source captions moved to `textSecondary` for consistency |

Display-only additions:
- core `HistoryRowView.classification`, `.epsVsEstimate` and `.surprise`. These are the server's values, formatted with the existing
  `EarningsFormatter`; the defaults keep older callers unchanged.
- shared `EarningsDisplay`: copy constants, `cardTitle`, `cardTopic`, `topicAction`, `historyChartShown` and the history line helpers.

### Files modified
- core: `earnings/EarningsPresentation.kt`; test `EarningsPhase5D2Test.kt` (new).
- shared: `theme/Spacing.kt` (`StockLayout.labelColumn`), `presentation/earnings/EarningsDisplay.kt`, `EarningsScreens.kt`, `EarningsPremiumUi.kt`;
  tests `ResponsiveLayoutRulesTest.kt`, `EarningsDisplayTest.kt`.
- iOS: `EarningsScenes.swift`, `EarningsPremiumViews.swift`, `EarningsReminderViews.swift`.
- docs: this report, captures `docs/design/earnings_phase5d_2/`, `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`.

## 3. Before / after (`docs/design/earnings_phase5d_2/`, Android emulator-5554, 360 dp, local MOCK, CNR.TO)

| Capture | Shows |
|---|---|
| `before-a360-details-cnr-header-5d1.png` | 5D.1 header: hand-made "Reported" pill, text-button actions, period in the header, "What does this mean?" |
| `after-a360-details-cnr-header.png` | Status badge, Remind me + Company details pair, quarter eyebrow + badge in the summary, "Earnings per share (EPS)" |
| `after-a360-details-cnr-reaction-1.0x.png` | **P0 fixed**: "Window"/"Source" on one line; values wrap between words; no overlap |
| `after-a360-details-cnr-reaction-1.3x.png` / `-2.0x.png` | Same card at 1.3× and 2.0×: labels whole, values wrap, nothing clipped |
| `after-a360-details-cnr-revenue-2.0x.png` | Long labels ("Growth vs a year ago") wrap between words; badge below the title |
| `after-a360-details-cnr-insights-history.png` | Insights + history chart with legend; tooltip "Q2 26 · reported C$1.94 · est. C$2.00" |
| `after-a360-details-cnr-history-rows.png` | History rows: badge + "EPS C$1.94 vs C$2.00 est. · −3.0%" (matches the tooltip), chevrons |
| `after-a360-details-learn-rows.png` | Learn as grouped navigation rows; disclaimers kept |
| `after-a360-results-cnr-top.png` / `-reaction.png` | Results: shared rows ("Expected" + consensus detail), reaction card with the same rule |
| `before_ios_details_signed_in_error.png` | iOS Details (5D.1 build) can't load for the cached signed-in session without the Auth emulator (see §6) |

The 5D.1 Android capture of the broken reaction card wasn't saved in 5D.1. The defect is documented in the 5D.1 handoff ("Window" and "Source"
wrap mid-word at 1.0×) and in the owner's report; the regression test reproduces the widths.

## 4. Android / iOS parity

| Item | Android | iOS |
|---|---|---|
| Label/value rule | `EarningsLines` (Compose) | `EarningsLines` (SwiftUI), same shared rule and thresholds |
| Badges, banners, buttons | design-system components | design-system components (`.stockPrimary`/`.stockSecondary`) |
| History chart | `StockTrendChart` (existing) | **new** `EarningsHistoryChart` (same tooltips and legend) |
| Header actions | `StockActionLayout` (fontScale ≥ 1.3 → stacked) | `StockActionLayout` (≥ xxxLarge → stacked) |
| Sample disclosure on Details | `StockSampleDataBanner` strip + the app strip | caption + the app strip (unchanged; see §7 D-1) |
| Calendar / Event screens | unchanged except the shared badge and rows | still system fonts (39 calls), out of scope (5D.3) |

## 5. Tests and builds (sequential)

| Suite / build | Result |
|---|---|
| `:core:jvmTest` | **PASS** 495/0 |
| `:core:iosSimulatorArm64Test` | **PASS** 495/0 on re-run. The first run had one timeout in `PortfolioSessionStateTest` (Portfolio, untouched, machine heavily loaded); it passed on the re-run |
| `:app:shared:testAndroidHostTest` | **PASS** 234/0. The first run failed my own new test, whose 2.0× numbers were wrong; the expectation was corrected and the rule kept |
| `:app:shared:iosSimulatorArm64Test` | **PASS** 230/0 |
| `:app:shared:compileAndroidMain` | PASS |
| `:app:androidApp:assembleDebug` (`-PstockstepsMockBackendUrl=http://127.0.0.1:8081`) | PASS |
| unsigned `xcodebuild` (iphonesimulator) | **BUILD SUCCEEDED** |
| `:server:test` | NOT RUN (no server change) |

New tests: `ResponsiveLayoutRulesTest.earningsLabelValueRowsNeverSqueezeALabelIntoLetters`, `EarningsPhase5D2Test` (3: classification carried,
row = tooltip amounts, missing estimate stays "—"), `EarningsDisplayTest` (+4: lesson-named links, sentence case, chart threshold, history lines).

## 6. Device verification

- **Android (emulator-5554, Pixel_9_Pro, 360 dp, light, local MOCK): PASS** for Details (header, summary, EPS/revenue, reaction at 1.0×/1.3×/2.0×,
  insights, history chart and rows, learn, AI card) and Results (top, reaction).
  - Nothing overlaps, no label breaks into letters, nothing critical is clipped, there's no horizontal scroll, the C$ currency is preserved, and the
    sample strip and bottom bar don't cover content.
  - Restored afterwards: font scale 1.0, `wm size reset`, `adb reverse --remove-all`. The emulator is still running.
- **Android dark theme: NOT RUN.** The app follows its own theme setting, not the system's. All colours are theme tokens.
- **Long company name:** verified with "Canadian National Railway Co" (wraps to 2 lines, nothing cut). No longer MOCK name was tried.
- **iOS runtime: NOT RUN for the 5D.2 build.** iPhone 16 taps now partly work (about half register), so this is no longer fully blocked.
  - **Details is BLOCKED**: the simulator's cached signed-in session makes Details call the signed-in endpoint, which needs the Firebase Auth emulator
    (owner approval). See `before_ios_details_signed_in_error.png`.
  - Results is reachable but wasn't captured, to save time.
- Screen readers: deferred by the owner.

## 7. Outstanding owner decisions

- **D-1 Sample-data disclosure.**
  - The Details/Results in-content line ("Sample earnings data for development, not real results.") says the *earnings figures* are fixtures. The
    app-wide strip ("Sample data · mock backend") says the *whole backend* is MOCK. Both are accurate.
  - Smallest proposal: replace the in-content strip with a compact **"Sample"** `StockStatusBadge` (SAMPLE kind) beside the status badge, as Discover
    did in Phase 5B. Results would keep its "Sample earnings data…" note once in the sources footer.
  - Not implemented: it reduces a disclosure.
- **D-2 Details vs Results roles** (audit E-04, decision 5D-2) stays for 5D.4. 5D.2 only aligned the shared titles and rows.
- **D-3 iOS signed-in checks:** approve starting the Firebase Auth emulator (or signing the simulator out) so iOS Details can be verified.

## 8. Known limitations and regressions

- No regressions found in the suites or on the Android device.
- At 2.0× the side-by-side reaction card is tall (values wrap over 6–7 lines), because the longest value word still fits. It is readable, but a
  stricter threshold (stack at ≥ 1.5×) could be tuned in 5D.3.
- The Calendar and Event screens still have system fonts on iOS and some caution captions (5D.3 scope).
- The history period label differs between the chart tooltip ("Q2 26") and the row ("Q2 FY2026"); the amounts match. Changing the core chart labels
  was out of scope.

## 9. Recommendation

**PASS WITH CONDITIONS.** The P0 defect is fixed and verified on Android at 1.0×/1.3×/2.0×. All suites pass and both apps build.
Conditions: iOS runtime check of the 5D.2 build (Results now; Details after D-3), Android dark theme, and owner decision D-1.

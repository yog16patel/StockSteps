# Global UI Refinement Phase 5D.4.2 — Borderless visual polish

Date: 2026-10-14. Status: **committed and pushed** as "Polish Global UI Refinement Phase 5D.4.2 Earnings with borderless content sections, a compact header, one-row Show details and Learn more, the comparison-unavailable badge below the title and quieter secondary metrics on Android and iOS" (on top of `a4f35c4`). Plan: `GLOBAL_UI_REFINEMENT_PHASE5D_4_2_PLAN.md`.
Owner approval: "go ahead" (recommended options):
- 5D42-1: borderless, checked on device;
- 5D42-2: 20 sp company name;
- 5D42-3: reported value bold, estimate and surprise quieter;
- 5D42-4: StockSteps+ Earnings sections included.

Display only. Not changed: core, server, API, calculations, classifications, currency, reminders, navigation, auth, entitlements, AI, fixtures.

## Changes (Android and iOS)
| Area | Change |
|---|---|
| Borders | Earnings content surfaces use `StockCard(bordered = false)` / `.stockCard(bordered: false)` per call site:<br>• Details: takeaway, EPS/Revenue cards, Not reported yet, reaction, Insights, AI card, Learn<br>• Results: EPS, Revenue, growth cards, reaction, Learn<br>• StockSteps+ Earnings sections: Understand, Ask, History cards<br>Global defaults, buttons, fields, chips, badges, banners, the Sample strip, navigation and dividers are unchanged. Event/Calendar/Digest screens are not touched |
| Rhythm | 20 dp above section headings (Insights, Earnings history, Learn, Learn more) via an extra `sm` over the 12 dp list spacing; 8 dp heading → content |
| Header | Logo `logoCompact` (40) and name at `sectionTitle` (20 sp, wraps); action gap tightened (`xxs`). All lines and actions kept; reminder behaviour unchanged |
| Takeaway | "Show details" and "Learn more" share one row (`FlowRow` / `ViewThatFits`), wrapping at large text; about 48 dp less |
| Badge | `EarningsCardTitle`: "Comparison unavailable" (UNAVAILABLE) goes below the title in compact size; Beat / Met / Missed stay beside the title (`EarningsDisplay.badgeBelowTitle`, unit-tested) |
| Metrics | `StockMetric(quiet)` / `StockMetricItem.quiet`: new optional parameter, default unchanged elsewhere. Earnings key metrics: the reported value is bold `textPrimary`; estimate and surprise are medium weight in `textBody`, same size |
| Reaction | The % change sits on its own line under the title (Details and Results) |
| Insights | The icon lines up with the title, not the period caption |
| History | Plot height from `StockChartLayout.height(width)`: about 146 dp on a 324 dp content width at 360 dp; the shared rule replaces a fixed 150 / 140 |

## Verification
| Check | Result |
|---|---|
| shared Android / iOS tests | **PASS** 244/0 / 240/0 (+1 test) |
| core tests | NOT RUN (core not changed; 502/0 at `a4f35c4`) |
| `assembleDebug`, `xcodebuild` (iPhone 16) | **PASS** |
| Android 360 dp light 1.0× Details (header, takeaway, Revenue, reaction, insights, history) and Results | **PASS** (`after-a360-*.png`). White borderless surfaces read clearly on #F7FAFD, so no hairline was needed (5D42-1) |
| Android 1.3× / 2.0× | **PASS** (`…-1.3x.png`, `…-2.0x.png`): toggle and Learn more wrap, badge below the title, nothing clipped or broken |
| Android dark (Details, Results) | **PASS**; theme restored to Light |
| iOS runtime | **NOT RUN**. The build installs and launches, but simulator taps stopped registering again (Home → AAPL ignored twice). No restart this round |
| iOS Earnings Details | BLOCKED (owner sign-in on the Auth emulator) |
| TalkBack / VoiceOver | NOT VERIFIED |

Known limitations:
- In the large-text row mode, `StockMetricGrid` draws every value at the same weight, so "quiet" only applies in the column layout.
- About 24 dp of padding remains under the last toggle row in each surface (card padding plus the 48 dp touch row).

**Recommendation: PASS WITH CONDITIONS** (iOS runtime after a simulator restart; iOS Details after the owner signs in).

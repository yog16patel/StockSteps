# Global UI Refinement Phase 5D — Earnings read-only audit and implementation proposal

Date: 2026-10-13. Status: **AUDIT ONLY — no Earnings code changed.** Plan step 6 (`docs/GLOBAL_UI_REFINEMENT_PLAN.md`). Baseline `main` at
`24e197f` (Portfolio E.2 closed, accepted as PASS WITH CONDITIONS). Method: source review of every Earnings screen on both platforms plus the
core presenters and formatters. **No device captures in this pass**: the iPhone 16 simulator's tap input failed at the end of E.2, and the
Android emulator is stopped. Every layout finding marked *(verify)* needs a device check before it's fixed.

Scope reviewed:
- Compose: `presentation/earnings/` `EarningsScreens.kt` (706), `EarningsPremiumUi.kt` (493), `EarningsReminderUi.kt` (214), scenes, routes.
- iOS: `EarningsScenes.swift` (1,077), `EarningsPremiumViews.swift` (695), `EarningsReminderViews.swift` (214).
- Core: `earnings/EarningsPresentation.kt`, `EarningsResults.kt`, `EarningsPriceReaction.kt`, `EarningsPremiumPresentation.kt`, `EarningsCalculations.kt`.

## 1. Screen map (both platforms mirror each other)

| Screen | Route | Reached from | Content |
|---|---|---|---|
| Earnings Calendar | `EarningsCalendarRoute` | Home, Markets, Watchlist, deep links | week strip, search/filter, day groups of event cards, digest card, notes |
| Event | `EarningsEventRoute("SYM:YYYY-Qn")` | calendar card, reminders, notifications | date, timing, status, explainer, reminder/watchlist/company actions, sources |
| Results (one quarter) | `EarningsResultsRoute` | event "View Results", calendar, digest | EPS card, revenue card, YoY/QoQ growth, takeaway, price reaction, StockSteps+ sections, learn, sources |
| Details (one company) | `EarningsDetailsRoute(symbol)` | Company Details "Earnings" | summary, EPS/revenue cards, price reaction, insights, history + chart, Ask AI, learn |
| Digest / Digest settings / Reminders | own routes | calendar, settings | StockSteps+ digest, cadence, reminder list |

## 2. Findings (prioritized)

P1 = can mislead a beginner or misstate data; P2 = clear usability, consistency or large-text defect; P3 = polish.

### P1 — accuracy and meaning

| ID | Finding | Where | Platforms |
|---|---|---|---|
| E-01 | **The same BEAT/MISS reads differently on two screens.** Results shows a coloured pill (green BEAT, red MISS); Details shows the same headline in neutral brand blue, with a code comment saying beats are deliberately *not* shown as good. A beginner sees one result as "good/bad" on one screen and neutral on the other. One rule is needed, defined in core. | Compose `SurpriseIndicator` vs `ResultCardView`; iOS `EarningsScenes.swift:906-908` vs `:647` | both |
| E-02 | **"Not reported yet" estimate sentence.** Both platforms can say "Analysts expect EPS of — and revenue of —." (missing estimate). **iOS also formats the numbers itself** (`EarningsScenes.swift:659`): EPS with no currency, and revenue always as billions with no currency (a $500M estimate reads "0.50B"), while Android uses `EarningsFormatter` (currency, M/B scaling). Fix: one sentence built in core from `EarningsFormatter`, omitting missing parts. | Compose `EarningsScreens.kt:387`; iOS `EarningsScenes.swift:659` | both (iOS worse) |
| E-03 | **History chart tooltips drop the currency** (`EarningsFormatter.eps(it, null)`) while the rows show it. For a CAD-reporting company the chart reads like USD. | Compose Details history chart `details`; iOS `HistoryChart` (verify) | both |

### P2 — beginner usability and hierarchy

| ID | Finding | Platforms |
|---|---|---|
| E-04 | **Two overlapping "results" screens.** Details and Results both show EPS/revenue cards and a price reaction, with different layouts, labels ("Price reaction" vs "How Did the Stock React?") and depth. A beginner can't tell which is the full answer. Propose: Details = company overview + history, linking each quarter to Results; Results = the one place for a quarter's numbers. Navigation change → owner decision 5D-2. | both |
| E-05 | **Disclaimer stacking.** Results can show "education, not investment advice" (takeaway), "Price moves have many causes… isn't investment advice" (reaction) and "Not investment advice…" (learn), plus sample/freshness/warning lines. Repetition pushes the actions down and trains users to skip notes. Propose one footer disclaimer per screen and keep section-specific *factual* caveats only. | both |
| E-06 | **Warnings and data caveats are bare caption lines** in `cautionText` (stale dates, moved dates, missing prices, price-provider fallback), not `StockBanner`. They have no title, no consistent icon and aren't announced like other banners. | both |
| E-07 | **Status chips are hand-made**: Compose `Badge` (`EarningsScreens.kt:313`), `PlusBadge`, `SurpriseIndicator`; iOS capsules with fixed `padding(8/3)` and `.caption`. None use `StockStatusBadge` / `StockBadgeKind` / `StockSemanticStyles`, so colours and the no-wrap large-text rule (`StockBadgeLayout`) are bypassed. | both |
| E-08 | **Button styles diverge from the design system.** Details uses raw Material `Button`, `OutlinedButton`, `TextButton`, `OutlinedTextField`; iOS uses `.buttonStyle(.bordered)` in 8 places (CLAUDE.md: use `.stockPrimary`/`.stockSecondary`). Labels mix Title Case ("View Results", "Learn About Earnings", "How Did the Stock React?") with the app's sentence case. | both |
| E-09 | **iOS typography**: 156 font calls in Earnings, 150 of them system styles (`.caption`, `.subheadline`…) instead of `StockStepsTheme.font(…, relativeTo:)`. Sizes and weights differ from Android and the refined screens; small captions scale faster than values at XXXL (CLAUDE.md `relativeTo` rule). | iOS |
| E-10 | **Label/value rows don't stack at large text.** The shared `Line` (Compose) puts a weighted label beside a right-aligned value with no `StockLayout.metricRowStacked` decision; long labels ("Expected timing", "Revenue vs estimate") will squeeze at 2.0×/AX5 *(verify)*. | both |
| E-11 | **Truncation**: 4 `maxLines = 1` on Compose Earnings (company name on event cards etc.) and 2 `lineLimit(1)` on iOS; long company names are cut, the plan's truncation category *(verify at 1.3×/2.0×)*. | both |
| E-12 | **Dark-mode text weight**: StockSteps+ sections use `textPrimary` for supporting copy (Compose `EarningsPremiumUi.kt` 21 primary vs 6 muted); the plan's "excessive white text" item. | Compose (iOS similar, 4 vs 6) |
| E-13 | **Low-contrast disclaimers**: several notes use `textTertiary` (`remainingToday`, disclaimers, sources). Needs an AA check in light and dark; switch to `textMeta`/`textSecondary` where below 4.5:1. | both |

### P2 — states

| ID | Finding |
|---|---|
| E-14 | Loading is a bare `LinearProgressIndicator` on every screen; on first load there's no skeleton or "Loading earnings…" text in view, and Details renders its header before data (name falls back to the symbol). Propose a short loading line under the header (no skeleton work). |
| E-15 | Errors use `StockErrorState` with Retry (good). The Event screen adds an "Open Earnings Calendar" fallback (good). Results distinguishes "not published" from failure (good). Gap: Details has no "not published / no history" empty state; it just shows fewer cards. |
| E-16 | Offline: Results hides StockSteps+ sections when offline (good) but says nothing about why; the calendar's freshness line is a polite live region (good) while Event/Results freshness lines aren't. |

### P3 — polish

- E-17: duplicated helpers per file (`SectionTitle`, `Caption`, `PremiumTitle`, `Badge`) → one shared helper set.
- E-18: price-reaction window chips append " · not yet"; a disabled-with-reason pattern would read better.
- E-19: "Learn" lists render as plain `TextButton`s; reuse `StockNavigationRow` like Watchlist/Portfolio.
- E-20: chart helper copy ("Dashed line… Rings…") is long; move it behind the chart's info action like Comparison (5C.1).

### What's already right (keep)

- Every number comes from server calculations through the presenters; screens only render (permanent Earnings rules).
- The classification is always a **word**, not just colour; history rows and cards merge semantics for screen readers.
- Event and Results only link to results when a verified report exists, never a placeholder.
- Sample data uses `StockSampleDataBanner`; the calendar freshness line is announced.
- Touch targets: iOS `minHeight: 48` and Compose `touchTarget` throughout.

## 3. Phased implementation proposal (each phase: plan → owner approval → implement → tests → sequential suites → device checks → report)

| Phase | Content | Size (est.) | Risk |
|---|---|---|---|
| **5D.1 Accuracy and one meaning** | E-01 one classification style from a new core `EarningsDisplay.classificationBadge` (label + `StockBadgeKind`) used by both screens; E-02 estimate sentence without placeholders; E-03 currency in chart tooltips. Core tests for each. | ~6 files + 1 core object | Low (display only) |
| **5D.2 Design-system alignment** | E-06 caveats → `StockBanner`; E-07 chips → `StockStatusBadge`; E-08 buttons/fields → `StockButton`/`StockTextField` and `.stockPrimary`/`.stockSecondary`, sentence case; E-09 iOS fonts to `StockStepsTheme.font` with paired `relativeTo`; E-12/E-13 muted tokens. | ~8–10 files | Medium (broad but mechanical) |
| **5D.3 Large text and states** | E-10 stacked label/value rows via `StockLayout`; E-11 truncation; E-14–E-16 loading line, Details empty state, freshness announcements; charts at 2.0×/AX5. | ~6 files | Medium |
| **5D.4 Hierarchy (needs decision 5D-2)** | E-04 Details vs Results roles, E-05 one disclaimer per screen, E-17–E-20 polish. | ~6 files, navigation-adjacent | Medium–high |

Unchanged in every phase: `EarningsMath`, identities, server calculations, reminders pipeline, StockSteps+ gating and AI validation, API
contracts, fixtures. Daily Brief and Learn (plan step 6, smaller debt) follow as 5D.5 after Earnings.

Verification per phase: core JVM + iOS, shared Android + iOS, `assembleDebug`, unsigned `xcodebuild` (server tests only if server code is
touched — none planned); devices on local MOCK: Android 360 dp 1.0×/2.0× light/dark on a dedicated emulator (never the backend session's
`emulator-5556`), iPhone 16 Large/AX5 light/dark; screen readers stay NOT VERIFIED unless requested.

## 4. Owner decisions

| ID | Decision | Recommendation |
|---|---|---|
| 5D-1 | Classification style app-wide: **neutral for MET, positive container for BEAT, negative for MISS, always with the word**, or fully neutral (brand accent) for all three | Coloured with words (matches Results today and the semantic tokens; still says what it is, not whether it's good) |
| 5D-2 | Details vs Results roles (E-04): make Details the company overview + history with each quarter linking to Results | Yes, in 5D.4 after 5D.1–5D.3 |
| 5D-3 | Start with 5D.1 | Yes |
| 5D-4 | Reboot the iPhone 16 simulator before device checks (input failed at the end of E.2); nothing on it needs keeping | Approve |

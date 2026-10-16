# UI/UX Refinement Phase 3 — Company Details, Financials & Valuation

Status: committed and pushed as "Refine Company Details, Financials and Valuation for UI/UX Refinement Phase 3 with a delayed-quote freshness line, one chart direction for line, caption and spoken summary, a fixed iOS AX5 chart axis, explained highlight and ratio rows, consistent P/E difference rounding and readable financial dates on Android and iOS" (on `0696f7e`) on the owner's request after their final review. Nothing deployed; no Jira issue moved.
Base: `ff5662e` "Fix iOS landing content under the status bar and shorten company page titles for UI/UX Refinement Phase 2 (KAN-16) …"
(pushed). Committed on top of `0696f7e` (the REAL Portfolio & Practice fix, committed and pushed by its own session at 18:00). Review package: `docs/reviews/company_details_ui_phase3/`.

## 1. Baseline and pre-flight
- `git status` clean at start; `git log -5`: `ff5662e` (KAN-16), `77a2656` (Home Phase 1, KAN-14), `c6c3407` (DEBUG API logging), `44d0247`
  (5D.5.6D), `4c5990f` (5D.5.6C4). Phase 2 is committed and pushed.
- Read: `CLAUDE.md`, `docs/AI_REVIEW_WORKFLOW.md`, handoff/status, the 5D.5.4 plan and reports, 5D.5.6D, Home Phase 1 and Top Bars Phase 2.
- **Parallel work:** during this phase another session (REAL Portfolio & Practice fix) left uncommitted edits in `server/`, `deploy/`,
  `.gitignore`, `docs/REAL_PORTFOLIO_PRACTICE_FIX.md`, `docs/reviews/real_portfolio_practice/`, `docs/PROJECT_HANDOFF.md` and
  `docs/project-status.md`. None was touched; this phase only added its own entries to the two shared docs. That session committed and
  pushed its work as `0696f7e` at 18:00; the commit contains none of this phase's files or sections (checked).
- Environment: local MOCK `:8083` (left running by Phase 2; reused, not restarted), emulator-5554 (signed-in test session, adb reverse 8083),
  iPhone 16 simulator (guest). emulator-5556, the physical phone and `:8081` were not touched.

## 2. Inventory (what the screens show and where it comes from)
| Area | Data (shared model) | Android | iOS |
|---|---|---|---|
| Identity | `CompanyOverview.name/listing/logoUrl` (logo falls back to ticker initials) | `CompanyHeader` | `header` |
| Price | `price`, `changeAmount/Percent`, `direction`, `marketStatus` (server), `updatedAt` (quote timestamp) — **no delayed flag, no extended-hours price** | same | same |
| Chart | `PriceChart` points per range; `ChartPresentation.summary` (caption, axes, a11y description) | `StockLineChart` | `StockLineChart(compactAxisAtLargeText)` |
| Metrics | `glance`, `quickStats`, `dayRange`, `yearRange`, `highlights`, `keyRatios`, `valuation` | `StockMetricGrid`, `StockInfoRow` | `StockMetricGrid`, `StockInfoLineGroup` |
| Financials | `FinancialStatementsModel` via `FinancialsDisplay` | `CompanyFinancialsScene` | `CompanyFinancialsScene.swift` |
| Valuation | `ValuationModel` via `ValuationDisplay` | `CompanyValuationScene` | `CompanyValuationScene.swift` |

Freshness and source: quotes come from the provider (`timestamp`), session status from the server's US market clock; MOCK is the fixture
captured Oct 7 (sample strip visible). Loading: header/chart/news skeletons, sections load and fail on their own (unchanged).

## 3. Findings (BEFORE, both platforms unless noted)
| ID | Finding | Evidence |
|---|---|---|
| P1 | The market-status chip ("After-hours") sat beside the price, reading as the price's own label (an after-hours price the quote never is). Nothing said quotes may be delayed (Markets and Watchlist do). | before `a_aapl_light_1`, `i_aapl_light_1` |
| P2 | 1D chart caption said "**today**" for an earlier session (header said "Oct 7"); ALL said "past **ALL**". | before frames; Android `a_pfai_1d`, `a_pfai_all` |
| P3 | 1D chart **line and caption** coloured by the day's quote direction (vs the previous close), not the chart period's own change: a falling 1D line and its "-1.87%" caption drawn green (AAPL MOCK intraday fades while the quote is up; can happen on REAL after a gap up). The spoken summary gave a signed number with no period words. | `a_aapl_1d`, `i_aapl_1d` (interim builds) |
| P4 | iOS AX5: chart axis labels grew to ~body size, overlapped the plot and the divider. The 5D.5.4A cap (`.dynamicTypeSize(...accessibility1)`) never applied because `StockStepsTheme.font` scales against the app's content-size category. | before `i_aapl_ax5_chart` |
| P5 | Financial Highlights showed a "—" change for Net Margin and Free Cash Flow (no change by design), reading as missing data. Highlights and Key Ratios had no explanation/ⓘ (open 5D.5.3 follow-up F8). | before `a_aapl_light_10`, `i_aapl_valuation_health_dark` |
| P6 | Same P/E difference **"+2%"** on Company Details vs **"+1.8%"** on Valuation (0 vs 1 decimal). | before frames |
| P7 | Earnings card: "Explain With AI" opened the same Earnings Results as "View Results" (duplicate action in a 9-element card). | before `a_aapl_light_14` |
| P8 | Financials: raw ISO dates ("ended 2025-09-30", "As of 2025-09-30"); revenue value shown 3× (chart caption, first Key Metrics tile, Revenue section headline). | before `a_fin_annual_light_*`, `i_fin_annual_*` |

Checked and left as is: identity wraps long names (PFAI) without repetition; range selector clear; chart a11y description present;
scrub behaviour; Annual/Quarterly + 3Y/5Y/10Y next to the chart; "Only 8 quarters of history" note; money vs % separated; units (B/M,
USD) in values; Valuation page current/average/difference/badge and footnote; no under/overvalued wording.

## 4. Decisions
| ID | Decision | Where |
|---|---|---|
| D1 | Price alone on its line; status chip + `CompanyDetailsPresentation.freshness` ("Updated 4:00 PM EDT · Oct 7 · Prices may be delayed") on the secondary line; spoken price sentence ends "Prices may be delayed." | shared presentation; both headers |
| D2 | 1D caption names its baseline and session ("-1.87% from 9:30 AM on Oct 7"), ALL its first year ("since 2021"); fallbacks "on Oct 7", "over the day", "over all available history". Wording only; the percentage is unchanged. | core `ChartPresentation.period` |
| D3 (U3) | One direction per chart: `ChartSummary.direction` is the selected period's own first-to-last change for **every** range; the line colour, caption colour and spoken summary ("Price chart, 1D: down 1.87% from 9:30 AM on Oct 7, from 338.19 to 331.86.") all use it. The 1D override to the header's daily direction (and the app-internal `dayDirection` parameter) is removed; no server or API contract changes. The header keeps its own daily change (vs the previous close) and session label; the 1D caption names its own baseline so the two read as different measures. | core `ChartPresentation.summary`; both chart sections |
| D4 | iOS compact axis resolves its font at AX1 explicitly (`axisFont`); without `compactAxisAtLargeText` nothing changes (only Company Details opts in). | `StockLineChart` |
| D5 | Highlights / Key Ratios rows carry `metricId`; rows with an explanation open it (glance sheet or the 5D.5.3 metric sheet, ⓘ only with a Learn term); no "—" for rows without a change. | core `CompanyOverview`, both screens |
| D6 | Company Details difference uses one decimal, like the Valuation page (display rounding only; positions/badges use the raw value as before). | core `CompanyOverview` |
| D7 | Earnings: "Explain With AI" removed; the AI note + StockSteps+ badge stay under View Results ("Results include AI Earnings Insight …"). | both screens |
| D8 | Readable reporting dates via `FinancialStatementsPresenter.readableDate` ("Sep 30, 2025"; unknown forms verbatim). | core |
| D9 | `FinancialsDisplay.headlineInSection`: the Revenue section drops its headline/change when the first Key Metrics tile shows it (sentence and "What is this?" stay). | shared display; both Financials screens |
| D10 | Android ⓘ after a metric label is inline text content joined to the last word by a no-break space (`StockMetricLabel`), so a wrapping label never leaves the ⓘ alone on a line (seen at 2.0× under "Net Income"). Spoken label unchanged (`contentDescription = label`). Consumers: `StockMetric`, metric-grid rows, `StockInfoRow` (same ⓘ purpose); no default parameter changed. | design system (Android) |
| D11 | iOS `MarketStatusIndicator` reads `dynamicTypeSize` in `body`, so a live Dynamic Type change redraws the chip (its font already scaled; the view didn't refresh). Only consumer: Company Details. | iOS design system |
| — | Not changed | navigation, stacks, routes, requests, presenters' loading, quote/valuation/financial calculations, API contracts, Company Search |

Owner decisions (2026-10-16):
- **U1 approved:** the chip stays on the secondary freshness line (a deviation from 5D.5.4A, which put it beside the price).
- **U2 approved:** one View Results action. Verified that nothing distinct was removed: before this phase both "View Results" and "Explain With AI"
  called the same action (`CompanyDetailsAction.LatestResults` → `earnings.latestReportId` → Earnings Results on Android, `CompanyDetailsScene.kt:81`;
  `onLatestResults` on iOS, `CompanyDetailsScene.swift:87`). The AI explanation itself (guest sign-in prompt, StockSteps+ gating, "Explain with AI")
  lives on Earnings Results (`premiumEarningsItems`, `EarningsScreens.kt:680`; `PremiumEarningsSections`, `EarningsScenes.swift:1037`) and is
  still reached through View Results.
- **U3 fixed:** D3.

## 5. Files
| Module | File | Change |
|---|---|---|
| core | `companydetail/ChartPresentation.kt` | `period` wording; one period direction (`summary(points, range)`); spoken summary in words |
| core | `companydetail/CompanyOverview.kt` | difference 1 decimal; `metricId` on highlights / key ratios |
| core | `companydetail/FinancialStatementsPresentation.kt` | `readableDate` for period description, "As of", balance-sheet sentence |
| shared | `presentation/companydetails/CompanyDetailsPresentation.kt` | `freshness`, `DELAYED_NOTE`, `explains`, delay note in `priceDescription` |
| shared | `presentation/companydetails/FinancialsDisplay.kt` | `headlineInSection` |
| shared (Android) | `CompanyDetailsScreen.kt`, `CompanyFinancialsScene.kt`; `strings.xml` (unused `details_updated` removed) | D1, D3, D5, D7, D9 |
| shared (Android) | `designsystem/components/StockMetric.kt` | D10 inline ⓘ |
| iOS | `CompanyDetailsScreen.swift`, `CompanyFinancialsScene.swift`, `DesignSystem/StockDetailComponents.swift`, `DesignSystem/StockComponents.swift` | D1, D3, D4, D5, D7, D9, D11 |
| tests | `CompanyOverviewPresenterTest`, `FinancialStatementsPresenterTest` (core); `CompanyDetailsPresentationTest`, `FinancialsDisplayTest` (shared) | +5 core (two for conflicting daily vs period directions), +3 shared |

## 6. Verification (local MOCK :8083)
Two capture sets: "after" (Android and iOS, build before U3/D10/D11) and "final" (both platforms, final build). The Android final set was
taken after relaunching the Pixel_9_Pro AVD on the owner's go-ahead (§8).

| Scenario | Android (emulator-5554, 427 dp, signed in) | iOS (iPhone 16, guest) |
|---|---|---|
| Company Details light top / middle / bottom | PASS before/after (11 frames) | PASS before/after |
| Dark top | PASS | PASS |
| Long name (PFAI) | PASS (wraps, one request per endpoint) | PASS |
| Chart 1D after U3 (line + caption + colour agree) | **PASS** final: red line, red caption "-1.87% from 9:30 AM on Oct 7"; ALL and 1M green | **PASS** final: same |
| Chart 1M / ALL | PASS (after build; ALL frame predates the wording fix) | PASS ("+135.60% since 2021") |
| Spoken chart summary | **PASS** in the accessibility tree (uiautomator): "Price chart, 1D: down 1.87% from 9:30 AM on Oct 7, from 338.19 to 331.86." / "Price chart, 1M: up 6.47% past 1M, …". TalkBack itself NOT RUN | text covered by core tests; VoiceOver NOT RUN |
| Financials annual / quarterly, light / dark | PASS | PASS |
| Valuation light / dark | PASS (unchanged page, +1.8% matches) | PASS |
| Highlights / Key Ratios explanation sheets | PASS (Debt / equity) | PASS (Net Margin) |
| Android 2.0× | PASS for clipping (dark; header stacks, values whole). **Final (light):** ⓘ stays with its last word ("Net / Income ⓘ", "(EPS) ⓘ"); Financials tiles (corner ⓘ) unchanged; 1.0× rows unchanged | — |
| iOS AX5 incl. live change | — | PASS: chart axis capped at AX1; header reflows; **final:** status chip now grows on a live change to AX5 and back (D11) |
| Empty / missing data | PASS by tests (no-change rows, unknown dates, unexplained ids, zero-start chart → "change unavailable"); PFAI has no 52-week range and shows none | same |
| Loading / error states | NOT RUN (not reproducible without stopping a server this session didn't start) | NOT RUN |
| Guest vs signed in | signed in only (no sign-out allowed) | guest only — signed-in BLOCKED (no approved account) |
| TalkBack / VoiceOver | NOT RUN | NOT RUN |

### Regression (DEBUG API log, after build)
- Android: opening AAPL/PFAI = one request per endpoint; scrolling, sheets, section bar: 0; Back from Financials: 0; Financials
  annual→quarterly: 1 `fundamentals` (period change, unchanged behaviour). Tab switches Markets↔Home: first run after a fresh install
  2 requests (`/me/watchlists`, `/me/alerts`, one-time user-data sync, not Company Details); repeat 6 switches → 0.
- iOS: opening AAPL = 11 requests (one per endpoint); 4 tab switches → 0; reading position kept.
- The final changes (D3, D10, D11) touch no request, state or navigation code.

### Tests and builds (sequential; counts from the JUnit XML)
| Suite / build | Run 1 | Run 2 | Run 3 | Final run (U3, D10, D11) |
|---|---|---|---|---|
| `:core:jvmTest` | PASS 608/0 | PASS 609/0 | PASS 610/0 | **PASS 611/0** |
| `:core:iosSimulatorArm64Test` | PASS 606/0 | PASS 607/0 | PASS 608/0 | **PASS 609/0** |
| `:app:shared:testAndroidHostTest` | PASS 281/0 | PASS 281/0 | PASS 281/0 | **PASS 281/0** |
| `:app:shared:iosSimulatorArm64Test` | PASS 278/0 | PASS 278/0 | PASS 278/0 | **PASS 278/0** |
| `:app:androidApp:assembleDebug` | PASS | PASS | PASS | **PASS** |
| `xcodebuild` (simulator) | PASS | PASS | PASS | **PASS** |

No flaky failure in four full runs (the known `PortfolioSessionStateTest` flake did not occur; its fix is still separate).

## 7. Known issues
- MOCK data: the AAPL intraday points end at $331.86 while the quote is $336.67, and Market Cap $127.2B is inconsistent with the price
  (fixture data, out of scope). With U3 the 1D chart (down from 9:30 AM) and the header (up vs the previous close) now differ visibly; each
  names its own baseline.
- iOS status chip: the text scales; its dot keeps a fixed size (small at AX5, decorative).
- iOS Markets reselect didn't scroll to the top in this session's simulator taps (navigation untouched; NOT investigated).
- Key Ratios rows are now 48 dp/pt tap targets, so the group is taller.

## 8. Unverified
- Incident (resolved): during the first final-capture attempt emulator-5554 (Pixel_9_Pro) disappeared and a Claude process outside this
  session's family ran `emulator -kill` against emulator-5556 (Pixel_9_Pro_Fold). This session stopped nothing. On the owner's go-ahead the
  Pixel_9_Pro AVD was relaunched as emulator-5554 (`-no-snapshot-save`, as before), `adb reverse tcp:8083` restored, the final build installed;
  the cached signed-in test session survived. emulator-5556 was **not** relaunched (not this session's device).
- BLOCKED: iOS signed in (no approved account); Android guest (no sign-out allowed).
- NOT RUN: TalkBack/VoiceOver (the spoken chart text was checked in the accessibility tree only); loading and error captures;
  tablets/foldables; Android dark 2.0× on the final build (light 2.0× captured).

## 9. Recommendation
**APPROVE** for the implementation — U1/U2 as approved, U3 fixed (one direction for line, caption and spoken summary, with conflicting-direction
tests), Android ⓘ wrap and iOS chip live Dynamic Type fixed, all suites and builds green, final captures PASS on both platforms. Remaining
owner passes (not blockers for this UI phase): signed-in iOS, TalkBack/VoiceOver.

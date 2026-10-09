# StockSteps — session handoff (2026-10-08, end of the Earnings Phase 5 + Company Comparison session)

> **Update (2026-10-09):** Company Comparison Phase 3 — Historical Financial Comparison is committed as **"Add Company Comparison Phase 3 historical financial comparison (1Y free, 3Y/5Y StockSteps+) on Android and iOS"**; see the root
> `PROJECT_HANDOFF.md` (top section) and `docs/SCREENER_AND_COMPARISON.md` → "Company Comparison — Phase 3".
>
> **Update (Company Comparison Phase 2 session, 2026-10-08):** Phase 2 — Guided Metric Interpretation (free) is committed as
> **"Add Guided Company Comparison Phase 2 (guided metric interpretation) on Android and iOS"**. See the root `PROJECT_HANDOFF.md` (top section), `docs/project-status.md` §0 and
> `docs/SCREENER_AND_COMPARISON.md` → "Company Comparison — Phase 2". The full-suite verification that §3/§9 below list as pending was
> run in that session (results in the root handoff). Sections below describe the state before Phase 2.

End-of-session handoff for continuing in a fresh Claude Code session. Read order: `CLAUDE.md` → this file →
`docs/project-status.md` → the feature spec (`docs/SCREENER_AND_COMPARISON.md` "Company Comparison — Phase 1",
`docs/EARNINGS.md` "Phase 5") → the code. Always re-check `git status` / `git log` first; the root `PROJECT_HANDOFF.md`
is the canonical milestone log (every commit must update it, per `CLAUDE.md`).

---

## 1. Repository state (verified at handoff)

- Branch `main`, in sync with `origin/main`, **working tree clean** (before this doc-only update).
- HEAD **`9bbf586` "Improve Company Comparison Phase 1 for beginners on Android and iOS"** (pushed).
- Commits made this session (oldest → newest), both pushed:

  | Commit | Title | Size |
  |---|---|---|
  | `b558ba9` | Add StockSteps+ premium earnings intelligence (Earnings Intelligence Lite Phase 5) on Android and iOS | 45 files, +5260/−245 |
  | `9bbf586` | Improve Company Comparison Phase 1 for beginners on Android and iOS | 26 files, +1103/−302 |

  Before this session: `87acb1d` (Earnings Phase 4) → `dc9843d` (P3) → `5219e13` (P2) → `b5e2b9e` (P1) → `c92ec89` (Daily Brief).
- Uncommitted after this handoff: **documentation only** (`docs/PROJECT_HANDOFF.md`, `docs/project-status.md`).
- `docs/audits/` (an audit made by another session at ~22:46: feature audit, API audit, backlog, tracker CSV) was never
  committed by this session and **no longer exists in the working tree** (removed outside this session). Its findings
  that matter are folded into this file; its "Phase 5 uncommitted" statements are obsolete.
- **The local MOCK server is NOT running** (it was stopped before the last test run). Start it with `./gradlew :server:runMock`.
- Disk: ~32 GB free. Build iOS with the default DerivedData (no `-derivedDataPath`).

## 2. Current development objective

StockSteps is a beginner investing-education app (Android Compose Multiplatform, native SwiftUI iOS, shared Kotlin
Multiplatform core, Ktor backend). Philosophy NUMBER → CONTEXT → EXPLANATION → EDUCATION; never advice, rankings,
predictions; MOCK-first; REAL never fabricates. The user drives work with large "implement Phase N end-to-end" prompts,
then says "commit and push".

This session: (1) **Earnings Intelligence Lite Phase 5** (StockSteps+ premium AI earnings) — done and committed;
(2) **Company Comparison Phase 1 review & completion** (free) — implemented and committed, but **its final full-suite
verification was interrupted** (see §8).

## 3. Exact task at the end of the session

The last implementation task was the Company Comparison Phase 1 prompt ("audit + improve the existing comparison; keep it
free; MOCK-first; tests; docs; final report with READY/PARTIAL/BLOCKED verdicts"). Status:
- Implementation, targeted tests, Android compile and iOS build: **done**.
- The full test command + `:app:androidApp:assembleDebug` was started, then **stopped by the user** ("commit and push now")
  before finishing. It was committed with that limitation documented (root `PROJECT_HANDOFF.md`, `project-status.md` §0).
- **Not yet done**: the full suite, the Android APK build, a live MOCK check of `/api/v1/compare` with the new fields, a
  device/simulator walkthrough, and the formal final report (sections A–I) the prompt asked for. Verdict at handoff:
  **Phase 1 MOCK = PARTIAL** (code complete; verification incomplete); **REAL = not production-ready** (not live-verified).
- The last user request is this handoff (documentation only; no commit).

## 4. Work completed this session

### 4.1 Earnings Intelligence Lite Phase 5 — StockSteps+ Premium Earnings Intelligence (`b558ba9`)
Full spec: `docs/EARNINGS.md` → "Phase 5". Highlights:
- **Server** `earnings/EarningsAi.kt`: `EarningsAiQuotaLedger` (per-user daily quotas by category: explanations 10, questions 20,
  digest summaries 3; global budget 5000; env-configurable; failed calls released, never charged), `EarningsGrounding`
  (bounded verified context with source ids `S1-results`, `S2-estimates`, `S3-prices`, `S4-history`, `S5-alternate`, `L-*`
  lessons; missing data listed as unavailable, conflicts withheld), `TemplateEarningsAi` (MOCK, scenario hooks via
  `forScenario`), `GeminiEarningsAi` (REAL, only with `GEMINI_API_KEY`), `EarningsAiValidator` (citations, exact verified numbers,
  advice/prediction, causal claims, guidance/commentary, links/HTML, leakage), `EarningsQuestionScreen` (declines injection and
  advice before any provider call).
- `earnings/EarningsPremiumService.kt`: overview, history, explain (shared cache per report+sourceVersion+prompt+model,
  coalescing, STALE detection), ask (private, report- and version-scoped conversations), usage; routes under
  `/api/v1/me/earnings/{reports/{id}/premium[,/history], reports/{id}/ai/{explain,ask}, ai/usage, digest/*}`.
- `earnings/EarningsDigest.kt`: personalized watchlist digest (+ optional AI summary), preferences, history, weekly delivery
  (`WEEKLY_DIGEST` in the Phase 4 reminder pipeline; opt-in; once per ISO week; plan/opt-in/content re-checked at send time;
  counts only in the push).
- `EntitlementService`: `CANCELED`, `GRACE_PERIOD` (still Plus), `BILLING_ISSUE` (free), restored; unreadable record → 503
  `ENTITLEMENT_UNAVAILABLE` (fail closed). Debug API states `grace|canceled|payment-failed|restored|unavailable` (MOCK only).
- **Core** `earnings/EarningsPremium.kt` (models, `HistoricalEarningsEngine`, `EarningsQuestionChips`, `EarningsDigestRules`),
  `EarningsPremiumPresentation.kt` (`EarningsPremiumPresenter`, `EarningsDigestPresenter`, `RemoteEarningsPremium`), `UserApi` calls.
- **Android** `presentation/earnings/EarningsPremiumUi.kt`, `EarningsPremiumScenes.kt`; **iOS** `EarningsPremiumViews.swift`,
  `IosEarningsClient` bridge. Entry points: Earnings Results (3 premium cards after free sections), Earnings Center digest card,
  Company Details AI preview, Daily Brief digest link, Settings → Earnings Digest & AI, `earnings-digest` push deep link.
- Fixture: fictional `SSHX` (7 events: history gap + CAD quarter) via `scripts/generate_earnings_fixtures.py`.
- Verified at that commit: core JVM 341 / core iOS 341 / server 316 / shared Android host 55 / shared iOS 49, 0 failures;
  `assembleDebug` OK; iOS BUILD SUCCEEDED; live MOCK curl checks passed.

### 4.2 Company Comparison Phase 1 review & completion (`9bbf586`)
Full spec: `docs/SCREENER_AND_COMPARISON.md` → "Company Comparison — Phase 1" (scope, entry points, UI, metric formulas,
architecture, API, MOCK scenarios, tests, limitations, Phases 1–5 table).
- **Audit findings fixed**: horizontal scrolling for 3 companies (fixed 132+116 dp columns); ticker-only chips; no Replace;
  no Watchlist entry; ~30 rows up front; row periods taken from the catalog ("TTM") while values were annual; silent USD
  conversion of market cap; `$` vs `C$` ambiguity; no latest-quarter growth; generic N/A reasons ignoring the source note;
  P/E with losses and D/E with equity ≤ 0 labelled "missing"; chart rebased per series (not a common date) and full-period
  change claimed when history ended early; 1Y history fetched twice; no provenance/freshness.
- **Core**: `ComparisonSelection.replace/set`; `ComparisonPresenter` rebuilt (core groups Overview/Growth/Profitability/
  Financial Health/Valuation/Shareholder Returns + `advanced` "More metrics" sections; per-cell `MetricCell.detail` period;
  explicit currency markers; local market cap with ≈USD detail; `EXAMPLES`; `replace`, `useExample`; one shared request per
  chart period); `MetricFormatter.explanation(metricId, value)` and `money(…, explicit)`; `CompanyColumn.listing`;
  `PerformanceNormalizer.compute` (common base date, `lastDate`/`note` for early-ending history; `normalize` kept as wrapper);
  new comparison-only metric `quarterRevenueGrowth`; observation rule for it; MetricEducation formulas clarified.
- **Server**: `ScreenerService` gains `quarterlyRevenueGrowth` (wired in `Application.kt` to `EarningsService.latestResults` via
  `quarterRevenueGrowth(results)`) and `provenance`; compare notes (currencies, fiscal-year months, retrieval dates, sources);
  performance returns `baseDate` and clearer notes. `FmpFundamentalsMapper`: P/E with losses and D/E with equity ≤ 0 →
  `NON_POSITIVE_DENOMINATOR` + note.
- **Android** `ComparisonScreen.kt` rewritten (equal-width columns, companies card with Replace/Remove, examples, More metrics,
  "Share price change" card, sources); `ScreenerScene.kt` wiring; Watchlist "Compare these companies"; Markets tile "Compare
  Companies". **iOS** `ScreenerScenes.swift` compare section rewritten to match; `IosScreenerClient` (`replaceInComparison`,
  `useExample`, `compareCompanies`, `maxCompanies`); Watchlist and Markets wiring.
- **Tests**: core `ScreenerEngineTest.kt` (+ new `ComparisonPhase1Test` class), server `ScreenerRoutesTest.kt` (Phase 1 MOCK
  scenarios, quarterly provider failure, chart base date), new `repositoryImpl/ComparisonMetricsAdapterTest.kt` (FMP adapter
  with stubbed JSON).

## 5. Files created / modified this session
Created (Phase 5): core `earnings/EarningsPremium.kt`, `earnings/EarningsPremiumPresentation.kt`, `commonTest/.../earnings/EarningsPremiumTest.kt`;
server `earnings/EarningsAi.kt`, `EarningsPremiumService.kt`, `EarningsDigest.kt`, `test/.../earnings/EarningsPremiumTest.kt`;
Android `presentation/earnings/EarningsPremiumUi.kt`, `EarningsPremiumScenes.kt`; iOS `EarningsPremiumViews.swift`.
Created (Comparison): server `test/.../repositoryImpl/ComparisonMetricsAdapterTest.kt`.
Modified: see `git show --stat b558ba9 9bbf586` (notably `Application.kt`, `EarningsReminderService.kt`, `EarningsService.kt`,
`UserDataStore.kt`, `PortfolioAnalyticsService.kt` (EntitlementService), `AnalyticsModels.kt`, `ScreenerService.kt`,
`FmpFundamentalsMapper.kt`, `ScreenerEngine.kt`, `ScreenerModels.kt`, `ScreenerPresentation.kt`, `ScreenerCatalogDefinitions.kt`,
`MetricEducation.kt`, `ComparisonScreen.kt`, `ScreenerScenes.swift`, Watchlist/Markets/Settings/Brief/CompanyDetails screens on
both platforms, `events.json`, docs).

## 6. Important implementation decisions and reasons
| Decision | Reason |
|---|---|
| Phase 5 premium content is shared per report (not per user) and access follows the current plan | Public report data; cost control; matches the StockSteps+ model (expired → no premium content, free results stay). |
| One quota ledger for all earnings AI (incl. the older Earnings Details "Ask") | Spec forbids inconsistent independent counters. |
| Validator requires output numbers to equal verified numbers (rounding variants only on the context side) | First version accepted invented numbers via rounding; fixed and tested. |
| Weekly digest reuses `EarningsReminderDocument` + the Phase 4 delivery pipeline | One push system; Firestore persistence for free; opt-in preserved on downgrade. |
| Comparison latest-quarter growth reuses Earnings Results | Same comparability rules as the earnings screens; no extra statement requests. |
| Comparison table: label row above equal-width columns | Removes horizontal scrolling for 3 companies on 360 dp; 4 still supported (hint recommends 2–3). |
| Kept 4-company support | Works without horizontal scroll; values wrap to 2 lines; documented trade-off. |
| Advanced metrics kept under "More metrics" instead of removed | Phase 1 beginner focus without losing existing capability. |
| Market cap shown in listing currency + ≈USD detail; explicit US$/C$ when mixed | Spec: never silently compare different currencies. |
| Chart: common base date; carry-forward ≤ 5 days disclosed; early-ending history measured to last close | Correct alignment across exchange calendars without inventing data. |

## 7. Known bugs, blockers and risks
- **Comparison commit not fully verified** (see §8): run the full suite and `assembleDebug` first thing.
- MOCK fixture source has `sampleFallback = true`: some gaps (e.g. TD debt/equity) are filled with labelled sample values, so
  MOCK "missing metric" scenarios rely on CSU.TO (dividend history), LONGN (price to sales), TSLA (market cap).
- Comparison REAL: not live-verified (TSX coverage, Finnhub quarterly coverage for Canadian names); each compare loads full annual
  fundamentals per company (~13 FMP calls, cached 6 h).
- Phase 5: no store billing/receipt verification/checkout (upgrade → Settings; MOCK plans only); quotas, explanation cache,
  conversations and digest AI cache are in process memory (single instance only); REAL Gemini output unverified; no secondary
  results source, filing links, guidance or split data.
- Push: real FCM/APNs delivery unverified; per the audit, **iOS has no FirebaseMessaging product and no `aps-environment`
  entitlement**, so iOS remote push is effectively not wired. Cloud Scheduler jobs and Firestore indexes not set up.
- No UI-automation framework (Compose UI tests / XCUITest): rendering, dark/light, large text, TalkBack/VoiceOver unchecked on
  devices for Phase 5 and Comparison.
- The Settings plan simulator only offers Free/Plus/Expired; grace/canceled/payment-failed/unavailable are debug-API only.
- Leftover cleanup candidate: unused `EarningsDetailsState.reminder`/`reminderBusy` and the alerts collection in `EarningsDetailsPresenter`.

## 8. Build and test results

**Verified**
| When | Command | Result |
|---|---|---|
| Phase 5 (`b558ba9`) | full: `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug` | 341 / 341 / 316 / 55 / 49, 0 failures; APK built |
| Phase 5 | `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` | BUILD SUCCEEDED |
| Phase 5 | live MOCK curl script (plans, 401/403 gates, cache/STALE, AI failure scenarios, conflicts, ask, history, digest, weekly delivery, entitlement states, downgrade) | passed |
| Comparison (`9bbf586`) | `./gradlew :core:jvmTest --tests 'org.example.stocksteps.screener.*'` | 30 passed |
| Comparison | `./gradlew :server:test --tests 'org.example.stocksteps.screener.*' --tests 'org.example.stocksteps.repositoryImpl.*' --tests 'org.example.stocksteps.market.MockModeTest'` | all passed (screener 16 incl. 3 new; adapter 2 new) |
| Comparison | `./gradlew :app:shared:compileAndroidMain`, `:app:shared:compileKotlinIosSimulatorArm64` | succeeded |
| Comparison | iOS `xcodebuild` (as above) | BUILD SUCCEEDED |

**Not verified**
- Comparison commit: full suite (core iOS tests, all other server tests, shared Android/iOS host tests) and
  `:app:androidApp:assembleDebug` — started, **stopped before completion**.
- No live MOCK HTTP check of the new comparison fields (`quarterRevenueGrowth`, `baseDate`, notes).
- No device/simulator UI walkthrough of either feature; no REAL provider/AI calls (none authorized).

## 9. Exact next steps
1. `git status`, `git log -1` (expect `9bbf586` + these doc edits). Commit the docs only if the user asks.
2. Run the full verification for `9bbf586` and record results:
   `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug`
   then the iOS `xcodebuild` command. Fix anything that fails (likely candidates: tests asserting old comparison wording, the
   renamed "Revenue growth (fiscal year)" label, or the new `quarterRevenueGrowth` catalog entry in screener/catalog tests).
3. `./gradlew :server:runMock`, then live-check:
   `curl -s 'localhost:8081/api/v1/compare?symbols=AAPL,RY.TO'` (expect `metrics.quarterRevenueGrowth` with a note like
   "Q3 FY2026 vs Q3 FY2025", provenance/retrieval notes) and
   `curl -s 'localhost:8081/api/v1/compare/performance?symbols=AAPL,BB.TO&period=1Y'` (expect `baseDate`, BB.TO excluded with reason).
4. Produce the Comparison Phase 1 final report the prompt requested (A initial assessment … I verdicts) using §4.2/§7/§8; give
   a MOCK verdict (READY only after steps 2–3 pass and a UI pass) and a separate REAL verdict (not ready until an authorized
   live acceptance run).
5. Optional, only if asked: device walkthrough (emulator/simulator; drive UI only on request); Comparison Phase 2 (guided
   interpretation, free) — **not started**; Comparison Phases 3–5 not started.

## 10. Requirements discussed but not implemented
- Comparison: Phase 2 (guided interpretation, free), Phase 3 (historical financial comparison: 1Y free, 3Y/5Y StockSteps+),
  Phase 4 (guided research checklist, free), Phase 5 (AI comparison assistant, StockSteps+); total-return chart; persisted
  selection; lighter REAL metrics path; Compose/XCUITest UI tests.
- Earnings Phase 5: store billing (Play Billing / StoreKit + server verification), a real paywall/checkout, shared
  (Firestore/Redis) quota and AI caches, persisted conversations, secondary results provider, filing/press-release links,
  guidance data, corporate-action/split data, Settings segments for all billing states.
- Platform: iOS FirebaseMessaging + APNs entitlement; Cloud Scheduler jobs; Firestore indexes (`earningsDeliveries`);
  backend deployment (Practice/Learning/Brief/Earnings routes 404 in REAL until deployed); accessibility audits.

---

# Earlier handoff (after Earnings Phase 4, start of this session) — kept for history

End-of-session handoff for continuing in a fresh Claude Code session. It complements, and does not
replace:

- `PROJECT_HANDOFF.md` (repo root): the **canonical milestone log**. `AGENTS.md`/`CLAUDE.md` require
  every commit to update it.
- `CLAUDE.md`: permanent rules, conventions, build/test commands, interop pitfalls.
- `docs/project-status.md`: feature status table, architecture, business rules, pending tasks.
- `docs/EARNINGS.md`: the full specification of Earnings Intelligence Lite **Phases 1–4** (newest phase
  first), including MOCK scenario tables, API tables, policies and production gaps.

Read order for a new session: `CLAUDE.md` → this file → `docs/project-status.md` → `docs/EARNINGS.md`
(or the relevant `docs/<FEATURE>.md`) → the code. Always re-check `git status` and `git log` first.

---

## 1. Repository state (verified at handoff)

- Branch `main`, in sync with `origin/main`, **working tree clean** before this doc update.
- HEAD **`87acb1d` "Add earnings reminders and smart notifications (Earnings Intelligence Lite Phase 4)
  on Android and iOS"** (pushed).
- Commits made this session (oldest → newest), all pushed:

  | Commit | Title | Size |
  |---|---|---|
  | `b5e2b9e` | Add Earnings Calendar (Earnings Intelligence Lite Phase 1) on Android and iOS | 39 files |
  | `5219e13` | Add Earnings Results and beginner explanations (Earnings Intelligence Lite Phase 2) on Android and iOS | 37 files |
  | `dc9843d` | Add post-earnings price reaction (Earnings Intelligence Lite Phase 3) on Android and iOS | 26 files |
  | `87acb1d` | Add earnings reminders and smart notifications (Earnings Intelligence Lite Phase 4) on Android and iOS | 50 files |

  Earlier commits: `c92ec89` Daily Market Brief → `a4a4aeb` Practice Portfolio → `defaade` Guided
  Research → `54b5559` → `af39fb4` original Earnings Intelligence → `4c65521` → `bf44bd7` → `187d130`.
- Uncommitted after this handoff: **documentation only**: this file, `docs/project-status.md`,
  `CLAUDE.md` (new permanent earnings rules), and a one-line pointer in the root `PROJECT_HANDOFF.md`.
- The local **MOCK server is running on :8081** with the Phase 4 code (`GET /health` → 200 at handoff).
  Restart it after any server change (`./gradlew :server:stopMock`, then `./gradlew :server:runMock`).
- **Disk space is tight** (≈9.7 GB free at handoff; it fell to ≈0.4 GB during the session). See §7.

## 2. Current development objective

StockSteps is a beginner investing-education app: Android (Compose Multiplatform), native SwiftUI iOS,
a shared Kotlin Multiplatform core, and a Ktor backend. Philosophy NUMBER → CONTEXT → EXPLANATION →
EDUCATION; never advice, scores, predictions or trading signals; PortIQX is the separate advanced product.

This session delivered **Earnings Intelligence Lite Phases 1–4** from large user prompts ("implement
Phase N end-to-end, MOCK-first, tests, builds, final report"; the user then says "commit and push").
**Phase 5 (premium AI earnings intelligence) has not been requested or started**, and each phase prompt
said not to implement Phase 5.

## 3. Exact task at the end of this session

**No implementation was in progress.** The last request was this handoff (documentation only, no
commit). Phase 4 was finished, verified, committed and pushed as `87acb1d`. The user also re-sent the
Phase 1 prompt once by mistake; I verified Phase 1 existed and did not re-implement it.

The next session should ask the user what to do next. Likely candidates (§9): Phase 5 prompt, a device
walkthrough of Phases 1–4, or real push/scheduler verification.

## 4. Work completed this session (chronological, verified in the repo)

### Phase 1: Earnings Calendar (`b5e2b9e`)
Extended the existing Earnings Center (from `af39fb4`) instead of rebuilding it.
- Markets "Earnings Center" card: real 7-day scheduled count, next date, watchlist count, or a fallback.
- **Earnings Calendar** screen replaces the old Upcoming/Results/Following list:
  - week strip with day counts, previous/next week, Today, date picker;
  - Day/Week view, Upcoming/Reported tabs, All Companies/My Watchlist filter;
  - debounced server-side search (ticker or name-word prefix);
  - compact cards (date, timing, status, watchlist marker);
  - freshness and stale labels, MOCK scenario chips.
- **Earnings Event Details** screen.
- Company Details "Earnings" section (next date or "Next earnings date not available.").
- Daily Brief earnings rows open the event; Watchlist links to the calendar filtered to My Watchlist.
- Deep links: `earnings:<eventId>`, `earnings-calendar`.
- Statuses SCHEDULED/REPORTED/POSTPONED/CANCELED/UNKNOWN come from source data only.
- Server additions:
  - calendar response gains `dayCounts`, `q`, `day`, `scope=watchlist` (signed in), `view=reported|scheduled`;
  - new routes `/earnings/events/{id}`, `/earnings/company/{symbol}/next`, `/calendar/search`;
  - FRESH/CACHED/STALE with a last-good fallback; profiles only for the returned page.
- Files: core `earnings/EarningsCalendar.kt`; Android `presentation/earnings/{EarningsRoute,EarningsScenes,EarningsScreens}.kt`;
  iOS `EarningsScenes.swift`, `IosEarningsClient.kt`; fixtures `CALENDAR_DEMO` in `scripts/generate_earnings_fixtures.py`.

### Phase 2: Earnings Results (`5219e13`)
- **Earnings Results** screen per fiscal period (`EarningsResultsRoute(symbol, fiscalYear, fiscalQuarter)`,
  report id `SYMBOL:YYYY-Qn`, deep link `earnings-results:<id>`). Sections in order:
  1. header;
  2. EPS card and revenue card (one shared `FinancialComparisonCard`, expandable "What is…?");
  3. year-over-year growth;
  4. previous-quarter growth;
  5. deterministic Beginner Takeaway;
  6. Learn More (existing `BeginnerEducation` lessons);
  7. sources.
- Exact decimal maths (`EarningsMath`); **classification changed to exact: MET only when equal (no
  tolerance), `IN_LINE` renamed `MET`**, shared with the old Earnings Details screen.
- Endpoints: `/earnings/reports/{id}`, `/reports/{id}/insights`, `/company/{symbol}/latest`,
  `/company/{symbol}/reports`.
- Entry points: calendar "View Results", event "View Results", Company Details latest-results preview,
  Daily Brief result highlights (`WatchlistHighlight.reportId`).
- Offline saved copy on the device (`UserDataResultsCache`).
- Files: core `earnings/EarningsResults.kt`; fictional `SS*` demo companies in `DEMO_RESULTS`.

### Phase 3: Post-earnings price reaction (`dc9843d`)
- "How Did the Stock React?" section inside Earnings Results:
  - First Session / 3 Sessions / 5 Sessions windows;
  - before/after regular-session closes, change and %;
  - Phase 2 context reused;
  - deterministic non-causal explanations (cases A–H);
  - daily-close chart with dashed earnings marker, baseline/endpoint rings and gaps.
  - Android extends `StockTrendChart` with `markers`/`highlights`/`height`; iOS adds `ReactionChart`.
- Server `earnings/PriceReactionEngine.kt`:
  - `ExchangeCalendar` wraps the existing `UsMarketCalendar`/`TsxMarketCalendar`;
  - a timing-aware baseline/endpoint policy;
  - statuses `ReactionStatus` (AVAILABLE, EVENT_TIME_UNKNOWN, …), plus `ChartReactionPriceSource` and `FixtureReactionPriceSource`.
- Endpoints `/reports/{id}/price-reaction?window=…` and `/price-history`.
- **Earnings Details' reaction now uses the same engine** (old `EarningsReactionCalculator.compute` removed).
- Fixtures: `fixtures/earnings/price-scenarios.json` (generated), demo reports SSHU, SSCA.TO, SSHD.

### Phase 4: Earnings reminders and smart notifications (`87acb1d`)
- Server `earnings/EarningsReminderService.kt`:
  - `ReminderPlanner` (pure): offsets 1/3/7 calendar days, user IANA zone and delivery time, DST policy,
    quiet hours, 2 h late grace, WAITING_FOR_DATE, CANCELED, results once, optional date-change notice;
  - service: CRUD/preferences, legacy migration, batched pass (one event fetch per company), reconciliation, dispatch;
  - routes and `MockScenarioPushSender`.
- Persistence in `userdata/UserDataStore.kt` (+ Firestore + Unavailable):
  - `EarningsReminderDocument` per user;
  - `EarningsNotificationDelivery` with a unique idempotency key, atomic claim with lease, accepted devices,
    backoff 30 s×2ⁿ capped at 30 min, max 5 attempts, invalid-token cleanup.
- Core `earnings/EarningsReminders.kt` (models + `EarningsRemindersPresenter`, account-wide in
  `AccountDependencies.earningsReminders`).
- Android `presentation/earnings/EarningsReminderUi.kt`:
  - reminder button, "Remind Me About Earnings" sheet, Earnings Reminders settings screen;
  - `EarningsRemindersRoute`; `LocalNotificationAccess` provided in `AppNavigation`;
  - `DeviceZone` expect/actual;
  - `earnings_reminders` channel + deep links in `MainActivity`/`AndroidPush.kt`.
- iOS `EarningsReminderViews.swift` (sheet, control, settings scene), wired in `AppScene`,
  `CompanyDetailsScene/Screen`, `SettingsScene/Screen`, `WatchListScene`, `PushNotifications.swift`.
- **Legacy per-company EARNINGS alert rules are migrated once into company reminders and no longer
  evaluated by `AlertEvaluator`**. Creating EARNINGS alerts returns 400 `EARNINGS_REMINDERS`; the alert
  editors no longer offer Earnings; the old Earnings Details reminder dialog was replaced.
  Lead days and results notifications are now free.

### Other changes
- Test-only fix: `DailyBriefTest.offlineShowsOnlyBriefsThisDeviceDownloaded` had a race (it awaited any error;
  now it waits for the specific "Previously opened" message). It had failed intermittently on the iOS simulator.

## 5. Files created this session (all committed)

```
core/src/commonMain/kotlin/org/example/stocksteps/earnings/EarningsCalendar.kt        (P1)
core/src/commonMain/kotlin/org/example/stocksteps/earnings/EarningsResults.kt         (P2)
core/src/commonMain/kotlin/org/example/stocksteps/earnings/EarningsPriceReaction.kt   (P3)
core/src/commonMain/kotlin/org/example/stocksteps/earnings/EarningsReminders.kt       (P4)
core/src/commonTest/kotlin/org/example/stocksteps/earnings/{EarningsCalendarTest,EarningsResultsTest,EarningsPriceReactionTest,EarningsRemindersPresenterTest}.kt
server/src/main/kotlin/org/example/stocksteps/earnings/PriceReactionEngine.kt         (P3)
server/src/main/kotlin/org/example/stocksteps/earnings/EarningsReminderService.kt     (P4)
server/src/main/resources/fixtures/earnings/price-scenarios.json                     (P3, generated)
server/src/test/kotlin/org/example/stocksteps/earnings/{EarningsCalendarServiceTest,EarningsResultsServiceTest,EarningsPriceReactionServiceTest,EarningsRemindersTest}.kt
app/shared/src/commonMain/kotlin/org/example/stocksteps/presentation/earnings/{EarningsReminderUi,DeviceZone}.kt
app/shared/src/{androidMain,iosMain}/kotlin/org/example/stocksteps/presentation/earnings/DeviceZone.{android,ios}.kt
app/iosApp/iosApp/EarningsReminderViews.swift
docs/PROJECT_HANDOFF.md (this file)
```
Main files modified: `earnings/{EarningsModels,EarningsCalculations,EarningsPresentation}.kt`,
`network/StockStepsApi.kt`, `data/userdata/UserApi.kt`, `brief/DailyBriefModels.kt`; server
`earnings/{EarningsService,EarningsReaction}.kt`, `brief/DailyBriefService.kt`,
`userdata/{UserDataStore,FirestoreUserDataStore,Push,AlertEvaluator,AlertRules,AlertsService}.kt`,
`Application.kt`; Android `presentation/{AppNavigation,earnings/*,markets/MarketsScene,companydetails/*,
brief/*,watchlist/*,settings/*}.kt`, `designsystem/components/StockTrendChart.kt`,
`di/AccountDependencies.kt`, `composeResources/values/strings.xml`, `androidApp/.../{MainActivity,
account/AndroidPush}.kt`, `androidApp/.../res/values/strings.xml`; iOS `AppScene, EarningsScenes,
MarketsScene, CompanyDetailsScene/Screen, DailyBriefScenes, WatchListScene, SettingsScene/Screen,
PushNotifications`.swift, `IosEarningsClient.kt`; `scripts/generate_earnings_fixtures.py`,
`fixtures/earnings/events.json` (164 events, 40 companies), `fixtures/earnings/price-scenarios.json`; `docs/EARNINGS.md`, `docs/project-status.md`, `CLAUDE.md`, root `PROJECT_HANDOFF.md`; server tests `market/MockModeTest.kt`, `userdata/AlertsTest.kt`, `earnings/EarningsServiceTest.kt`.
Full per-commit lists: `git show --stat b5e2b9e 5219e13 dc9843d 87acb1d`.

## 6. Important implementation decisions and reasons

| Decision | Reason |
|---|---|
| Extend the existing Earnings Center and Earnings Details instead of rebuilding them. | The prompts said not to duplicate infrastructure; `af39fb4` already had sources, caching and history. |
| Event and report identity is the fiscal period `SYMBOL:YYYY-Qn`, with an exchange-qualified symbol (`TD` ≠ `TD.TO`). | Stable across reschedules; the canonical instrument id. Providers give no event ids. |
| Calendar status comes only from source data; "Reported" needs figures or an explicit flag; postponed/canceled only from an explicit flag. | The spec forbids inferring status from the date. |
| **Exact decimal classification (MET only when equal)**, replacing the ±0.5%/half-cent tolerance everywhere. | The spec forbids tolerances that hide differences; one policy across both screens. |
| Calculations live on the server (results, insights, reactions, schedules); apps only format. | "Backend authoritative"; iOS and Android can't diverge. |
| One price-reaction engine on the exchange calendar, also used by Earnings Details. | The two screens previously used different rules; the calendar handles holidays and half days honestly. |
| Reactions are recomputed from the cached series per request; incomplete windows are never stored. | Revisions, moved dates and newly completed windows show immediately. |
| Earnings Results and Price Reaction are free for every reported period. | "Basic results free"; StockSteps+ keeps the older Earnings Details history limit, AI and advanced insights. |
| One backend reminder system; legacy earnings alerts migrated and no longer evaluated. | Avoids duplicate notifications from two systems; the spec says to reuse the existing infrastructure. |
| Dedup key = user \| type \| event \| offset \| event date; claim with lease; per-device acceptance. | Exactly-once per logical notification across retries, workers, manual and auto reminders, and several watchlists. |
| Device zone sent on load and with every preference save. | Travel and time-zone changes reschedule pending reminders without asking. |
| Fictional "StockSteps Demo" (`SS*`) companies for edge-case fixtures. | Real tickers can't plausibly carry canceled, revised or fiscal-change scenarios; fictional ones are labelled demo data. |

## 7. Known bugs, blockers and risks

- **Not verified in production:**
  - real FCM/APNs delivery (no signed builds, devices or entitlements);
  - Cloud Scheduler jobs (`/internal/earnings-reminders/dispatch` every 5 min, plus the brief and alerts jobs);
  - Firestore composite indexes on `earningsDeliveries` (status+dueAt, status+leaseUntil);
  - the deployed backend (Practice, Learning, Brief and Earnings routes return 404 in REAL until deployed);
  - REAL Finnhub coverage of TSX and plan limits (no paid calls were made).
- **REAL data gaps:**
  - Finnhub gives no publication time, revision flag, estimate period, exact announcement time or
    postponed/canceled flags;
  - FMP daily closes only (no OHLC, intraday or extended hours);
  - no corporate-action feed (a warning is shown);
  - rule-based exchange calendars (no TSX early closes; unscheduled closures only appear as missing prices).
- **Disk space:** the machine ran out of space mid-session. The previous session's scratch derived data
  (`/private/tmp/claude-501/-Users-yogeshpatel-Documents-StockSteps/d9b12d26-…/scratchpad/dd`, ≈2 GB)
  is now **broken** (its package artifacts were partly purged). Build iOS with the **default DerivedData**
  (`~/Library/Developer/Xcode/DerivedData/iosApp-dafomoo…`): omit `-derivedDataPath`. That scratch folder
  can be deleted, but only with the user's OK.
- The core iOS test `DailyBriefTest.offlineShowsOnlyBriefsThisDeviceDownloaded` was racy (fixed in tests).
  Watch for other timing-sensitive tests on the iOS simulator.
- **The MOCK server's Gradle daemon can die** when other Gradle runs happen (seen once): if
  `curl localhost:8081/health` fails, restart `runMock`.
- MOCK reminder and delivery state is in-memory and cleared on server restart; the MOCK clock is pinned to
  `2026-10-07T21:15Z` at start and then advances.
- No UI-automation tests exist (no Compose UI or XCUITest setup). TalkBack/VoiceOver, large text and
  dark mode for Phases 1–4 were **not** checked on a device.
- Leftover, harmless: `EarningsDetailsState.reminder`/`reminderBusy` and the alerts collection in
  `EarningsDetailsPresenter` are unused since Phase 4 (cleanup candidate).
- No open functional bugs are known.

## 8. Build and test results

**Verified this session (after the final code change in each phase)**

| Suite | After P1 | After P2 | After P3 | After P4 (final) |
|---|---|---|---|---|
| core JVM | 309 | 322 | 325 | **329** |
| core iOS simulator | 309 | 322 | 325 | **329** |
| server | 257 | 266 | 278 | **297** |
| shared Android host | 55 | 55 | 55 | **55** |
| shared iOS | 49 | 49 | 49 | **49** |

All with 0 failures.
- `:app:androidApp:assembleDebug`: succeeded after each phase.
- iOS `xcodebuild` (scheme `app.iosApp`, generic simulator, `CODE_SIGNING_ALLOWED=NO`): **BUILD SUCCEEDED**
  after each phase. Phases 2–4 used the default DerivedData.
- Live MOCK checks (curl) after each phase:
  - calendar day counts, search, scenarios;
  - results spec example SSRV ($1.45 vs $1.20; $8.5B vs $8.2B; YoY +7.6%; QoQ +4.9%);
  - price reaction spec example ($150.00 → $157.50, +5.0%);
  - reminder flow: delivery time 17:00 Toronto, CRBU and JPM submitted, an `invalid-` token removed,
    reported event refused, 401 without auth.

**Not verified**
- On-device or simulator UI walkthroughs, accessibility and theme checks.
- Real push delivery on Android and iOS.
- REAL-mode end-to-end against a deployed backend.
- Gemini or other REAL AI.

## 9. Exact next steps for a new session

1. Run `git status` and `git log -1`. Expect `87acb1d` plus the uncommitted doc edits from this handoff.
   Commit them only if the user asks (e.g. "Update session handoff docs"), updating the root
   `PROJECT_HANDOFF.md` in the same commit and using the trailer from `CLAUDE.md`.
2. If needed:
   - `curl localhost:8081/health`, otherwise `./gradlew :server:runMock`;
   - for Android: `adb reverse tcp:8081 tcp:8081`;
   - test a MOCK user with `Authorization: Bearer mock-user:<uid>`;
   - run the reminder scheduler manually with `POST /internal/earnings-reminders/dispatch`.
3. Ask the user what's next. The default order:
   1. **Phase 5: premium AI earnings intelligence** (the user's series; not started). Expect a
      StockSteps+-only, server-side, validated, source-grounded design reusing the existing
      `EarningsResearchProvider` boundary (`TemplateEarningsResearch` in MOCK; REAL has no provider → 503)
      and the Gemini client with validators. Also reuse the Phase 2 insights and Phase 3 reaction as grounding.
   2. Device walkthrough of Phases 1–4: calendar → event → results → reaction, the reminder sheet and settings,
      and notification deep links. Drive the emulator or simulator only if the user asks.
   3. Production: deploy the backend; add Cloud Scheduler for reminders, brief and alerts; create the
      Firestore indexes; configure the APNs key and capabilities; then verify real push on one Android and
      one iOS device.
   4. Add a corporate-action source for REAL price reactions and Practice.
4. New features follow the established recipe:
   1. core models, policy and presenter, with `commonTest`;
   2. server service, routes, `UserDataStore` methods (InMemory, Firestore, Unavailable), MOCK fixtures and scenarios, tests;
   3. Android Route, Scene and Screen;
   4. iOS `Ios*Client` and SwiftUI;
   5. docs;
   6. the full test command, `assembleDebug` and `xcodebuild`.

## 10. Requirements discussed but not implemented

- Phase 5 (premium AI earnings analysis): not requested yet.
- UI automation (Compose UI tests, XCUITest), TalkBack/VoiceOver audits, dark/light and large-text checks:
  requested in every phase prompt, never done (no test infrastructure).
- iOS process-death restoration of the calendar selection (Android has it).
- Locale-aware date and number formatting (English only).
- Extended-hours, intraday and OHLC price data; corporate-action feed; versioned exchange calendars; TSX
  early closes.
- More delivery-time options and custom quiet hours in reminder settings (fixed choices and a 10 PM–7 AM preset).
- StockSteps+ digests or multiple schedules per event (architecture only reserved, per the Phase 4 spec).
- StockSteps+ purchase, restore and receipt validation; REAL AI providers for earnings and learning.
- From earlier sessions: scheduled brief generation and push retry queue; system Back between Guided Research
  steps; MOCK ETF profile fixtures.

## 11. Previous session (before `c92ec89`), for reference

Guided Research (`defaade`), Practice Portfolio (`a4a4aeb`) and the Daily Market Brief (`c92ec89`) were
built in the previous session; details are in the root `PROJECT_HANDOFF.md` milestones and
`docs/{GUIDED_RESEARCH,PRACTICE_PORTFOLIO,DAILY_MARKET_BRIEF}.md`.

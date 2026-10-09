# StockSteps — project status

Last reviewed: 2026-10-08 (end-of-session handoff) against the repository (`main`, latest commit **`87acb1d` "Add earnings reminders and smart notifications (Earnings Intelligence Lite Phase 4) on Android and iOS"**, pushed). Verify with
`git log`/`git status` before relying on this file. Per-feature details live in `docs/*.md`; the
milestone log and validation history are in `PROJECT_HANDOFF.md`.

## 0. Current task and next steps (read first)

HEAD is **"Improve Company Comparison Phase 1 for beginners on Android and iOS"** (on top of `b558ba9`, Earnings Phase 5). Company Comparison Phase 1 was reviewed and completed; see
`docs/SCREENER_AND_COMPARISON.md` → "Company Comparison — Phase 1". **No feature is in progress.**

Verified: core screener/comparison tests (`:core:jvmTest --tests org.example.stocksteps.screener.*`, 30 passed); server screener + FMP adapter tests (`:server:test --tests org.example.stocksteps.screener.* --tests org.example.stocksteps.repositoryImpl.*`, all passed); `:app:shared:compileAndroidMain` succeeded; iOS `xcodebuild` BUILD SUCCEEDED. **Not run to completion:** the full suite (core iOS, all server tests, shared Android/iOS host tests) and `:app:androidApp:assembleDebug` were started but stopped before finishing; no live MOCK curl pass; no REAL calls.

Next steps: run the full test command and Android build; restart the MOCK server (`./gradlew :server:runMock`) and do a live/device
check of Compare; authorized REAL acceptance run for comparison (TSX coverage, quarterly results); Comparison Phase 2 only when asked.

## 1. Feature status

### Committed and pushed (newest first)

| Feature | Commit (title) | Doc | Notes |
|---|---|---|---|
| Company Comparison Phase 1 review (beginner groups, no horizontal scroll, replace/examples, Watchlist entry, latest-quarter growth, periods/currencies, chart base date) | "Improve Company Comparison Phase 1 for beginners on Android and iOS" | `docs/SCREENER_AND_COMPARISON.md` | Free; full suite not re-run at commit (see §0). |
| StockSteps+ Premium Earnings Intelligence — Earnings Intelligence Lite Phase 5 (AI explanations, report-scoped questions, 8-quarter history, personalized weekly digest, fair-use quotas) | "Add StockSteps+ premium earnings intelligence (Earnings Intelligence Lite Phase 5) on Android and iOS" | `docs/EARNINGS.md` (Phase 5) | StockSteps+; MOCK plans only (no billing); in-memory quotas/caches. |
| Earnings reminders & smart notifications — Earnings Intelligence Lite Phase 4 (backend scheduling, opt-in watchlist reminders, results/date-change notices, deep links) | "Add earnings reminders and smart notifications (Earnings Intelligence Lite Phase 4) on Android and iOS" | `docs/EARNINGS.md` (Phase 4) | Free; earnings alerts migrated; real FCM/APNs delivery and Cloud Scheduler not verified. |
| Post-earnings price reaction — Earnings Intelligence Lite Phase 3 (calendar-aware First/3/5-session windows, chart, explanations) | "Add post-earnings price reaction (Earnings Intelligence Lite Phase 3) on Android and iOS" | `docs/EARNINGS.md` (Phase 3) | Free; regular-session closes only; no corporate-action feed in REAL. |
| Earnings Results & beginner explanations — Earnings Intelligence Lite Phase 2 (exact EPS/revenue comparisons, YoY/QoQ, takeaways) | "Add Earnings Results and beginner explanations (Earnings Intelligence Lite Phase 2) on Android and iOS" | `docs/EARNINGS.md` (Phase 2) | Free; classification now exact (MET only when equal); REAL lacks publication/revision metadata. |
| Earnings Calendar — Earnings Intelligence Lite Phase 1 (calendar, event details, Markets/Company Details/Brief/Watchlist entries) | "Add Earnings Calendar (Earnings Intelligence Lite Phase 1) on Android and iOS" | `docs/EARNINGS.md` (Phase 1) | Free; no UI automation; REAL TSX coverage unverified. |
| Daily Market Brief (Home/Markets previews, reader, personal overlay, Plus AI, notifications) | c92ec89 "Add Daily Market Brief on Android and iOS" | `docs/DAILY_MARKET_BRIEF.md` | Scheduler not deployed. |
| Practice Portfolio (virtual $10,000 simulator; Free 3 holdings / 14-day trial / StockSteps+) | a4a4aeb "Add Practice Portfolio simulator…" | `docs/PRACTICE_PORTFOLIO.md` | Includes the Practice "Buy Stock" → practice search → order fix. Backend not deployed. |
| Guided Stock Research & Beginner Learning (5-step guide, quizzes, Learn hub) | defaade "Add Guided Stock Research…" | `docs/GUIDED_RESEARCH.md` | Includes quiz-card spacing fix (`StockCard.verticalArrangement`). |
| Markets research tools spacing | 54b5559 | — | UI-only. |
| Earnings Intelligence & Earnings Calendar | af39fb4 | `docs/EARNINGS.md` | |
| Smart Stock Screener & Stock Comparison | 4c65521 | `docs/SCREENER_AND_COMPARISON.md` | |
| Portfolio Intelligence (Insights analytics) | bf44bd7 | `docs/PORTFOLIO_INTELLIGENCE.md` | |
| Portfolio tracker (real holdings ledger) | 187d130 | `docs/PORTFOLIO.md` | |
| Personalized Home dashboard | a6b3e2f | `docs/PERSONALIZED_HOME.md` | |
| Watchlists & smart alerts, accounts | e559ffe and earlier | `docs/AUTH_WATCHLIST.md` | Firebase auth; FCM/APNs push. |
| Markets dashboard (indices, movers, sectors, session) | ae05efd | `docs/MARKET_SNAPSHOT.md` | |
| Company News, article explanations, Why Did It Move | 4ddcd10 | `docs/AI_NEWS.md` | Gemini in REAL, templates in MOCK. |
| Biometric app lock + sign-out cleanup | 0d17770 | — | |
| Valuation (monthly historical P/E), Financials, Company Details | 4de8848, b1ed2cb, earlier | `docs/COMPANY_DETAIL.md` | |

### Implemented but NOT committed (working tree)

None.

## 2. Architecture and key decisions

- **Shared presenters in `:core`** (StateFlow) drive both UIs: Compose scenes collect them; SwiftUI
  wraps them in `@Observable` models via `Ios*Client` bridges (`IosPracticeClient`, `IosBriefClient`,
  `IosLearningClient`, `IosEarningsClient`, `IosScreenerClient`, `IosAccountClient`).
- **Account graph** (`AccountDependencies`): long-lived presenters/repositories shared across entry
  points (`practice`, `dailyBrief`, `learning`, `entitlements`, `watchlists`, `portfolio`, `home`…),
  created lazily; `StockStepsDependencies` is a per-screen public-data graph closed with its ViewModel.
- **Owner-tagged state**: every signed-in presenter resets on `(uid, environment)` change and passes
  `expectedOwner` to `UserApi`, so one account's data never flashes for another; caches are namespaced
  `"{env}|user:{uid}"` (`userCacheOwner`) or `"{env}|guest"` / `"{env}|public"`. Sign-out clears account caches.
- **Server user data** goes through `UserDataStore` (InMemory for MOCK/tests, Firestore in REAL,
  `UnavailableUserDataStore` → 503 when REAL has no credentials). Every read-modify-write is an atomic
  `update*` block (Firestore transactions).
- **Entitlements**: `EntitlementService` reads the server-written StockSteps+ record; Practice adds its
  own trial record (`PracticePolicy`) — access = PLUS > active TRIAL > FREE. Briefs use `BriefPolicy`.
- **Market data**: providers (FMP; Finnhub for news/earnings) only on the server; MOCK uses
  `FixtureMarketDataSource` (+ sample fallbacks). Quote freshness via `AlertRules.freshQuote`
  (US session aware); US calendar `UsMarketCalendar`; TSX calendar `brief/TsxMarketCalendar`.
  FX: Bank of Canada USD/CAD (REAL) / 1.35 (MOCK) via `PortfolioFxSource`.
- **AI**: Gemini JSON client (`news/GeminiInsights.kt` `GeminiJsonCall`) with validators
  (`InsightValidator`, `BriefAiValidator`); templates in MOCK.
- **Push**: devices stored via `DeviceRegistrar`; server `PushSender` (FCM in REAL, `SimulatedPushSender`
  in MOCK; `MockScenarioPushSender` for earnings reminders). `PushMessage.channel` selects the Android
  channel (`stock_alerts`, `earnings_reminders`). Deep links: Android `notificationLinks` channel
  (`"brief:<id>"`, `"earnings:<eventId>"`, `"earnings-results:<reportId>"`, `"earnings-calendar"`, or an
  alert symbol); iOS `NotificationCenter` names `.stockStepsOpenAlerts`, `.stockStepsOpenBrief`,
  `.stockStepsOpenEarningsEvent`, `.stockStepsOpenEarningsResults`.
- **Earnings Intelligence Lite** (server-authoritative; apps only format):
  - identity `SYMBOL:YYYY-Qn` (fiscal period, exchange-qualified symbol);
  - calendar status only from source data;
  - exact decimal maths (`EarningsMath`, MET only when equal);
  - one price-reaction engine on exchange calendars (`PriceReactionEngine`, also used by Earnings Details);
  - one earnings notification system (`EarningsReminderService`; legacy EARNINGS alert rules are migrated
    and no longer evaluated by `AlertEvaluator`);
  - reminder deliveries deduplicated by a unique key with an atomic claim and lease.

## 3. Business rules and requirements

- Beginner education first; never advice, ratings, scores or predictions; separate facts from possible
  explanations ("The available sources don't establish…").
- No fabricated data in REAL; stale/missing values labelled with their real timestamps.
- One unified **StockSteps+** subscription. In-app purchase is **not implemented** (no verified store
  products); paywalls say so and show no prices. MOCK can simulate plans (Settings → simulated plan).
- **Practice Portfolio**: $10,000 virtual cash; Free = 3 distinct open holdings forever (only opening a
  *new* instrument is limited; buying more/selling always allowed); optional one-time 14-day trial started
  only by explicit confirmation, server-timed, idempotent, no payment, no auto-renew; expiry keeps all
  data; weighted-average cost; server-priced immediate fills from fresh quotes (>1% move → re-review);
  idempotency keys; reset archives and never resets trial/challenges; real and practice data never mix.
- **Guided Research**: 5 steps, never locked; progress local-first, synced per account, guest progress
  never merged into an account; all education free; AI Plus-only.
- **Daily Brief**: Free = latest market summary, ≤3 stories, limited watchlist highlights, Concept of the
  Day, last 3 briefs; Plus = full personal overlay, more stories, AI, full history, personalized
  notifications. Never "today" for an old brief. Notifications opt-in, once per brief, market days only.

- **Earnings Intelligence Lite (free)**:
  - calendar, event details, results, price reaction and reminders are free for everyone;
  - StockSteps+ keeps the older Earnings Details full history, advanced insights and AI;
  - reminders: 1/3/7 calendar days before, the user's delivery time and IANA zone, opt-in automatic
    watchlist reminders (off by default), results notifications only after verified publication,
    optional date-change notices, cancellation notices only from an explicit source flag;
  - nothing is a trading signal.

## 4. Pending tasks

1. **Deploy the backend** (Cloud Run) with Practice, Learning and Brief routes; Practice/Learning/Brief
   don't work in REAL until then (404). Firestore credentials required (else 503).
2. **Cloud Scheduler** jobs: `POST /internal/earnings-reminders/dispatch` (every 5 min), `POST /internal/daily-brief/dispatch` (and existing
   `/internal/alerts/evaluate`) with header `X-StockSteps-Scheduler-Token` = `ALERTS_EVALUATOR_TOKEN`.
3. **StockSteps+ billing**: Play Billing / StoreKit + server receipt validation writing the entitlement
   record; restore/pending/cancelled states.
4. A verified **dividend/split source** for Practice in REAL (MOCK has sample events only).
5. Device/simulator walkthroughs and UI-automation tests (none exist; presenters are unit-tested).
6. REAL AI provider for earnings and learning explanations (currently 503).
7. Optional: system Back inside Guided Research steps; retry queue for brief pushes; TSX early closes.
8. **Earnings Intelligence Lite Phase 5** production gaps: store billing/receipt verification, shared (multi-instance) quota and AI caches,
   REAL Gemini output review, a secondary results provider, filing/press-release links, split data for EPS history.
9. **Verify earnings push end to end**:
   - Firebase project (`FIREBASE_PROJECT_ID`, ADC with the FCM Admin role);
   - APNs key in Firebase; iOS Push Notifications and Background Modes capabilities;
   - Firestore indexes `earningsDeliveries` (status, dueAt) and (status, leaseUntil);
   - one Android and one iOS device.
10. A REAL **corporate-action source** for price reactions (and Practice); intraday/extended-hours data if a plan supports it.
11. UI automation and accessibility checks for the earnings screens.
12. Cleanup: the unused `EarningsDetailsState.reminder`/`reminderBusy` and the alerts collection in `EarningsDetailsPresenter`.
13. Phase 5 follow-ups: Settings plan simulator segments for grace/canceled/payment-failed (debug API only today); persist
    conversations/explanations in Firestore; a real paywall/checkout once billing exists.

## 5. Known limitations / bugs to watch

- REAL AI features need `GEMINI_API_KEY` (otherwise 503 `AI_UNAVAILABLE`); earnings/learning AI have no
  REAL provider wired (503).
- MOCK ETF fixtures (SPY, QQQ…) have prices only (no profile file); some paths treat them as
  "unsupported" (Guided Research) while the sample profile fallback marks them ETF elsewhere.
- MOCK news feed includes general (political) news; the brief now filters to market-relevant stories,
  so a brief may show fewer than three stories.
- MOCK practice/brief state is in-memory: restarting the mock server clears it.
- Practice uses the NYSE calendar for TSX quote freshness; single-currency (CAD) cash.
- Firestore Practice document is per user, bounded (5,000 transactions/generation, 5 archives).
- Brief history persistence relies on briefs being requested (no scheduled generation yet).
- Android emulator needs `adb reverse tcp:8081 tcp:8081` again after restarts.
- REAL earnings data gaps:
  - Finnhub: no publication time, revision flag, estimate period, exact announcement time, or
    postponed/canceled flags;
  - FMP daily closes only (no OHLC, intraday or extended hours);
  - no corporate-action feed;
  - rule-based calendars (no TSX early closes);
  - TSX coverage unverified.
- Earnings reminder and delivery state is in-memory in MOCK (cleared on restart). The MOCK server's Gradle
  daemon can die during other Gradle runs, so restart `runMock` if `/health` fails.
- Disk space on the dev machine is tight. The previous session's scratch iOS derived data is broken; use
  the default DerivedData.
- Phase 1–4 screens have no device walkthrough or accessibility audit yet; there are no UI automation tests.

## 6. Where things are

| Area | Core | Server | Android (Compose) | iOS |
|---|---|---|---|---|
| Daily Brief | `brief/DailyBriefModels.kt`, `DailyBriefPresentation.kt` | `brief/BriefSessions.kt`, `DailyBriefService.kt`, `BriefAi.kt`, `DailyBriefRoutes.kt` | `presentation/brief/*`, Home/Markets cards | `DailyBriefScenes.swift`, `IosBriefClient.kt` |
| Practice | `practice/PracticeModels.kt`, `PracticeEngine.kt`, `PracticeContent.kt`, `PracticePresentation.kt` | `practice/PracticeService.kt`, `PracticeRoutes.kt` | `presentation/practice/*` | `PracticeScenes.swift`, `IosPracticeClient.kt` |
| Guided Research | `learning/*` | `learning/LearningService.kt` | `presentation/research/*`, `presentation/learn/*` | `GuidedResearchScenes.swift`, `IosLearningClient.kt` |
| Earnings (Phases 1–4) | `earnings/EarningsCalendar.kt`, `EarningsResults.kt`, `EarningsPriceReaction.kt`, `EarningsReminders.kt`, plus `EarningsModels/Calculations/Presentation.kt` | `earnings/EarningsService.kt`, `EarningsSources.kt`, `PriceReactionEngine.kt`, `EarningsReminderService.kt`, `EarningsReaction.kt` | `presentation/earnings/*` (`EarningsRoute`, `EarningsScenes`, `EarningsScreens`, `EarningsReminderUi`, `DeviceZone`) | `EarningsScenes.swift`, `EarningsReminderViews.swift`, `IosEarningsClient.kt` |
| Earnings fixtures | — | `scripts/generate_earnings_fixtures.py` → `fixtures/earnings/events.json`, `price-scenarios.json` | — | — |
| Screener/Compare | `screener/*` | `screener/*` | `presentation/screener/*` | `ScreenerScenes.swift` |
| Portfolio (+Insights) | `portfolio/*`, `portfolio/analytics/*` | `userdata/Portfolio*.kt` | `presentation/portfolio/*` | `Portfolio*.swift` |
| Entitlements | `portfolio/analytics/EntitlementsRepository.kt` | `userdata/PortfolioAnalyticsService.kt` (`EntitlementService`) | Settings simulated plan | `SettingsScreen.swift` |
| User data store | `data/userdata/*` (client cache, `UserApi`) | `userdata/UserDataStore.kt`, `FirestoreUserDataStore.kt` | — | — |
| Navigation | — | — | `presentation/AppNavigation.kt` | `AppScene.swift` |

Tests: `core/src/commonTest/.../{brief,practice,learning,earnings,…}`, `server/src/test/.../{brief,practice,learning,earnings,screener,userdata,…}`.

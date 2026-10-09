# StockSteps — project status

Last reviewed: 2026-10-09 (Phase 5A session) against the repository (`main`, HEAD **"Add Phase 5A Cloud Run deployment preparation: container image, health probes, startup validation, JSON logging and staging docs"** on top of `4586889` "Add financial API Phase 4: public API protection, durable AI quotas, provider budgets and usage monitoring", both pushed to `origin/main`). Verify with
`git log`/`git status` before relying on this file. Per-feature details live in `docs/*.md`; the
milestone log and validation history are in `PROJECT_HANDOFF.md`.

## 0. Current task and next steps (read first)

Phase 4: **`4586889` "Add financial API Phase 4: public API protection, durable AI quotas, provider budgets and usage monitoring"** — pushed (Phase 4: admission/identity, existence gate, watch-data cap, per-job internal auth, App Check monitor mode, durable AI quotas,
provider budgets, usage summaries, audit and implementation docs), on top of `6a70d0e` "Verify financial API Phase 3 with before/after benchmarks and fix earnings-aware statement coverage".

**Current objective**: financial API cost and safety program. Phases 1–3 are done, committed and verified. **Phase 4 (production security,
durable AI quotas, provider budgets, cost monitoring) is implemented, verified and committed** ("Add financial API Phase 4: public API protection, durable AI quotas, provider budgets and usage monitoring"): audit docs
`docs/FINANCIAL_API_PHASE4_*.md` and the report `docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md` (§8–§9 blockers and validation). Final matrix on the
Phase 4 code: server 473/0 (3 skipped), core JVM 416/0, core iOS 416/0, shared Android host 55/0, shared iOS 49/0, `assembleDebug` OK, iOS
`xcodebuild` BUILD SUCCEEDED. Verdict: READY WITH CONDITIONS. Full session handoff: `docs/PROJECT_HANDOFF.md` (top section).

**Phase 5A (committed: "Add Phase 5A Cloud Run deployment preparation: container image, health probes, startup validation, JSON logging and staging docs")**: Google Cloud Run deployment preparation — `Dockerfile`, `.dockerignore`, `deploy/cloud-run-staging.yaml`,
`/health/live` + `/health/ready`, startup configuration validation, JSON logging, server-only Gradle build flag, and `docs/CLOUD_RUN_*.md`;
report `docs/FINANCIAL_API_PHASE5A_IMPLEMENTATION.md`. Server tests 481/0 (3 skipped). Image built for `linux/amd64` and container-verified on the owner's Ubuntu server
(report §6a: non-root, no secrets, fail-fast config, JSON logs, probes, SIGTERM, `--network none`; 203 MB compressed). Verdict: READY WITH CONDITIONS for Phase 5B (staging deploy). Next: image vulnerability scan;
owner inputs in `docs/CLOUD_RUN_DEPLOYMENT_CHECKLIST.md` §1; then Phase 5B only on explicit request.

**Earlier next task (still open)**: push when asked; then the deployment-side conditions in `docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md` §8/§9d — verify
ingress and set `TRUSTED_PROXY_HOPS` (D1), set provider plan limits and `CLOUD_RUN_MAX_INSTANCES` (D4/D5), reconfigure Cloud Scheduler with OIDC or
per-job secrets, confirm the deployed logging config (rotate the FMP key if TRACE was ever deployed), add the App Check SDKs (monitor mode), create
the logs-based metrics, dashboard, alerts and billing budgets. Owner decisions: `docs/FINANCIAL_API_PHASE4_DECISIONS.md`.

Other open follow-ups (unchanged by this session's cost work): Comparison Phase 5 AI device walkthrough and authorized REAL Gemini run;
real-Firestore check of `users/{uid}/meta/aiUsage` (now also used by Brief and Earnings AI) and the research paths; billing; deploy + Cloud
Scheduler (OIDC or the new per-job secrets); push verification; per-IP limits once `TRUSTED_PROXY_HOPS` is verified on the deployment.

## 1. Feature status

### Committed and pushed (newest first)

| Feature | Commit (title) | Doc | Notes |
|---|---|---|---|
| Financial API Phase 3 implementation (screener re-warm fix, 150-company universe, selective statements, market-/earnings-aware freshness, stale fallback) | `517a46b` "Implement financial API Phase 3: screener re-warm fix, 150-company universe, selective statements, market- and earnings-aware freshness, stale fallback" | `docs/FINANCIAL_API_PHASE3_IMPLEMENTATION.md` | Verified in "Verify financial API Phase 3 with before/after benchmarks and fix earnings-aware statement coverage" (benchmark A–H); per-instance only; no REAL calls. |
| Financial API Phase 3 audit (selective loading, screener warm-up, freshness, earnings invalidation, budgets) + `Phase3AuditBenchmarkTest` | `e4ba2be` "Add financial API Phase 3 audit (selective loading, screener warm-up, freshness) with request benchmark" | `docs/FINANCIAL_API_PHASE3_*.md` | Read-only; found the screener re-warm bug (fixed in `517a46b`). |
| Financial API Phase 1 audit + Phase 2 shared provider cache (failure-safe single flight, duplicate-path removal, local market status, search cache, `ProviderCalls` metering, narration budget) | `75760ab` "Add financial API cost audit and Phase 2 shared provider cache with request reuse" | `docs/FINANCIAL_API_*.md`, `docs/FINANCIAL_API_CACHE_IMPLEMENTATION.md` | Server only; measured 26→20, 74→72, 15→14, 30→1 upstream requests (MockEngine). |
| Company Comparison Phase 5 — AI Comparison Assistant (StockSteps+; `/api/v1/me/compare/ai/*`, durable quota `users/{uid}/meta/aiUsage`) **and** sign-in/create-account redesign on `StockStepsTheme` | `e27991d` "Add Company Comparison Phase 5 AI comparison assistant (StockSteps+) and redesign sign-in screens on Android and iOS" | `docs/SCREENER_AND_COMPARISON.md` (Phase 5) | REAL Gemini not live-verified; no billing; auth behaviour unchanged, no password-reset flow exists. |
| Company Comparison Phase 4 — Guided Research Checklist (free + StockSteps+) | `423f44a` "Add Company Comparison Phase 4 guided research checklist (free + StockSteps+) on Android and iOS" | `docs/SCREENER_AND_COMPARISON.md` (Phase 4) | Firestore path not run against a real project. |
| Company Comparison Phase 3 — Historical Financial Comparison (1Y free; 3Y/5Y StockSteps+) | `f2fed1d` "Add Company Comparison Phase 3 historical financial comparison (1Y free, 3Y/5Y StockSteps+) on Android and iOS" | `docs/SCREENER_AND_COMPARISON.md` (Phase 3) | Core iOS tests now verified (413/0). |
| Company Comparison Phase 2 — Guided Metric Interpretation (free) | `77ef8fb` "Add Guided Company Comparison Phase 2 (guided metric interpretation) on Android and iOS" | `docs/SCREENER_AND_COMPARISON.md` (Phase 2) | |
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
6. REAL AI: Phase 5 premium earnings AI uses Gemini only when `GEMINI_API_KEY` is set (output unverified); the older Earnings
   Details "Ask" and Guided Research AI still have no REAL provider (503).
7. Optional: system Back inside Guided Research steps; retry queue for brief pushes; TSX early closes.
8. **Earnings Intelligence Lite Phase 5** production gaps: store billing/receipt verification, shared (multi-instance) quota and AI caches,
   REAL Gemini output review, a secondary results provider, filing/press-release links, split data for EPS history.
9. **Verify earnings push end to end**:
   - Firebase project (`FIREBASE_PROJECT_ID`, ADC with the FCM Admin role);
   - APNs key in Firebase; iOS Push Notifications and Background Modes capabilities;
   - Firestore indexes `earningsDeliveries` (status, dueAt) and (status, leaseUntil);
   - one Android and one iOS device;
   - iOS: add the FirebaseMessaging package product and an `aps-environment` entitlement (per the 2026-10-08 audit, iOS remote
     push isn't wired: every `#if canImport(FirebaseMessaging)` block compiles out).
10. A REAL **corporate-action source** for price reactions (and Practice); intraday/extended-hours data if a plan supports it.
11. UI automation and accessibility checks for the earnings screens.
12. Cleanup: the unused `EarningsDetailsState.reminder`/`reminderBusy` and the alerts collection in `EarningsDetailsPresenter`.
13. Phase 5 follow-ups: Settings plan simulator segments for grace/canceled/payment-failed (debug API only today); persist
    conversations/explanations in Firestore; a real paywall/checkout once billing exists.
14. **Company Comparison Phases 1–2**: device UI pass (Explain cards, related-metric scrolling,
    TalkBack/VoiceOver, large text, dark/light); authorized REAL acceptance run (FMP TSX coverage, sector/industry labels, Finnhub
    quarterly coverage, Bank of Canada date in the FX disclosure); consider a lighter REAL metrics path (each compare loads full annual
    fundamentals, ~13 FMP calls per company, cached 6 h). Phase 3 committed: REAL statement coverage (TSX quarterly,
    `netIncome` semantics) unverified; Company Details still exposes raw statements for free (product decision if that should change).
    Phase 4 committed: real-Firestore run, device pass, cross-instance cache decision pending. Phase 5 committed (`e27991d`): device
    walkthrough, authorized REAL Gemini run and real-Firestore `aiUsage` check pending.
15. **Financial API Phase 3**: implemented (`517a46b`) and verified ("Verify financial API Phase 3 with before/after benchmarks and fix earnings-aware statement coverage"). Remaining: stale fallback for profiles/daily closes,
    market-aware watch-data quotes, weekend/multi-instance cost measurement in production metrics (`docs/FINANCIAL_API_PHASE3_IMPLEMENTATION.md` §8).
16. **Financial API Phase 4** (implemented and committed; deployment configuration pending): admission control and identity, existence gate, watch-data cap, per-job internal auth,
    App Check (monitor mode, client SDKs pending), durable Brief/Earnings AI quotas, provider budgets with circuit breaker, usage summaries.
    Deployment conditions: `docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md` §8. Deferred: Phase 4E shared caching (licensing D3).
17. App Check client integration (Android Play Integrity, iOS App Attest/DeviceCheck) and later enforcement (`APP_CHECK_ENFORCE`).
18. Password reset / forgot-password flow (no backend support; the sign-in screen shows a "not available yet" notice).

## 5. Known limitations / bugs to watch

- REAL AI features need `GEMINI_API_KEY` (otherwise 503 `AI_UNAVAILABLE`); the older Earnings Details ask and Guided Research AI
  have no REAL provider wired (503). Phase 5 quotas/caches/conversations are in process memory (single instance).
- MOCK fixtures use `sampleFallback`: some missing values are filled with labelled sample numbers (e.g. TD debt/equity).
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
- Screener: default universe 150 (50 per exchange); with 25 warm-ups/h coverage is full after 6 h and needs every hourly slot to stay full
  (U / T exactly); a smaller budget or failures show partial coverage ("X of 150"). Re-warm bug fixed in `517a46b`.
- Earnings-aware statement refresh without a source `periodEnd` uses a baseline rule (worst case: 2 h polling for one company for ≤ 10 days).
- All provider caches, single flight and most budgets are per Cloud Run instance; Cloud Run instance counts are unknown (no deploy config
  in the repo). Phase 4: route limiters key on the verified uid or a trusted client IP (only once `TRUSTED_PROXY_HOPS` is verified);
  until then anonymous callers share a large per-instance pool per route group. Per-instance limits are not global: the provider budgets
  (`ProviderGuard`) are the deployment-wide backstop once `CLOUD_RUN_MAX_INSTANCES` and plan limits are configured.
- Phase 4: every provider-backed public route has a cost-weighted admission policy; unknown symbols are answered by a cached existence check.
- Flaky: `PracticeServiceTest.concurrentOrdersCannotOverspendOrBypassTheLimit` failed once in a full parallel run, passed 3/3 alone.
- Company Details market status now follows the NYSE calendar (may show pre-market/after-hours); TSX listings still use NYSE status there.

## 6. Where things are

| Area | Core | Server | Android (Compose) | iOS |
|---|---|---|---|---|
| Daily Brief | `brief/DailyBriefModels.kt`, `DailyBriefPresentation.kt` | `brief/BriefSessions.kt`, `DailyBriefService.kt`, `BriefAi.kt`, `DailyBriefRoutes.kt` | `presentation/brief/*`, Home/Markets cards | `DailyBriefScenes.swift`, `IosBriefClient.kt` |
| Practice | `practice/PracticeModels.kt`, `PracticeEngine.kt`, `PracticeContent.kt`, `PracticePresentation.kt` | `practice/PracticeService.kt`, `PracticeRoutes.kt` | `presentation/practice/*` | `PracticeScenes.swift`, `IosPracticeClient.kt` |
| Guided Research | `learning/*` | `learning/LearningService.kt` | `presentation/research/*`, `presentation/learn/*` | `GuidedResearchScenes.swift`, `IosLearningClient.kt` |
| Earnings (Phases 1–4) | `earnings/EarningsCalendar.kt`, `EarningsResults.kt`, `EarningsPriceReaction.kt`, `EarningsReminders.kt`, plus `EarningsModels/Calculations/Presentation.kt` | `earnings/EarningsService.kt`, `EarningsSources.kt`, `PriceReactionEngine.kt`, `EarningsReminderService.kt`, `EarningsReaction.kt` | `presentation/earnings/*` (`EarningsRoute`, `EarningsScenes`, `EarningsScreens`, `EarningsReminderUi`, `DeviceZone`) | `EarningsScenes.swift`, `EarningsReminderViews.swift`, `IosEarningsClient.kt` |
| Earnings fixtures | — | `scripts/generate_earnings_fixtures.py` → `fixtures/earnings/events.json`, `price-scenarios.json` | — | — |
| Screener/Compare | `screener/*` (Phase 2: `ComparisonInterpretation.kt`; Phase 3: `ComparisonHistory.kt`, `ComparisonHistoryPresentation.kt`; Phase 4: `ComparisonResearch.kt`, `ComparisonResearchPresentation.kt`) | `screener/*` (`ComparisonHistoryService.kt`, `ComparisonResearchService.kt`, `ResearchPdf.kt`), `service/ProviderUsage.kt` | `presentation/screener/*` (`GuidedMetricExplanation.kt`, `ComparisonHistoryUi.kt`, `ComparisonResearchUi.kt`, `PdfSaver*.kt`) | `ScreenerScenes.swift`, `ComparisonHistoryViews.swift`, `ComparisonResearchViews.swift` |
| Portfolio (+Insights) | `portfolio/*`, `portfolio/analytics/*` | `userdata/Portfolio*.kt` | `presentation/portfolio/*` | `Portfolio*.swift` |
| Entitlements | `portfolio/analytics/EntitlementsRepository.kt` | `userdata/PortfolioAnalyticsService.kt` (`EntitlementService`) | Settings simulated plan | `SettingsScreen.swift` |
| User data store | `data/userdata/*` (client cache, `UserApi`) | `userdata/UserDataStore.kt`, `FirestoreUserDataStore.kt` | — | — |
| Navigation | — | — | `presentation/AppNavigation.kt` | `AppScene.swift` |
| Comparison AI (Phase 5) | `screener/ComparisonAi.kt`, `ComparisonAiPresentation.kt` | `screener/ComparisonAiService.kt` | `presentation/screener/ComparisonAiUi.kt`, `ComparisonAiScene` | `ComparisonAiViews.swift`, `IosScreenerClient.kt` |
| Sign-in / create account | — | (Firebase auth unchanged) | `presentation/account/AuthScreen.kt`, `AuthComponents.kt`, `theme/AuthTokens.kt` | `AuthScreen.swift`, `AuthComponents.swift` |
| Provider caching & metering | — | `service/CompanyFinancialCache.kt`, `httpclient/NetworkUtils.kt` (`ProviderCalls`), `service/ProviderUsage.kt`, `repositoryImpl/Fmp*.kt` | — | — |

Tests: `core/src/commonTest/.../{brief,practice,learning,earnings,screener,…}`, `server/src/test/.../{brief,practice,learning,earnings,screener,service,userdata,…}` (provider-call benchmarks: `server/src/test/.../service/ProviderRequestBenchmarkTest.kt`, `Phase3AuditBenchmarkTest.kt`).

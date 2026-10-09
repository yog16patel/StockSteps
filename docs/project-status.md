# StockSteps — project status

Last reviewed: 2026-10-08 against the repository (`main`, latest commit **"Add post-earnings price reaction (Earnings Intelligence Lite Phase 3) on Android and iOS"**, pushed). Verify with
`git log`/`git status` before relying on this file. Per-feature details live in `docs/*.md`; the
milestone log and validation history are in `PROJECT_HANDOFF.md`.

## 0. Current task and next steps (read first)

The **Daily Market Brief** is committed and pushed as **c92ec89 "Add Daily Market Brief on Android and iOS"** (the commit after `a4a4aeb`);
this file and `CLAUDE.md` were added in the same commit. No application code is uncommitted; the only
working-tree changes are documentation (`CLAUDE.md`, this file, `PROJECT_HANDOFF.md` session handoff,
2026-10-08) plus the new `docs/PROJECT_HANDOFF.md` (full end-of-session handoff: objective, decisions,
verified vs unverified results, next steps). See also the "Session handoff" section at the top of the root
`PROJECT_HANDOFF.md`, which is the canonical milestone log.

Verification of `c92ec89` (test-result XML re-checked 2026-10-08): core JVM 299, server 247, core iOS 298,
shared Android host 55, shared iOS 49 — 0 failures. Android assembleDebug/installDebug and iOS xcodebuild
BUILD SUCCEEDED and live MOCK checks passed before the commit (not re-run since). MOCK server on :8081
was running the committed code at session end.

Next steps:
0. Price Reaction (Phase 3) is committed and pushed ("Add post-earnings price reaction (Earnings Intelligence Lite Phase 3) on Android and iOS").
   Earnings Results (Phase 2) is committed and pushed ("Add Earnings Results and beginner explanations (Earnings Intelligence Lite Phase 2) on Android and iOS").
   Earnings Calendar (Phase 1) is committed and pushed ("Add Earnings Calendar (Earnings Intelligence Lite Phase 1) on Android and iOS"); Phase 2
   (EPS/revenue vs estimates on the event screen) is the next earnings phase.
1. Ask the user for the next feature request or which §4 item to take.
2. Device walkthrough of Home card → brief reader → Scenarios menu → notification preferences
   (small screens, large text) — only when the user asks to drive the UI.
3. Production dependencies (§4): deploy the backend, Cloud Scheduler for `/internal/daily-brief/dispatch`,
   `GEMINI_API_KEY` for REAL brief AI.

## 1. Feature status

### Committed and pushed (newest first)

| Feature | Commit (title) | Doc | Notes |
|---|---|---|---|
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
  in MOCK). Deep links: Android `notificationLinks` channel (`"brief:<id>"` or alert symbol); iOS
  `NotificationCenter` names `.stockStepsOpenAlerts`, `.stockStepsOpenBrief`.

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

## 4. Pending tasks

1. **Deploy the backend** (Cloud Run) with Practice, Learning and Brief routes; Practice/Learning/Brief
   don't work in REAL until then (404). Firestore credentials required (else 503).
2. **Cloud Scheduler** jobs: `POST /internal/daily-brief/dispatch` (and existing
   `/internal/alerts/evaluate`) with header `X-StockSteps-Scheduler-Token` = `ALERTS_EVALUATOR_TOKEN`.
3. **StockSteps+ billing**: Play Billing / StoreKit + server receipt validation writing the entitlement
   record; restore/pending/cancelled states.
4. A verified **dividend/split source** for Practice in REAL (MOCK has sample events only).
5. Device/simulator walkthroughs and UI-automation tests (none exist; presenters are unit-tested).
6. REAL AI provider for earnings and learning explanations (currently 503).
7. Optional: system Back inside Guided Research steps; retry queue for brief pushes; TSX early closes.

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

## 6. Where things are

| Area | Core | Server | Android (Compose) | iOS |
|---|---|---|---|---|
| Daily Brief | `brief/DailyBriefModels.kt`, `DailyBriefPresentation.kt` | `brief/BriefSessions.kt`, `DailyBriefService.kt`, `BriefAi.kt`, `DailyBriefRoutes.kt` | `presentation/brief/*`, Home/Markets cards | `DailyBriefScenes.swift`, `IosBriefClient.kt` |
| Practice | `practice/PracticeModels.kt`, `PracticeEngine.kt`, `PracticeContent.kt`, `PracticePresentation.kt` | `practice/PracticeService.kt`, `PracticeRoutes.kt` | `presentation/practice/*` | `PracticeScenes.swift`, `IosPracticeClient.kt` |
| Guided Research | `learning/*` | `learning/LearningService.kt` | `presentation/research/*`, `presentation/learn/*` | `GuidedResearchScenes.swift`, `IosLearningClient.kt` |
| Earnings | `earnings/*` | `earnings/*` | `presentation/earnings/*` | `EarningsScenes.swift` |
| Screener/Compare | `screener/*` | `screener/*` | `presentation/screener/*` | `ScreenerScenes.swift` |
| Portfolio (+Insights) | `portfolio/*`, `portfolio/analytics/*` | `userdata/Portfolio*.kt` | `presentation/portfolio/*` | `Portfolio*.swift` |
| Entitlements | `portfolio/analytics/EntitlementsRepository.kt` | `userdata/PortfolioAnalyticsService.kt` (`EntitlementService`) | Settings simulated plan | `SettingsScreen.swift` |
| User data store | `data/userdata/*` (client cache, `UserApi`) | `userdata/UserDataStore.kt`, `FirestoreUserDataStore.kt` | — | — |
| Navigation | — | — | `presentation/AppNavigation.kt` | `AppScene.swift` |

Tests: `core/src/commonTest/.../{brief,practice,learning,earnings,…}`, `server/src/test/.../{brief,practice,learning,earnings,screener,userdata,…}`.

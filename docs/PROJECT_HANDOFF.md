# StockSteps — session handoff (2026-10-08)

This is the end-of-session handoff for continuing in a fresh Claude Code session. It complements, and
does not replace:

- `PROJECT_HANDOFF.md` (repo root) — the **canonical milestone log**. `AGENTS.md` requires every commit
  to update it. Its top "Session handoff" section matches this file.
- `CLAUDE.md` — permanent rules and conventions, build/test commands, interop pitfalls.
- `docs/project-status.md` — feature status table, architecture, business rules, pending tasks.
- `docs/<FEATURE>.md` — per-feature specifications.

Read order for a new session: `CLAUDE.md` → this file → `docs/project-status.md` → the relevant
feature doc → the code. Always re-check `git status` and `git log`.

---

> **Update (later on 2026-10-08):** HEAD is now **"Add Earnings Calendar (Earnings Intelligence Lite Phase 1)
> on Android and iOS"** (pushed), which also committed the doc edits listed below. See the top of the root
> `PROJECT_HANDOFF.md` and `docs/EARNINGS.md` "Phase 1" for that work.

## 1. Repository state (verified 2026-10-08)

- Branch `main`; HEAD **`c92ec89` "Add Daily Market Brief on Android and iOS"**, pushed to origin.
- Recent commits: `c92ec89` Daily Market Brief → `a4a4aeb` Practice Portfolio → `defaade` Guided
  Research → `54b5559` Markets spacing → `af39fb4` Earnings → `4c65521` Screener/Compare → `bf44bd7`
  Portfolio Intelligence → `187d130` Portfolio tracker.
- Uncommitted: **documentation only**: `CLAUDE.md` (commit hash wording), `docs/project-status.md`
  (hash, §0, §4 renumbered with REAL-AI item), `PROJECT_HANDOFF.md` (session-handoff section), and this
  new file `docs/PROJECT_HANDOFF.md`. No application code is uncommitted. Nothing was deleted.
- No `TODO`/`FIXME` markers in `core/src`, `server/src/main`, `app/shared/src`, `app/androidApp/src`,
  `app/iosApp/iosApp`.
- The local MOCK server is running on :8081 with the committed code (`GET /health` → 200 at handoff).
  An Android emulator (`emulator-5554`) is attached, with the latest debug build installed.

## 2. Current development objective

StockSteps is a beginner investing-education app: Android (Compose), native SwiftUI iOS, a shared
Kotlin Multiplatform core, and a Ktor backend. Philosophy: NUMBER → CONTEXT → EXPLANATION →
EDUCATION, and the journey Learn → Research → Practice → Understand. It never gives advice, scores or
predictions. PortIQX is the separate product for advanced investors.

The user drives development with large feature prompts ("implement X end-to-end, MOCK-first, tests,
builds, final report"). All feature milestones through the Daily Market Brief are complete and pushed.
**The objective now:** take the user's next feature request, or a pending item from §8, while keeping
the existing architecture and rules.

## 3. Exact task at the end of this session

**No feature implementation was in progress.** The final requests were documentation and handoff
only. The last code task, the Daily Market Brief, was finished, verified, committed and pushed as
`c92ec89`. The next session should start by asking the user what to build next. If the user says
"commit and push", commit the doc edits listed in §1 (they are docs only).

## 4. Work completed in this session (chronological, all verified in the repo)

1. **Guided Stock Research & Interactive Beginner Learning**: commit `defaade`. Doc: `docs/GUIDED_RESEARCH.md`.
   - Core `learning/`: `BeginnerEducation`, `GuidedResearch` (engine, content, quizzes), `LearningPresentation` (progress repository, presenter).
   - Server: `learning/LearningService.kt` (progress sync and Plus-only AI).
   - Android: `presentation/research/*`, `presentation/learn/*` (Learn hub, education sheet).
   - iOS: `GuidedResearchScenes.swift`, `IosLearningClient.kt`. The placeholder `LearnScreen.swift` was removed.
   - Follow-up fix: the quiz cards had no spacing, so `StockCard` gained a `verticalArrangement` parameter (default unchanged).
2. **Practice Portfolio** (virtual $10,000 simulator): commit `a4a4aeb`. Doc: `docs/PRACTICE_PORTFOLIO.md`.
   - Core `practice/`: models, `PracticePolicy` and `PracticeEngine`, challenges and insights, presenters.
   - Server: `practice/PracticeService.kt`, `PracticeRoutes.kt`.
   - Android: `presentation/practice/*`.
   - iOS: `PracticeScenes.swift`, `IosPracticeClient.kt`.
   - Entry points on Portfolio, Home, Learn and Company Details ("Practice Buy").
   - User-reported fix: "Buy Stock" now opens a practice search that goes straight to the order (`PracticeSearchRoute` on Android; a search mode in iOS `AppScene`).
   - MOCK vs REAL behaviour was documented on request.
3. **Daily Market Brief**: commit `c92ec89`. Doc: `docs/DAILY_MARKET_BRIEF.md`. The full file list is in §5.
4. Documentation: `CLAUDE.md`, `docs/project-status.md` (both created in `c92ec89`), and this handoff.

Earlier in the same session, before the history visible here (see the root `PROJECT_HANDOFF.md`):
- Portfolio Intelligence `bf44bd7`;
- Screener and Comparison `4c65521`;
- Earnings `af39fb4`;
- Markets spacing `54b5559`.

## 5. Files of the last feature (Daily Market Brief, `c92ec89`)

- **Core**
  - `core/src/commonMain/kotlin/org/example/stocksteps/brief/DailyBriefModels.kt` contains the models, `BriefPolicy`, `BriefContent`, `StoryRanker` and `BriefWording`.
  - `core/src/commonMain/kotlin/org/example/stocksteps/brief/DailyBriefPresentation.kt` contains `BriefRemote`/`RemoteBrief`, `DailyBriefPresenter` and `BriefFormat`.
  - Test: `core/src/commonTest/kotlin/org/example/stocksteps/brief/DailyBriefTest.kt`.
  - Changed: `core/.../network/StockStepsApi.kt` (public brief calls) and `core/.../data/userdata/UserApi.kt` (signed-in calls).
- **Server**
  - `server/src/main/kotlin/org/example/stocksteps/brief/BriefSessions.kt` contains `TsxMarketCalendar` and `BriefSessions`.
  - `DailyBriefService.kt` contains `BriefMarketSource`, `MarketsBriefSource`, `BriefScenario`, the service and `dispatch`.
  - `BriefAi.kt` contains the template provider, the Gemini provider and the validator.
  - `DailyBriefRoutes.kt`.
  - Test: `server/src/test/kotlin/org/example/stocksteps/brief/DailyBriefServiceTest.kt`.
  - Changed: `Application.kt` (wiring), `userdata/UserDataStore.kt` (brief and preference methods in all three stores) and `userdata/FirestoreUserDataStore.kt` (`dailyBriefs`, `briefPreferences`).
- **Android**
  - `app/shared/src/commonMain/kotlin/org/example/stocksteps/presentation/brief/{DailyBriefRoute,DailyBriefScenes,DailyBriefScreens}.kt`.
  - Changed: `di/AccountDependencies.kt` (`dailyBrief`), `presentation/AppNavigation.kt` (routes and `brief:<id>` deep link), `presentation/home/{HomeAction,HomeScene,HomeScreen}.kt`, `presentation/markets/MarketsScene.kt`, `app/androidApp/.../MainActivity.kt` and `account/AndroidPush.kt`.
- **iOS**
  - `app/iosApp/iosApp/DailyBriefScenes.swift` and `app/shared/src/iosMain/kotlin/org/example/stocksteps/IosBriefClient.kt`.
  - Changed: `AppScene.swift`, `HomeScene.swift`, `HomeScreen.swift`, `MarketsScene.swift` and `PushNotifications.swift`.
- **Docs:** `docs/DAILY_MARKET_BRIEF.md`, README "Daily Market Brief" section, `PROJECT_HANDOFF.md`, `CLAUDE.md`, `docs/project-status.md`.

## 6. Important implementation decisions and reasons

| Decision | Reason |
|---|---|
| Shared StateFlow **presenters in `:core`**. iOS stays native SwiftUI through `Ios*Client` bridges and `@Observable` models. | One implementation of logic and rules on both platforms, with a platform-native UI. |
| **Route / Scene / Screen** boundary on both platforms. | Screens are pure renderers; dependency resolution lives in one place (`AGENTS.md` rule). |
| **MOCK-first.** Fixtures, a pinned market clock (`2026-10-07T21:15Z`), simulated push, template AI; never a silent fallback to REAL. | Deterministic, free development; no accidental paid or production calls. |
| REAL **never fabricates**; missing → `null` and labelled with its real timestamp. | Trust for beginners. |
| Exact **`Decimal`** strings for money. Practice uses weighted-average cost, cash in cents, shares to 4 dp. | Deterministic, exact accounting. |
| **Server-enforced plans** via `EntitlementService` (one StockSteps+). Practice adds a server trial record; access is PLUS > active TRIAL > FREE. | Clients can't forge access; one subscription for the whole product. |
| Practice: every mutation in one **atomic `UserDataStore.update*`** call; idempotency keys; server-set prices; a >1% move forces a re-review. | No overspending or limit bypass under concurrency; no double fills. |
| Daily Brief: **one global brief per market edition**, built from the cached Markets overview and persisted; a **private per-user overlay**. | Bounded provider cost; no cross-user leakage in public responses. |
| Story ranking only accepts **market-relevant** stories (company link or market vocabulary); fewer is better than filler. | The live check showed political general news; the spec says "show fewer". |
| AI only on the server, **Plus checked first**, daily quotas, bounded context, **output validators**; MOCK templates are labelled "Sample". | Cost control, no paid content leaking to Free users, no hallucinated numbers, sources, advice or causes. |
| Notifications: opt-in, local delivery hour, claimed atomically before sending, no sends on days both markets are closed, existing push stack. | No duplicates or spam; reuses existing infrastructure. |
| StockSteps+ **purchase is not implemented**; paywalls state this and show no prices. | No verified store products; the rules forbid invented prices. |

## 7. Known bugs, blockers and risks

- **Blocker for REAL:**
  - The backend with the Practice, Learning and Brief routes is **not deployed**, so these return 404 in REAL.
  - Firestore credentials are required (otherwise 503).
  - `GEMINI_API_KEY` is required for REAL brief AI (otherwise 503).
  - Earnings and learning AI have no REAL provider (503).
- **The Brief scheduler isn't deployed.** Briefs generate on first request; `/internal/daily-brief/dispatch` needs Cloud Scheduler; there is no retry queue for brief pushes.
- **MOCK limitations:**
  - Practice and Brief state is in-memory, so restarting the mock server clears it.
  - ETF fixtures (SPY, QQQ) have no profile file, so Guided Research treats them as "unsupported".
  - The mock news feed contains general news, so a brief can show fewer than three stories.
- **Market calendars:** TSX early closes aren't modelled, and Practice checks TSX quote freshness with the NYSE calendar.
- The Android emulator needs `adb reverse tcp:8081 tcp:8081` again after a restart.
- **No UI-automation tests**, and no device walkthrough of the Brief (small screens, large text, screen reader).
- No known open functional bugs.

## 8. Build and test results

**Verified**
- Test-result XML in `*/build/test-results/`, written 2026-10-08 between 18:11 and 18:23 and inspected this session; all 0 failures:

  | Suite | Tests |
  |---|---|
  | core JVM | 299 |
  | server | 247 |
  | core iOS | 298 |
  | shared Android host | 55 |
  | shared iOS | 49 |

- Core JVM and server ran after the final story-relevance change. Core iOS and the shared suites ran on the full run just before it, and that change touched only core `StoryRanker` and its tests.
- The MOCK server on :8081 answers `GET /health` → 200 at handoff.

**Verified earlier in the session, before `c92ec89`, not re-run since**
- Android `assembleDebug`/`installDebug` → success.
- iOS `xcodebuild` (scheme `app.iosApp`, simulator, `CODE_SIGNING_ALLOWED=NO`) → BUILD SUCCEEDED.
- Live MOCK checks:
  - the after-close brief, with three index closes and two market stories;
  - the weekend scenario;
  - the private overlay;
  - the dispatch endpoint;
  - practice buy and sell fills.

**Never verified**
- A device walkthrough of the Brief, Practice and Guided Research UIs (only one Practice screenshot was inspected).
- UI-automation tests.
- REAL-mode end-to-end against a deployed backend.
- Real FCM/APNs delivery of brief notifications.
- REAL Gemini brief explanations.

## 9. Exact next steps for a new session

1. Run `git status` and `git log -1`. Expect `c92ec89` plus the four uncommitted doc files from §1.
   Commit them only if the user asks (message e.g. "Update session handoff docs", trailer
   `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`), updating the root `PROJECT_HANDOFF.md`
   header in the same commit.
2. If needed, start MOCK with `./gradlew :server:runMock`, plus `adb reverse tcp:8081 tcp:8081` for Android.
3. Ask the user for the next feature, or which pending item to take. The default order:
   1. Device/simulator walkthrough: Home brief card → reader → Scenarios menu → notification preferences. Only drive the UI if the user asks.
   2. Deploy the backend to Cloud Run, plus Cloud Scheduler jobs for `/internal/daily-brief/dispatch` and `/internal/alerts/evaluate` (header `X-StockSteps-Scheduler-Token` = `ALERTS_EVALUATOR_TOKEN`).
   3. StockSteps+ billing: Play Billing and StoreKit plus server receipt validation writing the `StoredEntitlement` record.
4. New features follow the established recipe:
   1. Core package: models, central policy, deterministic engine, presenter, `commonTest`.
   2. Server package: service and routes, `UserDataStore` methods (InMemory, Firestore, Unavailable), MOCK scenarios, tests.
   3. Android: `presentation/<feature>/` Route, Scene and Screen, wired in `AccountDependencies` and `AppNavigation`.
   4. iOS: `Ios<Feature>Client.kt` plus `<Feature>Scenes.swift`, wired in `AppScene.swift`.
   5. Docs: `docs/<FEATURE>.md`, a README section, and the root `PROJECT_HANDOFF.md` plus `docs/project-status.md`.
   6. Run the full test command from `CLAUDE.md`, Android `assembleDebug` and iOS `xcodebuild`.

## 10. Requirements discussed but not yet implemented

- StockSteps+ purchase, restore and pending flows, billing errors, and server receipt validation (all paywall prompts).
- A verified dividend/split source for Practice in REAL (MOCK has labelled sample events only).
- REAL AI providers for earnings and Guided Research explanations.
- Scheduled brief generation and delivery; a retry queue for brief pushes; TSX early-close modelling.
- UI tests requested in every feature prompt (Compose UI tests, XCUITest, TalkBack/VoiceOver audits, dark/light, large text).
- Optional: system Back moving between Guided Research steps; MOCK ETF profile fixtures, so the FUND path is visible.

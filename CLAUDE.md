# StockSteps — Claude Code session guide

Read this first, then `docs/project-status.md` (feature status, decisions, pending work) and
`PROJECT_HANDOFF.md` (milestone log + validation history). `AGENTS.md` has the same core rules.
Always verify against the code and `git status`/`git log` — docs can lag the repository.

## What StockSteps is

A **beginner-focused** investing education app (Android + iOS) with a Kotlin Ktor backend.
Philosophy: **NUMBER → CONTEXT → EXPLANATION → EDUCATION**. Journey: Learn → Research → Practice →
Understand. It is not a trading terminal and never gives buy/sell/hold advice, scores, ratings or
price predictions. Advanced analytics belong to a separate product, **PortIQX**.

## Current task (as of 2026-10-08)

Latest commit: "Add StockSteps+ premium earnings intelligence (Earnings Intelligence Lite Phase 5) on Android and iOS", after `87acb1d` (Phase 4).
Earnings Intelligence Lite Phases 1–5 are committed (spec: `docs/EARNINGS.md`, Phase 5 = StockSteps+ premium earnings
intelligence). No feature in progress. Read `docs/PROJECT_HANDOFF.md` (end-of-session handoff) first; next steps and
production dependencies are in `docs/project-status.md` §0 and §4. A local MOCK server may still be
running on :8081 (restart after server changes; stop with `./gradlew :server:stopMock`).

## Repository layout

| Module | Path | Contents |
|---|---|---|
| `:core` | `core/src/commonMain/kotlin/org/example/stocksteps/` | KMP shared domain/data: models, network clients (`network/StockStepsApi.kt` public, `data/userdata/UserApi.kt` signed-in), repositories, deterministic engines and **presenters** (StateFlow). Shared by server and apps. |
| `:app:shared` | `app/shared/src/commonMain/.../presentation/` | Compose Multiplatform UI (Android uses it). `di/AccountDependencies.kt` (Koin account graph, presenters), `di/StockStepsDependencies.kt` (public data graph). `iosMain/` has `Ios*Client.kt` bridges for SwiftUI. |
| `:app:androidApp` | `app/androidApp/` | Android entry (`MainActivity`), FCM (`account/AndroidPush.kt`). |
| iOS app | `app/iosApp/iosApp/` | **Native SwiftUI** (not Compose) mirroring shared presenters via `Ios*Client` bridges; `AppScene.swift` owns navigation. Xcode project uses synchronized folders (new `.swift` files are picked up automatically). |
| `:server` | `server/src/main/kotlin/org/example/stocksteps/` | Ktor backend: `Application.kt` (wiring + data sources), feature packages (`brief`, `practice`, `learning`, `earnings`, `screener`, `news`, `service`, `userdata`, `repositoryImpl/fixture`). |

Core feature packages: `brief` (Daily Market Brief), `practice` (Practice Portfolio), `learning`
(Guided Research), `earnings`, `screener`, `portfolio` (+`analytics`), `home`, `markets`, `news`,
`companydetail`, `watchlist`.

## Non-negotiable rules

- **Navigation boundary** (both platforms): Route = destination identity/args only; Scene = acquires
  presenter/ViewModel, collects state, wires navigation; Screen = renders state + action callbacks,
  never resolves dependencies.
- **MOCK-first**. `STOCKSTEPS_DATA_MODE=mock` server on :8081 uses fixtures
  (`server/src/main/resources/fixtures/`, manifest `keepMissing`) and must make **zero** paid provider,
  production Firebase, live AI or real push calls, with no silent fallback to REAL. MOCK market clock is
  pinned to fixture capture `2026-10-07T21:15Z` and advances.
- **REAL never fabricates** prices, returns, news, publishers or URLs. Missing data is `null` and shown
  as unavailable/labelled — never estimated or replaced with zero.
- **Money**: use the exact `portfolio/Decimal` (decimal strings), never Float/Double for accounting.
- **Plans are enforced server-side** via `EntitlementService` (unified **StockSteps+**; no per-feature
  subscriptions). Debug plan records only count in MOCK. Expired plans never delete user data.
- **AI**: StockSteps+ only, checked on the server *before* any provider call; daily quotas; bounded,
  source-grounded context; outputs validated (no links, invented numbers/sources, advice, predictions,
  invented causes). MOCK uses deterministic templates labelled "Sample". No AI calls from mobile.
- Secrets stay on the server (`FMP_API_KEY`, `FINNHUB_API_KEY`, `GEMINI_API_KEY`…). Never print or commit
  keys; never repeat the old FMP key from history; don't read IDE credential files.
- Don't enter credentials/passwords on emulators/simulators; don't drive the emulator UI unless asked;
  don't bypass simulator permission prompts.
- **Commit/push only when the user explicitly asks** (each time). Every commit must update
  `PROJECT_HANDOFF.md` in the same commit (completed work, validation, limits, next items), identify the
  current commit by title, and never describe committed work as uncommitted. Commit trailer:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Earnings** (permanent since Phases 1–4):
  - event/report identity is `SYMBOL:YYYY-Qn` (fiscal period, exchange-qualified symbol);
  - status comes from source data, never from the date passing;
  - EPS/revenue classification is exact decimal (MET only when equal, no tolerance) via `EarningsMath`;
  - results, insights, price reactions and reminder schedules are calculated on the server only;
  - price reactions use `PriceReactionEngine` (exchange calendars) for every screen;
  - earnings notifications go only through `EarningsReminderService`, never `AlertType.EARNINGS` alerts (the Phase 5 weekly
    digest is a `WEEKLY_DIGEST` delivery in the same pipeline);
  - Phase 5 AI: StockSteps+ verified server-side (fail closed), one `EarningsAiQuotaLedger` for every earnings AI feature,
    context only from `EarningsGrounding`, every output through `EarningsAiValidator`; history via `HistoricalEarningsEngine`.
- Match existing code style (dense Kotlin, KDoc on intent, theme tokens: `StockStepsTheme.spacing/
  colors/typography/dimensions/shapes`, `StockCard`, `StockButton`, 48dp touch targets, gain/loss in
  words not colour alone). Don't add a sixth bottom tab (Home | Markets | Portfolio | Watchlist | Learn).

## Build, run, test

```sh
./gradlew :server:runMock                     # mock backend :8081 (or installDist + STOCKSTEPS_DATA_MODE=mock PORT=8081 server/build/install/server/bin/server)
./gradlew :server:stopMock                    # stop it
adb reverse tcp:8081 tcp:8081                 # Android emulator → local mock
./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
./gradlew :app:androidApp:assembleDebug       # or installDebug
./gradlew :app:shared:compileAndroidMain      # fast Android compile check
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build   # default DerivedData; disk space is tight
```

Restart the mock server after changing server code or fixtures. Test results: `*/build/test-results/`.

## Kotlin ↔ Swift interop gotchas (seen in this repo)

- Kotlin `List<Double?>` arrives as `[Any]` → use `as? NSNumber`. `Double?`/`Int?` → `KotlinDouble?`/`KotlinInt?`.
- Properties named `description` become `description_`; `short` becomes `short_`.
- Nested classes: `LearningProgressRepository.State` (not `…RepositoryState`).
- Name clashes get a `_` suffix (e.g. two `AllocationSlice` types) — prefer unique names (`PracticeAllocationSlice`).
- Kotlin/Native: avoid JVM-only APIs (`toSortedSet`, `sortedMapOf`); cross-module properties can't be smart-cast (use locals).
- JUnit tests returning `runBlocking` need `: Unit`.

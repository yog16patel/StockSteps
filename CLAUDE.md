# StockSteps — Claude Code session guide

Read this first, then `docs/project-status.md` (feature status, decisions, pending work) and
`PROJECT_HANDOFF.md` (milestone log + validation history). `AGENTS.md` has the same core rules.
Always verify against the code and `git status`/`git log` — docs can lag the repository.

## What StockSteps is

A **beginner-focused** investing education app (Android + iOS) with a Kotlin Ktor backend.
Philosophy: **NUMBER → CONTEXT → EXPLANATION → EDUCATION**. Journey: Learn → Research → Practice →
Understand. It is not a trading terminal and never gives buy/sell/hold advice, scores, ratings or
price predictions. Advanced analytics belong to a separate product, **PortIQX**.

## Current task (as of 2026-10-09, end of session)

HEAD: "Close Phase 5C.1: fix iOS navigation hang, search and duplicate requests, finish iOS walkthroughs" (pushed; on top of the Phase 5C commit). **Phase 5C.1
(verification closure) is committed**: iOS walkthroughs A–G, critical iOS navigation hang + stuck search fixed, launch requests 19–21 → 13, minor
fixes; report `docs/PHASE5C_MOBILE_INTEGRATION_TEST_REPORT.md` §9–§16. Next: re-run the interrupted test matrix (core iOS, assembleDebug, iOS xcodebuild); redeploy the LAN backend
(`deploy/local/deploy.sh deploy`, approval); physical devices and screen readers. Cloud Run staging remains deferred. Read `docs/PROJECT_HANDOFF.md`
§0000 first. A MOCK server started by the user may be running on the Mac's :8081; the LAN MOCK backend runs on the Ubuntu host (address in git-ignored
`deploy/local/server.env`).

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
- **Provider calls and caching** (since the cost program, Phase 2):
  - all FMP/Finnhub requests go through `apiCall` (`httpclient/NetworkUtils.kt`); `ProviderCalls.record` there is the **only** place
    that counts `upstream` requests — don't add parallel `upstream` counters; BoC/Gemini call `ProviderCalls.record` themselves;
  - cache provider data with the existing `CompanyFinancialCache` at the lowest shared owner (adapter or the single service instance);
    keys must include endpoint, exchange-qualified symbol, period/frequency and row limit; don't add a second layer over an effective one;
  - failures are shared with concurrent callers and never stored as data; cooldowns only via `resultTtl`/`providerCooldown`;
    never retry 429/402/403;
  - measure provider-call changes with MockEngine benchmarks (`server/src/test/.../service/*Benchmark*Test.kt`), never REAL calls.
- **Phase 4 guards** (keep them in the path of new code): every provider-backed route belongs to an admission `RouteGroup`
  (`security/Admission.kt`); never key limits on `remoteHost` or the first `X-Forwarded-For` value (use `ClientIdentity`/`limiterKey()`); every new
  upstream call path must acquire a `ProviderGuard` permit and `complete` it (as `apiCall` does); new signed-in AI features use `DurableAiQuota`
  (reserve before the provider call; release only failures that produced nothing); per-symbol bundles go behind `SymbolExistence`; logs never contain
  provider URLs, keys or tokens (`io.ktor` stays at WARN).
- **Buttons**: primary fills use `primaryAction` (AA with white), never brand `primary`; on iOS use `.buttonStyle(.stockPrimary)` /
  `.stockSecondary`, not `.borderedProminent` (it fills with the brand-blue tint). Metric values wrap (`StockMetric`/`StockMetricGrid`);
  show readable labels for enums (`StockLabels.humanize` fallback). Visual direction: `docs/design/stocksteps-ui-reference.png`.
  Status labels use `StockStatusBadge` with a `StockBadgeKind` (MOCK/simulated = SAMPLE), title + badge rows use `StockTitleWithBadge`, banners
  `StockBanner`; badge/banner colours only via the shared `StockSemanticStyles`.
- **Sign-in/create-account screens use `StockStepsTheme`** (colours/typography); `theme/AuthTokens.kt` holds sizes only — never a
  separate hard-coded palette (it hides the screens from theme changes and dark mode).
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
- Kotlin methods starting with `new…`/`copy…` (and `alloc`/`init`) are renamed `do…` in Swift (`doNewConversation`) — name them otherwise
  (`startOver`, `addAiAnswerToNote`).
- In `Ios*Client` bridges never give a use-case property the same name as a member function: inside `scope.async { searchStocks(q) }` Kotlin calls
  the function (infinite recursion), not the property's `invoke`.
- Objects created in a SwiftUI view `init` for `@State` are re-created on every parent update (only the first is kept): no network/observation in
  their `init` — use `start: false` + `activate()` from `.task` (see `AppScene`).
- iOS simulator builds that sign in with Firebase Auth must be ad-hoc signed (`CODE_SIGN_IDENTITY=- CODE_SIGNING_ALLOWED=YES`); unsigned builds fail with
  keychain error 17995. Test sign-in only with the Firebase Auth emulator (`--firebase-emulators`; Android `-PfirebaseEmulators=true` + `adb reverse tcp:9099`).
- SwiftUI re-runs scene `init`s and evaluates `navigationDestination` builders on parent updates: models created there must not start presenters or
  clients in `init` (create on first use, subscribe from `.task`); scenes owning destinations must not declare `@Environment(\.openURL)` (render loop —
  use `ExternalURLOpener`).
- SwiftUI `Text("you@example.com")` (a string literal) is parsed as Markdown and auto-links emails/URLs — use `Text(verbatim:)` for
  placeholders and data.

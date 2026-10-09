# StockSteps — session handoff (2026-10-09, end of session: Phase 5C.1 verification closure, after Phase 5C, 5A/5B and the Phase 3/4 sessions)

Read order for a new session: `CLAUDE.md` → **§0000 below** → §000 → `docs/PHASE5C_MOBILE_INTEGRATION_TEST_REPORT.md` → `docs/LOCAL_DEVELOPMENT_SERVER.md`
→ §00 (Phase 5B Local) → §0 (Phase 5A) → the rest of this file (Phase 3/4 context) → `docs/project-status.md` §0 → root `PROJECT_HANDOFF.md` → the code.
Always start with `git status` and `git log -5 --oneline`; the repository is authoritative when docs disagree.

## 0000. Phase 5C.1 — verification closure (read first)

### Repository state
- Branch `main`. Phase 5C committed as "Fix Phase 5C mobile integration bugs and add end-to-end verification report" (`a50a2e3`) on top of the pushed
  Phase 5B Local commit; pushed. **Phase 5C.1 is committed and pushed** as "Close Phase 5C.1: fix iOS navigation hang, search and duplicate requests, finish iOS walkthroughs" (list below). The final test matrix was interrupted — see report §15.
- LAN backend runs `stocksteps-local:a50a2e3cc833` (= the 5C commit). The 5C.1 server change (`BriefWording.recentNews` used by `DailyBriefService`)
  is **not deployed** yet: after committing, `deploy/local/deploy.sh deploy` + `smoke` (needs the user's approval).

### What 5C.1 did (details: `docs/PHASE5C_MOBILE_INTEGRATION_TEST_REPORT.md` §9–§16)
- iOS walkthroughs A–G (Company Details, Screener, Comparison, Portfolio, Practice, Daily Brief, Earnings) on the spare iPhone 16 Simulator
  against the LAN MOCK backend through a counting proxy; results in report §10.
- Fixed: **critical iOS hang** opening Earnings history / price-move breakdown from Company Details (`@Environment(\.openURL)` render loop →
  `ExternalURLOpener`); stuck iOS search after fast typing; search field focus; launch requests 19–21 → 13; duplicate earnings/comparison/practice
  requests from presenters created in SwiftUI `init`s (lazy creation + `.task` activation); shared repository loading race (watchlists/alerts 2×,
  Android too); minor #1 (portfolio grouping), #2 (source link a11y labels), #3 (brief card at 1.3× font), #4 (stale iOS auth error),
  #6 (brief grammar); comparison chart axis "1.5E11". Deferred: #7/#8 MOCK fixture artefacts and the observations in report §14.
- New tests: `PortfolioFormatTest`, `WhyMovingSourceTest`, `BriefWordingTest`, `UserDataRepositoriesTest.repositoryIsLoadingFromTheMomentTheAccountIsKnown`.

### Files changed (in commit "Close Phase 5C.1: fix iOS navigation hang, search and duplicate requests, finish iOS walkthroughs")
- Core: `data/userdata/UserDataRepositories.kt`, `portfolio/Decimal.kt` (`PortfolioFormat`), `model/CompanyDetails.kt`, `brief/DailyBriefModels.kt`;
  tests above. Server: `brief/DailyBriefService.kt`. Shared: `presentation/brief/DailyBriefScreens.kt`, `presentation/companydetails/CompanyDetailsScreen.kt`,
  `iosMain/IosEarningsClient.kt`, `iosMain/IosPracticeClient.kt`.
- iOS: new `ExternalURLOpener.swift`; `AccountViewModel`, `AuthScene`, `CompanyDetailsScene`, `CompanyDetailsScreen`, `CompanyNewsViews`,
  `ComparisonHistoryViews`, `EarningsPremiumViews`, `EarningsScenes`, `MarketsScene`, `PracticeScenes`, `ScreenerScenes`, `StockSearchScene`,
  `StockSearchScreen`, `StockSearchViewModel`.
- Docs: report (§9–§16), this file, `docs/project-status.md`, root `PROJECT_HANDOFF.md`, `CLAUDE.md`.

### Rules learned (keep)
- SwiftUI evaluates `navigationDestination` builders and re-runs scene `init`s on parent updates: **never start presenters/clients in a model `init`**
  that a scene creates for `@State` — create on first use and subscribe from `.task` (pattern: `EarningsDetailsModel`, `ScreenerModel.client`).
- Scenes that own `navigationDestination`s must not declare `@Environment(\.openURL)` (render loop) — use `ExternalURLOpener`.
- Drive iOS searches from the model property (`didSet`), not a view `onChange`.

### Next steps
1. Re-run the interrupted matrix (core iOS, assembleDebug) and the iOS xcodebuild; verify #17–#20/#22 in the apps. 2. Redeploy LAN backend + smoke (approval). 3. Physical devices, VoiceOver/TalkBack, real Firebase. 4. #7/#8 fixture refresh.

## 000. Phase 5C — where the latest session stopped (read first)

### Repository state (verified at handoff)
- Branch `main`, in sync with `origin/main`. HEAD `1c1b073` "Add Phase 5B Local Docker deployment of the MOCK backend on a LAN host with smoke and
  reliability checks" (pushed). Earlier this session and pushed: `d138b19` (Phase 5A Cloud Run preparation), `4586889` (Phase 4).
- **Phase 5C is committed** as "Fix Phase 5C mobile integration bugs and add end-to-end verification report" (on top of HEAD "Add Phase 5B Local Docker deployment of the MOCK backend on a LAN host with smoke and reliability checks"): 23 modified + 4 new files (listed below; docs included). Push only when the user asks.

### Current objective
Prove the Android and iOS apps work end to end against the LAN MOCK backend (Phase 5C), fix confirmed integration bugs, document results. The
broader program: financial API cost/safety (Phases 1–4 done) → deployment preparation (5A done) → local Docker backend (5B Local done) → mobile E2E
(5C, this session) → Cloud Run staging (deferred, only on explicit request).

### Exact task at the end of the session
Phase 5C testing and fixes are finished and documented; the session ended with the user asking for this handoff. No code was being edited.

### Work completed in Phase 5C (details: `docs/PHASE5C_MOBILE_INTEGRATION_TEST_REPORT.md`)
Bugs found, root-caused and fixed (all verified):
1. **MOCK Guided Research sync always failed** → `server/.../learning/LearningService.kt`: the "progress in the future" check used the MOCK market clock
   (pinned to the Oct 7 fixture capture); new `wallClock` parameter (real time) used only for that check. Test `LearningRoutesTest.futureCheckUsesRealTimeNotThePinnedMarketClock`.
   Redeployed to the LAN host (LAN `PUT /api/v1/me/learning` with "now": 400 → 200).
2. **Every iOS search failed** ("Could not load stocks") → `app/shared/src/iosMain/.../IosStockStepsClient.kt`: `scope.async { searchStocks(query) }`
   resolved to the member function (infinite self-cancelling recursion), same for `getCompanyNews`. Properties renamed `searchStocksUseCase`,
   `companyNewsUseCase`; internal test constructor. Test `app/shared/src/iosTest/.../IosStockStepsClientTest.kt` (2, MockEngine).
3. **iOS MOCK banner covered tab bar titles** → `app/iosApp/iosApp/ContentView.swift`: banner stacked under the app (VStack) instead of `.safeAreaInset`;
   background `ignoresSafeArea(.container, edges: .bottom)`.
4. **"1 shares"** (both platforms) → `PracticeFormat.shareCount` (core) used by `PracticeOrderScreen.kt`, `PracticeScreens.kt`, `PracticeScenes.swift`,
   `PracticeEngine` sell-limit message; `IosPracticeClient.shareCount`.
5. **Holding a11y "Apple Inc.."; iOS dropped the stale-price note** → shared `PracticeFormat.holdingDescription` (both platforms). Test `PracticeFormatTest` (3).
6. **Research step a11y "?. Not started"** → `ResearchStep.accessibilityLabel` in `core/.../learning/GuidedResearch.kt`, used by
   `GuidedResearchScreens.kt` and `GuidedResearchScenes.swift` (via `IosLearningClient.stepAccessibilityLabel`). Test `ResearchStepLabelTest` (2).
7. **iOS Settings (guest) "Sign in to sync" did nothing** → `AppScene.swift` closes the Settings sheet before presenting sign-in.
8. **iOS duplicate launch requests** (screener 6 per launch) → models built in `AppScene.init` take `start: false` and are activated once in
   `.task` (`ScreenerScenes.swift`, `EarningsScenes.swift`, `EarningsReminderViews.swift`, `GuidedResearchScenes.swift`, `DailyBriefScenes.swift`);
   other call sites keep the default `start: true`. Measured screener 6 → 2 per launch.

UI verification performed (approved by the user): Android `emulator-5554` (Android 17) — Home, Markets, Details (+P/E sheet, rotation), Search,
Screener, Comparison, Portfolio (account + opening position), Practice buy/sell, Watchlist + alert, Learn/Research, Brief (+ Plus AI), Earnings,
Settings, light/1.3× font. iOS spare iPhone 16 Simulator (iOS 18.3) — signed-out/guest, sign-up mode, sign-in (Firebase Auth emulator), sign-out,
account isolation A↔B, Learn sync, Watchlist, Search, Settings sign-in. Zero StockSteps crashes; LAN `upstream` provider counter stayed 0.

### Files created or modified (in commit "Fix Phase 5C mobile integration bugs and add end-to-end verification report")
- Server: `server/src/main/kotlin/org/example/stocksteps/learning/LearningService.kt`; test `server/src/test/.../learning/LearningRoutesTest.kt`.
- Core: `core/src/commonMain/.../practice/{PracticePresentation,PracticeEngine}.kt`, `core/src/commonMain/.../learning/GuidedResearch.kt`;
  new tests `core/src/commonTest/.../practice/PracticeFormatTest.kt`, `core/src/commonTest/.../learning/ResearchStepLabelTest.kt`.
- Shared app: `app/shared/src/commonMain/.../presentation/practice/{PracticeOrderScreen,PracticeScreens}.kt`,
  `.../presentation/research/GuidedResearchScreens.kt`; `app/shared/src/iosMain/.../{IosStockStepsClient,IosPracticeClient,IosLearningClient}.kt`;
  new test `app/shared/src/iosTest/.../IosStockStepsClientTest.kt`.
- iOS: `app/iosApp/iosApp/{ContentView,AppScene,PracticeScenes,GuidedResearchScenes,ScreenerScenes,EarningsScenes,EarningsReminderViews,DailyBriefScenes}.swift`.
- Docs: new `docs/PHASE5C_MOBILE_INTEGRATION_TEST_REPORT.md`; updated `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`, root `PROJECT_HANDOFF.md`, `CLAUDE.md`.

### Build and test results
Verified (final code, 2026-10-09):
- `./gradlew :server:test :core:jvmTest :core:iosSimulatorArm64Test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test :app:androidApp:assembleDebug --continue`
  → BUILD SUCCESSFUL in 9m 19s: server 482 passed / 0 failed / 3 skipped (Firestore emulator); core JVM 421/0; core iOS 421/0; shared Android host 55/0;
  shared iOS 51/0; `assembleDebug` OK.
- `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGN_IDENTITY=- CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=YES build` → BUILD SUCCEEDED.
- LAN smoke suite last run 23/23 in Phase 5B (not re-run after the 5C redeploy; health/meta and the learning PUT were checked).
Unverified / not run: iOS walkthroughs of Screener, Comparison, Portfolio, Practice, Earnings, Brief detail, Company Details; physical devices; real
Firebase/Google sign-in; push; VoiceOver/TalkBack sessions; Dynamic Type; process-death restoration; Firestore-emulator tests (3 skipped).

### Important decisions and reasons
- Fix accessibility/copy strings in **shared core helpers** (`PracticeFormat`, `ResearchStep`) so Android and iOS read identical sentences.
- iOS model side effects deferred with `start: false` + `.task { activate() }` instead of restructuring `AppScene` (smallest change; SwiftUI keeps only the
  first `@State` value but re-runs `init`).
- Banner placement via VStack (UIKit tab bar ignores SwiftUI `safeAreaInset`).
- Auth testing only with the **Firebase Auth emulator** (`127.0.0.1:9099`, `firebase/node_modules/.bin/firebase emulators:start --only auth --project stocksteps --config ../firebase.json`);
  throwaway `@example.test` accounts; credentials never committed. Android: `-PfirebaseEmulators=true` + `adb reverse tcp:9099 tcp:9099`; iOS: launch
  argument `--firebase-emulators` and an **ad-hoc signed** simulator build (unsigned builds fail Firebase Auth with keychain error 17995).
- The LAN address stays out of the public repo (`deploy/local/server.env`, `app/iosApp/Configuration/Local.xcconfig` are git-ignored).

### Known bugs, blockers and risks
- Minor, open: three identical "Yahoo" source-link labels on Details; Brief card meta cramped at 1.3× font (Android); Portfolio totals "CAD 2145.53"
  without grouping; iOS auth error stays visible after editing the password; keychain errors map to the generic sign-in message; iOS search field
  needs a second tap to focus after the sheet opens; "1 of the 1 companies … have" grammar in Brief Plus insights; MOCK fixtures: implausible filler
  market cap, latest-quarter growth equal to annual growth.
- Earnings requests per iOS launch 5–7 — audit which endpoints.
- **User environment side effects**: the Android emulator has the LAN/auth-emulator debug build installed and the user's real-account session there
  was signed out (reinstall the normal debug build and sign in). The Mac's own MOCK server (pid started by the user) was left running on :8081.
- **LAN host**: running image `stocksteps-local:1c1b0731adb0-dirty-f8eecdbc` — verified in Phase 5C.1: its content hash over `core/` + `server/` equals the
  uncommitted 5C tree, so it already ran the tested code; a redeploy after the commit only changes the image tag to the clean commit. Still there from Phase 5A: `/tmp/stocksteps-phase5a-verify.*`, image
  `stocksteps-phase5a-verify:local`, build cache; SSH user in the `docker` group (owner decisions pending).
- Phase 4/5A conditions unchanged: Cloud Run staging deferred; owner inputs (`docs/CLOUD_RUN_DEPLOYMENT_CHECKLIST.md` §1), image vulnerability scan,
  scheduler OIDC verification, `TRUSTED_PROXY_HOPS` verification.

### Exact next steps
1. `git status` / `git diff --stat` — confirm the Phase 5C file list above; run the matrix only if code changed since.
2. When the user asks: commit Phase 5C (suggested title "Fix Phase 5C mobile integration bugs found against the LAN MOCK backend and add end-to-end
   test report"), updating the root `PROJECT_HANDOFF.md` to "committed" in the same commit; push only if asked.
3. Redeploy the LAN backend from the committed tree: `deploy/local/deploy.sh deploy`, then `deploy/local/deploy.sh smoke` (expect 23/23).
4. Finish the iOS walkthroughs marked NOT RUN (report §2/§7) on a spare simulator with ad-hoc signed builds; then physical-device checks.
5. Optional small fixes from the minor list; audit iOS launch earnings requests.
6. Cloud Run staging (Phase 5B proper) only on explicit request, after the Phase 5A owner inputs.

### Requirements discussed but not implemented
- Physical Android/iPhone end-to-end, real Firebase/Google sign-in, push delivery, VoiceOver/TalkBack and Dynamic Type sessions, process-death tests.
- Live fault injection (timeouts/429/500) on devices — covered only by existing MockEngine tests.
- Cloud Run staging deployment, App Check SDKs in the apps, image vulnerability scanning.

## 00. Phase 5B Local — where the latest session stopped

- Phase 5B Local = local integration milestone. Cloud Run staging deployment remains deferred and unverified.
- HEAD: **"Add Phase 5B Local Docker deployment of the MOCK backend on a LAN host with smoke and reliability checks"** (pushed), on top of `d138b19` "Add Phase 5A Cloud Run deployment preparation: container image, health probes, startup validation, JSON logging and staging docs". Phase 5B Local contents: `deploy/local/` (Compose, deploy/smoke/reliability scripts,
  `server.env.example`), iOS `Config.xcconfig` optional include + `Local.xcconfig.example`, `.gitignore`, two new docs, handoff/status updates.
  Git-ignored local files: `deploy/local/server.env` (LAN host), `app/iosApp/Configuration/Local.xcconfig` (LAN mock URL).
- Running: MOCK backend `stocksteps-local:d138b193ad44` on the LAN Ubuntu host (`~/stocksteps-local`, port 8081 on the LAN IP), healthy.
- Verdict **PASS WITH CONDITIONS**: remaining manual checks = Android app end-to-end (emulator/phone) and a physical iPhone
  (`LOCAL_DEVELOPMENT_SERVER.md` §5–§6). Next: the manual device checks; Cloud Run staging only on explicit request.

## 0. Phase 5A — where the latest session stopped

- HEAD: **"Add Phase 5A Cloud Run deployment preparation: container image, health probes, startup validation, JSON logging and staging docs"** on top of `4586889` (Phase 4); both pushed to `origin/main`. The commit also contains the earlier doc-only handoff edits. Nothing was deployed or created in Google Cloud; no real provider calls.
- Done: `Dockerfile` + `.dockerignore` (allow-list), `settings.gradle.kts` `-Pstocksteps.serverOnly=true`, `HealthRoutes.kt` (`/health/live`,
  `/health/ready`), `appconfig/StartupConfiguration.kt` (fail-fast validation, Cloud Run requirements), explicit shutdown grace/timeout in `main()`,
  `logging/CloudLoggingJsonLayout.kt` + `logback-{text,json}.xml` (`LOG_FORMAT`), `CloudRunReadinessTest` (8), `deploy/cloud-run-staging.yaml`,
  `docs/CLOUD_RUN_{ENVIRONMENT,SECRETS,COST_AND_SCALING,DEPLOYMENT_CHECKLIST}.md`, `docs/FINANCIAL_API_PHASE5A_IMPLEMENTATION.md`.
- Verified: `./gradlew :server:test --continue` → 481 passed, 0 failed, 3 skipped (Firestore emulator). Image built (`linux/amd64`) and
  container-verified on the owner's LAN Ubuntu server (report §6a; Docker is not installed on the Mac). Server changes made with the
  owner's approval: `docker-buildx` installed, the SSH user added to the `docker` group. Left there: a `/tmp/stocksteps-phase5a-verify.*` directory, image
  `stocksteps-phase5a-verify:local`, 2.59 GB build cache (removal pending the owner's decision).
- Verdict: **READY WITH CONDITIONS** for Phase 5B. Next:
  scan the image for vulnerabilities; owner inputs (checklist §1); verify scheduler OIDC behind
  IAM-required invocation before enabling jobs. **Do not deploy (Phase 5B) without an explicit request.**

## 1. Repository state at handoff (verified)

- (Phase 3/4 session state below is historical: Phase 4 `4586889` has since been pushed together with the Phase 5A commit.)
- Branch `main`, then one commit ahead of `origin/main` (Phase 4 committed, not pushed).
- HEAD: **`4586889` "Add financial API Phase 4: public API protection, durable AI quotas, provider budgets and usage monitoring"** (Phase 4), on top of `6a70d0e` "Verify financial API Phase 3 with before/after benchmarks and fix earnings-aware statement coverage".
- Commits made in this session: `517a46b` "Implement financial API Phase 3: screener re-warm fix, 150-company universe, selective
  statements, market- and earnings-aware freshness, stale fallback" (pushed); `6a70d0e` (above, pushed); **`4586889` Phase 4 (NOT pushed)**.
- Phase 4 = `4586889`: 51 files (31 modified, 20 added; +3,053/−138) — see §5. Working tree clean apart from this handoff's doc edits.
  Nothing was deployed; no cloud infrastructure or production Firestore was touched; no REAL provider calls. No MOCK server running.
- Shareable results document (Claude Docs, private until shared from its Share menu):
  https://claude.ai/code/artifact/c4ca8cbb-855c-4aa2-a43a-feecd79b4da9 ("StockSteps Financial API Phase 4 Results").

## 2. Current development objective

Financial API cost and safety program: reduce paid provider usage (FMP, Finnhub, Gemini, Bank of Canada) without losing financial correctness,
then make the backend safe for a controlled public launch. Phases 1–3 are done, committed and verified. **Phase 4 (production API security,
durable AI quotas, provider-wide budgets, cost monitoring) is implemented, fully tested and committed (not pushed).** Verdict: READY WITH CONDITIONS —
not launchable until the deployment-side settings in §8 are made and verified.

## 3. Exact task at the end of the session

Phase 4F verification was completed (full matrix green, provider-path audit, anonymous-pool audit and fix, older-client compatibility, §9 of the
implementation report), a shareable results doc was produced, the Phase 4 diff was reviewed (no secrets, debug output or unrelated changes; only
docs changed after the final test run) and Phase 4 was committed as `4586889` (not pushed). This handoff was then refreshed (docs only,
uncommitted). The next task is to **push `4586889` (and these doc edits, after committing them) only when the user asks**, then work through the
production configuration blockers (§8). Phase 5 has not been started and must not be started without an explicit request.

### Start-of-session checklist for the next session
1. `git status -sb` → expect `main...origin/main [ahead 1]` (or `[ahead 2]` if these doc edits were committed) and only doc changes.
2. `git log --oneline -3` → `4586889` on top of `6a70d0e`.
3. Read `docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md` §4 (configuration), §8–§9 (blockers, validation) before touching deployment settings.
4. Re-run tests only if code changes: `./gradlew :server:test` (fast), full matrix in §9 below.

## 4. Work completed in this session

### 4.1 Phase 3 implementation — commit `517a46b`
3B-0 screener re-warm fix (expired fundamentals reloaded oldest-first within `SCREENER_FUNDAMENTALS_PER_HOUR`, failure backoff, bounded maps);
3A `statementHistory` selective statements (1Y history income only); 3B-1 default universe 50 NASDAQ + 50 NYSE + 50 TSX = 150, screener dataset set
(11 requests per cold company), FMP dataset cache 4,096 entries; 3C `MarketFreshnessPolicy` (US/TSX calendars) for quotes, TTM ratios, intraday bars;
3D `EarningsStatementSignals` (2 h statement lifetime after a reported quarter until it appears); 3E labelled stale fallback (statements ≤ 7 d,
estimates ≤ 2 d), additive `CompanyFundamentals.freshness`/`staleDatasets`, Financials stale notice on Android and iOS.
Details: `docs/FINANCIAL_API_PHASE3_IMPLEMENTATION.md`.

### 4.2 Phase 3 verification — commit `6a70d0e`
Benchmark A–H rewritten (`Phase3AuditBenchmarkTest`, output `server/build/phase3-benchmark.txt`); pre-Phase 3 baseline re-measured on `e4ba2be`.
Fix: earnings "covered" rule (the 105-day fallback could mark a fast reporter's previous quarter as covered → now exact `periodEnd` or "newer than
the baseline cached when the report arrived"). Injectable wall clock for FMP retrieval times. Results: A 17→17, B 72→64, cold 1Y history 56→8,
screener first hour 328→278, 24 h 1,953 (bug) → 4,353 with full coverage, closed market 39→16, new quarter visible 19 h → 30 min.

### 4.3 Phase 4 audit (read-only) — in commit "Add financial API Phase 4: public API protection, durable AI quotas, provider budgets and usage monitoring"
`docs/FINANCIAL_API_PHASE4_SECURITY_AUDIT.md` (route inventory, findings S1–S14), `…_QUOTA_ARCHITECTURE.md`, `…_COST_MODEL.md`,
`…_OBSERVABILITY.md`, `…_DECISIONS.md` (D1–D8 register), `…_IMPLEMENTATION_PLAN.md`. Key P0s found: TRACE logging wrote FMP URLs with the
`apikey` query parameter (114,826 occurrences in one test run's output); random-symbol cost amplification (13–17 requests per unknown symbol);
watch-data fan-out (100 symbols ≈ 300 requests); no limits on public provider routes.

### 4.4 Phase 4 implementation — in commit "Add financial API Phase 4: public API protection, durable AI quotas, provider budgets and usage monitoring" (report: `docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md`)
- **4A-0 logging**: `server/src/main/resources/logback.xml` root INFO, `io.ktor` + `io.ktor.client` WARN, `io.ktor.server.Application` INFO, Google/gRPC
  WARN; separate plain-JSON appender for `StockSteps.Usage`.
- **4A public API protection** (package `security/`):
  - `ClientIdentity.kt`: verified Firebase uid → `User`; else the client IP at position `TRUSTED_PROXY_HOPS` from the right of `X-Forwarded-For`
    (IP literals only, IPv6 grouped by /64) → `Address`; else `Unverified`. Never `remoteHost`, never the first XFF value. `limiterKey()` for route
    limiters (null for unverified callers; legacy `remoteHost` only when the plugin isn't installed, i.e. route tests).
  - `Admission.kt`: `installAdmission` intercepts before routing: query ≤ 2,048 chars (414), body size per group (413; chunked without length 411),
    App Check guard, then per-`RouteGroup` admission (market data, screener, history, research, earnings, brief, public AI, premium AI, user data).
    Units = 1 per request + 1 per FMP/Finnhub/BoC call + 20 per Gemini call **actually made** (`RequestCost`, counted in `ProviderCalls.record`).
    Per-identity windows, per-identity and per-group in-flight caps, bounded state (10,000 identities/group), 429 `RATE_LIMITED` + `Retry-After`.
    **Unverified callers share one pool per group per instance that is charged only for upstream work** (cache hits free) — changed during
    verification so cheap floods can't lock guests out. Defaults in `AdmissionPolicy.DEFAULTS`; env overrides `ADMISSION_<GROUP>_…`.
  - `AppCheck.kt`: `FirebaseAppCheckVerifier` (JWKS, issuer/audience, optional app ids) + `AppCheckGuard` (monitor mode by default; counts
    `appcheck.valid|missing|invalid|unconfigured`; `APP_CHECK_ENFORCE=true` rejects; enforcement without a verifier fails startup). Not verified
    against real tokens.
  - `InternalAuth.kt`: `InternalJob` per-job secrets (`ALERTS_EVALUATOR_TOKEN`, `EARNINGS_REMINDERS_TOKEN`, `DAILY_BRIEF_DISPATCH_TOKEN`,
    `USAGE_METRICS_TOKEN`; ≥ 32 chars; a value shared by jobs disables those jobs), constant-time compare, optional Cloud Scheduler OIDC
    (`INTERNAL_OIDC_AUDIENCE`, `INTERNAL_OIDC_SERVICE_ACCOUNT`). Used by the four `/internal/…` routes (signatures unchanged).
  - `service/SymbolExistence.kt`: before fundamentals/details/valuation/movement and comparison bundles: cached profile, else quote; only a
    successful "neither exists" is negative-cached 24 h → 404 `SYMBOL_NOT_FOUND`; provider failure → `UNKNOWN` (request continues, never cached).
  - Watch-data: ≤ `WATCH_DATA_ANONYMOUS_MAX_SYMBOLS` (30) without a verified uid (`TOO_MANY_SYMBOLS`), signed-in 100, ≤ 8 symbols looked up at once
    per request; shared client `StockStepsApi.getWatchData` splits into chunks of `WATCH_DATA_CHUNK` = 30 and merges in order.
  - Existing route limiters (screener, history, brief, earnings) now key on `limiterKey()`.
- **4B durable AI quotas**: `service/AiQuota.kt` `DurableAiQuota` (feature-tagged charges in `users/{uid}/meta/aiUsage`, per-feature retention so
  features don't prune each other, idempotency keys, `settleFailure`: refunds failures that produced nothing, keeps timeouts/cancellations, store
  outage → `AiQuotaUnavailable` → 503 fail closed, optional `CombinedAiCap` disabled unless `STOCKSTEPS_PLUS_AI_DAILY_CAP`). Daily Brief AI
  (`brief-ai`, 15/day) and `EarningsAiQuotaLedger` (explanation 10, question 20, digest 3 per day; now suspend + durable; global per-day budget still
  per instance) use it. Optional `idempotencyKey` added to `BriefAiRequest` and `EarningsAiQuestion` (additive). `ComparisonAiQuota` unchanged.
- **4C provider budgets**: `service/ProviderBudget.kt` `ProviderGuard` (token bucket, burst, concurrency, priorities HIGH/NORMAL/LOW with a 30 %
  interactive reserve, slot waiting HIGH 2 s / NORMAL 5 s / LOW 10 s, circuit breaker: 429 pauses the provider for Retry-After else 30 s doubling to
  5 min; 5 consecutive 5xx/timeouts pause that endpoint; half-open single trial; 402/403 never trip). Enforced per attempt in `apiCall`
  (`httpclient/NetworkUtils.kt`), `GeminiJsonCall` (`news/GeminiInsights.kt`), `GeminiNewsSimplifier` (LOW) and `BankOfCanadaPortfolioFx`
  (`userdata/PortfolioMarketService.kt`). Denials = `StockProviderException(UNAVAILABLE)` with no upstream count → existing fallbacks (stale
  labelled data, unavailable, AI fallbacks; Practice refuses fills without a fresh quote). Screener warm-up runs LOW; alert/reminder/brief
  dispatch NORMAL. Installed globally only in REAL (`ProviderGuard.installed`); tests put a guard in the coroutine context. Config
  `ProviderBudgetConfig.fromEnvironment`: `PROVIDER_{FMP,FINNHUB,GEMINI,BOC}_{PER_MINUTE,BURST,CONCURRENCY,DAILY_TARGET}`, `PROVIDER_SAFETY_MARGIN`
  (0.8), `CLOUD_RUN_MAX_INSTANCES`; without them **development defaults** (FMP 600/min burst 600 conc. 24; Finnhub 60; Gemini 60, conc. 4; BoC 30)
  and a "not production-ready" warning.
- **4D monitoring**: `service/UsageSummary.kt` `UsageSummaryReporter` (REAL; `USAGE_SUMMARY_SECONDS`, default 60) logs one JSON line per instance
  per interval (`kind: stocksteps.usage`, counter deltas by provider/endpoint/feature/event, event deltas, gauges incl. `screener.coverage.*`) for
  logs-based metrics. `ProviderUsageMeter.gauge` + `snapshot`.

## 5. Files created or modified (Phase 4 commit)

- New main: `security/{ClientIdentity,Admission,AppCheck,InternalAuth}.kt`, `service/{SymbolExistence,AiQuota,ProviderBudget,UsageSummary}.kt`.
- Modified main: `resources/logback.xml`, `Application.kt`, `CompanyFinancialRoutes.kt`, `brief/DailyBriefRoutes.kt`, `brief/DailyBriefService.kt`,
  `earnings/{EarningsAi,EarningsPremiumService,EarningsReminderService,EarningsService}.kt`, `httpclient/NetworkUtils.kt`,
  `news/{GeminiInsights,GeminiNewsSimplifier}.kt`, `screener/{ComparisonHistoryService,ScreenerRoutes,ScreenerService}.kt`,
  `service/ProviderUsage.kt`, `userdata/{PortfolioMarketService,UserRoutes}.kt`.
- Core (KMP): `network/StockStepsApi.kt` (watch-data chunking), `earnings/EarningsPremium.kt`, `brief/DailyBriefModels.kt` (optional
  `idempotencyKey`); test `network/StockStepsApiTest.kt` (+2).
- New tests: `service/LoggingSecurityTest.kt` (2), `security/PublicApiProtectionTest.kt` (13), `service/DurableAiQuotaTest.kt` (6),
  `service/ProviderBudgetTest.kt` (10), `service/UsageSummaryTest.kt` (3). Modified tests: `brief/DailyBriefServiceTest.kt` (+1 cross-instance),
  `earnings/EarningsPremiumTest.kt` (timeouts now charged), `market/MarketSnapshotTest.kt` (thread-safe list), `service/FmpMock.kt`
  (unknown symbols, failing symbols, Finnhub events, concurrency peak), `service/Phase3AuditBenchmarkTest.kt` (24 h run under the dev budget).
- Docs: 7 new `docs/FINANCIAL_API_PHASE4_*.md`; updated `CLAUDE.md`, root `PROJECT_HANDOFF.md`, `docs/project-status.md`, this file.
- Android/iOS UI code: not changed in Phase 4.

## 6. Important decisions and reasons

- **Never trust forwarded headers without a verified hop count**: the deployed ingress is unknown; trusting the wrong XFF entry is spoofable.
  Unverified guests share a large pool charged only for upstream work, never a small proxy-keyed bucket (that throttled every guest together).
- **Admission charges actual upstream calls**, so cache hits are cheap and cold or AI-heavy requests cost what they cost.
- **Per-instance limits are not global**; the provider budget (`plan × margin ÷ maxInstances`) is the deployment-wide backstop once
  `CLOUD_RUN_MAX_INSTANCES` matches the real maximum. No Redis/Memorystore/Firestore counters for request admission (cost, contention).
- **Budget denial = provider outage semantics** so every existing fallback applies and no route can bypass a provider's budget.
- **Durable AI quotas reuse the existing `aiUsage` document** (no new collection/migration); per-feature retention; **timeouts and cancellations
  keep the charge** (the provider may have billed) — this deliberately changed the earlier "timeouts aren't charged" behaviour.
- **Combined StockSteps+ AI cap implemented but disabled** (owner decision D6). **App Check monitor mode only**, enforcement flag off.
- **Per-job scheduler secrets, fail closed** (a shared or short secret disables the route) — operator reconfiguration required.
- **Watch-data cap 30 for unverified callers** with client chunking; signed-in callers keep 100 (watchlists hold up to 100).
- Dev-default provider budget raised to FMP burst 600 and background slot waiting after the benchmark showed the screener warm-up being deferred.
- **Phase 4E shared caching deferred**: provider licensing (D3) unconfirmed.

## 7. Known bugs, blockers and risks

1. **Production configuration not done** (§8) — launch-blocking.
2. Without a trusted client IP, one abuser can still use up the shared provider budget (others get cached/stale/unavailable data, never invented
   values). Fix: `TRUSTED_PROXY_HOPS` (D1) + App Check.
3. **Older app builds** with watchlists > 30 stocks get `TOO_MANY_SYMBOLS` (mitigation `WATCH_DATA_ANONYMOUS_MAX_SYMBOLS=100`); unknown symbols now
   return 404 `SYMBOL_NOT_FOUND` on fundamentals/details/valuation/movement instead of 200 with empty data.
4. Existing deployments using one `ALERTS_EVALUATOR_TOKEN` for every job lose earnings reminders, brief dispatch and usage metrics until the new
   per-job secrets (or OIDC) are configured.
5. App Check verifier untested against real tokens; App Check SDKs not in the apps.
6. Global per-day AI budgets (Comparison 2,000; Earnings 5,000) and anonymous AI hourly budgets are still per instance.
7. Article insight results cache per instance (no shared persistence added).
8. Known flaky test (pre-existing): `PracticeServiceTest.concurrentOrdersCannotOverspendOrBypassTheLimit` (not seen this session).
9. Disk space on the dev machine is tight (~30 GB free); use default DerivedData for iOS.

## 8. Production-only configuration blockers (complete list)

| # | Item | Decision | Blocks launch |
|---|---|---|---|
| 1 | Confirm the deployed logging config; if TRACE was ever deployed, rotate `FMP_API_KEY` and review log access | — | yes |
| 2 | Verify ingress (direct Cloud Run vs load balancer) with a forged `X-Forwarded-For`, then set `TRUSTED_PROXY_HOPS` (1 or 2) | D1 | yes |
| 3 | Cloud Run max instances = `CLOUD_RUN_MAX_INSTANCES` | D4 | yes |
| 4 | `PROVIDER_*` plan limits (per minute, burst, concurrency, daily target) | D5 | yes |
| 5 | Cloud Scheduler OIDC or the four per-job secrets | — | yes |
| 6 | `WATCH_DATA_ANONYMOUS_MAX_SYMBOLS=100` if older builds are installed | — | yes, if old builds exist |
| 7 | App Check: Firebase registration, Play Integrity / App Attest-DeviceCheck, SDKs, `FIREBASE_PROJECT_NUMBER`, `APP_CHECK_APP_IDS`; enforce later | D1 | no (monitor) |
| 8 | Logs-based metrics, dashboard, alerts, billing budgets 50/80/100 % | D7 | no (advised) |
| 9 | AI policy: combined cap, per-instance AI budgets, Gemini project quota | D6 | no |

Unknown (owner input): provider plan limits and prices, Cloud Run settings, traffic, caching rights (D3).

## 9. Build and test results

Verified this session on the final Phase 4 code:
- `./gradlew :core:jvmTest :core:iosSimulatorArm64Test :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
  :app:androidApp:assembleDebug --continue` → **BUILD SUCCESSFUL in 9 m 1 s**: server **473/0 (3 skipped — Firestore emulator)**, core JVM **416/0**,
  core iOS simulator **416/0**, shared Android host **55/0**, shared iOS simulator **49/0**, `assembleDebug` up to date (built earlier from the same
  Android sources, including the Phase 4 client change). 1,409 tests, 0 failures.
- `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme app.iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator'
  CODE_SIGNING_ALLOWED=NO build` → **BUILD SUCCEEDED**.
- Phase 3 benchmark numbers unchanged under Phase 4 (A 17, B 64, B′ 8, D 278, D24 4,353, F 16, G 253, H 15→15); 24 h screener under the dev
  provider budget 4,353 requests, full coverage, 0 denials. 1,000 unknown symbols ≤ 2,000 requests (was ≈ 13,000 modeled); 429 storm 100 → 1
  upstream; five instances never exceed the plan.
Not verified: REAL FMP/Finnhub/Gemini/BoC calls, production Firestore (`aiUsage` transactions), Firestore emulator tests, Cloud Run topology/XFF,
real App Check tokens, Cloud Scheduler OIDC, device/simulator walkthroughs, logs-based metrics in Cloud Logging.

## 10. Exact next steps

1. Push the Phase 4 commit when the user asks (never push or deploy otherwise).
2. Production configuration (§8 items 1–6), each verified on the deployment (needs owner access/decisions D1, D4, D5).
3. App Check client integration (Android Play Integrity, iOS App Attest/DeviceCheck) in monitor mode; enforcement later.
4. Monitoring setup from `docs/FINANCIAL_API_PHASE4_IMPLEMENTATION.md` §10.
5. Optional follow-ups: shared Firestore store for article insights; durable global AI budgets; stale fallback for profiles/daily closes;
   market-aware watch-data quote caches; set the anonymous pool below the provider budget if guest abuse appears.

## 11. Requirements discussed but not implemented

- Phase 4E shared/persistent caching (blocked on licensing D3 and production evidence).
- App Check enforcement and client SDKs; Cloud Armor/API Gateway (not justified yet).
- Global (cross-instance) AI budgets and anonymous-AI result sharing; combined StockSteps+ AI cap activation (D6).
- Cloud Monitoring dashboards/alerts/billing budgets (documented only; no cloud resources created).
- Password reset flow (no backend support; unchanged).

## Earlier handoff (2026-10-09, morning: Comparison Phase 5 / sign-in redesign / financial API Phases 1–3 audit) — kept for history

Read order for a new session: `CLAUDE.md` → **this section** → `docs/project-status.md` §0 → the root `PROJECT_HANDOFF.md` top sections
(canonical per-commit milestone log) → the feature docs named below → the code. Always start with `git status` and `git log -5 --oneline`;
the repository is authoritative when docs disagree.

## 1. Repository state at handoff (verified)

- Branch `main`, in sync with `origin/main`, working tree clean except this documentation update (uncommitted).
- HEAD: **`e4ba2be` "Add financial API Phase 3 audit (selective loading, screener warm-up, freshness) with request benchmark"**.
- Commits made in this session (oldest → newest; all pushed):

  | Commit | Title | Size |
  |---|---|---|
  | `e27991d` | Add Company Comparison Phase 5 AI comparison assistant (StockSteps+) and redesign sign-in screens on Android and iOS | 34 files, +3,804/−312 |
  | `75760ab` | Add financial API cost audit and Phase 2 shared provider cache with request reuse | 33 files, +1,555/−71 |
  | `e4ba2be` | Add financial API Phase 3 audit (selective loading, screener warm-up, freshness) with request benchmark | 8 files, +680/−3 |

  (The session started at `423f44a`, "Add Company Comparison Phase 4 guided research checklist …".)
- A local MOCK server is **not** running (a temporary one on port 8091 was started for a smoke test and stopped).

## 2. Current development objective

**Reduce paid financial-provider usage (FMP, Finnhub, Gemini, Bank of Canada) without losing financial correctness or freshness.**
This is a phased program: Phase 1 audit (done) → Phase 2 shared cache & request reuse (done) → Phase 3 audit (done) → **Phase 3
implementation (next; nothing started)** → Phase 4 budgets/observability/durable quotas → Phase 5 verification.

**Exact task to pick up**: Phase 3 milestone **3B-0 — fix the screener re-warm bug and size the FMP dataset cache**
(`docs/FINANCIAL_API_PHASE3_IMPLEMENTATION_PLAN.md` §2). It is first because it is a correctness defect, not an optimisation.

## 3. Work completed in this session

### 3.1 Company Comparison Phase 5 — AI Comparison Assistant (StockSteps+) — in `e27991d`
Spec: `docs/SCREENER_AND_COMPARISON.md` → "Company Comparison — Phase 5".
- Core `screener/ComparisonAi.kt`: `FinancialEvidence` registry (stable ids like `AAPL.pe`, `MSFT.h.revenue.FY2025`, `I.netMargin`,
  `L.pe`), `ComparisonGrounding.build` (compact context from the cached Phase 1 comparison, Phase 2 interpretation and Phase 3 history),
  `ComparisonAiFocus` (keyword routing so a P/E question only ships P/E/P/S evidence), `ComparisonAiValidator` (evidence ids must exist,
  numbers must come from cited evidence, rejects advice/rankings/predictions/unhedged causes/links/leaks; removes unverifiable
  statements, regenerates once, then falls back to deterministic readings), `ComparisonQuestionScreen` (advice/injection answered without
  AI), `ComparisonAiSuggestions`, request/response/usage models. `ComparisonAiPresentation.kt`: `ComparisonAiPresenter` (one conversation
  per account + company selection, idempotency key reused on retry, double-tap guard, explicit note consent), `RemoteComparisonAi`;
  `UserApi.compareAiSummary/Ask/Usage`.
- Server `screener/ComparisonAiService.kt`: `ComparisonAiExplainer` (`TemplateComparisonAi` in MOCK, `GeminiComparisonAi` in REAL),
  durable `ComparisonAiQuota` (10/day, 50/rolling 30 days; env `COMPARISON_AI_DAILY_LIMIT`, `COMPARISON_AI_30_DAY_LIMIT`,
  `COMPARISON_AI_GLOBAL_DAILY_BUDGET`) via new `UserDataStore.updateAiUsage` (Firestore transaction at `users/{uid}/meta/aiUsage`),
  routes `POST /api/v1/me/compare/ai/summary`, `POST /api/v1/me/compare/ai/ask`, `GET /api/v1/me/compare/ai/usage`; shared cache for
  public summaries only; notes read server-side only with consent and never shared; token metering (`GeminiJsonCall.generateWithUsage`).
- Android `presentation/screener/ComparisonAiUi.kt`, `ComparisonAiScene` + `ComparisonAiRoute` (reuses the Compare ViewModel), Compare
  entry card, research-checklist "Ask StockSteps AI about this"; iOS `ComparisonAiViews.swift`, `IosScreenerClient` AI bridge.
- Removed the unused client-side `ComparisonExplainer`/`NoComparisonExplainer` no-op.

### 3.2 Sign-in / create-account UI redesign (Android + iOS) — in `e27991d`
- Root cause of "changes not visible": the auth screens were styled by a separate hard-coded `AuthTokens` palette, not
  `StockStepsTheme`, so theme/design-system edits never reached them and they ignored dark mode.
- Rebuilt `presentation/account/AuthScreen.kt` + `AuthComponents.kt` (Compose) and `AuthScreen.swift` + `AuthComponents.swift`
  (SwiftUI) on `StockStepsTheme` (dark `#07111C/#0D1B2A`, primary `#1683FF`, light `#F7FAFD`): header (blue "S" tile, wordmark,
  tagline), hero "Build your **investing skills**" with a decorative Canvas/Path chart, 24-dp auth card, white Google button with Google's
  "G", icon fields with visibility toggle, blue Sign in button with spinner, outlined guest action, "Create account" strip, inline error.
  `theme/AuthTokens.kt` now holds sizes only; new `StockIcons` Mail, Lock, Visibility, VisibilityOff, GoogleLogo.
- Auth behaviour unchanged (`AuthScene` untouched). There is **no password-reset feature**; "Forgot password?" keeps its existing
  "not available in the app yet" notice (no fake flow was built).
- Verified visually in the running apps: Android emulator (dark, light, 720×1280 compact, create-account mode) and iOS Simulator iPhone 16
  Pro (dark, scrolled, light). Screenshots were reviewed and then deleted at the user's request (not in the repo).

### 3.3 Financial API Phase 1 audit (read-only) — docs in `75760ab`
`docs/FINANCIAL_API_AUDIT_SUMMARY.md`, `…_ARCHITECTURE_AUDIT.md` (provider inventory, call graphs, cache inventory, security),
`…_COST_ANALYSIS.md` (scenario counts, variable cost model), `…_OPTIMIZATION_ROADMAP.md` (target architecture, P0–P2 backlog,
decisions D1–D7). One later correction: the Finnhub company-name map was already bounded.

### 3.4 Financial API Phase 2 — shared cache & request reuse (server only) — in `75760ab`
Spec/results: `docs/FINANCIAL_API_CACHE_IMPLEMENTATION.md`.
- `service/CompanyFinancialCache.kt` rewritten in place (same API): per-key single flight via an in-flight `CompletableDeferred`;
  **failures shared with joined callers and never stored**; cancelled loader hands over to waiters; expired-first then LRU trimming
  (trim runs after the new entry is marked loading — a bug the tests caught); `invalidate`, `purgeExpired`, `stats`, optional
  `cache.<name>.*` metrics. `FinancialCachePolicy` gained `SEARCH` (6 h) and `INTRADAY` (5 min).
- Duplicates removed: one Finnhub earnings history per symbol (`EarningsService.events` reads `history:SYM:w`); one 24-quarter income
  request for Financials (newest 8) and Valuation (`FmpFundamentalsLoader.load`); raw 5-min bars cached once in `FmpPriceHistoryProvider`
  for chart + sparkline; one `BankOfCanadaPortfolioFx` instance in `Application`.
- Company Details market status from `UsMarketCalendar.marketStatusAt` (no FMP `exchange-market-hours`); search cache in `StockService`;
  `EarningsService.retrying` never retries 429/402/403.
- Metering: `ProviderCalls.record` in `httpclient/NetworkUtils.kt` (`apiCall`) is the **only** place counting `upstream` requests
  (provider, endpoint without query, feature, outcome, latency); BoC and both Gemini paths record too; Gemini tokens for every caller;
  `FmpFundamentalsLoader` no longer double-counts.
- `MovementService(narrationsPerHour = 300)` cost cap on the public narration; Brief/Learning/Earnings-ask AI usage maps pruned.
- Not done in Phase 2 (deliberately): per-client rate limiting of public routes — on Cloud Run `remoteHost` is most likely the proxy
  (no forwarded-headers handling), so an IP limiter would throttle everyone together.

### 3.5 Financial API Phase 3 audit (read-only) — in `e4ba2be`
`docs/FINANCIAL_API_PHASE3_AUDIT.md`, `…_PHASE3_COST_MODEL.md`, `…_PHASE3_IMPLEMENTATION_PLAN.md`, `…_PHASE3_DECISIONS.md` and
`server/src/test/.../service/Phase3AuditBenchmarkTest.kt` (MockEngine, measures only, writes `server/build/phase3-audit-benchmark.txt`).
Measured findings (REAL adapters on a MockEngine, one instance):
- Screener warm-up: **328** upstream requests in the first hour per instance (3 universe + 25 symbols × 13).
- **Bug**: expired screener fundamentals are never re-warmed → financial filters evaluate **15 → 0** companies after 7 h.
- Comparison 1Y history: 12 requests for 4 companies (4 would do). 3Y/5Y, detailed research summary and an AI question add 0 after a comparison.
- TTM ratios/key metrics refetch every 5 min even on a Saturday; a new quarterly statement stays invisible up to 24 h.
- 50 users × 10 overlapping symbols → exactly 15 requests per symbol (single flight works per instance).
- Details/Financials need all 11 fundamentals datasets; screener and Comparison P1 need 10 of 11 (not historical annual `ratios`).

## 4. Files created or modified this session (by commit)

- `e27991d`: core `screener/ComparisonAi.kt` (new), `ComparisonAiPresentation.kt` (new), `ScreenerPresentation.kt`,
  `data/userdata/UserApi.kt`; core test `screener/ComparisonAiTest.kt` (new); server `screener/ComparisonAiService.kt` (new),
  `Application.kt`, `news/GeminiInsights.kt`, `service/ProviderUsage.kt`, `userdata/UserDataStore.kt`, `userdata/FirestoreUserDataStore.kt`;
  server test `screener/ComparisonAiRoutesTest.kt` (new); shared `presentation/screener/ComparisonAiUi.kt` (new), `ComparisonScreen.kt`,
  `ComparisonResearchUi.kt`, `ScreenerRoute.kt`, `ScreenerScene.kt`, `presentation/AppNavigation.kt`, `di/AccountDependencies.kt`,
  `presentation/account/AuthScreen.kt`, `AuthComponents.kt`, `theme/AuthTokens.kt`, `designsystem/icons/StockIcons.kt`, iosMain
  `IosScreenerClient.kt`; iOS `ComparisonAiViews.swift` (new), `ScreenerScenes.swift`, `ComparisonResearchViews.swift`, `AuthScreen.swift`,
  `AuthComponents.swift`; docs.
- `75760ab`: server `service/CompanyFinancialCache.kt`, `httpclient/NetworkUtils.kt`, `repositoryImpl/FmpFundamentalsLoader.kt`,
  `FmpPriceHistoryProvider.kt`, `FmpStockProviderRepositoryImpl.kt`, `earnings/EarningsService.kt`, `service/StockService.kt`,
  `CompanyDetailsService.kt`, `MovementService.kt`, `PriceChartService.kt`, `SparklineService.kt`, `MarketsService.kt`, `NewsService.kt`,
  `ValuationService.kt`, `screener/ComparisonHistoryService.kt`, `userdata/AlertEvaluator.kt`, `userdata/PortfolioMarketService.kt`,
  `news/GeminiInsights.kt`, `news/GeminiNewsSimplifier.kt`, `brief/DailyBriefService.kt`, `learning/LearningService.kt`, `Application.kt`;
  server tests `service/FinancialCacheTest.kt` (new, 17), `service/ProviderRequestBenchmarkTest.kt` (new); docs (5 new `FINANCIAL_API_*`,
  `COMPANY_DETAIL.md`, status/handoff/CLAUDE).
- `e4ba2be`: 4 new `docs/FINANCIAL_API_PHASE3_*.md`, `server/src/test/.../service/Phase3AuditBenchmarkTest.kt` (new), status/handoff/CLAUDE.
- This handoff (uncommitted): `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`, `CLAUDE.md`.

## 5. Important decisions and reasons

- **Phase 5 AI runs only on the server**, grounded in already-validated data with typed evidence; numbers in an answer must come from the
  evidence it cites. Reason: no fabricated figures, no client keys, auditable citations.
- **Durable AI quota for Comparison AI only** (Firestore transaction); 10/day + 50/30 days are proposals, configurable by env. Earnings/Brief
  quotas remain in memory (Phase 4 / D8).
- **Cache hits, declined questions, fallbacks and provider failures are not charged** to the user's AI allowance.
- **Auth screens must use `StockStepsTheme`**, never a separate palette (otherwise theme changes don't reach them).
- **No fake password-reset flow**: none exists in the backend.
- **Extend `CompanyFinancialCache` instead of adding a new cache layer**; failures are shared, never cached as data; cooldowns stay explicit via `resultTtl`.
- **One upstream counter** (`ProviderCalls` in `apiCall`) to avoid double counting.
- **No public-route IP limiting** until client identity behind Cloud Run is decided (proxy IP risk).
- **No new infrastructure** (no Redis/Firestore L2) until licensing (D3) and instance-count evidence (D4) exist.
- **Phase 3 order changed**: 3B-0 (re-warm bug) first because fixing it is correctness-critical — and it will *raise* screener cost relative
  to today, whose low steady state is a symptom of the bug.
- **Benchmarks use the REAL adapters on a Ktor MockEngine**; the Phase 2 "before" numbers came from running the same benchmark on an
  untouched worktree of the prior commit.

## 6. Known bugs, blockers and risks

1. **Screener re-warm bug (open, P0 correctness)**: `ScreenerService.warm()` (`server/.../screener/ScreenerService.kt` ≈ line 186) selects
   `fundamentals[it.symbol] == null`; expired entries stay in the `fundamentals` map (line 140) so they're never reloaded;
   `cachedFundamentals()` (line 173) treats them as absent → financial filters match nothing ≈ 12–18 h after first use per instance.
2. **FMP dataset cache too small for a warmed universe**: `FmpStockProviderRepositoryImpl.financialCache` (line 29) has 512 entries
   (≈ 46 symbols × 11 datasets) vs ≈ 3,300 needed for 300 symbols → LRU churn would force full re-loads (inferred).
3. **Public provider/AI routes have no per-client limits** (`/api/v1/stocks/*`, `/news`, `/news/{id}/insight` (Gemini), `/movement`
   (Gemini, now budgeted 300/h/instance), `/markets/overview`, `/stocks/watch-data`). Existing route limiters key on `remoteHost`, likely
   the Cloud Run proxy (unverified on a deployment).
4. **All caches, single flight and most budgets are per instance**; Cloud Run instance counts are unknown (no deploy config in repo).
5. **In-memory AI quotas** for Daily Brief, Earnings Phase 5 and article insights (multiply with instances).
6. **Flaky test**: `PracticeServiceTest.concurrentOrdersCannotOverspendOrBypassTheLimit` failed once under the full parallel suite and
   passed 3/3 alone; not touched this session.
7. **Licensing** of shared/persistent reuse of FMP/Finnhub data is unconfirmed (D3).
8. **REAL Gemini (Comparison P5) is not live-verified**; Firestore `aiUsage` path not run against a real project.
9. Disk space on the dev machine is tight (~30 GB free during the session); use default DerivedData for iOS.

## 7. Build and test results

Verified (run this session):
- Full suite after Phase 5 (`./gradlew :core:jvmTest :server:test :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
  :app:androidApp:assembleDebug :core:iosSimulatorArm64Test --continue`): core JVM 413/0, **core iOS simulator 413/0** (this run also
  covered the Phase 3/4 core tests that earlier sessions couldn't run on iOS), shared Android host 55/0, shared iOS 49/0, `assembleDebug`
  OK; server 366 with 1 failure (the flaky Practice test above).
- iOS `xcodebuild … CODE_SIGNING_ALLOWED=NO build`: BUILD SUCCEEDED (after Phase 5 and after the auth redesign).
- Auth redesign: `:app:shared:compileAndroidMain`, `:app:shared:testAndroidHostTest`, `:app:androidApp:installDebug` OK; real-app screenshots on emulator and simulator.
- Phase 2: `./gradlew :server:test` → 384 tests, 0 failures, 3 skipped; `FinancialCacheTest` 3/3 reruns green; MOCK server smoke test
  (details/search/sparkline/compare 200, market status `AFTER_HOURS`, zero upstream counters).
- Phase 3 audit: `./gradlew :server:test` → **385 tests, 0 failures, 3 skipped** (latest).
Not verified:
- Core/Android/iOS suites were not re-run after Phase 2 or the Phase 3 audit (no changes there).
- No REAL FMP/Finnhub/Gemini calls; no device walkthrough of the Phase 5 AI screens; no TalkBack/VoiceOver/large-font checks; no live
  MOCK curl pass of the AI endpoints (covered by route tests only); Firestore paths untested against a real project.

## 8. Exact next implementation steps

1. **3B-0 (start here)** — in `ScreenerService.warm()`: choose symbols whose cached fundamentals are missing **or expired**
   (`now() - at >= recordTtl`), oldest first, respecting `fundamentalsPerHour`; remove/replace expired map entries; make sure `records`
   built without fundamentals are refreshed once fundamentals return. Raise `FmpStockProviderRepositoryImpl.financialCache` capacity
   (configurable, e.g. env `FMP_DATASET_CACHE_ENTRIES`, default ≈ 3,500) and document memory.
   Tests: turn the "re-warm" scenario in `Phase3AuditBenchmarkTest` into an assertion test (fake `MutableClock`, universe 3 × 5,
   budget 25: coverage after 7 h must return to 15/15; budget never exceeded; oldest first). Run `./gradlew :server:test`.
2. **3A** — add `statementHistory(symbol, period, statements)` (proposed interface in the plan) on `StockProviderRepository` /
   `FmpFundamentalsLoader` (same dataset keys) and use it in `ComparisonHistoryService` (1Y → quarterly income only; 3Y/5Y → annual
   income) and the Phase 4 detailed summary. Assert 1Y = 1 request per cold company; outputs identical in MOCK.
3. **3C** — central `FreshnessPolicy` using `UsMarketCalendar` and the existing `TsxMarketCalendar` (`brief/BriefSessions.kt`; per-symbol
   choice like `earnings/PriceReactionEngine.kt`) for TTM ratios, quotes, intraday and daily closes.
4. **3B-1** (screener dataset set 13 → 10, budget sizing), **3D** (earnings-aware statement TTL), **3E** (labelled stale fallback).
5. Owner decisions needed before the security track: `docs/FINANCIAL_API_PHASE3_DECISIONS.md` D1–D8.
Each commit must update the root `PROJECT_HANDOFF.md` (per `CLAUDE.md`).

## 9. Requirements discussed but not implemented

- Per-client rate limiting of public routes; Firebase App Check; sign-in for AI explanation routes (D1/D2).
- Durable AI quotas for Brief/Earnings/article insights; one shared StockSteps+ AI allowance (D8).
- Global/per-provider request budgets; dashboards and alerts with configured prices (Phase 4).
- Shared cross-instance cache / screener snapshot / FMP bulk endpoints (gated by D3, D4, D7).
- Market-aware and earnings-aware freshness; stale-while-revalidate with UI labels (Phase 3C–3E).
- Phase 5 AI: authorized REAL Gemini acceptance run, device walkthrough, Firestore `aiUsage` check, streaming (not planned).
- Password reset / forgot-password flow (no backend support exists).
- Billing (Play Billing / StoreKit 2 + server verification) — still not implemented; upgrade buttons open Settings.
- Earlier-phase follow-ups listed in `docs/project-status.md` §4 (deploy, Cloud Scheduler, push verification, device passes).

---

## Earlier handoff (2026-10-08) — kept for history

#### StockSteps — session handoff (2026-10-08, end of the Earnings Phase 5 + Company Comparison session)

> **Update (2026-10-09, Phase 5 session):** Company Comparison Phase 5 — AI Comparison Assistant (StockSteps+) and the authentication UI
> redesign are committed as **"Add Company Comparison Phase 5 AI comparison assistant (StockSteps+) and redesign sign-in screens on Android and iOS"** (on top of the Phase 4 commit). See the root `PROJECT_HANDOFF.md` (top section),
> `docs/project-status.md` §0 and `docs/SCREENER_AND_COMPARISON.md` → "Company Comparison — Phase 5".
>
> **Update (2026-10-09, later):** Company Comparison Phase 4 — Guided Research Checklist is committed as **"Add Company Comparison Phase 4 guided research checklist (free + StockSteps+) on Android and iOS"**; see the root
> `PROJECT_HANDOFF.md` (top section).
>
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

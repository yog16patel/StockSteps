# Test results — REAL Portfolio & Practice fix (2026-10-16, uncommitted tree on `ff5662e`)

Run sequentially on the development Mac. Counts from `*/build/test-results/`.

| Suite / check | Command | Result |
|---|---|---|
| New tests | `./gradlew :server:test --tests 'org.example.stocksteps.userdata.FirestoreFailureMappingTest'` | PASS 5/5 |
| New tests against the old code (`awaitFirestore` temporarily = plain `future.get`) | same | 4/5 FAIL as expected: Portfolio oversell `500` (want 400), Practice insufficient cash `500` (want 400), unwrap and mapping unit tests (`ExecutionException` thrown). Code restored; re-run PASS |
| Server (full) | `./gradlew :server:test` | PASS — 507 tests, 0 failures, 0 errors, 3 skipped |
| Core JVM | `./gradlew :core:jvmTest` | PASS — 608 tests, 0 failures, 0 errors (tree includes another session's uncommitted core files) |
| Core iOS, shared Android/iOS | — | NOT RUN (no core/shared change) |
| Android `assembleDebug`, iOS `xcodebuild` | — | NOT RUN (no app change) |
| `deploy.sh` syntax | `bash -n deploy/local/deploy.sh` | PASS |
| Compose overlay | `docker compose config` on the host, temp dir, dummy empty files (removed) | PASS: `GOOGLE_APPLICATION_CREDENTIALS`, `FIREBASE_PROJECT_ID`, `GOOGLE_CLOUD_PROJECT`, read-only bind; without the project id: "required variable STOCKSTEPS_FIREBASE_PROJECT_ID is missing a value" |
| Ignore rules | `git check-ignore -v deploy/local/service-account.json` | PASS (`.gitignore` `*service-account*.json`) |

## Coverage of the brief's test list

| Scenario | Covered by |
|---|---|
| Firebase/Firestore initialization failure, missing credentials | `withoutStorageEverySignedInRouteAnswers503WithNeutralCopy`; existing `UserRoutesTest.missingStorageAnswers503InsteadOfCrashing` |
| Storage failures (PERMISSION_DENIED, NOT_FOUND, UNAUTHENTICATED, UNAVAILABLE, IO, timeout) | `storageFailuresAre503UserDataUnavailable` |
| Authenticated user-data access, isolation | `portfolioCreateReadAndRejectionOverFirestoreLikeStorage` (alice creates, bob doesn't see it) |
| Portfolio creation and rejection | same |
| Practice initialization, persistence, rejection | `practiceLoadsAndKeepsTransactionRejectionsOverFirestoreLikeStorage` |
| Unauthorized access | 401 assertions in the new tests; curl `GET /me/portfolio` without a token → 401 `SIGN_IN_REQUIRED` on `:8081` and `:8082` |
| Error mapping and serialization | status + `code` + message asserted in the new tests; `ApiLoggingTest` (core) unchanged |
| MOCK/REAL isolation | unchanged architecture (separate containers/stores); Pixel 7 REAL log shows only `:8082` requests |

## Device evidence

| Check | Result | Evidence |
|---|---|---|
| Pixel 7 REAL before (meta 200; portfolio, practice, all `/me/*` 503; personalized 500; no 401) | PASS (reproduced) | `captures/pixel7-real-before.log` (LAN address replaced by `<lan>`; redaction scan: 0 JWT, Bearer value, e-mail, `mock-user:`) |
| REAL deploy (clean `ff5662e` worktree + this change; image `ff5662ecba3e-dirty-b20aa97d`) health/ready/meta | PASS | `captures/deploy-real.log` |
| Pixel 7 REAL after: all `/me/*` GETs 200, 0 × 503 | PASS | `captures/pixel7-real-after.log` |
| Pixel 7 reload after force-stop: `/me/portfolio` 200 | PASS | `captures/pixel7-real-reload.log` |
| REAL Portfolio create / Practice order | NOT RUN | writes need explicit permission |
| iOS REAL signed in | BLOCKED | simulator in guest mode |

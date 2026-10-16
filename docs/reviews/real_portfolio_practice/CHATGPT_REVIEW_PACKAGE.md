# ChatGPT review package — REAL backend My Portfolio & Practice fix

## 1. Phase and Git baseline
- Phase: REAL Portfolio & Practice on the LAN Ubuntu backend (physical Android). Report: `docs/REAL_PORTFOLIO_PRACTICE_FIX.md`.
- Base: `ff5662e` "Fix iOS landing content under the status bar and shorten company page titles for UI/UX Refinement Phase 2 (KAN-16)…".
- Under review: commit "Fix REAL My Portfolio and Practice on the LAN backend with Firestore user data, unwrapped transaction rejections, 503 for storage failures and an opt-in read-only service-account mount" (server, deploy and docs files only; pushed). Another session's uncommitted Company Details files (core, shared, iOS) belong to
  that session and are not part of this change.

## 2. Changes implemented
- **Root cause (configuration, confirmed on the running container):** the REAL container has no Google credentials, so the server uses
  `UnavailableUserDataStore` and every `/api/v1/me/*` route (portfolio, practice, watchlists, alerts, entitlements, learning…) answers
  503 `USER_DATA_UNAVAILABLE`. Auth, networking and client handling are fine (token accepted, no 401).
- **Server fixes (needed before credentials help):** Firestore transaction rejections are rethrown unwrapped (Portfolio 400 / Practice
  400·403·409 instead of 500); Firestore storage failures → 503 with a type/status-only log; Daily Brief personalized 500 → 503
  (StatusPages maps `UserDataException`); neutral 503 copy instead of "Watchlists and alerts…"; startup warning names the exception type.
- **Deployment (prepared, inert):** an opt-in Compose overlay mounting an owner-placed, least-privilege service-account key read-only;
  `deploy.sh` checks the file's owner and mode only. Not enabled or deployed.

## 3. Affected files
- server: `userdata/FirestoreUserDataStore.kt`, `userdata/UserDataStore.kt`, `ApiErrorHandling.kt`, `Application.kt`;
  test `userdata/FirestoreFailureMappingTest.kt` (new).
- deploy: `deploy/local/docker-compose.real.userdata.yml` (new), `deploy/local/deploy.sh`, `deploy/local/server.env.example`; `.gitignore`.
- docs: `docs/REAL_PORTFOLIO_PRACTICE_FIX.md`, this package, `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`.
- core / shared / Android / iOS: none.

## 4. Architectural decisions
- Map Firestore exceptions at the lowest owner (`FirestoreUserDataStore`), so services and routes see the same exceptions as with the
  in-memory store; no route-by-route catches.
- Keep `UnavailableUserDataStore` (fail closed, no in-memory fallback on REAL; no MOCK fallback).
- StatusPages maps `UserDataException` as a safety net; existing route-level catches stay.
- Credentials: mounted file, owner-created, `roles/datastore.user` only; never in Git, the build context, the apps or an endpoint.
- Log only exception type and gRPC status code (messages can name documents/projects).

## 5. Test and build results (details: `CHATGPT_TEST_RESULTS.md`)
- `:server:test` PASS 507 / 0 failures / 3 skipped. `FirestoreFailureMappingTest` PASS 5/5; with the old `get()` 4/5 FAIL (proves the
  tests catch the defect). `:core:jvmTest` PASS 608 / 0.
- iOS core/shared tests, Android and iOS builds: NOT RUN (no core/shared/app change).

## 6. Android verification (Pixel 7, physical, debug build, REAL `<lan>:8082`, owner's signed-in session)
- Before: PASS (reproduced) — meta 200; `GET /me/portfolio` 503; `GET /me/practice` 503; all `/me/*` 503; personalized brief 500; no 401.
- After (deployed 2026-10-16, owner key + `roles/datastore.user`, report §10): PASS — every `/me/*` GET 200 incl. portfolio, practice,
  practice/performance and the personalized brief; 0 × 503; My Portfolio empty state without error; Practice initialized ($10,000, 0 of 3);
  portfolio 200 again after force-stop. Portfolio create and Practice orders NOT RUN (writes need permission).

## 7. iOS verification
- BLOCKED: simulator in guest mode; no iOS code changed; the same server cause applies.

## 8. Known issues
- `SIGN_IN_REQUIRED` copy still says "Sign in to use watchlists and alerts." on every route (not changed).
- REAL `screener/catalog` 503 `SCREENER_UNAVAILABLE` (warm-up off; separate).
- The only configured Firebase project is `stocksteps`; a key for it reaches that project's Firestore.

## 9. Unverified scenarios
- REAL Portfolio create and summary with an account; Practice orders; Practice reload after restart — NOT RUN (writes need explicit
  permission; Practice wasn't reopened after the restart).
- Real Firestore emulator or project run of `awaitFirestore` — NOT RUN (the wrapping is reproduced with `ApiFutures.immediateFailedFuture`,
  which is how `ApiFuture.get()` reports a failed transaction).
- Overlay deploy with the real key — PASS (report §10; deployed from a clean `ff5662e` worktree).

## 10. Deviations from the brief
- The brief named `:8081` as the REAL server; it is MOCK. REAL is `:8082`.
- Stopped at the credential / IAM / redeploy boundary as instructed; no Portfolio create or Practice order was sent.

## 11. Decisions requiring owner approval
1. Use project `stocksteps` for LAN REAL user data, or create a separate dev Firebase project.
2. Create the service-account key (`roles/datastore.user` only) and place it on the host (report §6).
3. Redeploy the REAL stack with this tree; then the signed-in device pass with an approved test account.
4. Commit (paths listed in §3; never `git add .`).

## 12. Links
- Report `docs/REAL_PORTFOLIO_PRACTICE_FIX.md`; focused diff `CHATGPT_FOCUSED_DIFF.md`; tests `CHATGPT_TEST_RESULTS.md`;
  evidence `captures/` (git-ignored: redacted Pixel 7 log, two screenshots). No contact sheets: no UI change.

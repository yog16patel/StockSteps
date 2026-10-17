# Authorization and ownership review — Phase 9A

## 1. Model
- The only owner of any user document is the verified uid from the principal. No route, body field, query or header can name another
  owner (audited: every `/api/v1/me` handler passes `uid` from `user(auth)` / `signedIn` to its service; no service accepts an owner from input).
- Firestore paths derive from the uid (`FirestoreUserDataStore`): `users/{uid}/portfolio/ledger`, `users/{uid}/practice/account`,
  `users/{uid}/watchlists/*`, `users/{uid}/meta/{learning,entitlements,aiUsage,screens,watchlists,comparisonResearch}`,
  `users/{uid}/comparisonResearch/*`, `briefPreferences/{uid}`, `earningsReminders/{uid}`, `pushDevices/{uid}|{deviceId}`.
  Top-level `alertRules` / `alertEvents` documents carry `ownerUid`; every read and update filters or checks it in the same transaction.
- Resource ids from clients are only looked up **inside the caller's own documents**, so another user's id is simply unknown: My Portfolio
  answers 400 `INVALID_PORTFOLIO` ("does not exist"), watchlist entries and alerts 404, research sessions 404. Deletes are idempotent on the
  caller's own data (deleting another user's id is a no-op, 200/404). Nothing ever reveals that the id exists for someone else.
- 9A hardening: the uid itself is validated (no `/`, whitespace, control characters, ≤ 128) so it can't change a Firestore path.
- Portfolio and Practice are separate documents and services; Practice idempotency keys are stored **per account** (B reusing A's key creates
  B's own order, verified on the emulator).
- Guest data is never merged: guests have no server-side user data (recent searches stay on the device, Phase 5D.5.5C); sign-in reads the
  account's own server documents. No 9A change.
- StockSteps+ and AI quotas are checked on the server (`EntitlementService`, `DurableAiQuota`) before any provider call (unchanged).
- Admin/internal: `/internal/*` needs Cloud Scheduler OIDC for the configured service account or the job's own ≥ 32-char secret (constant
  time); REAL doesn't register them without one. MOCK leaves them open locally (unchanged, documented).
- Debug routes (`PUT /api/v1/me/entitlements/debug`, `PUT /api/v1/me/practice/debug/scenario`, personas) exist only in MOCK; the services refuse
  them too.

## 2. Two-user verification on the Firebase emulators (PASS)
`FirebaseEmulatorTwoUserIsolationTest` (Auth + Firestore emulators, project `demo-stocksteps-9a`, real routes behind the production security
stack, real `FirestoreUserDataStore`):

| Attack | Result |
|---|---|
| Guest GET/PUT on Portfolio, Practice, Watchlists, Alerts, Learn | 401, nothing written |
| B reads My Portfolio after A created account + deposit | empty ledger |
| B opens A's account summary by id | not 200 |
| B deletes / edits A's transaction by id | 400, A's transaction unchanged |
| B creates an account with A's account id | B's own account; A's name unchanged |
| B reads Practice after A's buy | no transactions, no holdings |
| B executes with A's idempotency key | B's own fill (`replayed = false`); A still has 1 trade |
| B patches / deletes A's watchlist entry | 404 / no-op; A keeps the entry |
| B patches / deletes A's alert | 404 / no-op; B lists none, A keeps 1 |
| A and B order concurrently | each ledger has exactly its own trades |
| A's account deleted in the Auth emulator | A's token → 401 (emulator-verifier behaviour; see AUTHENTICATION_REVIEW §5) |

Also PASS on the emulator: the Phase 7D `FirestorePortfolioPracticeIntegrationTest` (5 tests: persistence across restarts, concurrent orders
never overspend or fill twice, unknown-outcome retry keeps its request id).

## 3. Route-level isolation already covered (unchanged, re-run)
`UserRoutesTest.routesRequireSignInAndKeepUsersApart`, `PortfolioRoutesTest`, `ComparisonResearchRoutesTest`, `LearningRoutesTest`,
`PracticeServiceTest`, `EarningsPremiumTest` (plan checks before AI), `PortfolioAnalyticsRoutesTest`.

## 4. Findings
- No cross-user read or write path was found.
- Low: re-registering a push token under another account removes the earlier account's link to it (intended for account switches on one
  device; requires knowing the device's FCM token). Documented in `KNOWN_LIMITATIONS.md`.
- 403 is reserved for plan/admin/anonymous denials; ownership never needs it (ids are only resolved inside the caller's own data).

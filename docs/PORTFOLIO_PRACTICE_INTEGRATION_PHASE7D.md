# Phase 7D — Portfolio & Practice integration verification

Date: 2026-10-17. Baseline: `b2b23fe` (Phase 7C, committed and pushed; **deployed to LAN REAL 2026-10-17 02:18Z**, image `b2b23fe095bb`,
built from the clean commit). Phases 7A (`c57135a`), 7B (`b9f871a`) and 7C are all deployed; their ChatGPT reviews are pending.
**This phase:** verification, new tests and **one narrow client fix (D2)**; committed and pushed as "Verify Portfolio and Practice integration for Phase 7D on a local Firestore emulator through real routes, keep the Practice order request id after an unknown-outcome storage failure so a retry cannot fill twice, and add token-verifier and hand-calculated accounting tests".

**Verdict: READY FOR REVIEW**, with the device and live-data rows BLOCKED (§6). Review package:
`docs/reviews/portfolio_practice_integration_phase7d/`.

## 1. Evidence levels used

| Level | Meaning in this report |
|---|---|
| Code inspection | read in the source at `b2b23fe` |
| Mock/unit | in-memory store, fakes, MockEngine |
| **Emulator** | real `FirestoreUserDataStore` + real Ktor routes on a **local Firestore emulator** (`cloud-firestore-emulator-v1.22.0.jar` from the local Firebase CLI cache, `127.0.0.1:8189`, project `demo-stocksteps-7d`; no network, no production credentials; started and stopped by this task only) |
| REAL backend | LAN REAL `:8082` (b2b23fe) |
| Device | physical Android / iOS simulator |

## 2. Results summary

| Area | Result | Level and evidence |
|---|---|---|
| Firestore emulator tests | **PASS**: the 3 previously skipped news-store tests and 5 new Portfolio/Practice integration tests run and pass (0 skipped) | Emulator; `CHATGPT_TEST_RESULTS.md` |
| Persistence across store/service recreation | PASS for Practice (buy, partial and full sell) and My Portfolio (account, transaction, summary) | Emulator, `FirestorePortfolioPracticeIntegrationTest` |
| Atomicity and idempotency on Firestore | PASS:<br>• two competing buys → one fill, one `INSUFFICIENT_CASH`;<br>• two competing sells → one fill, one `INSUFFICIENT_SHARES`;<br>• 10 same-key requests → 1 fill;<br>• 10 distinct keys never overspend, and every fill is in the recreated ledger;<br>• a lost response replays;<br>• a failed write → 503, nothing written, and the retry fills once | Emulator |
| Auth and isolation | PASS:<br>• missing, invalid or expired tokens → 401, the token never echoed;<br>• user B can't see A's ledger or account (404);<br>• the uid comes only from the verified token (no client uid in any route) | Emulator + `FirebaseTokenVerifierTest` (REAL verifier offline: malformed, other project or issuer, expired) + REAL `:8082` guest and forged-token 401 |
| Accounting | PASS against hand calculations | `PracticeAccountingReferenceTest`, emulator test, `PortfolioEodTest` (`FINANCIAL_ACCURACY_MATRIX.md`) |
| EOD and FX | PASS (mock) | `EOD_AND_FX_VERIFICATION.md` |
| Ktor HTTP | PASS: real routes with JSON bodies, statuses and codes | Emulator test (preview/execute/overview/transactions, portfolio put/post/get/summary) |
| Android / iOS builds | PASS | `CHATGPT_TEST_RESULTS.md` |
| Device passes | **BLOCKED** | `DEVICE_TEST_MATRIX.md` |

## 3. Defects found

| # | Defect | Severity | Action |
|---|---|---|---|
| **D2** | **Unknown outcome dropped the request id.** Under same-user contention on the emulator (10 concurrent orders, full parallel suite), Firestore aborted or timed out transactions: the order answered 503 `USER_DATA_UNAVAILABLE`. A timed-out write may still have been saved. The Practice order presenter dropped its idempotency key for every coded error, so confirming again sent a **new key**, and a saved first attempt could fill **twice** | **High** (duplicate simulated trade) | **Fixed** (core `PracticeOrderPresenter.confirm`): `USER_DATA_UNAVAILABLE` is treated like a network failure, so the key is kept, the screen stays on Review, and the copy says "Your order wasn't confirmed; trying again won't place it twice." Regression test in `PracticePresenterTest`. The emulator test retries 503s with the same key and asserts no key filled twice |
| F3 | Same-user contention: 10 simultaneous orders on one Practice document can exceed Firestore's transaction retries or the 15 s store timeout → 503 | Low (honest failure; a person can't place 10 orders at once) | Documented; safe with D2 (same-key retry) |
| D1 | The shared 401 (`requireUser`) says **"Sign in to use watchlists and alerts."** on Portfolio and Practice routes too (seen live on REAL `:8082`) | Low (copy; misleading) | **Not fixed**: copy change needs owner approval; proposed wording "Sign in to continue." |
| — | No accounting, isolation or EOD defect was found; server-side atomicity held (no negative cash or shares, no lost or duplicate fill per key) | — | — |

Production code changed: `core/.../practice/PracticePresentation.kt` (D2, two lines + comment). The rest is tests (§4).

## 4. Files (tests and docs only)

- server tests (new):
  - `userdata/FirestorePortfolioPracticeIntegrationTest.kt` (emulator; skipped without `FIRESTORE_EMULATOR_HOST`);
  - `userdata/FirebaseTokenVerifierTest.kt`;
  - `practice/PracticeAccountingReferenceTest.kt`.
- core: `practice/PracticePresentation.kt` (D2); test `practice/PracticeEngineTest.kt` (`PracticePresenterTest` storage-failure retry).
- docs: this report, the review package, handoff and status.

## 5. How to rerun the emulator tests

```sh
java -jar ~/.cache/firebase/emulators/cloud-firestore-emulator-v1.22.0.jar --host 127.0.0.1 --port 8189 &
FIRESTORE_EMULATOR_HOST=127.0.0.1:8189 ./gradlew :server:test --rerun
```

The tests use a `demo-` project id and random uids, and delete their documents afterwards. Stop the emulator by its own PID.

## 6. Blocked

| Item | Reason |
|---|---|
| Physical Android / iOS simulator REAL passes (sign in, buy/sell, restart, reload) | needs the owner's approved test account (no credentials are entered by Claude) |
| Live EOD values on REAL | the FMP key answered 429 to every request in the last live check (7B); not re-probed |
| Firebase Auth emulator end to end (real ID tokens) | the Auth emulator runs only with owner approval (`CLAUDE.md`); the verifier was tested offline instead |
| Production Firestore rules | not touched; the server uses the Admin SDK (rules don't apply to it); `firebase/tests/watchlist.rules.test.mjs` covers client rules (not run) |

## 7. Remaining launch blockers

`REMAINING_LAUNCH_BLOCKERS.md`: the FMP display licence and quota, corporate actions, EOD adjustment basis and publication delay, mixed FX
dates, device verification, and the D1 copy.

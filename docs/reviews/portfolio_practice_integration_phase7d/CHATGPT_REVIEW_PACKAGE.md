# ChatGPT review package — Phase 7D Portfolio & Practice integration verification

## 1. Phase and Git baseline
- **Phase:** 7D, integration verification.
- **Baseline:** `b2b23fe` (Phase 7C, pushed; deployed to LAN REAL 2026-10-17 02:18Z, image `b2b23fe095bb` from the clean commit).
  7A and 7B are deployed too.
- **Under review:** "Verify Portfolio and Practice integration for Phase 7D on a local Firestore emulator through real routes, keep the Practice order request id after an unknown-outcome storage failure so a retry cannot fill twice, and add token-verifier and hand-calculated accounting tests" (committed and pushed; not deployed).

## 2. Changes
- **D2 fix:** the Practice order screen keeps its request id after a 503 `USER_DATA_UNAVAILABLE` (unknown outcome), so a retry can't
  fill twice.
- **New tests:** Firestore-emulator integration through real routes; offline REAL token-verifier tests; a hand-calculated accounting
  ledger.
- **Docs.**

## 3. Files
- core: `practice/PracticePresentation.kt`; test `practice/PracticeEngineTest.kt`.
- server tests (new): `userdata/FirestorePortfolioPracticeIntegrationTest.kt`, `userdata/FirebaseTokenVerifierTest.kt`,
  `practice/PracticeAccountingReferenceTest.kt`.
- docs: `docs/PORTFOLIO_PRACTICE_INTEGRATION_PHASE7D.md`, this folder, handoff and status.

## 4. Key findings
- **Firestore (emulator):** persistence across recreation, user isolation and atomicity hold.
- **Concurrency:** no negative cash or shares; one fill per key; no lost fills.
- **D2 (high):** an unknown-outcome retry could create a duplicate Practice fill. Fixed and regression-tested.
- **F3 (low):** same-user contention can yield 503.
- **D1 (low):** the 401 copy says "watchlists and alerts" (not fixed; copy approval).
- **Accounting:** matches hand calculations to the cent.

## 5. Tests and builds
- core 615/0; core iOS PASS on rerun (known logging flake); server 565/0, 0 skipped (emulator); shared 284/0 and 281/0.
- assembleDebug PASS; xcodebuild PASS.

## 6–7. Android / iOS
BLOCKED (`DEVICE_TEST_MATRIX.md`).

## 8. Known issues and limits
- The emulator isn't production (latency, quotas). Security rules aren't exercised (the Admin SDK).
- FMP 429 prevents a live EOD check.

## 9. Unverified
- A valid Firebase ID token end to end (Auth emulator approval).
- Devices.
- Live EOD.

## 10. Deviations from the brief
- **Firebase Auth:** not used through its emulator; the real verifier was tested offline instead.
- **Emulator:** started from the cached jar directly (no `firebase emulators:start`, no `firebase.json`), on loopback.

## 11. Owner decisions
- D1 copy.
- The corporate-action launch gate (`REMAINING_LAUNCH_BLOCKERS.md` #3).
- Mixed FX dates.
- The device pass.
- Commit, push and deploy.

## 12. Links
`FIRESTORE_EMULATOR_VERIFICATION.md`, `AUTH_AND_USER_ISOLATION.md`, `FINANCIAL_ACCURACY_MATRIX.md`, `EOD_AND_FX_VERIFICATION.md`,
`DEVICE_TEST_MATRIX.md`, `REMAINING_LAUNCH_BLOCKERS.md`, `CHATGPT_FOCUSED_DIFF.md`, `CHATGPT_TEST_RESULTS.md`.

# Phase 7D — Firestore emulator verification

## Why tests were skipped before
- The 3 skipped server tests were `FirestoreNewsSimplificationStoreTest`.
- They call `assumeTrue(FIRESTORE_EMULATOR_HOST set)`, and no run had set it.
- No Firestore-backed test existed for Portfolio or Practice.

## Setup (local only)
- Emulator: `java -jar ~/.cache/firebase/emulators/cloud-firestore-emulator-v1.22.0.jar --host 127.0.0.1 --port 8189`, the jar already
  cached by the project's Firebase CLI.
- No download, no network, no Firebase login.
- Readiness was checked by an `Ok` answer from the emulator's own HTTP root, not by an open port.
- Tests use project `demo-stocksteps-7d` (`setEmulatorHost`), random uids, and delete their documents.
- The emulator was stopped by its recorded PID after the runs.

## Results

| Test | Scenario | Result |
|---|---|---|
| `FirestoreNewsSimplificationStoreTest` (3) | claim, save, expiry (previously skipped) | PASS |
| `practiceLedgerPersistsAcrossRecreationWithCorrectCashAndRealizedGain` | Through HTTP and then a **new store and service**:<br>• initialize;<br>• preview (110, EOD basis, close date);<br>• buy 2 → cash 9,703.00;<br>• recreated reads the same;<br>• partial sell at 120 → cash 9,865.00, realized 13.50;<br>• full sell → cash 10,027.00, no holdings;<br>• 3 transactions; HTTP and the recreated service agree | PASS |
| `concurrentOrdersOnFirestoreNeverOverspendOversellOrFillTwice` | • 2 competing buys of 5,940 CAD vs 10,000 → 1 fill + `INSUFFICIENT_CASH`;<br>• 2 competing sells of 30 vs 40 shares → 1 + `INSUFFICIENT_SHARES`;<br>• 10 same-key requests → 1 fill;<br>• 10 distinct keys: each a fill, `INSUFFICIENT_CASH`, or 503 `USER_DATA_UNAVAILABLE` under contention (seen in the parallel full-suite run: ABORTED and the 15 s timeout); 503s are retried with the same key; then no negative cash, no key filled twice, every fill in the recreated ledger;<br>• lost-response retry replays | PASS (first full-suite run: FAIL on the original assertion that only `INSUFFICIENT_CASH` could fail. That revealed D2. Reruns after the fix: PASS) |
| `aFailedWriteIsReportedAndTheRetryFillsOnce` | the order's write fails once → 503 `USER_DATA_UNAVAILABLE` (no transaction in the body, nothing stored); the retry with the same key fills once | PASS |
| `myPortfolioPersistsAndIsIsolatedPerUser` | • account + transaction through HTTP;<br>• summary 297.00 CAD at the dated close (`pricesAsOf` 2026-10-16), basis 270.00;<br>• recreated store reads the ledger;<br>• user B: no AAPL, A's account → 404;<br>• user-entered values unchanged | PASS |
| `missingOrInvalidTokensAreRejectedWithoutLeakingDetails` | missing or forged token on practice, portfolio and execute → 401 `SIGN_IN_REQUIRED`, the token not echoed | PASS |

## Limits
- **Emulator vs production:** the emulator implements Firestore transactions with the same optimistic-concurrency semantics as the
  client library. Production latency and contention are not measured.
- **Security rules:** not exercised. The server uses the Admin SDK.

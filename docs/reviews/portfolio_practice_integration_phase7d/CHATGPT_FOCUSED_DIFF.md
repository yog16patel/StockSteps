# Phase 7D — focused diff

Only one production change. The rest is tests.

## D2 — keep the request id when an order's outcome is unknown (core `practice/PracticePresentation.kt`, `PracticeOrderPresenter.confirm`)

```kotlin
when (errorCode(cause)) {
    "PRICE_CHANGED" -> { key = null; … refreshPreview() }
    "HOLDING_LIMIT" -> …
    // No answer, or the server couldn't reach its storage (a timed-out write may still have been saved): the outcome is
    // unknown, so the request id is kept — trying again replays a saved order instead of placing a second one (Phase 7D).
    null, "USER_DATA_UNAVAILABLE" -> mutable.update { it.copy(executing = false, error = "${message(cause)} Your order wasn't confirmed; trying again won't place it twice.") }
    else -> { key = null; … }   // definite rejections (INSUFFICIENT_CASH, QUOTE_STALE, …): a new review gets a new id
}
```

- **Why:** `FirestoreUserDataStore` answers 503 `USER_DATA_UNAVAILABLE` for an aborted transaction and also for a timed-out one
  (`awaitFirestore`, 15 s). A timed-out commit can still land. Before the fix, the next confirm used a **new** idempotency key, so the
  server couldn't recognize the retry and would fill a second time.
- **Risk:** low. A definite failure that reuses the key would get `IDEMPOTENCY_CONFLICT` only if the request changed. For the same order
  the server replays or executes once.
- **Both apps:** this is shared core, so Android and iOS both get the fix with no UI change. The existing message and the Review step are
  reused.
- **Tests:**
  - `PracticePresenterTest.orderFlowReusesItsKeyOnRetryAndRereviewsAfterAPriceMove`: a 503 `USER_DATA_UNAVAILABLE` keeps the key and the
    REVIEW step;
  - `FirestorePortfolioPracticeIntegrationTest.concurrentOrdersOnFirestoreNeverOverspendOversellOrFillTwice`: same-key retries of 503s;
    no key filled twice; ledger = fills.

## New tests (no production effect)

- `FirestorePortfolioPracticeIntegrationTest` (emulator, 5): real routes, the real store, and recreation.
- `FirebaseTokenVerifierTest` (3): the REAL verifier rejects malformed, other-project, other-issuer and expired tokens offline.
- `PracticeAccountingReferenceTest` (1): hand-calculated ledger.

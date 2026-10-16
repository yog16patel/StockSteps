# Focused diff — REAL Portfolio & Practice fix

Applies because the change touches persistence error handling, error mapping and credential handling (`AI_REVIEW_WORKFLOW.md` §3.4).

## 1. Firestore futures: unwrap transaction rejections, map storage failures (`FirestoreUserDataStore.kt`)

```kotlin
private fun <T> ApiFuture<T>.await(): T = awaitFirestore(this, OPERATION_TIMEOUT_SECONDS)

internal fun <T> awaitFirestore(future: ApiFuture<T>, timeoutSeconds: Long): T = try {
    future.get(timeoutSeconds, TimeUnit.SECONDS)
} catch (cause: java.util.concurrent.ExecutionException) {
    val failure = cause.cause ?: cause
    throw if (isStorageFailure(failure)) storageUnavailable(failure) else failure
} catch (cause: java.util.concurrent.TimeoutException) {
    throw storageUnavailable(cause)
}

private fun isStorageFailure(cause: Throwable): Boolean =
    cause is com.google.cloud.BaseServiceException || cause is com.google.api.gax.rpc.ApiException ||
        cause is io.grpc.StatusRuntimeException || cause is io.grpc.StatusException || cause is java.io.IOException ||
        cause is java.util.concurrent.TimeoutException
// storageUnavailable: logs "type=<class> status=<grpc code>" only, returns UserDataException(503, "USER_DATA_UNAVAILABLE", …)
```

- Decision: one mapping at the store, so `PortfolioService` (`IllegalArgumentException` → 400) and `PracticeService` (`UserDataException`
  inside `updatePractice`) behave as with `InMemoryUserDataStore`. Before, Firestore's `ExecutionException` escaped as 500.
- Risk: a domain exception that is itself an `IOException` would be reported as 503; none of the transaction blocks throw one.
  `InterruptedException` is not caught (unchanged). Non-storage throwables are rethrown as they are.
- Tests: `transactionRejectionsAreRethrownUnwrapped`, `storageFailuresAre503UserDataUnavailable`,
  `portfolioCreateReadAndRejectionOverFirestoreLikeStorage` (create, read back, other user isolated, oversell 400),
  `practiceLoadsAndKeepsTransactionRejectionsOverFirestoreLikeStorage` (init 10000 cash, order persists, insufficient cash 400).
  The test store runs the real in-memory transaction and reports it through `ApiFutures.immediateFailedFuture`, exactly the
  `ExecutionException` shape `ApiFuture.get()` produces.

## 2. StatusPages safety net (`ApiErrorHandling.kt`)

```kotlin
exception<org.example.stocksteps.userdata.UserDataException> { call, cause ->
    call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message))
}
```

- Fixes `GET /me/daily-brief/{id}/personalized` 500 → 503 (Daily Brief's `guarded` only maps `BriefRequestException`).
- Risk: low; `UserDataException` messages are user-facing copy everywhere they are thrown. Route-level catches still run first.
- Test: `withoutStorageEverySignedInRouteAnswers503WithNeutralCopy` (unmapped route → 503).

## 3. Neutral unavailable copy (`UserDataStore.kt`)

```kotlin
const val USER_DATA_UNAVAILABLE_MESSAGE = "Your saved data is temporarily unavailable. Try again later."
```

- The apps show the server message verbatim; "Watchlists and alerts are temporarily unavailable." was wrong on Portfolio and Practice.
  Code and status unchanged, so client mapping (`userDataErrorKind` → SERVER) is unchanged.
- Test: same as §2 (portfolio, practice, unmapped).

## 4. Credential overlay (`deploy/local/docker-compose.real.userdata.yml`, `deploy.sh`)

```yaml
environment:
  GOOGLE_APPLICATION_CREDENTIALS: /run/stocksteps/service-account.json
  FIREBASE_PROJECT_ID: ${STOCKSTEPS_FIREBASE_PROJECT_ID:?…}
  GOOGLE_CLOUD_PROJECT: ${STOCKSTEPS_FIREBASE_PROJECT_ID:?…}
volumes:
  - { type: bind, source: ./service-account.json, target: /run/stocksteps/service-account.json, read_only: true }
```

```bash
[[ "$stat" == "10001 400" ]] || { echo "…must be '10001 400' (the container user, read-only)." >&2; exit 1; }   # stat only
COMPOSE_FILE=docker-compose.yml:docker-compose.userdata.yml   # written to the host .env only when STOCKSTEPS_REAL_USER_DATA=1
```

- Decision: opt-in; default deploys are byte-for-byte the same `.env` and compose file (the overlay is removed from the host when off).
  `COMPOSE_FILE` in the host `.env` makes `start`/`restart`/`logs` use the overlay too.
- Risk: the key gives the LAN server Firestore access to project `stocksteps` (owner decision); mitigated by `roles/datastore.user` only,
  mode 400 owned by the container user, read-only mount, `.dockerignore`/`.gitignore` patterns.
- Test: `docker compose config` on the host (temp dir, dummy files): merged env and read-only bind present; missing project id fails.

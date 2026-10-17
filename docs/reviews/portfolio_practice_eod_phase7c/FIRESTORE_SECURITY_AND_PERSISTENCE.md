# Phase 7C — Firestore, security and persistence

No authentication, Firestore rule, collection layout or document schema was changed in this phase. Market data is read-only for both
features: no valuation refresh writes to Firestore.

| Check | Evidence | Result |
|---|---|---|
| Signed-in only; token validated on the server | `user(auth)` on every `/api/v1/me/*` route; `FirebaseIdTokenAuthenticator` (REAL); existing route tests (`PortfolioRoutesTest`, `PracticeRoutesTest`) assert 401 `SIGN_IN_REQUIRED` without a token | PASS (tests, unchanged) |
| User isolation | Documents are keyed by the authenticated uid only (`users/{uid}`), never by a client-supplied id; `PortfolioEodTest.repeatedRefreshesAndManyUsersShareOneCloseRequestPerSymbol` shares only public closes between 10 users | PASS (code + tests) |
| Shared caches hold only public market data | closes (`price-chart`), profiles, FX; no ledger data in a shared key | PASS (code) |
| Server is the authority for cash, positions and price | The Practice price is computed on the server; `reviewedPrice` is only checked (`PRICE_CHANGED`); cash and positions are replayed from the stored transactions | PASS (`PracticeEodTest.theClientCannotSetThePriceAndRetriesNeverFillTwice`) |
| Retries don't duplicate trades | The idempotency key is checked inside the atomic update; 10 concurrent retries → 1 fill | PASS (in-memory store test) |
| Failed writes aren't shown as success | `UserDataException` / 503 `USER_DATA_UNAVAILABLE` mapping (`FirestoreFailureMappingTest`, `0696f7e`) | PASS (existing tests) |
| Missing credentials → `USER_DATA_UNAVAILABLE` | `UnavailableUserDataStore` (`0696f7e`) | PASS (existing tests) |
| Real Firestore atomicity and persistence across restart, logout/login | Firestore emulator tests exist but are skipped without the emulator (3 skipped); the emulator runs only with owner approval | BLOCKED |
| LAN REAL signed-in device pass (create, buy/sell, restart, reload) | needs the owner's approved test account and a redeploy of this phase | BLOCKED |

# Phase 7D — authentication and user isolation

| Check | Level | Result |
|---|---|---|
| Missing token → 401 `SIGN_IN_REQUIRED` | Emulator (HTTP) + REAL `:8082` (curl, guest) | PASS |
| Forged or invalid token → 401 | Emulator (controlled authenticator) + REAL `:8082` (`Bearer forged.token.value`) | PASS |
| REAL verifier (`FirebaseIdTokenAuthenticator`): malformed, other project (audience), other issuer, expired → null | Unit, offline (claims are checked before the signature, so no certificate download; 0.07 s) | PASS |
| Valid Firebase ID token end to end | needs the Auth emulator (owner approval) or a real account | BLOCKED |
| User A can't read B's ledger or account | Emulator (B gets an empty ledger; A's account summary → 404 `ACCOUNT_NOT_FOUND`) | PASS |
| User A can't trade on B's Practice account | Code: every route derives the uid from the verified token (`user(auth) { uid -> … }`); no route takes a uid from the path, query or body. Emulator: B's Practice account stays empty | PASS |
| Client-supplied ids can't override ownership | Code: account and transaction ids are scoped inside `users/{uid}` documents | PASS |
| Guest contract | Practice and Portfolio are signed-in only (401); public market routes need no token (existing) | PASS |
| Errors don't leak tokens, credentials or documents | Emulator: the 401 body has no token; the 503 body has no exception or Firestore text; `ApiErrorHandling` logs the type only (existing) | PASS |
| Defect D1 | the 401 copy says "watchlists and alerts" on Portfolio and Practice | FOUND, not fixed (copy approval) |

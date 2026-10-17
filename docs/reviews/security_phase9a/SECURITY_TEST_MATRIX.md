# Security test matrix — Phase 9A

Emulators and test doubles only; nothing was sent to production. Status words per `docs/AI_REVIEW_WORKFLOW.md` §4. Test classes are under
`server/src/test/kotlin/org/example/stocksteps/` unless marked core.

| Scenario | Test(s) | Status |
|---|---|---|
| Public route without token | `security/RouteAccessTest.publicRoutesServeGuestsWithoutSignIn` (all 40 PUBLIC GETs on the real MOCK server), `AuthenticationGateTest.theGateProtects…` | PASS |
| Protected route without token | `RouteAccessTest.everySignedInRouteRefusesMissingAndForgedTokens` (88 routes), emulator test (guest reads/writes) | PASS |
| Malformed token | same sweep (`garbage`, `Basic`, empty Bearer), `AuthenticationGateTest.failuresMapToStableCodes…`, `FirebaseTokenVerifierTest` | PASS |
| Expired token | `FirebaseTokenVerifierTest` (signed + unsigned), gate mapping | PASS |
| Wrong-project token (aud / iss) | `FirebaseTokenVerifierTest.signatureProjectExpiryAndClaimsAreEnforced` | PASS |
| Bad signature / tampered uid / alg none | same | PASS |
| Certificate outage | `FirebaseTokenVerifierTest.aCertificateOutageIsUnavailableNotASignOut` → 503 path in gate test | PASS |
| Forged UID header or body | `AuthenticationGateTest.forgedUidsInHeadersQueryAndBodyAreIgnored`, `RouteAccessTest` sweep (X-User-Id + body uid) | PASS |
| A reads/edits B's portfolio | `userdata/FirebaseEmulatorTwoUserIsolationTest` (Auth + Firestore emulators) | PASS |
| A reads B's Practice | same | PASS |
| Watchlists / alerts across users | same; `UserRoutesTest` | PASS |
| Duplicate Practice trade | `RateLimitAbuseTest.practiceTradeRetries…`; 7D `FirestorePortfolioPracticeIntegrationTest.concurrentOrders…` (emulator) | PASS |
| Guest protected write | emulator test (PUT account → 401, nothing written); sweep | PASS |
| Anonymous Firebase account | `AuthenticationGateTest` (403 `ACCOUNT_REQUIRED`), `RateLimitAbuseTest.unverifiedGuests…` (no own bucket) | PASS |
| Rate-limit burst and recovery | `RateLimitAbuseTest` 1–3, 9; real sockets | PASS |
| Concurrency (expensive) | `RateLimitAbuseTest.concurrentExpensive…`; real sockets (2 × 200, 8 × 429) | PASS |
| Invalid symbol / path / query / pagination | `RequestValidationTest.pathQueryAndPaginationInputIsValidated`, `ApplicationTest`, `PublicApiProtectionTest.randomSymbols…` | PASS |
| Oversized request (413/414) | `RequestValidationTest.sizeLimitsAreEnforcedBeforeHandlers`, `PublicApiProtectionTest.oversizedBodies…` | PASS |
| Chunked body without length (411) | real socket (`captures/real-socket-checks*.txt`); test engine can't frame it | PASS (real socket) |
| Malformed JSON / wrong content type | `RequestValidationTest.malformedBodiesAndWrongContentTypes…` (found and fixed: text/plain → was 500, now 415) | PASS |
| Unsupported method / TRACE | `RequestValidationTest.unsupportedMethodsAreRefused` | PASS |
| CORS / security headers | `RequestValidationTest.noCorsAndSafeDefaultHeaders`; real sockets | PASS |
| Provider 429 / daily limit / plan restriction | `RateLimitAbuseTest.providerQuotaExhaustion…`, `ProviderBudgetTest`, `QuoteQuotaProtectionTest`, `FinancialCacheCooldownTest` | PASS |
| Firebase/Firestore unavailable | `FirestoreFailureMappingTest` (503 `USER_DATA_UNAVAILABLE`), token outage → 503 `AUTH_UNAVAILABLE` | PASS |
| Concurrency across users on Firestore | emulator test (parallel orders A and B) | PASS |
| Token refresh and retry (client) | core `StockStepsApiTest.signedInRequestsRetryA401OnceWithAFreshTokenAndNeverLoop` | PASS |
| 429 / Retry-After (client) | core `StockStepsApiTest.rateLimitsAreNotRetriedAutomaticallyAndExposeRetryAfter` | PASS |
| Auth state transition (account switch, sign-out) | existing core `UserDataRepositoriesTest`, `PortfolioSessionStateTest` (owner/identity checks, `expectedOwner`) | PASS (re-run in suites) |
| Secret-safe logging | `RateLimitAbuseTest.rateLimitDenialsAreLoggedWithoutIdentitiesOrPaths`, `providerQuotaExhaustion…` (no raw symbols), `service/LoggingSecurityTest`, core `ApiLoggingTest`; real server logs: 0 tokens/uids/IPs | PASS |
| Route classification complete | `RouteAccessTest.everyRegisteredRouteHasExactlyItsOwnRule`, `accessClassesFollowThePathPrefixes` | PASS |
| App Check missing / invalid / enforce | `PublicApiProtectionTest.appCheckMonitorsByDefaultAndEnforcesOnlyWhenAsked` (server only; apps not integrated) | PASS (server); apps NOT RUN (not integrated) |
| Debug / emulator access | MOCK-only debug routes (registration flags); REAL verifier refuses emulator tokens (`realVerifierRefusesEmulatorTokens`) | PASS |
| Production attack testing | — | NOT RUN (out of scope by rule) |

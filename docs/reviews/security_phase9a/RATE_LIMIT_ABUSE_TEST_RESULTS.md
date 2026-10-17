# Rate-limit and abuse test results — Phase 9A addendum

Two kinds of evidence:
- **Automated:** `server/src/test/kotlin/org/example/stocksteps/security/RateLimitAbuseTest.kt` (14 tests) against the production security stack
  (admission → class limits → central gate) with the **default** limits, Ktor test host, controllable clock. All PASS.
- **Real sockets:** a MOCK distribution built from the working tree, Netty on :8091 (no trusted proxy) and :8092 (`TRUSTED_PROXY_HOPS=1`), driven
  with curl — `captures/real-socket-checks.txt`.
Nothing touched production, a real provider or another session's server.

| # | Addendum scenario | Automated test | Result | Real-socket result |
|---|---|---|---|---|
| 1 | 100 rapid requests from one user | `oneUserFiringRapidRequestsIsServedUpToTheLimitThenGetsRetryAfter` | 100/100 OK (AUTHENTICATED_STANDARD 120/min); 130 → 120 OK + 10 × 429, `Retry-After` 1–60, no limits in body | 130 → 120 × 200, 10 × 429 |
| 2 | 100 rapid guest requests | `hundredRapidGuestRequestsFromOneAddressAreCappedAndSpoofingDoesNotReset` | 90 OK + 10 × 429; forged leading XFF and a fresh `X-Device-Id` still 429; other address OK; other class OK | :8092 90 × 200, 10 × 429, `Retry-After: 59`; spoofed XFF 429; other address 200. :8091 (no proxy) 100 × 200 (shared pool) |
| 3 | Two different authenticated users | `twoSignedInUsersHaveIndependentLimits` | A limited, B gets its full 120 | A 429 → B 200 |
| 4 | Concurrent expensive requests | `concurrentExpensiveRequestsAreCappedPerUserAndRejectedNotQueued`, `expensiveOperationsHaveALowPerMinuteLimit` | 2 in flight; 3rd → 429 `Retry-After: 1`, never reaches the handler; another user unaffected; slot frees after completion; 15 sequential → 12 OK | 10 concurrent AI movement calls → 2 × 200, 8 × 429; 15 sequential → 12 / 3 |
| 5 | Repeated identical stock searches | `repeatedIdenticalSearchesShareOneProviderCallAndTheSearchBucketStopsFloods` | 40 concurrent identical searches by 40 users → **1** provider call; 50 from one guest → 45 OK + 5 × 429; still 1 provider call | r2 (final defaults): 50 from one guest address → 45 × 200, 5 × 429 |
| 6 | Rapid tab switching | `rapidTabSwitchingBySignedInUserStaysWithinLimits` | worst case 25 full cycles (175 requests, every landing refetched) → 0 × 429 | emulator: 25 real switches → **0 requests** |
| 7 | Multiple users sharing one IP | `usersSharingOneAddressAreLimitedPerAccountAndGuestsThereShareTheAddressBudget` | guest address exhausted; 3 signed-in users on it still OK; invalid token falls back to the address → 429 | — |
| 8 | Provider quota exhausted | `providerQuotaExhaustionIsA503WithoutQuotaDetailsAndIsLogged` (+ existing `ProviderBudgetTest`, `QuoteQuotaProtectionTest`) | daily allowance → 503 `PROVIDER_DAILY_LIMIT_REACHED`; upstream 429 → 503 `PROVIDER_RATE_LIMITED`; 403 → 502; no key/host/number in bodies; `upstream_restricted` logged by route template | — |
| 9 | Retry after cooldown | `aLimitedCallerIsServedAgainAfterRetryAfter` | still 429 two seconds before `Retry-After`, 200 after | — |
| 10 | Practice trade retry with one idempotency key | `practiceTradeRetriesWithOneIdempotencyKeyFillExactlyOnce` | 8 concurrent executes → only 200/429, one transaction id; retry → `replayed = true`; ledger has 1 trade; same key + different order → 409 | — (emulator: `FirebaseEmulatorTwoUserIsolationTest`, keys per account) |
| 11 | Memory/state cleanup after windows | `limiterStateIsBoundedAndCleanedAfterTheWindow` | 5,000 addresses with `maxTracked = 1,000` → ≤ 1,000 kept; after 61 s + sweep → 0; in-flight entry kept until exit; log throttle state ≤ 10,000 | — |
| + | Unverified pool and anonymous accounts | `unverifiedGuestsShareALargePoolAndAnonymousAccountsGetNoBucketOfTheirOwn` | guests and fresh anonymous uids share the pool | — |
| + | Safe denial logging | `rateLimitDenialsAreLoggedWithoutIdentitiesOrPaths` | 10 denials → 1 log line (throttled), 10 counted; no IP, symbol or token in logs | server logs: 0 occurrences of tokens, uids or client IPs |
| + | Chunked body without length | (test engine can't frame chunked) | — | **411** |

Real-socket runs: `captures/real-socket-checks.txt` (first draft, search bucket 30/min) and `captures/real-socket-checks-r2.txt` (final
defaults; also 415 for a `text/plain` body). The `invalid_input` log for exception-mapped 400/415 was added after r2 and is covered by
`RequestValidationTest.malformedBodiesAndWrongContentTypesAreClientErrorsNotServerErrors`.

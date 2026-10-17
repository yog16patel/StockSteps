# Test results — Phase 9A (9A code on `1c71207`, before rebasing onto the test-only `1a39ce3`; 2026-10-17)

Run sequentially on this Mac (`captures/suite-summary.txt`, logs `captures/suite-*.log`, JUnit XML in `captures/`). Counts read from
the JUnit XML files.

| Suite / build | Result | Counts | Notes |
|---|---|---|---|
| `:core:jvmTest` | PASS | 625 tests, 0 failures, 0 skipped | incl. 2 new `StockStepsApiTest` cases |
| `:core:iosSimulatorArm64Test` | PASS | 623 tests, 0 failures, 0 skipped | the 8E-noted flaky `ApiLoggingTest` race did not occur in this run |
| `:server:test` | PASS | 604 tests, 0 failures, 10 skipped | re-run after the last server change (`suite-server-r2.log`); the 10 skipped need the emulators (next row) |
| Emulator tests (`firebase emulators:exec --only auth,firestore`, project `demo-stocksteps-9a`) | PASS | 10 / 10: `FirebaseEmulatorTwoUserIsolationTest` 2, `FirestorePortfolioPracticeIntegrationTest` 5, `FirestoreNewsSimplificationStoreTest` 3 | local emulators only (owner rule satisfied by the 9A brief); `captures/emulator-tests.log`, `captures/emulator/` |
| `:app:shared:testAndroidHostTest` | PASS | 289 tests, 0 failures | |
| `:app:shared:iosSimulatorArm64Test` | PASS | 286 tests, 0 failures | |
| `:app:androidApp:assembleDebug` | PASS | BUILD SUCCESSFUL | includes the uncommitted 8E code |
| `xcodebuild … CODE_SIGNING_ALLOWED=NO build` | PASS | BUILD SUCCEEDED | default DerivedData |

## Phase 9A security tests (all PASS)
| Class | Tests |
|---|---:|
| `security/AuthenticationGateTest` | 4 |
| `security/RateLimitAbuseTest` (addendum scenarios 1–11 + 3 more) | 14 |
| `security/RequestValidationTest` | 5 |
| `security/RouteAccessTest` (real MOCK server: 136 routes, 88 signed-in × 5 token variants, 40 public GETs) | 4 |
| `security/PublicApiProtectionTest` (Phase 4A, unchanged, re-run) | 13 |
| `userdata/FirebaseTokenVerifierTest` (3 existing + 4 new, real RSA signatures) | 7 |
| `userdata/FirebaseEmulatorTwoUserIsolationTest` (emulators) | 2 |
| core `network/StockStepsApiTest` (2 new: Retry-After, 401 once / no 429 retry / 403 no refresh) | 6 |

No security test was excluded. Tests changed for 9A behaviour: `ComparisonResearchRoutesTest` now installs the central header hook (the PDF
export's `no-store` comes from it).

## Other evidence
- Real sockets (Netty, local MOCK distribution): `captures/real-socket-checks.txt`, `captures/real-socket-checks-r2.txt` — 411, 90/10 guest,
  120/10 user, 45/5 search, 12/3 expensive, 2/8 concurrent, 415 for `text/plain`, security logs with 0 tokens/uids/IPs. PASS.
- Latency before/after (base `1c71207` vs working tree, MOCK, loopback): `captures/latency-before-after.txt` — p50 differences within ±1 ms
  (search 1.35 → 1.26 ms, Company Details 3.84 → 2.79 ms, Practice 1.06 → 0.98 ms). PASS (no regression).
- Android emulator-5554 request counts and 0 × 429: `RATE_LIMIT_REVIEW.md` §2. PASS.
- iOS simulator against the 9A server: NOT RUN. Signed-in iOS: BLOCKED (owner sign-in). TalkBack/VoiceOver: NOT RUN. Physical devices: NOT RUN.
- Production / staging: NOT RUN (out of scope by rule).

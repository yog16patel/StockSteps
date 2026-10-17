# Phase 8E — Test and build results (sequential, final code)

| Suite / build | Result |
|---|---|
| `:core:jvmTest` | PASS 623/0 (includes the new `NavigationMotionTest`, 4 tests) |
| `:core:iosSimulatorArm64Test` | FAIL 621 run, 1 failure → see the flake note; the class rerun passed 13/0 |
| `:server:test` | PASS 571/0, 8 skipped (Firestore-emulator tests skipped without the emulator) |
| `:app:shared:testAndroidHostTest` | PASS 289/0 |
| `:app:shared:iosSimulatorArm64Test` | PASS 286/0 |
| `:app:androidApp:assembleDebug` | PASS |
| `xcodebuild` (iOS simulator) | PASS |
| Firestore emulator tests | NOT RUN (emulator not started; no 8E data change) |
| Compose UI / Macrobenchmark | NOT RUN (not configured; none added) |

**Flake (not caused by 8E):** `ApiLoggingTest.concurrentRequestsGetDistinctIdsThroughTheBackgroundWriter[iosSimulatorArm64]` failed with
`kotlin.ConcurrentModificationException` during the full run on a heavily loaded host. Rerunning `ApiLoggingTest` alone passed 13/0.

8E touches neither the logger nor its test. The exception points to a real race in the DEBUG logging writer or the test's collector, so a
passing rerun does not resolve it. It is recorded for a separate fix.

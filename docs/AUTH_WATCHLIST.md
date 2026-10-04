# Authentication and offline watchlist

## App setup

Firebase project: `stocksteps`. Email/Password sign-in must be enabled in Firebase
Authentication. Both app registrations use `org.example.stocksteps`.

- Android config: `app/androidApp/google-services.json`.
- Apple config: `app/iosApp/iosApp/GoogleService-Info.plist`, included by the Xcode
  synchronized source group.
- Android uses Firebase BoM 34.19.0 (Auth and Firestore main modules).
- Apple uses FirebaseCore/Auth/Firestore through SPM, pinned to 12.19.2. This SDK
  requires Xcode 26.2 or newer. Xcode 16.2 cannot resolve it. Install/select a
  supported Xcode before building the real project; do not silently downgrade it.
- Shared SQLite uses SQLDelight 2.4.0, Android/native drivers, coroutine query
  observation, and `-lsqlite3` in the native app linker flags.

Firebase client config identifies the app; it does not grant privileged database
access. Config files are ignored for fresh checkouts and must be supplied per
build environment. A config the owner already staged remains staged. Never put
service-account credentials, passwords, or backend provider keys in mobile code.
Missing Firebase config enables guest storage and displays an account setup error;
it never substitutes fake authentication.

## User flow

Welcome → Start exploring → Home/WatchList/Learn/Settings.
Search for a stock, open its details, and use Add to WatchList. WatchList displays
saved ticker symbols; choosing one opens the existing stock search/details flow.
Quotes and profiles still come from the Ktor backend. No prices or financials are
written into the watchlist database or Firestore.

Sign in/create account from WatchList or Settings. Signup/login use native Firebase
SDKs, which own credential persistence and refresh. Password input is transient,
not saved in a ViewModel snapshot, database, saved-instance state, or logs.
Settings provides sign-out. Google/Apple sign-in are not implemented; platform
adapters and plain repository models allow those providers to be added later.

## Architecture and lifetime

`core` defines plain User/AuthSession/WatchlistItem models, AuthRepository,
WatchlistRepository, use cases, platform callback contracts, and the shared sync
coordinator. Firebase SDK types stay in Android and Swift data adapters.

`AccountDependencies` creates an isolated app-owned Koin graph. Its SQL store,
auth repository, and sync coordinator live across screen changes. Android's
activity ViewModel owns that graph across configuration changes; native Swift's
AccountService owns IosAccountClient through the root Observation model. Feature
ViewModels receive repositories/use cases and cannot close app-owned services.
Routes carry destination arguments. Scenes acquire models and connect events.
Screens receive state/callbacks/bindings. Existing financial-data services retain
their independent lifetimes and continue calling StockSteps only.

## Local storage, ownership, and merging

The local `watchlist` table has `(owner, symbol)` as its primary key. A separate
`pending_operation` table is a durable outbox. Each replacement gets a fresh,
autoincremented revision. Both row changes and outbox changes share one SQL
transaction. SQL query Flows are the UI's primary source.

- Guest namespace: `guest`.
- Account namespace: `user:{uid}`.
- Symbols are trimmed and uppercased; supported pattern is
  `[A-Z0-9][A-Z0-9.^-]{0,31}` (including `SHOP.TO`).
- Login/signup atomically merges guest rows into the authenticated namespace and
  consumes the guest rows. This preserves their data while preventing a later
  login to another account from re-importing it. Duplicate symbols merge with
  the earlier addedAt. A pending account removal takes precedence over a guest
  duplicate.
- Logout switches to guest data. It never copies account rows into the guest
  namespace. Account caches and pending operations remain isolated for a future
  login to the same UID.
- Reactive snapshots carry the owning UID. Views discard rows whose UID does
  not match the current session, including during asynchronous account changes.

## Cloud schema and security

Only `users/{uid}/watchlist/{symbol}` is used. Documents contain exactly:

```text
symbol: String
addedAt: Long       # Unix milliseconds
updatedAt: Long     # Unix milliseconds
```

`firestore.rules` requires authentication and UID ownership, validates document
ID/symbol equality, symbol format, exact keys, integer timestamps and chronological
ordering. Reads/deletes require ownership. Other document paths are denied.
No user profile document or financial fields are written.

On 2026-10-04, the owner explicitly approved deploying the tested rules. Deployment
to `stocksteps` succeeded, enabled the Firestore API, and created the default
Firestore Native database in `nam5`, Standard edition, with the free tier enabled.
No live test accounts or watchlist documents were created by our test suite.

## Synchronization and V1 conflicts

Local add/remove changes appear immediately. Account edits queue durable writes;
guest edits remain local until claimed by a login. One coordinator per app sends
operations serially. An acknowledgment clears only its own revision, so a newer
edit cannot be lost while an older request is in flight.

Remote listeners apply authoritative server snapshots to clean local rows.
Cached or locally pending Firestore snapshots cannot delete local rows. Pending
adds/removals override incoming server membership until acknowledged. Listener
cancellation and session-scoped coroutines reject stale callbacks after logout
or account changes.

Cloud writes use server transactions (including deletes), preventing Firestore
from keeping a second, independent offline write queue that could later replay
obsolete writes. Transactions preserve the earliest addedAt for an existing
symbol. The SQL outbox retains failed writes across restart. Retry runs on new
edits, manual retry, app foregrounding, network availability, and a bounded
30-second retry interval. SDK transactions supply their own deadline; the app
does not launch overlapping retries after a shorter coroutine timeout.

Across devices, the last transaction committed to Firestore wins. updatedAt is
metadata, not a trusted conflict-order clock. A later offline add can recreate a
stock another device previously removed; that is the deliberate V1 limitation.
Permanent permission/configuration errors remain visible and never erase local
rows. No general-purpose sync framework, background-job guarantee while the app
is terminated, alerts, learning sync, or portfolio storage has been added.

## Verification

Passed:

```sh
./gradlew :core:jvmTest :app:shared:testAndroidHostTest
./gradlew :app:androidApp:assembleDebug
./gradlew :app:shared:linkDebugFrameworkIosSimulatorArm64
npm ci --prefix firebase
npm test --prefix firebase
```

New coverage: three shared auth tests, four shared coordinator tests, four real
SQLite host tests, five Firestore emulator rules tests, and one Android device
integration scenario. The device test verifies guest signup merge, a second
client download, offline removal/reconnect, session identity, logout/account
isolation, and cross-UID server denial. Android UI checks verified add from live
Ktor stock details, full process restart persistence, removal, and auth entry.

Native Swift/Firebase adapter and view compilation passed in an isolated
**temporary** project using Firebase 12.11.0 and Xcode 16.2. The actual project
continues to require Firebase 12.19.2/newer Xcode; current-SDK build and iOS runtime
flows remain unverified until the toolchain is upgraded. No existing project
Firebase dependency was downgraded for that check.

## Local Firebase tests

Rules tests use `demo-stocksteps` and stop their local Firestore emulator after
completion. Android integration tests use named SDK apps and fake configuration,
explicitly connected to local emulators; they never use live credentials.

Start emulators from the project root in one terminal:

```sh
./firebase/node_modules/.bin/firebase emulators:start --project stocksteps --only auth,firestore
```

Then run from a second terminal:

```sh
adb reverse tcp:9099 tcp:9099
adb reverse tcp:8085 tcp:8085
./gradlew :app:androidApp:connectedDebugAndroidTest
```

For manual testing, build Android with `-PfirebaseEmulators=true`; release builds
always disable that option. For native iOS debug, pass `--firebase-emulators` as a
launch argument. Keep these endpoints local and opt-in. Existing Ktor testing
still uses `adb reverse tcp:8080 tcp:8080`.

Rules deployment (requires the project owner's explicit authorization):

```sh
./firebase/node_modules/.bin/firebase login
./firebase/node_modules/.bin/firebase deploy --only firestore:rules --project stocksteps
```

## Remaining checks

Upgrade/select Xcode 26.2+, build the actual pinned SPM package, and verify native
login/signup/logout, offline flows, and restored sessions on iPhone/iPad. Verify
live Android authentication with an account controlled by the owner. Provider
access, production HTTPS backend, distribution/signing, and broader adaptive/
accessibility checks remain as documented in PROJECT_HANDOFF.md.

### Google sign-in

The login button now uses Android Credential Manager (explicit Google button flow)
and native GoogleSignIn 9.2.0 on iOS, followed by Firebase signInWithCredential.
No provider token is saved or logged; Firebase owns the resulting session. The
existing UID-scoped guest merge and watchlist sync apply to Google accounts too.
Cancellation stays on login with a safe message; duplicate actions are blocked.

Owner confirmed Google provider is enabled for stocksteps. Current config files
contain Android/web OAuth clients and iOS client/reversed client IDs. Owner
registered this machine’s debug SHA-1 and refreshed Android config; exact match
verified on 2026-10-04. Register additional Android
signing certificates in Firebase project settings and download updated config.
Debug SHA-1 for this development machine:
`EE:EB:4D:0A:99:3E:C9:C3:F0:DF:4B:C5:F4:01:40:89:03:5C:CB:F9`.
Release/Play signing certificates must also be registered before production use.
iOS URL scheme comes from GoogleService-Info.plist's REVERSED_CLIENT_ID; refresh
Info.plist URL types if replacing the config with another OAuth client.

Android build/core tests passed and provider UI opened on emulator-5554. No real
Google account was entered by the agent. Native compile passed using the isolated
Firebase compatibility project; actual Firebase pin still needs upgraded Xcode.
Password recovery and account linking UI remain pending.

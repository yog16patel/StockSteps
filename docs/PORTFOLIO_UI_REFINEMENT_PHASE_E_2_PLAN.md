# Portfolio Phase E.2 — plan (read-only audit; nothing implemented)

Date: 2026-10-13. Status: **PLAN — awaiting owner approval.** No code, test or fixture was changed for this plan.

## 1. Repository baseline and worktree safety

- `main` at `82fd22e` "Record Portfolio Phase E.1 iOS signed-in verification with all add, edit, delete, rejection, sample, account-switch and
  navigation checks passing on the iPhone 16 simulator" (matches the handoff).
- Dirty, **not E.2 work, never staged by E.2**: root `PROJECT_HANDOFF.md` (backend session, 2 lines), `docs/PROJECT_HANDOFF.md` (Portfolio
  session-end handoff), `docs/project-status.md` (both sessions' hunks). E.2 adds this plan file only.
- E.2 will stage by explicit path. It won't touch `server/`, `deploy/` or `~/.gradle/gradle.properties`. Server tests run alone (shared `build/` with
  the backend session).
- Android device builds for local MOCK: `-PstockstepsMockBackendUrl=http://127.0.0.1:8081` (the machine-wide Gradle property targets the Ubuntu host).

## 2. Root causes

### A. Truncated iOS account-creation title (P2, verified in `docs/design/portfolio_phase_e_1/i16-e1-02-account-form-title-truncated.jpg`)

- `app/iosApp/iosApp/PortfolioForms.swift:36` sets `.navigationTitle(ACCOUNT_CREATE_TITLE)` with **no** `navigationBarTitleDisplayMode`. In a sheet the
  default is a **large** title, and iOS large titles never wrap, so "Create investment account" (25 characters) truncates at Large.
- Every other Portfolio sheet/screen uses `.inline` (`PortfolioForms.swift:272`, `PortfolioScreen.swift:536/573`, `PortfolioTransactionsScreen.swift:223`,
  `PortfolioTransactionDetailsScreen.swift:23`); this sheet is the outlier.
- Copy is shared: `PortfolioDisplay.ACCOUNT_CREATE_TITLE` (`app/shared/.../portfolio/PortfolioDisplay.kt:137`), also used by the Compose dialog
  (`PortfolioForms.kt:46`, an `AlertDialog` title that wraps, so Android isn't truncated).
- Related observation (same screenshot): the iOS **Account name** field shows only a placeholder, so once text is typed ("E1 iOS") its label is gone.
  The transaction form fixed the same problem in D1 (label above the input). VoiceOver still gets the `TextField` title. Rated **P3** (see §7, D-4).

### B. Transaction currency (TD.TO recorded in USD in a CAD account)

| # | Question | Finding (code reading; runtime confirmation in §6) |
|---|---|---|
| 1 | Where does the transaction currency come from? | The form. iOS `PortfolioForms.swift:292`: `existing.currency ?? security.currency ?? "USD"`. Android `PortfolioForms.kt:122/145`: `existing ?: initialCurrency ?: USD`. `security.currency`/`initialCurrency` come only from a **search/selection** (`InstrumentRef(..., stock.currency)`: `PortfolioScene.swift:119`, screener, company details, watchlist). The account's reporting currency is **not** used. |
| 2 | Does TD.TO select CAD automatically? | **Only when chosen through search/selection and the listing metadata says CAD.** A typed symbol gets no lookup and stays **USD**. In E.1 the symbol was typed (`i16-e1-04`: "Price per share (USD)"). Also, in **MOCK** the synthetic profile for any non-catalog symbol, TD.TO included, is `currency = "USD", exchange = "NASDAQ"` (`server/.../fixture/SampleMarketData.kt:62-72`), so even a search would say USD in MOCK. REAL uses the provider profile. |
| 3 | Can the user override it? | Yes: **Trade currency** picker. But it's an `advanced` field (`PortfolioFormRules.kt:15`), so it sits under the **collapsed "More options"**. |
| 4 | Is the trade currency distinct from the reporting currency? | Only through the field labels ("Price per share (USD)") and the account chip ("E1 iOS · CAD"). Nothing says the two differ or what that means. |
| 5 | FX for a USD trade in a CAD account? | **Correct; no engine defect found.** Basis converts at the trade-date rate (`PortfolioEngine.kt:146-160`, `historicalFx`). Value: the server converts the quote from its listing currency into the trade currency (`PortfolioMarketService.priceInTradeCurrency`), then the engine converts it to reporting at the current rate (`PortfolioEngine.kt:136-171`). E.1 numbers agree: 5 × 75 USD × 1.35 = **CAD 506.25** invested cost. Total CAD 2,166.48 = 5 × the synthetic MOCK quote (≈ USD 320.9) × 1.35, a MOCK sample price, not real TD. |
| 6 | Is the currency kept on edit? | Yes: both platforms start from `existing.currency` / `existing.cashCurrency`. |
| 7 | Do history and details show the recorded currency? | Yes: `PortfolioTransactionDetailsDisplay.kt:79/85/96` use `tx.currency` (recorded), never the account's. |
| 8 | Currency unknown or market data unavailable? | Server: listing currency from `quotes.currency()`, else `instrument.currency`, else **no price** (holding valued as missing, never guessed). **But** both forms always send an `instrumentCurrency`: iOS `securityCurrency = security?.currency ?? currency` (`:293`), Android `?: currency.name` (`PortfolioForms.kt:175`). For a typed symbol the default trade currency is stored as if it were listing metadata, and the server falls back to it when the profile is unavailable. That's a latent risk, not changed in E.2 without approval (D-3). |
| 9 | Could a beginner record the wrong currency? | **Yes; misleading default, the genuine defect.** A typed Canadian symbol in a CAD account defaults to USD. The only control is hidden under "More options", and nothing flags the mismatch. The resulting values are internally consistent but describe a trade the user didn't make. |

**iOS parity defect found (P2):** changing **Trade currency** on iOS doesn't move **Cash currency** with it (there's no `onChange` on the currency picker).
Android moves cash along when they were equal (`PortfolioForms.kt:208`). On iOS, picking CAD leaves cash in USD, so `cashDiffers` becomes true and an
**FX rate** field appears that is required by validation, which a beginner didn't ask for.

### C. Session-ended banner ("Offline · last saved portfolio" + "Your session has ended…" + Retry)

- Classification is lost in one place: `ServerBackedRepository.refresh` (`core/.../data/userdata/UserDataRepositories.kt:103-105`) sets
  `offline = it.value != null` for **every** failure and stores only a **string** (`userDataError`, `:39-43`):
  - `AccountException` (token refresh failed): "Your session has ended…" (E.1 mapper);
  - `StockStepsApiException` (server answered 4xx/5xx, including 401/503): the server's message;
  - anything else (network): "You're offline or the server can't be reached. Try again."
- `PortfolioDisplay.errorTitle` (`PortfolioDisplay.kt:51-59`) puts `offline` first, so any failure with a cached ledger is titled **"Offline · last
  saved portfolio"**, and `errorRetries` offers **Retry** for every refresh error. Retry with a dead session calls `idToken` again and fails the same way.
  It's a dead end, not a loop, but it never offers the recovery that works.
- **The session stays signed in:** a refresh failure doesn't clear `auth.session.user`. Navigating to the existing `AuthRoute` while `user != null` is
  the risky part: the app only routes to Login automatically when `user` becomes null (`AppNavigation.kt:73-82`), and the auth screens assume a
  signed-out session. The only reliable recovery is **sign out, then sign in**. Sign-out clears the per-user cache ("Cleared on sign-out",
  `UserDataRepositories.kt:18`), so the cached portfolio disappears until sign-in. That's acceptable (the data is on the server) but should be said.
- **Add stays enabled offline / with an ended session:** iOS `PortfolioScreen.swift:161` disables "+ Add transaction" only on `busy`; Compose is the
  same. A save then fails with the session message. Account "+ Add" is gated only on `signedIn` and `busy`.
- Other screens (Home freshness line `HomeDashboardPresentation.kt:64`, Watchlist, Practice) use the same `offline` flag; E.2 doesn't change them.

### D. Small-screen review

Static review only so far. Runtime matrix in §6. Already known: A (P2), the iOS cash-currency parity (P2, under B), the Account-name label (P3).

### E. Allocation denominator proposal (investigation only)

`docs/PORTFOLIO_ALLOCATION_DENOMINATOR_PROPOSAL.md` checked against the required topics:

| Topic | Covered? | Gap to close before implementation |
|---|---|---|
| Positive cash | Yes (cash slice per currency, §3.2) | — |
| Negative / unfunded cash | Yes (`unfundedCash`, no slice) | Say whether `unfundedCash` is per currency or one converted sum when both currencies are negative. |
| Zero-value portfolio | Yes (denominator ≤ 0 → null + note) | — |
| Missing prices | Yes (all null) | — |
| **Stale** prices | **No** | State that stale-but-present quotes still count (as today) and that the existing freshness line qualifies them. |
| Multiple reporting currencies | Partly (mixed-currency test) | Allocation is per account in its reporting currency; say there's no cross-account aggregate (excluded by the owner). |
| Rounding | Partly (half-up 2 dp) | The "sum = 100.00" test can fail after per-slice rounding: say whether displayed slices may sum to 99.99/100.01 (recommended: yes, with no largest-remainder adjustment, matching Insights). |
| API compatibility | Yes (additive fields) | Note that old clients show changed percentages without the cash slice, so their slices sum below 100% when cash is positive. |
| Labels | Partly ("share of what you own") | Add the exact caption copy for the dashboard and Insights, so both say the same thing. |

**Recommendation:** approve A1 as its own phase ("Portfolio Allocation Contract") after these five clarifications are written into the proposal.
No calculation change in E.2.

## 3. Proposed changes (only after approval)

| ID | Change | Files |
|---|---|---|
| **A1** | Shared title "Create investment account" → **"New investment account"**. iOS sheet `.navigationBarTitleDisplayMode(.inline)` like every other Portfolio sheet. The spoken title is the full visible text (nothing is truncated, so no separate label is needed; see D-1). Dynamic Type untouched. | `app/shared/.../portfolio/PortfolioDisplay.kt`, `app/iosApp/iosApp/PortfolioForms.swift` |
| **B1** | Move **Trade currency** from "More options" into **Amounts** (first row): `TRADE_CURRENCY(advanced = false)`, `section()` maps it to AMOUNTS; `redesignOrder` puts it before Shares. Both platforms read the shared rule, so no per-platform layout logic. | `PortfolioFormRules.kt` (+ both forms only if a section switch needs the new field) |
| **B2** | One factual note under it when trade ≠ the selected account's reporting currency: *"This account reports in CAD. These amounts are in USD and are converted with the exchange rate for the trade date."* New `PortfolioFormRules.currencyNote(trade, reporting): String?` + copy in `PortfolioDisplay`. Display only; nothing is converted or changed. | `PortfolioFormRules.kt`, `PortfolioDisplay.kt`, `PortfolioForms.kt`, `PortfolioForms.swift` |
| **B3** | iOS parity: changing Trade currency also moves Cash currency when they were equal (Android behaviour). | `PortfolioForms.swift` |
| **C1** | (Approval D-2) A shared failure kind next to the message: `ServerBackedRepository` keeps `errorKind = SESSION | NETWORK | SERVER` (from the exception type it already distinguishes in `userDataError`). `offline` keeps its meaning (cached data shown). `PortfolioUiState` passes it through. | `core/.../userdata/UserDataRepositories.kt`, `core/.../portfolio/PortfolioPresenter.kt` (+ `PortfolioUiState`) |
| **C2** | Banner contract in `PortfolioDisplay` (title/action chosen in one place, both platforms): see table below. | `PortfolioDisplay.kt`, `PortfolioTransactionViews.kt` (`PortfolioErrorBanner`), `PortfolioTransactionsScreen.swift` (`portfolioErrorBanner`) |
| **C3** | (Approval D-2) "Sign in again" action = existing **sign-out** first, then the existing sign-in entry. Android: `auth.signOut()`; the app's sign-out handling already lands on Login with an empty back stack (`AppNavigation.kt:73-82`). iOS: `AccountViewModel.signOut()` (`AccountViewModel.swift:75`), then the existing `onSignIn` (`showingAuth = true`, the `AuthScene` sheet, `AppScene.swift:277`), because iOS has no automatic route on sign-out. No new auth screen, and the auth UI is never opened while still signed in, so no login loop. | Compose `PortfolioScene.kt`; iOS `PortfolioScene.swift` / `AppScene.swift` (callback only) |
| **C4** | When the session has ended, "+ Add transaction" / account "+ Add" are disabled with a one-line reason ("Sign in again to make changes."); offline keeps Add enabled (unchanged; the save explains its own failure) unless D-2b says otherwise. | `PortfolioScreen.kt`, `PortfolioScreen.swift`, `PortfolioDisplay.kt` |

Proposed banner contract (C2):

| State (kind, offline) | Title | Message | Action |
|---|---|---|---|
| SESSION (cached or not) | "Signed out" → copy proposal: **"Sign in again to refresh"** | E.1 message ("Your session has ended. Sign in again to continue.") + "Showing your last saved portfolio." when cached | **Sign in again** |
| NETWORK, cached | "Offline · last saved portfolio" (unchanged) | unchanged | Retry |
| SERVER, cached | **"Couldn't refresh your portfolio"** (existing `REFRESH_FAILED_TITLE`) | server message + "Showing your last saved portfolio." | Retry |
| save / delete / account rejection | unchanged (D3.1) | unchanged | Dismiss |

## 4. Unchanged

`PortfolioEngine`, `Decimal`, ledger, validation (`PortfolioFormRules.check` still only re-runs `PortfolioEngine.validate`), FX sources and rates,
server code and API contracts, MOCK fixtures (including the synthetic USD profile for TD.TO), existing transactions (no rewriting), default trade
currency (no ticker-suffix inference, no account-currency default), allocation percentages, the auth state machine and token mappers, Home /
Watchlist / Practice offline handling, typography, spacing, colours, navigation structure.

## 5. Risks and regression impact

- **B1** changes field order: existing tests on `redesignOrder` / `sectionFields` / `moreOptionsOpen` will need updating (intended). Keyboard Next order
  on iOS follows `textFields` (pickers aren't in it), so it's unaffected. "More options" may become empty for Deposit-type forms; check `sectionFields(MORE)`
  still has Notes/Settlement for every type.
- **B3** could hide an intentional cash ≠ trade setting: only moves cash when it was equal, the same as Android.
- **C1** touches the shared `ServerBackedRepository` used by Watchlist and other repositories: additive field only, no behaviour change for other
  consumers; covered by existing repository tests plus new ones.
- **C3** sign-out discards the cached portfolio on the device (by design). Risk of the owner expecting cached data to stay: documented in the banner copy.
- iOS inline title at AX5: the nav bar title has a fixed size, but it's verified on device anyway; fallback copy "New account" (D-1).

## 6. Test and screenshot matrix

**Automated (new/updated):**
- `PortfolioFormRules`: Trade currency in AMOUNTS for every type that shows it; `currencyNote` null when equal, text when different, null without an
  account; "More options" content per type.
- `PortfolioDisplay`: banner title/message/action for SESSION, NETWORK, SERVER × cached/not, and rejections unchanged; Add-disabled reason only for
  SESSION.
- `ServerBackedRepository`: `AccountException` → SESSION, `StockStepsApiException` → SERVER, IOException → NETWORK; success clears the kind.
- Then sequentially: core JVM, core iOS, shared Android host, shared iOS, `assembleDebug`, unsigned `xcodebuild`. Server tests: not rerun unless C1
  touches a server-shared class (it doesn't; `:core` is shared by the server, so `:server:test` runs alone at the end).

**Device (local MOCK; Auth emulator only with owner approval). Before/after captures in `docs/design/portfolio_phase_e_2/`:**

| # | Scenario | Platform |
|---|---|---|
| 1 | Account sheet title: Large, AX5; keyboard shown/hidden; Save, Cancel, empty-name disabled | iPhone 16 |
| 2 | Account dialog title wraps (copy change only) 1.0× / 2.0× | Android 360 dp |
| 3 | Add transaction in a CAD account with a typed TD.TO: Trade currency visible in Amounts; note shown; switch to CAD → note gone, no FX field | both |
| 4 | Edit an existing USD transaction: currency kept, note shown | both |
| 5 | History/details still show the recorded currency | both |
| 6 | Session ended (Auth emulator restart): new banner title, Sign in again → Login; after sign-in the portfolio loads; Add disabled with reason | iOS (Android if a stale session can be produced) |
| 7 | Network down (MOCK stopped): Offline banner + Retry unchanged; Retry works after restart | both |
| 8 | Server error (e.g. a 5xx route in MOCK, if reachable without server changes; else NOT RUN): "Couldn't refresh…" + Retry | both |
| 9 | Small-screen review: delete dialog at 2.0×/AX5, history filters scroll, account selector labels, long names/values, primary actions reachable with keyboard; light + dark | both |

Each row records PASS / FAIL / BLOCKED / NOT RUN. **TalkBack, VoiceOver and Accessibility Inspector: NOT VERIFIED (deferred by the owner).** Static
semantics checks are reported as such, never as screen-reader results.

## 7. Owner decisions

| ID | Decision | Recommendation |
|---|---|---|
| D-1 | Account title copy: "New investment account" (fallback "New account" if it doesn't fit inline at AX5) | Approve |
| D-2 | Banner classification (C1) and "Sign in again" = sign out → Login (C3), accepting the cached portfolio is cleared on sign-out | Approve |
| D-2b | Also disable Add while offline (not only for an ended session)? | No: the save already explains its failure; keep scope small |
| D-3 | Stop sending the trade currency as `instrument.currency` when the security wasn't chosen from metadata (send null) | Defer to a separate phase: it changes what's stored for new transactions, and the server fallback then returns "no price" when the profile is down |
| D-4 | iOS Account name: persistent label like the transaction form (P3) | Include (small, same pattern as D1) or record only |
| D-5 | MOCK synthetic profile says TD.TO is USD/NASDAQ (server fixture fidelity, backend session) | Record for the backend session; not in E.2 |
| D-6 | Allocation proposal: add the five clarifications in §2-E, then schedule A1 as its own phase | Approve |

## 8. Estimated scope

- Code: about 8 files (2 core, 4 shared, 3 iOS), around 150–250 lines including tests. No server, fixture or API change.
- Tests: about 15–20 new/updated cases; one full sequential suite run plus both builds (~45–60 min).
- Device: rows 1–9 on both platforms (~1.5–2 h). Row 6 needs the Auth emulator (owner approval) and the owner's sign-in.

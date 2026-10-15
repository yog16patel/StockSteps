# Company Search refinement — Phase 5D.5.5C: search experience and entry points

- **Status:** implemented and verified on Android and iOS against the local MOCK server. Approved by the owner; committed locally as "Refine Company Search for Global UI Refinement Phase 5D.5.5C with a cleaner Android search header, local recent searches, search states with Retry and the shared result rows in the Comparison picker on Android and iOS" (**not pushed**).
- **Baseline:** `08723a6` (5D.5.5B, committed and pushed; clean tree). Plan: `docs/COMPANY_SEARCH_REFINEMENT_PHASE5D_5_5_PLAN.md` (D1–D8).
- **Reference:** the owner's "Phase 5D.5.5C — Company Search UI Reference (Android, 360 dp / iOS, iPhone 16)" image, followed closely, except:
  - initials avatars instead of logos (D5);
  - no typing suggestions (D6) — typing shows "Searching…", then results.
- **Review package:** `docs/reviews/phase5d_5_5c/` (two contact sheets plus `CHATGPT_REVIEW_PACKAGE.md`; `captures/` is git-ignored).

## 1. Implemented
- **C1 header:**
  - **Android:**
    - the "StockSteps" headline and outlined Material field are gone;
    - the top bar "Search stocks" (back) sits above a pinned, borderless `StockSearchField` ("Search company or ticker", magnifier, clear,
      Search key). It has a filled surface with no resting outline; the focus outline stays.
    - It auto-focuses on open; later query changes never dismiss the keyboard.
  - **iOS:** keeps the native `.searchable` sheet (Cancel, keyboard, focus). The prompt is now "Search company or ticker".
  - Debounce (300 ms) and cancellation are unchanged.
- **C2 recent searches (core `search/RecentSearches.kt`):**
  - only *selected* results are recorded, never typed queries;
  - newest first, at most 10; a repeat moves to the top;
  - one entry per listing (`SearchRanking.ticker`: GOOG ≠ GOOGL, BRK.B = BRK-B);
  - stored as JSON in one local key, `stocksteps.recentSearches`: Android SharedPreferences (`AndroidRecentSearchStorage`), iOS UserDefaults
    (`RecentSearchStorage.swift`, one shared store);
  - corrupt data reads as empty and is rewritten on the next selection;
  - no server, Firestore or account (works in guest mode).

  On an empty query: "Recent searches" + "Clear all" (spoken "Clear recent searches"; no confirmation, as it only clears this list) with the
  shared result row; otherwise the hint "Search by company name or ticker symbol." Every entry point that uses the main search
  (Home/Markets/Watchlist/Learn → Company Details, Portfolio add, Practice) records and shows recents. A recent entry opens the same
  destination as a result.
- **C3 states (one rule, `SearchDisplay.phase`):**
  - Recent / Hint / Searching… (compact spinner, polite announcement) / Results / No results / Error.
  - **No results:** "No companies found — Try the full company name or a different ticker symbol." + "Try a different search" (clears).
  - **Error:** "Couldn't load search results — Check your connection and try again." + Retry (primary).
  - Offline isn't distinguished: the client has no reliable signal, so it uses the general error.
  - **Android:** new `StockSearchViewModel.retrySearch()` re-runs the current query once; a double tap sends one request. Results apply only
    while their query is current.
  - **iOS:** keeps its existing cancellation.
- **C4 Comparison picker:**
  - **Android:** the small `AlertDialog` is now a full-screen "Add to comparison" page (back arrow, borderless field). **iOS:** the existing
    sheet, titled "Add to comparison".
  - Both use the shared result row, the server's order, the same states and Retry.
  - Kept: add/replace callbacks, the 8-result cap, "Already added" (now a dimmed, disabled row with the note, spoken), dismissal. A pick never
    opens Company Details.
  - Stale-response protection: a newer query or Retry cancels the previous task, and a cancelled task never writes results.
- **C5 visual:**
  - borderless field, lists and states, with subtle inset dividers;
  - design-system tokens only;
  - large text uses the 5D.5.5B stacked row (badge under the name when it doesn't fit).
- **Not changed:**
  - `SearchRanking` tiers and tie-breaks, Best-match eligibility, server filter/scope/cache, FMP configuration and API contract;
  - legacy inline pane and tablet sidebar (D7).

## 2. Files
- **core:**
  - `search/RecentSearches.kt` (new): `StringStorage`, `InMemoryStringStorage`, `RecentSearches`, `RecentSearchStore`;
  - `search/SearchDisplay.kt`: copy and `phase`;
  - test `search/RecentSearchesTest.kt` (new).
- **shared (Compose):**
  - `presentation/stocksearch/StockSearchPane.kt` (rewritten), `StockSearchScene.kt`, `StockSearchScreen.kt`, `StockSearchViewModel.kt`
    (`retrySearch`);
  - `presentation/screener/ComparisonScreen.kt` (full-screen picker);
  - `designsystem/components/StockSearchResultRow.kt` (`enabled`, `note`), `StockSearchBar.kt` (`bordered`), `StockStates.kt`
    (`primaryRetry`, `retryText`);
  - `designsystem/icons/StockIcons.kt` (`ErrorOutline`, `ArrowBack`);
  - `App.kt`, `presentation/AppNavigation.kt` (recent store passed through);
  - test `StockSearchViewModelTest.kt` (+1).
- **Android app:** `AndroidRecentSearchStorage.kt` (new), `MainActivity.kt`.
- **iOS:**
  - `RecentSearchStorage.swift` (new);
  - `StockSearchScreen.swift` (rewritten; `SearchStateView`, `searchListRow`), `StockSearchScene.swift`, `StockSearchViewModel.swift`,
    `StockSearchUiState.swift`;
  - `DesignSystem/SearchResultRow.swift` (`enabled`, `note`);
  - `ScreenerScenes.swift` (`AddCompanySheet`).

All new parameters are optional, and their defaults keep current behaviour.

## 3. Tests (commands run from the repo root)
| Command | Result |
|---|---|
| `./gradlew :core:jvmTest` | PASS 554/0 (+5 recent/phase tests) |
| `./gradlew :core:iosSimulatorArm64Test` | PASS 554/0 |
| `./gradlew :server:test` | PASS 502/0 |
| `./gradlew :app:shared:testAndroidHostTest` | PASS 264/0 (+1: out-of-order responses, error vs no results, Retry, double Retry, clear during a request) |
| `./gradlew :app:shared:iosSimulatorArm64Test` | PASS 260/0 |
| `./gradlew :app:androidApp:assembleDebug -PstockstepsMockBackendUrl=http://127.0.0.1:8081` | PASS (final code) |
| `xcodebuild … STOCKSTEPS_MOCK_BACKEND_URL=http://localhost:8081` (iPhone 16) | PASS (before the Android-only Retry-label change; no Swift change after it) |

One first-run failure was my own test's expectation: a double tap on Retry sends **one** request, not two. The behaviour is correct, and the
assertion was fixed.

## 4. Device QA (local MOCK)
Android: emulator-5554, 360 dp. iOS: iPhone 16, guest.

| Scenario | Android | iOS |
|---|---|---|
| 1 Empty query, no recents (hint) | PASS | PASS |
| 2 Several recents | PASS | PASS |
| 3 Select AAPL → Company Details, reopen → AAPL listed | PASS | PASS |
| 4 GOOG and GOOGL kept separate | PASS | PASS |
| Recents persist after app restart | PASS (force-stop) | PASS (relaunch) |
| Recent entry → same destination; moves to top | PASS | Destination PASS; move-to-top tested in core |
| 5 Clear all | PASS | NOT RUN (same store; core-tested) |
| 6 `apple` ranked, Best match | PASS | PASS |
| 7 `GOOG` (no label) | 5D.5.5B PASS | PASS |
| 8 `xyznonexistent123` | PASS | PASS |
| 9 Loading ("Searching…") | Seen during typing; not captured | Not captured |
| 10 Error (MOCK server stopped) → Retry after restart | PASS | PASS |
| 11 Rapid typing / out-of-order | Unit-tested (ViewModel) | Existing task cancellation; not device-tested |
| 12 Comparison picker: ranked rows, "Already added" disabled | PASS | PASS |
| 13 Comparison pick → added (2 of 4), no Company Details | PASS | PASS |
| 14 Back to the originating screen | PASS | PASS |
| Dark | 1.0× and 2.0× PASS | Default PASS |
| Large text | 1.3× and 2.0× PASS (results stay above the keyboard) | AX XL and AX5 PASS |
| Screen readers | Accessibility tree: one label per row (incl. "Already added"), Clear all spoken label | VoiceOver NOT RUN |

**Device state afterwards:**
- emulator: Light, 1.0×, `adb reverse` removed;
- simulator: Default size, light;
- MOCK server stopped;
- recents left on the devices: Android none (cleared), iOS GOOGL / GOOG / AAPL.

## 5. Screenshots
`docs/reviews/phase5d_5_5c/CHATGPT_UI_REVIEW_ANDROID.png` and `CHATGPT_UI_REVIEW_IOS.png`. All raw captures are in
`docs/reviews/phase5d_5_5c/captures/` (git-ignored); the file names give platform, scale, theme, MOCK and scenario.

## 6. Known issues
- **Android error capture:** taken before the label change, so it shows "Try again"; the final build says "Retry" (not recaptured).
- **Android picker:** a full-screen dialog window, so the status-bar strip shows the dialog scrim (grey) rather than the page colour.
- **iOS "Try a different search":** clears the query but doesn't raise the keyboard again (the search field was already active).
- **iOS AX XL:** the compact "Best match" badge text scales about as large as the company name.
- **iOS search sheet:** keeps the previous query when reopened (existing behaviour, unchanged).

## 7. Deferred
- Logos (D5) and typing suggestions (D6) stay out, as decided.
- Removing the legacy inline pane and tablet sidebar (D7).
- VoiceOver/TalkBack walkthrough (owner).

## 8. Next
Owner review of 5D.5.5C, then 5D.5.5D:
- full device matrix and parity, including the not-run rows above;
- screen readers with the owner;
- the polish items in §6.

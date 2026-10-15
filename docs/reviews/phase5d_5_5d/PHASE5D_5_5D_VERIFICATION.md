# Phase 5D.5.5D — Company Search final verification & polish

- **Status:** implemented and verified on the local MOCK server. Approved by the owner; committed and pushed as "Finish Company Search for Global UI Refinement Phase 5D.5.5D with an edge-to-edge Comparison picker, iOS search refocus and empty reopen, a smaller Best match label, logos beside large text and logos for older recent searches on Android and iOS" (`458e6a2`).
  The final verification and closure pass (2026-10-15) is in **§8**; it supersedes the "not re-run" and "NOT RUN" cells in §4.
- **Baseline:** `65ad004` (search logos, pushed; clean tree, 12 GB free).
- **Reference:** the owner's "Phase 5D.5.5D — Company Search Final UI Reference" image, followed. At large text the logo stays on the left and
  the "Best match" label stays small.
- **Owner instruction (mid-phase):** proceed with recommended options without asking; deleting rebuildable data is allowed. No deletion was
  needed.

## 1. Defects found and fixed
| # | Defect | Fix | Files |
|---|---|---|---|
| D1 | Android "Add to comparison": grey dialog scrim behind the status bar | New `FullScreenDialog`, edge to edge: the page colour sits behind the system bars, with icon colour from the page brightness. iOS/JVM: plain full-width dialog | `presentation/components/FullScreenDialog.kt` (+ `.android/.ios/.jvm`), `ComparisonScreen.kt` |
| D2 | iOS "Try a different search" cleared the query but the field lost focus (no keyboard; typing went nowhere) | `.searchFocused` + re-activate search after 0.35 s (immediate and same-moment attempts didn't work; verified by typing with no tap) | `StockSearchScreen.swift`, `ScreenerScenes.swift` |
| D3 | iOS AX XL: "Best match" badge text as large as the company name | The label grows only up to 1.3× (Android `SEARCH_BADGE_MAX_SCALE`, iOS `UIFontMetrics` capped). Name, ticker and exchange keep full size; the label is also spoken | `StockSearchResultRow.kt`, `SearchResultRow.swift` |
| D4 | iOS search sheet reopened with the previous query (Android always starts empty) | Each opening clears the query (unless opened for a specific stock) and shows recent searches | `StockSearchScene.swift` |
| D5 | Large-text row didn't match the reference: the avatar moved above the text | The logo stays on the left at every size; only ticker and exchange stack at very large text; dividers inset to the text | `StockSearchResultRow.kt`, `SearchResultRow.swift`, `StockSearchPane.kt` |
| D6 | Recent searches saved before logos had no logo forever | `RecentSearchStore.refreshLogos(results)`: an entry without a logo takes it from a completed search that already lists the same listing — no extra request, order unchanged | `RecentSearches.kt`, `StockSearchScene.kt`, `StockSearchViewModel.swift`, `RecentSearchesTest` (+1) |

## 2. Unchanged (confirmed)
- Ranking (`SearchRanking`) and Best-match eligibility. MOCK `AAPL`, `MSFT`, `microsoft`, `GOOGL` and mixed-case `ApPlE` return the same
  order as before.
- Search API contract and providers.
- Entry-point navigation and Comparison limits ("1 of 4", Back returns to Compare).

## 3. Automated tests
Commands run sequentially from the repo root (final code; the last iOS-only Swift edits were rebuilt with `xcodebuild`).

| Command | Result |
|---|---|
| `./gradlew :core:jvmTest` | 556 tests, 1 failure: `PortfolioSessionStateTest` timed out (known intermittent, tracked in `CLAUDE.md`). Rerun alone: PASS 3/0 |
| `./gradlew :core:iosSimulatorArm64Test` | PASS 556/0 |
| `./gradlew :server:test` | PASS 502/0 |
| `./gradlew :app:shared:testAndroidHostTest` | PASS 264/0 |
| `./gradlew :app:shared:iosSimulatorArm64Test` | PASS 260/0 |
| `./gradlew :app:androidApp:assembleDebug -PstockstepsMockBackendUrl=http://127.0.0.1:8081` | PASS |
| `xcodebuild` iPhone 16, MOCK | PASS (final Swift) |

## 4. Device QA (local MOCK)
Android: emulator-5554, 360 dp. iOS: iPhone 16, guest.

| Scenario | Android | iOS |
|---|---|---|
| Exact ticker / name / mixed case (API) | PASS | PASS (same API) |
| `apple`: Best match + real logos, order unchanged | PASS | PASS |
| `GOOG`: no label; GOOGN/GOOGM without a logo show initials | PASS | PASS |
| Recent searches with logos; old entries refresh from a search | Android recents were already re-selected | PASS (GOOG/GOOGL refreshed; AAPL kept initials until listed) |
| Reopen search → empty field + recents | PASS (existing) | PASS (fixed, D4) |
| No results | 5D.5.5C PASS (unchanged) | PASS |
| "Try a different search" refocuses | PASS (existing) | PASS (fixed, D2) |
| Error + Retry | 5D.5.5C PASS (logic unchanged; not re-run) | 5D.5.5C PASS (not re-run) |
| Rapid typing / stale responses | Unit test PASS (5D.5.5C) | NOT RUN on device |
| Result → Company Details | PASS (Apple) | PASS (Apple) |
| Comparison picker: logos, "Already added", Back | PASS; status bar fixed (D1) | PASS |
| Dark | 1.0× and 2.0× PASS | Default PASS |
| Large text | 2.0× PASS (logo left, small badge) | AX XL and AX5 PASS (D3/D5) |
| Clear all, persistence, guest | 5D.5.5C PASS (unchanged) | 5D.5.5C PASS (unchanged) |
| Screen readers | NOT RUN (owner) | NOT RUN (owner) |

**Device state afterwards:** emulator Light 1.0×, `adb reverse` removed; simulator Default, light; MOCK server stopped.

## 5. Screenshots
- `CHATGPT_UI_REVIEW_ANDROID.png` and `CHATGPT_UI_REVIEW_IOS.png`, numbered to match the reference.
- The Android no-results, error and recent panels reuse the 5D.5.5C captures (unchanged code paths).
- Raw captures are in `captures/` (git-ignored).

## 6. Remaining issues
- **Error screens not re-captured:** the Android error screenshot is still the 5D.5.5C one, labelled "Try again" (the build says "Retry"); the
  error state wasn't re-run in 5D.5.5D.
- **Small logos at accessibility sizes:** the logo tile is a fixed size, so it looks small next to AX5 text (as in the reference).
- **FMP logo licence** is not yet confirmed by the owner.
- **Known intermittent test:** `PortfolioSessionStateTest` (JVM) still fails occasionally.

## 7. Recommendation
5D.5.5 can be closed after the owner's review and screen-reader pass. (Superseded by §8.6.)

## 8. Final verification & closure (2026-10-15, on `458e6a2`)
- **Scope:** verification only, on the local MOCK server (:8081). No production or live provider calls.
- **Code changes:** none. No new defects were found, so no fixes were needed. Only the docs and the git-ignored captures changed.

### 8.1 Automated tests (run sequentially, one full run, final code)
| Command | Result |
|---|---|
| `./gradlew :core:jvmTest` | PASS 556/0 (`PortfolioSessionStateTest` passed in this run) |
| `./gradlew :core:iosSimulatorArm64Test` | PASS 556/0 |
| `./gradlew :server:test` | PASS 502/0 |
| `./gradlew :app:shared:testAndroidHostTest` | PASS 264/0 |
| `./gradlew :app:shared:iosSimulatorArm64Test` | PASS 260/0 |
| `./gradlew :app:androidApp:assembleDebug -PstockstepsMockBackendUrl=http://127.0.0.1:8081` | PASS |
| `xcodebuild` (iPhone 16, MOCK) | PASS |

### 8.2 Device matrix
Android: emulator-5554, 360 dp, guest. iOS: iPhone 16 simulator, guest.

| Check | Android | iOS | Evidence |
|---|---|---|---|
| Error copy "Couldn't load search results" + **Retry** (MOCK server stopped) | PASS | PASS | `final-*-error.jpg` |
| Retry sends a new request; success replaces the error | PASS | PASS (server restarted → `amzn` results; `intel` → "No companies found", which is correct because MOCK has no fixture for it) | `final-*-retry-results.jpg` |
| Double-tap Retry → one request, no flicker | PASS (ViewModel unit test: one request) | PASS on screen: one result list. iOS `search()` cancels the task in flight (code review). Requests not counted because the server doesn't log them | — |
| Rapid typing a → ap → apple | PASS | PASS: only the "apple" results show | `final-ios-…-rapid-apple.jpg` |
| Clear during a request → recents, no late results | PASS | PASS | `final-ios-…-clear-during-request.jpg` |
| A new query before the previous completes (tesla → msft) | PASS | PASS: only MSFT results | `final-ios-…-newer-query-wins.jpg` |
| Cancel or close the sheet while a request is pending | PASS | PASS: no stale results, no crash; reopens empty | `final-ios-…-cancel-pending.jpg`, `…-reopen-empty.jpg` |
| Navigate away (Company Details) and return | PASS | PASS: empty field and recents | `final-ios-…-return-after-details.jpg` |
| Old recents acquire logos from completed results; order unchanged | PASS | PASS (AAPL picked up its logo; order AAPL, GOOGL, GOOG kept) | `final-ios-…-relaunch-recents.jpg` |
| Initials fallback (recent entry without a logo, GOOGN) | PASS (results, 5D) | PASS | `final-ios-…-recent-initials.jpg` |
| GOOG and GOOGL distinct | PASS | PASS | same |
| Guest persistence across app restart | PASS (reinstall) | PASS (terminate + relaunch, Continue as guest) | `final-ios-…-relaunch-recents.jpg` |
| Clear all (storage emptied, hint shown) | PASS | PASS (UserDefaults key removed) | `final-*-clear-all.jpg` |
| No extra provider requests | PASS: `refreshLogos` only reads completed results; MOCK makes zero provider calls | PASS | code + MOCK |
| Android 1.3× (recent, results, Best match) | PASS | — | `final-android-1.3x-*.jpg` |
| Android 1.0× / 2.0× / dark; iOS light, dark, AX XL, AX5; Comparison picker | PASS (§4, same code) | PASS (§4) | §5 captures |
| TalkBack / VoiceOver walkthrough | **NOT RUN** | **NOT RUN** | see §8.3 |

**Device state afterwards:**
- emulator: Light, 1.0×, `adb reverse` removed;
- simulator: light, Default size, guest;
- MOCK server stopped.

The simulator's recent searches now hold GOOGN only, which is test data.

### 8.3 Screen readers: NOT RUN
TalkBack focus can't be driven reliably by automation here, and VoiceOver doesn't run in the iOS Simulator. **No PASS is claimed.**

**Code-level evidence (Android accessibility tree, 1.3×):**
- field: "Search company or ticker";
- clear button: "Clear search";
- rows: "Apple Inc., AAPL, NASDAQ, best match";
- headers: "Recent searches", "Best match" and "Other results";
- "Clear all".

**Manual checklist for the owner (both platforms):**
1. **Search field:** the label is announced; once typing starts, the clear (X) button is reachable and its name is announced.
2. **Results:** each row reads name, ticker and exchange as one element. The Best-match row adds "best match" once, and the label isn't read twice. Logos aren't announced separately.
3. **Headings:** the section titles are headings, so heading navigation works.
4. **Recent searches:** "Clear all" is announced as clearing recent searches. After activation, focus lands sensibly and the hint is read.
5. **No results:** the title and message are announced once; "Try a different search" refocuses the field.
6. **Error:** "Couldn't load search results. Check your connection and try again." is announced once; Retry is reachable and announces the results.
7. **Comparison picker:**
   - the title is announced;
   - "Already added" rows read as dimmed or disabled, with the note;
   - Back or Close returns to Compare.
8. **Large text with the screen reader on:** no truncated names, and the order matches the visual order.

### 8.4 Licensing
The FMP company-logo commercial display licence is **UNVERIFIED** and stays that way until the owner or FMP confirms it. Nothing in this phase approves production rights to show these logos. The initials fallback works without logos, so logos can be switched off server-side (`logoUrl = null`) if rights are refused.

### 8.5 Remaining issues and risks
- Screen-reader walkthroughs are pending with the owner (§8.3).
- The FMP logo licence is UNVERIFIED and is a launch dependency for showing logos in production.
- `PortfolioSessionStateTest` is still a known intermittent failure (it passed in this run). It is tracked separately and was not changed.
- Logos look small at AX5 (fixed tile size). This is cosmetic and matches the reference.
- Duplicate Retry requests aren't counted on device (no server request log). This relies on the unit test plus code review.

### 8.6 Recommendation
**CLOSE WITH DEFERRED ITEMS.** Company Search (5D.5.5) meets its scope on both platforms. The deferred items are the owner's screen-reader pass and the FMP logo licence confirmation, which must happen before production use of logos.

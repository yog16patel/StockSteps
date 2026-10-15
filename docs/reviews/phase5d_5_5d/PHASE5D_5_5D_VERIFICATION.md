# Phase 5D.5.5D — Company Search final verification & polish

- **Status:** implemented and verified on the local MOCK server. Approved by the owner; committed locally as "Finish Company Search for Global UI Refinement Phase 5D.5.5D with an edge-to-edge Comparison picker, iOS search refocus and empty reopen, a smaller Best match label, logos beside large text and logos for older recent searches on Android and iOS" (**not pushed**).
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
5D.5.5 can be closed after the owner's review and screen-reader pass.

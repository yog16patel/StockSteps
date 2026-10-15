# Company Search refinement — Phase 5D.5.5B: ranking and search-result rows

- **Status:** implemented and verified on Android and iOS against the local MOCK server. Approved by the owner; committed locally as "Add Company Search ranking and result rows for Global UI Refinement Phase 5D.5.5B with deterministic server-side relevance, a conservative Best match label and borderless search result rows on Android and iOS, plus the 5D.5.5A search audit and plan" (**not pushed**). The iOS build blocker was cleared after the owner approved deleting the project build directories (§3.1).
- **Baseline:** `0c5c4c4`. Plan: `docs/COMPANY_SEARCH_REFINEMENT_PHASE5D_5_5_PLAN.md`, approved with D1–D8 as recommended.
- **Review package:** `docs/reviews/phase5d_5_5b/` (Android and iOS contact sheets plus `CHATGPT_REVIEW_PACKAGE.md`; `captures/` is git-ignored).

## 1. Changes
- **Ranking (core, used by the server):** `search/SearchRanking.kt`, pure and deterministic. Match tiers:
  1. exact ticker (`.` = `-`, so BRK.B = BRK-B);
  2. exact name (one legal suffix ignored);
  3. ticker prefix;
  4. name starts with the query as a whole word;
  5. name starts with the query as part of a word;
  6. whole word later in the name;
  7. word prefix later in the name;
  8. substring;
  9. provider-only.

  Ties: a major exchange before OTC (D2), then the provider's order, then the symbol. Nothing is dropped.
- **Server:** `StockService.search` keeps the USD/US scope (D1) and dedupe, then applies `SearchRanking` to everything both FMP endpoints
  returned. The 6 h cache stores the ranked list. No endpoint, DTO or provider change; no `limit` change (D8).
- **Display rules (core):** `search/SearchDisplay.kt`:
  - Best match only for a unique exact ticker or name, with no other result for the same company. GOOG/GOOGL → no label.
  - Sections: "Best match" / "Other results", or one "Results" section.
  - Row text "TICKER · EXCHANGE" and one spoken label.
- **Android:** new `designsystem/components/StockSearchResultRow.kt` (initials avatar (D5), name with an INFO "Best match" badge, ticker ·
  exchange, chevron; one spoken element with a button role). From 1.75× the avatar goes above the text and ticker and exchange stack.
  `StockSearchPane` draws the sections with `StockSectionHeader` and inset dividers (full width when stacked). The header is unchanged (5D.5.5C).
- **iOS:**
  - new `DesignSystem/SearchResultRow.swift` (same row; stacks from AX3; the badge follows the name while both fit, otherwise it goes under
    it);
  - `StockSearchScreen` uses the sections in a plain, borderless list.
- **D1 copy fixes:**
  - iOS: the empty-state text is now "…listed on a US exchange", and the "USD · CAD" chip is removed.
  - Comparison: the hint is now "e.g. Apple or AAPL" (was "RY.TO").
- **Deviation:** the Best-match badge uses the INFO (blue) style instead of the reference's green. Green means a gain in StockSteps, and a match
  label isn't a gain.

## 2. Files
- **New:**
  - `core/.../search/SearchRanking.kt`, `core/.../search/SearchDisplay.kt`;
  - `core/src/commonTest/.../search/SearchRankingTest.kt`, `SearchDisplayTest.kt`;
  - `app/shared/.../designsystem/components/StockSearchResultRow.kt`;
  - `app/iosApp/iosApp/DesignSystem/SearchResultRow.swift`.
- **Changed:**
  - `server/.../service/StockService.kt`, `server/src/test/.../StockSearchTest.kt` (+1 test);
  - `app/shared/.../presentation/stocksearch/StockSearchPane.kt`, `app/shared/.../presentation/screener/ComparisonScreen.kt` (hint only);
  - `app/iosApp/iosApp/StockSearchScreen.swift`.
- **Not changed:** API/DTO, providers, the cache policy, entry-point navigation, the legacy inline pane and sidebar (D7), header and states
  (5D.5.5C).

## 3. Verification
| Check | Result |
|---|---|
| core JVM (incl. 8 new search tests) | PASS 549/0 |
| server (incl. the new ranking test) | PASS 502/0 |
| shared Android host | PASS 263/0 |
| `:app:androidApp:assembleDebug` | PASS |
| core iOS / shared iOS tests | PASS 549/0 / 259/0 |
| `xcodebuild` (iOS, MOCK) | PASS (after §3.1; one compile fix: file-private `space`/`type`/`dims` in `SearchResultRow.swift`) |
| MOCK API order (`curl`, local) | PASS — "apple": AAPL, APLE, AAPI, APRU, APPLX, AAPX, AAPY, GAPJ, PEGY, PAPL, MLP, PNPL, MNAO; "GOOG": GOOG, GOOGL, GOOGN, GOOGM |
| Android 1.0× light: Best match + Other results, server order, avatars, chevrons | PASS |
| Android GOOG: one "Results" section, no label | PASS (captured against the LAN MOCK host; the local server returns the same order) |
| Android row → Company Details (Apple) | PASS |
| Android spoken labels ("Apple Inc., AAPL, NASDAQ, best match") | PASS (accessibility tree) |
| Android 1.3× / 2.0× (stacked row, no truncation of ticker or exchange) | PASS |
| Android dark (1.0×, 2.0×; restored to Light) | PASS |
| iOS Default light: Best match + Other results, server order, avatars, chevrons | PASS |
| iOS GOOG: one "Results" section, no label | PASS |
| iOS empty state: "…listed on a US exchange", no "USD · CAD" chip | PASS |
| iOS row → Company Details (Apple) | PASS |
| iOS dark (Default) | PASS |
| iOS AX XL / AX5: stacked row, ticker and exchange whole; at AX5 the badge sits under the name | PASS |

### Note: Android MOCK URL
`~/.gradle/gradle.properties` sets `stockstepsMockBackendUrl` to the **LAN** MOCK host. Debug builds therefore talk to the Ubuntu MOCK server,
not the local one, unless built with `-PstockstepsMockBackendUrl=http://127.0.0.1:8081`. This phase used the override. Earlier Android checks
in 5D.5.4 (UI-only) used the LAN host.

The emulator now has the localhost build installed. Rebuild without the override to return to the LAN default.

### 3.1 Disk space (owner-approved clean-up)
1. Deleted Xcode DerivedData (2.3 GB).
2. Deleted 10 unavailable simulators (`xcrun simctl delete unavailable`); the booted iPhone 16 / 16 Pro were kept.
3. Deleted the project build directories (`build/`, `core/build`, `server/build`, `app/*/build`, with Gradle stopped first). All rebuilt.

Free space also moved by several GB because of other activity on the Mac.

**Note:** while capturing AX XL, a stray tap hit the keyboard microphone and iOS asked "Enable Dictation?". It was answered **Not Now**
(nothing enabled). The simulator was restored to Default size and light.

## 4. Remaining
- **5D.5.5C:**
  - header (`StockSearchField` / "Search companies"), states (initial with recent searches (D6), loading, no results, error + Retry on
    Android);
  - the Comparison dialog on the shared row;
  - keyboard handling.

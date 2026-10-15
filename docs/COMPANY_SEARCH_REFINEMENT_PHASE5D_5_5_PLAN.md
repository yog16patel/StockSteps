# Company Search refinement — Phase 5D.5.5 plan (5D.5.5A: audit + plan only)

- **Status:** approved by the owner with D1–D8 as recommended; committed with 5D.5.5B (see `docs/COMPANY_SEARCH_REFINEMENT_PHASE5D_5_5B.md`).
- **Baseline:** `0c5c4c4` (5D.5.4D, pushed; `main` = `origin/main`, clean tree).
- **Owner reference:**
  - Image: "Phase 5D.5.5A — Company Search UI Reference (Android & iOS)", saved as
    `docs/reviews/phase5d_5_5a/captures/owner-reference-5d55a.webp` (git-ignored).
  - Shows: initial (recent searches), typing (query suggestions), results (Best match + Other results with logo, ticker · exchange,
    sector/type), no results, error, dark, 2.0× / AX5.
- **Subphases:**
  - 5D.5.5A: this audit and plan.
  - 5D.5.5B: ranking and result rows.
  - 5D.5.5C: states and entry points.
  - 5D.5.5D: device QA, parity and accessibility.

## 1. Executive summary
Search works and is safe, but it doesn't reliably put the company a beginner means first, and it doesn't look like the rest of StockSteps.

**Ranking and data:**
- **Exact ticker only:** the server lifts an exact ticker match to the top; everything else keeps the provider's order. "apple" lists
  *Appleseed Fund* 2nd and four OTC micro-caps before *Apple Hospitality REIT*, and "Pineapple …" names sit among them.
- **USD-only scope that the copy contradicts:** results are filtered to USD listings on US exchanges. The iOS sheet says "in USD or CAD" and
  shows a "USD · CAD" chip, and the Comparison dialog suggests "RY.TO", yet a TSX listing can never appear.
- **Thin results:** no instrument type, sector or logo. Sector and type in the owner reference aren't available from the search response.

**UI:**
- Android uses an old Material layout: a "StockSteps" headline, an outlined field and plain text rows.
- iOS uses the system `.searchable` field with an inset grouped list.
- No recent searches, no avatars.
- Android has no retry on errors.

**Recommendation:**
1. One pure, deterministic ranking function in core, applied **on the server** to the full merged provider set.
2. A conservative **Best match** rule computed on the client from the same function (no API change).
3. One compact, borderless result row and search screen per platform, built from existing design-system parts.
4. The current per-entry-point outcomes are kept (Company Details, Portfolio add, Practice, Comparison).

## 2. Current search architecture (inspected)
| Layer | File | Behaviour |
|---|---|---|
| Server route | `server/.../Application.kt` `stockRoutes` → `GET /api/v1/stocks/search?query=` | Trims; 1–100 chars, no control characters (else 400 `INVALID_QUERY`). Admission group `MARKET_DATA` (`security/Admission.kt`, path prefix) |
| Server service | `server/.../service/StockService.kt` `searchStocks` | Cache key = trimmed, lower-cased query, `FinancialCachePolicy.SEARCH` = 6 h, failures not cached. Filter: `currency == USD` and `exchange in US_EXCHANGES` (NASDAQ, NYSE, AMEX, NYSEARCA, NYSEAMERICAN, BATS, CBOE, OTC, OTCQX, OTCQB, PNK). Dedupe `distinctBy(symbol)`. Sort: exact ticker first (stable), else provider order |
| REAL provider | `server/.../repositoryImpl/FmpStockProviderRepositoryImpl.kt` `searchStocks` | Two concurrent FMP calls, `stable/search-symbol` + `stable/search-name`, concatenated (symbol hits first). No `limit` parameter sent (provider default; **not verified**, no REAL calls in this phase). Fields: `symbol, name, currency, exchange, exchangeFullName` (`FmpSearchResult`) |
| MOCK provider | `server/.../repositoryImpl/fixture/FixtureMarketDataSource.kt` `searchStocks` | `stocks/search.json` (46 rows: 45 USD, 1 TSX/CAD) plus the 137-company sample catalog (`fixtures/stocks/catalog.json`). Match: symbol `startsWith` or name `contains`. Order = fixture order, then catalog |
| DTO | `core/.../model/StockSearchResult.kt` | `symbol, name, currency?, exchange?, exchangeFullName?` |
| Client | `core/.../network/StockStepsApi.kt` `searchStocks` → `RemoteStockRepository` → `domain/SearchStocks` (blank → empty, else trimmed) | No client-side ranking |
| Tests | `server/src/test/.../StockSearchTest.kt` (3 tests: USD/US filter keeps provider order; merge, dedupe, exact ticker first; validation and errors) | No core or UI ranking tests |

## 3. Entry-point inventory
| Entry point | Android (shared Compose) | iOS (SwiftUI) | Outcome of a selection |
|---|---|---|---|
| Home search icon | `AppNavigation` → `StockSearchRoute()` → `StockSearchScene` | `AppScene` sheet `showingSearch` → `StockSearchScene` | Company Details (`openStock(symbol)` / `detailsSymbol = symbol`) |
| Markets search | `MarketsScene` `onSearch` → `StockSearchRoute()` | `MarketsScene` `onSearch` → same sheet | Company Details |
| Watchlist "search" | `WatchListScene` `onSearch` → `StockSearchRoute()` | `WatchListScene` `onSearch` → same sheet | Company Details (saving happens there) |
| Learn → Research a company / term pages | `LearnScene` / `LearnTermScene` `onSearch` | `LearnScene`, `LearnTopicsView`, `LearnTermView` `onSearchCompanies` | Company Details |
| Guided Research "Research another" | `GuidedResearchScene` `onSearch` | iOS guided research → `researchTarget` (separate) | Company Details |
| Discovery | `DiscoveryScreen` "Search stocks" | `DiscoveryScreen` "Search stocks" | Company Details |
| Portfolio add holding | `PortfolioEntryScene` → `PortfolioSearchRoute` → same `StockSearchScene` | `PortfolioScene` own `StockSearchViewModel` + `StockSearchScene` | Returns `symbol, name, exchange, currency` to the entry form |
| Practice buy | `PracticeSearchRoute` → same `StockSearchScene` | Global sheet with `searchForPractice` | Practice order `(symbol, BUY)` |
| Comparison add/replace | `ComparisonScreen.AddCompanyDialog` (own field; 350 ms debounce; `take(8)`; single-line names) | `ScreenerScenes` add-company sheet (own `.searchable`; 350 ms) | Adds `(symbol, name)`; "already added" disabled |

**Reuse:** the main screen is already one component per platform with an `onOpenStock` callback per entry point. Only Comparison has its own
copy. The plan keeps every outcome above unchanged and passes the same callback into the new UI.

## 4. Android / iOS UI audit (inspected on devices, MOCK, Apple queries)
| Aspect | Android (`presentation/stocksearch/StockSearchPane.kt`) | iOS (`StockSearchScreen.swift`) |
|---|---|---|
| Header | Top bar "Search stocks" + back; "StockSteps" headline; Material `OutlinedTextField` "Search company or ticker" | Sheet with "Search stocks" + Done; system `.searchable` "Company or ticker"; "USD · CAD" chip |
| Initial | Empty field only | "Find your first stock — Search for a company or ticker in USD or CAD." |
| Loading | `LinearProgressIndicator` row | Centred "Searching…" (replaces results) |
| Results | Name / "AAPL · NASDAQ · USD", dividers, no avatar or chevron; `LazyColumn` keyed by `symbol` | Inset grouped "STOCKS" section (bordered container), name / same line, chevron |
| No results | "No matches found." plain text | `ContentUnavailableView.search` |
| Error | Red text ("Could not load stock data. Try again."), **no Retry** | Wi-Fi label + Retry |
| Design system | Not used (legacy `MaterialTheme`); `StockSearchField` exists but is unused here | Partly (fonts and colours) |
| Large text | Not checked in this phase | Not checked in this phase |

The Android screen and ViewModel also carry a legacy inline quote/detail pane (`StockQuoteDetail`, `StockSearchSidebar`, the quote, profile,
news and fundamentals loaders). Every current entry point passes `onOpenStock`, so that pane is only a fallback (and a two-pane layout on
large screens).

## 5. Provider and result-field analysis
- **Available per result:** `symbol` (FMP is exchange-suffixed for non-US: `SHOP.TO`, `RY.TO`; share classes as `GOOG`/`GOOGL`, `BRK-B`), `name`,
  `currency`, `exchange` (short code), `exchangeFullName`.
- **Not available:** instrument type, sector, logo, popularity, market cap. Getting them would mean one profile call per result (≈ 10–50 extra
  FMP calls per query), so they are **out of scope** by default.
- **Security identity:** `symbol` as FMP returns it. With the USD/US filter, symbols are unique in a response; exchange suffixes keep them
  unique if TSX is added. Android Company Details receives only `symbol` (fine for this identity).
- **Result caps:**
  - FMP's default page size wasn't verified (no REAL calls).
  - The server merges both endpoints before filtering, so ranking there sees everything the provider returned; a client re-rank of a page
    could not.
  - Comparison then keeps the top 8.

### Example queries (MOCK, 2026-10-15)
Expected behaviour is shown separately from actual MOCK results. REAL FMP results weren't checked.

| Query | Actual MOCK result (order) | Problem against the expected relevance |
|---|---|---|
| `AAPL` | AAPL, AAPY (ETF) | OK |
| `apple` | AAPL, APPLX *Appleseed Fund*, AAPI (OTC), APRU (OTC), APLE *Apple Hospitality REIT* (NYSE), PNPL/PEGY/PAPL/MNAO *Pineapple…*, GAPJ, AAPX (ETF), MLP, AAPY | Fund 2nd; OTC before NYSE; "Pineapple" substrings before word matches |
| `MSFT` | MSFT, MSFY | OK |
| `micro` | MSFT, MSFX, MSFY, MU *Micron* | Acceptable (name-prefix) |
| `GOOG` | GOOG, GOOGL, GOOGN, GOOGM | GOOG/GOOGL both named "Alphabet Inc." (share classes); preferred depositary shares included |
| `BRK.B` | none | MOCK has no Berkshire row; REAL FMP uses `BRK-B` (punctuation) — needs `.`↔`-` matching, **unverified** |
| `SHOP` | SHOP (NASDAQ in MOCK) | REAL lists SHOP on NYSE and SHOP.TO on TSX; TSX is filtered out |
| `VFV` | none | TSX ETF, filtered out by the USD/US scope |
| `xyznonexistent123` | none | No-results state |
| `  Apple  ` | same as `apple` | Trim works; shares the cache key |

## 6. Current ranking behaviour (evidence)
1. FMP symbol hits, then FMP name hits (provider order).
2. Filter to USD and US exchanges; `distinctBy(symbol)`.
3. A stable sort that only lifts `symbol.equals(query, ignoreCase)`.

Evidence: `StockService.search` and `StockSearchTest.tickerSearchMergesDeduplicatesAndRanksExactMatchFirst`. Nothing else is ranked: no name
match, word boundary or exchange quality.

## 7. Proposed deterministic ranking (5D.5.5B)
`core/src/commonMain/kotlin/org/example/stocksteps/search/SearchRanking.kt`: a pure function
`rank(query: String, results: List<StockSearchResult>): List<StockSearchResult>` plus `tier(query, result)`.
- **Applied on the server:** in `StockService.search` after filter and dedupe, so every client and the 6 h cache get the ranked list.
- **Also used on the client:** for the Best-match rule only.

**Normalisation (matching only; displayed values are never changed):**
- Trim, case-fold (`Locale.ROOT`), collapse inner whitespace.
- For tickers, treat `.` and `-` as equal (`BRK.B` ↔ `BRK-B`). Other characters are kept, so `GOOG` ≠ `GOOGL`.
- For names, drop punctuation and remove one trailing legal suffix from a fixed list (Inc, Incorporated, Corp, Corporation, Co, Company, Ltd,
  Limited, plc, LLC, L.P.). This is used only for the "exact name" check.

**Tiers (lower is better):**
1. **Exact ticker:** `AAPL`; `BRK.B` = `BRK-B`.
2. **Exact name:** without the legal suffix ("apple" = "Apple Inc.").
3. **Ticker prefix:** `MSF` → MSFT.
4. **Name starts with the query as a whole word:** "Apple Hospitality REIT".
5. **Name starts with the query as part of a word:** "Appleseed Fund", "Micron".
6. **Whole word later in the name:** "Golden Apple Oil & Gas".
7. **Word prefix later in the name.**
8. **Any other substring:** "Pineapple". Kept, never dropped.
9. **Provider-only:** no textual match; kept in provider order (fuzzy hits).

**Tie-breaks, in order:**
1. **Optional (owner decision D2):** listings on a major exchange (NASDAQ, NYSE, AMEX, NYSEARCA, NYSEAMERICAN, BATS, CBOE; plus TSX if D1)
   before OTC (OTC, OTCQX, OTCQB, PNK).
2. The provider's own order (keeps FMP's relevance).
3. `symbol` ascending.

The ranking is deterministic for the same provider response.

**Policies:**
- **Never:** popularity, market cap, AI or paid placement.
- **Dedupe:** by exact `symbol` (unchanged). Different share classes and listings stay separate.
- **Missing fields:** a missing exchange ranks as non-OTC (no penalty for unknown) and still shows; a missing name is impossible (the provider
  rejects it).
- **Instruments:** no type label by default (D3), because the provider gives no type; names are shown verbatim (they often say "ETF", "Fund",
  "REIT"). No instrument is renamed "company".
- **Performance:** O(n log n) over ≤ ~100 merged rows; negligible next to the provider call.

**Expected result for "apple" after B (if D2 is approved):**
1. AAPL;
2. APLE (NYSE);
3. AAPI, APRU (OTC, whole word);
4. APPLX (partial word);
5. GAPJ, AAPX, AAPY (later word);
6. Pineapple names and MLP (substring).

**Unit tests:**
- `core/src/commonTest/.../search/SearchRankingTest.kt`:
  - every tier;
  - `BRK.B` ↔ `BRK-B`;
  - `GOOG` vs `GOOGL`;
  - suffix stripping;
  - OTC tie-break on and off;
  - stable provider order;
  - no results dropped;
  - whitespace and case.
- `server/src/test/.../StockSearchTest.kt`: update the exact-ticker test and add "apple"-style ordering through `StockService`.

## 8. Proposed search UI hierarchy
**Header:**
- **Android:**
  - `StockStepsTopBar` with Back and the title "Search companies";
  - the existing `StockSearchField` (magnifier, clear, loading inside the field, IME Search, auto-focus);
  - "StockSteps" headline removed.
- **iOS:** keep `.searchable` (native keyboard, Cancel, VoiceOver) in the sheet titled "Search companies"; remove the "USD · CAD" chip (or keep
  it only if D1 adds CAD).

**Result row:** one shared row per platform, `StockSearchResultRow` (Compose) / `SearchResultRow` (SwiftUI), borderless with an inset divider
and a 48 dp minimum height.

```
[StockTickerAvatar]  Apple Inc.                      [Best match]   >
                     AAPL · NASDAQ
```

- Name: `bodySemiBold`, wraps, never truncated.
- Ticker (bold) · exchange: `small`, `textSecondary`. Ticker and exchange are never truncated.
- Currency only when it differs from USD (relevant only with D1).
- No price, change, market cap or sector (D4).
- Avatar: existing `StockTickerAvatar` (ticker initials). No logo fetch, because search has no logo URL (D5).

**Best match:** shown only if the top result is tier 1 or tier 2 **and** no other result has the same tier **and** no other result has the same
normalised name.
- `GOOG` → no badge (GOOGL shares "Alphabet").
- `AAPL` and `apple` → badge.
- `micro` → none.

It's computed in a core display object from `SearchRanking`, so there's no API change. It uses the existing `StockStatusBadge` (positive
kind), within `StockBadgeLayout` rules.

**Sections:**
- "Best match" (one row, when eligible), then "Other results" (as in the reference). Without a badge there is one "Results" header.
- Section headers are `StockSectionHeader`. There's no card around the list; this is the borderless page surface.

**Large text (Android ≥ 1.75×, iOS AX3+):**
- the avatar goes above the text;
- the name wraps;
- ticker and exchange stack on separate lines (as in the reference AX5 panel);
- the chevron stays at the trailing edge, centred;
- the badge sits on its own line under the name (compact, then wrapping, as in 5D.5.4B).

## 9. States and navigation (5D.5.5C)
| State | Behaviour |
|---|---|
| Initial | Recent searches (D6) if approved; otherwise the field plus a one-line hint "Search by company name or ticker". No trending list (no data source) |
| Typing | Keep the 300 ms debounce, stale-request cancellation (`collectLatest` / task cancel) and the query check before applying results. Minimum length stays 1 (single-letter tickers: F, T, X). Typing suggestions from the reference are **not** proposed (no suggestion source; D6) |
| Loading | Progress inside the field (Android) / small progress row (iOS); previous results stay dimmed instead of a blank flash |
| Results | Ranked server list; Best-match section when eligible; stable identity = `symbol` |
| No results | Icon, "No companies found", "Check the ticker or company name and try again.", "Try a different search" (clears and focuses the field) |
| Error | "Something went wrong — We couldn't search for companies right now. Check your connection and try again." + Try again (adds Retry on Android). Kept separate from no results |
| Guest | No sign-in needed (current behaviour kept) |
| MOCK | The "Sample data · mock backend" banner stays; no live wording |

**Navigation:** every `onOpenStock` outcome in §3 is unchanged. Comparison moves onto the shared row and ranking but keeps its dialog, its
8-result cap and "already added".

## 10. Reusable component opportunities
- **New:** `StockSearchResultRow` (Compose) / `SearchResultRow` (SwiftUI).
- **Core:** `SearchDisplay` (titles, copy, Best-match rule, row text).
- **Existing:** `StockSearchField`, `StockTickerAvatar`, `StockStatusBadge`, `StockSectionHeader`, `StockEmptyState`/`StockErrorState` (Compose),
  `ContentUnavailableView`/`StockSectionMessage` (iOS).
- **Comparison dialog:** reuse the row and copy.
- **Not changed:** the legacy inline detail pane (removing it is a separate clean-up decision, D7).

## 11. Performance and API implications
- **Unchanged:**
  - two FMP calls per uncached normalised query;
  - 6 h server cache;
  - `MARKET_DATA` admission;
  - 300 ms debounce (search) / 350 ms (Comparison);
  - cancellation on both platforms.
- **Ranking:** server CPU only; no new endpoint, no extra provider calls.
- **Optional (D8):** send an explicit `limit` to FMP only after verifying its default with an approved REAL check.
- **Logos:** none fetched (no URL in search). Offline: the error state with Try again; no offline cache of results (recent searches only, if D6).
- **Response time:** MOCK search answered in 2–6 ms locally (first call 144 ms). REAL wasn't measured.

## 12. Accessibility requirements
- Each row is one element: "Apple Inc., AAPL, NASDAQ" (+ ", best match"), button role or trait, hint "Opens company details" (or the outcome's
  verb). Ticker letters may be spelled out.
- The avatar and chevron are hidden from screen readers.
- Section headers are headings. The field has a label and its Clear button is labelled.
- Results count is announced once per completed search (live region / `AccessibilityNotification`). Errors are announced once.
- 48 dp / 44 pt targets; the keyboard doesn't cover the error or no-results action (`KeyboardResizesContent` on Android).
- Large text per §8; no truncation of ticker or exchange; no horizontal overflow.
- Screen-reader QA is manual (owner), as in 5D.5.4D.

## 13. Risks and trade-offs
- **Ranking scope:** only what the provider returned can be ranked. A company absent from FMP's first page can't be promoted (D8).
- **Exchange tie-break (D2):** fewer OTC micro-caps up top, at the cost of a rule the owner must accept.
- **Type labels:** omitting them keeps data honest; names carry "ETF/Fund/REIT" text but not consistently.
- **CAD scope (D1):** adding TSX doubles the result sets beginners see and needs copy and currency display; keeping USD-only needs copy fixes
  (iOS "USD or CAD", the chip, Comparison's "RY.TO" hint).
- **Server ordering change:** affects every client and the cache (old entries expire within 6 h; no migration).

## 14. Files likely to change
**5D.5.5B:**
- `core/src/commonMain/kotlin/org/example/stocksteps/search/SearchRanking.kt` (new)
- `core/src/commonMain/kotlin/org/example/stocksteps/search/SearchDisplay.kt` (new)
- `core/src/commonTest/kotlin/org/example/stocksteps/search/SearchRankingTest.kt` (new)
- `core/src/commonTest/kotlin/org/example/stocksteps/search/SearchDisplayTest.kt` (new)
- `server/src/main/kotlin/org/example/stocksteps/service/StockService.kt` (use `SearchRanking`; scope per D1)
- `server/src/test/kotlin/org/example/stocksteps/StockSearchTest.kt`
- `app/shared/src/commonMain/kotlin/org/example/stocksteps/designsystem/components/StockSearchResultRow.kt` (new)
- `app/shared/src/commonMain/kotlin/org/example/stocksteps/presentation/stocksearch/StockSearchPane.kt`
- `app/iosApp/iosApp/DesignSystem/SearchResultRow.swift` (new)
- `app/iosApp/iosApp/StockSearchScreen.swift`

**5D.5.5C:**
- `presentation/stocksearch/StockSearchScene.kt`, `StockSearchViewModel.kt`, `StockSearchState.kt` (Retry, states, recent searches if D6)
- `presentation/screener/ComparisonScreen.kt` (`AddCompanyDialog`)
- iOS `StockSearchScene.swift`, `StockSearchViewModel.swift`, `ScreenerScenes.swift` (add-company sheet), `PortfolioScene.swift` (search sheet
  title only)
- Recent searches (D6): a small local store per platform (DataStore / `UserDefaults`, symbol + name + exchange only, max 10, Clear all)

**Docs:** `docs/COMPANY_SEARCH_REFINEMENT_PHASE5D_5_5B.md`, `_5C.md`, `_5D.md`; review packages under `docs/reviews/phase5d_5_5*/`.

## 15. Automated and device QA matrix
**Automated:**
- `:core:jvmTest`, `:core:iosSimulatorArm64Test` (ranking, display);
- `:server:test` (search route order);
- `:app:shared:testAndroidHostTest`, `:app:shared:iosSimulatorArm64Test`;
- `assembleDebug`, `xcodebuild` (MOCK).

**Device:**
- Android `emulator-5554` 360 dp at 1.0×, 1.3× and 2.0×, light and dark.
- iPhone 16 at Default, AX XL and AX5, light and dark.
- **States:** initial, typing with the keyboard visible, loading, results with and without Best match, no results, error (server stopped),
  guest (iOS; Android blocked by the signed-in emulator).
- **Queries:** the §5 set.
- **Entry points:** Home, Markets, Watchlist, Learn, Portfolio add, Practice, Comparison. Each outcome is checked.
- **Screen readers:** owner, manual.

### Screenshot index (this phase)
All at 1.0× / Default, light, MOCK, "before" (current UI), in `docs/reviews/phase5d_5_5a/captures/` (git-ignored):

| File | Device | Screen | QA |
|---|---|---|---|
| `android-1.0x-light-mock-before-search-empty.jpg` | emulator-5554 | Search, empty | Audit |
| `android-1.0x-light-mock-before-search-apple.jpg` | emulator-5554 | Results "apple" | Audit (fund 2nd, OTC before NYSE) |
| `android-1.0x-light-mock-before-search-none.jpg` | emulator-5554 | No results | Audit |
| `ios-default-light-mock-before-search-empty.jpg` | iPhone 16 | Search, empty ("USD or CAD") | Audit |
| `ios-default-light-mock-before-search-apple.jpg` | iPhone 16 | Results "apple" | Audit |
| `owner-reference-5d55a.webp` | — | Owner reference | Target |

Earlier large-text captures of the iOS search sheet aren't kept. Large-text "before" is NOT captured; it will be recorded at the start of
5D.5.5B.

## 16. Open owner decisions (recommended default first)
- **D1 Market scope:**
  - **Recommended: keep USD/US-only for now** and fix the contradicting copy (iOS "USD or CAD", the "USD · CAD" chip, Comparison's "RY.TO"
    hint).
  - Alternative: add TSX/CAD listings (Practice already uses CAD), with currency shown in rows.
- **D2 Exchange tie-break:** **recommended: yes.** Major exchanges before OTC within the same match tier (data-based; not popularity).
- **D3 Instrument type labels:** **recommended: none** (the provider gives no type; names are shown verbatim). Alternative: a "Fund/ETF" label
  from name keywords (heuristic, could mislabel).
- **D4 Sector/industry in rows:** **recommended: none** (would need a profile call per result). The reference's sector line is dropped.
- **D5 Logos:** **recommended: ticker-initials avatar** (`StockTickerAvatar`); no logo fetching. **Superseded 2026-10-15:** the owner approved real logos via the server-built `fmpLogoUrl(symbol)` with an initials fallback (5D.5.5C report §9).
- **D6 Recent searches:** **recommended: yes, local-only** (device storage, last 10 selected companies, Clear all; nothing sent to the server;
  works in guest mode). Query suggestions while typing: **no** (no data source).
- **D7 Legacy inline detail pane:** **recommended: leave as-is in 5D.5.5;** schedule removal separately.
- **D8 Provider page size:** **recommended: no change** until an approved REAL check confirms FMP's default `limit` for both endpoints.

## 17. Recommended implementation sequence
1. **5D.5.5B:**
   - core `SearchRanking` + tests;
   - server applies it (and D1/D2);
   - core `SearchDisplay` (Best match, copy) + tests;
   - result row components on both platforms;
   - the main search screen uses them.
   - Verify with MOCK queries §5 and both builds.
2. **5D.5.5C:**
   - header and states (initial/recent, loading, no results, error + Retry);
   - Comparison dialog on the shared row;
   - copy fixes per D1;
   - keyboard handling.
3. **5D.5.5D:**
   - the device matrix §15, entry-point outcomes, large text, dark;
   - review package (one Android and one iOS contact sheet);
   - owner screen-reader pass.

Out of scope without a new decision: sector discovery, trending companies, typing suggestions, logo service, new endpoints.

## Verification status of this audit
- **Inspected (code):** all files in §2–§4.
- **Tested:** MOCK `/api/v1/stocks/search` with the §5 queries; Android and iOS search screens at 1.0× / Default light (captures above).
- **Not run:** REAL provider queries (needs approval and a key); large-text and dark captures of the current search; screen readers.
- **Blocked:** none.
- **No code changed.** Disk space was critically low during the audit (≈ 160 MB–1 GB free); only this session's scratchpad files were removed.

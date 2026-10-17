# ChatGPT review package — UI/UX Refinement Phase 8B (Discover, Search, Company Details)

## 1. Phase and Git baseline

- **Phase:** 8B of the Phase 8 brief. 8C has not started.
- **Base:** `852afb9`, "Refine Home and Markets for UI/UX Refinement Phase 8A …" (committed and pushed). The tree was clean at the start.
- **Under review:** "Refine Discover for UI/UX Refinement Phase 8B with a scope line, one-tap sector filters, Clear all, the sort in words, row sectors, the design-system search field and an About these results fold, fix the legacy Unavailable% movers line, and verify Company Search and Company Details on Android and iOS" (committed and pushed).

## 2. Changes implemented (Android + iOS)

**Discover:**
- A scope line, e.g. "160 companies · <the server's universe description>".
- One-tap sector pills. Each tap applies one sector on top of the applied query and sends one search.
- "Clear all" beside the filter count.
- The sort label in words: "Sort: Market cap (high to low)".
- A sector caption on every row.
- The design-system search field. On iOS the label is no longer repeated as the placeholder.
- An "About these results" disclosure. Server warnings stay visible.

**Legacy Discovery movers:** "Change unavailable · Price unavailable" or "+1.23% · $12.34". They used to show "Unavailable%" and raw
doubles.

**Company Search and Company Details:** audited and verified, no change (`DISCOVER_SEARCH_REVIEW.md`, `COMPANY_DETAILS_REVIEW.md`).

## 3. Affected files

**core:**
- `screener/ScreenerDisplay.kt`: scope, sector options and selection, Clear all, sort in words.
- `screener/ScreenerPresentation.kt`: `ScreenerRowView.sector`, `selectSector`.
- `ScreenerDisplayTest`.

**shared:**
- `presentation/screener/ScreenerScreen.kt`, `ScreenerScene.kt`.
- `presentation/discovery/MarketMoverDisplay.kt` (new), `MarketMoversSection.kt`.
- `MarketMoverDisplayTest` (new).

**iOS:** `DiscoverStocksViews.swift`, `MarketMoversView.swift`.

**server:** none.

**docs:**
- `docs/UI_UX_REFINEMENT_PHASE8.md` (8B section) and this folder.
- `docs/design/ui_ux_phase8b/`.
- `docs/PROJECT_HANDOFF.md`, `docs/project-status.md`.

## 4. Architectural decisions

- All wording and selection rules live in the core `ScreenerDisplay`, which both platforms call.
- Sector taps go through one presenter method that reuses the sheet's own `ChoiceFilter`. It starts from the applied query, so unapplied
  sheet edits are never sent by accident.
- The legacy formatter reuses the `HomePresentation` formatters.
- Existing components are reused: `StockPillSelector`, `StockSearchField`, `StockDisclosure`. No component defaults changed.

## 5. Tests and builds

See `CHATGPT_TEST_RESULTS.md`: all five suites and both builds pass. The 8 skipped server tests are Firestore-emulator tests; they are
reported as skipped, not passed.

## 6. Android verification

emulator-5554, 412 dp, local MOCK `:8087`, same script for every pass.

| Check | Result |
|---|---|
| Light, dark and 2.0× BEFORE/AFTER | PASS |
| Sector tap and Clear all | PASS: one request each |
| Request totals for the scripted flow | 25 = 25 |

## 7. iOS verification

iPhone 16, guest, local MOCK `:8087`.

| Check | Result |
|---|---|
| Light BEFORE/AFTER | PASS |
| Dark and AX5 AFTER | PASS |
| Sector tap | PASS |
| Dark and AX5 BEFORE (Discover) | NOT RUN |

## 8. Known issues

See `KNOWN_LIMITATIONS.md`:
- the Phase 7E enrichment quota is still open (not changed);
- Discover rows have no price date;
- the MOCK scope text is long;
- the search placeholder ends in "…" at 2.0×;
- the chart axis has no currency symbol (P2).

## 9. Unverified scenarios

All NOT RUN:
- TalkBack / VoiceOver and focus order;
- signed-in iOS and Android guest;
- REAL (FMP 429);
- small phones and tablets;
- the legacy movers screens on a device (they can't be reached);
- the iOS runtime request count.

## 10. Deviations from the brief

- **Company Details hierarchy:** left in the owner-approved 5D.5.4 order. The audit found nothing above P2, and reordering would need an
  owner decision.
- **Discover rows:** no "EOD price · date" line, because the server sends no per-row date.
- **Search:** no change was needed.

## 11. Decisions requiring owner approval

- **D8B-1:** sector pills on Discover.
- **D8B-2:** the sort label in words.
- **Open:** the Phase 7E enrichment budget for the LAN key.

## 12. Links

- `UI_AUDIT.md`
- `DISCOVER_SEARCH_REVIEW.md`
- `COMPANY_DETAILS_REVIEW.md`
- `SCREEN_BEFORE_AFTER.md`
- `ANDROID_IOS_PARITY.md`
- `ACCESSIBILITY_CHECKLIST.md`
- `API_REQUEST_REGRESSION.md`
- `KNOWN_LIMITATIONS.md`
- `CHATGPT_FOCUSED_DIFF.md`
- `CHATGPT_TEST_RESULTS.md`
- `CHATGPT_UI_REVIEW_ANDROID.png`, `CHATGPT_UI_REVIEW_IOS.png`
- `docs/design/ui_ux_phase8b/`

**Verdict: READY FOR REVIEW.**

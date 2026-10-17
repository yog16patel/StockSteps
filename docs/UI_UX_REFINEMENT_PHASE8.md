# UI/UX Refinement Phase 8 — Home and Markets (batch 8A)

Status: **8A committed and pushed as "Refine Home and Markets for UI/UX Refinement Phase 8A with open borderless Markets sections, a one-line market status, the Daily Brief entry in the indices group, diverging sector bars, readable Top Movers at large iOS text and the Portfolio overview wording on Home on Android and iOS"; READY FOR REVIEW.** 8B and later batches are not started. Base `ddb6dfb` (Phase 7E).
Review package: `docs/reviews/ui_ux_phase8a/`.

## 1. Scope (8A)

Home and Markets only, Android (Compose) and iOS (SwiftUI). Out of scope and untouched: Discover, search ranking, Company Details,
Portfolio accounting, Practice execution / idempotency, Watchlist logic, Learn content, server and provider code, the Phase 7E
enrichment budget.

## 2. Audit

Full inventory and plan: `docs/reviews/ui_ux_phase8a/UI_AUDIT.md`. Summary:

- **P0:** none found on Home or Markets.
- **P1:**
  - H1: the Home portfolio summary wording differed from Portfolio (raw notice codes, metrics without currency);
  - M1: seven bordered cards on Markets;
  - M2: a card and an extra heading for the market status;
  - M3: one-sided sector bars;
  - M4: iOS Top Movers names squeezed to one letter per line at AX sizes.
- **P2:**
  - H2: a redundant Home subtitle;
  - H3/M6: no shared section-gap role;
  - M5: a separate brief card;
  - M7: the news inset;
  - L1: legacy iOS Discover formatting (out of scope, follow-up).

## 3. Changes

| # | Change | Platforms |
|---|---|---|
| 1 | New token `ThemeSpacing.pageSectionGap` = 20 (open landing sections); Home uses it (same value), Markets moves from 12 | both |
| 2 | Markets sections use the KAN-14 open-section pattern (`HomeSection` + borderless `HomeRowGroup` / `.homeRowGroup()`); 7 borders → 0 | both |
| 3 | Market status as an open block: "US Market · After-hours" + Sample badge, next open, "Sample quotes · Not live" | both |
| 4 | Daily Market Brief entry + "Previous briefs" close the Major indices group (own group when the overview failed) | both |
| 5 | Diverging sector bars around a centre line (`MarketsScreenPresentation.sectorBar`) | both |
| 6 | Top Movers at accessibility sizes: values under the name (iOS `StockRow(stackTrailing:)`, default off) | iOS |
| 7 | News group inset 16 like the other groups | both |
| 8 | Home: greeting without "What matters to you today" | both |
| 9 | Home portfolio summary from `PortfolioOverviewDisplay` (currency on every metric, notice in words, one spoken summary) | both |

## 4. Data truth (unchanged)

- EOD labels ("Last close · Oct 16") and "Change from the previous close" still come from core `MarketsPresenter` (Phase 7B); the sector
  caption text is unchanged.
- ETF-proxy labels and fund currency (CAD 310.94, "Proxy: … fund price") are unchanged and never shown as an index level.
- Dated values, unavailable states ("Not available right now", "—") and typed provider errors (the overview's section errors) are
  unchanged.
- The Daily Brief takeaway (D-B1) isn't touched; Home's brief rows are unchanged.
- Guest mode works (iOS frames are guest). MOCK keeps its Sample badge, "Sample quotes · Not live" and the app's Sample strip.
- No new requests: 12 → 12 per Home → Markets → Home → Markets run (`API_REQUEST_REGRESSION.md`).

## 5. Verification

- **Tests and builds:** see `docs/reviews/ui_ux_phase8a/CHATGPT_TEST_RESULTS.md`.
- **Devices:** BEFORE/AFTER on local MOCK `:8087`:
  - Android emulator-5554: light, dark and 2.0×;
  - iPhone 16 guest: light, dark and AX5.
  - Details: `SCREEN_BEFORE_AFTER.md`.
- **NOT RUN:**
  - TalkBack / VoiceOver;
  - the Home portfolio summary with an owned account on a device;
  - loading and error states on device;
  - small phones and tablets;
  - REAL (FMP 429).

## 6. Owner decisions

- **D8A-1:** drop "What matters to you today" under the Home greeting (and "US Market" stops being a separate heading on Markets).
- **D8A-2:** diverging sector bars (shorter bars; direction by side).
- **D8A-3:** the Daily Brief entry inside the Major indices group.

## 7. Next

Owner and ChatGPT review of `docs/reviews/ui_ux_phase8a/`. 8B only after approval.

---

# Batch 8B — Discover, Company Search and Company Details

Status: **8B committed and pushed as "Refine Discover for UI/UX Refinement Phase 8B with a scope line, one-tap sector filters, Clear all, the sort in words, row sectors, the design-system search field and an About these results fold, fix the legacy Unavailable% movers line, and verify Company Search and Company Details on Android and iOS"; READY FOR REVIEW.** Base `852afb9` (8A). Package: `docs/reviews/ui_ux_phase8b/`. 8C not started.

## 8B.1 Audit

Full inventory: `docs/reviews/ui_ux_phase8b/UI_AUDIT.md`.

- No P0.
- **P1 (all Discover):**
  - D1: no scope on the landing page;
  - D2: sector filters three levels deep;
  - D3: "Clear all" offered only when nothing matched;
  - D4: an arrow in the sort label;
  - D5: no sector on rows.
- **Legacy movers:** "Unavailable%" (L1, on screens that aren't reachable).
- **Search and Company Details:** already refined (5D.5.5, 5D.5.4, Phases 3–5); verified with no change. The chart axis currency is
  left as a P2 follow-up.
- **Phase 7E quota (≈ 310 requests a day vs a 250-a-day key):** recorded as outstanding; no backend work.

## 8B.2 Changes (Android and iOS, display plus one presenter shortcut)

| # | Change |
|---|---|
| 1 | Scope line on Discover, e.g. "160 companies · <server's universe description>" (`ScreenerDisplay.scopeLine`) |
| 2 | Sector pills: "All sectors" plus the catalogue's sectors. A tap applies the sheet's own sector filter on top of the applied query (`ScreenerPresenter.selectSector`), one request |
| 3 | "Clear all" beside the filter count (existing `resetAll`) |
| 4 | Sort label in words: "Sort: Market cap (high to low)" |
| 5 | Sector caption on result rows (`ScreenerRowView.sector`, the provider's value; also spoken) |
| 6 | Design-system search field on Discover (Android `StockSearchField`; iOS no longer repeats the label as the placeholder) |
| 7 | Results explanation, preset notes and "What these numbers mean" in one "About these results" disclosure; server warnings stay visible |
| 8 | Legacy movers line via the shared `MarketMoverDisplay`: missing values in words, a real zero as 0.00 %, Home formatters |

## 8B.3 Data truth

- No change to the curated universe, ranking, filters, pagination, cursor, server or 7E budget.
- Missing values stay "No data available", "Change unavailable" or "Price unavailable", never 0.
- No price date is invented: the server has no per-row date.
- Company Details still says "Closed · Updated … · Prices may be delayed", never "live".
- Requests are unchanged: 25 → 25 over the scripted Discover → Search → Company Details flow.

## 8B.4 Owner decisions
- **D8B-1:** sector pills on Discover.
- **D8B-2:** the sort label in words.

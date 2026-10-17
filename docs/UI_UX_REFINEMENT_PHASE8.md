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

---

# Batch 8C — My Portfolio, Practice Portfolio and Watchlist

**Status:** 8C is committed and pushed as "Refine My Portfolio, Practice and Watchlist for UI/UX Refinement Phase 8C with the summary figures before the account-value history, borderless Practice sections without the repeated cash amount, house iOS buttons and a dated Watchlist header on Android and iOS"; READY FOR REVIEW. 8D has not started.

**Base:** `67d40d7` (8B, committed and pushed). **Package:** `docs/reviews/ui_ux_phase8c/`.

## 8C.1 Audit

The full inventory is in `docs/reviews/ui_ux_phase8c/UI_AUDIT.md`. No P0 issue and no calculation defect was found.

| ID | Priority | Finding |
|---|---|---|
| B1 | P1 | The My Portfolio account-value history split the total from Invested cost / Unrealized / Realized P/L |
| H1 | P1 | Practice was a column of bordered cards, with a nested bordered lock row |
| H2 | P1 | The empty Practice portfolio said "$10,000.00 virtual cash" three times |
| H3 | P2 | iOS used `.bordered` buttons |
| M1 | P1 | The Watchlist header read "Updated 4:48 AM ET" with no date, over Oct 7 closes |
| G1 | P2 | A single holding at 100 % draws a full ring — not changed (see D8C-3) |

**Not changed:**
- **G1:** the single-holding donut. Phase C's tests encode it, so it is owner decision D8C-3.
- **Forms:** transaction forms.
- **Practice order flow:** the trade preview, review and result.
- **Watchlist add flow:** still three steps.

## 8C.2 Changes (display only, Android and iOS)

| # | Change |
|---|---|
| 1 | My Portfolio overview order: total → today → Invested / Unrealized / Realized P/L → notice and valuation date → account-value history |
| 2 | Practice sections and rows borderless per call site (summary, metrics, insights, holdings, allocation, activity, challenges); order flow unchanged |
| 3 | Practice empty-state text without the repeated cash amount |
| 4 | iOS Practice buttons use `.stockSecondary` |
| 5 | Watchlist header dated: "Updated Oct 7, 5:15 PM ET" (core `WatchlistPresentation`) |

## 8C.3 Integrity

No accounting, valuation, execution, idempotency, persistence, auth or Firestore change. The financial-integrity gate passes
(`FINANCIAL_INTEGRITY_CHECKLIST.md`).

## 8C.4 Owner decisions

- **D8C-1:** overview order.
- **D8C-2:** borderless Practice.
- **D8C-3:** a bar instead of a full ring for a single holding at 100 %.

---

# Batch 8D — Learn, accessibility and final cross-platform polish

**Status:** committed and pushed as "Finish UI/UX Refinement Phase 8D with a short Discover scope label and About this list, a compact Practice heading and sign-in at large text, Learn starting with Start here at very large text, wrapping guided-research steps and the consolidated launch-readiness backlog on Android and iOS"; READY FOR REVIEW for the UI/UX scope. Public launch is still BLOCKED by non-UI items.

- **Base:** `823d247` (8C).
- **Package:** `docs/reviews/ui_ux_phase8d/`, including `LAUNCH_READINESS_BACKLOG.md`.

## 8D.1 Audit

Consolidated across all screens (`CONSOLIDATED_UI_AUDIT.md`). No P0 issue found.

**P1, all fixed:**

| ID | Issue |
|---|---|
| X1 | iOS Discover AX5: search was below the fold |
| X2 | iOS Practice AX5 (guest): Sign in was below the fold |
| X3 | Learn AX5: the intro sentence filled the first screen |

**P2, fixed:**

| ID | Issue |
|---|---|
| X4 | research step descriptions were cut off |

**Carried to the backlog:**

| ID | Issue |
|---|---|
| X5 | REAL search error copy |
| X6 | REAL Company Details empty header |

**Android sweep at 1.5×:** no issues.

## 8D.2 Changes

| # | Change |
|---|---|
| 1 | Discover: short scope label "N companies · not the whole market" (core `ScreenerDisplay.scopeLabel`); the server description is under "About this list" |
| 2 | Practice heading inside the Portfolio landing uses the section-heading role at large text; iOS guest sign-in order and the AX label "Sign in to use Practice" |
| 3 | Learn intro left out at very large text |
| 4 | Guided-research step descriptions wrap in full |

## 8D.3 Learn

Learn already answers what, where to start, what's completed and what's next (`LEARN_REVIEW.md`).

- The content is unchanged; no inaccuracies were found.
- There is no reading time or difficulty data, so none is shown.

## 8D.4 Accessibility

TalkBack and VoiceOver were NOT RUN. Accessibility is assessed by code inspection, screenshots and unit tests.

## 8D.5 Owner decisions

- **D8D-1:** the scope label wording.
- **D8D-2:** the Practice heading and sign-in order at large text.

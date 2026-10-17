# Phase 8C — My Portfolio, Practice and Watchlist UI audit (before changes)

**Baseline:**
- Base `67d40d7`, Phase 8B, committed and pushed. The coordinating session's note called 8B uncommitted; it isn't.
- The tree was clean.

**Evidence:** BEFORE frames on local MOCK `:8087`.
- Android: emulator-5554 (Pixel 9 Pro, 412 dp). The stored session is signed in on the MOCK user-data store, not production, and has no
  accounts of its own. It uses the read-only Portfolio sample scenarios (One CAD, Missing FX, Multiple accounts) and the Practice sample
  scenario `one-holding`.
- iOS: iPhone 16, guest only. Signed-in iOS is **BLOCKED**: no test credentials may be entered on devices.
- Originals: `captures/before/`.

## Screens audited (A–O)

| Screen | Goal / main action | Findings |
|---|---|---|
| A Portfolio landing | choose My Portfolio or Practice | Clear: pinned nested tabs, SIMULATED badge on Practice. Guest sees "Sign in to save your portfolio securely" and an empty state. No change |
| B My Portfolio overview | total, gain/loss, cost | **P1 (B1):** the "Account value" history (period pills plus "Unchanged at CAD 1,000.00 · Sep 1 – Oct 8") sits between Today's change and Invested cost / Unrealized / Realized P/L. The core figures are split and pushed below the fold (`one-cad-1`, `missing-fx-1`) |
| C Accounts | switch or add | Selector plus "+ Add" in the card; also "+ Add transaction" below the card. Two add entries (P2, owner-approved D-series layout), not changed |
| D Holdings list | scan holdings | Borderless rows with name, ticker · exchange · shares, value, gain %. Missing price → "USD —" / unavailable. No change |
| E Holding details | — | Sheet with metric grid (Phase C). No change |
| F Transactions | add/edit/delete | `PortfolioFormRules` forms. Delete only on own data, never samples (D2). No change: forms are financial-flow code and the audit found no P0/P1 |
| G Allocation | how money is spread | **P2 (G1):** a single holding at 100 % draws a full ring, which carries no information (`one-cad-2`) |
| H Practice landing | value, cash, trade | **P1 (H1):** six bordered cards in a column (summary, Available cash, Open holdings, insights with a nested bordered lock row, holding cards). Inconsistent with borderless My Portfolio |
| | | **P1 (H2):** "$10,000.00 virtual cash" is said three times on an empty portfolio: Available cash, the empty-state card, and the insights (engine text) |
| | | **P2 (H3):** iOS uses `.bordered` buttons (tinted) instead of the house `.stockSecondary` |
| I Practice buy/sell | trade | `PracticeOrderScreen` (Phase 7C): EOD basis with date, preview, review, result. Execution semantics, idempotency and freshness can't change in 8C. Not changed |
| J Trade preview / K result | review, confirm | Confirmed-result-only success, "Simulated with virtual money. No real order was placed." Not changed except the iOS result button style (H3) |
| L Practice history | past trades | "Practice buy · AAPL, 5 shares at US$303.42, Aug 3, 2026 · Simulated". Each row is a bordered card (H1). Server records only |
| M Watchlist landing | scan saved companies | Phase 5A rows (logo, ticker, name, price, change), "Change since the previous close. Prices may be delayed." |
| | | **P1 (M1):** the header says "Updated 4:48 AM ET" with no date, while the prices are the Oct 7 close. A time alone reads as today's |
| N Watchlist add/remove | add company | Add a company → search → Company Details → star: three steps (P2, existing navigation; noted, not changed) |
| O States | empty, guest, errors | Empty watchlist, guest Practice ("Sign in to use your Practice Portfolio…"), guest Portfolio and the missing-FX unavailable state are all plain English. No raw codes |

**No P0.** No calculation defect was found: totals, P/L, allocation % and Practice values come from the engine and presenters unchanged
(`FINANCIAL_INTEGRITY_CHECKLIST.md`).

## Plan (8C, display only)

1. **B1:** the My Portfolio overview reads total → today's change → Invested / Unrealized / Realized P/L → notice and valuation date →
   account-value history (both platforms).
2. **G1:** not changed. An implementation was reverted because Phase C's `PortfolioPhaseCTest.donutOnlyForValidAllocations` deliberately
   allows the donut for one holding at 100 %, so it's raised as owner decision **D8C-3** instead.
3. **H1:** Practice sections and rows are borderless per call site (summary, metrics, insights, holdings, allocation, activity,
   challenges). The trade preview, review and result cards are unchanged.
4. **H2:** the Practice empty-state text no longer repeats the cash amount.
5. **H3:** iOS Practice buttons use `.stockSecondary`.
6. **M1:** the Watchlist header shows the date, "Updated Oct 7, 5:15 PM ET" (core `WatchlistPresentation`, tested).

**Owner decisions:**
- D8C-1: the overview order;
- D8C-2: borderless Practice;
- D8C-3: whether a lone 100 % holding should show a bar instead of a full ring (Phase C currently says ring).

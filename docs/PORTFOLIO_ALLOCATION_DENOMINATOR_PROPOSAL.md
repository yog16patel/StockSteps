# Portfolio allocation denominator — investigation and proposed financial contract

Status: **proposal for owner review (2026-10-13). Nothing implemented.** Committed with Phase E.1 ("Fix Portfolio Phase E.1 session-refresh messaging so an ended session says to sign in again instead of blaming the password, and record the allocation-denominator proposal and blocked screen-reader checks"). Prepared after Phase E.1 at the owner's request (E.1 condition 4). It is a
separate phase: it changes engine output.

## 1. Current behaviour

| Screen | Denominator | Code | Cash below zero | Cash above zero |
|---|---|---|---|---|
| Dashboard "By holding" (server report → `PortfolioPresenter.allocations` → `PortfolioAllocationDisplay`) | **Net account value**: holdings + **signed** cash in reporting currency | `PortfolioEngine.allocations` (`core/.../PortfolioEngine.kt`) | Holdings add up to **> 100%** | Holdings add up to **< 100%** (cash isn't listed) |
| Insights allocation (`/portfolio/accounts/{id}/analytics`) | **Gross long value**: holdings + **positive** cash only | `PortfolioAnalyticsEngine.allocation` (`core/.../analytics/PortfolioAnalyticsEngine.kt`) | Borrowed cash excluded and stated in a note; slices add up to 100% | Cash is its own slice; slices add up to 100% |

Both screens:
- use exact `Decimal`, current prices and FX;
- hide all percentages if any price or FX value is missing (never renormalized);
- disclose their rule on screen.

The dashboard says (D3.1 note) "Percentages are of account value, which includes cash." Insights says "Percentages are shares of holdings plus
cash" and "Borrowed or unfunded cash … is shown separately and not included in percentages."

**Both are internally correct.** They answer different questions:
- dashboard: *what share of my net account value is this holding?*
- Insights: *what share of what I own is this holding?*

## 2. Worked example (D3.1 ledger, USD→CAD 1.35)

| Item | CAD |
|---|---|
| TD.TO | 2,166.48 |
| AAPL | 4,545.05 |
| Cash | −69.53 |
| Net account value | 6,642.00 |
| Gross long value | 6,711.53 |

| Holding | Dashboard (÷ 6,642.00) | Insights (÷ 6,711.53) |
|---|---|---|
| TD.TO | 32.62% | 32.28% |
| AAPL | 68.43% | 67.72% |
| Sum | 101.05% | 100.00% |

## 3. Proposed contract (recommendation: one rule app-wide = **gross long value**)

**Why this rule.** For a beginner, "share of what you own" is the intuitive reading:
- it always adds up to 100%;
- cash appears as a slice;
- negative cash is a separate, labelled fact rather than a distortion of every percentage.

It's also the rule the more detailed screen (Insights) already uses.

Contract for `PortfolioEngine.allocations` (and the server report):
1. Denominator = Σ holdings (reporting currency) + Σ **positive** cash balances (reporting currency).
2. Holding slice % = value / denominator; a **cash slice** per currency with positive cash.
3. Negative cash: no slice. A separate `unfundedCash` amount (reporting currency) is returned. The UI shows "Unfunded cash −CAD 69.53" and a note,
   replacing the D3.1 ">100%" note.
4. Any missing price or FX → every percentage null (unchanged).
5. Denominator ≤ 0 → percentages null, with the note "Nothing is invested or held in cash yet" (as Insights).
6. Exact `Decimal`; display rounding half-up to 2 places (unchanged).

**Unchanged:** total value, cash lines, overview metrics, Insights, ledger and validation.

**API change (additive):**
- `PortfolioAllocations` gains `cash: List<PortfolioCurrencyAllocation>` and `unfundedCash: String?`;
- existing fields keep their meaning except the percent denominator;
- old clients still render (their percentages simply match Insights).

## 4. Tests (to add with the change)

- **Core `PortfolioEngineTest`:**
  - D3.1 ledger → TD.TO 32.28, AAPL 67.72, no cash slice, `unfundedCash` = −69.525 CAD;
  - positive cash → a cash slice, sum = 100.00;
  - zero cash → no cash slice;
  - mixed currencies with one negative and one positive balance;
  - missing price → all null;
  - denominator ≤ 0 → null.
- **Cross-check:** for the same inputs, dashboard percentages equal Insights percentages (one shared test over both engines).
- **Server report test:** the new fields serialize; MOCK fixtures regenerate.
- **Shared display (`PortfolioPhaseD31Test` successor):** the unfunded line and note replace `cashNote`'s ">100%" text.
- **Device:** D3.1 scenario on Android and iOS; light/dark; 1×/2× / AX5.

## 5. Risks

- Users who saw ">100%" will see different percentages. The release note should say percentages are now "share of what you own".
- Server and client must ship together for the cash slice; percentages alone are safe with old clients.
- MOCK restart needed; no paid provider calls.

## 6. Decision needed

| ID | Question | Recommendation |
|---|---|---|
| A1 | Adopt gross long value app-wide (§3)? | Yes |
| A2 | Alternatively keep both rules and only relabel (no engine change)? | Fallback if A1 is declined |

## 7. Clarifications added in Phase E.2 (documentation only; still not implemented)

Added at the owner's request (E.2 decision D-6) so the A1 phase starts from a complete contract. Nothing here changes code or numbers today.

1. **Unfunded cash with several negative balances.** `unfundedCash` is **one amount in the account's reporting currency**: the sum of every
   negative cash balance, each converted at the same current FX as the holdings. If any negative balance can't be converted (FX missing),
   `unfundedCash` is null and every percentage is null (rule 4). The UI line names the reporting currency only ("Unfunded cash −CAD 69.53").
   The per-currency balances stay in the existing cash lines.
2. **Stale prices.** A quote that is present but not from the current session still counts (as today, on both screens). Only a **missing**
   price or FX value nulls the percentages. The existing freshness line ("Prices as of …") qualifies the percentages; A1 adds no second staleness rule.
3. **One account, one reporting currency.** Allocation is calculated per account, in that account's reporting currency. There's no cross-account
   or cross-currency aggregate allocation (the "All accounts" view was excluded by the owner), so two accounts with different reporting
   currencies never share a denominator.
4. **Rounding.** Each slice is the exact `Decimal` ratio, rounded half-up to 2 places **for display only**. Displayed slices may add up to
   99.99% or 100.01%. There's no largest-remainder adjustment (Insights doesn't adjust either), so the §4 test asserts the **exact** sum = 100
   before rounding, not the displayed sum.
5. **Old clients.** Old clients get the new percentages (gross long value) without the new `cash` slices, so their listed holdings add up to
   **less than 100%** when cash is positive. With negative cash they no longer exceed 100%. They keep rendering; only the ">100%" note they
   show becomes unnecessary. Server and clients should ship in the same release (§5).
6. **Labels** (exact copy, the same on the dashboard and Insights):
   - caption: "Share of what you own: holdings plus cash you hold.";
   - unfunded line: "Unfunded cash −CAD 69.53", with the note "Borrowed or unfunded cash isn't part of these percentages.";
   - the D3.1 `cashNote` (">100%") is removed.

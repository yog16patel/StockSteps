# Financial metrics engine — Phase 9B

Code: `server/.../marketdata/metrics/FinancialMetricsEngine.kt` (pure: no clock, no I/O; exact `BigDecimal`, `MathContext(16, HALF_EVEN)`,
results rounded half-even to 6 decimals). Tests: `FinancialMetricsEngineTest` (11, hand-calculated). Input: `CompanyFinancialHistory`
(any provider; SEC today) + optional `PriceObservation` + `SplitHistory`. Every `MetricResult` carries value **or** a status (never both —
enforced in `init`), formula, period label, as-of date, inputs (tag, accession, derived/restated flags) and a note. Percent metrics are in
percent (25 = 25 %), like `FinancialCalculations`. Not wired to routes (D-9B-2); existing `FinancialCalculations`/`ValuationCalculations`
are unchanged.

## 1. Formulas and rules

| Metric | Formula | Period | Null when (status) |
|---|---|---|---|
| Revenue growth | (cur − prior) ÷ prior × 100 | same fiscal period one year earlier (350–380 days) | no prior (INSUFFICIENT_HISTORY); different period kind or gap (PERIOD_MISMATCH); currency/unit differs (CURRENCY_MISMATCH); different tag (UNRELIABLE_COMPARISON); prior ≤ 0 (NON_POSITIVE_DENOMINATOR) |
| Net income growth, EPS growth | same, diluted EPS for EPS | same | same; growth from a loss is never shown |
| Gross margin | gross profit ÷ revenue × 100; gross profit = revenue − cost of revenue only when both reported (noted) | the period | revenue ≤ 0; inputs missing |
| Operating / net margin | operating income (net income to parent) ÷ revenue × 100; negative margins are valid | the period | revenue ≤ 0 |
| EPS basic / diluted | reported | the period | not reported |
| TTM | latest FY when no quarter is newer; else four consecutive discrete quarters (84–98 days, gaps 1–7 days, one currency); never YTD sums | rolling | < 4 quarters; gap/overlap; currency mix |
| TTM diluted EPS | as TTM; a missing Q4 per-share value = FY − Q1 − Q2 − Q3 (flagged **approximate**: share counts change) | rolling | as TTM |
| P/E | price ÷ TTM diluted EPS (split-adjusted to the price date) | TTM to latest quarter | EPS ≤ 0 (NOT_MEANINGFUL — never a negative P/E); price stale/unknown/≤ 0 (PRICE_UNUSABLE); currency mismatch or unknown; fundamentals > 200 days before or after the price date (PERIOD_MISMATCH); split-like share jump without a verified split history (SPLIT_UNVERIFIED) |
| P/S | price × latest cover shares outstanding (≤ price date, ≤ 200 days old) ÷ TTM revenue | TTM | several share classes (AMBIGUOUS); revenue ≤ 0; price/currency/period rules as P/E |
| Dividend yield | TTM dividends declared per share (split-adjusted) ÷ price × 100 | TTM | no dividend facts (MISSING_INPUT — absence ≠ no dividend); negative DPS; price rules; split rule |
| Debt-to-equity | (`LongTermDebt`, or `LongTermDebtCurrent` + `LongTermDebtNoncurrent`, + `ShortTermBorrowings` when reported) ÷ equity | balance at period end | no long-term debt total (absence ≠ zero debt); equity ≤ 0 |
| Current ratio | current assets ÷ current liabilities | period end | missing; liabilities ≤ 0 |
| ROE / ROA | FY net income ÷ average(opening, closing) equity / total assets × 100 | fiscal years only (quarters aren't annualized) | no prior-year balance; average ≤ 0; currency mismatch |
| Free cash flow | operating cash flow − purchases of PP&E (both reported); negative FCF is valid | the period | either missing; capex reported negative (NOT_MEANINGFUL) |

## 2. Edge cases covered by tests

Negative earnings (P/E NOT_MEANINGFUL, growth from a loss refused, negative margin kept); missing facts (FCF without capex, D/E without
debt totals, yield without dividend facts, ROE without opening equity); restatements (10-K/A used, provenance kept, growth on the restated
value); incomplete quarters (fewer than four, a gap); YTD never summed; derived Q4 (flows exact, per-share approximate); tag switch
(UNRELIABLE_COMPARISON); currency mismatch (price CAD vs USD statements, unknown currency); stale/unknown/zero price; fundamentals too old
or newer than the price; split-like share jump without verified splits (SPLIT_UNVERIFIED) and the adjusted P/E with a verified 4-for-1
split; share-class ambiguity (P/S AMBIGUOUS); same-filing duplicate values (AMBIGUOUS); determinism (two runs equal).

Hand-calculated fixture values (growth company, FY2025, price 65.00 on 2026-05-15): revenue growth 20 %, net income growth 40 % (restated),
EPS growth 50 %, gross margin 41.666667 %, operating 16.666667 %, net 11.666667 %, D/E 0.55, current ratio 1.5, ROE 25.454545 %, ROA
10.769231 %, FCF 200,000,000, TTM revenue 1,270,000,000, TTM diluted EPS 3.25 (approximate), P/E 20, P/S 2.559055, dividend yield
1.615385 %.

## 3. Provenance and licence

A ratio computed only from SEC facts is INTERNALLY_DERIVED from OFFICIAL_PUBLIC data (cleared). A ratio that uses a price (P/E, P/S,
yield) inherits the price's licence (`LicenseRegistry.derivedDisplayAllowed`) — FMP prices aren't cleared, so those stay blocked for a
commercial launch.

## 4. Limits
- Split history comes from a licensed corporate-actions source that doesn't exist yet in REAL (D-9B-5); without it only split-like jumps
  are detected.
- Financial-sector companies (no gross profit; interest income) aren't specially modelled: missing inputs give MISSING_INPUT.
- Differences from FMP's own ratios (definitions, TTM methods) must be reviewed before switching (D-9B-2).

# Phase 8B — Company Details review (verification; no code change)

Company Details was refined in 5D.5.4A–D and UI Phase 3 under an owner-approved page order (plan §C, D-E1…D-B1). 8B audited it against
the brief and found no P0/P1 issue, so the order and layout were left as approved. Evidence: Android `light/dark/font2-details1..3`, iOS
`light-details1..2`, `dark-details2`, `ax5-details2` (MOCK, AAPL).

| Brief question / requirement | Result | Where |
|---|---|---|
| Which company? | PASS | Logo, "Apple Inc.", "AAPL · NASDAQ" first |
| What does it do? | PASS | "What Apple does" (first sentence verbatim, `whatItDoes`), sector · industry |
| Latest price and when | PASS | "$336.67", "+$3.04 (+0.91%) Oct 7", "Closed · Updated 4:00 PM EDT · Oct 7 · Prices may be delayed" — dated, never "live" |
| Price history | PASS | 1D–ALL selector; the caption follows the selected period ("+6.47% past 1M"); touch to inspect with dated labels; spoken summary (`ChartPresentation.summary`); no smoothing or invented points; loading / no-history / retry states |
| Key metrics | PASS | At a Glance: Market Cap ("Large company"), P/E ("28x earnings"), Revenue Growth ("Year over year · Growing revenue"), Dividend Yield ("Pays dividend"), 52-week range — each with ⓘ explanation sheets (`MetricLearnTerms`) |
| Financial health / valuation | PASS | Understand rows (Growth, Profitability, Financial Health, Valuation) with core tones; Valuation card (P/E vs 5Y average only with a reliable average); Financial Highlights and Key Ratios with period labels ("Latest fiscal year (2025) · Change vs prior year") |
| Missing data | PASS | "—" / unavailable notices; never 0 (`CompanyDetailsPresentation`, 5D.5.4 tests) |
| No advice / fair value | PASS | "This is context, not a recommendation."; no scores or targets |
| AX5 / 2.0× | PASS | iOS AX5 stats stack; Android 2.0× chart stats single column |

Remaining P2 (not changed): chart Y-axis labels show numbers without a currency symbol (the headline and scrub label carry it). Changing
axis labels touches the shared chart used by Portfolio and Comparison; left as a follow-up.

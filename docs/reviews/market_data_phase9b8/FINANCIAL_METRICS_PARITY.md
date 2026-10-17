# Financial metrics parity — Phase 9B.8

**Enabled (Alpha Vantage source, via `FinancialMetricsEngine`):** revenue growth, net income growth, gross/operating/net margin, ROE, ROA,
D/E, current ratio, FCF, FCF margin, net debt, interest coverage, trailing dividend per share and yield, dividend growth, payout ratio,
revenue CAGR 3/5. **Not enabled (unverified inputs):** EPS and EPS growth/CAGR, P/E, PEG, forward P/E, P/S, P/B, P/FCF, EV, EV/EBITDA,
quick ratio, ROIC, net debt/EBITDA, shares, valuation history ratios.

Fixture parity (hand-calculated, `AlphaVantageFundamentalsTest`): revenue growth 20 %, net income growth 40 %, gross 41.666667 %,
operating 16.666667 %, ROE 25.454545 %, ROA 10.769231 %, D/E 0.55, current ratio 1.5, net debt 210 M, interest coverage 21, FCF 200 M,
FCF margin 16.67 %, payout 35.71 %, CAGR3 14.471424 %, DPS 1.00, yield 2.00 % — the same values the 9B engine gives for the SEC fixture of
the same company.

Known definition differences vs FMP (expected, documented — the reason the switch is opt-in):
| Metric | FMP | 9B.8 Alpha Vantage |
|---|---|---|
| margins | TTM (ratios-ttm) | the reported period |
| ROE / ROA | TTM, provider method | fiscal year ÷ average opening/closing balance |
| D/E | provider TTM | reported total debt ÷ equity at the period end |
| dividend yield | provider TTM | ex-dates in the trailing 365 days ÷ our dated close |
| P/E, P/S… | provider TTM | unavailable |

**REAL parity BLOCKED** (no Alpha Vantage key, no SEC User-Agent, no REAL FMP calls): the six-company comparison (large profitable tech,
loss-maker, bank, negative equity, split, incomplete quarters) is the first task after D-9B8-1/2. No value is produced from an invalid
denominator in any path (tests: negative equity, losses, zero revenue).

# Financial metrics comparison — Phase 9B.7

**REAL comparison BLOCKED:** comparing Alpha Vantage, SEC and the existing FMP values for real companies (a large profitable tech company,
a loss-maker, a bank, a negative-equity company, a split company, a company with incomplete quarters) needs an Alpha Vantage key and an
approved SEC User-Agent; neither exists here, and REAL FMP calls are excluded by rule (key at its limit).

What was verified with fixtures:
- The AV statement fixture (`AvFixtures.INCOME/BALANCE/CASH_FLOW`) uses the same figures as the SEC fixture's growth company; the AV
  mapping produces the same `FinancialPeriodStatement` fields as `SecEdgarClient.toStatement` for FY2025 (revenue 1.2 B, net income
  140 M, OCF 260 M, capex 60 M, FCF 200 M, equity 600 M) — `AlphaVantageProvidersTest.statementsMapToTheExistingContract`.
- Differences by construction: AV statements carry **no EPS** (null; SEC and FMP have diluted EPS); AV debt = `shortLongTermDebtTotal`
  (SEC: `LongTermDebt` + short-term borrowings); AV gives no fiscal labels (inferred from period ends).
- The metrics engine (9B) is **not** switched to AV values: formulas agree, but periods (inferred labels) and definitions (debt, EPS basis)
  differ, so values would change without a documented comparison.

Planned comparison (next phase, after D-9B7-1/3): for AAPL (profitable tech), RIVN (loss-maker), JPM (bank: no gross profit), MCD
(negative equity), NVDA (2024 10-for-1 split) and one recent IPO (incomplete quarters): revenue growth, margins, EPS, P/E, P/S, D/E, ROE,
ROA, FCF, dividend yield, TTM — FMP vs AV vs SEC, flagging every disagreement with its cause; no value from an invalid denominator.

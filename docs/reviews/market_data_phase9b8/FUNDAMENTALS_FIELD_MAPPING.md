# Fundamentals field mapping — Phase 9B.8

Contract: core `CompanyFundamentals` (unchanged). One seam serves every consumer: `DataSources.stockProvider` →
`CompanyFinancialService` → Company Details (`/stocks/{s}/details`, `/fundamentals`), valuation (`/valuation` fallback), Comparison
(history, research, AI comparison input), Discover (`screenerFundamentals`, value filters when the warm-up is enabled). No Watchlist or
Portfolio insight reads fundamentals (checked). With `FUNDAMENTALS_PRIMARY_PROVIDER=alpha_vantage` every consumer reads the same bundle.

Legend: AV = Alpha Vantage field (documented shape; live names to confirm, `KNOWN_LIMITATIONS.md`); E = `FinancialMetricsEngine`;
C = existing `FinancialCalculations`; — = unavailable with a reason. Periods: annual = latest fiscal year; quarter = latest quarter vs the
same quarter a year earlier; ROE/ROA/payout always fiscal-year (basis says "annual"). Units: whole reporting-currency units; percentages
in percent.

| Group · id | FMP today | Alpha Vantage (9B.8) | SEC (9B, not wired) | Notes |
|---|---|---|---|---|
| growth · revenue | `income-statement.revenue` | `totalRevenue` | Revenues tags | reported |
| growth · revenueGrowth | YoY (C) | E.growth (same tag, same period kind, prior > 0) | E | |
| growth · revenueCagr3/5 | C on FY | C on FY (`totalRevenue`, same currency) | — | |
| growth · netIncome | `netIncome` | `netIncome` | NetIncomeLoss | parent only on SEC |
| growth · netIncomeGrowth | YoY, prior > 0 | E.growth | E | |
| growth · eps, epsGrowth, epsCagr3/5 | `epsDiluted` | — (no EPS in statements; EARNINGS/OVERVIEW EPS basis undocumented) | EarningsPerShareDiluted | biggest gap |
| profitability · gross/operating/net margin | `ratios-ttm` TTM, else statement | E.margin on the period (gross = revenue − cost only if both reported) | E | basis: period, not TTM |
| profitability · roe, roa | `key-metrics-ttm` TTM | E.returnOn: FY net income ÷ average opening/closing equity/assets | E | convention differs from FMP's TTM |
| profitability · roic | key-metrics | — | — | |
| health · cash, assets, liabilities, equity | balance sheet | `cashAndCashEquivalentsAtCarryingValue`, `totalAssets`, `totalLiabilities`, `totalShareholderEquity` | tags | |
| health · debt | `totalDebt` | `shortLongTermDebtTotal` (engine concept TOTAL_DEBT) | LongTermDebt + ShortTermBorrowings | definitions differ |
| health · netDebt | `netDebt` or debt − cash | debt − cash (calculated) | — | |
| health · debtEquity | ratios-ttm or debt ÷ equity | E.debtToEquity (reported total debt; equity ≤ 0 → not meaningful) | E | |
| health · currentRatio | ratios-ttm or CA ÷ CL | E.currentRatio | E | |
| health · quickRatio, netDebtEbitda | ratios / key-metrics | — | — | |
| health · interestCoverage | ratios-ttm or EBIT ÷ interest | C: `ebit` ÷ `interestExpense` (> 0) | — | |
| cashFlow · operatingCashFlow, capex | cash-flow statement (capex negative → spending) | `operatingCashflow`, `capitalExpenditures` (stored as positive spending); **quarterly only if verified standalone** | tags | YTD detection |
| cashFlow · freeCashFlow | reported or OCF − capex | E.freeCashFlow (both reported) | E | |
| cashFlow · fcfMargin | C | C | — | |
| shareholder · dividendYield | ratios-ttm | E.trailingDividendYield: DIVIDENDS ex-dates in trailing 365 days ÷ dated price, coverage proven, no split in window | — | |
| shareholder · dividendPerShare | ratios-ttm | E.trailingDividendPerShare | DPS declared | |
| shareholder · dividendGrowth | calendar-year totals (C) | same rule on DIVIDENDS (C) | — | |
| shareholder · payoutRatio | ratios-ttm | FY `dividendPayout` ÷ net income > 0 (C) | — | |
| shareholder · buybacks | cash flow | `paymentsForRepurchaseOfCommonStock` (FY) | — | |
| shareholder · shares, sharesChange5 | shares-float | — (no verified share basis) | dei cover shares | |
| valuation · pe, peg, forwardPe | ratios-ttm, estimates | — | E.pe with SEC EPS (after validation) | |
| valuation · priceSales, priceBook, priceFcf, enterpriseValue, evEbitda | ratios-ttm / key-metrics | — (needs verified shares) | E.ps with SEC shares | |
| valuation · historical (pe, priceSales, priceBook, priceFcf, evEbitda) | FMP annual ratios | empty with reason | — | P/E monthly history is separate (`ValuationService`, FMP EPS) |
| history (statement rows) | FMP statements | AV statements (`AlphaVantageStatements.merge`) | `toStatement` | same rows as `statementHistory` |
| datasets | per FMP dataset | income / balance / cashFlow / dividends / splits | — | TEMPORARILY_UNAVAILABLE for a refused optional dataset |

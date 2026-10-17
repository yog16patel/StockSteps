# SEC EDGAR integration — Phase 9B (adapter + tests; not wired to routes)

Code: `marketdata/sec/SecEdgarClient.kt`, `marketdata/sec/SecCompanyFacts.kt`. Tests: `SecEdgarParsingTest` (10), `SecEdgarClientTest` (8),
SEC part of `MarketDataPhase9bBenchmarkTest`. Fixtures: `server/src/test/.../marketdata/SecFixtures.kt` (SEC-shaped JSON, invented values,
two curated test companies: a growing filer with 10-K comparatives, 10-Q YTD values and a 10-K/A; a loss-making filer with a revenue tag
switch, negative equity, no capex, a foreign-currency fact and two share classes). **No request to sec.gov was made in this phase.**

## 1. Endpoints

| Use | URL | Bound | TTL | Notes |
|---|---|---|---|---|
| Ticker ↔ CIK ↔ exchange | `https://www.sec.gov/files/company_tickers_exchange.json` | 8 MB | 24 h | `fields`/`data` arrays; `BRK.B` = `BRK-B`; an ambiguous ticker (two CIKs) → null |
| Company facts | `https://data.sec.gov/api/xbrl/companyfacts/CIK##########.json` | 48 MB (configurable) | 24 h | only the tags in `SecConcepts` are kept in memory |
| Submissions | `https://data.sec.gov/submissions/CIK##########.json` | 12 MB | 6 h | name, tickers, exchanges, fiscal year end, recent filings (accession, form, filing and report dates) |

Fair access: every request sends `User-Agent: <SEC_USER_AGENT>` (must contain a contact e-mail; the client refuses to start otherwise; no
CR/LF), `Accept: application/json`; the `sec` lane of `ProviderGuard` paces 300/min (5/s), burst 10, 2 concurrent; configuration can't raise
it above 600/min/burst 10 (SEC: 10 requests/second); 30 s per-request timeout in production. Coalescing: one `CompanyFinancialCache` with
single flight per key (1,000 concurrent readers of 20 companies → 21 requests). Bounded payloads: `apiCallText` refuses a declared
`Content-Length` over the bound and stops reading at bound + 1 byte. Outcomes: 404 cached as "none" (24 h); 403 cooled down 1 h, 429 10 min
(`providerCooldown`), never retried; malformed JSON → `INVALID_RESPONSE`, not data. Metrics: `ProviderCalls.record` counts `sec` with
bounded endpoint labels (`xbrl/companyfacts`, `submissions`, `company_tickers_exchange.json` — never a CIK); cache events under `cache.sec.*`.

## 2. Parsing rules

- Facts are identified by **start/end dates**, not `fy`/`fp`: those describe the filing (a FY2025 10-K carries FY2024/2023 comparatives with
  `fy = 2025`).
- Forms: `10-K`, `10-K/A`, `10-Q`, `10-Q/A` only (8-K, 20-F, 40-F, S-1… ignored; foreign private issuers are unsupported — explicit gap).
- Units: the reporting currency is the 3-letter unit of most revenue/net-income facts; other currencies are excluded; per-share
  `XXX/shares`; share counts `shares`. Values are exact `BigDecimal`.
- Malformed rows (string values, bad dates, start after end, no accession/form/filed) are skipped and counted.

## 3. Fiscal periods

| Rule | Implementation |
|---|---|
| Annual | 350–380 days (52/53-week years) |
| Quarter | 84–98 days (13/14 weeks); 6- and 9-month YTD values are **never** quarters |
| Fiscal labels | from the earliest filing whose own period (latest us-gaap duration end) is this period; a period only seen as a comparative takes that filing's fy minus the whole years between the two ends |
| Q4 | for summable flows only: FY − 9-month YTD of the same fiscal year and the same tag, marked `derived`; per-share values and share counts are never derived by the adapter |
| Balance sheet | instants at the period end (Q4 takes the year-end balances) |
| Shares outstanding | `dei:EntityCommonStockSharesOutstanding` (cover page), newest first; several values on one date in one filing = several classes → `AMBIGUOUS` (never summed) |

## 4. Concepts (priority order; tag used is kept)

Revenue: `Revenues` → `RevenueFromContractWithCustomerExcludingAssessedTax` → `…IncludingAssessedTax` → `SalesRevenueNet`. Cost of revenue:
`CostOfRevenue` → `CostOfGoodsAndServicesSold`. `GrossProfit`, `OperatingIncomeLoss`, `NetIncomeLoss` (parent only; `ProfitLoss` is never a
fallback), `EarningsPerShareBasic`, `EarningsPerShareDiluted`, `WeightedAverageNumberOfDilutedSharesOutstanding`,
`NetCashProvidedByUsedInOperatingActivities`, `PaymentsToAcquirePropertyPlantAndEquipment`, `CommonStockDividendsPerShareDeclared`,
`Assets`, `Liabilities`, `StockholdersEquity`, `AssetsCurrent`, `LiabilitiesCurrent`, `CashAndCashEquivalentsAtCarryingValue`,
`LongTermDebt`, `LongTermDebtCurrent`, `LongTermDebtNoncurrent`, `ShortTermBorrowings`.

Tag choice per period: among candidate tags, the facts of the **latest filing** reporting the period, then priority — so one filing's
comparatives stay on one tag. A series that changes tag between periods is flagged by the metrics engine (`UNRELIABLE_COMPARISON`).

## 5. Original vs amended

Default `RestatementPolicy.LATEST_FILED`: the most recent filing's value, with `originalValue` = first filed, `restated` when they differ,
`amended` for `/A` forms, `sourceRef` = accession, `filed`, `form`. `ORIGINAL` gives as-first-reported values (for "what was known then").
Two different values for one period in **the same** filing → `AMBIGUOUS`, no value.

## 6. Freshness

`Provenance.asOf` = latest period end; `sourceRef` = latest filing accession; `retrievedAt` = fetch time (cache hits keep it). Filing lag:
10-Q within 40–45 days, 10-K within 60–90 days of period end (SEC deadlines by filer size). Freshness of price ratios is enforced by the
metrics engine (fundamentals ≤ 200 days before the price date).

## 7. Known gaps
- Taxonomy variety: banks/insurers (no `GrossProfit`, different revenue tags), REITs, extension tags (`aapl:…`) → missing, not guessed.
- Dimensional facts (segments, share classes) aren't in companyfacts.
- Fiscal-year labels for the very oldest comparatives are inferred (rule above).
- `company_tickers*.json` is "not guaranteed accurate or complete" (SEC); delisted tickers drop out.
- Wiring into Company Details (D-9B-2) and a field-by-field comparison with FMP values on real filings are the next phase. No live SEC
  request was made (needs `SEC_USER_AGENT` with the owner's contact address).

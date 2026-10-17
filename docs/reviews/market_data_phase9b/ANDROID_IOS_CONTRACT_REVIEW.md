# Android / iOS contract review — Phase 9B

**No mobile contract changed.** 9B touches only `server/` (new package `marketdata/`, `httpclient/NetworkUtils.kt`, `service/ProviderBudget.kt`,
`screener/CuratedScreenerUniverse.kt`, `appconfig/StartupConfiguration.kt`, one startup log line in `Application.kt`) and server tests.
`core/`, `app/shared/`, `app/androidApp/` and `app/iosApp/` are untouched by 9B (the uncommitted 8E changes in `core/navigation` and
`app/shared/presentation` are another phase's and were not modified).

| Check | Result |
|---|---|
| Route list / `RouteAccess` table | unchanged (no new endpoint; `RouteAccessTest` passes) |
| Response models (`StockQuote`, `CompanyFundamentals`, `FinancialPeriodStatement`, `PricePoint`, screener models, Portfolio/Practice reports) | unchanged |
| Nullability / freshness fields | unchanged; Discover rows still carry `priceDate` and nulls for not-yet-enriched values |
| Discover behaviour visible to users | fewer companies may show the **latest** close on a trading day (each row keeps its own dated close); coverage warnings unchanged |
| 9A controls (central auth, uid ownership, guest public access, per-class limits with Retry-After, internal routes, safe logging, MOCK/REAL isolation) | unchanged; full `:server:test` incl. 9A suites |
| MOCK | `SCREENER_UNIVERSE` MOCK path unchanged (fixture universe); SEC adapter not constructed; zero provider calls |

## Backward-compatible migration path (future, D-9B-2)
SEC statements map into the existing `FinancialPeriodStatement` (`SecEdgarClient.toStatement`): same fields, Doubles in whole currency
units, `period` "FY"/"Q1"…"Q4", `fiscalYear`, `date` = period end, `currency`. Derived Q4 values can be excluded (`includeDerived = false`).
`StatementHistory.source` would read `"sec"`; older apps ignore it. A provenance/attribution string ("Source: SEC EDGAR filings") would be
an **additive optional** field if the owner wants it shown — not added in 9B. No Android/iOS build change is needed for any of this.

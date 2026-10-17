# Cache inventory — Phase 6 audit (code at `be3c15e`)

All rows are `CompanyFinancialCache` (bounded LRU, access-ordered; per-key single flight; a failing load is shared with joined callers and
**not stored** unless the caller caches an outcome value with `resultTtl`/`providerCooldown`; stats as `cache.<name>.<event>`), unless
noted. All are **per process**: a restart or a new Cloud Run instance starts empty. Public market data only, unless marked user data.

## 1. Server caches

| Cache (`name`) | Owner | Key | TTL | Capacity | Failure handling | Consumers |
|---|---|---|---|---|---|---|
| `fmp` | `FmpStockProviderRepositoryImpl` | `quote:SYM` | 30 s (≤ 15 min while the listing's market is inactive) | 4,096 (shared) | **none: refused quotes are re-requested every time** | every quote reader |
| `fmp` | same | `profile:SYM` | 24 h | shared | none at this layer (`StockService` cools down) | profile readers |
| `fmp` | `FmpFundamentalsLoader` | `endpoint:SYM:period:limit` (+ `cooldown:` layer) | statements 24 h (earnings-aware shorter), TTM statements 6 h, ratios/key metrics 15 min active (≤ 6 h closed), estimates 6 h | shared | 402/403 → 1 h, else 30 s; stale copy ≤ 7 d (statements) / 2 d (estimates), labelled | Details, Financials, Valuation, Comparison, screener |
| `fmp-intraday` | `FmpPriceHistoryProvider` | `5min:SYM` | 5 min (until active when closed, ≤ 6 h) | 256 | callers' cooldowns | 1D chart, sparklines (one request) |
| `profiles` | `StockService` | SYM | 24 h | 512 | outcome: 402/403 1 h, 429 10 min, else 30 s | profile route, Details, Watch, Comparison, Earnings names |
| `search` | `StockService` | normalized lower-case query | 6 h | 512 | not cached | Search, Earnings name filter |
| `price-chart` | `PriceChartService` | `daily:SYM`, `intraday:SYM` | 6 h / 5 min | 256 | outcome cooldown (402/403 1 h, 429 10 min) | charts, valuation, movement, comparison, portfolio, earnings |
| `sparklines` | `SparklineService` | SYM | 5 min | 256 | outcome cooldown | Home sparklines |
| `markets` | `MarketsService` | `overview`, `section:*` | overview 30 s / 5 min; sections 60 s / 15 min (sectors ×2); news 5 min | 128 | whole-section failures cool down; **individual quote failures inside a section don't** | Markets, Daily Brief |
| (unnamed) | `CompanyDetailsService` | `status` | 60 s | 4 | — | legacy path only; REAL passes the local `UsMarketCalendar` status |
| `symbol-not-found` | `SymbolExistence` (Phase 4A gate before per-symbol bundles) | SYM | 24 h, only a confirmed "no profile and no quote" | 20,000 | provider failures → UNKNOWN, never cached as not found | Details, fundamentals, comparison |
| `valuation` | `ValuationService` | `valuation:SYM` | 6 h | 512 | — | Valuation, Details |
| `movement` | `MovementService` | `movement|SYM|P|ver`, `quote|SYM`, narration | 5 min (1D) / 1 h; quote 60 s; narration 24 h | 512 | — | Why Did It Move? |
| `company-news` | `NewsService` | `company:SYM` | 10 min (empty 1 min) | 256 | — | news, movement, watch, alerts, brief |
| Finnhub names map | `FinnhubNewsProviderRepositoryImpl` | SYM | 6 h / failure 5 min | unbounded map | — | company-news relevance |
| `earnings` | `EarningsService` | `calendar:F:T`, `history:SYM`, `history:SYM:w`, `names:q` | 1 h (past 12 h), 6 h, 6 h, 1 h | 512 | last-good stale copy | earnings, watch, comparison, reminders |
| `watch` | `WatchMarketData` | `quote|SYM`, `profile|SYM`, `earnings|SYM`, `earnings-result|SYM` | 60 s, 24 h, 12 h, 30 min | 2,048 | any failure or null → 60 s | Watchlist, alerts, Portfolio, Practice, Brief overlay |
| (portfolio) | `PortfolioMarketService` | `portfolio-history:SYM` | 6 h (on top of `price-chart`) | 256 | — | My Portfolio |
| `fx` | `BankOfCanadaPortfolioFx` (one per consumer) | `FROM:THROUGH` | 6 h | 64 | — | Portfolio, Practice, Comparison |
| (practice) | `PracticeService` | `fx:FROM:TO` | 1 h (on top of BoC) | — | `safe` → null | Practice |
| `screener-universe` | `FmpScreenerUniverse` | `universe` | 24 h | 4 | outcome cooldown (since `be3c15e`) | Discover |
| screener records / page | `ScreenerService` | SYM / query | 6 h / 5 min | universe / 128 | — | Discover, Comparison |
| `comparison-history` | `ComparisonHistoryService` | `SYM|period` | 6 h | 256 | budget `ProviderRequestBudget` | Comparison P3–P5 |
| AI caches | Article insights, Earnings P5 explanations, Comparison AI | content + version keys | 7 d / version / 10 min | bounded | failure 15 min (insights) | AI features (public summaries only) |
| News simplification | Firestore store | article digest + version | persistent | — | claim/fail records | news (cross-instance) |
| **User data** | Firestore (`users/{uid}/…`), durable AI quota | uid-scoped | persistent | — | 503 `USER_DATA_UNAVAILABLE` | never in a shared key (verified in the Phase 1 audit and unchanged) |
| Admission / budget | `AdmissionController`, `ProviderGuard`, `ProviderUsageMeter` | identity / provider / endpoint | rolling minute; circuit 30 s–5 min | — | — | all routes |

## 2. Observed hit/miss (LAN REAL, 21:57Z–00:05Z, one user)

`fmp` 3 hit / 198 miss / 13 join / 20 expired; `profiles` 109 / 21; `search` 14 / 11; `markets` 12 / 39 (+1 join, 32 expired);
`price-chart` 12 / 3; `earnings` 16 / 4; `fx` 2 / 1; `valuation` 0 / 1. The `fmp` rate is low because refused quotes are never cached.

## 3. Duplicate or wasted provider calls

| # | Where | What | Recommendation |
|---|---|---|---|
| 1 | every quote reader | a refused (402) quote is re-requested on every read | cache the outcome: 402/403 1 h, 429 10 min |
| 2 | Markets sectors / proxies | the same refused ETFs every 2 min live, every 30 min closed | as 1, and back sectors with EOD closes |
| 3 | ProviderGuard on a daily cap | 429 "Limit Reach" retried after 30 s → 5 min | daily-cap state until reset |
| 4 | Finnhub earnings history | `history:SYM` and `history:SYM:w` for the same data | one key |
| 5 | BoC FX | separate instances per consumer + Practice's own layer | one shared instance |
| 6 | profile layers | three 24 h layers (data up to ~72 h old) | acceptable for profiles; keep |
| 7 | daily closes | `price-chart` 6 h + `portfolio-history` 6 h | collapse to one layer, or a session-aware "until the next close" TTL |
| — | cross-feature | Home, Markets, Details, Search, Discover, Watchlist, Portfolio and Practice share the same `fmp`/`watch`/`price-chart` caches on one instance | already shared; no new layer needed |

## 4. Justified changes only (recommendations, not implemented)

- **Negative caching:** quote outcomes (item 1) and the daily-cap state (item 3). Nothing else is missing.
- **Session-aware EOD TTL:** daily closes could live until the next official close is expected (~16:30 ET plus a provider delay), rather
  than a flat 6 h, so the new close appears promptly and isn't refetched overnight.
- **Stale-while-revalidate:** it already exists for statements and estimates (Phase 3E, labelled). Extending it to EOD closes is
  reasonable (a close never changes, except for corrections).
- **Coalescing:** already present everywhere (single flight).
- **Persistence and cross-instance caching:** not until the vendor licence allows shared storage (Q4); no Redis.

# Recommended MVP market-data architecture — Phase 6 (recommendation only)

The smallest design that is licensable, cheap and honest for a beginner education app. It reuses the existing server, adapters,
`CompanyFinancialCache`, `apiCall`/`ProviderGuard`/`ProviderCalls` and admission groups. **No new infrastructure, no migration.** Every item
needs owner approval; the vendor choice depends on the licence answers (report Q1–Q8).

## 1. Primary provider per dataset

| Dataset | Primary | Fallback | Freshness shown to users | Shared cache TTL |
|---|---|---|---|---|
| Daily closes and history (charts, valuation, Markets sectors/indices, Watchlist, Portfolio, Practice) | FMP `historical-price-eod/light` (works on today's key) | none (missing = unavailable) | "Close, <date>" | until the next expected close (~16:30 ET + provider delay); stale copy labelled |
| Delayed intraday quote (Details header only, optional) | FMP `quote` **only if licensed for display** | last close, labelled | "Delayed · <time>" | 30 s active; until the next session when closed; outcome cooldown 402/403 1 h, 429 10 min |
| Profiles, logos | FMP `profile` + logo URL | — | — | 24 h |
| Statements, ratios, estimates, dividends | FMP (existing bundle, earnings-aware) | stale ≤ 7 d labelled (existing) | fiscal period + filing date | 24 h / 6 h / 15 min (existing) |
| Search | FMP `search-symbol` + `search-name` | — | — | 6 h |
| Movers | FMP gainers/losers/most-actives | — | "Latest session" | 60 s / 15 min |
| Discover universe | curated, labelled list of ~150 US large caps in the repository, enriched with profiles and the existing bundle | — | "A fixed list of about N large US companies chosen by StockSteps" | static list; enrichment 24 h |
| News | Finnhub (if its commercial terms are acceptable), else the primary vendor's news | — | publisher + time | 10 min / 5 min |
| Earnings calendar and history | Finnhub (as above) | last-good copy (existing) | — | 1 h / 6 h |
| FX | Bank of Canada Valet | — | publication date | 6 h, one shared instance |

Fallbacks are added only where licensed and justified. Never REAL → MOCK, never estimated values (existing rule).

## 2. Practice

Immediate execution and valuation at the **last official close**, labelled with its date, through the same shared EOD cache. No pending
orders, no scheduler. A queued next-close model can come later as a lesson. A REAL corporate-actions source (splits) is a prerequisite
before Practice holds positions across a split (known gap).

## 3. Guards and error handling

- `ProviderGuard` with the **verified** plan values: per-minute rate and `PROVIDER_FMP_DAILY_TARGET` (today the LAN value 250 is applied
  per minute).
- A **daily-cap state**: a 429 "Limit Reach" stops FMP calls until the provider's reset and serves cached or EOD data with a labelled notice,
  instead of a 30 s → 5 min breaker.
- **Outcome cooldowns for every per-symbol dataset** (quotes added; statements, charts and the universe already have them).
- Distinct codes everywhere: plan-restricted (no Retry; "not included in this server's market data plan"), rate-limited ("try again in a few
  minutes"), temporary ("try again shortly"), the pattern already used by Discover.
- Coalescing: existing single flight. Admission `RouteGroup`s and `RequestRateLimiter`s: unchanged.

## 4. Isolation and monitoring

- Public market data in shared per-instance caches; user data only in Firestore under `users/{uid}`, never in a shared key (unchanged).
- Quota monitoring: the existing `stocksteps.usage` summaries per minute. Alert on `denied` > 0 for a dataset that should be licensed, on
  `provider.fmp.dailyTargetExceeded`, and on `circuitOpened.rateLimited`.
- `/internal/metrics/usage` stays internal (token).

## 5. What is deliberately not in the MVP

Real-time streaming; full-market screener; intraday 1D charts and sparklines unless the licence covers them (drop or keep labelled);
Redis/Firestore market-data caches (licence Q4); more than one vendor per dataset.

## 6. Order of work (after the licence answers)

1. Config: verified per-minute and daily FMP values (no code).
2. Server: quote outcome cooldown, daily-cap state, plan-restricted error code (small; MockEngine tests and benchmarks).
3. Markets: sectors and index proxies from EOD closes.
4. Practice and Portfolio: last-close valuation and execution (owner-approved copy).
5. Discover: curated universe.
6. Small duplicates: Finnhub history key, one BoC instance, one daily-close layer.

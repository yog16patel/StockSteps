# API usage and cost model — Phase 6 audit

**MODELED, order of magnitude, one server instance.** Per-action request counts are from the code paths, the Phase 3 MockEngine benchmark
(`Phase3AuditBenchmarkTest`: Company Details cold 17) and the LAN REAL usage summaries (Markets build ≈ 26 quotes + 3 movers). No dollar
figure is claimed: provider subscription and licence prices are **UNVERIFIED** (Q1–Q5, Q8 in the report). Cloud costs (Cloud Run,
Firestore, logging) are separate: `docs/CLOUD_RUN_COST_AND_SCALING.md`.

## 1. Measured per-action upstream requests (cold → warm)

| Action | Cold | Warm (within TTLs) |
|---|---:|---:|
| Company Details open (profile, quote, 11 annual datasets, 24-quarter income, EOD; status local) | ≈ 16 FMP + Finnhub news 2 | 1–3 (quote 30 s, TTM ratios 15 min) |
| Financials quarterly tab | +3 | 0 |
| Search query | 2 FMP | 0 (6 h) |
| Markets / Home overview rebuild | ≈ 3 movers + 19–26 quotes + 3 EOD + 1 Finnhub | 0 within 30 s live / 5 min closed |
| Watchlist view (n symbols) | n quotes + n profiles + n Finnhub histories | n quotes per 60 s |
| Portfolio view (h holdings) | h quotes + h EOD + 1 BoC | h quotes per 60 s |
| Practice overview (p positions) / trade | p quotes / 1 quote + 1 profile | per 60 s |
| Discover open + first page | 3 universe + 20 quotes (+ warm-up, 0 on LAN) | 0 (page 5 min) |

Markets is a **fixed cost per instance** independent of DAU. Sections refresh every 60 s (sectors 120 s) while US markets are active
(04:00–20:00 ET = 960 min) and every 15 min otherwise. At saturation (requested at least once a minute), that is
≈ 17.5 FMP/min × 960 + ≈ 30 per 15 min × 32 ≈ **17,700 FMP/day**, of which ≈ 13,000 are refused quotes on the current plan.

## 2. Assumptions per daily active user

1.5 sessions; 1.5 Home/Markets opens; 2 Company Details opens; 1.5 searches; 0.2 Discover opens; Watchlist of 8 symbols viewed 1.5×;
Portfolio of 5 holdings viewed 1×; Practice 4 positions viewed 1× and 0.3 trades; news through Details only. Market vs after hours: 40 % of
activity during the regular session, 40 % extended hours, 20 % closed. **Cache miss rates** fall as users overlap on popular symbols:

| DAU | Details | Search | Watch/Portfolio/Practice quotes | Markets minutes covered |
|---:|---:|---:|---:|---:|
| 100 | 50 % | 70 % | 90 % | ≈ 10 % |
| 1,000 | 25 % | 50 % | 60 % | ≈ 55 % |
| 10,000 | 10 % | 30 % | 25 % | 100 % |

"Without shared caching" = each user action fetches its own data (a per-user or client-side cache only).

## 3. FMP requests per day

| Component | 100 DAU | 1,000 DAU | 10,000 DAU |
|---|---:|---:|---:|
| Company Details (2 opens × (m × 16 + (1 − m) × 1.5)) | 1,750 | 10,250 | 59,000 |
| Search | 210 | 1,500 | 9,000 |
| Watchlist quotes | 1,080 | 7,200 | 30,000 |
| Portfolio quotes | 450 | 3,000 | 12,500 |
| Practice quotes | 360 | 2,400 | 10,000 |
| Markets / Home (shared) | 1,800 | 10,000 | 17,700 |
| Discover (if available) | 320 | 2,000 | 6,000 |
| **Total, today's design with shared caches** | **≈ 6,000** | **≈ 36,000** | **≈ 145,000** |
| **Total without shared caching** | ≈ 10,000 | ≈ 100,000 | ≈ 1,000,000 |
| **EOD-first design** (§5) | ≈ 2,500 | ≈ 12,000 | ≈ 45,000 |

Finnhub (shared): company news 10 min + profile2 6 h + earnings histories 6–12 h + market news 5 min → ≈ 500 / 2,500 / 8,000 per day;
peaks must stay under the plan's per-minute rate (60/min published for free). BoC: < 20/day at every size. Gemini: StockSteps+ only, bounded
by per-user quotas (not modeled here; see the Phase 4 cost model).

## 4. Against plan limits

- **250 requests/day (FMP Basic, published):** exceeded 24× at 100 DAU; one Markets rebuild uses ~10 % of the day. The key is already at
  its cap with a single tester.
- **Per-minute paid tiers (published 300 / 750 / 3,000 per minute):** daily volume fits easily (300/min = 432,000/day theoretical).
  Peak-minute check at 10k DAU: if 10 % of daily traffic falls in the opening hour, ≈ 240 FMP/min (today's design) vs ≈ 75/min
  (EOD-first) against 300/min with `PROVIDER_SAFETY_MARGIN` 0.8 → 240. Today's design is marginal on Starter at 10k; EOD-first fits.
- **Multiple instances:** every per-process cache multiplies cold loads and the Markets fixed cost by the number of warm instances
  (`CLOUD_RUN_MAX_INSTANCES`). Keep `min-instances` low and concurrency high before considering a shared cache (licence permitting).
- Subscription fee = plan price + display licence (**UNVERIFIED**). With per-minute plans the marginal request is free up to the rate, so
  shared caching saves money only by keeping a smaller tier viable and by protecting the rate at peaks.

## 5. EOD-first design (what changes the numbers)

- Markets sectors and index proxies from EOD closes (1 EOD per fund per day instead of 11 quotes per 2 min): Markets ≈ 3 movers per
  refresh + ≈ 20 EOD/day → ≈ 3,000/day at saturation instead of 17,700.
- Watchlist, Portfolio and Practice valued at the last close: quotes → 1 EOD per distinct symbol per day (≈ 200 / 800 / 3,000 symbols).
- Details keeps a delayed quote only if licensed, otherwise the last close; statements unchanged (the largest remaining item).
- Quote outcome cooldowns remove refused calls entirely (≈ 13,000/day at saturation on the current plan).
- Bulk EOD (one request for all symbols) would cut the EOD rows to ≈ 1–3/day if the tier includes it (UNVERIFIED, Q2).

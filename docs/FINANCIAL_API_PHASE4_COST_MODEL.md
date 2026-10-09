# Financial API — Phase 4 cost model (read-only)

Date: 2026-10-09, `main` at `6a70d0e`. **Measured** inputs come from the Phase 3 benchmark (`Phase3AuditBenchmarkTest`, MockEngine, one
instance). Everything else is **MODELED** from stated assumptions or **UNKNOWN**. No prices are invented: provider plan prices/limits, Cloud Run
settings and real traffic are not in the repository.

## 1. Measured baseline (per instance)

| Path | Upstream requests (measured) |
|---|---:|
| Company Details (overview + annual + quarterly + valuation), cold | 17 |
| Comparison of 4 + history + repeat | 64 |
| Cold 1Y history, 4 companies | 8 |
| Screener first hour (3 universe + 25 × 11) | 278 |
| Screener 24 h, 150 companies, 25/h | 4,353 (first day) |
| Closed-market workflow (Saturday) | 16 |
| 50 users × 10 symbols (single flight) | ≈ 15 per symbol |
| TRACE logging volume (local) | ≈ 1.5 KB and 6 log lines per upstream request (client side only) |

## 2. Cloud Run instance multiplication (screener only)

Modeled steady state ≈ 5,250 FMP requests/day per instance that serves screener traffic (150 × 4 re-warms × 6 + 150 × 11 daily statement reloads).

| Instances serving screener traffic | Screener requests/day (MODELED, linear) |
|---|---:|
| 1 | ~5,250 |
| 2 | ~10,500 |
| 5 | ~26,250 |
| 10 | ~52,500 |

When the linear model **overestimates**: warm-up is lazy (only instances that receive screener requests warm); at low traffic most instances never
warm; weekends lengthen TTM lifetimes (Phase 3C) so re-warms cost less; idle instances are reclaimed.
When it **underestimates**: instance churn (every new instance repeats the 278-request first hour and reloads statements); scale-out during bursts
creates short-lived instances that warm and die; a dataset cache smaller than the working set evicts (6,603/day measured with 512 entries).
A distributed warm-up *coordinator alone* would not reduce requests, because each instance still needs the data in its own memory; only a shared
cache (licensing D3) or fewer warm instances reduce them.

## 3. Traffic scenarios (MODELED, assumptions explicit)

Assumptions (per active user per day): 3 Company Details opens (15 cold requests each), 0.5 four-company comparisons (64 cold), 2 Watchlist polls of
20 symbols (1 quote each); cache miss rates fall as users overlap on popular symbols (low/medium/high traffic: details 30/20/10 %, comparisons
50/30/15 %, quotes 50/30/15 %). Interactive FMP/day = DAU × (3 × 15 × m₁ + 0.5 × 64 × m₂ + 40 × m₃). Finnhub ≈ 10 % of FMP (news feeds 10 min,
earnings histories 6 h). Gemini: 5 % of users on StockSteps+, 2 AI calls each, plus capped anonymous AI.

| Scenario | DAU | Instances (assumed) | Interactive FMP/day | Screener FMP/day | Total FMP/day | Finnhub/day | Gemini calls/day (signed-in) |
|---|---:|---:|---:|---:|---:|---:|---:|
| Low | 100 | 1 | ≈ 100 × (13.5 + 16 + 20) ≈ 5,000 | ~5,250 | ≈ 10,000 | ≈ 1,000 | ≈ 10 |
| Medium | 1,000 | 2 | ≈ 1,000 × (9 + 9.6 + 12) ≈ 31,000 | ~10,500 | ≈ 41,000 | ≈ 4,000 | ≈ 100 |
| High | 10,000 | 5 | ≈ 10,000 × (4.5 + 4.8 + 6) ≈ 153,000 | ~26,250 | ≈ 180,000 | ≈ 18,000 | ≈ 1,000 |

These are order-of-magnitude planning numbers, not forecasts. Abuse scenarios dominate every row: 1,000 random symbols on `/details` ≈ 15,000 FMP
requests; one watch-data call with 100 new symbols ≈ 300.

## 4. AI token scenarios (MODELED)

Output caps per call: 300–700 tokens (code). Input is the bounded context (UNKNOWN size; measure with the existing token metering). Worst case per
user per day today (in-memory limits, per instance): Brief 15 + Earnings 33 + Comparison 10 = 58 calls; × instances for the in-memory ones.
Anonymous: article insight ≤ 200/h and movement ≤ 300/h **per instance** → ≤ 12,000 Gemini calls/day per instance if abused continuously.
Gemini price per token: UNKNOWN (not in repo; `AiPricing` env vars exist but are unset).

## 5. Firestore operation estimates (MODELED)

| Operation | Reads | Writes |
|---|---:|---:|
| Durable AI reservation (transaction) | 1 | 1 (+1 on release) |
| AI usage display | 1 (transaction read, no write when unchanged) | 0 |
| Entitlement check per premium request | 1 | 0 |
| Public news response (enriched) | up to `limit` (≤ 20) | 0 (+1 per completed simplification) |
| Global AI counter (if chosen, K shards) | K per check (or cached) | 1 per AI call |

Medium scenario with durable quotas: ~100 AI calls → ~200 transaction ops/day — negligible. Public news reads dominate Firestore usage at scale
(e.g. 10,000 news views × 20 = 200,000 reads/day) — an in-memory read cache would remove most of them.

## 6. Monitoring overhead (MODELED)

- Current TRACE logging: ≈ 1.5 KB per upstream request on the client side alone; 5,250 screener requests/day ≈ 8 MB/day/instance before server-side
  request traces. Switching to INFO removes almost all of it.
- Recommended: one structured usage summary per instance per minute (≈ 1–2 KB) = ≈ 1.5–3 MB/day/instance; logs-based metrics on it. Cloud Logging
  and Monitoring prices/free tiers: confirm on the current Google Cloud pricing pages (not hard-coded here).

## 7. Shared cache break-even (MODELED, blocked by D3)

Duplicates a shared L2 could remove: `(N − 1)/N` of statement-class loads and screener warm-up for N warm instances — e.g. N = 2: ≈ 5,250 screener
requests/day + duplicated interactive cold loads. L2 cost per removed provider request: one L2 read per instance per refresh plus one write per
refresh (Firestore) or a fixed Memorystore instance. Break-even condition:
`avoided provider requests × provider unit cost > L2 operations × L2 unit cost (+ fixed costs)`.
Provider unit cost is UNKNOWN (plan-based pricing may make marginal requests free until a limit, in which case an L2 only matters near the limit).
Recommendation: don't build an L2 until (1) licensing allows it (D3), (2) metrics show ≥ 2 warm instances most of the day, and (3) the plan limit is
within ~2× of measured usage.

## 8. Unknowns

FMP plan (daily/minute limits, bulk endpoints, price), Finnhub plan (calls/minute), Gemini pricing and project quotas, Cloud Run settings and instance
counts, real DAU and anonymous share, Firestore region pricing, Cloud Logging retention and volume, provider caching/redistribution terms.

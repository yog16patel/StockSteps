# Financial API cost analysis (Phase 1, read-only)

Companion to `FINANCIAL_API_ARCHITECTURE_AUDIT.md`. All counts are **static, code-derived estimates** for the REAL configuration
(`QUOTE_PROVIDER=fmp`, Gemini key set) on **one** server instance, unless stated otherwise. Nothing here was measured live; no provider
prices or plan limits are known from the repository, so costs are expressed as formulas.

Labels: **[code]** derived from the call graph; **[assume]** an assumption you can change; **[unknown]** needs provider/deployment data.

## 1. Building blocks (per symbol, cold, one instance) [code]

| Block | Upstream calls | Reused by |
|---|---|---|
| `Q` quote | 1 FMP `/quote` (30 s) | everything that shows a price |
| `P` profile | 1 FMP `/profile` (24 h) | details, watch, comparison, earnings names |
| `F_a` fundamentals annual | **11 FMP** (income/balance/cash annual, ratios-ttm, key-metrics-ttm, ratios, income-ttm, cash-ttm, estimates, dividends, shares-float) | details, valuation, screener, comparison, history 3Y/5Y |
| `F_q` extra for quarter | +3 FMP (income/balance/cash quarter ×8) | financials quarter, comparison history 1Y |
| `E` quarterly EPS | +1 FMP (income quarter ×24) | valuation |
| `R` TTM ratio refresh | 2 FMP every 5 min while requested | details/valuation/comparison |
| `D` daily closes | 1 FMP (6 h) | charts, valuation, movement, comparison, portfolio, earnings |
| `I` intraday | 1 FMP (5 min) ×2 caches (chart, sparkline) | 1D chart, Home sparklines |
| `H` Finnhub history | 1 per key; **2 keys** for the same request (`history:SYM`, `history:SYM:w`) (6 h) | earnings, watch next-earnings, comparison latest-quarter growth |
| `N` company news | 1 Finnhub news + 1 Finnhub profile2 (10 min / 6 h) | news, movement, alerts, brief |
| `S` search | 2 FMP per query (no cache) | search |

## 2. Scenario request counts

### A — New user: open app, Markets, search "AAPL", open Company Details
| | Cold | Warm (same instance, within TTLs) |
|---|---|---|
| Markets overview | ≈ 25–35 (3 mover lists, most-active enrichment quotes, 11 sector-ETF quotes, index quotes + histories, 1 Finnhub news) [code; exact count depends on mover payload volumes and index availability] | 0 within 30 s live (5 min closed) |
| Search (typing "AAPL", 300 ms debounce) | 2 × (number of debounced queries, typically 1–4) [assume] | same — **no cache** |
| Company Details | ≈ 16 FMP (P, Q, market hours, `F_a`, `E`, `D`) | 0–4 (quote, market hours, two TTM ratio refreshes) |
| Why-moving preview on Details | SPY Q + SPY `D` + `N` (2 Finnhub) + 1 Gemini narration | 0 within 5 min (then a new narration if prices moved) |
| **Total** | **≈ 45–60 provider calls + 1 Gemini** | ≈ 2–8 + search |

### B — Beginner research: Details → Financials → Valuation → Guided Research → Comparison (AAPL vs MSFT)
Cold ≈ Details 16 + Financials 3 + Valuation 0 (reused) + Guided Research 0–4 + Comparison: AAPL 1 Finnhub `H:w` + MSFT (Q + P + `F_a` +
`H:w` + `D`) ≈ 15 + AAPL `D` reused ≈ **≈ 35–40 FMP + 2–3 Finnhub** [code]. Warm: ≈ 2–6. **Duplicate path**: Earnings Details after
this would add the second Finnhub `history:SYM` key for each company (+1 per symbol).

### C — Portfolio user: 10 holdings, 20 watchlist symbols (assume 5 overlap → 25 distinct), opens Portfolio, Watchlist, Markets
| | Cold | Warm |
|---|---|---|
| Watchlist (`watch-data`) | 20 × (Q + P + `H`) = 60 | 20 Q after 60 s |
| Portfolio | 5 new Q + 5 new P + BoC 1 + 10 `D` (history ranges) | Q only |
| Markets | ≈ 25–35 | 0 / sections 60 s |
| **Total** | **≈ 105–120** | **≈ 25 per refresh after 60 s** (quotes dominate) |
FMP `/quote` accepts one symbol per call in this code; whether a batch quote endpoint is available on the account is **[unknown]**.

### D — Comparison: 4 companies, switch metrics, 1Y→5Y history, save notes, 5 AI questions
| Step | Cold | Warm |
|---|---|---|
| Compare 4 companies | 4 × (Q + P + `F_a` + `H:w`) ≈ 52 FMP + 4 Finnhub; + BoC if CAD | 0–8 (quotes, TTM ratios) |
| Performance 1Y/3Y | 4 `D` (shared across periods) | 0 |
| Switch metrics / Explain (Phase 2) | 0 (client-side) | 0 |
| History 1Y | 4 × `F_q` = 12 FMP (whole quarterly bundle, although only income is used) | 0 |
| History 3Y/5Y | 0 (annual bundle reused) | 0 |
| Research notes (Phase 4) | 0 provider calls | 0 |
| 5 AI questions (Phase 5) | 0 FMP (10-min comparison cache + history cache); **5 Gemini** (+ ≤ 1 regeneration each on invalid output); 2–3 Firestore transactions each | same |
| **Total** | **≈ 68 FMP + 4 Finnhub + 5–10 Gemini** | ≈ 0–8 + Gemini |

### E — Earnings: open calendar, view an event, open results, ask one AI question
Calendar: 1 Finnhub window + **1 FMP profile per listed symbol** for names/logos (bounded concurrency; ≈ page size, e.g. 20–50) [code;
page size depends on the query]. Event/results: `H` ×2 keys (2 Finnhub) + `D` 1. AI: 1 Gemini (explanations shared across Plus users
per report version). **Cold ≈ 25–55 calls + 1 Gemini**; warm ≈ 0–1.

### F — Background
| Job | Trigger [unknown cadence] | Upstream per run |
|---|---|---|
| Alerts (`POST /internal/alerts/evaluate`) | Cloud Scheduler (docs: ~15 min in market hours; **the code doesn't check market hours**) | 1 Q per distinct alerted symbol (watch cache 60 s < run interval → always cold) + news feeds for news rules |
| Earnings reminders (`/internal/earnings-reminders/dispatch`) | Cloud Scheduler | 1 Finnhub `H` per distinct watched/reminded symbol per 6 h |
| Daily Brief dispatch | Cloud Scheduler | overview (shared) + per-user watch overlay (Q/P/`N`/`H` per symbol, cached) |
| Screener warm-up | any screener request in REAL | up to 25 symbols/hour × (≈ 13) ≈ **≈ 325 FMP/hour per instance** until the universe (≤ 100 per exchange) is warm; re-warms as 6 h records expire |
| News simplification | news reads | ≤ 5 Gemini per news response (Firestore-deduped across instances) |
| Legacy snapshot | only if called | 3 FMP quotes (QQQ/DIA reportedly 402 → +2 Finnhub) + 3 movers + market hours per 45 s |

## 3. Quota and metering status

| Question | Answer |
|---|---|
| Which calls are metered? | Every FMP and Finnhub request is presumably plan-metered; FMP plans count calls per minute/day and gate endpoints by tier **[unknown: plan, limits and per-endpoint cost]**. BoC Valet is free/public. Gemini bills tokens. |
| Different credit costs per endpoint? | **[unknown]**. |
| Batching? | Not used. FMP batch quote / batch statements availability on the account **[unknown]**; Finnhub quote is single-symbol. |
| Avoidable paid requests | yes — see roadmap (market hours computed locally, duplicate history keys, separate intraday caches, quarterly bundle for income-only history, 5-min TTM ratio refresh when closed, uncached search/movers, warm-up volume). |
| Fallback extra cost | index fallback to Finnhub (legacy snapshot); `QUOTE_PROVIDER=finnhub` removes the repo quote cache layer. |
| Usage limits enforced? | per-instance rate limiters on screener/compare/earnings/AI/user routes; **none on public stock/news/markets routes**; `ProviderRequestBudget` only for comparison history/research; Comparison AI quota durable; others in memory. |
| Attribution to features? | only FMP fundamentals datasets, comparison history statements and comparison AI (`ProviderUsageMeter`, coroutine `ProviderFeature`). |
| Attempts vs successes vs retries vs cache hits? | separated for FMP datasets only (`upstream`, `cacheHit`, `cacheMiss`, `error`, `rateLimited`); not elsewhere. |

## 4. Cost model (variables only)

```
Monthly provider cost  = Σ_provider  BillingModel_p( U_p )
U_p (upstream calls)   = Σ_features  Σ_symbols  ColdLoads(feature, symbol) × Instances_eff × (1 − h_feature)
Gemini cost            = Σ_ai_features  Requests_f × (1 − c_f) × (tokens_in × price_in + tokens_out × price_out)
```
- `BillingModel_p`: flat subscription with per-minute/day caps (typical for FMP/Finnhub) **or** per-call credits — **[unknown]**. With a
  flat plan the "cost" of waste is **headroom against rate limits** (429s, degraded screens) and the plan tier you must buy.
- `Instances_eff`: effective number of independent warm caches serving traffic (1 ≤ Instances_eff ≤ Cloud Run instance count) **[unknown]**.
- `h_feature`: cache hit rate after first load; `c_f`: AI cache/decline rate.

### Symbol popularity [assume]
Daily active users `U`; each views `k` symbols/day (assume `k = 6`); popularity is Zipf-like: the top 50 symbols take ~70 % of views,
the long tail the rest. Distinct symbols/day ≈ `50 + 0.3 × U × k × t` where `t` = long-tail novelty share (assume 0.35).

| DAU (`U`) | Symbol views/day | Distinct symbols/day (≈) | Cold symbol loads/day with 1 instance | with 4 independent instances |
|---|---|---|---|---|
| 100 | 600 | ≈ 113 | ≈ 113 × (1 + daily refreshes) | × up to 4 |
| 1,000 | 6,000 | ≈ 680 | ≈ 680 × … | × up to 4 |
| 10,000 | 60,000 | ≈ 6,350 | ≈ 6,350 × … | × up to 4 |

Per cold symbol (Details path) ≈ 16 FMP; statements re-load daily (24 h TTL), TTM ratios every 5 min **while being viewed**, quotes every
30–60 s while viewed. Example (illustrative only, [assume] each distinct symbol is first opened once and re-viewed 3× across the day,
2 instances):
- FMP statement-class calls/day ≈ distinct × 13 × 2 → 100 DAU ≈ 2.9 k; 1,000 DAU ≈ 17.7 k; 10,000 DAU ≈ 165 k.
- Quote/TTM refresh calls scale with **concurrent viewing**, not DAU: ≈ views × (1 − h) × 3; with h = 0.6 → 100 DAU ≈ 0.7 k;
  1,000 DAU ≈ 7.2 k; 10,000 DAU ≈ 72 k.
- Screener warm-up adds up to ≈ 325 FMP/hour/instance during warm-up windows regardless of DAU.
- Markets overview is DAU-independent once warm: ≈ 30 calls per 60 s per instance during market hours ≈ 11.7 k/day/instance at 6.5 h,
  **only if requested at least once per minute**; less at low traffic.
These numbers are not forecasts; they show which terms dominate. Request volume is **sub-linear** in DAU because popular symbols are
shared, and **linear in instances** because caches are not shared.

### Sensitivity (FMP calls per day, 1,000 DAU, 2 instances, from the formulas above) [assume]
| Cache hit rate on quote/TTM paths | 0.4 | 0.6 | 0.8 | 0.9 |
|---|---|---|---|---|
| Quote/TTM calls | ≈ 10.8 k | ≈ 7.2 k | ≈ 3.6 k | ≈ 1.8 k |
| + statements (fixed by distinct symbols) | 17.7 k | 17.7 k | 17.7 k | 17.7 k |
| Shared cache across instances (Instances_eff 2 → 1) | statements → 8.9 k | | | |

Takeaways: (1) statement-class loads per distinct symbol × instances dominate — a cross-instance shared statement cache roughly divides
them by the instance count; (2) the 11-dataset bundle per symbol is the unit cost to reduce (lazy datasets per screen); (3) quote/TTM
refresh is driven by TTL choice and concurrency, and is cheap to cut when markets are closed.

### Gemini [assume token sizes; prices unknown]
| Feature | Calls driver | Cache effect |
|---|---|---|
| Article insight (public) | articles opened; ≤ 200 starts/h/instance | 7-day per-article cache shared by everyone |
| Why-moved narration (public) | symbols × periods × fact changes (≤ 1 per 5 min per symbol for 1D while viewed) | 24 h per facts digest |
| News simplification | ≤ 5 per news response, deduped via Firestore | durable |
| Brief / Earnings / Comparison AI (Plus) | user questions within daily quotas | earnings/comparison summaries shared per data version |
Comparison AI logs real token counts (`ai.comparison.inputTokens/outputTokens`) and estimates cost only when prices are configured; other AI
features record no tokens.

## 5. Known unknowns
FMP and Finnhub plan tiers, per-minute/day limits, per-endpoint credit costs, batch endpoints on the account, data licensing for shared
caching/redistribution, Cloud Run instance counts/concurrency/min-instances, Cloud Scheduler cadences, real DAU and symbol mix, real cache
hit rates (no production metrics), Gemini model pricing and average token sizes per feature, Firestore read/write pricing tier.

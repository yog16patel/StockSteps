# Cost simulation at 100 / 1,000 / 10,000 DAU — Phase 9B

**MODELED, order of magnitude, one server instance, per day.** No vendor price is claimed (all subscription, licence and cloud prices are
**UNVERIFIED** or need a commercial quote). Request counts come from code paths and MockEngine benchmarks: Company Details cold ≈ 16 FMP
(`Phase3AuditBenchmarkTest`), Markets saturated trading day 3,043 FMP (7B `MarketsEodBenchmarkTest`), Portfolio/Practice closes 1 per
symbol per session (7C), Discover ≤ 100/day (9B), SEC 1 companyfacts per company per day (9B), mobile → server flows from 9A
(`RATE_LIMIT_REVIEW.md` §2).

## 1. Assumptions per DAU (base; low = 0.5×, high = 2× user-driven activity)
1.5 sessions; 1.5 Home/Markets opens; 2 Company Details opens; 1.5 searches (≈ 4 search requests each with 300 ms debounce, 2 FMP calls per
uncached query); 0.2 Discover opens; Watchlist 8 symbols × 1.5 views; Portfolio 5 holdings × 1; Practice 4 positions × 1 and 0.3 trades;
StockSteps+ share 2 % / 5 % / 5 % with 2 AI calls a day. Cache miss rates fall with overlap (Phase 6): Details 50 / 25 / 10 %, search
70 / 50 / 30 %, quotes 90 / 60 / 25 %; Markets minutes covered 10 / 55 / 100 %. Distinct companies viewed per day **D = 150 / 600 / 2,000**.

## 2. Formulas
- Markets (fixed per instance) = 3,043 × coverage (trading day; weekend 159).
- Details FMP = 2 × DAU × (m × 16 + (1 − m) × w), w = warm requests per open (1.5 with quotes; ≈ 0.2 EOD-only).
- Statements alone scale with **D, not DAU**: FMP ≈ 11 × D per day; SEC = D + 1 (ticker file) (+ ≤ 4 × D submissions if used for freshness).
- Search FMP = DAU × 1.5 × 2 × miss.
- Watchlist quotes (today) = Phase 6 figures; EOD-based = distinct symbols S ≈ 200 / 800 / 3,000.
- Portfolio + Practice = closes for S distinct symbols (shared with charts).
- Discover = min(cap, need) = 100.
- Gemini calls = DAU × paid share × 2; cost = calls × (input tokens × in-price + output tokens × out-price) with ≈ 3,000 in / 400 out
  tokens (assumption; prices UNVERIFIED).

## 3. Licensed price/news traffic (FMP + Finnhub), requests per day

| Component | 100 | 1,000 | 10,000 |
|---|---:|---:|---:|
| Markets / Home (shared, per instance) | 300 | 1,700 | 3,043 |
| Company Details (today: m × 16 + (1 − m) × 1.5) | 1,750 | 10,250 | 59,000 |
| Search | 210 | 1,500 | 9,000 |
| Watchlist quotes (today) | 1,080 | 7,200 | 30,000 |
| Portfolio + Practice closes | 200 | 800 | 3,000 |
| Discover enrichment (9B cap) | 100 | 100 | 100 |
| **FMP total, current design (base)** | **≈ 3,600** | **≈ 21,500** | **≈ 104,000** |
| low / high (0.5× / 2× user-driven) | 1,900 / 7,000 | 11,000 / 41,000 | 53,000 / 205,000 |
| **FMP, proposed** (SEC statements, EOD Watchlist and Details price) | **≈ 1,800** | **≈ 10,200** | **≈ 34,000** |
| Finnhub (news, earnings; shared) | ≈ 500 | ≈ 2,500 | ≈ 8,000 |

Phase 6 for comparison (before 7B/7C/7E/9B): ≈ 6,000 / 36,000 / 145,000; without shared caching ≈ 10,000 / 100,000 / 1,000,000.
Against plans: a 250/day key is exceeded at every size; per-minute plans fit the volume (peak hour at 10k DAU ≈ 10 % of the day ≈ 170 FMP/min
today, ≈ 60/min proposed). **The cost driver is the licence, not request volume.**

## 4. Free official traffic

| Source | 100 | 1,000 | 10,000 | Limit (published) |
|---|---:|---:|---:|---|
| SEC EDGAR (if D-9B-2) | ≈ 150 | ≈ 600 | ≈ 2,000 | 10 req/s; lane 5/s → ≤ 432,000/day |
| Bank of Canada FX | < 20 | < 20 | < 20 | don't circumvent limits |
| Treasury/BLS/BEA/FRED | 0 (no feature) | 0 | 0 | BLS 500/day; BEA 100/min |

## 5. GCP hosting, cache and storage (quantities only; prices from the current Google price list, see `docs/CLOUD_RUN_COST_AND_SCALING.md`)

| Item | 100 | 1,000 | 10,000 | Formula |
|---|---:|---:|---:|---|
| Cloud Run requests/day | ≈ 4,500 | ≈ 45,000 | ≈ 450,000 | DAU × ≈ 45 API calls (9A measured flows) |
| CPU time/day (≈ 30 ms avg, assumption) | ≈ 2 min | ≈ 23 min | ≈ 3.8 h | requests × 30 ms |
| Firestore reads / writes per day (60 % signed in, ≈ 20 reads / 2 writes each) | 1,200 / 120 | 12,000 / 1,200 | 120,000 / 12,000 | |
| In-memory caches | per instance, bounded LRU (≤ 4,096 FMP entries; SEC 512) | | | no paid cache; Redis/Memorystore not approved |
| Logs | 1 usage summary/min/instance + warnings | | | no per-request provider URLs |

## 6. Gemini (StockSteps+ only)

| | 100 | 1,000 | 10,000 |
|---|---:|---:|---:|
| AI calls/day (paid share × 2) | ≈ 4 | ≈ 100 | ≈ 1,000 |
| Tokens/day (3,000 in + 400 out per call) | ≈ 14 k | ≈ 340 k | ≈ 3.4 M |
| Cost | tokens × current Gemini price — **UNVERIFIED** | | |

Durable per-user quotas (Phase 4B) bound the high case regardless of DAU.

## 7. Costs that need a commercial quote (cannot be modeled)
FMP Data Display and Licensing Agreement (Q1–Q4); Finnhub commercial plan (Q5); an alternative EOD + fundamentals vendor (Q8, Q10); index
level licences (Q9); delayed-quote exchange fees if delayed quotes are kept (Q7); logo display (FMP image host).

## 8. What grows with distinct companies (D)
Statements and profiles scale with D (≈ 12 FMP or 1 SEC request per company per day), closes with distinct symbols S, Markets is fixed,
Discover is capped. Multiple instances multiply every cold load and the Markets fixed cost by the number of warm instances (per-instance
caches; shared caching needs a licence that allows it and an approved shared store).

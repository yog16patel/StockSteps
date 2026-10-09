# Financial API — Phase 4 owner decisions

Date: 2026-10-09. Updates the Phase 3 register (`FINANCIAL_API_PHASE3_DECISIONS.md`, unchanged). Nothing is decided here; recommendations are
defaults for the owner to accept or change.

## Mapping from the Phase 3 register

| Phase 3 | Status | Phase 4 |
|---|---|---|
| D1 Public API client identity | open | D1 (unchanged scope) |
| D2 Rate-limiting policy | open | split into D1 (identity) and D2 (anonymous access) |
| D3 Persistent/shared cache licensing | open | D3 (unchanged) |
| D4 Cross-instance coordination | open | D4 (Cloud Run deployment) |
| D5 Acceptable data staleness | **implemented with the recommended defaults** in Phase 3C/3E (owner may still revise) | — |
| D6 Screener universe size | **decided: 150 (50 per exchange)**, implemented | — |
| D7 Infrastructure and provider spending | open | D5 (subscriptions) + D7 (spending targets) |
| D8 AI fair use | open | D6 (AI allowance policy) |
| — | new | D8 (failure policy) |

## Decisions

| # | Decision | Current known state | Options | Recommendation | Trade-offs / cost | Blocks |
|---|---|---|---|---|---|---|
| D1 | **Client identity** | uid verified for signed-in routes; anonymous limits keyed on `remoteHost` (proxy) | trusted XFF hop count; App Check; both; sign-in required | **Trusted XFF (verified hop count) now + App Check (monitor → enforce)** | App Check needs SDK work and app releases on Android and iOS; XFF needs the deployed topology | 4A identity part |
| D2 | **Anonymous access** | all market/news/details/screener/watch-data/insight/movement routes are anonymous | keep all anonymous; require sign-in for AI routes (insight, movement narration); require sign-in for watch-data beyond N symbols | **Keep market data anonymous with limits; anonymous AI only through cached/budgeted generation; watch-data ≤ 30 symbols anonymous** | sign-in walls reduce guest UX; anonymous AI costs scale with abuse | 4A limits values |
| D3 | **Provider licensing** | no contract text in repo | process-local only; shared/persistent caching; derived snapshots | **Ask FMP and Finnhub in writing; process-local until confirmed** | without it no L2/snapshot | 4E |
| D4 | **Cloud Run deployment** | not in repo | min 0/1; max 2/3/5/10; concurrency 40–80 | **min 1, max 3, concurrency 80 initially** (bounds per-instance budgets ×3; one warm instance avoids repeated cold warm-ups) | min 1 = always-on cost; low max risks latency under bursts (watch p95) | 4C sizing |
| D5 | **Provider subscriptions** | plans/limits unknown | — | **Provide FMP and Finnhub plan limits (per minute/day), bulk endpoint availability, Gemini project quotas** | — | 4C values, alerts |
| D6 | **AI allowance policy** | Comparison 10/day + 50/30 d durable; others in memory (Brief 15, Earnings 10/20/3) | per-feature only; one combined StockSteps+ allowance; both | **Per-feature limits + a combined StockSteps+ daily cap (e.g. 40) in the same `aiUsage` doc; anonymous AI hourly budgets** | combined cap simplifies cost control but needs clear UI copy | 4B values |
| D7 | **Spending targets** | none recorded | monthly target per provider and infrastructure | **Owner sets monthly targets; billing alerts at 50/80/100 %** | alerts are not caps | 4D thresholds |
| D8 | **Failure policy** | Phase 3: stale labelled data, coverage disclosure, AI fallbacks | fail open vs closed per feature | **As `FINANCIAL_API_PHASE4_QUOTA_ARCHITECTURE.md` §6** (premium AI fail closed; data stale-labelled; Practice never fills without a fresh price) | — | 4B/4C behaviour |

## Not blocking

- Immediate hotfix (log level, FMP key rotation) needs no decision.
- Durable AI quotas (4B) can proceed with today's numeric limits while D6 is decided.
- Observability (4D) can proceed; thresholds wait for D5/D7.

## Unresolved questions for the owner

1. Is the backend currently deployed? With which `logback.xml`? (If yes: rotate the FMP key now.)
2. Ingress: direct `run.app` URL or an external HTTPS load balancer/custom domain?
3. Expected launch DAU and guest share?
4. Is StockSteps+ billing live (who writes `users/{uid}/meta/entitlements` in REAL)?
5. Gemini: AI Studio key or Vertex AI? Can a project-level quota be set?

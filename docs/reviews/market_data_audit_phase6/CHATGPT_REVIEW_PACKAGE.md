# ChatGPT review package — Phase 6 REAL market-data provider audit (audit only)

## 1. Phase and Git baseline
- Phase: Phase 6, REAL market-data provider audit and cost optimization. **Read-only audit and recommendation.**
- Base commit: `be3c15e` "Fix Discover Stocks on the LAN REAL backend with distinct plan-restricted, rate-limited and temporary screener
  errors, …" (committed and pushed; `main == origin/main`, clean tree at start).
- Under review: "Add the Phase 6 REAL market-data provider audit with the provider compatibility matrix, cost model, cache inventory and an EOD-first MVP architecture recommendation" (documentation only, committed locally, not pushed): (this folder, `docs/REAL_MARKET_DATA_PROVIDER_AUDIT_PHASE6.md`, handoff and status entries).
- The LAN REAL container runs image `ff5662e…-dirty`: the Discover fix in `be3c15e` is **not deployed** there.

## 2. Changes implemented
None to code, configuration, subscriptions, deployments or Ubuntu services. Documents only.

## 3. Affected files (docs)
`docs/REAL_MARKET_DATA_PROVIDER_AUDIT_PHASE6.md`; `docs/reviews/market_data_audit_phase6/{CHATGPT_REVIEW_PACKAGE, PROVIDER_COMPATIBILITY_MATRIX,
API_USAGE_AND_COST_MODEL, CACHE_INVENTORY, RECOMMENDED_MVP_ARCHITECTURE}.md`; `docs/PROJECT_HANDOFF.md`; `docs/project-status.md`.

## 4. Key findings (decisions behind the recommendations)
1. **Quotes are refused by symbol, not at random.** 139 FMP quote attempts → 24 ok, 111 × 402, 4 × 429. The refusals repeat per Markets
   build: 26 quotes (21 refused), and 11 refused when the sector section refreshes alone. Last week's fixture capture recorded no
   quotes for the 11 sector ETFs or the index levels, but did record their daily charts. Profiles: 21/21 ok.
2. **The key is at a daily cap.** All 8 probes this session returned 429 "Limit Reach". The LAN config sets `PROVIDER_FMP_PER_MINUTE=250`,
   which matches FMP's published *daily* Basic limit, with no daily target. Tier unconfirmed (Q1).
3. **Refused calls are wasted and mislabelled.**
   - Quote failures have no cooldown (`quote:SYM` caches only successes).
   - A 402 reaches the user as 502 "temporarily unavailable" (or Practice `QUOTE_MISSING`).
   - Practice trades are blocked for every refused symbol, though EOD closes for them work.
4. **Licence is the launch blocker.** FMP's published terms: individual plans don't permit display to third parties without a Data Display
   and Licensing Agreement. Finnhub's free tier is described as personal/non-commercial. Prices UNVERIFIED.
5. **Recommended MVP:**
   - EOD-first prices with dates;
   - FMP for statements, profiles and EOD; Finnhub for news and earnings if licensable;
   - curated, labelled Discover list (~150);
   - Practice at the last official close;
   - quote outcome cooldowns and a daily-cap state; verified guard values;
   - no new infrastructure.
6. **Cost model (FMP/day, one instance):** 100 / 1k / 10k DAU ≈ 6k / 36k / 145k today with shared caches; ≈ 10k / 100k / 1M without;
   ≈ 2.5k / 12k / 45k EOD-first. Markets is a fixed ≈ 17.7k/day per instance at saturation, ~75 % of it refused quotes on this plan.

## 5. Test and build results
NOT RUN: no code changed. Evidence used:
- read-only `docker logs` of `stocksteps-real-api-1`: 21 usage summaries and 130 sanitized failure lines; 0 lines containing a key;
- the fixture manifest;
- 8 in-container probes, all 429; key redacted; no further probes after the 429s.

## 6. Android verification
NOT RUN (no UI change).

## 7. iOS verification
NOT RUN (no UI change).

## 8. Known issues
- The REAL key is at its daily cap: REAL screens degrade until it resets.
- The quote symbol list is inferred: symbols are never logged.
- The 8 probes bypassed the server counters and may have counted against the cap.
- REAL corporate actions are not wired for Practice.

## 9. Unverified scenarios
- Same-symbol quote vs profile difference: BLOCKED (daily cap).
- FMP tier, reset time, whether 402/429 count against the limit: BLOCKED (owner/FMP, Q1–Q3).
- All display/caching/redistribution rights: BLOCKED (vendor answers, Q4–Q8).
- Weekday-open traffic: NOT RUN.
- 5-min intraday and Finnhub company news on REAL: NOT RUN.

## 10. Deviations from the brief
- Only 8 probes were sent, all refused with 429. The brief allowed a very small number; none was repeated.
- FMP's own pricing page refuses automated fetches, so published figures come from FMP pages quoted in search results and third-party
  listings, all marked UNVERIFIED.

## 11. Decisions requiring owner approval
- Contact FMP (Q1–Q4) and one alternative vendor (Q8).
- Choose the Discover option (recommend B, curated) and the Practice model (recommend last close).
- Approve the backlog items 2–10 (report §12), each as its own phase.
- Redeploy LAN REAL with `be3c15e`.

## 12. Links
Report: `docs/REAL_MARKET_DATA_PROVIDER_AUDIT_PHASE6.md`. Matrix, cost model, caches, architecture: this folder. Earlier audits:
`docs/FINANCIAL_API_*`; Discover: `docs/DISCOVER_REAL_BACKEND_INTEGRATION_FIX.md`.

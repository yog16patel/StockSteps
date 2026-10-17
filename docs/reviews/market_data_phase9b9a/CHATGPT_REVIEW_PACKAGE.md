# ChatGPT review package — Phase 9B.9A: Alpha Vantage free-tier validation

## 1. Baseline
`main` = `origin/main` = `30bf1df` (9B.8; 9B.7 `a3aa026` — both committed and pushed, contrary to the brief's "uncommitted"). Under review:
the commit "Validate the Alpha Vantage free tier for Phase 9B.9A …" (server + docs; pushed). No commit, push, deploy, purchase or premium activation.

## 2. Outcome
- **Authenticated requests: 0.** Free allowance (25/day) and terms re-verified on Alpha Vantage's public pages; terms say use "as or on
  behalf of a corporation" is commercial, so a company-associated workflow is **not clearly permitted** on the free personal licence →
  fixture-only validation (brief step 3). No key exists here either.
- Explicit `ALPHA_VANTAGE_ENTITLEMENT=free|premium` (default free); rate settings never imply premium; FREE lane = free rate + exact
  20/day budget (≤ 25); premium functions refused locally with 0 calls.
- No Discover bulk enrichment or fundamentals warm-up on FREE (forced in code).
- Personal access: loopback only (NAT-safe) + verified uids; LAN addresses rejected at startup.
- Bounded opt-in smoke test `./gradlew :server:alphaVantageSmokeTest` (≤ 10 requests, no retries, stops at the first refusal, no secrets).
- FMP stays the default; REAL/MOCK isolation, 9A limits, licence gate, single flight and caches unchanged.

## 3. Files
server main: `marketdata/alphavantage/{AlphaVantageConfig,AlphaVantageSmokeTest}.kt` (smoke test new), `service/ProviderBudget.kt`,
`appconfig/StartupConfiguration.kt`, `marketdata/MarketDataAccessGate.kt`, `Application.kt`; `server/build.gradle.kts` (task). Tests:
`AlphaVantageFreeTierTest` (new), 6 updated. Docs: this directory, `PROJECT_HANDOFF.md`, `project-status.md`. Apps: none.

## 4–9
Results: `CHATGPT_TEST_RESULTS.md` (server 702/0; core-iOS known flake re-run 13/0). Endpoints: `FREE_TIER_ENDPOINT_RESULTS.md`. Quota:
`QUOTA_USAGE.md`. Limits: `KNOWN_LIMITATIONS.md`. No app change (no contact sheets).

## 10. Deviations
Live smoke test not run (terms + credentials) — the brief's fixture-only path. Loopback-only personal clients is a tightening of 9B.7.

## 11. Owner decisions — `OWNER_DECISIONS.md`
D-9B9A-1 revised by the owner: a free personal key for owner-only local testing (terms caveat recorded: company-associated use is
commercial under ToS §2 (ii)). Draft sales questions for anything beyond local testing: `ALPHA_VANTAGE_SALES_QUESTIONS.md` (not sent).

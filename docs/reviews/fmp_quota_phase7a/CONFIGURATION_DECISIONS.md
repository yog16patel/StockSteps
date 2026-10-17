# Phase 7A — configuration and operations

No environment value, credential, quota or deployment was changed in this phase.

## 1. Relevant variables

| Variable | Meaning | Default | LAN REAL today | Verified? |
|---|---|---|---|---|
| `PROVIDER_FMP_PER_MINUTE` | requests per minute, whole deployment | 600 (development default, `verified=false`) | 250 | **UNVERIFIED**: 250 matches FMP's *published daily* Basic limit; the account's tier isn't confirmed (Q1) |
| `PROVIDER_FMP_BURST` / `_CONCURRENCY` | token-bucket burst / parallel requests | per-minute value / 24 | unset | n/a |
| `PROVIDER_FMP_DAILY_TARGET` | soft target: one `provider.fmp.dailyTargetExceeded` event when passed | unset | unset | n/a |
| **`PROVIDER_FMP_DAILY_LIMIT`** (new) | **hard** daily allowance: attempts beyond this instance's share are refused locally until 00:00 UTC | unset = not enforced | unset | must be the account's **confirmed** daily limit |
| `PROVIDER_FINNHUB_*`, `PROVIDER_GEMINI_*`, `PROVIDER_BOC_*` | same set per provider (the daily limit works for each) | — | Finnhub 60/min, BoC 10/min, Gemini 60/min | Finnhub 60/min published for free; unconfirmed for the account |
| `PROVIDER_SAFETY_MARGIN` | share of every limit this deployment uses (0.1–1.0) | 0.8 | 0.5 (the key is shared with the Mac, D-R3) | owner decision |
| `CLOUD_RUN_MAX_INSTANCES` | divides every limit between instances | 1 | 1 | — |

Local value per instance = `limit × PROVIDER_SAFETY_MARGIN ÷ CLOUD_RUN_MAX_INSTANCES` (at least 1). Example: a confirmed 250/day with
the LAN margin 0.5 and one instance → 125 admitted FMP requests per UTC day on that server.

## 2. Validation added (startup report)

- `PROVIDER_<NAME>_DAILY_LIMIT` must be a positive whole number (startup error otherwise).
- Warning when `DAILY_TARGET > DAILY_LIMIT` (the target alert could never fire first).
- Warning when `DAILY_LIMIT < PER_MINUTE`: "check that the per-minute value isn't the plan's daily figure".
- Warning when `PROVIDER_FMP_DAILY_LIMIT` is unset: no daily FMP allowance is enforced.
- The daily limit is never derived from the per-minute value or a published plan (test: `withoutAConfiguredAllowanceNothingChanges`).

## 3. Reset handling

- The allowance resets at **00:00 UTC**. That is StockSteps' own budget day, the same counter day the existing daily target uses.
- **FMP's reset boundary is UNVERIFIED** (Q1). If FMP resets later than 00:00 UTC, the first requests after midnight may still get 429.
  They are handled as ordinary rate limits (breaker + 10-minute outcome cooldown), so nothing is blocked indefinitely.
- A local daily denial is cached only 30 s per symbol, so data recovers within about 30 s of the reset.
- A provider 429 never switches on the daily state: the existing breaker handles it, bounded at 5 minutes and retried half-open.

## 4. Metrics and monitoring

Usage summaries (`stocksteps.usage`, every 60 s) carry, without keys, symbols or users:
- `fmp quote budget budgetDenied` and the `provider.fmp.budget.denied.dailyLimit` events;
- `provider.fmp.dailyLimitReached`, once per day;
- `provider.fmp.dailyTargetExceeded`;
- `fmp quote … denied` (402/403 upstream) and `rateLimited`;
- `cache.fmp.hit|miss|join` (a cooled-down refusal counts as a hit).

`ProviderGuard.dailyUsage(provider)` gives the admitted count and the local allowance for diagnostics.

## 5. Recovery after a subscription change

- **Restricted outcomes:** they expire within 1 hour. No restart is needed for quotes to recover after a plan upgrade. To see the
  change immediately, restart the server (all caches are in memory).
- **A new daily limit:** set `PROVIDER_FMP_DAILY_LIMIT` (and correct `PROVIDER_FMP_PER_MINUTE`) and restart. Environment values are read
  at startup.

## 6. Deploying this phase (needs approval; not done)

1. Rebuild the image from the approved commit.
2. Run `STOCKSTEPS_STACK=real deploy/local/deploy.sh` on the Ubuntu host. This recreates `stocksteps-real-api-1`; MOCK is untouched.
3. This also deploys `be3c15e` (the Discover cooldown), which isn't live yet.

## 7. Owner decisions

1. Confirm the FMP tier, its daily limit and its reset time (Q1).
2. If Basic:
   - set `PROVIDER_FMP_DAILY_LIMIT=250`;
   - replace `PROVIDER_FMP_PER_MINUTE=250` with the plan's real per-minute value (or a conservative one);
   - keep the margin at 0.5 while the Mac shares the key.
3. Approve the copy of `PROVIDER_RESTRICTED`, `PROVIDER_DAILY_LIMIT_REACHED` and `QUOTE_RESTRICTED`.
4. Approve the LAN REAL redeploy.

# Known limitations and remaining risks — Phase 9A

| # | Limitation | Impact | Mitigation / next step | Owner decision? |
|---|---|---|---|---|
| L1 | Rate limits are **per instance** (process memory) | N instances → N × limits | small `CLOUD_RUN_MAX_INSTANCES`; Cloud Armor / API Gateway policy; shared counter store later (`RATE_LIMIT_REVIEW.md` §6) | yes (infra/cost) |
| L2 | Without `TRUSTED_PROXY_HOPS`, guests share one large pool per class | no per-guest limits on the LAN stack | set and verify the hop count on the deployed topology (Phase 4 doc §5) | config |
| L3 | Guests behind one NAT / carrier NAT share an address budget (90 market-data requests/min) | heavy shared networks could see 429s | signed-in users are keyed by uid; limits are env-configurable | monitor |
| L4 | ID-token revocation not checked (stateless JWT) | a disabled/deleted account works ≤ 1 h | Admin SDK `checkRevoked` for sensitive operations (adds an Auth call per request) | yes |
| L5 | Free account creation gives fresh per-account budgets | scripted sign-ups | Firebase sign-up quotas; App Check; provider budgets are global | App Check plan |
| L6 | App Check not integrated in the apps; server in monitor mode | scripts can call public routes | `APP_CHECK_ASSESSMENT.md` §2 | yes (console + SDK) |
| L7 | Push token re-registration under another account unlinks the earlier one | needs the victim device's FCM token | intended for account switching; could require the same install id | low |
| L8 | `/internal/*` share one ADMIN_INTERNAL pool (120/min) when callers are unverified | a flood of bad requests could delay a scheduler call by ≤ 60 s | restrict `/internal` with Cloud Run ingress/IAM or a separate service | yes (deploy) |
| L9 | Chunked-body 411 check relies on HTTP/1.1 framing; h2c is not enabled | if h2c is ever enabled, add a streaming body cap | keep h2c off or add a read cap | — |
| L10 | No HTTP-level response dedup; dedup is in the public-data caches | identical concurrent requests still run handler code (cheap; upstream shared) | by design | — |
| L11 | iOS simulator not run against the 9A server; signed-in iOS, TalkBack/VoiceOver, physical devices not run | shared KMP client logic tested; Android verified on emulator | owner pass | — |
| L12 | Provider plan limits and instance count are still development defaults | budgets aren't production-accurate | set `PROVIDER_*` and `CLOUD_RUN_MAX_INSTANCES` (Phase 4C/7A item) | yes |
| L13 | The iOS `GoogleService-Info.plist` (Firebase client config, public API key) is tracked in Git, unlike Android's `google-services.json` | not a server secret; key should be restricted to the iOS bundle in Google Cloud | restrict the key; decide whether to untrack | yes |
| L14 | Phase 8E stays uncommitted in the main checkout; its notes in `docs/PROJECT_HANDOFF.md` / `docs/project-status.md` were left out of the 9A commit | 8E must be committed on its own | 8E review | yes |

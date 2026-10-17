# Guest mode review — Phase 9A

## 1. Current guest model (verified, unchanged)
- A guest is an **unauthenticated** client: no `Authorization` header. Neither app calls Firebase anonymous sign-in
  (`signInAnonymously` / `isAnonymous` appear nowhere in `core`, `app/shared` or `app/iosApp`). No device id is sent or trusted.
- Guest state on the device only: recent searches (`RecentSearchStore`), Learn progress before sign-in, backend choice. Nothing is merged
  into an account on sign-in.
- 9A does not replace this model (no owner approval needed). An anonymous Firebase token, if someone minted one with the project's public
  API key, gets **no** signed-in feature (403 `ACCOUNT_REQUIRED`) and **no** per-account rate budget (treated like a guest).

## 2. Guest features kept public (41 PUBLIC routes, `ENDPOINT_ACCESS_MATRIX.md`)
Company Search, profiles, quotes, Company Details (details, chart, valuation, fundamentals, why-moving, news, sparkline), article AI
explanations and the movement explainer (cached; EXPENSIVE class), watch-data for local watchlists (≤ 30 symbols per guest request), market
movers, Markets overview, snapshot, Daily Brief public edition, earnings calendar / events / results / price reactions, Discover catalog and
search, comparison (1Y), `/api/v1/meta`. Learn content is bundled in the apps (no server route); guided-research **progress sync** and the
research AI are signed-in.

Verified: `RouteAccessTest.publicRoutesServeGuestsWithoutSignIn` sends every PUBLIC GET to the real MOCK server without a token — none
answers 401/403/429. On emulator-5554 the app's guest-reachable screens loaded normally through the new stack (search, Company Details,
Markets, Earnings calendar; 0 rate-limit responses, `RATE_LIMIT_REVIEW.md` §2).

## 3. Guest limits
| Identity available | Bucket | Example (PUBLIC_MARKET_DATA) |
|---|---|---|
| `TRUSTED_PROXY_HOPS` set (Cloud Run / LB) | per client address (IPv6 per /64) | 90 requests/min |
| not set (local LAN, tests) | one shared pool per class and instance | 9,000 requests/min |

The shared pool is deliberately large: one small shared bucket would let a single flood lock out every guest. Production must set and verify
`TRUSTED_PROXY_HOPS` (startup already warns when it's missing). Guests behind one NAT share an address budget; signed-in users behind the
same NAT don't (tested: `usersSharingOneAddressAreLimitedPerAccount…`).

## 4. Expired tokens on public routes
A presented-but-invalid token on a public route is ignored (the caller is a guest): an expired session never breaks market data.
Signed-in routes still refuse it (401).

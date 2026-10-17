# Known limitations — Phase 9B.7

1. **No Alpha Vantage key here:** account entitlement, live field names (other than the two daily series) and quota behaviour are
   UNVERIFIED; the bounded REAL smoke test is BLOCKED until the owner sets `ALPHA_VANTAGE_API_KEY` server-side.
2. **Licence:** the personal plan allows only the key holder to see the data; internal testers, a beta and the public need a written
   commercial agreement (Q13). Personal mode is enforced by allowlists; commercial mode fails closed.
3. **Personal-mode identity:** public app requests carry no token, so guests are matched by client address (socket peer or trusted proxy
   address). A shared NAT/LAN address admits every device behind it — the owner must keep the dev server private.
4. **Remaining FMP/Finnhub dependencies:** fundamentals bundle (ratios, valuation metrics, estimates, dividends), quarterly EPS for P/E
   history, profiles on a free AV plan, legacy snapshot indices (FMP); news and earnings calendar (Finnhub).
5. **SEC adapter still unwired** (needs an approved identifying User-Agent and a real statement/period comparison).
6. **Metrics engine not switched** to AV/SEC facts (definitions and inferred periods differ; real comparison BLOCKED).
7. **Splits/dividends:** parsed, not applied to Portfolio/Practice (policy decision D-9B7-5); charts show as-traded cliffs; P/E history
   omits months that may span a split (conservative).
8. **Free plan:** 100 daily sessions of history, 25 requests/day — unusable beyond a smoke test.
9. AV sectors (SEC office names) aren't mapped to Discover's 11 sectors (left null); movers have no company names; statement EPS absent;
   quarter labels inferred from the fiscal year-end month (52/53-week years handled).
10. Budgets, caches and the Discover cap are per instance.
11. A Ktor exception that isn't a known network/timeout type is mapped to a typed failure without its message (it could contain the URL
    with the key); the existing `apiCall` (FMP) still rethrows such unknown exceptions (pre-existing, unchanged).

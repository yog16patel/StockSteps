# Phase 7C — Practice Portfolio and My Portfolio EOD valuation

Date: 2026-10-17. Base: `b9f871a` (Phase 7B, committed and pushed; **deployed to LAN REAL 2026-10-17**, image `b9f871a57642`, built from the
clean commit before any 7C edit). Phase 7A `c57135a` is deployed too. ChatGPT reviews of 7A and 7B are pending. **This phase:
"Value My Portfolio and execute Practice trades at dated official closing prices for Phase 7C with one latest-close rule, no quotes on REAL, unavailable totals instead of partial sums, split-like move warnings and a closing-price freshness line on Android and iOS", committed and pushed on the owner's request; not deployed.** Review package: `docs/reviews/portfolio_practice_eod_phase7c/`.

**Verdict: READY FOR REVIEW**, with owner decisions:
- the valuation and execution windows;
- the copy;
- keeping "Today's change" unavailable on closing prices;
- the redeploy for the device pass.

## 1. Data flows (before this phase, from the code)

**My Portfolio**
- **Path:** app → shared `PortfolioPresenter` → `UserApi` `/api/v1/me/portfolio/*` (Firebase ID token → `FirebaseIdTokenAuthenticator`
  → uid) → `PortfolioService` (Firestore `users/{uid}` ledger via `FirestoreUserDataStore`, transactional `mutate`) →
  `PortfolioMarketService.report`.
- **Pricing:**
  - **one quote per holding** (`WatchMarketData.quote`, 60 s) + the profile currency (24 h);
  - BoC USD/CAD (6 h);
  - dated closes only for the history chart (`portfolio-history:SYM` 6 h on top of `price-chart`).
- **Totals:** `PortfolioEngine.value`. Exact `Decimal`; a missing price or FX makes `totalValue` null and is listed in `missing`.
  Allocations stay unavailable rather than renormalized.
- **Daily change:** only with fresh same-session quotes.

**Practice**
- **Path:** app → `PracticePresenter` → `/api/v1/me/practice/*` → `PracticeService`. One Firestore document per user; every mutation in
  one atomic update; idempotency records per order key; reset by generation.
- **Price:** `priceForTrade`. A **fresh quote** is required: `AlertRules.freshQuote` (today's session; age limit while open), else
  `QUOTE_STALE` / `QUOTE_MISSING` / `QUOTE_RESTRICTED` (7A). FX at the quote date. The client's `reviewedPrice` must match the server
  price within 1 % (`PRICE_CHANGED`).
- **Valuation:** the same quotes.
- **Where restricted quotes blocked valid operations:**
  - every Practice trade for a symbol whose quote the plan refuses;
  - every My Portfolio total containing such a holding (total null, "Unavailable: SYM"), although daily closes for those symbols were
    available.

## 2. What changed

| Area | Change |
|---|---|
| Server `service/EodCloses.kt` | `EodPrice.latest`: the latest valid official close (finite, > 0, trading day, not after the expected session) with its date, closing instant and `sessionsBehind`. `VALUATION_MAX_SESSIONS = 5`, `EXECUTION_MAX_SESSIONS = 1`. `isSplitLike(EodChange)` (≥ 1.8× or ≤ 0.55× between the last two consecutive closes) |
| Server `PracticeService` | `eod` (REAL): trades price at the last available close (`EOD_BASIS` "Simulated trade using the last available closing price", `quoteAsOf` = the closing instant, FX at the close date); one session behind allowed (late publication); older → `QUOTE_STALE` (with the date); none or invalid → `QUOTE_MISSING`; 402/403 → `QUOTE_RESTRICTED`. Valuation at the same close (≤ 5 sessions, `stale` when behind). Split-like warning notice |
| Server `PortfolioMarketService` | `eod` (REAL): current prices from `EodPrice.latest` per holding through the shared `price-chart` cache (≤ 5 sessions); no quotes; `pricesAsOf` = oldest close used; `quoteAsOf` null; daily change → `EOD_DAILY_NOTICE`; split-like note appended to the chart notice |
| Server `MarketsService` | an all-failed sector section with any 429 reads `PROVIDER_RATE_LIMITED` (found on the LAN REAL check, §6) |
| Server `Application.kt` | `eod = eodSessions` for Portfolio and Practice **in REAL only**; MOCK keeps fixture quotes (MOCK closes stop at the 2026-10-07 capture and would read as stale) |
| Core `PortfolioModels.kt`, `PortfolioPresenter.kt` | `PortfolioReport.pricesAsOf` / `PortfolioUiState.pricesAsOf` (optional) |
| Shared `PortfolioPresentation.freshnessLines` | "Closing prices as of Oct 16, 2026" when `pricesAsOf` is set (Portfolio and Home cards on Android and iOS use it) |
| Android / iOS | no code change: the Practice order screen already shows `priceBasis · date`; blocked previews show "Unavailable" (7A) |

Unchanged:
- `PortfolioEngine` and `PracticeEngine` mathematics;
- Firestore documents (no migration, no rewrite);
- idempotency, authentication and the user-data error mapping;
- Markets, Discover and the UI layouts.

## 3. Valuation and execution policy

`docs/reviews/portfolio_practice_eod_phase7c/VALUATION_AND_EXECUTION_RULES.md`.

## 4. Request benchmark (MockEngine; `server/build/phase7c-portfolio-benchmark.txt`)

| Scenario | Before quote / profile / EOD / BoC | After quote / profile / EOD / BoC |
|---|---|---|
| Portfolio, 5 holdings, one summary | 5 / 5 / 0 / 1 | 0 / 5 / 5 / 1 |
| Portfolio, 20 holdings, one summary | 20 / 20 / 0 / 1 | 0 / 20 / 20 / 1 |
| 5 holdings, 10 refreshes 1 min apart | 50 / 5 / 0 / 1 | 0 / 5 / 5 / 1 |
| 10 users holding the same stock, concurrent | 1 / 1 / 0 / 1 | 0 / 1 / 1 / 1 |
| Weekend, 5 holdings, hourly for 24 h | 120 / 5 / 0 / 4 | 0 / 5 / 5 / 4 |
| 5 holdings, one without a close, 10 refreshes / 1 h | 50 / 5 / 0 / 1 | 0 / 5 / 6 / 1 |
| 5 holdings, one refused (402), 10 refreshes / 1 h | 41 / 5 / 0 / 1 | 0 / 5 / 5 / 1 |
| Practice, 4 positions, 10 overviews 1 min apart | 31 / 4 / 0 / 1 | 0 / 4 / 4 / 1 |

Closes are shared with charts, valuation and Markets (7B), so a symbol already seen elsewhere costs nothing.

## 5. Tests and builds

`CHATGPT_TEST_RESULTS.md` in the package. New tests:
- `PracticeEodTest` (6);
- `PortfolioEodTest` (5);
- `PortfolioPracticeEodBenchmarkTest` (1);
- `MarketsEodTest` sector rate-limit case;
- `PortfolioPresentationTest` closing-price line.

## 6. Live LAN REAL check of 7B (2026-10-17 ~01:3xZ, one Markets overview)

FMP answered 429 to every request, so the key is still at its limit. Only 7 FMP requests were made, then the circuit refused the rest
locally. All sections failed independently with typed errors, and no value was invented. The sector section then read "temporarily
unavailable" instead of "rate limited", which is fixed here (§2). EOD values on REAL are NOT VERIFIED until the FMP limit resets.

## 7. Device verification

**BLOCKED:**
- this phase needs an approved redeploy;
- the FMP key is rate-limited;
- signed-in device passes need the owner's test account.

No Android/iOS code changed; both builds pass.

## 8. Corporate actions, Firestore, licensing

- Corporate actions: `CORPORATE_ACTIONS_GAPS.md` (a warning only; nothing adjusted).
- Firestore and security: `FIRESTORE_SECURITY_AND_PERSISTENCE.md`.
- Licensing: display and shared-caching rights for FMP closes are UNVERIFIED (Phase 6 Q4). The EOD adjustment basis is UNVERIFIED (Q6).

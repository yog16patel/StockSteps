# Alpha Vantage licence and entitlements — Phase 9B.7

**A personal premium key is not a commercial licence.** Evidence (Alpha Vantage Terms of Service PDF, retrieved 2026-10-17, §2 "Grant of
License"): the licence is "for personal, non-commercial use, unless you and Alpha Vantage have agreed otherwise in writing". Use is
commercial if any applies: purposes beyond private and individual activities such as investment analysis, research, testing and
monitoring; use as or on behalf of a company; providing information "as part of any type of commercial activity that allows individuals
or entities other than User to access information directly or indirectly"; affiliation with financial professionals. Realtime and
15-minute delayed US data is premium "for your personal use; for commercial use, please contact sales" (documentation tips) and
regulated by the exchanges, FINRA and the SEC.

| Dimension | Status | Basis |
|---|---|---|
| Technical entitlement (free key) | VERIFIED (docs): free functions only, 25 requests/day; daily `compact` only | documentation + premium page |
| Technical entitlement (premium key) | VERIFIED (docs) per function; **account UNVERIFIED** (no key here) | documentation |
| Personal development and testing by the key holder | VERIFIED_ALLOWED — "testing" by the user privately; the owner must be the only person who sees the data | ToS §2 (i) |
| Internal testers / closed beta | VERIFIED_RESTRICTED — other people accessing the information = commercial | ToS §2 (iii) |
| Commercial mobile display | VERIFIED_RESTRICTED — written agreement needed | ToS §2 |
| Commercial website display | VERIFIED_RESTRICTED (separate channel) | ToS §2 |
| Shared caching and storage | PENDING_CONFIRMATION — terms are silent | — |
| Derived metrics shown to others | VERIFIED_RESTRICTED (it is still providing information to others) | ToS §2 (iii) |
| AI processing (Gemini explanations) | PENDING_CONFIRMATION — terms are silent; output shown to others is commercial | — |
| Redistribution / export | VERIFIED_RESTRICTED | ToS §2 |
| If StockSteps is operated by a company | VERIFIED_RESTRICTED even for the owner | ToS §2 (ii) — counsel/owner question |

In code (`marketdata/DataRights.kt`): `alphavantage` rows for every dataset with `display = VERIFIED_RESTRICTED`, `personalDevTest =
VERIFIED_ALLOWED`, `internalTesters = VERIFIED_RESTRICTED`, `sharedCache`/`aiProcessing = PENDING_CONFIRMATION`, no channels.

## Enforcement at the serving boundary (`marketdata/MarketDataAccessGate.kt`)
- **Personal mode** (default when Alpha Vantage is primary): market-data routes answer only callers on explicit allowlists — a verified
  Firebase uid in `ALPHA_VANTAGE_PERSONAL_UIDS` or a client address in `ALPHA_VANTAGE_PERSONAL_CLIENTS` (trusted address with
  `TRUSTED_PROXY_HOPS`, else the socket peer; `loopback`). Empty lists serve nobody (startup warning). Refused on Cloud Run (startup error).
  Public market-data requests from the apps carry no token today, so a guest owner device is matched by its address.
- **Commercial mode** (`ALPHA_VANTAGE_LICENSE_MODE=commercial` or `STOCKSTEPS_LICENSE_MODE=commercial`): every active market-data provider
  must be `VERIFIED_ALLOWED` for the channel — none is, so market data fails closed (502 `PROVIDER_RESTRICTED`, no provider call).
- Non-market-data features (meta, Learn, entitlements, watchlist/alert management, Practice entitlement/trial/reset, devices, health) are
  never gated. MOCK never gates (sample data).

## Exact questions for Alpha Vantage sales (Q13)
1. Does a premium subscription held by an individual allow that individual to build and test an app on their own devices, with the data
   served from their own server (shared cache, one user)?
2. Price and terms for a free consumer app (mobile and, separately, web) showing end-of-day US prices, daily history, fundamentals,
   corporate actions and movers to the public, at 1,000 and 10,000 MAU.
3. May responses be cached server-side and served to many users; for how long; may they be persisted (database)?
4. May StockSteps show values derived from the data (returns, P/E, margins) and send it to an AI model to generate explanations?
5. Attribution wording; logo use; any exchange or index-owner fees for EOD prices and index levels shown commercially.

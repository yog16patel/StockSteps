# Portfolio and Practice data correctness — Phase 9B (audit only; no calculation, rule or UI change)

Read in code: `practice/PracticeService.kt` (`eodPriceForTrade`, `priceForTrade`, corporate actions), `userdata/PortfolioMarketService.kt`
(`Context`, `ratesAt`, `priceInTradeCurrency`, `closes`), `service/EodCloses.kt` (`EodPrice`, `EodChange`, `isSplitLike`),
`userdata/FirestoreUserDataStore.kt` (transactions), core `portfolio/PortfolioEngine` + `Decimal`. Evidence from 7C/7D tests
(`PracticeAccountingReferenceTest`, the Firestore-emulator integration tests, `PortfolioPracticeEodBenchmarkTest`).

| Area | Current behaviour (REAL) | Assessment | Risk | Option (needs approval) |
|---|---|---|---|---|
| Execution policy | immediate fill at the expected session's official close, or the previous one while the provider is late (`EXECUTION_MAX_SESSIONS = 1`); older → `QUOTE_STALE`, none → `QUOTE_MISSING`, plan refusal → `QUOTE_RESTRICTED`; the client never sets the price; label "Simulated trade using the last available closing price" | correct and deterministic; matches Phase 6 recommendation | price can be a day old during the session (labelled) | queued next-close fills as a later teaching feature |
| Valuation freshness | latest valid close ≤ 5 sessions (`VALUATION_MAX_SESSIONS`), dated ("Closing prices as of …"); a holding without one stays missing and the total is **null** (never a partial sum) | correct | a delisted holding becomes unavailable after 5 sessions with no explanation beyond "missing" | add a "no longer trading" state when a corporate-actions/listing source exists |
| Missing prices | null, never $0 (7A/7C tests) | correct | — | — |
| Split-adjusted history | ledger quantities are as-traded; daily closes come from FMP light with an **unverified basis**; `isSplitLike` only warns (nothing adjusted) | **open** | if the series is split-adjusted, pre-split history values = as-traded quantity × adjusted price → wrong by the split ratio before the split date; if unadjusted, the graph is right but the split day shows a cliff unless quantities are adjusted | D-9B-5: licensed corporate actions + one documented basis (prefer unadjusted closes + split events applied to quantities) |
| Corporate actions | REAL `CorporateActionSource` not wired (MOCK only); Practice `applyActions` exists | **gap (launch blocker, 8D backlog)** | a split mis-values Practice positions and Portfolio holdings until handled | as above |
| Trade-date FX vs reporting FX | cost basis converted at the trade date's latest BoC publication (≤ 7 days earlier, else unavailable: `ratesAt`); current value: listing → trade currency at the close's date, trade → reporting currency at today's latest publication; Practice: FX at the close's date | consistent, no future FX (MOCK FX dated too); disclosed `fxAsOf` | close date and FX date can differ by a few days around holidays (≤ 7 days rule) | none needed; document in-app (already: FX date shown) |
| History graph methodology | "Account value" from dated daily closes × replayed holdings, FX dated; chart only with ≥ 2 dated values; never "performance" | correct by design | the history cache (`portfolio-history:SYM`, 6 h) sits on top of the shared close cache → a new close can appear up to 6 h late (Phase 6 dup #4) | key the history layer to the close cache's TTL (S, server) |
| Holidays/weekends | `EodSessions` with US/TSX calendars; expected session after a 60-min publication delay; tested on Saturday/holidays (7B/7C, 9B `PriceBoundaryTest`) | correct | provider publication delay unverified (`EOD_PUBLICATION_DELAY_MINUTES`) | confirm with the vendor |
| Ownership | 9A central gate; every `/me` read/write scoped to the verified uid; Firestore paths `users/{uid}/…` | correct (two-user isolation test on emulators, 9A) | — | — |
| Atomicity | Firestore `runTransaction` for ledger writes (reads before writes) | correct | — | — |
| Idempotency, double fills | request id kept across unknown-outcome failures (7D D2 fix); same-key retries fill once; competing buys/sells serialised (7D emulator tests) | correct | — | — |
| Overspending | `PortfolioEngine` / Practice cash checks before writing; negative cash is a supported *Portfolio* state (user entries), not a Practice outcome | correct | — | — |
| Cross-user data | no user data in shared caches (keys are symbols/dates only) | correct | — | — |
| Money arithmetic | ledger in `portfolio/Decimal`; provider closes arrive as `Double` and are converted with `BigDecimal.valueOf(...).setScale(8)` | acceptable (8 decimals, no float accounting) | — | the price boundary carries `BigDecimal` closes for future use |

**Material decisions to stop on (none implemented):** D-9B-5 corporate actions and price basis; the history-cache TTL fix (small, but it
changes when values update — needs approval like every valuation change). Calculations, execution rules and copy are unchanged in 9B.

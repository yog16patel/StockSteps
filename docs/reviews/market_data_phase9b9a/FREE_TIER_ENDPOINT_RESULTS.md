# Free-tier endpoint results — Phase 9B.9A

**Authenticated requests made: 0.** Live validation is BLOCKED:
1. **Terms:** Alpha Vantage Terms of Service §2 (re-read 2026-10-17, unchanged): the free licence is "personal, non-commercial" unless
   agreed in writing, and use "as or on behalf of a corporation … and not as an individual" is commercial. The brief describes a
   company-associated development workflow, so permission is **unclear** → fixture-only validation, as the brief requires.
2. **Credentials:** no `ALPHA_VANTAGE_API_KEY` on this machine.

Verified without a key (official pages, 2026-10-17): free service "covering the majority of our datasets for 25 API requests per day"
(support page); ToS identical to the 9B.7 read; premium labels per function as in `../market_data_phase9b7/ALPHA_VANTAGE_ENDPOINT_MATRIX.md`.

| Function | FREE status (docs) | StockSteps on FREE | Fixture validation | Live |
|---|---|---|---|---|
| TIME_SERIES_DAILY | free, `compact` (100 sessions); `full` premium | requested `compact` only | fields, dates (oldest first), zone, zero/invalid rows dropped, as-traded | NOT RUN |
| GLOBAL_QUOTE | free, end-of-day | no `entitlement` parameter | symbol, latest trading day, price, previous close, change %, empty quote → none | NOT RUN |
| SYMBOL_SEARCH | free | US equities/ETFs only | 9 fields, region/type filter | NOT RUN |
| INCOME_STATEMENT / BALANCE_SHEET / CASH_FLOW | free | annual + quarterly; quarters only if verified standalone | "None"/"-" → null, currency, quarter labels, 52/53-week years, standalone vs YTD | NOT RUN |
| EARNINGS (optional) | free | parsed, not used for metrics | `reportedEPS` "None" → null | NOT RUN |
| SPLITS (optional) | free | dividend-yield split check | ratio, effective date | NOT RUN |
| OVERVIEW, DAILY_ADJUSTED, NEWS_SENTIMENT, INDEX_DATA, realtime/delayed | premium | **refused locally, 0 requests** | `premiumFunctionsCostZeroUpstreamCallsOnFree` | — |
| Information / Note / Error Message in HTTP 200 | — | typed failures (429 / 402 / 403 / none), never data | `AlphaVantageParsersTest`, smoke-runner stop rule | NOT RUN |

When the owner confirms the terms and sets the key, `./gradlew :server:alphaVantageSmokeTest` (`OWNER_DECISIONS.md`) fills the "Live"
column with ≤ 10 requests.

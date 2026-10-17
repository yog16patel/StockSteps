# Alpha Vantage adapter review — Phase 9B.7

Code: `server/.../marketdata/alphavantage/` (`AlphaVantageConfig.kt`, `AlphaVantageParsers.kt`, `AlphaVantageClient.kt`,
`AlphaVantageProviders.kt`), `httpclient/NetworkUtils.kt` (`alphaVantageCall`, `AlphaVantageAnswers`, provider/endpoint labels),
`service/ProviderBudget.kt` (`alphavantage` lane), `marketdata/MarketDataAccessGate.kt`, `Application.kt` (wiring), `StartupConfiguration.kt`.

| Requirement | Implementation |
|---|---|
| HTTPS, server-side credentials | fixed base `https://www.alphavantage.co/query` (asserted); key from `ALPHA_VANTAGE_API_KEY`, added only as the `apikey` request parameter — never in the URL string that counters/logs see, never in responses, `toString`, startup messages or fixtures |
| Typed functions | `dailyRaw`, `dailyAdjusted`, `quote`, `search`, `overview`, `statement`, `earnings`, `dividends`, `splits`, `movers` |
| Safe bounded JSON | body ≤ 16 MB (declared `Content-Length` refused before reading; read stops at bound + 1); parsers never throw on missing fields; `"None"`/`"-"`/`""` → null; malformed → `INVALID_RESPONSE` |
| Timeouts | shared client (connect 5 s, socket 10 s) + 20 s per request in REAL |
| HTTP-200 refusals | `AlphaVantageAnswers.classify`: `Note`/`Information` with rate wording → 429 (rate limited, breaker opens); "premium" → 402 (restricted, 1 h cooldown); key notice → 403; `Error Message` → 404 ("no such symbol", cached as none); other notices → 503; refusal text never logged or returned |
| ProviderGuard / ProviderCalls | every attempt acquires an `alphavantage` permit and completes it with the classified status; one `ProviderCalls.record` with bounded labels `query/<FUNCTION>` |
| Concurrency and quota | lane from `ALPHA_VANTAGE_PLAN_REQUESTS_PER_MINUTE` / `_DAILY_BUDGET` / `_MAX_CONCURRENT`; free default 5/min, 25/day, 1 at a time (× 0.8 margin per instance) |
| Coalescing + cache | one `CompanyFinancialCache("alphavantage")`, single flight per function + symbol; TTLs: daily until next expected close, quote/movers 15 min, search 6 h, overview/statements/actions 24 h, earnings 12 h |
| No uncontrolled retries | none; refusals cached as outcomes (`providerCooldown`); fallback never retries another provider for a refused dataset |
| Plan gating | premium functions refused locally without a configured plan (no request); index tickers refused locally |
| Response contracts | unchanged: `StockQuote`, `CompanyProfile`, `StockSearchResult`, `MarketMover`, `StatementHistory`, `PricePoint` |

**Wired datasets** (when `ALPHA_VANTAGE_ENABLED=true`, key set, `MARKET_DATA_PRIMARY_PROVIDER=alpha_vantage`): daily closes (charts,
Markets sectors and fund closes, Portfolio/Practice valuation and execution, Discover closes, valuation history with the raw-basis rule),
quotes (Details, Watchlist, alerts, Markets index quotes), search, movers, statement history, profiles (premium plan only).
**Remaining FMP dependencies by configuration:** the fundamentals bundle (`getFundamentals`/`screenerFundamentals`: ratios, valuation
metrics, dividends, estimates), quarterly EPS for P/E history (`QuarterlyEarningsSource`), profiles without a premium plan, the legacy
`/market/snapshot` indices. **Finnhub:** news, earnings calendar.

Known risks: field names other than the daily series are from the documented shapes (smoke test needed); Alpha Vantage gives no company
names for movers (rows show tickers); OVERVIEW sectors use SEC office names (left null); statement EPS absent (stays null); quarter labels
inferred from the fiscal year-end month.

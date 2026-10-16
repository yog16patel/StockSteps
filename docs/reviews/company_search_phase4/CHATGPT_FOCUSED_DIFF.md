# Phase 4 Company Search — focused diff

Applies because the change touches a **cache key** (AI_REVIEW_WORKFLOW §3.4) and adds a **concurrency** regression test. Baseline `6d6b68e`;
uncommitted working tree.

## 1. Server: normalized query for the provider and the search cache
`server/src/main/kotlin/org/example/stocksteps/service/StockService.kt`
```kotlin
-    suspend fun searchStocks(query: String): List<StockSearchResult> =
-        searchCache.getOrLoad(query.trim().lowercase(Locale.ROOT), FinancialCachePolicy.SEARCH) { search(query.trim()) }
+    suspend fun searchStocks(query: String): List<StockSearchResult> {
+        val normalized = query.trim().split(WHITESPACE).joinToString(" ")
+        return searchCache.getOrLoad(normalized.lowercase(Locale.ROOT), FinancialCachePolicy.SEARCH) { search(normalized) }
+    }
+        val WHITESPACE = Regex("\\s+")
```
- **Decision:** the approved 5D.5.5 plan (§7) already required collapsing inner whitespace; only `SearchRanking` did. The provider query and
  the cache key now use the same normalized text, so `apple  inc` finds Apple and shares `apple inc`'s entry.
- **Risk:** low. Same endpoint, same parameters, same TTL/capacity. The route still rejects control characters (tabs/newlines → 400), so
  only spaces collapse in practice. Keys still contain only public query text (no user data). Old entries keyed with doubled spaces simply
  expire (6 h).
- **Test:** `StockSearchTest.innerWhitespaceAndCaseShareOneNormalizedProviderQueryAndCacheEntry` — three spellings → AAPL each, provider saw
  exactly `["apple inc", "apple inc"]` (one search-symbol + one search-name). Existing `FinancialCacheTest.chartAndSparklineShareOneIntradayRequestAndSearchIsNormalized`
  still covers case/trim sharing.

## 2. Shared ViewModel: stale-response regression test (no production change)
`app/shared/src/commonTest/.../stocksearch/StockSearchViewModelTest.kt` — `olderSlowerQueryNeverReplacesNewerResultsEvenWithoutCancellation`
```kotlin
if (query == "app") withContext(NonCancellable) { delay(3000) } else delay(500)
...
model.changeQuery("app"); advanceTimeBy(301); runCurrent()
model.changeQuery("apple"); runCurrent()          // searching = true, results cleared
advanceTimeBy(301); runCurrent(); advanceTimeBy(500); runCurrent()
assertEquals(listOf("AAPL"), results)               // B applied
advanceTimeBy(3000); runCurrent()
assertEquals(listOf("AAPL"), results)               // A's late answer ignored
assertEquals(listOf("app", "apple"), calls)
```
- **Decision:** proves the latest-query guard in `StockSearchViewModel.search` holds even when cancellation can't stop the transport.
- **Risk:** none (test only).

## 3. Core ranking examples (test only)
`core/src/commonTest/.../search/SearchRankingTest.kt` — `exactTickerBeatsPartialNamesAndShareClassesStayApart`: APP above AAPL, AAPL above an
ETF naming it, Microsoft/micro, GOOG vs GOOGL, SPY vs SPYG, unknown symbol keeps provider order, BRK.B = BRK-B, inner whitespace. No production
ranking code changed; nothing is hard-coded in `SearchRanking`.

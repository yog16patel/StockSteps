package org.example.stocksteps

import kotlinx.coroutines.runBlocking
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.FinancialCachePolicy
import kotlin.test.*

class FinancialCacheCooldownTest {
    @Test fun deniedLoadsUseLongerCooldownAndRetryAfterExpiry() = runBlocking {
        var now = 0L
        var calls = 0
        val cache = CompanyFinancialCache(now = { now })
        suspend fun load() = cache.getOrLoad("denied", FinancialCachePolicy.FAILURE,
            resultTtl = { _: String -> FinancialCachePolicy.ACCESS_COOLDOWN }) { calls++; "neutral missing" }
        load()
        now += FinancialCachePolicy.FAILURE + 1
        load()
        assertEquals(1, calls)
        now = FinancialCachePolicy.ACCESS_COOLDOWN + 1
        load()
        assertEquals(2, calls)
    }
}

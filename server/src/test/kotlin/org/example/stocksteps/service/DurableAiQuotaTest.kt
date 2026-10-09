package org.example.stocksteps.service

import kotlinx.coroutines.*
import org.example.stocksteps.earnings.EarningsAiCategory
import org.example.stocksteps.earnings.EarningsAiQuotaLedger
import org.example.stocksteps.earnings.EarningsRequestException
import org.example.stocksteps.userdata.AiUsageDocument
import org.example.stocksteps.userdata.InMemoryUserDataStore
import org.example.stocksteps.userdata.UserDataStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.*

/**
 * Phase 4B: durable AI quotas on the shared `aiUsage` document. "Instances" are separate quota objects over one store (Firestore in REAL,
 * whose transactions the in-memory store's lock stands in for); fake clocks; no AI provider.
 */
class DurableAiQuotaTest {
    private class MovableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
        override fun instant() = now
    }
    private val clock = MovableClock(Instant.parse("2026-10-07T18:00:00Z"))
    private val daily = AiQuotaPolicy("brief-ai", 10)

    @Test fun sameUserOnTwoInstancesSharesOneAllowanceUnderConcurrency(): Unit = runBlocking {
        val store = InMemoryUserDataStore()
        val a = DurableAiQuota(store, clock); val b = DurableAiQuota(store, clock)
        val outcomes = coroutineScope { (1..50).map { i -> async(Dispatchers.Default) { runCatching { (if (i % 2 == 0) a else b).reserve("u", daily) }.isSuccess } }.awaitAll() }
        assertEquals(10, outcomes.count { it }, "50 simultaneous requests across two instances → exactly the daily limit")
        assertEquals(10 to 10, a.usage("u", listOf(daily))["brief-ai"])
        assertEquals("AI_DAILY_LIMIT", assertFailsWith<AiQuotaDenied> { b.reserve("u", daily) }.code)
        // A restarted instance (a new object) sees the same count.
        assertFailsWith<AiQuotaDenied> { DurableAiQuota(store, clock).reserve("u", daily) }
    }

    @Test fun dailyResetAndRollingWindowBoundaries(): Unit = runBlocking {
        val q = DurableAiQuota(InMemoryUserDataStore(), clock)
        val windowed = AiQuotaPolicy("comparison-ai", dailyLimit = 3, windowLimit = 5, windowDays = 30)
        repeat(3) { q.reserve("u", windowed) }
        val denied = assertFailsWith<AiQuotaDenied> { q.reserve("u", windowed) }
        assertEquals("AI_DAILY_LIMIT", denied.code); assertEquals(Instant.parse("2026-10-08T00:00:00Z"), denied.nextAvailable)
        clock.now = Instant.parse("2026-10-08T00:00:01Z")                                  // UTC day boundary
        repeat(2) { q.reserve("u", windowed) }
        val window = assertFailsWith<AiQuotaDenied> { q.reserve("u", windowed) }
        assertEquals("AI_QUOTA_EXCEEDED", window.code, "5 in the rolling 30 days")
        assertEquals(Instant.parse("2026-11-06T18:00:00Z"), window.nextAvailable, "the oldest charge leaves the window 30 days later")
        clock.now = Instant.parse("2026-11-06T18:00:00Z")
        q.reserve("u", windowed)                                                              // exactly at the boundary the oldest charges expire
    }

    @Test fun retriesFailuresTimeoutsAndCancellation(): Unit = runBlocking {
        val q = DurableAiQuota(InMemoryUserDataStore(), clock)
        val first = q.reserve("u", daily, "idem-key-0001")
        val retry = q.reserve("u", daily, "idem-key-0001")
        assertTrue(retry.duplicate); assertEquals(first.chargeId, retry.chargeId)
        assertEquals(1, q.usage("u", listOf(daily))["brief-ai"]!!.first, "a retry isn't charged twice")
        val failed = q.reserve("u", daily)
        assertTrue(q.settleFailure(failed, IllegalStateException("provider 500")), "a provider error is refunded")
        val timedOut = q.reserve("u", daily)
        assertFalse(q.settleFailure(timedOut, TimeoutCancellationExceptionFactory.create()), "a timeout keeps the charge")
        val cancelled = q.reserve("u", daily)
        assertFalse(q.settleFailure(cancelled, CancellationException("client left")), "a cancellation keeps the charge")
        assertEquals(3, q.usage("u", listOf(daily))["brief-ai"]!!.first)
    }

    @Test fun storageOutageFailsClosed(): Unit = runBlocking {
        val broken = object : UserDataStore by InMemoryUserDataStore() {
            override suspend fun <T> updateAiUsage(uid: String, block: (AiUsageDocument) -> Pair<AiUsageDocument, T>): T = throw IllegalStateException("firestore down")
        }
        assertFailsWith<AiQuotaUnavailable> { DurableAiQuota(broken, clock).reserve("u", daily) }
        val ledger = EarningsAiQuotaLedger(mapOf(EarningsAiCategory.QUESTION to 5), 100, clock, broken)
        assertEquals(503, assertFailsWith<EarningsRequestException> { ledger.reserve("u", EarningsAiCategory.QUESTION) }.status)
    }

    @Test fun featuresDontPruneEachOthersHistoryAndTheCombinedCapIsOptional(): Unit = runBlocking {
        val store = InMemoryUserDataStore()
        val comparison = AiQuotaPolicy("comparison-ai", dailyLimit = 10, windowLimit = 50, windowDays = 30)
        val q = DurableAiQuota(store, clock)
        q.reserve("u", comparison)
        clock.now = clock.now.plusSeconds(5 * 86_400L)
        q.reserve("u", daily)                                                                 // a daily-only feature writes the shared doc
        assertEquals(1, q.usage("u", listOf(comparison))["comparison-ai"]!!.second, "the 5-day-old comparison charge is still counted")
        // Combined cap: off by default; when configured it spans the listed features.
        val capped = DurableAiQuota(store, clock, CombinedAiCap(setOf("brief-ai", "earnings-ai-question"), 2))
        capped.reserve("v", daily)
        capped.reserve("v", AiQuotaPolicy("earnings-ai-question", 20))
        assertTrue(assertFailsWith<AiQuotaDenied> { capped.reserve("v", daily) }.combined)
        DurableAiQuota(store, clock).reserve("v", daily)                                       // without a cap only feature limits apply
    }

    @Test fun earningsLedgerIsSharedAcrossInstances(): Unit = runBlocking {
        val store = InMemoryUserDataStore()
        val limits = mapOf(EarningsAiCategory.EXPLANATION to 3, EarningsAiCategory.QUESTION to 20, EarningsAiCategory.DIGEST to 3)
        val a = EarningsAiQuotaLedger(limits, 1_000, clock, store); val b = EarningsAiQuotaLedger(limits, 1_000, clock, store)
        a.reserve("u", EarningsAiCategory.EXPLANATION); b.reserve("u", EarningsAiCategory.EXPLANATION); a.reserve("u", EarningsAiCategory.EXPLANATION)
        assertEquals("AI_QUOTA_EXCEEDED", assertFailsWith<EarningsRequestException> { b.reserve("u", EarningsAiCategory.EXPLANATION) }.code)
        assertEquals(0, b.usage("u", true, org.example.stocksteps.portfolio.analytics.EntitlementStatus.ACTIVE).quota(EarningsAiCategory.EXPLANATION)!!.remaining)
        assertEquals(20, a.usage("u", true, org.example.stocksteps.portfolio.analytics.EntitlementStatus.ACTIVE).quota(EarningsAiCategory.QUESTION)!!.remaining)
    }
}

/** `TimeoutCancellationException` has no public constructor; obtain a real one. */
private object TimeoutCancellationExceptionFactory {
    fun create(): TimeoutCancellationException = runBlocking { try { withTimeout(1) { delay(1_000) }; error("unreachable") } catch (e: TimeoutCancellationException) { e } }
}

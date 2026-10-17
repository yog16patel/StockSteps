package org.example.stocksteps.marketdata

import kotlinx.coroutines.runBlocking
import org.example.stocksteps.appconfig.DataMode
import org.example.stocksteps.repository.StockProviderException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 9B: fail-closed licensing, deterministic source order and fallback rules (fakes only). */
class SourceSelectionAndLicensingTest {
    private val at = Instant.parse("2026-10-17T00:00:00Z")
    private val registry = LicenseRegistry.DEFAULT
    private fun src(p: String, d: Dataset = Dataset.DAILY_PRICES, mode: DataMode = DataMode.REAL, fresh: Int = 1, complete: Int = 100, cost: Int = 1, basis: AdjustmentBasis? = null) =
        SourceCandidate(p, d, mode, fresh, complete, cost, basis)
    private fun value(p: String, d: Dataset = Dataset.DAILY_PRICES, stale: Boolean = false) = Sourced("v-$p", Provenance(p, d, at, "2026-10-16", stale = stale))

    @Test fun officialPublicSourcesAreClearedAndCommercialFeedsFailClosed() {
        assertTrue(registry.displayAllowed("sec", Dataset.REPORTED_FUNDAMENTALS, DisplayChannel.MOBILE_APP))
        assertTrue(registry.displayAllowed("sec", Dataset.REPORTED_FUNDAMENTALS, DisplayChannel.WEBSITE))
        assertTrue(registry.displayAllowed("boc", Dataset.FX_RATES, DisplayChannel.MOBILE_APP))
        for (d in listOf(Dataset.QUOTES, Dataset.DAILY_PRICES, Dataset.REPORTED_FUNDAMENTALS, Dataset.COMPANY_LOGO, Dataset.COMPANY_PROFILE))
            assertFalse(registry.displayAllowed("fmp", d, DisplayChannel.MOBILE_APP), "fmp $d")
        assertFalse(registry.displayAllowed("finnhub", Dataset.COMPANY_NEWS, DisplayChannel.MOBILE_APP))
        assertFalse(registry.displayAllowed("alphavantage", Dataset.DAILY_PRICES, DisplayChannel.MOBILE_APP), "unknown pairs are never allowed")
        assertFalse(registry.sharedCacheAllowed("fmp", Dataset.DAILY_PRICES))
        assertTrue(registry.sharedCacheAllowed("sec", Dataset.REPORTED_FUNDAMENTALS))
    }

    @Test fun derivedValuesInheritTheMostRestrictiveInput() {
        val secOnly = listOf("sec" to Dataset.REPORTED_FUNDAMENTALS)
        assertTrue(registry.derivedDisplayAllowed(secOnly, DisplayChannel.MOBILE_APP))           // margins, growth, ROE from SEC
        assertFalse(registry.derivedDisplayAllowed(secOnly + ("fmp" to Dataset.DAILY_PRICES), DisplayChannel.MOBILE_APP))  // P/E with an FMP price
        assertFalse(registry.derivedDisplayAllowed(emptyList(), DisplayChannel.MOBILE_APP))
    }

    @Test fun aSignedAgreementCanBeRecordedPerChannel() {
        val signed = registry.with(DataRights("fmp", Dataset.DAILY_PRICES, DataClass.LICENSED_COMMERCIAL, LicenseStatus.VERIFIED_ALLOWED,
            LicenseStatus.VERIFIED_ALLOWED, LicenseStatus.VERIFIED_ALLOWED, LicenseStatus.NOT_ALLOWED, setOf(DisplayChannel.MOBILE_APP), "Data: FMP", "agreement #test"))
        assertTrue(signed.displayAllowed("fmp", Dataset.DAILY_PRICES, DisplayChannel.MOBILE_APP))
        assertFalse(signed.displayAllowed("fmp", Dataset.DAILY_PRICES, DisplayChannel.WEBSITE), "mobile rights never imply web rights")
    }

    @Test fun licenseModeDefaultsToDevelopmentAndCommercialFailsClosed() {
        assertEquals(LicenseMode.DEVELOPMENT, LicenseMode.fromEnvironment { null })
        assertEquals(LicenseMode.COMMERCIAL, LicenseMode.fromEnvironment { if (it == "STOCKSTEPS_LICENSE_MODE") " Commercial " else null })
        assertTrue(LicenseGate(LicenseMode.DEVELOPMENT).permits("fmp", Dataset.QUOTES, DisplayChannel.MOBILE_APP))
        assertFalse(LicenseGate(LicenseMode.COMMERCIAL).permits("fmp", Dataset.QUOTES, DisplayChannel.MOBILE_APP))
        assertTrue(LicenseGate(LicenseMode.COMMERCIAL).permits("sec", Dataset.REPORTED_FUNDAMENTALS, DisplayChannel.WEBSITE))
        val summary = LicenseGate(LicenseMode.DEVELOPMENT).summary()
        assertTrue("fmp:quotes" in summary && "sec:" !in summary && "apikey" !in summary, summary)
    }

    @Test fun orderIsDeterministicByLicenseFreshnessCompletenessCostThenName() {
        val s = SourceSelector(LicenseGate(LicenseMode.DEVELOPMENT))
        val c = listOf(src("zeta", fresh = 1), src("fmp", fresh = 1, complete = 90), src("finnhub", Dataset.QUOTES), src("alpha", fresh = 1),
            src("sec", Dataset.REPORTED_FUNDAMENTALS, fresh = 3))
        val order = s.order(c).map { it.provider }
        assertEquals("sec", order.first(), "the verified source ranks first")
        assertEquals(order, s.order(c.reversed()).map { it.provider })
    }

    @Test fun fallbackNeverCrossesModesLicencesOrAdjustmentBases() = runBlocking {
        val commercial = SourceSelector(LicenseGate(LicenseMode.COMMERCIAL))
        val tried = mutableListOf<String>()
        val r = commercial.select(listOf(src("fmp"), src("mock", mode = DataMode.MOCK)), SourceRequirement(DataMode.REAL)) { tried += it.provider; value(it.provider) }
        assertNull(r.result, "nothing cleared → nothing shown, and never MOCK for REAL")
        assertTrue(tried.isEmpty())
        assertEquals(setOf(SkipReason.NOT_LICENSED, SkipReason.WRONG_MODE), r.attempts.map { it.reason }.toSet())

        val dev = SourceSelector(LicenseGate(LicenseMode.DEVELOPMENT))
        val b = dev.select(listOf(src("a", basis = AdjustmentBasis.UNVERIFIED), src("b", basis = AdjustmentBasis.SPLIT_ADJUSTED)),
            SourceRequirement(DataMode.REAL, adjustment = AdjustmentBasis.SPLIT_ADJUSTED)) { value(it.provider) }
        assertEquals("b", b.result!!.provenance.provider)
        assertEquals(SkipReason.ADJUSTMENT_MISMATCH, b.attempts.first { it.provider == "a" }.reason)
    }

    @Test fun refusalsAreTriedOnceAndStaleIsKeptOnlyAsALabelledLastResort() = runBlocking {
        val dev = SourceSelector(LicenseGate(LicenseMode.DEVELOPMENT))
        val calls = mutableMapOf<String, Int>()
        val r = dev.select(listOf(src("a", fresh = 0), src("b", fresh = 1), src("c", fresh = 2), src("d", fresh = 3)), SourceRequirement(DataMode.REAL)) {
            calls.merge(it.provider, 1, Int::plus)
            when (it.provider) {
                "a" -> throw StockProviderException(StockProviderException.Failure.UNAVAILABLE, 402)
                "b" -> throw StockProviderException(StockProviderException.Failure.RATE_LIMITED, 429)
                "c" -> value("c", stale = true)
                else -> null
            }
        }
        assertEquals(mapOf("a" to 1, "b" to 1, "c" to 1, "d" to 1), calls, "one attempt each; no retry loop")
        assertEquals("c", r.result!!.provenance.provider); assertTrue(r.result!!.provenance.stale, "stale stays labelled")
        assertEquals(listOf(SkipReason.RESTRICTED, SkipReason.RATE_LIMITED, SkipReason.STALE_KEPT_LOOKING, SkipReason.NO_DATA), r.attempts.map { it.reason })

        val fresh = dev.select(listOf(src("c", fresh = 0), src("d", fresh = 1)), SourceRequirement(DataMode.REAL)) { if (it.provider == "c") value("c", stale = true) else value("d") }
        assertEquals("d", fresh.result!!.provenance.provider); assertFalse(fresh.result!!.provenance.stale)
    }

    @Test fun provenanceCannotBeRelabelledByAFallback() = runBlocking {
        val dev = SourceSelector(LicenseGate(LicenseMode.DEVELOPMENT))
        val failure = runCatching { dev.select(listOf(src("a")), SourceRequirement(DataMode.REAL)) { value("someone-else") } }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }
}

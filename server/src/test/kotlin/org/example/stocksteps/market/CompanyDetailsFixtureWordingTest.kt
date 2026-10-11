package org.example.stocksteps.market

import kotlinx.coroutines.runBlocking
import org.example.stocksteps.companydetail.CompanyOverview
import org.example.stocksteps.companydetail.CompanyOverviewPresenter
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.CompanyDetailsService
import org.example.stocksteps.service.CompanyFinancialService
import org.example.stocksteps.service.StockService
import kotlin.test.*

/**
 * Global UI Refinement Phase 4C.3: the MOCK fixtures both apps show (`/details` payloads) run through the shared Company Details presenter,
 * so the beginner wording is checked on real fixture shapes — a loss maker (RIVN, BB.TO), a thin margin (LONGN), a company with no
 * fundamentals (SSHC.TO) and a profitable one (AAPL). Fixture-only: no provider, AI or Firebase calls.
 */
class CompanyDetailsFixtureWordingTest {
    private val fixtures = FixtureMarketDataSource()
    private val details = CompanyDetailsService(StockService(fixtures, fixtures), CompanyFinancialService(fixtures), fixtures)

    private fun overview(symbol: String): CompanyOverview = runBlocking { CompanyOverviewPresenter.build(details.getDetails(symbol)) }

    /** Every sentence a beginner reads about the company's finances. */
    private fun sentences(o: CompanyOverview) =
        (o.assessment.mapNotNull { it.explanation } + listOfNotNull(o.insight, o.valuation?.explanation) + o.highlights.mapNotNull { it.helper })

    @Test fun lossMakersSayLostNeverNegativeDollars() {
        val rivn = overview("RIVN")
        assertEquals("Rivian Automotive lost about $80 for every $100 in revenue.", rivn.assessment.first { it.id == "profitability" }.explanation)
        assertEquals("Losing money", rivn.assessment.first { it.id == "profitability" }.badge)
        assertTrue(rivn.insight!!.contains("It lost about $80 for every $100 in revenue."), rivn.insight)
        // Negative earnings: P/E is "N/A" with its reason, never 0.
        assertEquals("N/A", rivn.glance.first { it.id == "pe" }.value)
        val bb = overview("BB.TO")
        assertTrue(bb.insight!!.contains("revenue fell 3.0%") && bb.insight!!.contains("lost about $15 for every $100"), bb.insight)
        listOf(rivn, bb).flatMap(::sentences).forEach { assertFalse(it.contains("$-") || it.contains("kept about $-"), it) }
    }

    @Test fun thinMarginAndProfitableCompaniesKeepTheirWording() {
        assertEquals("Apple kept about $32 of every $100 in revenue as profit.", overview("AAPL").assessment.first { it.id == "profitability" }.explanation)
        val longn = overview("LONGN")
        assertEquals("Thin margin", longn.assessment.first { it.id == "profitability" }.badge)
        assertTrue(longn.assessment.first { it.id == "profitability" }.explanation!!.contains("kept about $2 of every $100"))
    }

    @Test fun missingFundamentalsAreNeverShownAsZero() {
        val o = overview("SSHC.TO")
        assertNull(o.assessment.firstOrNull { it.id == "profitability" }, "no margin, no profitability row")
        assertNull(o.insight, "no facts, no insight")
        val glance = o.glance.associate { it.id to it.value }
        assertEquals("—", glance["revenueGrowth"])
        assertTrue(o.glance.none { it.value.startsWith("0") || it.value.startsWith("$0") }, o.glance.toString())
        sentences(o).forEach { assertFalse(it.contains("$0"), it) }
    }
}

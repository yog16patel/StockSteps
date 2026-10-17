package org.example.stocksteps.marketdata

import org.example.stocksteps.marketdata.FinancialConcept.*
import org.example.stocksteps.marketdata.sec.RestatementPolicy
import org.example.stocksteps.marketdata.sec.SecCompanyFactsParser
import org.example.stocksteps.marketdata.sec.SecEdgarClient
import org.example.stocksteps.marketdata.sec.SecPeriodBuilder
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 9B: SEC companyfacts parsing, fiscal periods, restatements, YTD handling and Q4 derivation (fixtures only, no network). */
class SecEdgarParsingTest {
    private val at = Instant.parse("2026-05-16T00:00:00Z")
    private fun growth(policy: RestatementPolicy = RestatementPolicy.LATEST_FILED) =
        SecPeriodBuilder.build("EXGC", SecCompanyFactsParser.parse(SecFixtures.json(1001, "Example Growth Co", SecFixtures.growthCo)), at, policy)
    private fun d(s: String) = LocalDate.parse(s)
    private fun bd(s: String) = BigDecimal(s)

    @Test fun parsesOnlyUsedTagsAndPeriodicFormsAndCountsTheRest() {
        val facts = SecCompanyFactsParser.parse(SecFixtures.json(1001, "Example Growth Co", SecFixtures.growthCo))
        assertEquals(1001L, facts.cik); assertEquals("Example Growth Co", facts.entityName)
        assertTrue(facts.facts.none { it.form == "8-K" })
        assertEquals(1, facts.skipped)  // the 8-K row
    }

    @Test fun malformedEntriesAreSkippedNotGuessed() {
        val raw = ""","Revenues":{"units":{"USD":[{"end":"2025-12-31","val":"12","accn":"a","form":"10-K","filed":"2026-01-01"},
            {"end":"not-a-date","val":5,"accn":"a","form":"10-K","filed":"2026-01-01"},{"start":"2026-01-01","end":"2025-01-01","val":5,"accn":"a","form":"10-K","filed":"2026-01-01"},
            {"end":"2025-12-31","val":5,"form":"10-K","filed":"2026-01-01"}],"pure":[{"end":"2025-12-31","val":1,"accn":"a","form":"10-K","filed":"2026-01-01"}]}}"""
        val facts = SecCompanyFactsParser.parse(SecFixtures.json(1001, "X", SecFixtures.growthCo, extraRaw = raw))
        assertTrue(facts.facts.none { it.tag == "us-gaap:Revenues" })
        assertEquals(1 + 4 + 1, facts.skipped)
        assertFailsWith<IllegalArgumentException> { SecCompanyFactsParser.parse("""{"cik":1}""") }
        assertFailsWith<Exception> { SecCompanyFactsParser.parse("not json") }
    }

    @Test fun annualPeriodsUseDatesAndTheEarliestFilingsFiscalYear() {
        val h = growth()
        assertEquals(listOf(2025, 2024, 2023), h.annual.map { it.fiscalYear })
        val fy24 = h.annual[1]
        // FY2024 is reported by the FY2024 10-K and again (fy = 2025) by the FY2025 10-K: still labelled 2024.
        assertEquals("FY", fy24.fiscalPeriod); assertEquals(d("2024-12-31"), fy24.end)
        assertEquals(bd("1000000000"), fy24[REVENUE]!!.value)
        assertEquals("0001001-26-000010", fy24[REVENUE]!!.sourceRef)  // latest filing reporting it
        assertFalse(fy24[REVENUE]!!.restated)
        assertEquals("us-gaap:RevenueFromContractWithCustomerExcludingAssessedTax", fy24[REVENUE]!!.concept)
    }

    @Test fun yearToDateValuesAreNeverQuartersAndQ4IsDerivedForFlowsOnly() {
        val h = growth()
        assertTrue(h.quarterly.all { (it.days ?: 0) in 84..98 })
        val labels = h.quarterly.map { "${it.fiscalPeriod}${it.fiscalYear}" }
        assertEquals(listOf("Q12026", "Q42025", "Q32025", "Q22025", "Q12025"), labels)
        val q4 = h.quarterly[1]
        assertEquals(d("2025-10-01"), q4.start)
        assertEquals(bd("350000000"), q4[REVENUE]!!.value); assertTrue(q4[REVENUE]!!.derived)
        // Net income Q4 uses the restated FY value (140) less the 9-month YTD (105).
        assertEquals(bd("35000000"), q4[NET_INCOME]!!.value)
        assertNull(q4[EPS_DILUTED], "per-share values are never derived by the SEC adapter")
        assertNull(q4[DIVIDENDS_PER_SHARE])
        assertEquals(bd("600000000"), q4[EQUITY]!!.value, "year-end balance sheet belongs to Q4")
        assertEquals(bd("300000000"), h.quarterly[2][REVENUE]!!.value, "Q3 is the discrete quarter, not the 9-month YTD")
    }

    @Test fun restatementsUseTheLatestFilingAndKeepTheOriginal() {
        val ni = growth().annual[0][NET_INCOME]!!
        assertEquals(bd("140000000"), ni.value); assertEquals(bd("150000000"), ni.originalValue)
        assertTrue(ni.restated); assertTrue(ni.amended); assertEquals("10-K/A", ni.form)
        val original = growth(RestatementPolicy.ORIGINAL).annual[0][NET_INCOME]!!
        assertEquals(bd("150000000"), original.value); assertFalse(original.amended)
    }

    @Test fun reportingCurrencyIsChosenAndOtherCurrenciesExcluded() {
        val h = SecPeriodBuilder.build("EXLC", SecCompanyFactsParser.parse(SecFixtures.json(1002, "Example Loss Co", SecFixtures.lossCo)), at)
        assertEquals("USD", h.annual[0].currency)
        assertEquals("us-gaap:RevenueFromContractWithCustomerExcludingAssessedTax", h.annual[0][REVENUE]!!.concept)
        assertEquals("us-gaap:SalesRevenueNet", h.annual[1][REVENUE]!!.concept)
    }

    @Test fun severalShareClassesOnOneCoverDateAreAmbiguous() {
        val h = SecPeriodBuilder.build("EXLC", SecCompanyFactsParser.parse(SecFixtures.json(1002, "Example Loss Co", SecFixtures.lossCo)), at)
        assertEquals(FactIssue.AMBIGUOUS, h.sharesOutstandingIssue)
        assertTrue(h.sharesOutstanding.isEmpty())
        val g = growth()
        assertNull(g.sharesOutstandingIssue)
        assertEquals(d("2026-04-20") to bd("50000000"), g.sharesOutstanding.first().let { it.first to it.second.value })
    }

    @Test fun provenanceNamesSecTheLatestPeriodAndFiling() {
        val p = growth().provenance
        assertEquals("sec", p.provider); assertEquals(Dataset.REPORTED_FUNDAMENTALS, p.dataset)
        assertEquals("2026-03-31", p.asOf); assertEquals("0001001-26-000030", p.sourceRef); assertEquals(at, p.retrievedAt)
    }

    @Test fun mapsToTheExistingStatementContract() {
        val row = SecEdgarClient.toStatement(growth().annual[0])
        assertEquals("FY", row.period); assertEquals(2025, row.fiscalYear); assertEquals("2025-12-31", row.date); assertEquals("USD", row.currency)
        assertEquals(1.2e9, row.revenue); assertEquals(1.4e8, row.netIncome); assertEquals(3.0, row.epsDiluted)
        assertEquals(2.0e8, row.freeCashFlow); assertEquals(6.0e8, row.equity)
        val q4 = SecEdgarClient.toStatement(growth().quarterly[1], includeDerived = false)
        assertNull(q4.revenue, "derived values can be left out of the contract")
    }

    @Test fun parsesTickerAndSubmissionFiles() {
        val map = SecEdgarClient.parseTickers(SecFixtures.tickersJson(Triple(1001L, "Example Growth Co", "EXGC"), Triple(1067983L, "Berkshire", "BRK-B")))
        assertEquals(1001L, map.cik("exgc")); assertEquals(1067983L, map.cik("BRK.B")); assertNull(map.cik("NOPE"))
        val subs = SecEdgarClient.parseSubmissions("""{"cik":"0001001","name":"Example Growth Co","tickers":["EXGC"],"exchanges":["Nasdaq"],"fiscalYearEnd":"1231",
            "filings":{"recent":{"accessionNumber":["a3","a2","a1"],"form":["8-K","10-Q","10-K"],"filingDate":["2026-05-01","2026-04-30","2026-02-01"],"reportDate":["","2026-03-31","2025-12-31"]}}}""")
        assertEquals(1001L, subs.cik); assertEquals("1231", subs.fiscalYearEnd)
        assertEquals("a2", subs.latestPeriodic!!.accession); assertEquals(d("2026-03-31"), subs.latestPeriodic!!.reportDate)
        assertNotNull(subs.filings.first { it.form == "8-K" }.filingDate)
    }
}

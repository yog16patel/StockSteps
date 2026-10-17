package org.example.stocksteps.marketdata

import org.example.stocksteps.marketdata.FinancialConcept.*
import org.example.stocksteps.marketdata.metrics.FinancialMetricsEngine
import org.example.stocksteps.marketdata.metrics.Metric
import org.example.stocksteps.marketdata.metrics.MetricStatus
import org.example.stocksteps.marketdata.metrics.SplitHistory
import org.example.stocksteps.marketdata.sec.SecCompanyFactsParser
import org.example.stocksteps.marketdata.sec.SecPeriodBuilder
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 9B: hand-calculated metric values and every missing-data rule (SEC-shaped fixtures; no network, no clock). */
class FinancialMetricsEngineTest {
    private val at = Instant.parse("2026-05-16T00:00:00Z")
    private val growth = SecPeriodBuilder.build("EXGC", SecCompanyFactsParser.parse(SecFixtures.json(1001, "Example Growth Co", SecFixtures.growthCo)), at)
    private val loss = SecPeriodBuilder.build("EXLC", SecCompanyFactsParser.parse(SecFixtures.json(1002, "Example Loss Co", SecFixtures.lossCo)), at)
    private fun price(v: String, date: String = "2026-05-15", delay: PriceDelay = PriceDelay.END_OF_DAY, currency: String? = "USD", basis: AdjustmentBasis = AdjustmentBasis.UNVERIFIED) =
        PriceObservation("EXGC", BigDecimal(v), currency, LocalDate.parse(date), basis, delay, Provenance("fmp", Dataset.DAILY_PRICES, at, date))
    private fun assertValue(expected: String, r: org.example.stocksteps.marketdata.metrics.MetricResult) {
        assertEquals(MetricStatus.AVAILABLE, r.status, "${r.metric}: ${r.note}")
        assertEquals(0, BigDecimal(expected).compareTo(r.value!!), "${r.metric}: expected $expected, got ${r.value}")
    }
    private fun assertStatus(s: MetricStatus, r: org.example.stocksteps.marketdata.metrics.MetricResult) {
        assertEquals(s, r.status, "${r.metric}: ${r.note}"); assertNull(r.value)
    }

    @Test fun fiscalYearMetricsMatchHandCalculations() {
        val all = FinancialMetricsEngine.all(growth)
        assertValue("20", all.getValue(Metric.REVENUE_GROWTH))               // (1200 − 1000) / 1000
        assertValue("40", all.getValue(Metric.NET_INCOME_GROWTH))            // restated 140 vs 100
        assertValue("50", all.getValue(Metric.EPS_GROWTH))                   // 3.00 vs 2.00
        assertValue("41.666667", all.getValue(Metric.GROSS_MARGIN))          // 500 / 1200
        assertValue("16.666667", all.getValue(Metric.OPERATING_MARGIN))      // 200 / 1200
        assertValue("11.666667", all.getValue(Metric.NET_MARGIN))            // 140 / 1200
        assertValue("3", all.getValue(Metric.EPS_DILUTED)); assertValue("3.05", all.getValue(Metric.EPS_BASIC))
        assertValue("0.55", all.getValue(Metric.DEBT_TO_EQUITY))             // (300 + 30) / 600
        assertValue("1.5", all.getValue(Metric.CURRENT_RATIO))               // 450 / 300
        assertValue("25.454545", all.getValue(Metric.RETURN_ON_EQUITY))      // 140 / ((500 + 600) / 2)
        assertValue("10.769231", all.getValue(Metric.RETURN_ON_ASSETS))      // 140 / ((1200 + 1400) / 2)
        assertValue("200000000", all.getValue(Metric.FREE_CASH_FLOW))        // 260 − 60
        val inputs = all.getValue(Metric.NET_INCOME_GROWTH).inputs
        assertTrue(inputs.first().restated && inputs.first().sourceRef == "0001001-26-000020", "provenance of the restated input is kept")
    }

    @Test fun ttmUsesFourDiscreteQuartersAndNeverYearToDate() {
        val rev = FinancialMetricsEngine.ttm(growth, REVENUE)
        assertEquals(0, BigDecimal("1270000000").compareTo(rev.value!!))     // 320 + 350 (FY − 9M) + 300 + 300
        assertEquals(LocalDate.parse("2026-03-31"), rev.end); assertEquals(LocalDate.parse("2025-04-01"), rev.start)
        assertTrue(!rev.approximate && rev.inputs.any { it.derived })
        val eps = FinancialMetricsEngine.epsTtm(growth)
        assertValue("3.25", eps)                                              // 0.85 + (3.00 − 0.6 − 0.7 − 0.8) + 0.80 + 0.70
        assertTrue(eps.approximate)
    }

    @Test fun ttmIsTheFiscalYearWhenNoQuarterIsNewer() {
        val fyOnly = growth.copy(quarterly = growth.quarterly.filter { it.end <= LocalDate.parse("2025-12-31") })
        assertEquals(0, BigDecimal("1200000000").compareTo(FinancialMetricsEngine.ttm(fyOnly, REVENUE).value!!))
        val gap = growth.copy(quarterly = growth.quarterly.filter { it.fiscalPeriod != "Q3" })
        assertStatus(MetricStatus.PERIOD_MISMATCH, FinancialMetricsEngine.epsTtm(gap))
        val short = growth.copy(annual = emptyList(), quarterly = growth.quarterly.take(3))
        assertStatus(MetricStatus.INSUFFICIENT_HISTORY, FinancialMetricsEngine.epsTtm(short))
    }

    @Test fun priceRatiosMatchHandCalculations() {
        val p = price("65.00")
        assertValue("20", FinancialMetricsEngine.pe(growth, p))                         // 65 / 3.25
        assertValue("2.559055", FinancialMetricsEngine.ps(growth, p))                   // 65 × 50 M / 1,270 M
        assertValue("1.615385", FinancialMetricsEngine.dividendYield(growth, p))        // (0.30 + 0.25 + 0.25 + 0.25) / 65
        assertTrue(FinancialMetricsEngine.pe(growth, p).note!!.contains("Split history not verified"))
    }

    @Test fun priceRatiosRefuseUnusablePrices() {
        assertStatus(MetricStatus.PRICE_UNUSABLE, FinancialMetricsEngine.pe(growth, price("65", delay = PriceDelay.STALE)))
        assertStatus(MetricStatus.PRICE_UNUSABLE, FinancialMetricsEngine.pe(growth, price("65", delay = PriceDelay.UNKNOWN)))
        assertStatus(MetricStatus.PRICE_UNUSABLE, FinancialMetricsEngine.pe(growth, price("0")))
        assertStatus(MetricStatus.CURRENCY_MISMATCH, FinancialMetricsEngine.pe(growth, price("65", currency = "CAD")))
        assertStatus(MetricStatus.CURRENCY_MISMATCH, FinancialMetricsEngine.pe(growth, price("65", currency = null)))
        assertStatus(MetricStatus.PERIOD_MISMATCH, FinancialMetricsEngine.pe(growth, price("65", date = "2027-03-01")), )
        assertStatus(MetricStatus.PERIOD_MISMATCH, FinancialMetricsEngine.pe(growth, price("65", date = "2026-03-01")))
    }

    @Test fun lossesAndBadDenominatorsAreNullWithAReason() {
        val all = FinancialMetricsEngine.all(loss, price("10", date = "2026-02-20"))
        assertStatus(MetricStatus.UNRELIABLE_COMPARISON, all.getValue(Metric.REVENUE_GROWTH))   // SalesRevenueNet → RevenueFromContract…
        assertStatus(MetricStatus.NON_POSITIVE_DENOMINATOR, all.getValue(Metric.NET_INCOME_GROWTH))
        assertStatus(MetricStatus.NON_POSITIVE_DENOMINATOR, all.getValue(Metric.EPS_GROWTH))
        assertStatus(MetricStatus.NOT_MEANINGFUL, all.getValue(Metric.PE_RATIO))                 // never a negative P/E
        assertStatus(MetricStatus.AMBIGUOUS, all.getValue(Metric.PS_RATIO))                      // two share classes
        assertStatus(MetricStatus.NON_POSITIVE_DENOMINATOR, all.getValue(Metric.DEBT_TO_EQUITY)) // negative equity
        assertStatus(MetricStatus.MISSING_INPUT, all.getValue(Metric.FREE_CASH_FLOW))            // no capex reported ≠ zero capex
        assertStatus(MetricStatus.MISSING_INPUT, all.getValue(Metric.CURRENT_RATIO))
        assertStatus(MetricStatus.MISSING_INPUT, all.getValue(Metric.GROSS_MARGIN))
        assertStatus(MetricStatus.MISSING_INPUT, all.getValue(Metric.RETURN_ON_EQUITY))          // no opening equity
        assertStatus(MetricStatus.MISSING_INPUT, all.getValue(Metric.DIVIDEND_YIELD))            // no dividend facts ≠ no dividend
        assertValue("-6.666667", all.getValue(Metric.NET_MARGIN))                                // −60 / 900: a negative margin is valid
    }

    @Test fun grossProfitFallsBackToRevenueLessCostOfRevenueOnlyWhenBothAreReported() {
        val fy = growth.annual[0]
        val noGross = fy.copy(values = fy.values - GROSS_PROFIT + (COST_OF_REVENUE to fy[REVENUE]!!.copy(value = BigDecimal("900000000"), concept = "us-gaap:CostOfRevenue")))
        val r = FinancialMetricsEngine.margin(Metric.GROSS_MARGIN, GROSS_PROFIT, noGross)
        assertValue("25", r); assertTrue(r.note!!.contains("revenue − cost of revenue"))
        val zeroRevenue = fy.copy(values = fy.values + (REVENUE to fy[REVENUE]!!.copy(value = BigDecimal.ZERO)))
        assertStatus(MetricStatus.NON_POSITIVE_DENOMINATOR, FinancialMetricsEngine.margin(Metric.NET_MARGIN, NET_INCOME, zeroRevenue))
    }

    @Test fun growthNeedsTheSameFiscalPeriodOneYearApartAndOneCurrency() {
        val (fy25, fy24, fy23) = growth.annual
        assertStatus(MetricStatus.PERIOD_MISMATCH, FinancialMetricsEngine.growth(Metric.REVENUE_GROWTH, REVENUE, fy25, fy23))
        assertStatus(MetricStatus.PERIOD_MISMATCH, FinancialMetricsEngine.growth(Metric.REVENUE_GROWTH, REVENUE, fy25, growth.quarterly[1]))
        assertStatus(MetricStatus.CURRENCY_MISMATCH, FinancialMetricsEngine.growth(Metric.REVENUE_GROWTH, REVENUE, fy25, fy24.copy(currency = "CAD")))
        assertStatus(MetricStatus.INSUFFICIENT_HISTORY, FinancialMetricsEngine.growth(Metric.REVENUE_GROWTH, REVENUE, fy23, FinancialMetricsEngine.priorYear(fy23, growth.annual)))
        assertEquals(fy24, FinancialMetricsEngine.priorYear(fy25, growth.annual))
    }

    @Test fun splitsAreAdjustedOnlyWithAVerifiedHistory() {
        // A 4-for-1 split in Q1 2026: diluted shares jump 50 M → 200 M and the quarter's EPS is on the new basis.
        val q1 = growth.quarterly[0]
        val split = growth.copy(quarterly = listOf(q1.copy(values = q1.values + (DILUTED_SHARES to q1[DILUTED_SHARES]!!.copy(value = BigDecimal("200000000"))) +
            (EPS_DILUTED to q1[EPS_DILUTED]!!.copy(value = BigDecimal("0.2125"))))) + growth.quarterly.drop(1))
        assertStatus(MetricStatus.SPLIT_UNVERIFIED, FinancialMetricsEngine.pe(split, price("16.25")))
        assertStatus(MetricStatus.SPLIT_UNVERIFIED, FinancialMetricsEngine.dividendYield(split, price("16.25")))
        val verified = SplitHistory(true, listOf(SplitHistory.Split(LocalDate.parse("2026-02-15"), BigDecimal(4))))
        // EPS on the post-split basis: 0.2125 + (0.90 + 0.80 + 0.70) / 4 = 0.8125 → 16.25 / 0.8125 = 20.
        assertValue("20", FinancialMetricsEngine.pe(split, price("16.25"), verified))
        assertEquals(BigDecimal.ONE, verified.factor(LocalDate.parse("2026-03-31"), LocalDate.parse("2026-05-15")))
        assertEquals(BigDecimal(4), verified.factor(LocalDate.parse("2025-12-31"), LocalDate.parse("2026-05-15")))
    }

    @Test fun ambiguousFactsAndResultInvariant() {
        val fy = growth.annual[0]
        val amb = fy.copy(values = fy.values - CURRENT_ASSETS, issues = mapOf(CURRENT_ASSETS to FactIssue.AMBIGUOUS))
        assertStatus(MetricStatus.AMBIGUOUS, FinancialMetricsEngine.currentRatio(amb))
        assertFailsWith<IllegalArgumentException> { org.example.stocksteps.marketdata.metrics.MetricResult(Metric.PE_RATIO, BigDecimal.ONE, MetricStatus.MISSING_INPUT) }
        assertStatus(MetricStatus.PERIOD_MISMATCH, FinancialMetricsEngine.returnOn(Metric.RETURN_ON_EQUITY, growth.quarterly[0], null))
        val negCapex = fy.copy(values = fy.values + (CAPITAL_EXPENDITURE to fy[CAPITAL_EXPENDITURE]!!.copy(value = BigDecimal("-1"))))
        assertStatus(MetricStatus.NOT_MEANINGFUL, FinancialMetricsEngine.freeCashFlow(negCapex))
    }

    @Test fun resultsAreDeterministic() {
        val a = FinancialMetricsEngine.all(growth, price("65.00")); val b = FinancialMetricsEngine.all(growth, price("65.00"))
        assertEquals(a, b)
        // The Phase 9B.8 trailing-dividend metrics have their own entry points (dividend records, not periods).
        assertTrue(Metric.entries.filter { it != Metric.DIVIDEND_PER_SHARE_TTM && it != Metric.DIVIDEND_YIELD_TRAILING }.all { it in a.keys })
    }
}

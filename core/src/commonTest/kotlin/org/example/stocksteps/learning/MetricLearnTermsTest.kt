package org.example.stocksteps.learning

import org.example.stocksteps.companydetail.MetricEducation
import kotlin.test.*

/** Phase 5D.5.3: metric → Learn term mapping (ids only; deterministic). */
class MetricLearnTermsTest {
    /** The full mapping for every metric Company Details shows; a change here must be deliberate. */
    private val expected = mapOf(
        "marketCap" to "marketCap", "pe" to "pe", "eps" to "eps", "revenue" to "revenue", "revenueGrowth" to "revenueGrowth",
        "netIncome" to "netIncome", "netMargin" to "netMargin", "freeCashFlow" to "freeCashFlow", "debt" to "debt", "cash" to "cash",
        "debtEquity" to "debtEquity", "dividendYield" to "dividendYield"
    )
    private val noTerm = listOf("forwardPe", "peg", "priceSales", "priceBook", "priceFcf", "evEbitda", "epsGrowth", "grossMargin", "operatingMargin",
        "roe", "roic", "currentRatio", "interestCoverage", "operatingCashFlow", "capex", "fcfMargin", "payoutRatio", "yearRangePosition", "enterpriseValue")

    @Test fun knownMetricsResolveToTheirTerm() {
        expected.forEach { (metric, term) -> assertEquals(term, MetricLearnTerms.termIdFor(metric), metric) }
    }

    @Test fun ambiguousCashResolvesToItsOwnTermNotDebt() {
        // "cash" is listed by both the cash and debt terms; the exact id wins.
        assertTrue("cash" in BeginnerEducation.entry("debt")!!.relatedMetrics)
        assertEquals("cash", MetricLearnTerms.termIdFor("cash"))
    }

    @Test fun metricsWithoutATermAndUnknownIdsReturnNull() {
        noTerm.forEach { assertNull(MetricLearnTerms.termFor(it), it) }
        assertNull(MetricLearnTerms.termFor("beta"))
        assertNull(MetricLearnTerms.termFor(""))
        assertNull(MetricLearnTerms.termFor("P/E"))       // a visible title is never a key
        assertFalse(MetricLearnTerms.hasTerm("roe"))
    }

    @Test fun everyMappedTermExistsAndEveryRelatedMetricIsAKnownMetricOrDocumented() {
        expected.values.forEach { assertNotNull(BeginnerEducation.entry(it), it) }
        // Every metric id Company Details can show and that resolves must be a real MetricEducation id.
        expected.keys.forEach { assertNotNull(MetricEducation.find(it), it) }
        // relatedMetrics that are not displayed metrics (e.g. netDebt) never map to anything by themselves.
        assertNull(MetricEducation.find("netDebt")); assertEquals("debt", MetricLearnTerms.termIdFor("netDebt"))
    }

    @Test fun mappingIsStableAcrossTheWholeMetricCatalogue() {
        val ids = listOf("marketCap", "pe", "forwardPe", "revenueGrowth", "quarterRevenueGrowth", "epsGrowth", "roic", "revenue", "netIncome", "eps",
            "grossMargin", "operatingMargin", "netMargin", "roe", "cash", "debt", "debtEquity", "interestCoverage", "operatingCashFlow", "capex",
            "freeCashFlow", "dividendYield", "dividendGrowth", "payoutRatio", "yearRangePosition", "price", "volume")
        val mapped = ids.mapNotNull { id -> MetricLearnTerms.termIdFor(id)?.let { id to it } }.toMap()
        assertEquals(expected, mapped)
        assertEquals(mapped, ids.mapNotNull { id -> MetricLearnTerms.termIdFor(id)?.let { id to it } }.toMap())
    }

    @Test fun labelsUseTheTermName() {
        assertEquals("Learn more about P/E ratio", MetricLearnTerms.learnMoreLabel(BeginnerEducation.entry("pe")!!))
        assertEquals("Learn more about market capitalization", MetricLearnTerms.learnMoreLabel(BeginnerEducation.entry("marketCap")!!))
        assertEquals("Explain P/E Ratio", MetricLearnTerms.explainLabel("P/E Ratio"))
    }

    @Test fun everyTaggedRowIdHasAnExplanationAndOnlyMappedOnesALearnLink() {
        // Ids tagged on Financials and Valuation rows (FinancialStatementsPresentation, ValuationPresentation).
        val tagged = listOf("netIncome", "netMargin", "operatingMargin", "grossMargin", "operatingCashFlow", "capex", "freeCashFlow", "cash", "debt", "assets",
            "liabilities", "equity", "currentRatio", "debtEquity", "interestCoverage", "dividendPerShare", "dividendYield", "payoutRatio", "roe", "roa", "roic",
            "revenueCagr3", "revenueCagr5", "epsCagr3", "epsCagr5", "priceSales", "priceBook", "evEbitda", "peg", "pe", "revenueGrowth", "epsGrowth")
        tagged.forEach { id -> assertNotNull(MetricLearnTerms.explain(id), id) }
        assertEquals(expected.keys.filter { it in tagged }.toSet(), tagged.filter { MetricLearnTerms.explain(it)!!.term != null }.toSet())
        val pe = MetricLearnTerms.explain("pe", "P/E Ratio")!!
        assertEquals("P/E Ratio", pe.title); assertEquals("Learn more about P/E ratio", pe.learnMore)
        assertEquals(MetricEducation.metric("pe").explanation, pe.explanation)
        assertNull(MetricLearnTerms.explain("roe")!!.learnMore)
        assertNull(MetricLearnTerms.explain("beta"))
    }
}

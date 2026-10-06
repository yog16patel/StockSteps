package org.example.stocksteps

import kotlinx.coroutines.*
import org.example.stocksteps.model.FinancialObservation
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.FinancialCalculations as Calc
import kotlin.test.*

class FinancialCalculationsTest {
    @Test fun growthHandlesLossesZerosAndInvalidInputs() {
        assertEquals(20.0, Calc.yoy(120.0, 100.0))
        assertEquals(50.0, Calc.yoy(-50.0, -100.0))
        assertEquals(200.0, Calc.yoy(100.0, -100.0))
        assertNull(Calc.yoy(100.0, 0.0))
        assertNull(Calc.yoy(Double.NaN, 10.0))
        assertNull(Calc.yoy(10.0, Double.POSITIVE_INFINITY))
        assertNull(Calc.yoy(null, 100.0))
        assertEquals(10.0, Calc.cagr(133.1, 100.0, 3)!!, 1e-8)
        assertEquals(10.0, Calc.cagr(161.051, 100.0, 5)!!, 1e-8)
        assertNull(Calc.cagr(-100.0, -50.0, 5))
        assertNull(Calc.cagr(100.0, 0.0, 5))
        assertNull(Calc.cagr(100.0, 100.0, 0))
    }
    @Test fun freeCashFlowPreservesProviderAndOutflowConvention() {
        assertEquals(80.0, Calc.freeCashFlow(null, 100.0, -20.0))
        assertEquals(90.0, Calc.freeCashFlow(90.0, 100.0, -20.0))
        assertNull(Calc.freeCashFlow(null, 100.0, 20.0))
        assertNull(Calc.freeCashFlow(null, null, -20.0))
        assertEquals(40.0, Calc.margin(80.0, 200.0))
        assertEquals(-10.0, Calc.margin(-20.0, 200.0))
        assertNull(Calc.margin(100.0, 0.0))
    }
    @Test fun historicalWindowPreservesGapsAndFlagsDispersion() {
        val full = (2021..2025).map { FinancialObservation(it, "$it-12-31", 20.0) }
        val result = Calc.historical(30.0, full)
        assertEquals(20.0, result.average)
        assertEquals(50.0, result.differencePercent)
        assertEquals(5, result.validCount)
        assertTrue(result.reliable)
        val partial = Calc.historical(30.0, full.mapIndexed { i, row -> row.copy(value = if (i < 2) null else row.value) })
        assertEquals(3, partial.validCount)
        assertContains(partial.note!!, "3 valid")
        assertTrue(partial.reliable)
        assertNull(Calc.historical(30.0, full.take(2)).average)
        val extreme = Calc.historical(30.0, full.mapIndexed { i, row -> row.copy(value = if (i == 0) 1000.0 else row.value) })
        assertFalse(extreme.reliable)
        assertNull(extreme.differencePercent)
        assertEquals(1000.0, extreme.maximum)
        assertEquals(0, Calc.historical(10.0, full.map { it.copy(value = -1.0) }).validCount)
    }
    @Test fun cacheCoalescesAndExpiresWithoutMixingSymbols() = runBlocking {
        var time = 0L
        var calls = 0
        val cache = CompanyFinancialCache(now = { time })
        suspend fun load(symbol: String) = cache.getOrLoad(symbol, 100) { calls++; delay(10); symbol }
        coroutineScope { List(8) { async { load("AAPL") } }.awaitAll() }
        assertEquals(1, calls)
        assertEquals("MSFT", load("MSFT"))
        assertEquals(2, calls)
        time = 100
        load("AAPL")
        assertEquals(3, calls)
    }
}

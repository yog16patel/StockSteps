package org.example.stocksteps.market

import kotlinx.coroutines.runBlocking
import org.example.stocksteps.mockDataSources
import org.example.stocksteps.repository.Statement
import org.example.stocksteps.repositoryImpl.fixture.FixtureMarketDataSource
import org.example.stocksteps.service.ProviderUsageMeter
import kotlin.test.*

/**
 * Financial API Phase 3 in MOCK: the new accessors (statement history, screener set) are served from fixtures with no
 * provider client, earnings-aware refresh is off, and nothing reaches the upstream counters.
 */
class Phase3MockSeparationTest {
    @Test fun phase3AccessorsUseFixturesOnly() = runBlocking {
        val before = ProviderUsageMeter.shared.count(event = "upstream")
        val sources = mockDataSources()
        assertNull(sources.statementSignals, "no earnings-aware statement refresh in MOCK")
        val fixture = sources.stockProvider as FixtureMarketDataSource
        val symbol = "AAPL"
        val full = fixture.getFundamentals(symbol, "quarter")
        val history = fixture.statementHistory(symbol, "quarter", setOf(Statement.INCOME))
        assertEquals(full.history, history.rows)
        assertEquals(fixture.getFundamentals(symbol, "annual"), fixture.screenerFundamentals(symbol, "USD"))
        assertNull(full.freshness, "fixtures are never labelled stale")
        assertEquals(before, ProviderUsageMeter.shared.count(event = "upstream"), "zero provider requests")
    }
}

package org.example.stocksteps.tools

import java.nio.file.Files
import java.time.Instant
import kotlin.test.*

class FixtureImporterTest {
    @Test fun fmpQuoteBecomesPublicStockQuoteFixture() {
        val root = Files.createTempDirectory("fixtures").toFile()
        val files = FixtureImporter(root) { Instant.parse("2026-10-07T12:00:00Z") }.import(
            "fmp-quote",
            """[{"symbol":"aapl","name":"Apple Inc.","price":331.85,"changePercentage":-1.87,"change":-6.33,"volume":28718014.9,"marketCap":4874072686740,"open":333.13}]"""
        )
        val quote = files.single().readText()
        assertTrue(files.single().path.endsWith("stocks/AAPL/quote.json"))
        assertTrue(quote.contains("\"changePercent\": -1.87") && quote.contains("\"volume\": 28718014"))
        assertFalse(quote.contains("open"))
        assertTrue(root.resolve("manifest.json").readText().contains("2026-10-07T12:00:00Z"))
        assertFailsWith<IllegalArgumentException> {
            FixtureImporter(root).import("fmp-quote", """[{"symbol":"AAPL","price":-1}]""")
        }
        root.deleteRecursively()
    }
}

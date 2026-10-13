package org.example.stocksteps.repositoryImpl.fixture

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * MOCK only: `fixtures/stocks/catalog.json`, US companies without captured fixtures. Names, exchanges and classifications are real;
 * price, share count, net margin, P/E and dividend yield are simulated anchors, so [SampleMarketData] generates a plausible, internally
 * consistent quote, profile and statements for each (latest EPS = price ÷ P/E). A negative [netMargin] is a loss-making sample: no P/E
 * ([pe] null), sized by [priceSales]. Captured fixtures always win over the catalog.
 */
@Serializable
data class SampleCompany(
    val symbol: String,
    val name: String,
    val exchange: String,
    val sector: String,
    val industry: String,
    val price: Double,
    val sharesBillions: Double,
    val netMargin: Double,
    val pe: Double? = null,
    val priceSales: Double? = null,
    val dividendYield: Double = 0.0
) {
    val marketCap: Double get() = price * sharesBillions * 1_000_000_000
    /** Price-to-sales that makes the latest net income match [pe] (or the stated [priceSales] for a loss). */
    val impliedPriceSales: Double get() = pe?.let { it * netMargin / 100 } ?: priceSales ?: 2.0
}

object SampleCompanyCatalog {
    @Serializable private data class File(val description: String, val companies: List<SampleCompany>)

    /** Keyed by upper-case symbol; empty when the fixture is absent. */
    val companies: Map<String, SampleCompany> by lazy {
        val text = SampleCompanyCatalog::class.java.classLoader.getResource("fixtures/stocks/catalog.json")?.readText() ?: return@lazy emptyMap()
        Json { ignoreUnknownKeys = true }.decodeFromString(File.serializer(), text).companies.associateBy { it.symbol.uppercase(Locale.ROOT) }
    }
}

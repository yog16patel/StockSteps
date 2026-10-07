package org.example.stocksteps.tools

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import org.example.stocksteps.model.MarketMover
import org.example.stocksteps.model.MarketSnapshot
import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.model.StockQuote
import org.example.stocksteps.repository.models.FmpMarketHours
import org.example.stocksteps.repository.models.FmpQuote
import org.example.stocksteps.repository.models.FmpSnapshotMover
import org.example.stocksteps.repository.models.toMarketStatus
import org.example.stocksteps.repository.models.toSnapshotMover
import org.example.stocksteps.repository.models.toStockQuote
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.time.Instant

/**
 * Dev tool (test source set, never shipped): converts raw provider JSON (e.g. documentation
 * examples or a manual API call) into MOCK fixtures using the backend's own mappers, so
 * fixtures get the same normalization as live responses.
 *
 *   ./gradlew :server:importFixture -Pkind=fmp-quote -Pinput=/path/to/quote.json
 */
fun main(args: Array<String>) {
    val (kind, inputPath, outputRoot) = args.toList().also { require(it.size == 3) { "usage: <kind> <input> <fixturesDir>" } }
    val input = File(inputPath)
    if (!input.isFile) {
        System.err.println("Input file not found: ${input.absolutePath}\nSave the provider response there first, or pass -Pinput=/full/path/to/file.json")
        kotlin.system.exitProcess(1)
    }
    val written = FixtureImporter(File(outputRoot)).import(kind, input.readText())
    written.forEach { println("saved  ${it.relativeTo(File(outputRoot))}") }
}

class FixtureImporter(private val root: File, private val now: () -> Instant = Instant::now) {
    private val providerJson = Json { ignoreUnknownKeys = true }
    private val fixtureJson = Json { prettyPrint = true }

    fun import(kind: String, raw: String): List<File> = when (kind) {
        "fmp-quote" -> providerJson.decodeFromString(ListSerializer(FmpQuote.serializer()), raw).map { quote ->
            // Same validation as FmpStockProviderRepositoryImpl.loadQuote.
            val symbol = quote.symbol.trim().uppercase()
            require(symbol.matches(SYMBOL)) { "Invalid symbol: ${quote.symbol}" }
            require(quote.price?.let { it.isFinite() && it >= 0.0 } != false) { "Invalid price for $symbol" }
            write("stocks/$symbol/quote.json", StockQuote.serializer(), quote.toStockQuote().copy(symbol = symbol))
        }
        // Movers use the snapshot mapper and the same ordering as FmpMarketDataProvider.
        "fmp-gainers" -> listOf(updateSnapshot { it.copy(gainers = movers(raw).sortedByDescending(MarketMover::changePercent)) })
        "fmp-losers" -> listOf(updateSnapshot { it.copy(losers = movers(raw).sortedBy(MarketMover::changePercent)) })
        "fmp-most-actives" -> listOf(updateSnapshot { it.copy(mostActive = movers(raw).sortedByDescending(MarketMover::volume)) })
        "fmp-market-hours" -> {
            val hours = providerJson.decodeFromString(ListSerializer(FmpMarketHours.serializer()), raw)
            // The backend reads the NYSE entry; a response for another exchange is accepted for mocks.
            val entry = hours.firstOrNull { it.exchange.equals("NYSE", true) } ?: hours.single()
            listOf(updateSnapshot { it.copy(marketStatus = entry.toMarketStatus()) })
        }
        "fmp-news" -> listOf(write("news/market.json", ListSerializer(NewsArticle.serializer()),
            mergeNews(providerJson.decodeFromString(ListSerializer(FmpNewsArticle.serializer()), raw).mapNotNull { it.toNewsArticle() })))
        else -> error("Unknown kind '$kind'. Supported: fmp-quote, fmp-gainers, fmp-losers, fmp-most-actives, fmp-market-hours, fmp-news")
    }.also { ensureManifest() }

    private fun movers(raw: String): List<MarketMover> =
        providerJson.decodeFromString(ListSerializer(FmpSnapshotMover.serializer()), raw)
            .mapNotNull { it.toSnapshotMover() }.distinctBy { it.symbol }

    private fun updateSnapshot(change: (MarketSnapshot) -> MarketSnapshot): File {
        val file = File(root, "market/snapshot.json")
        val current = if (file.exists()) fixtureJson.decodeFromString(MarketSnapshot.serializer(), file.readText())
        else MarketSnapshot(lastUpdated = now().toString())
        return write("market/snapshot.json", MarketSnapshot.serializer(), change(current))
    }

    private fun mergeNews(incoming: List<NewsArticle>): List<NewsArticle> {
        val file = File(root, "news/market.json")
        val existing = if (file.exists()) fixtureJson.decodeFromString(ListSerializer(NewsArticle.serializer()), file.readText()) else emptyList()
        return (incoming + existing).distinctBy { it.url }.sortedByDescending { it.publishedAt }
    }

    private fun <T> write(path: String, serializer: KSerializer<T>, value: T): File =
        File(root, path).apply {
            parentFile.mkdirs()
            writeText(fixtureJson.encodeToString(serializer, value) + "\n")
        }

    /** Imported examples are not a live capture; record that, and when they were added. */
    private fun ensureManifest() {
        val manifest = File(root, "manifest.json")
        if (manifest.exists()) return
        manifest.parentFile.mkdirs()
        manifest.writeText(
            "{\n  \"capturedAt\": \"${now()}\",\n  \"source\": \"Provider response examples imported with FixtureImporter\"\n}\n"
        )
    }

    private companion object {
        val SYMBOL = Regex("[A-Z0-9][A-Z0-9.-]{0,19}")
    }
}

/**
 * FMP news shape (`publishedDate`, `publisher`, `text`). The backend's live news comes from
 * Finnhub, so this mock-only conversion has no production counterpart. FMP times are New York.
 */
@Serializable
internal data class FmpNewsArticle(
    val title: String? = null,
    val url: String? = null,
    val publisher: String? = null,
    val site: String? = null,
    val publishedDate: String? = null,
    val image: String? = null,
    val text: String? = null,
    val symbol: String? = null
) {
    fun toNewsArticle(): NewsArticle? {
        val headline = title?.takeIf { it.isNotBlank() } ?: return null
        val link = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        val published = publishedDate?.let {
            runCatching {
                LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(ZoneId.of("America/New_York")).toInstant().toString()
            }.getOrNull()
        }
        return NewsArticle(
            title = headline,
            url = link,
            symbol = symbol?.takeIf { it.isNotBlank() },
            source = publisher ?: site,
            publishedAt = published,
            imageUrl = image?.takeIf { it.startsWith("https://") },
            id = link,
            description = text?.takeIf { it.isNotBlank() }
        )
    }
}

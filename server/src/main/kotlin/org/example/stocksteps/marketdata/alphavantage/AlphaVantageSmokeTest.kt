package org.example.stocksteps.marketdata.alphavantage

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.example.stocksteps.repository.StockProviderException
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.service.EodSessions
import java.io.File
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase 9B.9A: a bounded, opt-in Alpha Vantage **free-tier** smoke test (never part of the test suite, never automatic).
 *
 * - Runs only with `ALPHA_VANTAGE_API_KEY`, `ALPHA_VANTAGE_SMOKE_CONFIRM=run-bounded-free-tier-smoke-test` and
 *   `ALPHA_VANTAGE_TERMS_CONFIRMED=personal-non-commercial` (the key holder confirms this use is personal under Alpha Vantage's terms §2).
 * - At most [HARD_MAX] authenticated requests (`ALPHA_VANTAGE_SMOKE_MAX_REQUESTS`, default 10), one symbol (`ALPHA_VANTAGE_SMOKE_SYMBOL`,
 *   default AAPL), FREE entitlement only (premium functions are refused locally), [SPACING_MILLIS] between requests, **no retries**; the
 *   first refusal (rate limit, premium, key) stops the run.
 * - The report shows function names, outcomes and field checks only — never the key, request URLs or response bodies.
 * Command: `./gradlew :server:alphaVantageSmokeTest` (see `docs/reviews/market_data_phase9b9a/OWNER_DECISIONS.md`).
 */
object AlphaVantageSmokeTest {
    const val HARD_MAX = 10
    const val SPACING_MILLIS = 15_000L
    const val CONFIRM = "run-bounded-free-tier-smoke-test"
    const val TERMS = "personal-non-commercial"
    val DEFAULT_FUNCTIONS = listOf("TIME_SERIES_DAILY", "GLOBAL_QUOTE", "SYMBOL_SEARCH", "INCOME_STATEMENT", "BALANCE_SHEET", "CASH_FLOW")
    val OPTIONAL_FUNCTIONS = listOf("EARNINGS", "SPLITS")

    data class Step(val function: String, val outcome: String, val checks: List<String>)
    data class Report(val symbol: String, val requestsSent: Int, val budget: Int, val steps: List<Step>, val stoppedEarly: Boolean) {
        fun markdown() = buildString {
            appendLine("# Alpha Vantage free-tier smoke test")
            appendLine(); appendLine("Symbol $symbol · authenticated requests sent: **$requestsSent** of a budget of $budget · stopped early: $stoppedEarly")
            appendLine(); appendLine("| Function | Outcome | Checks |"); appendLine("|---|---|---|")
            steps.forEach { s -> appendLine("| ${s.function} | ${s.outcome} | ${s.checks.joinToString("; ")} |") }
        }
    }

    /** Refuses to start unless every opt-in is present; returns the reason, or null when the run may proceed. */
    fun refusal(env: (String) -> String?): String? = when {
        env("ALPHA_VANTAGE_API_KEY").isNullOrBlank() -> "ALPHA_VANTAGE_API_KEY is not set"
        env("ALPHA_VANTAGE_SMOKE_CONFIRM") != CONFIRM -> "ALPHA_VANTAGE_SMOKE_CONFIRM must be $CONFIRM"
        env("ALPHA_VANTAGE_TERMS_CONFIRMED") != TERMS -> "ALPHA_VANTAGE_TERMS_CONFIRMED must be $TERMS (the key holder confirms personal, non-commercial use)"
        (env("ALPHA_VANTAGE_SMOKE_MAX_REQUESTS")?.toIntOrNull() ?: HARD_MAX) !in 1..HARD_MAX -> "ALPHA_VANTAGE_SMOKE_MAX_REQUESTS must be 1–$HARD_MAX"
        else -> null
    }

    /**
     * Runs [functions] in order against [client] (FREE entitlement), counting real requests with [sent]. Stops before the budget would be
     * exceeded and at the first refusal. [pause] spaces requests (tests pass a no-op).
     */
    suspend fun run(client: AlphaVantageClient, sent: AtomicInteger, symbol: String, functions: List<String>, budget: Int,
                    pause: suspend () -> Unit = { delay(SPACING_MILLIS) }): Report {
        require(budget in 1..HARD_MAX)
        val steps = mutableListOf<Step>()
        var stopped = false
        for ((i, f) in functions.withIndex()) {
            if (sent.get() >= budget) { steps += Step(f, "SKIPPED (budget)", emptyList()); stopped = true; continue }
            if (stopped) { steps += Step(f, "SKIPPED (stopped)", emptyList()); continue }
            if (i > 0) pause()
            val before = sent.get()
            val step = try { Step(f, "OK", checks(client, f, symbol)) } catch (cause: StockProviderException) {
                stopped = true
                Step(f, when {
                    cause.upstreamStatus == 402 && sent.get() == before -> "REFUSED LOCALLY (premium function on FREE)"
                    cause.upstreamStatus == 402 -> "PREMIUM_ONLY (in HTTP 200)"
                    cause.upstreamStatus == 403 -> "INVALID_KEY (in HTTP 200)"
                    cause.failure == StockProviderException.Failure.RATE_LIMITED -> "RATE_LIMITED"
                    cause.failure == StockProviderException.Failure.INVALID_RESPONSE -> "INVALID_RESPONSE"
                    else -> "FAILED (${cause.failure.name})"
                }, emptyList())
            }
            steps += step
        }
        return Report(symbol, sent.get(), budget, steps, stopped)
    }

    private suspend fun checks(c: AlphaVantageClient, f: String, symbol: String): List<String> = when (f) {
        "TIME_SERIES_DAILY" -> c.dailyRaw(symbol)?.let { s -> listOf("bars ${s.bars.size}", "last ${s.bars.lastOrNull()?.date}", "lastRefreshed ${s.lastRefreshed}",
            "zone ${s.timeZone}", "size ${s.outputSize}", "skipped ${s.skipped}", "allCloses>0 ${s.bars.all { it.close.signum() > 0 }}", "adjusted ${s.adjusted}") } ?: listOf("no series")
        "GLOBAL_QUOTE" -> c.quote(symbol)?.let { q -> listOf("symbol ${q.symbol == symbol}", "day ${q.latestTradingDay}", "price>0 ${q.price?.signum() == 1}",
            "previousClose ${q.previousClose != null}", "change% ${q.changePercent != null}") } ?: listOf("no quote")
        "SYMBOL_SEARCH" -> c.search(symbol).let { m -> listOf("matches ${m.size}", "exactUS ${m.any { it.symbol == symbol && it.region == "United States" }}",
            "currencies ${m.mapNotNull { it.currency }.distinct().take(4)}") }
        "INCOME_STATEMENT", "BALANCE_SHEET", "CASH_FLOW" -> c.statement(f, symbol)?.let { s ->
            val key = when (f) { "INCOME_STATEMENT" -> "totalRevenue"; "BALANCE_SHEET" -> "totalAssets"; else -> "operatingCashflow" }
            listOf("annual ${s.annual.size}", "quarterly ${s.quarterly.size}", "latest ${s.annual.firstOrNull()?.fiscalDateEnding}",
                "currencies ${(s.annual + s.quarterly).mapNotNull { it.currency }.distinct()}", "$key missing ${(s.annual + s.quarterly).count { it.values[key] == null }}",
                "skipped ${s.skipped}") + if (f != "BALANCE_SHEET") listOf("quarters ${AlphaVantageFundamentalsMapper.quarterBasis(s, key)}") else emptyList()
        } ?: listOf("no statement")
        "EARNINGS" -> c.earnings(symbol)?.let { e -> listOf("annual ${e.annual.size}", "quarterly ${e.quarterly.size}",
            "reportedEPS missing ${e.quarterly.count { it.reportedEps == null }}") } ?: listOf("no earnings")
        "SPLITS" -> c.splits(symbol)?.let { s -> listOf("splits ${s.size}", "latest ${s.lastOrNull()?.effectiveDate}") } ?: listOf("no splits")
        else -> throw IllegalArgumentException("unsupported function $f")
    }
}

/** `./gradlew :server:alphaVantageSmokeTest` → prints and writes `build/alphavantage-smoke.md`. Exits non-zero when refused. */
fun main() {
    val env: (String) -> String? = System::getenv
    AlphaVantageSmokeTest.refusal(env)?.let { System.err.println("Alpha Vantage smoke test not run: $it"); kotlin.system.exitProcess(2) }
    val budget = env("ALPHA_VANTAGE_SMOKE_MAX_REQUESTS")?.toIntOrNull() ?: AlphaVantageSmokeTest.HARD_MAX
    val symbol = env("ALPHA_VANTAGE_SMOKE_SYMBOL")?.trim()?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{1,6}")) } ?: "AAPL"
    val functions = AlphaVantageSmokeTest.DEFAULT_FUNCTIONS + if (env("ALPHA_VANTAGE_SMOKE_OPTIONAL") == "true") AlphaVantageSmokeTest.OPTIONAL_FUNCTIONS else emptyList()
    val sent = AtomicInteger()
    val http = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 15_000 } }
    http.plugin(HttpSend).intercept { request -> sent.incrementAndGet(); execute(request) }
    val config = AlphaVantageConfig(env("ALPHA_VANTAGE_API_KEY")!!.trim(), primary = false, planRequestsPerMinute = null,
        licenseMode = AlphaVantageConfig.LicenseMode.PERSONAL, personalUids = emptySet(), personalClients = emptySet(), entitlement = AlphaVantageConfig.Entitlement.FREE)
    val client = AlphaVantageClient(http, config, EodSessions(Clock.systemUTC()), cache = CompanyFinancialCache(capacity = 32))
    val report = runBlocking { AlphaVantageSmokeTest.run(client, sent, symbol, functions, budget) }
    val text = report.markdown()
    File("build/alphavantage-smoke.md").apply { parentFile?.mkdirs() }.writeText(text)
    println(text)
    http.close()
}

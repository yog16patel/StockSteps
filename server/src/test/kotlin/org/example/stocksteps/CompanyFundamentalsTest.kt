package org.example.stocksteps

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.example.stocksteps.model.*
import org.example.stocksteps.repositoryImpl.FmpStockProviderRepositoryImpl
import kotlin.test.*

class CompanyFundamentalsTest {
    private fun client(handler: (String, String?) -> Pair<String, HttpStatusCode>): HttpClient = HttpClient(MockEngine { request ->
        assertEquals("AAPL", request.url.parameters["symbol"])
        assertEquals("fixture-key", request.url.parameters["apikey"])
        val (body, status) = handler(request.url.encodedPath.substringAfterLast('/'), request.url.parameters["period"])
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    @Test fun mapsFinancialsCalculationsAndHistoricalComparisonsFromExactStableFields() = runBlocking<Unit> {
        val counts = mutableMapOf<String, Int>()
        client { endpoint, period ->
            counts[endpoint] = (counts[endpoint] ?: 0) + 1
            val body = when (endpoint) {
                "income-statement" -> (2020..2025).reversed().joinToString(",", "[", "]") { year ->
                    val revenue = 100 * (year - 2019)
                    """{"symbol":"AAPL","date":"$year-09-30","fiscalYear":"$year","period":"FY","reportedCurrency":"USD","revenue":$revenue,"netIncome":60,"epsDiluted":3,"grossProfit":300,"operatingIncome":100,"ebitda":120,"ebit":100,"interestExpense":10,"weightedAverageShsOut":20}"""
                }
                "balance-sheet-statement" -> """[{"symbol":"AAPL","date":"2025-09-30","fiscalYear":"2025","period":"FY","reportedCurrency":"USD","cashAndCashEquivalents":50,"totalDebt":100,"totalAssets":500,"totalLiabilities":300,"totalStockholdersEquity":200,"totalCurrentAssets":80,"totalCurrentLiabilities":40},{"symbol":"AAPL","date":"2024-09-30","fiscalYear":"2024","period":"FY","reportedCurrency":"USD","cashAndCashEquivalents":40,"totalDebt":80}]"""
                "cash-flow-statement" -> """[{"symbol":"AAPL","date":"2025-09-30","fiscalYear":"2025","period":"FY","reportedCurrency":"USD","operatingCashFlow":100,"capitalExpenditure":-20,"commonStockRepurchased":-15}]"""
                "ratios-ttm" -> """[{"symbol":"AAPL","priceToEarningsRatioTTM":30,"priceToSalesRatioTTM":5,"priceToBookRatioTTM":4,"priceToFreeCashFlowRatioTTM":10,"grossProfitMarginTTM":0.5,"operatingProfitMarginTTM":0.2,"netProfitMarginTTM":0.1,"dividendYieldTTM":0,"dividendPerShareTTM":0,"dividendPayoutRatioTTM":0}]"""
                "key-metrics-ttm" -> """[{"symbol":"AAPL","returnOnEquityTTM":0.3,"returnOnAssetsTTM":0.12,"returnOnInvestedCapitalTTM":0.25,"enterpriseValueTTM":1000,"evToEBITDATTM":8}]"""
                "ratios" -> (2021..2025).joinToString(",", "[", "]") { """{"symbol":"AAPL","date":"$it-09-30","period":"FY","fiscalYear":"$it","priceToEarningsRatio":20,"priceToSalesRatio":4,"priceToBookRatio":3,"priceToFreeCashFlowRatio":8,"enterpriseValueMultiple":6}""" }
                "income-statement-ttm" -> """[{"symbol":"AAPL","date":"2026-06-30","reportedCurrency":"USD","netIncome":65,"ebitda":125,"interestExpense":0}]"""
                "cash-flow-statement-ttm" -> """[{"symbol":"AAPL","date":"2026-06-30","freeCashFlow":90,"commonDividendsPaid":0}]"""
                "analyst-estimates" -> """[{"symbol":"AAPL","date":"2027-09-30","epsAvg":5,"numAnalystsEps":8}]"""
                "shares-float" -> """[{"symbol":"AAPL","date":"2026-06-30","outstandingShares":19}]"""
                "profile" -> """[{"symbol":"AAPL","currency":"USD"}]"""
                "quote" -> """[{"symbol":"AAPL","price":100}]"""
                else -> "[]"
            }
            body to HttpStatusCode.OK
        }.use { http ->
            val provider = FmpStockProviderRepositoryImpl(http, "fixture-key", today = { java.time.LocalDate.of(2026, 10, 5) })
            val result = provider.getFundamentals("AAPL", "annual")
            assertEquals(600L, result.financials.growth["revenue"]?.amount)
            assertEquals(20.0, result.financials.growth["revenueGrowth"]?.value)
            assertEquals(80L, result.financials.cashFlow["freeCashFlow"]?.amount)
            assertEquals(FinancialSource.BACKEND_CALCULATED, result.financials.cashFlow["freeCashFlow"]?.source)
            assertEquals(25.0, result.financials.financialHealth["cash"]?.changePercent)
            // The REAL path emits the same statement history the Financials screen reads in MOCK mode.
            assertEquals((2025 downTo 2020).toList(), result.history.map { it.fiscalYear })
            val latest = result.history.first()
            assertEquals(600.0, latest.revenue)
            assertEquals(20.0, latest.capitalExpenditure, "FMP's negative capex becomes positive spending")
            assertEquals(80.0, latest.freeCashFlow, "matches the freeCashFlow fact")
            assertEquals(50.0, latest.cash)
            assertEquals(40.0, result.history[1].cash, "each year keeps its own balance sheet")
            assertNull(result.history[1].operatingCashFlow, "a year without a cash-flow row stays unreported")
            assertEquals(25.0, result.financials.financialHealth["debt"]?.changePercent)
            assertEquals(FinancialSource.CONTEXT_DERIVED, result.financials.financialHealth["cash"]?.contextSource)
            assertEquals(50.0, result.financials.profitability["grossMargin"]?.value)
            assertEquals(20.0, result.valuation.metrics["forwardPe"]?.value)
            assertEquals(1000L, result.valuation.metrics["enterpriseValue"]?.amount)
            assertEquals(19L, result.financials.shareholderReturns["shares"]?.amount)
            assertNull(result.financials.financialHealth["interestCoverage"]?.value)
            assertNull(result.financials.shareholderReturns["dividendYield"]?.value)
            assertContains(result.financials.shareholderReturns["dividendYield"]?.note!!, "No dividends")
            assertEquals(FinancialAvailability.NO_DIVIDEND, result.financials.shareholderReturns["dividendYield"]?.availability)
            assertEquals(50.0, result.valuation.historical["pe"]?.differencePercent)
            provider.getQuote("AAPL")
            provider.getFundamentals("AAPL", "annual")
            assertTrue(counts.values.all { it == 1 }, counts.toString())
        }
    }

    @Test fun restrictedAndMalformedDatasetsDoNotDiscardSuccessfulStatements() = runBlocking<Unit> {
        client { endpoint, _ -> when (endpoint) {
            "ratios-ttm", "key-metrics-ttm", "analyst-estimates" -> "restricted" to HttpStatusCode.PaymentRequired
            "income-statement" -> """[{"symbol":"AAPL","date":"2025-09-30","period":"FY","fiscalYear":2025,"reportedCurrency":"USD","revenue":100}]""" to HttpStatusCode.OK
            "shares-float" -> """[{"symbol":"MSFT","outstandingShares":9}]""" to HttpStatusCode.OK
            else -> "[]" to HttpStatusCode.OK
        } }.use { http ->
            val result = FmpStockProviderRepositoryImpl(http, "fixture-key", today = { java.time.LocalDate.of(2026, 10, 5) }).getFundamentals("AAPL", "annual")
            assertEquals(100L, result.financials.growth["revenue"]?.amount)
            assertEquals(FinancialAvailability.TEMPORARILY_UNAVAILABLE, result.datasets["ratiosTtm"])
            assertEquals(FinancialAvailability.TEMPORARILY_UNAVAILABLE, result.valuation.metrics["pe"]?.availability)
            assertEquals(FinancialAvailability.INVALID_VALUE, result.datasets["shares"])
            assertNull(result.financials.shareholderReturns["shares"]?.amount)
            assertFalse(Json.encodeToString(result).contains("fixture-key"))
        }
    }

    @Test fun quarterlyGrowthUsesSameQuarterAndNegativeEarningsSuppressPe() = runBlocking<Unit> {
        client { endpoint, period ->
            val body = when {
                endpoint == "income-statement" && period == "quarter" -> """[
                    {"symbol":"AAPL","date":"2025-06-30","period":"Q2","fiscalYear":"2025","reportedCurrency":"USD","revenue":120,"netIncome":-10,"epsDiluted":-0.5},
                    {"symbol":"AAPL","date":"2025-03-31","period":"Q1","fiscalYear":"2025","reportedCurrency":"USD","revenue":900},
                    {"symbol":"AAPL","date":"2024-06-30","period":"Q2","fiscalYear":"2024","reportedCurrency":"USD","revenue":100,"netIncome":-20,"epsDiluted":-1}
                ]"""
                endpoint == "income-statement-ttm" -> """[{"symbol":"AAPL","date":"2025-06-30","netIncome":-30,"ebitda":-10}]"""
                endpoint == "ratios-ttm" -> """[{"symbol":"AAPL","priceToEarningsRatioTTM":15,"priceToEarningsGrowthRatioTTM":2,"priceToSalesRatioTTM":"invalid","quickRatioTTM":1.2,"interestCoverageRatioTTM":0}]"""
                endpoint == "cash-flow-statement" -> """[{"symbol":"AAPL","date":"2025-06-30","period":"Q2","reportedCurrency":"EUR","operatingCashFlow":100,"capitalExpenditure":-20}]"""
                else -> "[]"
            }
            body to HttpStatusCode.OK
        }.use { http ->
            val result = FmpStockProviderRepositoryImpl(http, "fixture-key", today = { java.time.LocalDate.of(2026, 10, 5) }).getFundamentals("AAPL", "quarter")
            assertEquals(20.0, result.financials.growth["revenueGrowth"]?.value)
            // A smaller loss (−20 → −10) isn't "+50% growth": no percentage across losses.
            assertNull(result.financials.growth["netIncomeGrowth"]?.value)
            assertEquals(org.example.stocksteps.model.FinancialAvailability.UNRELIABLE_COMPARISON, result.financials.growth["netIncomeGrowth"]?.availability)
            assertNull(result.financials.growth["epsGrowth"]?.value)
            assertNull(result.valuation.metrics["pe"]?.value)
            assertContains(result.valuation.metrics["pe"]?.note!!, "non-positive")
            assertNull(result.valuation.metrics["peg"]?.value)
            assertNull(result.valuation.metrics["priceSales"]?.value)
            assertNull(result.financials.financialHealth["interestCoverage"]?.value)
            assertEquals(1.2, result.financials.financialHealth["quickRatio"]?.value)
            assertNull(result.financials.cashFlow["freeCashFlow"]?.amount)
            assertEquals("Q2", result.financials.growth["revenue"]?.basis?.period)
        }
    }

    @Test fun cancellationPropagatesAcrossFundamentalsLoads() = runBlocking<Unit> {
        HttpClient(MockEngine { throw CancellationException("fixture cancellation") }).use { http ->
            assertFailsWith<CancellationException> {
                FmpStockProviderRepositoryImpl(http, "fixture-key", today = { java.time.LocalDate.of(2026, 10, 5) }).getFundamentals("AAPL", "annual")
            }
        }
    }
}

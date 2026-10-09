package org.example.stocksteps

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.service.CompanyFinancialService
import java.util.Locale

fun Route.companyFinancialRoutes(service: CompanyFinancialService, gate: org.example.stocksteps.service.SymbolExistence? = null) {
    get("/api/v1/stocks/{symbol}/fundamentals") {
        val symbol = call.parameters["symbol"]?.uppercase(Locale.ROOT)
        val period = call.request.queryParameters["period"] ?: "annual"
        if (symbol == null || !Regex("[A-Z0-9][A-Z0-9.-]{0,19}").matches(symbol)) {
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_SYMBOL", "Provide a valid stock symbol."))
            return@get
        }
        if (period !in listOf("annual", "quarter")) {
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_PERIOD", "Period must be annual or quarter."))
            return@get
        }
        // Phase 4A: an unknown symbol is answered from the existence gate (≤ 2 cached lookups), never with a 13-request bundle.
        if (gate?.check(symbol) == org.example.stocksteps.service.SymbolExistence.Status.NOT_FOUND) {
            call.respond(HttpStatusCode.NotFound, ApiError("SYMBOL_NOT_FOUND", "We couldn't find a company with this symbol."))
            return@get
        }
        call.respond(service.getFundamentals(symbol, period))
    }
}

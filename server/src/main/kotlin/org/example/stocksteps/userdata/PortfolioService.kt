package org.example.stocksteps.userdata

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.example.stocksteps.portfolio.*
import java.time.LocalDate
import kotlinx.serialization.json.Json

/** Atomic, uid-owned ledger. An ID is an idempotency key: retries cannot duplicate a trade.
 * Deliberately bounded below Firestore's document limit; a future paged ledger can migrate this
 * versioned representation without changing the public transaction models.
 */
class PortfolioService(private val store: UserDataStore, private val now: () -> Long = System::currentTimeMillis) {
    suspend fun get(uid: String) = store.updatePortfolio(uid) { it to it }
    suspend fun saveAccount(uid: String, request: PortfolioAccount) = mutate(uid) { ledger ->
        require(request.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid account ID." }
        require(request.name.trim().length in 1..40) { "Account name must contain 1–40 characters." }
        require(ledger.accounts.none { it.id != request.id && it.name.equals(request.name.trim(), ignoreCase = true) }) { "Choose a different account name; this name is already in use." }
        val existing = ledger.accounts.find { it.id == request.id }
        require(existing != null || ledger.accounts.size < 20) { "Maximum 20 accounts." }
        val account = request.copy(name = request.name.trim(), createdAt = existing?.createdAt ?: now(), updatedAt = now())
        ledger.copy(accounts = ledger.accounts.filterNot { it.id == account.id } + account)
    }
    suspend fun deleteAccount(uid: String, id: String) = mutate(uid) { ledger ->
        require(ledger.accounts.any { it.id == id }) { "Account does not exist." }
        ledger.copy(accounts = ledger.accounts.filterNot { it.id == id }, transactions = ledger.transactions.filterNot { it.accountId == id })
    }
    suspend fun saveTransaction(uid: String, request: PortfolioTransaction, edit: Boolean) = mutate(uid) { ledger ->
        val account = ledger.accounts.find { it.id == request.accountId }
        require(account != null && !account.archived) { "Choose an active account." }
        require(request.tradeDate <= java.time.Instant.ofEpochMilli(now()).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()) { "Future transactions are not supported." }
        val existing = ledger.transactions.find { it.id == request.id }
        if (existing != null && !edit) {
            require(existing.copy(createdAt = 0, updatedAt = 0, sequence = 0) == normalize(request).copy(createdAt = 0, updatedAt = 0, sequence = 0)) { "This transaction ID already has different contents." }
            ledger
        } else {
            require(!edit || existing != null && existing.accountId == request.accountId) { "Transaction does not exist in this account." }
            require(existing != null || ledger.transactions.size < 1000) { "Maximum 1,000 transactions in this ledger." }
            val transaction = normalize(request).copy(createdAt = existing?.createdAt ?: now(), updatedAt = now(), sequence = existing?.sequence ?: ((ledger.transactions.maxOfOrNull { it.sequence } ?: 0) + 1))
            ledger.copy(transactions = ledger.transactions.filterNot { it.id == transaction.id } + transaction)
        }
    }
    suspend fun deleteTransaction(uid: String, id: String) = mutate(uid) { ledger ->
        require(ledger.transactions.any { it.id == id }) { "Transaction does not exist." }
        ledger.copy(transactions = ledger.transactions.filterNot { it.id == id })
    }
    private fun normalize(tx: PortfolioTransaction): PortfolioTransaction {
        PortfolioEngine.validate(tx)
        val quantity = Decimal.parse(tx.quantity)
        val price = Decimal.parse(tx.unitPrice)
        val fees = Decimal.parse(tx.fees)
        val gross = if (tx.type in setOf(TransactionType.BUY, TransactionType.SELL, TransactionType.OPENING_POSITION, TransactionType.DIVIDEND_REINVESTMENT)) quantity * price else Decimal.parse(tx.grossAmount)
        val net = when (tx.type) {
            TransactionType.BUY, TransactionType.OPENING_POSITION, TransactionType.DIVIDEND_REINVESTMENT -> gross + fees
            TransactionType.SELL, TransactionType.DIVIDEND, TransactionType.CASH_DEPOSIT -> gross - fees
            TransactionType.CASH_WITHDRAWAL, TransactionType.FEE -> gross + fees
            TransactionType.CASH_ADJUSTMENT, TransactionType.TRANSFER_IN -> gross - fees
            TransactionType.TRANSFER_OUT -> gross + fees
            else -> gross
        }
        return tx.copy(quantity = quantity.toString(), unitPrice = price.toString(), fees = fees.toString(), grossAmount = gross.toString(), netAmount = net.toString())
    }
    private suspend fun mutate(uid: String, transform: (PortfolioLedger) -> PortfolioLedger): PortfolioLedger = try {
        store.updatePortfolio(uid) { current ->
            val transformed = transform(current)
            require(transformed.transactions.mapNotNull { it.instrument?.symbol }.distinct().size <= 100) { "Maximum 100 distinct securities per portfolio ledger." }
            transformed.accounts.forEach { PortfolioEngine.replay(transformed, it.id) }
            val next = if (transformed == current) current else transformed.copy(revision = current.revision + 1)
            require(Json.encodeToString(PortfolioLedger.serializer(), next).encodeToByteArray().size < 800_000) { "Portfolio storage limit reached." }
            next to next
        }
    } catch (cause: IllegalArgumentException) {
        throw UserDataException(400, "INVALID_PORTFOLIO", cause.message ?: "Invalid portfolio transaction.")
    }
}

fun Route.portfolioRoutes(auth: UserAuthenticator, service: PortfolioService, market: PortfolioMarketService) {
    route("/api/v1/me/portfolio") {
        get("/accounts/{id}/summary") { user(auth) { uid -> call.respond(market.report(uid, call.parameters["id"].orEmpty(), "1M", false)) } }
        get("/accounts/{id}/history") { user(auth) { uid -> call.respond(market.report(uid, call.parameters["id"].orEmpty(), call.request.queryParameters["range"] ?: "1M", true)) } }
        get { user(auth) { call.respond(service.get(it)) } }
        put("/accounts") { user(auth) { call.respond(service.saveAccount(it, call.receive())) } }
        delete("/accounts/{id}") { user(auth) { call.respond(service.deleteAccount(it, call.parameters["id"].orEmpty())) } }
        post("/transactions") { user(auth) { call.respond(service.saveTransaction(it, call.receive(), false)) } }
        put("/transactions") { user(auth) { call.respond(service.saveTransaction(it, call.receive(), true)) } }
        delete("/transactions/{id}") { user(auth) { call.respond(service.deleteTransaction(it, call.parameters["id"].orEmpty())) } }
    }
}

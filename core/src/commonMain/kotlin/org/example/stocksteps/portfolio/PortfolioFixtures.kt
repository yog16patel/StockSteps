package org.example.stocksteps.portfolio

import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.model.WatchQuote

/** Read-only deterministic development cases. Never imported into a user's database. */
data class PortfolioFixture(
    val id: String,
    val ledger: PortfolioLedger,
    val quotes: List<WatchQuote>,
    val selectedAccount: String?,
    val missingHistory: Boolean = false,
    val missingFx: Boolean = false,
    val watchlistSymbols: List<String> = emptyList()
) {
    fun report(accountId: String): PortfolioReport {
        val account = ledger.accounts.first { it.id == accountId }
        val fx = if (missingFx) emptyMap() else if (account.reportingCurrency == PortfolioCurrency.CAD)
            mapOf(PortfolioCurrency.USD to "1.35") else mapOf(PortfolioCurrency.CAD to "0.74074074")
        val basisFx = ledger.transactions.map { it.tradeDate }.distinct().associateWith { fx }
        fun observation(date: String) = PortfolioPrices(date, quotes.mapNotNull { quote -> quote.price?.let { quote.symbol to it.toLong().toString() } }.toMap(), fx, basisFx)
        val current = observation("2026-10-08")
        val history = if (missingHistory) emptyList() else PortfolioEngine.history(ledger, accountId,
            listOf("2026-09-01", "2026-09-15", "2026-10-01", "2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08").map(::observation))
        val summary = PortfolioEngine.value(ledger, accountId, current)
        val allocations = PortfolioEngine.allocations(PortfolioEngine.replay(ledger, accountId), current, account.reportingCurrency)
        return PortfolioReport(accountId, ledger.revision, summary, history, "ALL",
            "Deterministic sample ledger and dated prices. Read-only; your saved accounts and watchlists are unchanged.",
            "2026-10-08", if (quotes.any { it.stale }) PortfolioDailyPerformance(null, null, "Daily change is unavailable with stale prices.")
            else PortfolioEngine.daily(ledger, accountId, observation("2026-10-07"), current), allocations.holdings, fx, allocations.currencies, "2026-10-08T15:00:00Z")
    }
}

object PortfolioFixtureCatalog {
    val ids = listOf("no-portfolios", "one-cad", "multiple-accounts", "mixed-currencies", "fractional-shares",
        "multiple-buys", "partial-sale", "complete-sale", "dividend", "dividend-reinvestment", "cash-flows",
        "missing-history", "missing-fx", "stale-quote", "partial-failure", "empty-account", "watched-and-owned", "account-switch")
    fun get(id: String): PortfolioFixture {
        require(id in ids) { "Unknown portfolio scenario." }
        val usd = PortfolioAccount("sample-usd", "Personal USD", reportingCurrency = PortfolioCurrency.USD)
        val cad = PortfolioAccount("sample-cad", "TFSA CAD", PortfolioCategory.TFSA, PortfolioCurrency.CAD)
        val apple = InstrumentRef("AAPL", "Apple", "NASDAQ", "USD")
        val td = InstrumentRef("TD.TO", "Toronto-Dominion Bank", "TSX", "CAD")
        val accounts = when (id) {
            "no-portfolios" -> emptyList()
            "one-cad" -> listOf(cad)
            "multiple-accounts", "account-switch" -> listOf(usd, cad)
            "mixed-currencies", "missing-fx" -> listOf(cad)
            else -> listOf(usd)
        }
        var index = 0
        fun transaction(type: TransactionType, quantity: String = "0", price: String = "0", amount: String = "0", date: String = "2026-09-01", account: PortfolioAccount = accounts.first(), instrument: InstrumentRef? = apple, fees: String = "0") =
            PortfolioTransaction("sample-${++index}", account.id, type, date, if (instrument == td) PortfolioCurrency.CAD else PortfolioCurrency.USD,
                instrument, quantity, price, amount, fees, createdAt = index.toLong())
        val transactions = mutableListOf<PortfolioTransaction>()
        if (id !in listOf("no-portfolios", "empty-account")) {
            transactions += transaction(TransactionType.OPENING_POSITION, if (id == "fractional-shares") "0.125" else "10", "80", instrument = if (id == "one-cad") td else apple)
        }
        when (id) {
            "multiple-accounts", "account-switch" -> transactions += transaction(TransactionType.OPENING_POSITION, "5", "75", account = cad, instrument = td)
            "mixed-currencies", "partial-failure" -> transactions += transaction(TransactionType.OPENING_POSITION, "5", "75", instrument = td)
            "multiple-buys" -> {
                transactions += transaction(TransactionType.CASH_DEPOSIT, amount = "1000", instrument = null, date = "2026-09-15")
                transactions += transaction(TransactionType.BUY, "2.5", "90", date = "2026-09-15", fees = "1")
            }
            "partial-sale" -> transactions += transaction(TransactionType.SELL, "3", "95", fees = "1", date = "2026-10-01")
            "complete-sale" -> transactions += transaction(TransactionType.SELL, "10", "95", fees = "1", date = "2026-10-01")
            "dividend" -> transactions += transaction(TransactionType.DIVIDEND, amount = "12", date = "2026-10-01")
            "dividend-reinvestment" -> transactions += transaction(TransactionType.DIVIDEND_REINVESTMENT, "0.12", "100", date = "2026-10-01")
            "cash-flows" -> {
                transactions += transaction(TransactionType.CASH_DEPOSIT, amount = "1000", instrument = null, date = "2026-10-05")
                transactions += transaction(TransactionType.CASH_WITHDRAWAL, amount = "100", instrument = null, date = "2026-10-08")
            }
        }
        val symbols = transactions.mapNotNull { it.instrument?.symbol }.distinct()
        val quotes = symbols.map { symbol -> WatchQuote(symbol, if (symbol == "AAPL") "Apple" else "Toronto-Dominion Bank",
            price = if (id == "partial-failure" && symbol == "AAPL") null else 100.0, previousClose = 100.0,
            currency = if (symbol.endsWith(".TO")) "CAD" else "USD", stale = id == "stale-quote", asOf = "2026-10-08T15:00:00Z") }
        return PortfolioFixture(id, PortfolioLedger(accounts, transactions, 1), quotes, if (id == "account-switch") cad.id else accounts.firstOrNull()?.id,
            id == "missing-history", id == "missing-fx", if (id == "watched-and-owned") listOf("AAPL") else emptyList())
    }
}

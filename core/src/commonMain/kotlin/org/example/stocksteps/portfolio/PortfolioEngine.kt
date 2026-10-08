package org.example.stocksteps.portfolio

/** Moving-average investment book, not a jurisdiction-specific tax calculation.
 * Buy fees increase basis; sell fees reduce proceeds; dividends never reduce basis.
 * Reinvestment increases basis without a net cash movement. Standalone fees reduce cash.
 * Selling/transfer-out removes proportional basis; the last share removes all residual basis.
 */
object PortfolioEngine {
    private data class Position(
        val instrument: org.example.stocksteps.model.InstrumentRef,
        val currency: PortfolioCurrency,
        var shares: Decimal = Decimal.ZERO,
        var basis: Decimal = Decimal.ZERO,
        var realized: Decimal = Decimal.ZERO,
        var dividends: Decimal = Decimal.ZERO
    )

    fun replay(ledger: PortfolioLedger, accountId: String, throughDate: String = "9999-12-31"): PortfolioPositionState =
        replayBook(ledger, accountId, throughDate, emptyMap())

    private fun replayBook(ledger: PortfolioLedger, accountId: String, throughDate: String, bookAmounts: Map<String, Pair<Decimal, Decimal>>): PortfolioPositionState {
        require(ledger.accounts.any { it.id == accountId }) { "Account does not exist." }
        val positions = linkedMapOf<String, Position>()
        val cash = mutableMapOf<PortfolioCurrency, Decimal>()
        val realized = mutableMapOf<PortfolioCurrency, Decimal>()
        val contributions = mutableMapOf<PortfolioCurrency, Decimal>()
        val dividends = mutableMapOf<PortfolioCurrency, Decimal>()
        fun MutableMap<PortfolioCurrency, Decimal>.change(currency: PortfolioCurrency, amount: Decimal) {
            this[currency] = (this[currency] ?: Decimal.ZERO) + amount
        }
        ledger.transactions.filter { it.accountId == accountId && it.tradeDate <= throughDate }
            .sortedWith(compareBy<PortfolioTransaction> { it.tradeDate }.thenBy { it.sequence }.thenBy { it.createdAt }.thenBy { it.id })
            .forEach { tx ->
                validate(tx)
                val quantity = Decimal.parse(tx.quantity)
                val price = Decimal.parse(tx.unitPrice)
                val fees = Decimal.parse(tx.fees)
                val gross = Decimal.parse(tx.grossAmount)
                val position = tx.instrument?.let { instrument ->
                    positions.getOrPut(instrument.symbol) { Position(instrument, tx.currency) }.also {
                        require(it.currency == tx.currency) { "A holding must use a consistent trading currency." }
                    }
                }
                fun cashChange(amount: Decimal) {
                    if (amount == Decimal.ZERO) return
                    val converted = if (tx.cashCurrency == tx.currency) amount else amount * Decimal.parse(requireNotNull(tx.fxRate) { "Cash conversion needs a trade-date FX rate." })
                    cash.change(tx.cashCurrency, converted)
                }
                fun acquire() {
                    requireNotNull(position)
                    position.shares += quantity
                    position.basis += bookAmounts[tx.id]?.first ?: (quantity * price + fees)
                }
                fun dispose(sale: Boolean) {
                    requireNotNull(position)
                    require(quantity <= position.shares) { "Transaction would sell or transfer more shares than owned on ${tx.tradeDate}." }
                    val allocated = if (quantity == position.shares) position.basis else position.basis.multiplyDivide(quantity, position.shares)
                    position.shares -= quantity
                    position.basis -= allocated
                    if (sale) {
                        val gain = (bookAmounts[tx.id]?.second ?: (quantity * price - fees)) - allocated
                        position.realized += gain
                        realized.change(tx.currency, gain)
                    }
                }
                when (tx.type) {
                    TransactionType.OPENING_POSITION -> acquire()
                    TransactionType.BUY -> { acquire(); cashChange(-(quantity * price + fees)) }
                    TransactionType.SELL -> { dispose(true); cashChange(quantity * price - fees) }
                    TransactionType.DIVIDEND_REINVESTMENT -> {
                        acquire()
                        requireNotNull(position).dividends += quantity * price + fees
                        dividends.change(tx.currency, quantity * price + fees)
                    }
                    TransactionType.DIVIDEND -> {
                        cashChange(gross - fees)
                        position?.let { it.dividends += gross }
                        dividends.change(tx.currency, gross)
                    }
                    TransactionType.CASH_DEPOSIT -> { cashChange(gross - fees); contributions.change(tx.currency, gross) }
                    TransactionType.CASH_WITHDRAWAL -> { cashChange(-(gross + fees)); contributions.change(tx.currency, -gross) }
                    TransactionType.FEE -> cashChange(-(gross + fees))
                    TransactionType.CASH_ADJUSTMENT -> cashChange(gross - fees)
                    TransactionType.TRANSFER_IN -> if (position != null) { acquire(); cashChange(-fees) } else cashChange(gross - fees)
                    TransactionType.TRANSFER_OUT -> if (position != null) { dispose(false); cashChange(-fees) } else cashChange(-(gross + fees))
                    TransactionType.STOCK_SPLIT -> {
                        requireNotNull(position)
                        position.shares *= quantity
                    }
                }
            }
        return PortfolioPositionState(
            positions.values.map { PortfolioHolding(it.instrument, it.currency, it.shares.toString(), it.basis.toString(), it.realized.toString(), it.dividends.toString()) },
            cash.mapValues { it.value.toString() },
            realized.mapValues { it.value.toString() },
            contributions.mapValues { it.value.toString() },
            dividends.mapValues { it.value.toString() }
        )
    }

    fun validate(tx: PortfolioTransaction) {
        require(tx.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid transaction ID." }
        require(validDate(tx.tradeDate) && (tx.settlementDate == null || validDate(tx.settlementDate))) { "Use a valid YYYY-MM-DD date." }
        require(tx.settlementDate == null || tx.settlementDate >= tx.tradeDate) { "Settlement cannot precede the trade." }
        val quantity = Decimal.parse(tx.quantity)
        val price = Decimal.parse(tx.unitPrice)
        val gross = Decimal.parse(tx.grossAmount)
        val fees = Decimal.parse(tx.fees)
        Decimal.parse(tx.netAmount)
        require(quantity >= Decimal.ZERO && price >= Decimal.ZERO && fees >= Decimal.ZERO) { "Shares, price and fees cannot be negative." }
        require(tx.type == TransactionType.CASH_ADJUSTMENT || gross >= Decimal.ZERO) { "Amount cannot be negative." }
        val shareType = tx.type in setOf(TransactionType.BUY, TransactionType.SELL, TransactionType.OPENING_POSITION, TransactionType.DIVIDEND_REINVESTMENT, TransactionType.STOCK_SPLIT) || tx.instrument != null && tx.type in setOf(TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT)
        if (shareType) require(tx.instrument != null && quantity > Decimal.ZERO) { "Choose a security and a positive share quantity." }
        if (tx.type in setOf(TransactionType.BUY, TransactionType.SELL, TransactionType.DIVIDEND_REINVESTMENT)) require(price > Decimal.ZERO) { "Trade price must be positive." }
        if (tx.type in setOf(TransactionType.CASH_DEPOSIT, TransactionType.CASH_WITHDRAWAL, TransactionType.DIVIDEND)) require(gross > Decimal.ZERO) { "Amount must be positive." }
        if (tx.type == TransactionType.STOCK_SPLIT) require(fees == Decimal.ZERO) { "Record split-related fees as a separate fee transaction." }
        tx.instrument?.let { require(it.symbol.matches(Regex("[A-Z0-9][A-Z0-9.^-]{0,31}"))) { "Invalid security symbol." } }
        tx.fxRate?.let { require(Decimal.parse(it) > Decimal.ZERO) { "FX rate must be positive." } }
        require(tx.notes.orEmpty().length <= 1000) { "Notes are too long." }
    }

    fun validDate(date: String): Boolean {
        if (!date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return false
        val year = date.take(4).toInt()
        val month = date.substring(5, 7).toInt()
        val day = date.takeLast(2).toInt()
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days = listOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return year in 1900..9999 && month in 1..12 && day in 1..days[month - 1]
    }

    fun value(ledger: PortfolioLedger, accountId: String, observations: PortfolioPrices): PortfolioValuation {
        val account = ledger.accounts.first { it.id == accountId }
        val state = replay(ledger, accountId, observations.date)
        val missing = mutableListOf<String>()
        fun convert(value: Decimal, currency: PortfolioCurrency): Decimal? {
            if (currency == account.reportingCurrency || value == Decimal.ZERO) return value
            val rate = observations.fx[currency]?.let(Decimal::parse)
            if (rate == null) missing += "FX:$currency/${account.reportingCurrency}"
            return rate?.let { value * it }
        }
        var total = Decimal.ZERO
        var basis = Decimal.ZERO
        var realized = Decimal.ZERO
        val bookAmounts = mutableMapOf<String, Pair<Decimal, Decimal>>()
        // Reporting basis is acquired at the dated FX rate, not retranslated at today's FX.
        val convertedTransactions = ledger.transactions.filter { it.accountId == accountId && it.tradeDate <= observations.date &&
            (it.type in setOf(TransactionType.BUY, TransactionType.SELL, TransactionType.OPENING_POSITION, TransactionType.DIVIDEND_REINVESTMENT, TransactionType.STOCK_SPLIT) ||
                it.instrument != null && it.type in setOf(TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT)) }.map { tx ->
            if (tx.currency == account.reportingCurrency) tx.copy(cashCurrency = account.reportingCurrency, fxRate = null)
            else {
                val rate = if (tx.type in setOf(TransactionType.STOCK_SPLIT, TransactionType.TRANSFER_OUT)) Decimal.ONE
                    else observations.historicalFx[tx.tradeDate]?.get(tx.currency)?.let(Decimal::parse)
                if (rate == null) missing += "FX:basis:${tx.currency}:${tx.tradeDate}"
                fun amount(value: String) = (Decimal.parse(value) * (rate ?: Decimal.ONE)).toString()
                val originalGross = Decimal.parse(tx.quantity) * Decimal.parse(tx.unitPrice)
                val originalFees = Decimal.parse(tx.fees)
                bookAmounts[tx.id] = ((originalGross + originalFees) * (rate ?: Decimal.ONE)) to ((originalGross - originalFees) * (rate ?: Decimal.ONE))
                tx.copy(currency = account.reportingCurrency, cashCurrency = account.reportingCurrency, fxRate = null,
                    grossAmount = amount(tx.grossAmount), fees = amount(tx.fees), netAmount = amount(tx.netAmount))
            }
        }
        val reportingState = replayBook(ledger.copy(transactions = convertedTransactions), accountId, observations.date, bookAmounts)
        reportingState.holdings.forEach { basis += Decimal.parse(it.costBasis) }
        reportingState.realizedGain.values.forEach { realized += Decimal.parse(it) }
        state.holdings.filter { Decimal.parse(it.quantity) > Decimal.ZERO }.forEach { holding ->
            val quote = observations.prices[holding.instrument.symbol]?.let(Decimal::parse)
            if (quote == null) missing += holding.instrument.symbol
            else convert(Decimal.parse(holding.quantity) * quote, holding.currency)?.let { total += it }
        }
        state.cash.forEach { (currency, amount) -> convert(Decimal.parse(amount), currency)?.let { total += it } }
        return PortfolioValuation(observations.date, account.reportingCurrency,
            total.toString().takeIf { missing.none { !it.startsWith("FX:basis:") } }, basis.toString().takeIf { missing.none { it.startsWith("FX:") } },
            (total - state.cash.entries.fold(Decimal.ZERO) { sum, entry -> sum + (convert(Decimal.parse(entry.value), entry.key) ?: Decimal.ZERO) } - basis).toString().takeIf { missing.isEmpty() },
            realized.toString().takeIf { missing.none { it.startsWith("FX:") } }, missing.distinct())
    }

    /** Only observation dates on/after the first actual transaction can produce a chart point. */
    fun history(ledger: PortfolioLedger, accountId: String, observations: List<PortfolioPrices>): List<PortfolioValuation> {
        val first = ledger.transactions.filter { it.accountId == accountId }.minOfOrNull { it.tradeDate } ?: return emptyList()
        return observations.filter { it.date >= first }.sortedBy { it.date }.map { value(ledger, accountId, it) }
    }

    /** Allocation includes cash. Missing data makes percentages unavailable, never renormalized
     * over only the holdings for which a quote happened to succeed.
     */
    fun allocations(state: PortfolioPositionState, observations: PortfolioPrices, reporting: PortfolioCurrency): PortfolioAllocations {
        var total = Decimal.ZERO
        var complete = true
        val grouped = linkedMapOf<PortfolioCurrency, Decimal?>()
        fun converted(amount: Decimal, currency: PortfolioCurrency): Decimal? =
            if (currency == reporting || amount == Decimal.ZERO) amount else observations.fx[currency]?.let { amount * Decimal.parse(it) }
        fun add(currency: PortfolioCurrency, amount: Decimal?) {
            if (amount == null) { complete = false; grouped[currency] = null }
            else {
                total += amount
                if (currency !in grouped) grouped[currency] = amount
                else grouped[currency]?.let { grouped[currency] = it + amount }
            }
        }
        val values = state.holdings.filter { Decimal.parse(it.quantity) > Decimal.ZERO }.map { holding ->
            val value = observations.prices[holding.instrument.symbol]?.let { converted(Decimal.parse(holding.quantity) * Decimal.parse(it), holding.currency) }
            add(holding.currency, value)
            Triple(holding.instrument.symbol, holding.currency, value)
        }
        state.cash.forEach { (currency, amount) -> add(currency, converted(Decimal.parse(amount), currency)) }
        fun percent(amount: Decimal?) = if (complete && total > Decimal.ZERO && amount != null) (amount / total * Decimal.parse("100")).toString() else null
        return PortfolioAllocations(values.map { (symbol, currency, amount) -> PortfolioAllocation(symbol, currency, amount?.toString(), percent(amount)) },
            grouped.map { (currency, amount) -> PortfolioCurrencyAllocation(currency, amount?.toString(), percent(amount)) })
    }

    /** Daily gain removes external cash flows. Percentage uses beginning-day capital plus
     * deposits (a conservative dated-flow convention, not intraday TWR). Opening imports,
     * corrections and transfers need an observed boundary value; do not call them profit.
     */
    fun daily(ledger: PortfolioLedger, accountId: String, previous: PortfolioPrices, current: PortfolioPrices): PortfolioDailyPerformance {
        val account = ledger.accounts.first { it.id == accountId }
        val flows = ledger.transactions.filter { it.accountId == accountId && it.tradeDate > previous.date && it.tradeDate <= current.date }
        val convention = "Deposits and withdrawals are not counted as gains. The percentage uses your starting balance plus deposits."
        fun unavailable(reason: String) = PortfolioDailyPerformance(null, null, reason)
        if (flows.any { it.type in setOf(TransactionType.OPENING_POSITION, TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT, TransactionType.CASH_ADJUSTMENT, TransactionType.STOCK_SPLIT) }) {
            return unavailable("Daily return unavailable across an opening position, transfer, correction or split without a measured boundary value.")
        }
        val start = value(ledger, accountId, previous).totalValue?.let(Decimal::parse) ?: return unavailable("Previous valuation unavailable.")
        val end = value(ledger, accountId, current).totalValue?.let(Decimal::parse) ?: return unavailable("Current valuation unavailable.")
        var netFlow = Decimal.ZERO
        var deposits = Decimal.ZERO
        for (tx in flows.filter { it.type == TransactionType.CASH_DEPOSIT || it.type == TransactionType.CASH_WITHDRAWAL }) {
            val amount = Decimal.parse(tx.grossAmount) * if (tx.cashCurrency == tx.currency) Decimal.ONE else tx.fxRate?.let(Decimal::parse) ?: return unavailable("Cash-flow conversion unavailable.")
            val rate = if (tx.cashCurrency == account.reportingCurrency) Decimal.ONE else current.historicalFx[tx.tradeDate]?.get(tx.cashCurrency)?.let(Decimal::parse)
                ?: return unavailable("Cash-flow FX unavailable.")
            val converted = amount * rate
            if (tx.type == TransactionType.CASH_DEPOSIT) { netFlow += converted; deposits += converted } else netFlow -= converted
        }
        val gain = end - start - netFlow
        val denominator = start + deposits
        return PortfolioDailyPerformance(gain.toString(), if (denominator > Decimal.ZERO) (gain / denominator * Decimal.parse("100")).toString() else null, convention)
    }
}

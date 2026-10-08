package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.portfolio.*

@Composable
internal fun PortfolioAccountDialog(existing: PortfolioAccount?, onDismiss: () -> Unit, onSave: (PortfolioAccount) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: PortfolioCategory.PERSONAL) }
    var currency by rememberSaveable { mutableStateOf(existing?.reportingCurrency ?: PortfolioCurrency.CAD) }
    var archived by rememberSaveable { mutableStateOf(existing?.archived ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create investment account" else "Account settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
                OutlinedTextField(name, { name = it }, label = { Text("Account name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PortfolioChoice("Account category", category.name, PortfolioCategory.entries.map { it.name }) { category = PortfolioCategory.valueOf(it) }
                PortfolioChoice("Reporting currency", currency.name, PortfolioCurrency.entries.map { it.name }) { currency = PortfolioCurrency.valueOf(it) }
                if (existing != null) Row { Checkbox(archived, { archived = it }); Text("Archive account") }
                Text("Account categories organize investments; calculations are not tax advice.", style = StockStepsTheme.typography.caption)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(PortfolioAccount(existing?.id ?: PortfolioPresenter.newId(), name.trim(), category, currency, archived)) }, enabled = name.trim().length in 1..40) { Text("Save account") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun PortfolioChoice(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${selected.replace('_', ' ')}") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option.replace('_', ' ')) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@Composable
internal fun PortfolioTransactionForm(
    state: PortfolioUiState,
    modifier: Modifier = Modifier,
    initialSymbol: String,
    initialName: String?,
    initialExchange: String?,
    initialCurrency: String?,
    existing: PortfolioTransaction? = null,
    onSubmit: (PortfolioTransaction) -> Unit
) {
    val id = rememberSaveable { existing?.id ?: PortfolioPresenter.newId() }
    var accountId by rememberSaveable { mutableStateOf(existing?.accountId ?: state.selectedAccountId.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: TransactionType.OPENING_POSITION) }
    var symbol by rememberSaveable { mutableStateOf(initialSymbol) }
    var name by rememberSaveable { mutableStateOf(initialName.orEmpty()) }
    var exchange by rememberSaveable { mutableStateOf(initialExchange.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(existing?.quantity ?: "") }
    var price by rememberSaveable { mutableStateOf(existing?.unitPrice ?: "") }
    var amount by rememberSaveable { mutableStateOf(existing?.grossAmount ?: "") }
    var fees by rememberSaveable { mutableStateOf(existing?.fees ?: "0") }
    var date by rememberSaveable { mutableStateOf(existing?.tradeDate ?: state.today) }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: PortfolioCurrency.entries.firstOrNull { it.name == initialCurrency } ?: PortfolioCurrency.USD) }
    var cashCurrency by rememberSaveable { mutableStateOf(existing?.cashCurrency ?: currency) }
    var fxRate by rememberSaveable { mutableStateOf(existing?.fxRate.orEmpty()) }
    var settlement by rememberSaveable { mutableStateOf(existing?.settlementDate.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var previousSecurity by rememberSaveable { mutableStateOf(initialSymbol) }
    LaunchedEffect(initialSymbol) {
        if (previousSecurity != initialSymbol) {
            symbol = initialSymbol
            name = initialName.orEmpty()
            exchange = initialExchange.orEmpty()
            initialCurrency?.let { selected ->
                PortfolioCurrency.entries.firstOrNull { it.name == selected }?.let { currency = it; cashCurrency = it }
            }
            quantity = ""
            price = ""
            previousSecurity = initialSymbol
        }
    }
    LaunchedEffect(state.selectedAccountId) { if (accountId.isEmpty()) accountId = state.selectedAccountId.orEmpty() }
    val sharesRequired = type in setOf(TransactionType.BUY, TransactionType.SELL, TransactionType.OPENING_POSITION, TransactionType.DIVIDEND_REINVESTMENT, TransactionType.STOCK_SPLIT)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(StockStepsTheme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)
    ) {
        Text(if (existing == null) "Add investment or transaction" else "Edit transaction", style = StockStepsTheme.typography.sectionTitle)
        Text("Existing investments start on the effective date you enter. We never invent earlier returns.")
        val available = state.accounts.filterNot { it.archived }
        PortfolioChoice("Account", available.firstOrNull { it.id == accountId }?.name ?: "Choose", available.map { it.name }) { selected -> accountId = available.first { it.name == selected }.id }
        PortfolioChoice("Transaction", type.name, TransactionType.entries.map { it.name }) { type = TransactionType.valueOf(it) }
        PortfolioChoice("Trade / cash currency", currency.name, PortfolioCurrency.entries.map { it.name }) { if (cashCurrency == currency) cashCurrency = PortfolioCurrency.valueOf(it); currency = PortfolioCurrency.valueOf(it) }
        run {
            OutlinedTextField(symbol, { symbol = it.trim().uppercase() }, label = { Text("Exact security symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, label = { Text("Company name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(exchange, { exchange = it }, label = { Text("Exchange (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (sharesRequired || type in setOf(TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT) && symbol.isNotBlank()) {
            OutlinedTextField(quantity, { quantity = it }, label = { Text(if (type == TransactionType.STOCK_SPLIT) "Split ratio: new / old" else "Shares (fractional supported)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(price, { price = it }, label = { Text(if (type == TransactionType.OPENING_POSITION) "Average cost per share" else "Price per share") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } else OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(fees, { fees = it }, label = { Text("Fees") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("Effective / trade date · YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        PortfolioChoice("Cash currency", cashCurrency.name, PortfolioCurrency.entries.map { it.name }) { cashCurrency = PortfolioCurrency.valueOf(it) }
        if (cashCurrency != currency) OutlinedTextField(fxRate, { fxRate = it }, label = { Text("Trade-date FX: one ${currency.name} in ${cashCurrency.name}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(settlement, { settlement = it }, label = { Text("Settlement date (optional) · YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") })
        (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !state.busy && accountId.isNotEmpty(),
            onClick = {
                try {
                    val transaction = PortfolioTransaction(id, accountId, type, date, currency,
                        instrument = symbol.takeIf { it.isNotBlank() }?.let { InstrumentRef(it, name.ifBlank { null }, exchange.ifBlank { null }, existing?.instrument?.currency ?: initialCurrency ?: currency.name) },
                        quantity = quantity.ifBlank { "0" }, unitPrice = price.ifBlank { "0" }, grossAmount = amount.ifBlank { "0" }, fees = fees,
                        notes = notes.ifBlank { null }, cashCurrency = cashCurrency, fxRate = fxRate.ifBlank { null }, settlementDate = settlement.ifBlank { null })
                    PortfolioEngine.validate(transaction)
                    error = null
                    onSubmit(transaction)
                } catch (cause: IllegalArgumentException) { error = cause.message }
            }
        ) { Text(if (state.busy) "Saving…" else "Save transaction") }
    }
}

package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import org.example.stocksteps.designsystem.components.StockButton
import org.example.stocksteps.designsystem.components.StockSelectField
import org.example.stocksteps.designsystem.components.StockTextField
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.portfolio.*

@Composable
internal fun PortfolioAccountDialog(existing: PortfolioAccount?, onDismiss: () -> Unit, onSave: (PortfolioAccount) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: PortfolioCategory.PERSONAL) }
    var currency by rememberSaveable { mutableStateOf(existing?.reportingCurrency ?: PortfolioCurrency.CAD) }
    var archived by rememberSaveable { mutableStateOf(existing?.archived ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) PortfolioDisplay.ACCOUNT_CREATE_TITLE else PortfolioDisplay.ACCOUNT_SETTINGS_TITLE) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
                StockTextField(name, { name = it }, PortfolioDisplay.ACCOUNT_NAME, Modifier.fillMaxWidth())
                StockSelectField(PortfolioDisplay.ACCOUNT_CATEGORY, PortfolioFormRules.categoryOptions, category, PortfolioPresentation::categoryLabel,
                    onSelect = { category = it }, modifier = Modifier.fillMaxWidth())
                StockSelectField(PortfolioDisplay.ACCOUNT_CURRENCY, PortfolioFormRules.currencyOptions, currency, { it.name },
                    onSelect = { currency = it }, modifier = Modifier.fillMaxWidth())
                if (existing != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(archived, { archived = it }); Text(PortfolioDisplay.ACCOUNT_ARCHIVE, color = StockStepsTheme.colors.textBody)
                }
                Text(PortfolioDisplay.ACCOUNT_NOTE, style = StockStepsTheme.typography.caption, color = StockStepsTheme.colors.textMeta)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(PortfolioAccount(existing?.id ?: PortfolioPresenter.newId(), name.trim(), category, currency, archived)) }, enabled = name.trim().length in 1..40) { Text(PortfolioDisplay.ACCOUNT_SAVE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(PortfolioDisplay.FORM_CANCEL) } }
    )
}

/** Plain choice button for already-readable strings (the Insights sample-scenario picker); forms use `StockSelectField`. */
@Composable
internal fun PortfolioChoice(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: $selected") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
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
    // The saved choice is an account id (null = not chosen yet); names aren't unique, so the id is what is shown and submitted (A-F1).
    var accountChoice by rememberSaveable { mutableStateOf(existing?.accountId) }
    val accountId = PortfolioFormRules.resolveAccountId(accountChoice, state.accounts, state.selectedAccountId).orEmpty()
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
    // Pin the dashboard's account once, as before: a later dashboard change never moves an open form to another account.
    LaunchedEffect(accountId) { if (accountChoice == null && accountId.isNotEmpty()) accountChoice = accountId }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(StockStepsTheme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)
    ) {
        Text(if (existing == null) PortfolioDisplay.FORM_ADD_TITLE else PortfolioDisplay.FORM_EDIT_TITLE, Modifier.semantics { heading() },
            style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textTitle)
        Text(PortfolioDisplay.FORM_INTRO, style = StockStepsTheme.typography.small, color = StockStepsTheme.colors.textSupporting)
        // Same fields, order and labels as SwiftUI (PortfolioFormRules); number fields keep the typed text exactly (no rounding).
        PortfolioFormRules.visibleFields(type, symbol, cashCurrency != currency).forEach { field ->
            val label = PortfolioFormRules.label(field, type, currency.name, cashCurrency.name)
            val keyboard = if (PortfolioFormRules.decimal(field)) KeyboardType.Decimal else KeyboardType.Text
            val fill = Modifier.fillMaxWidth()
            when (field) {
                PortfolioFormField.ACCOUNT -> {
                    val options = PortfolioFormRules.accountOptions(state.accounts, accountId)
                    val selected = options.firstOrNull { it.id == accountId }
                    StockSelectField(label, options, selected, optionLabel = { it.label }, onSelect = { accountChoice = it.id }, modifier = fill,
                        placeholder = PortfolioDisplay.CHOOSE_ACCOUNT, supporting = selected?.detail)
                }
                PortfolioFormField.TRANSACTION_TYPE -> StockSelectField(label, PortfolioFormRules.typeOptions, type, PortfolioPresentation::transactionLabel,
                    onSelect = { type = it }, modifier = fill)
                PortfolioFormField.TRADE_CURRENCY -> StockSelectField(label, PortfolioFormRules.currencyOptions, currency, { it.name },
                    onSelect = { if (cashCurrency == currency) cashCurrency = it; currency = it }, modifier = fill)
                PortfolioFormField.CASH_CURRENCY -> StockSelectField(label, PortfolioFormRules.currencyOptions, cashCurrency, { it.name },
                    onSelect = { cashCurrency = it }, modifier = fill)
                PortfolioFormField.SYMBOL -> StockTextField(symbol, { symbol = it.trim().uppercase() }, label, fill, keyboardType = KeyboardType.Ascii)
                PortfolioFormField.COMPANY_NAME -> StockTextField(name, { name = it }, label, fill)
                PortfolioFormField.EXCHANGE -> StockTextField(exchange, { exchange = it }, label, fill)
                PortfolioFormField.SHARES -> StockTextField(quantity, { quantity = it }, label, fill, keyboardType = keyboard)
                PortfolioFormField.PRICE -> StockTextField(price, { price = it }, label, fill, keyboardType = keyboard)
                PortfolioFormField.AMOUNT -> StockTextField(amount, { amount = it }, label, fill, keyboardType = keyboard)
                PortfolioFormField.FEES -> StockTextField(fees, { fees = it }, label, fill, keyboardType = keyboard)
                PortfolioFormField.FX_RATE -> StockTextField(fxRate, { fxRate = it }, label, fill, keyboardType = keyboard)
                PortfolioFormField.DATE -> StockTextField(date, { date = it }, label, fill)
                PortfolioFormField.SETTLEMENT -> StockTextField(settlement, { settlement = it }, label, fill)
                PortfolioFormField.NOTES -> StockTextField(notes, { notes = it }, label, fill, singleLine = false, minLines = 2)
            }
        }
        (error ?: state.error)?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = StockStepsTheme.typography.small,
            color = StockStepsTheme.colors.negativeText) }
        StockButton(
            if (state.busy) PortfolioDisplay.FORM_SAVING else PortfolioDisplay.FORM_SAVE,
            onClick = {
                try {
                    val transaction = PortfolioFormRules.transaction(id, accountId, type, date, currency, symbol, name, exchange,
                        existing?.instrument?.currency ?: initialCurrency ?: currency.name, quantity, price, amount, fees, cashCurrency, fxRate, settlement, notes)
                    PortfolioEngine.validate(transaction)
                    error = null
                    onSubmit(transaction)
                } catch (cause: IllegalArgumentException) { error = cause.message }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy && accountId.isNotEmpty()
        )
    }
}

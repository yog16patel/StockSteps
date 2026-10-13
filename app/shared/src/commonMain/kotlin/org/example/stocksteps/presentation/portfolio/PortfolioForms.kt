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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import org.example.stocksteps.designsystem.components.StockBanner
import org.example.stocksteps.designsystem.components.StockButton
import org.example.stocksteps.designsystem.components.StockButtonVariant
import org.example.stocksteps.designsystem.components.StockDateField
import org.example.stocksteps.designsystem.components.StockSelectField
import org.example.stocksteps.designsystem.components.StockTextField
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.theme.StockBannerKind
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.portfolio.*
import kotlinx.coroutines.launch

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

/**
 * Portfolio transaction form (Phase D1): "Transaction information" (type, date, account, security), "Amounts" (only the fields the type uses,
 * labelled with their currency), then "More options" collapsed. Dates use the Material picker and are stored as YYYY-MM-DD; typed numbers are
 * sent exactly as entered. Errors from the existing checks (`PortfolioFormRules.check` → `PortfolioEngine.validate`) sit next to their field.
 * Sample scenarios show why entry is unavailable instead of a save that would do nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PortfolioTransactionForm(
    state: PortfolioUiState,
    modifier: Modifier = Modifier,
    initialSymbol: String,
    initialName: String?,
    initialExchange: String?,
    initialCurrency: String?,
    existing: PortfolioTransaction? = null,
    /** Shown as the form's heading (the edit sheet has no top bar); the Add screen's top bar already names it. */
    showTitle: Boolean = false,
    onSearch: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onUseSavedAccounts: (() -> Unit)? = null,
    onSubmit: (PortfolioTransaction) -> Unit
) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    if (PortfolioFormRules.readOnly(state)) {
        PortfolioReadOnlyEntry(modifier, onUseSavedAccounts)
        return
    }
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
    // Field errors survive recomposition and rotation ("FIELD|message"); a field's error clears only when that field changes or on the next check.
    var errorList by rememberSaveable { mutableStateOf(listOf<String>()) }
    val errors = errorList.associate { PortfolioFormField.valueOf(it.substringBefore('|')) to it.substringAfter('|') }
    var formMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var moreOpen by rememberSaveable {
        mutableStateOf(PortfolioFormRules.moreOptionsOpen(mapOf(PortfolioFormField.FX_RATE to fxRate, PortfolioFormField.SETTLEMENT to settlement,
            PortfolioFormField.NOTES to notes), emptySet(), currency.name, cashCurrency.name))
    }
    var picking by rememberSaveable { mutableStateOf<PortfolioFormField?>(null) }
    // The presenter's failure belongs to this form only after this form submitted (an earlier dashboard error is not a save error).
    var attempted by rememberSaveable { mutableStateOf(false) }
    var previousSecurity by rememberSaveable { mutableStateOf(initialSymbol) }
    fun edited(field: PortfolioFormField) { if (errors.containsKey(field)) errorList = errorList.filterNot { it.startsWith("${field.name}|") } }
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
            edited(PortfolioFormField.SYMBOL)
        }
    }
    // Pin the dashboard's account once, as before: a later dashboard change never moves an open form to another account.
    LaunchedEffect(accountId) { if (accountChoice == null && accountId.isNotEmpty()) accountChoice = accountId }
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val cashDiffers = cashCurrency != currency
    val sections = PortfolioFormSection.entries.associateWith { PortfolioFormRules.sectionFields(it, type, symbol, cashDiffers) }
    // Keyboard: Next through the visible text fields, Done on the last one.
    val textFields = sections.filterKeys { it != PortfolioFormSection.MORE || moreOpen }.values.flatten().filter {
        it in setOf(PortfolioFormField.SYMBOL, PortfolioFormField.COMPANY_NAME, PortfolioFormField.EXCHANGE) || PortfolioFormRules.decimal(it)
    }
    fun ime(field: PortfolioFormField) = if (field == textFields.lastOrNull()) ImeAction.Done else ImeAction.Next
    // Explicit order: Next always lands in the next input, never on a link or picker between them.
    val requesters = remember { PortfolioFormField.entries.associateWith { FocusRequester() } }
    fun actions(field: PortfolioFormField) = KeyboardActions(
        onNext = { textFields.getOrNull(textFields.indexOf(field) + 1)?.let { runCatching { requesters.getValue(it).requestFocus() } } ?: focus.clearFocus() },
        onDone = { focus.clearFocus() })
    fun Modifier.order(field: PortfolioFormField) = focusRequester(requesters.getValue(field))

    fun submit() {
        val transaction = PortfolioFormRules.visibleTransaction(id, accountId, type, date, currency, symbol, name, exchange,
            existing?.instrument?.currency ?: initialCurrency ?: currency.name, quantity, price, amount, fees, cashCurrency, fxRate, settlement, notes)
        val check = PortfolioFormRules.check(transaction, mapOf(PortfolioFormField.SHARES to quantity, PortfolioFormField.PRICE to price,
            PortfolioFormField.AMOUNT to amount, PortfolioFormField.FEES to fees, PortfolioFormField.FX_RATE to fxRate))
        errorList = check.errors.map { (field, message) -> "${field.name}|$message" }
        formMessage = check.message ?: if (check.errors.isNotEmpty()) PortfolioDisplay.FORM_ERRORS_SUMMARY else null
        if (check.errors.keys.any { PortfolioFormRules.section(it) == PortfolioFormSection.MORE }) moreOpen = true
        if (check.valid) { focus.clearFocus(); attempted = true; onSubmit(transaction); return }
        // Bring the first problem into view: focus its input, or scroll to the top for pickers (type, date, account).
        val first = PortfolioFormRules.redesignOrder.firstOrNull { it in check.errors }
        if (first != null && first in textFields) runCatching { requesters.getValue(first).requestFocus() }
        else if (first != null && PortfolioFormRules.section(first) == PortfolioFormSection.DETAILS) scope.launch { scroll.animateScrollTo(0) }
    }

    @Composable
    fun field(field: PortfolioFormField) {
        val label = PortfolioFormRules.label(field, type, currency.name, cashCurrency.name)
        val fill = Modifier.fillMaxWidth()
        val error = errors[field]
        val number = KeyboardType.Decimal
        when (field) {
            PortfolioFormField.ACCOUNT -> {
                val options = PortfolioFormRules.accountOptions(state.accounts, accountId)
                val selected = options.firstOrNull { it.id == accountId }
                StockSelectField(label, options, selected, optionLabel = { it.label }, onSelect = { accountChoice = it.id }, modifier = fill,
                    placeholder = PortfolioDisplay.CHOOSE_ACCOUNT, supporting = selected?.detail)
            }
            PortfolioFormField.TRANSACTION_TYPE -> StockSelectField(label, PortfolioFormRules.typeOptions, type, PortfolioPresentation::transactionLabel,
                onSelect = { type = it; formMessage = null; attempted = false }, modifier = fill) // the last save's error was about the old type
            PortfolioFormField.TRADE_CURRENCY -> StockSelectField(label, PortfolioFormRules.currencyOptions, currency, { it.name },
                onSelect = { if (cashCurrency == currency) cashCurrency = it; currency = it }, modifier = fill)
            PortfolioFormField.CASH_CURRENCY -> StockSelectField(label, PortfolioFormRules.currencyOptions, cashCurrency, { it.name },
                onSelect = { cashCurrency = it }, modifier = fill)
            PortfolioFormField.SYMBOL -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                StockTextField(symbol, { symbol = it.trim().uppercase(); edited(field) }, label, fill.order(field), error = error, keyboardType = KeyboardType.Ascii,
                    imeAction = ime(field), keyboardActions = actions(field))
                onSearch?.let { StockButton(PortfolioDisplay.SEARCH_SECURITY, it, variant = StockButtonVariant.TEXT, icon = StockIcons.Search) }
            }
            PortfolioFormField.COMPANY_NAME -> StockTextField(name, { name = it }, label, fill.order(field), imeAction = ime(field), keyboardActions = actions(field))
            PortfolioFormField.EXCHANGE -> StockTextField(exchange, { exchange = it }, label, fill.order(field), imeAction = ime(field), keyboardActions = actions(field))
            PortfolioFormField.SHARES -> StockTextField(quantity, { quantity = it; edited(field) }, label, fill.order(field), error = error, keyboardType = number, imeAction = ime(field), keyboardActions = actions(field))
            PortfolioFormField.PRICE -> StockTextField(price, { price = it; edited(field) }, label, fill.order(field), error = error, keyboardType = number, imeAction = ime(field), keyboardActions = actions(field))
            PortfolioFormField.AMOUNT -> StockTextField(amount, { amount = it; edited(field) }, label, fill.order(field), error = error, keyboardType = number, imeAction = ime(field), keyboardActions = actions(field))
            PortfolioFormField.FEES -> StockTextField(fees, { fees = it; edited(field) }, label, fill.order(field), error = error, keyboardType = number, imeAction = ime(field), keyboardActions = actions(field))
            PortfolioFormField.FX_RATE -> StockTextField(fxRate, { fxRate = it; edited(field) }, label, fill.order(field), error = error, keyboardType = number, imeAction = ime(field), keyboardActions = actions(field))
            PortfolioFormField.DATE -> StockDateField(label, PortfolioFormDates.display(date), { focus.clearFocus(); picking = field }, fill,
                placeholder = PortfolioDisplay.FORM_CHOOSE_DATE, error = error, actionLabel = PortfolioDisplay.FORM_CHOOSE_DATE)
            PortfolioFormField.SETTLEMENT -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                StockDateField(label, PortfolioFormDates.display(settlement), { focus.clearFocus(); picking = field }, fill,
                    placeholder = PortfolioDisplay.FORM_ADD_SETTLEMENT, error = error, actionLabel = PortfolioDisplay.FORM_CHOOSE_DATE)
                if (settlement.isNotBlank()) StockButton(PortfolioDisplay.FORM_REMOVE_SETTLEMENT, { settlement = ""; edited(field) }, variant = StockButtonVariant.TEXT)
            }
            PortfolioFormField.NOTES -> StockTextField(notes, { notes = it; edited(field) }, label, fill, error = error, singleLine = false, minLines = 2)
        }
    }

    @Composable
    fun sectionTitle(section: PortfolioFormSection) =
        Text(PortfolioFormRules.sectionTitle(section), Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textTitle)

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(scroll).padding(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            if (showTitle) Text(if (existing == null) PortfolioDisplay.FORM_ADD_TITLE else PortfolioDisplay.FORM_EDIT_TITLE, Modifier.semantics { heading() },
                style = typography.sectionTitle, color = colors.textTitle)
            Text(PortfolioDisplay.FORM_INTRO, style = typography.small, color = colors.textSupporting)
        }
        listOf(PortfolioFormSection.DETAILS, PortfolioFormSection.AMOUNTS).forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                sectionTitle(section)
                sections.getValue(section).forEach { field(it) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
                .clickable(role = Role.Button, onClickLabel = if (moreOpen) "Collapse" else "Expand") { moreOpen = !moreOpen }
                .semantics { stateDescription = if (moreOpen) "Expanded" else "Collapsed" },
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(PortfolioDisplay.FORM_SECTION_MORE, Modifier.semantics { heading() }, style = typography.cardTitle, color = colors.textTitle)
                    if (!moreOpen) Text(PortfolioDisplay.FORM_MORE_HINT, style = typography.caption, color = colors.textSupporting)
                }
                Icon(StockIcons.ChevronDown, contentDescription = null, tint = colors.iconSecondary,
                    modifier = Modifier.size(StockStepsTheme.dimensions.icon).graphicsLayer { rotationZ = if (moreOpen) 180f else 0f })
            }
            if (moreOpen) sections.getValue(PortfolioFormSection.MORE).forEach { field(it) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            formMessage?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = typography.small, color = colors.negativeText) }
            state.error?.takeIf { attempted }?.let { StockBanner(StockBannerKind.ERROR, it, title = PortfolioDisplay.FORM_SAVE_FAILED_TITLE) }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                onCancel?.let { StockButton(PortfolioDisplay.FORM_CANCEL, it, Modifier.weight(1f), variant = StockButtonVariant.SECONDARY, enabled = !state.busy) }
                StockButton(if (state.busy) PortfolioDisplay.FORM_SAVING else PortfolioDisplay.FORM_SAVE, ::submit, Modifier.weight(1f),
                    enabled = !state.busy && accountId.isNotEmpty())
            }
        }
    }

    picking?.let { field ->
        val current = if (field == PortfolioFormField.SETTLEMENT) settlement.ifBlank { date } else date
        val picker = rememberDatePickerState(initialSelectedDateMillis = PortfolioFormDates.epochDay(current)?.let { it * MILLIS_PER_DAY })
        DatePickerDialog(onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val chosen = PortfolioFormDates.date(millis.floorDiv(MILLIS_PER_DAY))
                        if (field == PortfolioFormField.SETTLEMENT) settlement = chosen else date = chosen
                        edited(field)
                    }
                    picking = null
                }) { Text(PortfolioDisplay.SCENARIO_DONE) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(PortfolioDisplay.FORM_CANCEL) } }) { DatePicker(picker) }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

/** Sample scenarios can't take transactions: say why and offer the existing switch back to the saved accounts (Phase D1). */
@Composable
private fun PortfolioReadOnlyEntry(modifier: Modifier, onUseSavedAccounts: (() -> Unit)?) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(spacing.screen), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        StockBanner(StockBannerKind.SAMPLE, PortfolioDisplay.SAMPLE_SHORT)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Icon(StockIcons.Lock, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.educationIcon))
            Text(PortfolioDisplay.FORM_READ_ONLY_TITLE, Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle,
                color = colors.textTitle, textAlign = TextAlign.Center)
            Text(PortfolioDisplay.FORM_READ_ONLY_MESSAGE, style = StockStepsTheme.typography.body, color = colors.textBody, textAlign = TextAlign.Center)
        }
        onUseSavedAccounts?.let { StockButton(PortfolioDisplay.FORM_READ_ONLY_ACTION, it, Modifier.fillMaxWidth()) }
    }
}

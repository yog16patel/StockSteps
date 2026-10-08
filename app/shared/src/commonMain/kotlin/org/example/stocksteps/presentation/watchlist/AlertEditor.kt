package org.example.stocksteps.presentation.watchlist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.model.*
import org.example.stocksteps.resources.*
import org.jetbrains.compose.resources.stringResource

/** Notification permission as seen by shared UI; platforms supply the real implementation. */
interface NotificationAccess {
    val enabled: Boolean
    /** Shows the system prompt (after the app has explained why); returns whether push is now allowed. */
    suspend fun request(): Boolean
}

private enum class AlertKind(val label: String, val type: AlertType) {
    ABOVE("Price above", AlertType.PRICE_ABOVE), BELOW("Price below", AlertType.PRICE_BELOW),
    MOVE("Daily move", AlertType.DAILY_MOVE), EARNINGS("Earnings", AlertType.EARNINGS), NEWS("News", AlertType.NEWS)
}

/**
 * Create-alert sheet for one stock. Explains what each alert does and how fresh the data is; asks
 * before notifying when a price condition is already met; explains notifications before asking the
 * system for permission, and keeps the alert even if permission is denied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlertEditorSheet(
    instrument: InstrumentRef,
    currentPrice: String?,
    deliveryNote: String?,
    notifications: NotificationAccess?,
    onSubmit: suspend (CreateAlertRequest) -> AlertSubmitResult,
    onDismiss: () -> Unit
) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(AlertKind.ABOVE) }
    var value by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(MoveDirection.EITHER) }
    var timing by remember { mutableStateOf(EarningsTiming.BOTH) }
    var repeat by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var alreadyMet by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var pushAllowed by remember { mutableStateOf(notifications?.enabled ?: false) }
    val currency = instrument.currency ?: "USD"

    fun request(choice: AlreadyMetChoice?): CreateAlertRequest? {
        val number = value.trim().replace(",", "").toDoubleOrNull()
        val needsNumber = kind == AlertKind.ABOVE || kind == AlertKind.BELOW || kind == AlertKind.MOVE
        if (needsNumber && (number == null || number <= 0)) {
            error = if (kind == AlertKind.MOVE) "Enter a percentage, for example 5." else "Enter a target price greater than zero."
            return null
        }
        return CreateAlertRequest(
            instrument = instrument,
            type = kind.type,
            threshold = if (needsNumber) number else null,
            currency = if (kind == AlertKind.ABOVE || kind == AlertKind.BELOW) currency else null,
            direction = if (kind == AlertKind.MOVE) direction else null,
            earningsTiming = if (kind == AlertKind.EARNINGS) timing else null,
            repeat = if (kind == AlertKind.ABOVE || kind == AlertKind.BELOW) (if (repeat) RepeatPolicy.REPEAT else RepeatPolicy.ONCE) else null,
            whenAlreadyMet = choice
        )
    }

    fun submit(choice: AlertMetChoiceHolder = AlertMetChoiceHolder(null)) {
        val body = request(choice.value) ?: return
        submitting = true
        error = null
        scope.launch {
            when (val result = onSubmit(body)) {
                AlertSubmitResult.Created -> onDismiss()
                is AlertSubmitResult.AlreadyMet -> alreadyMet = result.message
                is AlertSubmitResult.Failed -> error = result.message
            }
            submitting = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().padding(start = spacing.screen, end = spacing.screen, bottom = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(stringResource(Res.string.alert_create_title, instrument.symbol), Modifier.semantics { heading() },
                    style = StockStepsTheme.typography.sectionTitle, color = colors.textPrimary)
                listOfNotNull(instrument.name, currentPrice?.let { "Now $it $currency" }).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = StockStepsTheme.typography.small, color = colors.textSecondary)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                AlertKind.entries.forEach { option ->
                    StockChip(option.label, option == kind, onClick = { kind = option; error = null; alreadyMet = null })
                }
            }
            when (kind) {
                AlertKind.ABOVE, AlertKind.BELOW -> {
                    StockTextField(value, { value = it; error = null; alreadyMet = null }, label = stringResource(Res.string.alert_target_price),
                        placeholder = "0.00", prefix = currency, keyboardType = KeyboardType.Decimal, error = error)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget)
                            .toggleable(value = repeat, role = Role.Switch, onValueChange = { repeat = it }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(Res.string.alert_repeat), Modifier.weight(1f), style = StockStepsTheme.typography.body, color = colors.textBody)
                        Switch(checked = repeat, onCheckedChange = null)
                    }
                    Text(stringResource(if (kind == AlertKind.ABOVE) Res.string.alert_above_help else Res.string.alert_below_help),
                        style = StockStepsTheme.typography.caption, color = colors.textSecondary)
                }
                AlertKind.MOVE -> {
                    StockTextField(value, { value = it; error = null }, label = stringResource(Res.string.alert_move_percent),
                        placeholder = "5", keyboardType = KeyboardType.Decimal, error = error, supporting = stringResource(Res.string.alert_move_help))
                    StockPillSelector(MoveDirection.entries, direction, label = { when (it) { MoveDirection.UP -> "Up"; MoveDirection.DOWN -> "Down"; MoveDirection.EITHER -> "Either way" } }, onSelect = { direction = it })
                }
                AlertKind.EARNINGS -> {
                    StockPillSelector(EarningsTiming.entries, timing, label = { when (it) { EarningsTiming.DAY_BEFORE -> "Day before"; EarningsTiming.DAY_OF -> "Day of"; EarningsTiming.BOTH -> "Both" } }, onSelect = { timing = it })
                    Text(stringResource(Res.string.alert_earnings_help), style = StockStepsTheme.typography.caption, color = colors.textSecondary)
                }
                AlertKind.NEWS -> Text(stringResource(Res.string.alert_news_help), style = StockStepsTheme.typography.caption, color = colors.textSecondary)
            }
            if (notifications != null && !pushAllowed) {
                StockCard(bordered = false, containerColor = colors.educationContainer, contentPadding = PaddingValues(spacing.md)) {
                    Text(stringResource(Res.string.alert_push_why), style = StockStepsTheme.typography.small, color = colors.textBody)
                    StockButton(stringResource(Res.string.alert_push_enable), onClick = { scope.launch { pushAllowed = notifications.request() } },
                        modifier = Modifier.padding(top = spacing.sm), variant = StockButtonVariant.SECONDARY)
                }
            }
            deliveryNote?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textTertiary) }
            if (kind != AlertKind.ABOVE && kind != AlertKind.BELOW) error?.let { Text(it, style = StockStepsTheme.typography.small, color = colors.negativeText) }
            val met = alreadyMet
            if (met != null) {
                StockCard(bordered = false, containerColor = colors.cautionContainer, contentPadding = PaddingValues(spacing.md)) {
                    Text(met, style = StockStepsTheme.typography.body, color = colors.textPrimary)
                    Text(stringResource(Res.string.alert_already_met), Modifier.padding(top = spacing.xxs), style = StockStepsTheme.typography.small, color = colors.textBody)
                    Row(Modifier.padding(top = spacing.sm), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        StockButton(stringResource(Res.string.alert_notify_now), onClick = { submit(AlertMetChoiceHolder(AlreadyMetChoice.NOTIFY_NOW)) }, enabled = !submitting, variant = StockButtonVariant.SECONDARY)
                        StockButton(stringResource(Res.string.alert_wait_cross), onClick = { submit(AlertMetChoiceHolder(AlreadyMetChoice.WAIT_FOR_CROSS)) }, enabled = !submitting)
                    }
                }
            } else {
                StockButton(stringResource(Res.string.alert_create), onClick = { submit() }, enabled = !submitting, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Wraps the optional choice so `submit()` can be called without arguments. */
private class AlertMetChoiceHolder(val value: AlreadyMetChoice?)

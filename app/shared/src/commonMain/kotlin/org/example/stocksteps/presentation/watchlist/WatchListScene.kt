package org.example.stocksteps.presentation.watchlist

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.model.InstrumentRef

/** Owns the Watchlist and alert-editor ViewModels and wires navigation. */
@Composable
internal fun WatchListScene(
    accounts: AccountDependencies,
    hinge: WindowHinge?,
    notifications: NotificationAccess?,
    onSignIn: () -> Unit,
    onSearch: () -> Unit,
    onOpenStock: (String) -> Unit,
    onAddPortfolio: (InstrumentRef) -> Unit,
    onOpenAlerts: (String?) -> Unit,
    onEarnings: () -> Unit = {},
    onEarningsReminders: () -> Unit = {}
) {
    val model = viewModel(key = "watchlist") {
        WatchListViewModel(accounts.auth, accounts.watchlists, accounts.alerts, accounts.watchData, accounts.watchlist, accounts.environment)
    }
    val alerts = viewModel(key = "alerts-editor") { AlertsViewModel(accounts.auth, accounts.alerts, null) }
    val state by model.state.collectAsStateWithLifecycle()
    val alertsState by alerts.state.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<Pair<InstrumentRef, String?>?>(null) }
    WatchListScreen(state, hinge) { action ->
        when (action) {
            is WatchListAction.AddPortfolio -> onAddPortfolio(action.instrument)
            WatchListAction.Refresh -> model.refresh()
            WatchListAction.SignIn -> onSignIn()
            WatchListAction.Search -> onSearch()
            is WatchListAction.OpenAlerts -> onOpenAlerts(action.symbol)
            is WatchListAction.Select -> model.select(action.id)
            is WatchListAction.OpenStock -> onOpenStock(action.symbol)
            is WatchListAction.CreateList -> model.createList(action.name)
            is WatchListAction.RenameList -> model.renameList(action.id, action.name)
            is WatchListAction.DeleteList -> model.deleteList(action.id)
            is WatchListAction.Remove -> model.remove(action.entryId)
            is WatchListAction.RemoveGuest -> model.removeGuest(action.symbol)
            is WatchListAction.SaveNote -> model.saveNote(action.entryId, action.note)
            is WatchListAction.Move -> model.move(action.entryId, action.targetId, action.copy)
            is WatchListAction.Shift -> model.shift(action.entryId, action.by)
            is WatchListAction.AddAlert -> editor = action.instrument to action.price
            WatchListAction.DismissMessage -> model.dismissMessage()
            WatchListAction.Earnings -> onEarnings()
            WatchListAction.EarningsReminders -> onEarningsReminders()
        }
    }
    editor?.let { (instrument, price) ->
        AlertEditorSheet(instrument, price, alertsState.alerts.value?.deliveryNote, notifications, onSubmit = alerts::submit, onDismiss = { editor = null })
    }
}

package org.example.stocksteps.presentation.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.model.InstrumentRef
import org.example.stocksteps.model.Watchlist
import org.example.stocksteps.presentation.AdaptiveSinglePane
import org.example.stocksteps.resources.*
import org.example.stocksteps.watchlist.WatchlistModel
import org.example.stocksteps.watchlist.WatchlistRowModel
import org.jetbrains.compose.resources.stringResource

internal sealed interface WatchListAction {
    data object Refresh : WatchListAction
    data object SignIn : WatchListAction
    data object Search : WatchListAction
    data class OpenAlerts(val symbol: String?) : WatchListAction
    data class Select(val id: String) : WatchListAction
    data class OpenStock(val symbol: String) : WatchListAction
    data class CreateList(val name: String) : WatchListAction
    data class RenameList(val id: String, val name: String) : WatchListAction
    data class DeleteList(val id: String) : WatchListAction
    data class Remove(val entryId: String) : WatchListAction
    data class RemoveGuest(val symbol: String) : WatchListAction
    data class SaveNote(val entryId: String, val note: String?) : WatchListAction
    data class Move(val entryId: String, val targetId: String, val copy: Boolean) : WatchListAction
    data class Shift(val entryId: String, val by: Int) : WatchListAction
    data class AddAlert(val instrument: InstrumentRef, val price: String?) : WatchListAction
    data object DismissMessage : WatchListAction
}

private sealed interface Sheet {
    data class RowActions(val row: WatchlistRowModel, val index: Int) : Sheet
    data class Note(val row: WatchlistRowModel) : Sheet
    data class MoveTo(val row: WatchlistRowModel, val copy: Boolean) : Sheet
    data class Name(val list: Watchlist?) : Sheet
    data class ConfirmDelete(val list: Watchlist) : Sheet
}

/**
 * Watchlist tab. Signed in: list chips, today's summary (equal-weighted, documented), insights,
 * stock rows with alert indicators and note previews, and an overflow menu per row. Signed out:
 * the on-device list with an invitation to sign in for lists, notes and alerts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchListScreen(state: WatchListState, hinge: WindowHinge?, onAction: (WatchListAction) -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val model = remember(state) { if (state.signedIn) state.model else state.guestModel }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val content = Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxWidth()
    Box(Modifier.fillMaxSize().background(colors.appBackground)) {
        AdaptiveSinglePane(hinge) { region ->
            PullToRefreshBox(isRefreshing = state.lists.loading && state.lists.value != null, onRefresh = { onAction(WatchListAction.Refresh) }, modifier = region) {
                LazyColumn(
                    contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "header") { Header(state, model, content.padding(top = spacing.lg), onAction, onManage = { sheet = it }) }
                    if (state.signedIn && model == null) {
                        item(key = "status") {
                            if (state.lists.loading || state.session.initializing) StockLoadingState(content.padding(top = spacing.lg)) { repeat(4) { StockRowSkeleton() } }
                            else StockCard(content.padding(top = spacing.lg), bordered = false) {
                                StockErrorState(state.lists.error ?: stringResource(Res.string.watchlist_load_failed), { onAction(WatchListAction.Refresh) })
                            }
                        }
                        return@LazyColumn
                    }
                    if (model == null) return@LazyColumn
                    if (state.signedIn && model.tabs.isNotEmpty()) {
                        item(key = "tabs") { Tabs(model, content.padding(top = spacing.md), onAction, onNew = { sheet = Sheet.Name(null) }) }
                    }
                    model.summary?.takeIf { model.rows.isNotEmpty() }?.let { summary ->
                        item(key = "summary") { SummaryCard(summary, content.padding(top = spacing.md)) }
                    }
                    if (state.lists.offline && state.lists.error != null) {
                        item(key = "offline") { Text(state.lists.error!!, content.padding(top = spacing.sm), style = StockStepsTheme.typography.caption, color = colors.cautionText) }
                    }
                    if (!state.signedIn) item(key = "signin") { SignInCard(content.padding(top = spacing.md)) { onAction(WatchListAction.SignIn) } }
                    model.emptyMessage?.let { message ->
                        item(key = "empty") {
                            StockCard(content.padding(top = spacing.md), bordered = false) {
                                StockEmptyState(if (state.signedIn) message else stringResource(Res.string.watchlist_guest_empty))
                                StockButton(stringResource(Res.string.watchlist_find_stocks), onClick = { onAction(WatchListAction.Search) },
                                    modifier = Modifier.padding(top = spacing.sm), variant = StockButtonVariant.SECONDARY)
                            }
                        }
                    }
                    if (model.rows.isNotEmpty()) {
                        // At most 100 rows per list, so one card keeps the grouped layout simple.
                        item(key = "rows") {
                            StockCard(content.padding(top = spacing.md), bordered = false, contentPadding = PaddingValues(vertical = spacing.xxs)) {
                                model.rows.forEachIndexed { index, row ->
                                    if (index > 0) StockDivider(startIndent = spacing.cardPadding)
                                    StockRowLine(row, state.signedIn, onAction, onMore = { sheet = Sheet.RowActions(row, index) })
                                }
                            }
                        }
                    }
                    if (model.insights.isNotEmpty()) {
                        item(key = "insights") { InsightsCard(model.insights, content.padding(top = spacing.xl)) }
                    }
                    item(key = "disclaimer") {
                        Text(stringResource(Res.string.watchlist_disclaimer), content.padding(top = spacing.lg), style = StockStepsTheme.typography.caption, color = colors.textTertiary)
                    }
                }
            }
        }
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = { onAction(WatchListAction.DismissMessage) },
            containerColor = colors.surface,
            text = { Text(message, style = StockStepsTheme.typography.body, color = colors.textBody) },
            confirmButton = { StockButton(stringResource(Res.string.action_ok), onClick = { onAction(WatchListAction.DismissMessage) }, variant = StockButtonVariant.TEXT) }
        )
    }
    when (val open = sheet) {
        is Sheet.RowActions -> RowActionsSheet(open, state, onAction, onSheet = { sheet = it }, onDismiss = { sheet = null })
        is Sheet.Note -> NoteSheet(open.row, onSave = { onAction(WatchListAction.SaveNote(open.row.entryId, it)); sheet = null }, onDismiss = { sheet = null })
        is Sheet.MoveTo -> MoveSheet(open, state.watchlists.filter { it.id != state.selected?.id }, onPick = { target ->
            onAction(WatchListAction.Move(open.row.entryId, target, open.copy)); sheet = null
        }, onDismiss = { sheet = null })
        is Sheet.Name -> NameDialog(open.list, onSave = { name ->
            onAction(if (open.list == null) WatchListAction.CreateList(name) else WatchListAction.RenameList(open.list.id, name)); sheet = null
        }, onDismiss = { sheet = null })
        is Sheet.ConfirmDelete -> AlertDialog(
            onDismissRequest = { sheet = null },
            containerColor = colors.surface,
            title = { Text(stringResource(Res.string.watchlist_delete_title, open.list.name), style = StockStepsTheme.typography.sectionTitle, color = colors.textPrimary) },
            text = { Text(stringResource(Res.string.watchlist_delete_body, open.list.entries.size), style = StockStepsTheme.typography.body, color = colors.textBody) },
            dismissButton = { StockButton(stringResource(Res.string.action_cancel), onClick = { sheet = null }, variant = StockButtonVariant.TEXT) },
            confirmButton = { StockButton(stringResource(Res.string.watchlist_delete), onClick = { onAction(WatchListAction.DeleteList(open.list.id)); sheet = null }, variant = StockButtonVariant.DESTRUCTIVE) }
        )
        null -> Unit
    }
}

@Composable
private fun Header(state: WatchListState, model: WatchlistModel?, modifier: Modifier, onAction: (WatchListAction) -> Unit, onManage: (Sheet) -> Unit) {
    val colors = StockStepsTheme.colors
    var menu by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
            Text(stringResource(Res.string.watchlist_title), Modifier.semantics { heading() }, style = StockStepsTheme.typography.screenTitle, color = colors.textPrimary)
            Text(stringResource(Res.string.watchlist_subtitle), style = StockStepsTheme.typography.small, color = colors.textSecondary)
        }
        IconButton(onClick = { onAction(WatchListAction.Search) }) { Icon(StockIcons.Search, stringResource(Res.string.markets_search), tint = colors.textPrimary) }
        if (state.signedIn) {
            IconButton(onClick = { onAction(WatchListAction.OpenAlerts(null)) }) { Icon(StockIcons.Bell, stringResource(Res.string.alerts_title), tint = colors.textPrimary) }
            val selected = state.selected
            if (model != null && selected != null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(StockIcons.More, stringResource(Res.string.watchlist_manage), tint = colors.textPrimary) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.surface) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.watchlist_new)) }, onClick = { menu = false; onManage(Sheet.Name(null)) })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.watchlist_rename)) }, onClick = { menu = false; onManage(Sheet.Name(selected)) })
                        if (state.watchlists.size > 1) DropdownMenuItem(text = { Text(stringResource(Res.string.watchlist_delete)) }, onClick = {
                            menu = false
                            if (selected.entries.isEmpty()) onAction(WatchListAction.DeleteList(selected.id)) else onManage(Sheet.ConfirmDelete(selected))
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun Tabs(model: WatchlistModel, modifier: Modifier, onAction: (WatchListAction) -> Unit, onNew: () -> Unit) {
    Row(modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xs)) {
        model.tabs.forEach { tab -> StockChip("${tab.name} ${tab.count}", tab.selected, onClick = { onAction(WatchListAction.Select(tab.id)) }) }
        StockChip("+ ${stringResource(Res.string.watchlist_new_short)}", false, onClick = onNew)
    }
}

@Composable
private fun SummaryCard(summary: org.example.stocksteps.watchlist.WatchlistSummary, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    StockCard(modifier, bordered = false, contentPadding = PaddingValues(spacing.md)) {
        Text(summary.title.uppercase(), style = StockStepsTheme.typography.label, color = colors.textSecondary)
        Row(Modifier.padding(top = spacing.xxs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(summary.companies, Modifier.weight(1f), style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
            summary.change?.let { StockPriceChange(it, summary.direction, style = StockStepsTheme.typography.numberEmphasis) }
        }
        summary.methodology?.let { Text(it, style = StockStepsTheme.typography.caption, color = colors.textTertiary) }
        summary.updated?.let { Text(it, style = StockStepsTheme.typography.caption, color = if (summary.stale) colors.cautionText else colors.textTertiary) }
    }
}

@Composable
private fun StockRowLine(row: WatchlistRowModel, signedIn: Boolean, onAction: (WatchListAction) -> Unit, onMore: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            StockRow(
                symbol = row.symbol,
                name = row.name,
                logoUrl = row.logoUrl,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = row.accessibilityLabel },
                onClick = { onAction(WatchListAction.OpenStock(row.symbol)) },
                trailing = {
                    Text(listOfNotNull(row.price, row.currency).joinToString(" ").ifEmpty { "—" }, style = StockStepsTheme.typography.numberLabelStrong, color = colors.textPrimary, maxLines = 1)
                    StockPriceChange(row.change, row.direction)
                    row.staleLabel?.let { Text(it, style = StockStepsTheme.typography.tiny, color = colors.cautionText, maxLines = 1) }
                }
            )
            row.notePreview?.let {
                Text("“$it”", Modifier.padding(start = spacing.cardPadding + StockStepsTheme.dimensions.logo + spacing.sm, end = spacing.sm, bottom = spacing.sm),
                    style = StockStepsTheme.typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (signedIn && row.activeAlerts > 0) {
            IconButton(onClick = { onAction(WatchListAction.OpenAlerts(row.symbol)) }) {
                Icon(StockIcons.Bell, stringResource(Res.string.watchlist_alerts_for, row.activeAlerts, row.symbol), tint = colors.primaryText, modifier = Modifier.size(StockStepsTheme.dimensions.iconSmall))
            }
        }
        IconButton(onClick = onMore) { Icon(StockIcons.More, stringResource(Res.string.watchlist_row_actions, row.symbol), tint = colors.iconSecondary) }
    }
}

@Composable
private fun InsightsCard(insights: List<String>, modifier: Modifier) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    Column(modifier.clip(StockStepsTheme.shapes.card).background(colors.educationContainer).padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(stringResource(Res.string.watchlist_insights_title), Modifier.semantics { heading() }, style = StockStepsTheme.typography.cardTitle, color = colors.textPrimary)
        insights.forEach { Text("• $it", style = StockStepsTheme.typography.small, color = colors.textBody) }
    }
}

@Composable
private fun SignInCard(modifier: Modifier, onSignIn: () -> Unit) {
    StockInsightCard(
        title = stringResource(Res.string.watchlist_signin_title),
        body = stringResource(Res.string.watchlist_signin_body),
        actionText = stringResource(Res.string.watchlist_signin_action),
        onClick = onSignIn,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowActionsSheet(open: Sheet.RowActions, state: WatchListState, onAction: (WatchListAction) -> Unit, onSheet: (Sheet) -> Unit, onDismiss: () -> Unit) {
    val row = open.row
    val others = state.watchlists.size > 1
    val count = state.selected?.entries?.size ?: 0
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().padding(bottom = StockStepsTheme.spacing.xxl)) {
            Text(listOfNotNull(row.symbol, row.name).joinToString(" · "), Modifier.padding(horizontal = StockStepsTheme.spacing.screen, vertical = StockStepsTheme.spacing.sm).semantics { heading() },
                style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary)
            if (!state.signedIn) {
                SheetAction(stringResource(Res.string.watchlist_remove)) { onAction(WatchListAction.RemoveGuest(row.symbol)); onDismiss() }
                return@Column
            }
            SheetAction(stringResource(Res.string.alert_add)) {
                onAction(WatchListAction.AddAlert(InstrumentRef(row.symbol, row.name, currency = row.currency), row.price)); onDismiss()
            }
            SheetAction(stringResource(if (row.note == null) Res.string.watchlist_add_note else Res.string.watchlist_edit_note)) { onSheet(Sheet.Note(row)) }
            if (others) {
                SheetAction(stringResource(Res.string.watchlist_move)) { onSheet(Sheet.MoveTo(row, copy = false)) }
                SheetAction(stringResource(Res.string.watchlist_copy)) { onSheet(Sheet.MoveTo(row, copy = true)) }
            }
            if (open.index > 0) SheetAction(stringResource(Res.string.watchlist_move_up)) { onAction(WatchListAction.Shift(row.entryId, -1)); onDismiss() }
            if (open.index < count - 1) SheetAction(stringResource(Res.string.watchlist_move_down)) { onAction(WatchListAction.Shift(row.entryId, 1)); onDismiss() }
            SheetAction(stringResource(Res.string.watchlist_remove), destructive = true) { onAction(WatchListAction.Remove(row.entryId)); onDismiss() }
        }
    }
}

@Composable
private fun SheetAction(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        Modifier.fillMaxWidth().heightIn(min = StockStepsTheme.dimensions.touchTarget).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = StockStepsTheme.spacing.screen, vertical = StockStepsTheme.spacing.md),
        style = StockStepsTheme.typography.body,
        color = if (destructive) StockStepsTheme.colors.negativeText else StockStepsTheme.colors.textPrimary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(row: WatchlistRowModel, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    var text by remember { mutableStateOf(row.note.orEmpty()) }
    val tooLong = text.length > NOTE_LIMIT
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().padding(start = spacing.screen, end = spacing.screen, bottom = spacing.xxl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text(stringResource(Res.string.watchlist_note_title, row.symbol), Modifier.semantics { heading() }, style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary)
            StockTextField(text, { text = it }, label = stringResource(Res.string.watchlist_note_label), singleLine = false, minLines = 4,
                placeholder = stringResource(Res.string.watchlist_note_placeholder),
                supporting = "${text.length} / $NOTE_LIMIT · ${stringResource(Res.string.watchlist_note_private)}",
                error = if (tooLong) stringResource(Res.string.watchlist_note_too_long, NOTE_LIMIT) else null)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (row.note != null) StockButton(stringResource(Res.string.watchlist_note_delete), onClick = { onSave(null) }, variant = StockButtonVariant.TEXT)
                Spacer(Modifier.weight(1f))
                StockButton(stringResource(Res.string.action_save), onClick = { onSave(text.trim().ifEmpty { null }) }, enabled = !tooLong)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(open: Sheet.MoveTo, targets: List<Watchlist>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StockStepsTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().padding(bottom = StockStepsTheme.spacing.xxl)) {
            Text(stringResource(if (open.copy) Res.string.watchlist_copy_to else Res.string.watchlist_move_to, open.row.symbol),
                Modifier.padding(horizontal = StockStepsTheme.spacing.screen, vertical = StockStepsTheme.spacing.sm).semantics { heading() },
                style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary)
            targets.forEach { list -> SheetAction("${list.name} (${list.entries.size})") { onPick(list.id) } }
        }
    }
}

@Composable
private fun NameDialog(list: Watchlist?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(list?.name.orEmpty()) }
    val clean = name.trim()
    val error = when {
        clean.length > NAME_LIMIT -> stringResource(Res.string.watchlist_name_too_long, NAME_LIMIT)
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StockStepsTheme.colors.surface,
        title = { Text(stringResource(if (list == null) Res.string.watchlist_new else Res.string.watchlist_rename), style = StockStepsTheme.typography.sectionTitle, color = StockStepsTheme.colors.textPrimary) },
        text = { StockTextField(name, { name = it }, label = stringResource(Res.string.watchlist_name_label), placeholder = stringResource(Res.string.watchlist_name_placeholder), error = error) },
        dismissButton = { StockButton(stringResource(Res.string.action_cancel), onClick = onDismiss, variant = StockButtonVariant.TEXT) },
        confirmButton = { StockButton(stringResource(Res.string.action_save), onClick = { onSave(clean) }, enabled = clean.isNotEmpty() && error == null, variant = StockButtonVariant.SECONDARY) }
    )
}

private const val NOTE_LIMIT = 1_000
private const val NAME_LIMIT = 40

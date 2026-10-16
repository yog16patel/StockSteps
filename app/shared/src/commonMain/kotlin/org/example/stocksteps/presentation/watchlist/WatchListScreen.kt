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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import org.example.stocksteps.watchlist.WatchlistPresenter
import org.example.stocksteps.watchlist.WatchlistRowModel
import org.jetbrains.compose.resources.stringResource

internal sealed interface WatchListAction {
    data object Refresh : WatchListAction
    data object SignIn : WatchListAction
    data object Search : WatchListAction
    /** 5D.5.6B landing bar. */
    data object Notifications : WatchListAction
    data object Settings : WatchListAction
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
    data class AddPortfolio(val instrument: InstrumentRef) : WatchListAction
    data class AddAlert(val instrument: InstrumentRef, val price: String?) : WatchListAction
    data object DismissMessage : WatchListAction
    /** Earnings Calendar filtered to the user's watchlists. */
    data object Earnings : WatchListAction
    /** Earnings Reminders settings (incl. automatic reminders for watchlist companies). */
    data object EarningsReminders : WatchListAction
    /** Company Comparison with the first three companies of the shown list (Phase 1 entry point). */
    data class Compare(val companies: List<org.example.stocksteps.screener.SelectedCompany>) : WatchListAction
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
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            org.example.stocksteps.presentation.ScrollToTopOnReselect(org.example.stocksteps.navigation.AppTab.WATCHLIST, listState) // D12
            PullToRefreshBox(isRefreshing = state.lists.loading && state.lists.value != null, onRefresh = { onAction(WatchListAction.Refresh) }, modifier = region) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Phase 5A order: header → watchlist selector → stocks → alerts and related actions → education.
                    item(key = "header") { Header(state, model, content.padding(top = spacing.lg), onAction, onManage = { sheet = it }) }
                    if (state.signedIn && model == null) {
                        item(key = "status") {
                            if (state.lists.loading || state.session.initializing) StockLoadingState(content.padding(top = spacing.lg)) { repeat(4) { StockRowSkeleton() } }
                            else StockCard(content.padding(top = spacing.lg), bordered = false) {
                                StockErrorState(state.lists.error ?: stringResource(Res.string.watchlist_load_failed), { onAction(WatchListAction.Refresh) },
                                    title = "Couldn't load your watchlist")
                            }
                        }
                        return@LazyColumn
                    }
                    if (model == null) return@LazyColumn
                    if (state.signedIn && model.tabs.isNotEmpty()) {
                        item(key = "tabs") { Tabs(model, content.padding(top = spacing.md), onAction, onNew = { sheet = Sheet.Name(null) }) }
                    }
                    if (state.lists.offline && state.lists.error != null) {
                        item(key = "offline") { Text(state.lists.error!!, content.padding(top = spacing.sm), style = StockStepsTheme.typography.caption, color = colors.cautionText) }
                    }
                    model.emptyMessage?.let {
                        item(key = "empty") {
                            StockCard(content.padding(top = spacing.md), bordered = false) {
                                StockEmptyState(
                                    message = if (state.signedIn) WatchlistPresenter.EMPTY_MESSAGE else "${WatchlistPresenter.EMPTY_MESSAGE} They're saved on this device.",
                                    title = model.emptyTitle ?: "Your watchlist is empty",
                                    titleDescription = model.emptySpokenTitle,
                                    icon = StockIcons.StarOutline,
                                    actionText = "Add a company",
                                    onAction = { onAction(WatchListAction.Search) }
                                )
                            }
                        }
                    }
                    if (model.rows.isNotEmpty()) {
                        // At most 100 rows per list: one grouped card of compact rows (no card per stock).
                        item(key = "rows") {
                            StockCard(content.padding(top = spacing.md), bordered = false, contentPadding = PaddingValues(vertical = spacing.xxs)) {
                                model.rows.forEachIndexed { index, row ->
                                    if (index > 0) StockDivider(startIndent = spacing.cardPadding)
                                    WatchlistQuoteRow(row, loading = state.dataLoading, signedIn = state.signedIn, onAction = onAction, onMore = { sheet = Sheet.RowActions(row, index) })
                                }
                            }
                        }
                        item(key = "context") {
                            val unavailable = WatchlistPresenter.pricesUnavailableMessage(hasRows = true, hasData = state.data != null, loading = state.dataLoading)
                            Column(content.padding(top = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                if (unavailable != null) StockErrorState(unavailable, { onAction(WatchListAction.Refresh) })
                                else Text(WatchlistPresenter.CHANGE_CONTEXT, style = StockStepsTheme.typography.caption, color = colors.textMeta)
                                StockButton("Add a company", onClick = { onAction(WatchListAction.Search) }, modifier = Modifier.fillMaxWidth(),
                                    variant = StockButtonVariant.SECONDARY, icon = StockIcons.Search)
                            }
                        }
                    }
                    item(key = "related") { RelatedActions(state, content.padding(top = spacing.sectionGap), onAction) }
                    if (model.insights.isNotEmpty()) {
                        item(key = "insights") { InsightsCard(model.insights, content.padding(top = spacing.md)) }
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

/**
 * "Watchlist", the company count and update time, then search and (signed in) the watchlist menu — new, rename, delete. Alerts, earnings and
 * comparison moved to the related-actions group under the stocks (Phase 5A), so the header stays compact.
 */
@Composable
private fun Header(state: WatchListState, model: WatchlistModel?, modifier: Modifier, onAction: (WatchListAction) -> Unit, onManage: (Sheet) -> Unit) {
    val colors = StockStepsTheme.colors
    val selected = state.selected
    val manage = state.signedIn && model != null && selected != null
    // 5D.5.6B: the shared landing bar ("Watchlist", Search, the list menu when signed in, then Notifications and Settings); the count and
    // freshness line stays under it.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.xxs)) {
        org.example.stocksteps.designsystem.components.StockLandingTopBar(org.example.stocksteps.navigation.AppTab.WATCHLIST,
            onNotifications = { onAction(WatchListAction.Notifications) }, onSettings = { onAction(WatchListAction.Settings) },
            actions = listOfNotNull<@Composable () -> Unit>(
                { IconButton(onClick = { onAction(WatchListAction.Search) }) { Icon(StockIcons.Search, "Add a company", tint = colors.textPrimary) } },
                if (manage) ({ ManageMenu(state, selected!!, onAction, onManage) }) else null))
        // Count and freshness under the title; wraps only between parts, never inside "5:16 PM ET" (Phase 5A.1).
        model?.headerLine?.let { line ->
            Text(line, style = StockStepsTheme.typography.small, color = if (model.headerCaution) colors.cautionText else colors.textSecondary)
        }
    }
}

@Composable
private fun ManageMenu(state: WatchListState, selected: org.example.stocksteps.model.Watchlist, onAction: (WatchListAction) -> Unit, onManage: (Sheet) -> Unit) {
    val colors = StockStepsTheme.colors
    var menu by remember { mutableStateOf(false) }
    Box {
                IconButton(onClick = { menu = true }) { Icon(StockIcons.More, stringResource(Res.string.watchlist_manage), tint = colors.textPrimary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.surface) {
                    DropdownMenuItem(text = { Text(stringResource(Res.string.watchlist_new)) }, onClick = { menu = false; onManage(Sheet.Name(null)) })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.watchlist_rename)) }, onClick = { menu = false; onManage(Sheet.Name(selected)) })
                    if (state.watchlists.size > 1) DropdownMenuItem(text = { Text(stringResource(Res.string.watchlist_delete), color = colors.negativeText) }, onClick = {
                        menu = false
                        if (selected.entries.isEmpty()) onAction(WatchListAction.DeleteList(selected.id)) else onManage(Sheet.ConfirmDelete(selected))
                    })
                }
    }
}

/** One chip per watchlist (full names; the row scrolls instead of truncating) and a "+" chip to create one. */
@Composable
private fun Tabs(model: WatchlistModel, modifier: Modifier, onAction: (WatchListAction) -> Unit, onNew: () -> Unit) {
    // Phase 5A.1: the shared fading scroll row shows that more lists (and "+") lie beyond a long selected name, and keeps the selected list in view.
    Box(modifier) {
        StockFadingScrollRow(model.tabs.map { it.id } + NEW_LIST, model.selectedId, Modifier.selectableGroup(), StockStepsTheme.spacing.xs) { id, itemModifier ->
            val tab = model.tabs.firstOrNull { it.id == id }
            if (tab == null) StockChip("+", false, onClick = onNew, modifier = itemModifier.semantics { contentDescription = "New watchlist" })
            else StockChip(tab.name, tab.selected, onClick = { onAction(WatchListAction.Select(tab.id)) },
                modifier = itemModifier.semantics { contentDescription = "${tab.name}, ${tab.count} ${if (tab.count == 1) "company" else "companies"}" })
        }
    }
}

private const val NEW_LIST = "__new_list__"

/**
 * Compact quote row (Phase 5A): logo, symbol and name on the left; price and "+$3.04 (+0.91%)" on the right — or on their own line under the
 * company when they would crowd the name (shared `StockQuoteRowLayout`). Missing quotes say "No data available" (never $0.00); while prices
 * load, a placeholder is shown instead. The whole row opens Company Details; the bell (active alerts) and ⋮ (actions) are separate 48dp targets.
 */
@Composable
private fun WatchlistQuoteRow(row: WatchlistRowModel, loading: Boolean, signedIn: Boolean, onAction: (WatchListAction) -> Unit, onMore: () -> Unit) {
    val spacing = StockStepsTheme.spacing
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val values: @Composable () -> Unit = {
        when {
            row.quoteAvailable -> {
                Text(row.price.orEmpty(), style = typography.numberLabelStrong, color = colors.textValue, maxLines = 1)
                row.changeLine?.let { StockPriceChange(it, row.direction, style = typography.numberLabel) }
            }
            loading -> StockLoadingState { StockSkeleton(Modifier.width(StockStepsTheme.dimensions.touchTarget * 1.5f)) }
            else -> Text(WatchlistPresenter.UNAVAILABLE, style = typography.caption, color = colors.textTertiary, maxLines = 1)
        }
        row.staleLabel?.let { Text(it, style = typography.tiny, color = colors.cautionText, maxLines = 1) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val valuesWidth = with(density) {
                listOfNotNull(row.price?.let { measurer.measure(it, typography.numberLabelStrong).size.width },
                    row.changeLine?.let { measurer.measure(it, typography.numberLabel).size.width + StockStepsTheme.dimensions.iconSmall.roundToPx() })
                    .maxOrNull()?.toDp()?.value ?: 0f
            }
            val beside = maxWidth.value - StockStepsTheme.dimensions.logo.value - spacing.cardPadding.value - spacing.sm.value
            val stacked = org.example.stocksteps.theme.StockQuoteRowLayout.stacksValues(valuesWidth, beside,
                accessibilityText = density.fontScale >= StockStepsTheme.dimensions.largeFontScale * 1.15f)
            // One element for the whole row (identity, values, note): one tap target and one spoken label, even when values are stacked.
            val open = { onAction(WatchListAction.OpenStock(row.symbol)) }
            Column(Modifier.clickable(role = Role.Button, onClick = open).clearAndSetSemantics {
                contentDescription = row.accessibilityLabel
                role = Role.Button
                onClick(label = "Open details") { open(); true }
            }) {
                StockRow(
                    symbol = row.symbol,
                    name = row.name,
                    logoUrl = row.logoUrl,
                    nameMaxLines = 2,
                    contentPadding = PaddingValues(start = spacing.cardPadding, end = spacing.xs, top = spacing.sm, bottom = if (stacked) spacing.xxs else spacing.sm),
                    trailing = if (stacked) ({}) else ({ values() })
                )
                if (stacked) {
                    // Stacked: price and change share one line (wrapping only if even that doesn't fit), so rows stay compact at large text.
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(Modifier.padding(start = spacing.cardPadding + StockStepsTheme.dimensions.logo + spacing.sm, end = spacing.sm, bottom = spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm), itemVerticalAlignment = Alignment.CenterVertically) { values() }
                }
                row.notePreview?.let {
                    Text("“$it”", Modifier.padding(start = spacing.cardPadding + StockStepsTheme.dimensions.logo + spacing.sm, end = spacing.sm, bottom = spacing.sm),
                        style = typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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

/** Alerts and related actions under the stocks: what used to be header links, as one grouped list of navigation rows. */
@Composable
private fun RelatedActions(state: WatchListState, modifier: Modifier, onAction: (WatchListAction) -> Unit) {
    val shown = (state.selected?.entries?.sortedBy { it.order }?.map { it.instrument } ?: if (!state.signedIn) state.guestItems.map { InstrumentRef(it.symbol, it.name) } else emptyList())
        .map { org.example.stocksteps.screener.SelectedCompany(it.symbol, it.name ?: it.symbol) }
    val rows = buildList<Pair<String, @Composable () -> Unit>> {
        if (state.signedIn) {
            add("alerts" to { StockNavigationRow("My alerts", { onAction(WatchListAction.OpenAlerts(null)) }, icon = StockIcons.Bell, detail = "Price, earnings and news alerts") })
            add("earnings" to { StockNavigationRow("Earnings dates", { onAction(WatchListAction.Earnings) }, icon = StockIcons.Document, detail = "When your companies report next") })
            add("reminders" to { StockNavigationRow("Earnings reminders", { onAction(WatchListAction.EarningsReminders) }, icon = StockIcons.Bell, detail = "Get a reminder before results") })
        }
        if (shown.size >= 2) add("compare" to { StockNavigationRow("Compare these companies", { onAction(WatchListAction.Compare(shown.take(3))) }, icon = StockIcons.PieChart, detail = "See up to three side by side") })
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StockStepsTheme.spacing.sm)) {
        if (!state.signedIn) SignInCard(Modifier) { onAction(WatchListAction.SignIn) }
        if (rows.isNotEmpty()) {
            StockSectionHeader(if (state.signedIn) "Alerts and more" else "More")
            StockCard(bordered = false, contentPadding = PaddingValues(horizontal = StockStepsTheme.spacing.md, vertical = StockStepsTheme.spacing.xxs)) {
                rows.forEachIndexed { index, (_, content) -> if (index > 0) StockDivider(); content() }
            }
        }
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
            SheetAction("View details") { onAction(WatchListAction.OpenStock(row.symbol)); onDismiss() }
            if (!state.signedIn) {
                SheetAction(stringResource(Res.string.watchlist_remove), destructive = true) { onAction(WatchListAction.RemoveGuest(row.symbol)); onDismiss() }
                return@Column
            }
            SheetAction(stringResource(Res.string.alert_add)) {
                onAction(WatchListAction.AddAlert(InstrumentRef(row.symbol, row.name, currency = row.currency), row.price)); onDismiss()
            }
            SheetAction("Add to portfolio") {
                val instrument = state.watchlists.flatMap { it.entries }.firstOrNull { it.instrument.symbol == row.symbol }?.instrument
                    ?: InstrumentRef(row.symbol, row.name, currency = row.currency)
                onAction(WatchListAction.AddPortfolio(instrument)); onDismiss()
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

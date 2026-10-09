package org.example.stocksteps.presentation.brief

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.brief.DailyBriefPresenter
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.presentation.AdaptiveSinglePane
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal fun briefNow(): Long = Clock.System.now().toEpochMilliseconds()

/** Wires the shared brief presenter (account graph) to the reader; the route only names the brief. */
@Composable
internal fun DailyBriefScene(
    route: DailyBriefRoute,
    presenter: DailyBriefPresenter,
    hinge: WindowHinge?,
    onOpenStock: (String) -> Unit,
    onHistory: () -> Unit,
    onOpenBrief: (String) -> Unit,
    onWatchlist: () -> Unit,
    onLearn: () -> Unit,
    onSignIn: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenEarnings: (String) -> Unit = {},
    onOpenResults: (String) -> Unit = {},
    onOpenDigest: () -> Unit = {}
) {
    val state by presenter.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(route.briefId) { presenter.start(); presenter.open(route.briefId) }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            DailyBriefScreen(state, briefNow(), Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize()) { action ->
                handle(action, presenter, { runCatching { uriHandler.openUri(it) } }, onOpenStock, onHistory, onOpenBrief, onWatchlist, onLearn, onSignIn, onUpgrade, onOpenEarnings, onOpenResults, onOpenDigest)
            }
        }
    }
}

@Composable
internal fun DailyBriefHistoryScene(presenter: DailyBriefPresenter, hinge: WindowHinge?, onOpenBrief: (String) -> Unit, onUpgrade: () -> Unit) {
    val state by presenter.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { presenter.start(); presenter.loadHistory() }
    AdaptiveSinglePane(hinge) { region ->
        Box(region.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            DailyBriefHistoryScreen(state, Modifier.widthIn(max = StockStepsTheme.dimensions.contentMaxWidth).fillMaxSize()) { action ->
                when (action) {
                    is BriefAction.OpenBrief -> onOpenBrief(action.id)
                    BriefAction.Upgrade -> { presenter.dismissUpgrade(); onUpgrade() }
                    BriefAction.DismissUpgrade -> presenter.dismissUpgrade()
                    else -> Unit
                }
            }
        }
    }
}

private fun handle(
    action: BriefAction, presenter: DailyBriefPresenter, openUrl: (String) -> Unit, onOpenStock: (String) -> Unit, onHistory: () -> Unit,
    onOpenBrief: (String) -> Unit, onWatchlist: () -> Unit, onLearn: () -> Unit, onSignIn: () -> Unit, onUpgrade: () -> Unit, onOpenEarnings: (String) -> Unit,
    onOpenResults: (String) -> Unit, onOpenDigest: () -> Unit
) {
    when (action) {
        BriefAction.EarningsDigest -> onOpenDigest()
        BriefAction.Retry -> presenter.loadLatest()
        is BriefAction.OpenUrl -> openUrl(action.url)
        is BriefAction.OpenStock -> onOpenStock(action.symbol)
        is BriefAction.OpenEarnings -> onOpenEarnings(action.eventId)
        is BriefAction.OpenResults -> onOpenResults(action.reportId)
        is BriefAction.Explain -> presenter.explain(action.storyId)
        is BriefAction.Ask -> presenter.ask(action.question)
        BriefAction.History -> onHistory()
        is BriefAction.OpenBrief -> onOpenBrief(action.id)
        BriefAction.Watchlist -> onWatchlist()
        BriefAction.Learn -> onLearn()
        BriefAction.SignIn -> onSignIn()
        BriefAction.Upgrade -> { presenter.dismissUpgrade(); onUpgrade() }
        BriefAction.DismissUpgrade -> presenter.dismissUpgrade()
        BriefAction.DismissMessage -> presenter.dismissMessage()
        is BriefAction.SavePreferences -> presenter.savePreferences(action.value)
        BriefAction.LoadPreferences -> presenter.loadPreferences()
        is BriefAction.Scenario -> presenter.scenario(action.name)
    }
}

package org.example.stocksteps.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.brief.BriefFormat
import org.example.stocksteps.brief.DailyBriefUiState
import org.example.stocksteps.designsystem.components.*
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.theme.StockBadgeKind
import org.example.stocksteps.portfolio.PortfolioFormat
import org.example.stocksteps.portfolio.PortfolioUiState
import org.example.stocksteps.presentation.portfolio.PortfolioPresentation
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Inviting learning banner: soft gradient, decorative illustration, indigo title and a round
 * arrow cue. The whole banner is one button; Learn owns the content.
 */
@Composable
internal fun HomeLearnCard(
    onLearn: () -> Unit,
    modifier: Modifier = Modifier,
    /** Overrides for "Continue learning" (real saved progress only). */
    title: String? = null,
    body: String? = null,
    actionLabel: String? = null
) {
    val colors = StockStepsTheme.colors
    val spacing = StockStepsTheme.spacing
    val dimensions = StockStepsTheme.dimensions
    val shape = StockStepsTheme.shapes.cardLarge
    val action = actionLabel ?: stringResource(Res.string.home_learn_action)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(colors.learnContainerStart, colors.learnContainerEnd)))
            .clickable(onClickLabel = action, role = Role.Button, onClick = onLearn)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Image(
            painter = painterResource(Res.drawable.ill_learn_basics),
            contentDescription = null,
            modifier = Modifier.size(dimensions.learnIllustration)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                text = title ?: stringResource(Res.string.home_learn_title),
                style = StockStepsTheme.typography.cardTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.learnAccent
            )
            Text(
                text = body ?: stringResource(Res.string.home_learn_body),
                style = StockStepsTheme.typography.caption,
                color = colors.textBody
            )
        }
        Box(
            modifier = Modifier
                .size(dimensions.learnAction)
                .clip(CircleShape)
                .background(colors.learnAccent)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Icon(StockIcons.ArrowForward, contentDescription = null, tint = colors.onLearnAccent, modifier = Modifier.size(dimensions.iconSmall))
        }
    }
}

/**
 * "Markets today" (Phase 4A): market sessions, the brief's first indices as compact rows (value, signed change with arrow and words,
 * quote state) and the Daily Market Brief entry. Uses the brief already loaded on Home — no extra requests, never placeholder values.
 */
@Composable
internal fun HomeMarketOverview(state: DailyBriefUiState?, nowMillis: Long, onOpenBrief: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val brief = state?.latest
    StockCard(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        StockSectionHeader("Markets today", trailing = if (brief?.sampleData == true) {
            { StockStatusBadge("Sample", StockBadgeKind.SAMPLE, size = StockBadgeSize.COMPACT) }
        } else null)
        when {
            brief != null -> {
                HomeDashboardPresentation.sessionLines(brief).forEach { Text(it, style = typography.caption, color = colors.textSupporting) }
                val indices = HomeDashboardPresentation.indices(brief)
                if (indices.isEmpty()) {
                    Text(HomeDashboardPresentation.INDICES_UNAVAILABLE, style = typography.small, color = colors.textSupporting)
                } else Column {
                    indices.forEachIndexed { n, index ->
                        if (n > 0) StockDivider()
                        org.example.stocksteps.presentation.brief.BriefIndexRow(index)
                    }
                }
                StockDivider()
                val caution = BriefFormat.isStale(brief, nowMillis) || state.offline
                HomeLinkRow(
                    title = "Your Daily Market Brief",
                    summary = HomeDashboardPresentation.briefSummary(brief),
                    detail = HomeDashboardPresentation.briefMeta(brief, nowMillis, state.offline),
                    detailColor = if (caution) colors.cautionText else colors.textMeta,
                    actionLabel = HomeDashboardPresentation.briefAction(brief, nowMillis),
                    onClick = onOpenBrief
                )
            }
            state?.loading != false -> LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading market overview" })
            else -> Text(state.error ?: "Market overview isn't available right now.", style = typography.small, color = colors.textSupporting)
        }
    }
}

/** A full-width tappable row inside a Home card (Upcoming & alerts, brief entry); the shared [StockNavigationRow] without an icon. */
@Composable
internal fun HomeLinkRow(title: String, detail: String?, onClick: () -> Unit, modifier: Modifier = Modifier,
                         detailColor: Color = StockStepsTheme.colors.textSupporting, actionLabel: String? = null, summary: String? = null) =
    StockNavigationRow(title, onClick, modifier, detail = detail, detailColor = detailColor, actionLabel = actionLabel, summary = summary)

/**
 * Compact portfolio summary in the Portfolio (Phase 3) language: total value dominant, today's change (or that it is unavailable), one
 * metric row and one freshness line. The whole card opens Portfolio; without an account it explains and offers to create one.
 */
@Composable
internal fun HomePortfolioSummary(state: PortfolioUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StockStepsTheme.colors
    val typography = StockStepsTheme.typography
    val spacing = StockStepsTheme.spacing
    val present = HomeDashboardPresentation
    val owned = present.hasPortfolio(state)
    StockCard(modifier, onClick = if (owned) onOpen else null, onClickLabel = present.portfolioAction(state),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        StockSectionHeader("Your portfolio", trailing = if (owned) {
            { Icon(StockIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(StockStepsTheme.dimensions.icon)) }
        } else null)
        if (owned) {
            Text(present.portfolioLabel(state), style = typography.label, color = colors.textSupporting)
            Text("${state.currency} ${PortfolioFormat.amount(state.total)}", style = typography.largeNumber, color = colors.textValue)
            val today = PortfolioPresentation.todayChange(state)
            if (today != null) StockPriceChange("$today today", PortfolioPresentation.direction(state.dailyGain), style = typography.numberMedium, maxLines = 2)
            else Text(PortfolioPresentation.TODAY_UNAVAILABLE, style = typography.small, color = colors.textSupporting)
            StockDivider(Modifier.padding(vertical = spacing.xs))
            StockMetricGrid(listOf(
                StockMetricItem("Invested cost", PortfolioFormat.amount(state.basis)),
                StockMetricItem("Unrealized P/L", PortfolioPresentation.signedAmount(state.unrealized) ?: "—", direction = PortfolioPresentation.direction(state.unrealized)),
                StockMetricItem("Realized P/L", PortfolioPresentation.signedAmount(state.realized) ?: "—", direction = PortfolioPresentation.direction(state.realized))
            ), rowsWhenNarrow = true)
            state.notice?.let { Text(it, style = typography.caption, color = colors.cautionText) }
            present.portfolioFreshness(state)?.let { Text(it, style = typography.caption, color = colors.textMeta) }
        } else if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading your portfolio" })
        } else {
            Text(present.PORTFOLIO_EMPTY, style = typography.small, color = colors.textBody)
            StockButton(present.portfolioAction(state), onOpen, variant = StockButtonVariant.SECONDARY)
        }
        if (state.mockScenario != null) StockStatusBadge("Sample · ${state.mockScenario}", StockBadgeKind.SAMPLE, size = StockBadgeSize.COMPACT)
    }
}

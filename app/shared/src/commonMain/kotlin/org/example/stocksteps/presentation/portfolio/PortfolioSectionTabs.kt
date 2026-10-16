package org.example.stocksteps.presentation.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.example.stocksteps.designsystem.components.StockLandingTopBar
import org.example.stocksteps.designsystem.components.StockSegment
import org.example.stocksteps.designsystem.components.StockSegmentedControl
import org.example.stocksteps.designsystem.icons.StockIcons
import org.example.stocksteps.designsystem.theme.StockStepsTheme
import org.example.stocksteps.navigation.AppTab
import org.example.stocksteps.navigation.PortfolioSection
import org.example.stocksteps.navigation.PortfolioSections

/**
 * Phase 5D.5.6C4: the Portfolio landing header shared by both sections, as list content: the landing bar ("Portfolio", the section's own
 * [actions], Notifications, Settings) scrolls away, and the My Portfolio | Practice nested tabs stay pinned at the top (a sticky header), so the
 * user can switch sections at any scroll position and each section keeps its own scroll position.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun androidx.compose.foundation.lazy.LazyListScope.portfolioLandingHeader(
    section: PortfolioSection,
    onSection: (PortfolioSection) -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
    actions: List<@Composable () -> Unit> = emptyList()
) {
    item("landing-bar") { StockLandingTopBar(AppTab.PORTFOLIO, onNotifications, onSettings, actions = actions) }
    stickyHeader("section-tabs") {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().background(StockStepsTheme.colors.appBackground)
            .padding(vertical = StockStepsTheme.spacing.xs)) { PortfolioSectionTabs(section, onSection) }
    }
}

/** The My Portfolio | Practice nested tabs: the existing segmented style, announced as tabs with their selected state. */
@Composable
internal fun PortfolioSectionTabs(selected: PortfolioSection, onSelect: (PortfolioSection) -> Unit, modifier: Modifier = Modifier) {
    StockSegmentedControl(
        options = listOf(StockSegment(PortfolioSection.MY_PORTFOLIO, PortfolioSection.MY_PORTFOLIO.label, StockIcons.Briefcase),
            StockSegment(PortfolioSection.PRACTICE, PortfolioSection.PRACTICE.label, StockIcons.School)),
        selected = selected,
        onSelect = onSelect,
        modifier = modifier.semantics { contentDescription = PortfolioSections.SELECTOR_SPOKEN },
        compactIcons = true,
        asTabs = true
    )
}

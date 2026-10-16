package org.example.stocksteps.navigation

/**
 * The two nested tabs of the Portfolio landing page (Phase 5D.5.6C4, plan §13): the real-investment portfolio and the separate, simulated
 * Practice portfolio. They are sections of the Portfolio primary tab — never primary tabs and never pushed screens — so switching between them
 * changes the landing page's content in place and keeps the Portfolio tab selected.
 */
enum class PortfolioSection(val label: String) { MY_PORTFOLIO("My Portfolio"), PRACTICE("Practice") }

/** Shared rules for the Portfolio sections, used by Android and iOS. */
object PortfolioSections {
    /** A fresh session (and every sign-out) starts on the real portfolio. */
    val DEFAULT = PortfolioSection.MY_PORTFOLIO

    /** Spoken label of the selector (each option is announced as a tab with its selected state). */
    const val SELECTOR_SPOKEN = "Portfolio sections"

    /**
     * The section a Portfolio-owned screen belongs to: opening it selects that section, so Back from it shows the matching section (Holding
     * details → My Portfolio, a practice order → Practice). Null for screens outside the Portfolio tab.
     */
    fun sectionFor(screen: ScreenId): PortfolioSection? = when (screen) {
        ScreenId.PRACTICE, ScreenId.PRACTICE_ORDER -> PortfolioSection.PRACTICE
        ScreenId.PORTFOLIO, ScreenId.HOLDING_DETAILS, ScreenId.PORTFOLIO_TRANSACTIONS, ScreenId.PORTFOLIO_TRANSACTION,
        ScreenId.PORTFOLIO_INSIGHTS -> PortfolioSection.MY_PORTFOLIO
        else -> null
    }

    /**
     * Practice is a section of the Portfolio landing page, not a pushed screen: "open Practice" from anywhere shows Portfolio's root with the
     * Practice section selected (a tab switch, no duplicate Practice page).
     */
    fun isLandingSection(screen: ScreenId): Boolean = screen == ScreenId.PRACTICE
}

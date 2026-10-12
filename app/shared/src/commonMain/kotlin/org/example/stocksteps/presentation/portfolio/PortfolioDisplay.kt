package org.example.stocksteps.presentation.portfolio

/**
 * Portfolio screen copy shared by Compose and SwiftUI (Portfolio UI Refinement Phase A, finding A-F18): one wording per meaning on both
 * platforms. Sample (MOCK) copy always says it is a read-only sample and that saved data is untouched; nothing here shortens a disclosure.
 */
object PortfolioDisplay {
    // Screen and destination names (A-F12: one name for the insights destination).
    const val TITLE = "Portfolio"
    const val HOLDING_DETAILS_TITLE = "Holding details"
    const val INSIGHTS_TITLE = "Portfolio insights"
    const val INSIGHTS_SUBTITLE = "Performance, allocation and concentration"
    const val INSIGHTS_OPEN = "Open portfolio insights"
    /** The engine's observation list inside Portfolio insights (was a second "Insights" heading). */
    const val INSIGHTS_LIST_TITLE = "Key observations"

    /** "As of Oct 8, 2026, 9:15 PM UTC" for an analytics `asOf` instant (finding A-F14); UTC stays UTC, an unknown format is shown as given. */
    fun asOf(instant: String): String = "As of ${PortfolioDates.dateTime(instant)}"

    // Sample data (MOCK builds only).
    const val SAMPLE_SCENARIO = "Sample scenario"
    const val SAVED_ACCOUNTS = "My saved accounts"
    /** Menu text for a scenario id ("mixed-currencies" → "Mixed currencies"); the saved-accounts option stays as written. */
    fun scenarioLabel(id: String): String = if (id == SAVED_ACCOUNTS) id else org.example.stocksteps.designsystem.StockLabels.humanize(id)
    const val SAMPLE_BANNER = "Read-only sample. Your saved accounts and watchlists stay unchanged; switch to My saved accounts to make changes."

    // Status.
    const val LOADING = "Loading portfolio"
    const val RESTORING = "Restoring portfolio"
    const val OFFLINE_TITLE = "Offline · last saved portfolio"
    const val REFRESH_FAILED_TITLE = "Couldn't refresh your portfolio"
    const val OUT_OF_DATE = "Values may be out of date."
    const val SIGN_IN_PROMPT = "Sign in to save your portfolio securely."

    // Empty states.
    const val EMPTY_TITLE = "Start tracking what you own"
    const val EMPTY_MESSAGE = "Create an account, then add an existing investment or record a trade. Saving a stock to a watchlist does not buy it."
    const val EMPTY_ACTION = "Create portfolio"
    const val HOLDINGS_EMPTY = "No holdings yet. Add an existing investment with its effective date, or record a buy."
    const val TRANSACTIONS_EMPTY = "No transactions yet"

    /**
     * The dated history chart plots account value (cash, deposits and withdrawals included), not a return (finding A-F6) — the title says
     * what is drawn and the note says where deposit-adjusted returns are.
     */
    const val CHART_TITLE = "Account value"
    const val CHART_NOTE = "Includes cash, deposits and withdrawals, so a rise isn't always a gain. Returns that leave out deposits are in Portfolio insights (StockSteps+)."
    const val CHART_EMPTY_TITLE = "No account value history shown"
    const val CHART_EMPTY_MESSAGE = "Choose a period to load dated values."

    /** Spoken chart summary: "Account value in CAD, including cash. Sep 1, 2026 · CAD 1,850.00. Oct 8, 2026 · CAD 1,850.00. Missing observations are gaps." */
    fun chartDescription(currency: String, first: String, last: String): String =
        "Account value in $currency, including cash. $first. $last. Missing observations are gaps."

    // About these numbers (ⓘ).
    const val ABOUT_TITLE = "About these numbers"
    const val ABOUT_CASH = "Cash is included in account value. A negative cash balance represents unfunded or borrowed cash; record deposits separately from existing investments."
    const val ABOUT_ALLOCATION = "Borrowed cash can make a holding exceed 100%. Sector and ETF look-through allocations are not estimated."

    // Add / edit transaction.
    const val FORM_ADD_TITLE = "Add transaction"
    const val FORM_EDIT_TITLE = "Edit transaction"
    const val FORM_INTRO = "Existing investments start on the effective date you enter. We never invent earlier returns."
    const val FORM_SAVE = "Save transaction"
    const val FORM_SAVING = "Saving…"
    const val FORM_CANCEL = "Cancel"
    const val CREATE_ACCOUNT_FIRST = "Create an account first"
    const val SEARCH_SECURITY = "Search and select a security"
    const val CHOOSE_ACCOUNT = "Choose an account"

    // Account form.
    const val ACCOUNT_CREATE_TITLE = "Create investment account"
    const val ACCOUNT_SETTINGS_TITLE = "Account settings"
    const val ACCOUNT_NAME = "Account name"
    const val ACCOUNT_CATEGORY = "Account category"
    const val ACCOUNT_CURRENCY = "Reporting currency"
    const val ACCOUNT_ARCHIVE = "Archive account"
    const val ACCOUNT_SAVE = "Save account"
    const val ACCOUNT_NOTE = "Account categories organize investments. Moving-average cost is not a legal tax-basis calculation, and nothing here is tax advice."

    // Delete confirmations.
    fun deleteAccountTitle(name: String): String = "Delete $name?"
    const val DELETE_ACCOUNT_MESSAGE = "This deletes this account and its transactions. Watchlists and alerts stay saved."
    const val DELETE_TRANSACTION_TITLE = "Delete transaction?"
    const val DELETE_TRANSACTION_MESSAGE = "Balances will be recalculated. Deletion is rejected if a later sale would exceed your shares."
    const val DELETE = "Delete"
}

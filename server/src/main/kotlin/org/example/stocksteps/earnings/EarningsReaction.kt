package org.example.stocksteps.earnings

import java.util.Locale

/**
 * Wording for the Earnings Details reaction line. The reaction itself is calculated by
 * [PriceReactionEngine] (one policy for every screen). Describes the change, never a cause.
 */
object EarningsReactionCalculator {
    fun sentence(reaction: PriceReaction): String? {
        val change = reaction.changePercent ?: return null
        val direction = if (change >= 0) "rose" else "fell"
        return "The stock $direction ${"%.1f".format(Locale.US, kotlin.math.abs(change))}% over the measured earnings window" +
            (if (reaction.approximate) " (approximate)." else ".") +
            " Other news and overall market moves can also affect the price."
    }
}

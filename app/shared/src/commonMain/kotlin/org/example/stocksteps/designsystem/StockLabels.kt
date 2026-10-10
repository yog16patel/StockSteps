package org.example.stocksteps.designsystem

/**
 * User-facing labels for code identifiers, shared by Compose and SwiftUI (Phase 5C: Portfolio forms showed "PERSONAL",
 * "OPENING POSITION"). Use a hand-written label when one exists; this is the readable fallback, never raw enum names.
 */
object StockLabels {
    /** "OPENING_POSITION" / "openingPosition" / "opening-position" → "Opening position"; known acronyms stay upper case. */
    fun humanize(identifier: String): String {
        val words = identifier.trim()
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .split('_', '-', ' ')
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
        if (words.isEmpty()) return ""
        return words.mapIndexed { i, w ->
            when {
                w.uppercase() in acronyms -> w.uppercase()
                i == 0 -> w.replaceFirstChar { it.uppercase() }
                else -> w
            }
        }.joinToString(" ")
    }

    private val acronyms = setOf("TFSA", "RRSP", "RESP", "FHSA", "RRIF", "LIRA", "ETF", "IPO", "EPS", "USD", "CAD", "AI", "US")
}

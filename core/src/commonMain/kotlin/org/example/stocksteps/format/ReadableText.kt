package org.example.stocksteps.format

/** Splitting server text for progressive disclosure (Earnings 5D.4, Daily Brief 5D.5.1). The text itself is never reworded. */
object ReadableText {
    /**
     * The first sentence and the rest. A sentence ends at ". ", "? " or "! " followed by a capital letter, so amounts ("C$1.94", "3.0%") and
     * abbreviations never split. Returns (first, rest or null).
     */
    fun firstSentence(text: String): Pair<String, String?> {
        val match = Regex("""[.?!]\s+(?=[A-Z])""").find(text) ?: return text to null
        val end = match.range.first + 1
        return text.substring(0, end) to text.substring(match.range.last + 1).trim().ifEmpty { null }
    }
}

package org.example.stocksteps.search

import org.example.stocksteps.model.StockSearchResult

/**
 * Company search relevance (Phase 5D.5.5B, plan `docs/COMPANY_SEARCH_REFINEMENT_PHASE5D_5_5_PLAN.md` §7). Pure and deterministic: the same
 * query and provider list always give the same order. Applied on the server to everything the provider returned (both FMP endpoints, after
 * the scope filter), so every client and the search cache share it.
 *
 * Matching only normalises for comparison; displayed symbols and names are never changed. Nothing is dropped — a weak match only ranks lower.
 * No popularity, market cap, AI or paid placement: within a tier, a major-exchange listing comes before an OTC one (owner decision D2), then
 * the provider's own order, then the symbol.
 */
object SearchRanking {

    /** Match tiers, best first. */
    enum class Tier { EXACT_TICKER, EXACT_NAME, TICKER_PREFIX, NAME_WORD_START, NAME_PREFIX, LATER_WORD, LATER_WORD_PREFIX, SUBSTRING, PROVIDER }

    /** Over-the-counter markets rank after major exchanges within the same tier (D2). An unknown exchange is not penalised. */
    val OTC_EXCHANGES = setOf("OTC", "OTCQX", "OTCQB", "OTCMKTS", "PNK")

    private val LEGAL_SUFFIXES = setOf("inc", "incorporated", "corp", "corporation", "co", "company", "ltd", "limited", "plc", "llc", "lp")

    fun rank(query: String, results: List<StockSearchResult>): List<StockSearchResult> =
        results.withIndex()
            .sortedWith(compareBy<IndexedValue<StockSearchResult>>({ tier(query, it.value).ordinal }, { if (isOtc(it.value)) 1 else 0 }, { it.index },
                { it.value.symbol }))
            .map { it.value }

    fun tier(query: String, result: StockSearchResult): Tier {
        val q = text(query)
        if (q.isEmpty()) return Tier.PROVIDER
        val ticker = ticker(result.symbol)
        val qTicker = ticker(query)
        val name = text(result.name)
        val words = name.split(' ').filter { it.isNotEmpty() }
        val qWords = q.split(' ').filter { it.isNotEmpty() }
        return when {
            qTicker.isNotEmpty() && ticker == qTicker -> Tier.EXACT_TICKER
            withoutSuffix(words) == qWords || words == qWords -> Tier.EXACT_NAME
            qTicker.isNotEmpty() && ticker.startsWith(qTicker) -> Tier.TICKER_PREFIX
            startsWithWords(words, qWords, wholeLast = true) -> Tier.NAME_WORD_START
            startsWithWords(words, qWords, wholeLast = false) -> Tier.NAME_PREFIX
            words.indices.drop(1).any { startsWithWords(words.drop(it), qWords, wholeLast = true) } -> Tier.LATER_WORD
            words.indices.drop(1).any { startsWithWords(words.drop(it), qWords, wholeLast = false) } -> Tier.LATER_WORD_PREFIX
            name.contains(q) || ticker.contains(qTicker.ifEmpty { q }) -> Tier.SUBSTRING
            else -> Tier.PROVIDER
        }
    }

    fun isOtc(result: StockSearchResult): Boolean = result.exchange?.uppercase()?.trim() in OTC_EXCHANGES

    /** Case-folded, punctuation as spaces, single spaces (names and free text). */
    fun text(value: String): String =
        value.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").split(' ').filter { it.isNotEmpty() }.joinToString(" ")

    /** Tickers compare case-insensitively with "." and "-" treated alike (BRK.B = BRK-B); every other character still counts (GOOG ≠ GOOGL). */
    fun ticker(value: String): String = value.trim().uppercase().replace('-', '.').filterNot { it.isWhitespace() }

    /** The name's words without one trailing legal suffix ("Apple Inc." → apple). */
    fun withoutSuffix(words: List<String>): List<String> = if (words.size > 1 && words.last() in LEGAL_SUFFIXES) words.dropLast(1) else words

    /** The name's normalised words without the legal suffix, for comparing companies (share classes share it: GOOG / GOOGL "Alphabet"). */
    fun companyKey(name: String): String = withoutSuffix(text(name).split(' ').filter { it.isNotEmpty() }).joinToString(" ")

    private fun startsWithWords(words: List<String>, query: List<String>, wholeLast: Boolean): Boolean {
        if (query.isEmpty() || words.size < query.size) return false
        for (i in 0 until query.size - 1) if (words[i] != query[i]) return false
        val last = words[query.size - 1]
        return if (wholeLast) last == query.last() else last.startsWith(query.last()) && last != query.last()
    }
}

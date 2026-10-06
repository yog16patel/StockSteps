package org.example.stocksteps.repositoryImpl

import org.example.stocksteps.repository.models.FinnhubNewsArticle

/** Conservative evidence filter, not an AI claim about an article's primary subject. */
internal object CompanyNewsRelevance {
    fun matches(article: FinnhubNewsArticle, symbol: String, companyName: String?): Boolean {
        val related = article.related?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotBlank() }.orEmpty()
        if (related.isNotEmpty() && symbol.uppercase() !in related) return false
        if (tickerMention(article.headline, symbol) || tickerMention(article.summary.orEmpty(), symbol)) return true
        val name = companyName?.trim()
            ?.replace(Regex("(?i)^the\\s+"), "")
            ?.replace(Regex("(?i)(?:[,. ]+(?:incorporated|inc|corporation|corp|limited|ltd|plc|llc|ag|sa)\\.?)+$"), "")
            ?.trim()
            ?.takeIf { it.length >= 4 || it.any(Char::isDigit) }
            ?: return false
        // A company name in only the summary can be an incidental competitor reference.
        return bounded(name).containsMatchIn(article.headline)
    }

    private fun tickerMention(text: String, symbol: String): Boolean {
        if (symbol.length >= 4 || symbol.contains('.')) return bounded(symbol).containsMatchIn(text)
        // Avoid interpreting words such as A, ON, IT or ALL as bare tickers.
        val escaped = Regex.escape(symbol)
        return Regex("(?i)(?:\\$$escaped(?![\\p{L}\\p{N}])|\\($escaped\\)|(?:NASDAQ|NYSE|AMEX)\\s*:\\s*$escaped(?![\\p{L}\\p{N}])|(?<![\\p{L}\\p{N}])$escaped\\s*:\\s*(?:NASDAQ|NYSE|AMEX))").containsMatchIn(text)
    }
    private fun bounded(value: String) = Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(value)}(?![\\p{L}\\p{N}])")
}

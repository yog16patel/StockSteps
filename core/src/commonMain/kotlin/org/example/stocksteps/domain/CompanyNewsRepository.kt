package org.example.stocksteps.domain

import org.example.stocksteps.model.ArticleInsight
import org.example.stocksteps.model.MovementExplanation
import org.example.stocksteps.model.MovementPeriod
import org.example.stocksteps.model.NewsArticle

/** Company News, article explanations and "Why did it move?" from the StockSteps backend. */
interface CompanyNewsRepository {
    /** The company's whole recent feed, newest first; the apps filter categories locally. */
    suspend fun getCompanyNewsFeed(symbol: String): List<NewsArticle>
    /** Null when the article is no longer in the company's recent feed. */
    suspend fun getArticleInsight(symbol: String, articleId: String): ArticleInsight?
    /** Null when there isn't enough price data for the period (a normal state, not a failure). */
    suspend fun getMovement(symbol: String, period: MovementPeriod): MovementExplanation?
}

class GetCompanyNewsFeed(private val repository: CompanyNewsRepository) {
    suspend operator fun invoke(symbol: String) = repository.getCompanyNewsFeed(symbol.trim().uppercase())
}

class GetArticleInsight(private val repository: CompanyNewsRepository) {
    suspend operator fun invoke(symbol: String, articleId: String) = repository.getArticleInsight(symbol.trim().uppercase(), articleId)
}

class GetMovementExplanation(private val repository: CompanyNewsRepository) {
    suspend operator fun invoke(symbol: String, period: MovementPeriod) = repository.getMovement(symbol.trim().uppercase(), period)
}

package org.example.stocksteps.data

import org.example.stocksteps.domain.CompanyNewsRepository
import org.example.stocksteps.model.ArticleInsight
import org.example.stocksteps.model.MovementExplanation
import org.example.stocksteps.model.MovementPeriod
import org.example.stocksteps.network.StockStepsApi
import org.example.stocksteps.network.StockStepsApiException

class RemoteCompanyNewsRepository(private val api: StockStepsApi) : CompanyNewsRepository {
    override suspend fun getCompanyNewsFeed(symbol: String) = stockDataRequest { api.getCompanyNews(symbol, category = null, page = 0, limit = FEED_LIMIT) }

    override suspend fun getArticleInsight(symbol: String, articleId: String): ArticleInsight? = stockDataRequest {
        notFoundAsNull(ARTICLE_NOT_FOUND) { api.getArticleInsight(symbol, articleId) }
    }

    override suspend fun getMovement(symbol: String, period: MovementPeriod): MovementExplanation? = stockDataRequest {
        notFoundAsNull(MOVEMENT_UNAVAILABLE) { api.getMovement(symbol, period) }
    }

    private inline fun <T> notFoundAsNull(code: String, block: () -> T): T? = try { block() } catch (cause: StockStepsApiException) {
        if (cause.error.code == code) null else throw cause
    }

    private companion object {
        const val FEED_LIMIT = 50
        const val ARTICLE_NOT_FOUND = "ARTICLE_NOT_FOUND"
        const val MOVEMENT_UNAVAILABLE = "MOVEMENT_UNAVAILABLE"
    }
}

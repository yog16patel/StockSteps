package org.example.stocksteps.presentation.home

import org.example.stocksteps.home.MoverCategory

/** User intents from the stateless Home screen; HomeScene routes them to the model or navigation. */
internal sealed interface HomeAction {
    data object RetryMarket : HomeAction
    data object RetryNews : HomeAction
    data object Learn : HomeAction
    data object ViewAllMovers : HomeAction
    data object ViewAllNews : HomeAction
    data class SelectMovers(val category: MoverCategory) : HomeAction
    data class OpenStock(val symbol: String) : HomeAction
    data class OpenArticle(val url: String) : HomeAction
}

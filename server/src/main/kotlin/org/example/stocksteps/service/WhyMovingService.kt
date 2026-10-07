package org.example.stocksteps.service

import org.example.stocksteps.model.WhyMoving

/**
 * Source of "Why did it move?" explanations. The REAL pipeline (relevant news → AI simplification
 * → cached, source-backed text) is not built yet, so REAL mode has no source and the endpoint
 * reports the explanation as unavailable. MOCK mode serves fixtures.
 */
fun interface WhyMovingSource {
    suspend fun getWhyMoving(symbol: String): WhyMoving?
}

class WhyMovingService(private val source: WhyMovingSource?) {
    suspend fun getWhyMoving(symbol: String): WhyMoving? = source?.getWhyMoving(symbol)
}

package org.example.stocksteps.news

import org.example.stocksteps.model.SimplifiedNews

/** Reuses earlier local summaries during migration instead of paying to generate them again. */
class MigratingNewsSimplificationStore(
    private val cloud: NewsSimplificationStore,
    private val local: NewsSimplificationStore,
    private val now: () -> Long = System::currentTimeMillis
) : NewsSimplificationStore by cloud {
    override suspend fun read(key: String): SimplifiedNews? {
        cloud.read(key)?.let { return it }
        val existing = local.read(key)?.validated() ?: return null
        if (cloud.claim(key, now())) cloud.save(key, existing)
        return existing
    }
}

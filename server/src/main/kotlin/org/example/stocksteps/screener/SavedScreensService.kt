package org.example.stocksteps.screener

import org.example.stocksteps.userdata.EntitlementService
import org.example.stocksteps.userdata.UserDataException
import org.example.stocksteps.userdata.UserDataStore

/**
 * Per-user saved screens: filter definitions only, so reapplying always gives current matches.
 * The free tier keeps [FREE_LIMIT]; StockSteps+ keeps [PLUS_LIMIT]. When a plan lapses, existing
 * screens stay readable, usable, renamable and deletable; only creating beyond the free limit stops.
 */
class SavedScreensService(private val store: UserDataStore, private val entitlements: EntitlementService, private val now: () -> Long) {
    companion object {
        const val FREE_LIMIT = 3
        const val PLUS_LIMIT = 25
    }

    private suspend fun limit(uid: String): Pair<Int, Boolean> = entitlements.get(uid).plus.let { plus -> (if (plus) PLUS_LIMIT else FREE_LIMIT) to plus }

    suspend fun list(uid: String): SavedScreensResponse {
        val (limit, plus) = limit(uid)
        return SavedScreensResponse(store.updateSavedScreens(uid) { it to it }, limit, plus)
    }

    private fun validName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.length !in 1..40) throw UserDataException(400, "INVALID_SCREEN", "Use a name of 1–40 characters.")
        return trimmed
    }

    private fun validQuery(query: ScreenerQuery): ScreenerQuery {
        try { ScreenerEngine.validate(query) } catch (cause: ScreenerValidationException) {
            throw UserDataException(400, "INVALID_SCREEN", cause.message ?: "Invalid filters.")
        }
        return query.definition()
    }

    suspend fun create(uid: String, request: SaveScreenRequest): SavedScreensResponse {
        val (limit, plus) = limit(uid)
        val name = validName(request.name)
        val query = validQuery(request.query)
        store.updateSavedScreens(uid) { screens ->
            if (screens.size >= limit) throw UserDataException(403, "SAVED_SCREEN_LIMIT",
                if (plus) "You can keep up to $limit saved screens." else "The free plan keeps up to $limit saved screens. StockSteps+ keeps up to $PLUS_LIMIT.")
            if (screens.any { it.name.equals(name, ignoreCase = true) }) throw UserDataException(409, "DUPLICATE_NAME", "You already have a screen with this name.")
            val id = "scr-" + now().toString(36) + "-" + (screens.size + 1)
            (screens + SavedScreen(id, name, query, now(), now())) to Unit
        }
        return list(uid)
    }

    suspend fun update(uid: String, id: String, name: String?, query: ScreenerQuery?): SavedScreensResponse {
        val newName = name?.let(::validName)
        val newQuery = query?.let(::validQuery)
        store.updateSavedScreens(uid) { screens ->
            val existing = screens.find { it.id == id } ?: throw UserDataException(404, "SCREEN_NOT_FOUND", "Saved screen not found.")
            if (newName != null && screens.any { it.id != id && it.name.equals(newName, ignoreCase = true) }) throw UserDataException(409, "DUPLICATE_NAME", "You already have a screen with this name.")
            screens.map { if (it.id == existing.id) it.copy(name = newName ?: it.name, query = newQuery ?: it.query, updatedAt = now()) else it } to Unit
        }
        return list(uid)
    }

    suspend fun delete(uid: String, id: String): SavedScreensResponse {
        store.updateSavedScreens(uid) { screens ->
            if (screens.none { it.id == id }) throw UserDataException(404, "SCREEN_NOT_FOUND", "Saved screen not found.")
            screens.filterNot { it.id == id } to Unit
        }
        return list(uid)
    }
}

package org.example.stocksteps.screener

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.service.ProviderFeature
import org.example.stocksteps.service.ProviderUsageMeter
import org.example.stocksteps.userdata.*
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * Company Comparison Phase 4: Guided Research Checklist sessions, owned by the verified user.
 *
 * - Basic CRUD (create, read, notes, statuses, delete) touches only the user-data store: **no provider calls**.
 * - Free accounts keep [FREE_LIMIT] sessions; StockSteps+ keeps [plusLimit] (fair use). The limit is checked
 *   inside the same atomic store update that adds the session, so parallel creates can't exceed it.
 * - StockSteps+ is read from the stored entitlement on every request (never from the client). Advanced
 *   questions, snapshots, the detailed summary and PDF export are verified before any costly work.
 * - After a plan lapses nothing is deleted: sessions stay readable, editable (basic questions) and deletable;
 *   advanced answers stay readable; snapshots stay readable and deletable; new sessions only when under the
 *   free limit; snapshots, the detailed summary and export need StockSteps+ again.
 * - Summaries and exports reuse the comparison and history services (and their caches) — one data layer.
 */
class ComparisonResearchService(
    private val store: UserDataStore,
    private val entitlements: EntitlementService,
    private val comparison: suspend (List<String>) -> ComparisonResponse,
    private val history: ComparisonHistoryService,
    private val clock: Clock,
    private val plusLimit: Int = 100,
    private val meter: ProviderUsageMeter = ProviderUsageMeter.shared,
    private val renderPdf: (ResearchSummary) -> ByteArray = { ResearchPdf.render(it) }
) {
    companion object {
        const val FREE_LIMIT = 3
        const val MAX_SNAPSHOTS = 10
        const val MAX_UPDATES = 50
        const val MAX_SESSIONS_LISTED = 200
    }
    private val log = LoggerFactory.getLogger("StockSteps.Research")
    private fun now() = clock.millis()
    private fun fail(status: Int, code: String, message: String): Nothing = throw UserDataException(status, code, message)

    /** null when the plan record can't be read (callers decide whether that's fatal). */
    private suspend fun plusOrNull(uid: String): Boolean? = try { entitlements.get(uid).plus } catch (cause: UserDataException) { if (cause.code == "ENTITLEMENT_UNAVAILABLE") null else throw cause }
    private suspend fun requirePlus(uid: String, feature: String) {
        meter.event("research.premium.requested")
        if (!entitlements.get(uid).plus) fail(403, "PLUS_REQUIRED", "$feature ${if (feature.endsWith("s")) "are" else "is"} part of StockSteps+. The basic checklist, notes and summary stay free.")
    }
    private fun limit(plus: Boolean) = if (plus) plusLimit else FREE_LIMIT

    private fun info(s: ResearchSession) = ResearchSessionInfo(s.id, s.title, s.symbols, s.createdAt, s.updatedAt,
        s.responses.values.count { it.status == ResearchStatus.REVIEWED }, s.snapshots.size)

    private fun response(session: ResearchSession, plus: Boolean) = ResearchSessionResponse(session, plus, ResearchProgress.of(session.responses, plus), !plus, if (plus) MAX_SNAPSHOTS else 0)

    suspend fun list(uid: String): ResearchSessionsResponse {
        val plus = plusOrNull(uid)
        val index = store.updateComparisonResearch(uid, null) { index, _ -> ResearchWrite(index, result = index) }
        val limit = limit(plus == true)
        return ResearchSessionsResponse(index.sessions.sortedByDescending { it.updatedAt }.take(MAX_SESSIONS_LISTED), limit, plus == true, plus != null && index.sessions.size < limit,
            when {
                plus == null -> "Your StockSteps+ status can't be checked right now. Your research is safe; new sessions can be created once it's available."
                index.sessions.size >= limit && plus -> "You've reached the fair-use limit of $limit saved research sessions. Delete one to start another."
                index.sessions.size >= limit -> "The free plan keeps $FREE_LIMIT saved research sessions. Delete one, or use StockSteps+ for more."
                else -> null
            })
    }

    private fun title(raw: String?, symbols: List<String>): String {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: symbols.joinToString(" vs ")
        if (trimmed.length > ResearchChecklist.MAX_TITLE) fail(400, "INVALID_TITLE", "Use a title of up to ${ResearchChecklist.MAX_TITLE} characters.")
        return trimmed
    }

    suspend fun create(uid: String, request: CreateResearchRequest): ResearchSessionResponse {
        val symbols = try { parseComparisonSymbols(request.symbols.joinToString(",")) } catch (cause: ScreenerRequestException) { fail(cause.status, cause.code, cause.message ?: "Invalid companies.") }
        val title = title(request.title, symbols)
        val plus = entitlements.get(uid).plus                          // 503 when unreadable: never create on an unknown plan
        val limit = limit(plus)
        val time = now()
        val session = store.updateComparisonResearch(uid, null) { index, _ ->
            if (index.sessions.size >= limit) fail(403, "RESEARCH_SESSION_LIMIT",
                if (plus) "You've reached the fair-use limit of $limit saved research sessions. Delete one to start another."
                else "The free plan keeps $FREE_LIMIT saved research sessions. Delete one, or use StockSteps+ for more.")
            val id = "rs-" + time.toString(36) + "-" + (index.sessions.size + 1) + (1000..9999).random().toString(36)
            val created = ResearchSession(id, title, symbols, ResearchChecklist.VERSION, emptyMap(), time, time, 1)
            ResearchWrite(index.copy(sessions = index.sessions + info(created)), created, result = created)
        }
        meter.event("research.session.created")
        return response(session, plus)
    }

    private suspend fun load(uid: String, id: String): ResearchSession =
        store.updateComparisonResearch(uid, id) { index, session -> ResearchWrite(index, result = session) } ?: fail(404, "RESEARCH_NOT_FOUND", "Research session not found.")

    suspend fun get(uid: String, id: String): ResearchSessionResponse {
        val session = load(uid, id)
        meter.event("research.checklist.opened")
        return response(session, plusOrNull(uid) == true)
    }

    /** Notes, statuses and title only — never a market-data request. */
    suspend fun update(uid: String, id: String, request: UpdateResearchRequest): ResearchSessionResponse {
        if (request.responses.size > MAX_UPDATES) fail(400, "TOO_MANY_UPDATES", "Send at most $MAX_UPDATES changes at once.")
        val questions = request.responses.map { u -> u to (ResearchChecklist.question(u.questionId) ?: fail(400, "UNKNOWN_QUESTION", "Unknown checklist question.")) }
        request.responses.forEach { u -> if ((u.note?.length ?: 0) > ResearchChecklist.MAX_NOTE) fail(400, "NOTE_TOO_LONG", "Notes can be up to ${ResearchChecklist.MAX_NOTE} characters.") }
        val needsPlus = questions.any { it.second.tier == ResearchTier.PLUS }
        val plus = if (needsPlus) entitlements.get(uid).plus else plusOrNull(uid) == true
        if (needsPlus && !plus) fail(403, "PLUS_REQUIRED", "Advanced research questions are part of StockSteps+. Your earlier answers are kept and stay readable.")
        val time = now()
        val updated = store.updateComparisonResearch(uid, id) { index, session ->
            session ?: fail(404, "RESEARCH_NOT_FOUND", "Research session not found.")
            if (request.expectedRevision != null && request.expectedRevision != session.revision)
                fail(409, "RESEARCH_CONFLICT", "This research changed on another device. The latest version has been loaded; your unsaved text is kept.")
            val responses = session.responses.toMutableMap()
            for ((u, _) in questions) {
                val current = responses[u.questionId] ?: ResearchResponse(u.questionId)
                responses[u.questionId] = current.copy(status = u.status ?: current.status, note = u.note?.trim() ?: current.note, updatedAt = time)
            }
            val next = session.copy(title = request.title?.let { title(it, session.symbols) } ?: session.title, responses = responses, updatedAt = time, revision = session.revision + 1)
            ResearchWrite(index.copy(sessions = index.sessions.map { if (it.id == id) info(next) else it }), next, result = next)
        }
        return response(updated, plus)
    }

    suspend fun delete(uid: String, id: String): ResearchSessionsResponse {
        store.updateComparisonResearch(uid, id) { index, session ->
            session ?: fail(404, "RESEARCH_NOT_FOUND", "Research session not found.")
            ResearchWrite(index.copy(sessions = index.sessions.filterNot { it.id == id }), deleteSession = true, result = Unit)
        }
        return list(uid)
    }

    // ---------- StockSteps+: snapshots, detailed summary, export ----------

    suspend fun createSnapshot(uid: String, id: String, request: CreateSnapshotRequest): ResearchSessionResponse {
        requirePlus(uid, "Research snapshots")
        val label = request.label?.trim()?.take(ResearchChecklist.MAX_TITLE)?.ifEmpty { null }
        val existing = load(uid, id)
        if (existing.snapshots.size >= MAX_SNAPSHOTS) fail(403, "SNAPSHOT_LIMIT", "A session keeps up to $MAX_SNAPSHOTS snapshots. Delete an older one first.")
        // The metric references come from the shared comparison data (cached); a failure still saves the user's research.
        val data = withContext(ProviderFeature("comparison-research")) { runCatching { comparison(existing.symbols) }.getOrNull() }
        val time = now()
        val saved = store.updateComparisonResearch(uid, id) { index, session ->
            session ?: fail(404, "RESEARCH_NOT_FOUND", "Research session not found.")
            if (session.snapshots.size >= MAX_SNAPSHOTS) fail(403, "SNAPSHOT_LIMIT", "A session keeps up to $MAX_SNAPSHOTS snapshots. Delete an older one first.")
            val snapshot = ResearchSnapshot("snap-" + time.toString(36) + "-" + (session.snapshots.size + 1), label ?: "Snapshot ${session.snapshots.size + 1}", time,
                data?.companies?.mapNotNull { it.record?.fundamentalsAsOf }?.maxOrNull() ?: data?.asOf, session.symbols, session.responses,
                ResearchProgress.of(session.responses, true), ResearchSummaryEngine.snapshotMetrics(data))
            val next = session.copy(snapshots = session.snapshots + snapshot, updatedAt = time, revision = session.revision + 1)
            ResearchWrite(index.copy(sessions = index.sessions.map { if (it.id == id) info(next) else it }), next, result = next)
        }
        meter.event("research.snapshot.created")
        return response(saved, true)
    }

    /** Readable after a plan lapses (user-authored data is never withheld). */
    suspend fun snapshots(uid: String, id: String): List<ResearchSnapshot> = load(uid, id).snapshots.sortedByDescending { it.createdAt }

    suspend fun deleteSnapshot(uid: String, id: String, snapshotId: String): ResearchSessionResponse {
        val time = now()
        val next = store.updateComparisonResearch(uid, id) { index, session ->
            session ?: fail(404, "RESEARCH_NOT_FOUND", "Research session not found.")
            if (session.snapshots.none { it.id == snapshotId }) fail(404, "SNAPSHOT_NOT_FOUND", "Snapshot not found.")
            val next = session.copy(snapshots = session.snapshots.filterNot { it.id == snapshotId }, updatedAt = time, revision = session.revision + 1)
            ResearchWrite(index.copy(sessions = index.sessions.map { if (it.id == id) info(next) else it }), next, result = next)
        }
        return response(next, plusOrNull(uid) == true)
    }

    /** Basic summary for everyone; the detailed one for StockSteps+ (an unreadable plan gives the basic one). */
    suspend fun summary(uid: String, id: String, detailedRequested: Boolean = true): ResearchSummary {
        val session = load(uid, id)
        val detailed = detailedRequested && plusOrNull(uid) == true
        meter.event(if (detailed) "research.summary.detailed" else "research.summary.basic")
        return build(session, detailed)
    }

    private suspend fun build(session: ResearchSession, detailed: Boolean): ResearchSummary = withContext(ProviderFeature("comparison-research")) {
        val data = try { comparison(session.symbols) } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            null
        }
        val hist = try { history.data(session.symbols, if (detailed) HistoryRange.FIVE_YEARS else HistoryRange.ONE_YEAR, detailed) } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            null
        }
        // Annual statements come from the history service's cache (just loaded for 5Y): no extra requests.
        val statements = if (detailed) session.symbols.mapNotNull { s -> history.statements(s, "annual")?.let { s to it } }.toMap() else emptyMap()
        ResearchSummaryEngine.build(ResearchSummaryEngine.Inputs(session, data, hist, statements, detailed, clock.instant().toString()))
    }

    suspend fun export(uid: String, id: String): Pair<String, ByteArray> {
        requirePlus(uid, "PDF research reports")
        val session = load(uid, id)
        val summary = build(session, detailed = true)
        val bytes = try { renderPdf(summary) } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            log.warn("Research export failed: {}", cause.javaClass.simpleName)          // never logs notes
            fail(500, "EXPORT_FAILED", "The report couldn't be created. Try again.")
        }
        meter.event("research.export.generated")
        val name = "StockSteps-research-" + session.symbols.joinToString("-").replace(Regex("[^A-Za-z0-9.-]"), "") + ".pdf"
        return name to bytes
    }
}

/** `/api/v1/me/comparison-research…` — every route derives the owner from the verified token. */
fun Route.comparisonResearchRoutes(service: ComparisonResearchService, auth: UserAuthenticator, limiter: RequestRateLimiter) {
    suspend fun RoutingContext.guarded(uid: String, block: suspend () -> Any) {
        if (!limiter.allow("research:$uid")) { call.respond(HttpStatusCode.TooManyRequests, ApiError("RATE_LIMITED", "Too many requests. Wait a moment and try again.")); return }
        if ((call.request.headers["Content-Length"]?.toLongOrNull() ?: 0) > 128_000) { call.respond(HttpStatusCode.PayloadTooLarge, ApiError("TOO_LARGE", "Request too large.")); return }
        try { call.respond(block()) } catch (cause: UserDataException) {
            call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message))
        } catch (cause: io.ktor.server.plugins.BadRequestException) {
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_REQUEST", "The request couldn't be read."))
        }
    }
    fun RoutingContext.id() = call.parameters["id"].orEmpty().also { if (!Regex("[A-Za-z0-9_-]{1,64}").matches(it)) throw UserDataException(404, "RESEARCH_NOT_FOUND", "Research session not found.") }
    route("/api/v1/me/comparison-research") {
        get { user(auth) { uid -> guarded(uid) { service.list(uid) } } }
        post { user(auth) { uid -> guarded(uid) { service.create(uid, call.receive<CreateResearchRequest>()) } } }
        get("/{id}") { user(auth) { uid -> guarded(uid) { service.get(uid, id()) } } }
        patch("/{id}") { user(auth) { uid -> guarded(uid) { service.update(uid, id(), call.receive<UpdateResearchRequest>()) } } }
        delete("/{id}") { user(auth) { uid -> guarded(uid) { service.delete(uid, id()) } } }
        get("/{id}/summary") { user(auth) { uid -> guarded(uid) { service.summary(uid, id(), call.request.queryParameters["detail"] != "basic") } } }
        get("/{id}/snapshots") { user(auth) { uid -> guarded(uid) { service.snapshots(uid, id()) } } }
        post("/{id}/snapshots") { user(auth) { uid -> guarded(uid) { service.createSnapshot(uid, id(), call.receive<CreateSnapshotRequest>()) } } }
        delete("/{id}/snapshots/{snapshotId}") { user(auth) { uid -> guarded(uid) { service.deleteSnapshot(uid, id(), call.parameters["snapshotId"].orEmpty()) } } }
        post("/{id}/export") {
            user(auth) { uid ->
                if (!limiter.allow("research:$uid")) { call.respond(HttpStatusCode.TooManyRequests, ApiError("RATE_LIMITED", "Too many requests. Wait a moment and try again.")); return@user }
                try {
                    val (name, bytes) = service.export(uid, id())
                    // Streamed to this caller only: never stored, no public URL, not cacheable.
                    call.response.header(HttpHeaders.CacheControl, "no-store, private")
                    call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, name).toString())
                    call.respondBytes(bytes, ContentType.Application.Pdf)
                } catch (cause: UserDataException) {
                    call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message))
                }
            }
        }
    }
}

package org.example.stocksteps.earnings

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.portfolio.Decimal
import org.example.stocksteps.portfolio.analytics.Entitlements
import org.example.stocksteps.screener.RequestRateLimiter
import org.example.stocksteps.userdata.EntitlementService
import org.example.stocksteps.userdata.UserAuthenticator
import org.example.stocksteps.userdata.UserDataException
import org.example.stocksteps.userdata.user
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * StockSteps+ Premium Earnings Intelligence (Phase 5). Every request: verified identity → verified
 * StockSteps+ (fail closed) → request validation → quota → bounded, verified context → provider (with
 * timeout, one controlled retry, bounded concurrency) → validation → structured response → usage.
 *
 * Explanations contain only public report data, so one explanation per report + source version +
 * prompt version + model is shared by every StockSteps+ user (cost control); identical concurrent
 * requests share one provider call. Access to previously generated explanations follows the current
 * plan: when StockSteps+ ends, the server stops returning them (free results stay available).
 * Conversations are private to their user and scoped to one report and one source version.
 */
class EarningsPremiumService(
    private val earnings: EarningsService,
    private val entitlements: EntitlementService,
    private val provider: EarningsAiProvider?,
    val quota: EarningsAiQuotaLedger,
    private val clock: Clock,
    private val sampleData: Boolean,
    private val timeoutMillis: Long = 15_000,
    maxConcurrent: Int = 4,
    val metrics: EarningsAiMetrics = EarningsAiMetrics()
) {
    private val log = LoggerFactory.getLogger("StockSteps.EarningsAI")
    private val permits = Semaphore(maxConcurrent)
    /** Latest explanation per report (public data only; never per-user content). */
    private val explanations = ConcurrentHashMap<String, EarningsAiExplanation>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<EarningsAiExplanation>>()
    private val conversations = ConcurrentHashMap<String, Conversation>()

    private class Conversation(val id: String, val uid: String, val reportId: String, val version: String) {
        val exchanges = ArrayList<ConversationExchange>()
        @Volatile var updatedAt: Long = 0
    }

    companion object {
        const val DISCLAIMER = "Education, not investment advice. Built only from the verified figures shown on this screen; AI can still make mistakes, so check them."
        const val MAX_QUESTION = 300
        const val MAX_CONVERSATIONS = 5_000
        const val CONVERSATION_TTL = 24 * 3_600_000L
        const val MAX_EXCHANGES = 6
    }

    private fun scenarioOf(scenario: String?) = scenario.takeIf { sampleData }

    // ---------- Entitlement (fail closed) ----------

    suspend fun plan(uid: String, scenario: String? = null): Entitlements {
        if (scenarioOf(scenario) == "entitlement-unavailable") throw EarningsRequestException(503, "ENTITLEMENT_UNAVAILABLE", "Your StockSteps+ status can't be checked right now. Try again shortly. (Sample scenario)")
        return try { entitlements.get(uid) } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            log.warn("Entitlement check failed: {}", cause::class.simpleName)
            throw EarningsRequestException(503, "ENTITLEMENT_UNAVAILABLE", (cause as? UserDataException)?.message ?: "Your StockSteps+ status can't be checked right now. Try again shortly.")
        }
    }

    suspend fun requirePlus(uid: String, scenario: String?, feature: String): Entitlements =
        plan(uid, scenario).also { if (!it.plus) throw EarningsRequestException(403, "PLUS_REQUIRED", "$feature ${if (feature.endsWith("s")) "are" else "is"} part of StockSteps+. Basic earnings results stay free.") }

    // ---------- Context ----------

    private suspend fun reaction(reportId: String): EarningsPriceReactionResponse? = try { earnings.priceReaction(reportId, null) } catch (cause: Exception) {
        if (cause is CancellationException) throw cause
        null
    }

    /** The verified, bounded context for one report (404/400 exactly as Earnings Results). */
    suspend fun context(reportId: String, scenario: String?): EarningsGroundingContext {
        val results = earnings.resultsFor(reportId)
        val s = scenarioOf(scenario)
        val events = runCatching { earnings.reminderEvents(results.report.symbol) }.getOrNull()
        val history = events?.let { all -> HistoricalEarningsEngine.build(upTo(all, results.report), HistoricalEarningsEngine.MAX_QUARTERS, null, clock.instant().toString(), sampleData) }
        // MOCK only: a second source that disagrees on revenue. REAL has no secondary results provider yet.
        val alternative = if (s == "conflicting-sources") results.report.revenueActual?.let { listOf(AlternativeFigure("revenue", (Decimal.parse(it) + Decimal.parse("150000000")).toString(), "StockSteps sample secondary source")) }.orEmpty() else emptyList()
        return EarningsGrounding.build(results, reaction(reportId), history, alternative, if (s == "revised-source") "revised-sample" else null)
    }

    private fun upTo(events: List<EarningsEvent>, report: EarningsReport) =
        events.filter { HistoricalEarningsEngine.index(it.fiscalYear, it.fiscalQuarter) <= HistoricalEarningsEngine.index(report.fiscalYear, report.fiscalQuarter) }

    private fun preview(r: EarningsResultsResponse): String =
        "${r.report.companyName}, ${r.report.period}: ${EarningsDigestRules.headline(r.insights.eps.classification, r.insights.revenue.classification)}. " +
            "The explanation covers what was reported, how EPS and revenue compared with expectations, revenue growth, the stock's price reaction, what's still uncertain and key terms."

    private fun current(e: EarningsAiExplanation?, c: EarningsGroundingContext, p: EarningsAiProvider?) =
        e != null && p != null && e.sourceDataVersion == c.sourceDataVersion && e.promptVersion == p.promptVersion && e.model == p.model

    // ---------- Endpoints ----------

    suspend fun overview(uid: String, reportId: String, scenario: String?): PremiumEarningsOverview {
        val plan = plan(uid, scenario)
        val results = earnings.resultsFor(reportId)
        val rx = reaction(reportId)?.reaction?.takeIf { it.hasChange }
        val p = provider?.forScenario(scenarioOf(scenario))
        var availability = ExplanationAvailability.NONE
        var generatedAt: String? = null
        // Free users learn nothing about cached premium content.
        if (plan.plus) explanations[reportId]?.let { e ->
            availability = if (current(e, context(reportId, scenario), p)) ExplanationAvailability.CURRENT else ExplanationAvailability.STALE
            generatedAt = e.generatedAt
        }
        val events = runCatching { earnings.reminderEvents(results.report.symbol) }.getOrNull().orEmpty()
        val notes = buildList {
            if (!plan.plus) add("AI explanations, questions, expanded history and digests are part of StockSteps+. Basic earnings results stay free.")
            if (availability == ExplanationAvailability.STALE) add("An earlier explanation used figures that have since changed. A new explanation will use the current data.")
            if (provider == null) add("AI explanations aren't available right now. Basic results and history are unaffected.")
            if (sampleData) add("Sample AI for development: explanations and answers are deterministic templates, not an AI service.")
        }
        return PremiumEarningsOverview(reportId, plan.plus, plan.status, preview(results), availability, generatedAt,
            EarningsQuestionChips.forReport(results.insights, rx?.percentChange), quota.usage(uid, plan.plus, plan.status),
            HistoricalEarningsEngine.available(upTo(events, results.report)), provider != null, notes, sampleData)
    }

    suspend fun history(uid: String, reportId: String, limit: Int, cursor: String?, scenario: String?): HistoricalEarningsInsight {
        requirePlus(uid, scenario, "Expanded earnings history")
        if (limit !in 1..HistoricalEarningsEngine.MAX_QUARTERS) throw EarningsRequestException(400, "INVALID_LIMIT", "Choose 1–${HistoricalEarningsEngine.MAX_QUARTERS} quarters.")
        if (cursor != null && EarningsReportMapper.parse(cursor) == null) throw EarningsRequestException(400, "INVALID_CURSOR", "Invalid cursor.")
        val results = earnings.resultsFor(reportId)
        val events = earnings.reminderEvents(results.report.symbol)
        return try { HistoricalEarningsEngine.build(upTo(events, results.report), limit, cursor, clock.instant().toString(), sampleData) }
            catch (_: IllegalArgumentException) { throw EarningsRequestException(400, "INVALID_CURSOR", "That page isn't available. Reload the history.") }
    }

    suspend fun usage(uid: String): EarningsAiUsage {
        val plan = plan(uid)
        return quota.usage(uid, plan.plus, plan.status, if (provider == null) "AI features aren't available right now." else null)
    }

    /** One provider call with a timeout, bounded concurrency and one retry for transient failures only. */
    private suspend fun <T> call(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            attempt++
            metrics.providerCalls.incrementAndGet()
            try {
                return permits.withPermit { withTimeout(timeoutMillis) { block() } }
            } catch (cause: TimeoutCancellationException) {
                metrics.timeouts.incrementAndGet()
                throw EarningsRequestException(504, "AI_TIMEOUT", "The AI service took too long to respond. Basic results are still available; try again.")
            } catch (cause: EarningsAiProviderException) {
                when (cause.kind) {
                    EarningsAiProviderException.Kind.TIMEOUT -> { metrics.timeouts.incrementAndGet(); throw EarningsRequestException(504, "AI_TIMEOUT", "The AI service took too long to respond. Basic results are still available; try again.") }
                    EarningsAiProviderException.Kind.RATE_LIMITED -> throw EarningsRequestException(429, "AI_RATE_LIMITED", "The AI service is busy right now. Try again in a minute.")
                    EarningsAiProviderException.Kind.MALFORMED -> { metrics.rejected.incrementAndGet(); throw EarningsRequestException(502, "AI_INVALID_OUTPUT", "The AI service didn't produce a reliable answer, so nothing is shown. Try again later.") }
                    EarningsAiProviderException.Kind.UNAVAILABLE -> if (attempt >= 2) throw EarningsRequestException(503, "AI_UNAVAILABLE", "The AI service isn't available right now. Basic results are still available; try again later.")
                }
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                if (cause is EarningsRequestException) throw cause
                if (attempt >= 2) throw EarningsRequestException(503, "AI_UNAVAILABLE", "The AI service isn't available right now. Basic results are still available; try again later.")
            }
            delay(300L * attempt)
        }
    }

    private fun rejected(cause: EarningsAiValidator.Rejected, what: String): Nothing {
        metrics.rejected.incrementAndGet()
        log.warn("Earnings AI {} rejected: {}", what, cause.reason)
        throw EarningsRequestException(502, "AI_INVALID_OUTPUT", "The AI service didn't produce a reliable ${if (what == "answer") "answer" else "explanation"}, so nothing is shown. Try again later.")
    }

    suspend fun explain(uid: String, reportId: String, scenario: String?): EarningsAiExplanation {
        val plan = requirePlus(uid, scenario, "AI earnings explanations")
        val p = (provider ?: throw EarningsRequestException(503, "AI_UNAVAILABLE", "AI explanations aren't available right now. Basic results are still available.")).forScenario(scenarioOf(scenario))
        val ctx = context(reportId, scenario)
        fun usage() = quota.usage(uid, plan.plus, plan.status)
        // Identical data → identical explanation: a current cached copy is reused (no AI call, no quota).
        explanations[reportId]?.takeIf { current(it, ctx, p) }?.let { metrics.cacheHits.incrementAndGet(); return it.copy(cached = true, usage = usage()) }
        val key = "$reportId|${ctx.sourceDataVersion}|${p.promptVersion}|${p.model}"
        inFlight[key]?.let { metrics.coalesced.incrementAndGet(); return it.await().copy(cached = true, usage = usage()) }
        if (scenarioOf(scenario) == "ai-quota") quota.exhaust(uid, EarningsAiCategory.EXPLANATION)
        val reservation = quota.reserve(uid, EarningsAiCategory.EXPLANATION)
        val deferred = CompletableDeferred<EarningsAiExplanation>()
        inFlight.putIfAbsent(key, deferred)?.let { other -> quota.release(reservation); metrics.coalesced.incrementAndGet(); return other.await().copy(cached = true, usage = usage()) }
        try {
            val draft = call { p.explain(ctx) }
            val valid = try { EarningsAiValidator.explanation(draft, ctx) } catch (cause: EarningsAiValidator.Rejected) { rejected(cause, "explanation") }
            val claims = listOfNotNull(valid.summary, valid.eps, valid.revenue, valid.growth, valid.priceReaction)
            val used = claims.flatMap { it.sourceIds }.toSet()
            val e = EarningsAiExplanation(
                "exp-" + EarningsGrounding.version(listOf(key)), ctx.reportId, ctx.instrumentId, ctx.companyName, ctx.period, clock.instant().toString(), ctx.sourceDataVersion,
                p.promptVersion, p.model, ExplainedClaim(valid.summary.text.trim(), valid.summary.sourceIds.distinct()),
                valid.eps?.let { ExplainedClaim(it.text.trim(), it.sourceIds.distinct()) }, valid.revenue?.let { ExplainedClaim(it.text.trim(), it.sourceIds.distinct()) },
                valid.growth?.let { ExplainedClaim(it.text.trim(), it.sourceIds.distinct()) }, valid.priceReaction?.let { ExplainedClaim(it.text.trim(), it.sourceIds.distinct()) },
                valid.uncertainty, valid.keyTerms, valid.takeaways, ctx.citations.filter { it.sourceId in used }, DISCLAIMER, (ctx.conflicts + ctx.warnings).distinct(),
                sample = !p.usesAi)
            if (explanations.size > 2_000) explanations.keys.take(500).forEach(explanations::remove)
            explanations[reportId] = e
            metrics.generated.incrementAndGet()
            deferred.complete(e)
            return e.copy(usage = usage())
        } catch (cause: Throwable) {
            quota.release(reservation)
            if (cause !is CancellationException) metrics.failures.incrementAndGet()
            deferred.completeExceptionally(cause)
            throw cause
        } finally {
            inFlight.remove(key, deferred)
        }
    }

    private fun newId() = java.util.UUID.randomUUID().toString().replace("-", "").take(20)

    private fun prune(now: Long) {
        if (conversations.size < MAX_CONVERSATIONS) return
        conversations.values.filter { now - it.updatedAt > CONVERSATION_TTL }.forEach { conversations.remove(it.id) }
        if (conversations.size >= MAX_CONVERSATIONS) conversations.values.sortedBy { it.updatedAt }.take(MAX_CONVERSATIONS / 4).forEach { conversations.remove(it.id) }
    }

    suspend fun ask(uid: String, reportId: String, request: EarningsAiQuestion, scenario: String?): EarningsAiAnswer {
        val plan = requirePlus(uid, scenario, "Questions about earnings")
        val question = request.question.trim()
        if (question.length !in 3..MAX_QUESTION) throw EarningsRequestException(400, "INVALID_QUESTION", "Ask a question of 3–$MAX_QUESTION characters.")
        if ((request.conversationId?.length ?: 0) > 64 || (request.previousContextId?.length ?: 0) > 64) throw EarningsRequestException(400, "INVALID_REQUEST", "Invalid conversation.")
        val ctx = context(reportId, scenario)
        val now = clock.millis()
        // Another user's conversation id looks exactly like an unknown one: a fresh, private conversation.
        val prior = request.conversationId?.let { conversations[it] }?.takeIf { it.uid == uid && now - it.updatedAt < CONVERSATION_TTL }
        val sameReport = prior?.reportId == reportId
        val revised = (prior != null && sameReport && prior.version != ctx.sourceDataVersion) || (request.previousContextId != null && request.previousContextId != ctx.sourceDataVersion)
        val reset = request.conversationId != null && (prior == null || !sameReport || revised)
        val conversation = if (prior != null && !reset) prior else Conversation(newId(), uid, reportId, ctx.sourceDataVersion).also { prune(now); conversations[it.id] = it }
        conversation.updatedAt = now
        val note = when {
            reset && revised -> "These results were updated, so a new conversation started using the current figures."
            reset && prior != null -> "This conversation is about ${ctx.companyName}'s ${ctx.period} results."
            else -> null
        }
        fun usage() = quota.usage(uid, plan.plus, plan.status)
        fun answer(text: String, points: List<String>, sourceIds: List<String>, scope: AnswerScope, sample: Boolean) = EarningsAiAnswer(newId(), conversation.id, reportId, question, text.trim(),
            points, ctx.citations.filter { it.sourceId in sourceIds }, clock.instant().toString(), scope, ctx.sourceDataVersion, reset, note, usage(), sample)
        // Advice, predictions and prompt injection are declined before any provider call (not charged).
        EarningsQuestionScreen.decline(question)?.let { metrics.declined.incrementAndGet(); return answer(it, emptyList(), emptyList(), AnswerScope.UNSUPPORTED, false) }
        val p = (provider ?: throw EarningsRequestException(503, "AI_UNAVAILABLE", "AI answers aren't available right now.")).forScenario(scenarioOf(scenario))
        if (scenarioOf(scenario) == "ai-quota") quota.exhaust(uid, EarningsAiCategory.QUESTION)
        val reservation = quota.reserve(uid, EarningsAiCategory.QUESTION)
        try {
            val history = synchronized(conversation) { conversation.exchanges.takeLast(3) }
            val draft = call { p.answer(ctx, question, history) }
            val valid = try { EarningsAiValidator.answer(draft, ctx, question) } catch (cause: EarningsAiValidator.Rejected) { rejected(cause, "answer") }
            synchronized(conversation) {
                conversation.exchanges += ConversationExchange(question, valid.answer)
                while (conversation.exchanges.size > MAX_EXCHANGES) conversation.exchanges.removeAt(0)
            }
            metrics.generated.incrementAndGet()
            val scope = when (valid.scope) { "education" -> AnswerScope.EDUCATION; "insufficient" -> AnswerScope.INSUFFICIENT_DATA; else -> AnswerScope.REPORT }
            return answer(valid.answer, valid.points, valid.sourceIds, scope, !p.usesAi)
        } catch (cause: Throwable) {
            quota.release(reservation)
            if (cause !is CancellationException) metrics.failures.incrementAndGet()
            throw cause
        }
    }

    /** Validated AI summary of a deterministic digest (the digest service supplies the grounded lines). */
    suspend fun digestSummary(uid: String, plan: Entitlements, context: DigestAiContext, scenario: String?): DigestAiSummary {
        val p = (provider ?: throw EarningsRequestException(503, "AI_UNAVAILABLE", "AI summaries aren't available right now. Your digest is still available.")).forScenario(scenarioOf(scenario))
        if (scenarioOf(scenario) == "ai-quota") quota.exhaust(uid, EarningsAiCategory.DIGEST)
        val reservation = quota.reserve(uid, EarningsAiCategory.DIGEST)
        try {
            val draft = call { p.digest(context) }
            val valid = try { EarningsAiValidator.digest(draft, context) } catch (cause: EarningsAiValidator.Rejected) { rejected(cause, "digest") }
            metrics.generated.incrementAndGet()
            return DigestAiSummary(valid.summary.trim(), valid.points, valid.sourceIds.distinct(), clock.instant().toString(), !p.usesAi)
        } catch (cause: Throwable) {
            quota.release(reservation)
            throw cause
        }
    }

    /** Test/diagnostic: drops cached explanations (e.g. to simulate a fresh process). */
    internal fun clearCache() = explanations.clear()
}

/** Signed-in premium earnings routes. Identity comes only from the verified token; scenarios only in MOCK. */
fun Route.earningsPremiumRoutes(premium: EarningsPremiumService, digests: EarningsDigestService, auth: UserAuthenticator, limiter: RequestRateLimiter) {
    suspend fun RoutingContext.guarded(uid: String, block: suspend () -> Any) {
        if (!limiter.allow("premium:$uid")) { call.respond(HttpStatusCode.TooManyRequests, ApiError("RATE_LIMITED", "Too many requests. Wait a moment and try again.")); return }
        if ((call.request.headers["Content-Length"]?.toLongOrNull() ?: 0) > 4_096) { call.respond(HttpStatusCode.PayloadTooLarge, ApiError("TOO_LARGE", "Request too large.")); return }
        try { call.respond(block()) } catch (cause: EarningsRequestException) {
            call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message ?: "Request failed."))
        } catch (cause: UserDataException) {
            call.respond(HttpStatusCode.fromValue(cause.status), ApiError(cause.code, cause.message))
        } catch (cause: io.ktor.server.plugins.BadRequestException) {
            call.respond(HttpStatusCode.BadRequest, ApiError("INVALID_REQUEST", "The request couldn't be read."))
        }
    }
    fun RoutingContext.scenario() = call.request.queryParameters["scenario"]?.takeIf { Regex("[a-z0-9-]{1,40}").matches(it) }
    fun RoutingContext.reportId() = call.parameters["reportId"].orEmpty()
    route("/api/v1/me/earnings") {
        get("/reports/{reportId}/premium") { user(auth) { uid -> guarded(uid) { premium.overview(uid, reportId(), scenario()) } } }
        get("/reports/{reportId}/premium/history") { user(auth) { uid -> guarded(uid) {
            val limit = call.request.queryParameters["limit"]?.let { it.toIntOrNull() ?: throw EarningsRequestException(400, "INVALID_LIMIT", "Choose 1–8 quarters.") } ?: HistoricalEarningsEngine.MAX_QUARTERS
            premium.history(uid, reportId(), limit, call.request.queryParameters["cursor"], scenario())
        } } }
        post("/reports/{reportId}/ai/explain") { user(auth) { uid -> guarded(uid) { premium.explain(uid, reportId(), scenario()) } } }
        post("/reports/{reportId}/ai/ask") { user(auth) { uid -> guarded(uid) { premium.ask(uid, reportId(), call.receive<EarningsAiQuestion>(), scenario()) } } }
        get("/ai/usage") { user(auth) { uid -> guarded(uid) { premium.usage(uid) } } }
        get("/digest/latest") { user(auth) { uid -> guarded(uid) { digests.latest(uid, scenario()) } } }
        post("/digest/latest/ai") { user(auth) { uid -> guarded(uid) { digests.explain(uid, scenario()) } } }
        get("/digest/history") { user(auth) { uid -> guarded(uid) { digests.history(uid) } } }
        get("/digest/preferences") { user(auth) { uid -> guarded(uid) { digests.settings(uid) } } }
        put("/digest/preferences") { user(auth) { uid -> guarded(uid) { digests.setPreferences(uid, call.receive<EarningsDigestPreferences>()) } } }
    }
}

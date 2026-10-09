package org.example.stocksteps.learning

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.example.stocksteps.model.CompanyDetails
import org.example.stocksteps.userdata.EntitlementService
import org.example.stocksteps.userdata.UserAuthenticator
import org.example.stocksteps.userdata.UserDataException
import org.example.stocksteps.userdata.UserDataStore
import org.example.stocksteps.userdata.user
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/** Grounded answers to a StockSteps+ user's research question (only public company data is used). */
fun interface ResearchAiProvider {
    suspend fun answer(question: String, step: Int?, snapshot: ResearchSnapshot): ResearchAnswer
}

/**
 * MOCK: a deterministic answer assembled from the guide's own verified figures. Never calls an AI
 * service, and says plainly when the data can't answer the question.
 */
object TemplateResearchAi : ResearchAiProvider {
    override suspend fun answer(question: String, step: Int?, snapshot: ResearchSnapshot): ResearchAnswer {
        val view = step?.let(snapshot::step)
        val facts = (if (view != null) listOf(view) else snapshot.steps).mapNotNull { s ->
            (s.figures.joinToString("; ") { "${it.label}: ${it.value}" }.takeIf { it.isNotEmpty() }?.let { "$it." } ?: "") + (s.meaning?.let { " $it" } ?: "")
        }.map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        val why = Regex("(?i)\\b(why|will|should|buy|sell|predict)\\b").containsMatchIn(question)
        return ResearchAnswer(
            "Sample answer (no AI service in mock mode). From ${snapshot.name}'s reported figures: " + facts.joinToString(" ").ifBlank { "no reported figures are available." } +
                if (why) " These figures don't explain causes or predict what happens next, and StockSteps doesn't make buy or sell recommendations." else "",
            sources = listOf("StockSteps sample company fixtures")
        )
    }
}

/**
 * Learning progress per account (local-first on devices, merged here per company by most recent
 * visit) and the StockSteps+ research assistant. All education is free; only AI is gated.
 */
class LearningService(
    private val store: UserDataStore,
    private val entitlements: EntitlementService,
    private val details: suspend (String) -> CompanyDetails,
    private val research: ResearchAiProvider?,
    private val aiDailyLimit: Int,
    private val clock: Clock
) {
    private val aiUsage = ConcurrentHashMap<String, Int>()
    private val symbolPattern = Regex("[A-Z0-9][A-Z0-9.-]{0,19}")

    suspend fun get(uid: String): LearningProgressDocument = store.updateLearning(uid) { it to it }

    suspend fun save(uid: String, incoming: LearningProgressDocument): LearningProgressDocument {
        val clean = validate(incoming)
        return store.updateLearning(uid) { current -> current.merge(clean).let { it to it } }
    }

    private fun validate(document: LearningProgressDocument): LearningProgressDocument {
        if (document.journeys.size > LearningProgressDocument.MAX_JOURNEYS) invalid("Too many companies.")
        val limit = clock.millis() + 86_400_000
        return document.copy(journeys = document.journeys.map { j ->
            val symbol = j.symbol.uppercase()
            if (!symbolPattern.matches(symbol)) invalid("Invalid symbol.")
            if (j.name.length > 120 || j.completedSteps.any { it !in 1..ResearchStep.COUNT } || j.currentStep !in 0..6 || j.quizzes.size > 20 ||
                j.quizzes.keys.any { it.length > 40 } || j.lastVisited > limit || j.startedAt > limit) invalid("Invalid progress.")
            j.copy(symbol = symbol, completedSteps = j.completedSteps.distinct().sorted())
        })
    }

    private fun invalid(message: String): Nothing = throw UserDataException(400, "INVALID_PROGRESS", message)

    /** StockSteps+ only, checked before anything else; fair-use limit per day. */
    suspend fun ask(uid: String, symbol: String, question: ResearchQuestion): ResearchAnswer {
        if (!entitlements.get(uid).plus) throw UserDataException(403, "PLUS_REQUIRED", "Ask StockSteps AI is part of StockSteps+.")
        val trimmed = question.question.trim()
        if (trimmed.length !in 3..300) throw UserDataException(400, "INVALID_QUESTION", "Ask a question of 3–300 characters.")
        if (question.step != null && question.step !in 1..ResearchStep.COUNT) throw UserDataException(400, "INVALID_QUESTION", "Unknown step.")
        val key = symbol.uppercase()
        if (!symbolPattern.matches(key)) throw UserDataException(400, "INVALID_SYMBOL", "Invalid symbol.")
        val provider = research ?: throw UserDataException(503, "AI_UNAVAILABLE", "StockSteps AI isn't available yet.")
        val day = "$uid:${LocalDate.now(clock.withZone(ZoneOffset.UTC))}"
        if (aiUsage.size > 5_000) aiUsage.keys.removeIf { !it.endsWith(":" + day.substringAfterLast(':')) } // earlier days only
        val used = aiUsage.merge(day, 1, Int::plus)!!
        if (used > aiDailyLimit) { aiUsage.merge(day, -1, Int::plus); throw UserDataException(429, "AI_LIMIT", "You've reached today's limit of $aiDailyLimit questions.") }
        // Only public company data is sent; learning progress and holdings are never included.
        val snapshot = GuidedResearchEngine.build(details(key), LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString())
        return provider.answer(trimmed, question.step, snapshot).copy(remainingToday = aiDailyLimit - used)
    }
}

/** Signed-in only; the owner comes from the verified token, never from the request. */
fun Route.learningRoutes(auth: UserAuthenticator, service: LearningService) {
    route("/api/v1/me") {
        get("/learning") { user(auth) { uid -> call.respond(service.get(uid)) } }
        put("/learning") { user(auth) { uid -> call.respond(service.save(uid, call.receive<LearningProgressDocument>())) } }
        post("/research/{symbol}/ask") { user(auth) { uid -> call.respond(service.ask(uid, call.parameters["symbol"].orEmpty(), call.receive<ResearchQuestion>())) } }
    }
}

package org.example.stocksteps.userdata

import com.google.auth.oauth2.GoogleCredentials
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import org.example.stocksteps.httpclient.apiCall
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.example.stocksteps.model.EarningsDateStatus
import org.example.stocksteps.model.EarningsTime
import org.example.stocksteps.model.UpcomingEarnings
import java.util.concurrent.CopyOnWriteArrayList

/** [channel] is the Android notification channel ("stock_alerts", "earnings_reminders"). */
data class PushMessage(val token: String, val title: String, val body: String, val data: Map<String, String>, val channel: String = "stock_alerts")

sealed interface PushResult {
    /** The provider accepted the message. This is not proof the user saw it. */
    data class Accepted(val messageId: String?) : PushResult
    /** MOCK: recorded locally, nothing sent. */
    data object Simulated : PushResult
    /** The token is no longer valid; the device registration is removed. */
    data object InvalidToken : PushResult
    data class Retryable(val reason: String) : PushResult
    data class Failed(val reason: String) : PushResult
}

fun interface PushSender {
    suspend fun send(message: PushMessage): PushResult
}

/** MOCK and tests: records messages in memory; never contacts FCM. */
class SimulatedPushSender : PushSender {
    val sent = CopyOnWriteArrayList<PushMessage>()
    override suspend fun send(message: PushMessage): PushResult { sent += message; return PushResult.Simulated }
}

/**
 * REAL: Firebase Cloud Messaging HTTP v1 with Application Default Credentials (the Cloud Run
 * service account needs the "Firebase Cloud Messaging API Admin" role). Notification text never
 * includes personal notes. APNs delivery requires the APNs key uploaded in the Firebase console.
 */
class FcmPushSender(
    private val client: HttpClient,
    private val projectId: String,
    private val credentials: GoogleCredentials = GoogleCredentials.getApplicationDefault()
        .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
) : PushSender {
    override suspend fun send(message: PushMessage): PushResult {
        val token = withContext(Dispatchers.IO) { credentials.refreshIfExpired(); credentials.accessToken?.tokenValue }
            ?: return PushResult.Failed("No FCM credentials")
        val body = buildJsonObject {
            putJsonObject("message") {
                put("token", message.token)
                putJsonObject("notification") { put("title", message.title); put("body", message.body) }
                putJsonObject("data") { message.data.forEach { (key, value) -> put(key, value) } }
                putJsonObject("android") { put("priority", "high"); putJsonObject("notification") { put("channel_id", message.channel) } }
                putJsonObject("apns") { putJsonObject("payload") { putJsonObject("aps") { put("sound", "default") } } }
            }
        }
        return try {
            val response = client.post("https://fcm.googleapis.com/v1/projects/$projectId/messages:send") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
                timeout { requestTimeoutMillis = 10_000 }
            }
            val text = response.bodyAsText()
            when (response.status.value) {
                200 -> PushResult.Accepted(runCatching { Json.parseToJsonElement(text).jsonObject["name"]?.jsonPrimitive?.content }.getOrNull())
                404 -> PushResult.InvalidToken
                400 -> if (text.contains("UNREGISTERED") || text.contains("registration token", ignoreCase = true)) PushResult.InvalidToken else PushResult.Failed("Rejected (400)")
                429, 500, 502, 503, 504 -> PushResult.Retryable("FCM ${response.status.value}")
                else -> PushResult.Failed("FCM ${response.status.value}")
            }
        } catch (cause: Exception) {
            if (cause is kotlinx.coroutines.CancellationException && cause !is kotlinx.coroutines.TimeoutCancellationException) throw cause
            PushResult.Retryable(cause::class.simpleName ?: "network")
        }
    }
}

/** Next scheduled earnings for a symbol, or null when none is known in the next 90 days. */
fun interface EarningsCalendarSource {
    suspend fun upcoming(symbol: String): UpcomingEarnings?
    /** The latest reported event if it was reported in the last few days (results reminders). */
    suspend fun recentResult(symbol: String): org.example.stocksteps.earnings.EarningsEvent? = null
}


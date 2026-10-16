package org.example.stocksteps.network

import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.isSaved
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.discard
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.TimeSource

/** Logcat priority per line (failures as warnings); iOS prints every level the same way. */
enum class ApiLogLevel { INFO, WARN }

/** Where finished, already-redacted lines go (Logcat tag [ApiLogging.TAG] / iOS console). */
fun interface ApiLogSink { fun write(level: ApiLogLevel, line: String) }

/**
 * Ordered, non-blocking line writer: the request path only enqueues a builder; formatting and
 * redaction run on [dispatcher], never on the caller's (possibly main) thread. A full queue drops
 * the oldest lines rather than waiting. [dispatcher] null writes inline (tests).
 */
class ApiLogWriter(private val sink: ApiLogSink, private val dispatcher: CoroutineDispatcher? = Dispatchers.Default) {
    private val queue = Channel<Pair<ApiLogLevel, () -> String>>(capacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        if (dispatcher != null) CoroutineScope(SupervisorJob() + dispatcher).launch {
            for ((level, build) in queue) write(level, build)
        }
    }

    fun emit(level: ApiLogLevel, build: () -> String) {
        if (dispatcher == null) write(level, build) else queue.trySend(level to build)
    }

    // Logging never breaks a request: a failing formatter or sink loses only its own line.
    private fun write(level: ApiLogLevel, build: () -> String) {
        try { sink.write(level, build()) } catch (_: Throwable) {}
    }
}

/**
 * What the debug log needs from the host app. [environment] is the explicit MOCK/REAL choice
 * (never inferred from the URL). [errorKind] lets a platform classify its own engine errors
 * (Darwin `NSError` codes); null falls back to the exception type.
 */
class ApiLogSettings(
    val environment: () -> String,
    val writer: ApiLogWriter,
    val maxBodyChars: Int = ApiLogging.DEFAULT_MAX_BODY_CHARS,
    val errorKind: (Throwable) -> String? = { null }
)

/**
 * DEBUG-only HTTP request/response log for the app's backend clients (`StockStepsApi`, `UserApi`).
 * Off unless [enable] is called with `debugBuild = true`; Android passes `BuildConfig.DEBUG` and the
 * app's debuggable flag, iOS `#if DEBUG` and the framework's debug-binary flag, so release builds
 * can't turn it on. The backend environment (MOCK/REAL) never enables it. Every printed value
 * goes through [ApiLogRedaction].
 */
object ApiLogging {
    const val TAG = "StockSteps-API"
    const val DEFAULT_MAX_BODY_CHARS = 2048

    @Volatile var settings: ApiLogSettings? = null
        private set

    val enabled: Boolean get() = settings != null

    /** Returns whether logging is now on. A non-debug build always leaves (and turns) it off. */
    fun enable(debugBuild: Boolean, settings: ApiLogSettings): Boolean {
        this.settings = if (debugBuild) settings else null
        return this.settings != null
    }

    fun disable() { settings = null }

    /** A host configuration line (start-up backend choice); redacted like everything else. */
    fun config(message: String) {
        settings?.writer?.emit(ApiLogLevel.INFO) { "CONFIG " + ApiLogRedaction.text(message) }
    }
}

/** Distinct per request, also for concurrent requests: a per-process prefix plus a counter. */
@OptIn(ExperimentalAtomicApi::class)
internal object ApiLogIds {
    private val prefix = (1..4).map { "0123456789abcdefghijklmnopqrstuvwxyz"[Random.nextInt(36)] }.joinToString("")
    private val counter = AtomicInt(0)
    fun next(): String = "$prefix-" + counter.incrementAndFetch().toString().padStart(4, '0')
}

class ApiLoggingConfig {
    /** Read per request, so enabling or disabling applies to every existing client. */
    var settings: () -> ApiLogSettings? = { ApiLogging.settings }
}

private val ApiLogIdKey = AttributeKey<String>("StockStepsApiLogId")

/** One per process: the apps have several clients (public and account graphs), and a switch is logged once. */
private object EnvironmentTracker { @Volatile var last: String? = null }

/**
 * Installed by the apps' `createBackendClient()` only (never on the server). It changes nothing
 * about the request or response: the hook only observes, rethrows every failure unchanged, and reads
 * the response body from the copy Ktor already saved for non-streaming calls (streaming bodies are
 * never touched: [ApiLogRedaction.NOT_AVAILABLE]).
 */
@OptIn(InternalAPI::class)
val StockStepsApiLogging = createClientPlugin("StockStepsApiLogging", ::ApiLoggingConfig) {
    val settingsOf = pluginConfig.settings
    val tracker = EnvironmentTracker

    on(Send) { request ->
        val settings = settingsOf() ?: return@on proceed(request)
        val id = ApiLogIds.next()
        request.attributes.put(ApiLogIdKey, id)
        val environment = runCatching { settings.environment() }.getOrNull()?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
        val url = request.url.build()
        val method = request.method.value
        val started = TimeSource.Monotonic.markNow()
        val timestamp = Clock.System.now()
        val content = request.body as? OutgoingContent
        val contentType = content?.contentType
        val headers = request.headers.entries().flatMap { (name, values) -> values.map { name to it } } +
            listOfNotNull(contentType?.let { HttpHeaders.ContentType to it.toString() }, content?.contentLength?.takeIf { it > 0 }?.let { HttpHeaders.ContentLength to it.toString() })
        val body: ByteArray? = when (content) {
            null, is OutgoingContent.NoContent -> ByteArray(0)
            is OutgoingContent.ByteArrayContent -> content.bytes()
            else -> null // streamed request bodies are never read for logging
        }
        val line = "env=$environment method=$method path=${ApiLogRedaction.path(url.encodedPath)}"
        val userData = ApiLogRedaction.isUserDataPath(url.encodedPath)

        val previous = tracker.last
        if (previous != environment) {
            tracker.last = environment
            settings.writer.emit(ApiLogLevel.INFO) { "ENV id=$id from=${previous ?: "-"} to=$environment host=${url.host} port=${url.port}" }
        }
        settings.writer.emit(ApiLogLevel.INFO) {
            "REQ id=$id env=$environment ts=$timestamp method=$method url=${ApiLogRedaction.url(url)} host=${url.host} port=${url.port} " +
                "path=${ApiLogRedaction.path(url.encodedPath)} query=${ApiLogRedaction.query(url).ifEmpty { "-" }} " +
                "contentType=${contentType?.withoutParameters() ?: "-"} headers=${ApiLogRedaction.headers(headers)} " +
                "body=${ApiLogRedaction.body(body, content?.contentLength ?: body?.size?.toLong(), contentType, userData, settings.maxBodyChars)}"
        }
        try {
            val call = proceed(request)
            val response = call.response
            val duration = started.elapsedNow().inWholeMilliseconds
            val status = response.status.value
            val responseType = response.contentType()
            val responseHeaders = response.headers.entries().flatMap { (name, values) -> values.map { name to it } }
            val saved = savedBody(response, responseType)
            settings.writer.emit(if (status >= 400) ApiLogLevel.WARN else ApiLogLevel.INFO) {
                "RES id=$id $line status=$status result=${resultOf(status)} durationMs=$duration " +
                    "size=${saved.size?.toString() ?: ApiLogRedaction.NOT_AVAILABLE} contentType=${responseType?.withoutParameters() ?: "-"} " +
                    "headers=${ApiLogRedaction.headers(responseHeaders)} " +
                    // Error payloads (code, message) stay readable; signed-in success payloads are metadata only.
                    "body=${ApiLogRedaction.body(saved.bytes, saved.size, responseType, userData && status in 200..299, settings.maxBodyChars)}"
            }
            call
        } catch (cause: Throwable) {
            val duration = started.elapsedNow().inWholeMilliseconds
            val (kind, source) = ApiLogErrors.classify(cause, settings.errorKind)
            if (kind == ApiLogErrors.CANCELLED) {
                settings.writer.emit(ApiLogLevel.INFO) { "CANCEL id=$id $line type=${ApiLogErrors.typeName(cause)} durationMs=$duration" }
            } else {
                // The classified cause names the failure (a timeout arrives wrapped in a CancellationException).
                settings.writer.emit(ApiLogLevel.WARN) {
                    "ERR id=$id $line kind=$kind type=${ApiLogErrors.typeName(source)} durationMs=$duration message=${ApiLogErrors.safeMessage(source)}"
                }
            }
            throw cause
        }
    }

    // Deserialization runs later, when the caller reads `body<T>()`; Transform (ContentNegotiation)
    // comes after Receive, so its failures pass through here. Rethrown unchanged.
    client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
        try {
            proceed()
        } catch (cause: Throwable) {
            val settings = settingsOf()
            if (settings != null && cause !is CancellationException) {
                val id = context.attributes.getOrNull(ApiLogIdKey) ?: "-"
                val url = context.request.url
                val target = subject.expectedType.type.simpleName ?: "?"
                val environment = runCatching { settings.environment() }.getOrNull() ?: "UNKNOWN"
                settings.writer.emit(ApiLogLevel.WARN) {
                    "ERR id=$id env=$environment method=${context.request.method.value} path=${ApiLogRedaction.path(url.encodedPath)} " +
                        "kind=${ApiLogErrors.SERIALIZATION} type=${ApiLogErrors.typeName(cause)} target=$target detail=${ApiLogErrors.serializationDetail(cause)}"
                }
            }
            throw cause
        }
    }
}

private class SavedBody(val bytes: ByteArray?, val size: Long?)

/** The saved copy's bytes (in memory already); nothing is read from a streaming response. */
@OptIn(InternalAPI::class)
private suspend fun savedBody(response: HttpResponse, contentType: ContentType?): SavedBody {
    val declared = response.contentLength()
    if (!response.isSaved) return SavedBody(null, declared)
    return try {
        val channel = response.rawContent // a fresh channel over the saved bytes each time
        if (ApiLogRedaction.isBinary(contentType)) return SavedBody(null, declared ?: channel.discard())
        val prefix = channel.readRemaining(ApiLogRedaction.PARSE_LIMIT_BYTES + 1L).readByteArray()
        SavedBody(prefix, prefix.size + channel.discard())
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Throwable) {
        SavedBody(null, declared)
    }
}

private fun resultOf(status: Int) = when (status) {
    in 200..299 -> "OK"
    in 300..399 -> "REDIRECT"
    in 400..499 -> "HTTP_4XX"
    in 500..599 -> "HTTP_5XX"
    else -> "HTTP_$status"
}

/** Error classification and safe context. Only type names and redacted, bounded messages; never stack traces. */
object ApiLogErrors {
    const val CANCELLED = "CANCELLED"
    const val SERIALIZATION = "SERIALIZATION"

    fun typeName(cause: Throwable): String = cause::class.simpleName ?: "Throwable"

    fun kind(cause: Throwable, platform: (Throwable) -> String? = { null }): String = classify(cause, platform).first

    /** The kind and the exception in the cause chain that decided it. */
    fun classify(cause: Throwable, platform: (Throwable) -> String? = { null }): Pair<String, Throwable> {
        var current: Throwable? = cause
        var depth = 0
        while (current != null && depth < 6) {
            val found = current
            fun result(kind: String) = kind to found
            (runCatching { platform(found) }.getOrNull())?.let { return result(it) }
            val name = typeName(found)
            when {
                name == "HttpRequestTimeoutException" -> return result("TIMEOUT_REQUEST")
                name == "ConnectTimeoutException" -> return result("TIMEOUT_CONNECT")
                name == "SocketTimeoutException" -> return result("TIMEOUT_SOCKET")
                "Timeout" in name -> return result("TIMEOUT")
                name == "UnknownHostException" || name == "UnresolvedAddressException" -> return result("DNS")
                name.startsWith("SSL") || "Certificate" in name || name.startsWith("Tls") -> return result("TLS")
                name == "ConnectException" -> return result(connectKind(found))
                name == "NoRouteToHostException" -> return result("NO_ROUTE")
                name == "JsonConvertException" || name == "NoTransformationFoundException" || "Serialization" in name ||
                    name == "JsonDecodingException" || name == "MissingFieldException" -> return result(SERIALIZATION)
            }
            current = found.cause.takeIf { it !== found }
            depth++
        }
        if (cause is CancellationException) return CANCELLED to cause
        val name = typeName(cause)
        return (if (name.endsWith("IOException") || name == "EOFException" || name.endsWith("ClosedReceiveChannelException")) "NETWORK" else "UNKNOWN") to cause
    }

    /** OkHttp wraps the socket error ("ECONNREFUSED", "EHOSTUNREACH (No route to host)") in "Failed to connect to …". */
    private fun connectKind(cause: Throwable): String {
        val messages = generateSequence(cause) { it.cause.takeIf { next -> next !== it } }.take(6).joinToString(" ") { it.message.orEmpty() }.lowercase()
        return when {
            "refused" in messages -> "CONNECTION_REFUSED"
            "no route" in messages || "ehostunreach" in messages || "enetunreach" in messages || "network is unreachable" in messages -> "NO_ROUTE"
            else -> "CONNECT_FAILED"
        }
    }

    /** Redacted (URLs, tokens, e-mails) and bounded; serialization messages quote the body, so they get [serializationDetail] instead. */
    fun safeMessage(cause: Throwable): String {
        if (kind(cause) == SERIALIZATION) return serializationDetail(cause)
        val message = cause.message?.replace('\n', ' ')?.takeIf { it.isNotBlank() } ?: return "-"
        return ApiLogRedaction.truncate(ApiLogRedaction.text(message), 200)
    }

    private val PATH = Regex("at path:? (\\$[A-Za-z0-9_.\\[\\]]*)")
    private val FIELD = Regex("Fields? \\[?'?([A-Za-z0-9_, ]{1,80})'?]? (?:is|are) required")
    private val UNKNOWN_KEY = Regex("unknown key '([A-Za-z0-9_]{1,40})'")

    /** Field names and JSON path only, never the quoted input. */
    fun serializationDetail(cause: Throwable): String {
        val messages = generateSequence(cause) { it.cause.takeIf { next -> next !== it } }.take(4).mapNotNull { it.message }.joinToString(" ")
        val parts = listOfNotNull(
            FIELD.find(messages)?.let { "missing=" + it.groupValues[1].replace(" ", "") },
            UNKNOWN_KEY.find(messages)?.let { "unknownKey=" + it.groupValues[1] },
            PATH.find(messages)?.let { "jsonPath=" + it.groupValues[1] }
        )
        return parts.joinToString(",").ifEmpty { "-" }
    }
}

/** Host and port only, for start-up lines (`real=…`, `mock=…`). */
fun ApiLogRedaction.baseUrl(value: String?): String =
    value?.let { runCatching { Url(it) }.getOrNull() }?.let { "${it.protocol.name}://${it.host}:${it.port}" } ?: "-"

/** The start-up line both apps print: stored and effective environment, base URLs as host and port only. */
fun ApiLogging.start(stored: String, effective: String, real: String?, mock: String?) =
    config("start stored=$stored effective=$effective real=${ApiLogRedaction.baseUrl(real)} mock=${ApiLogRedaction.baseUrl(mock)}")

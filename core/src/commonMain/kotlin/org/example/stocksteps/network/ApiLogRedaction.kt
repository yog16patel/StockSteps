package org.example.stocksteps.network

import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.*

/**
 * Default-deny redaction for the debug API log ([ApiLogging]). Everything that reaches a log line
 * passes through here: URLs, query strings, headers, JSON bodies (nested), error payloads and
 * exception messages. Only listed headers keep their values; sensitive keys and value shapes
 * (tokens, e-mails, phone and card numbers, opaque ids) become [REDACTED]. Signed-in user data
 * (`/api/v1/me/…`: portfolio, holdings, practice, account) is reduced to its shape ([shape]),
 * so no financial value is ever printed.
 */
object ApiLogRedaction {
    const val REDACTED = "[REDACTED]"
    const val BINARY = "[BINARY CONTENT OMITTED]"
    const val TRUNCATED = "[TRUNCATED]"
    const val NOT_AVAILABLE = "[NOT AVAILABLE]"

    /** JSON bodies larger than this are never parsed (and so never printed). */
    const val PARSE_LIMIT_BYTES = 256 * 1024

    /** Headers whose values are printed; every other header is listed by name with [REDACTED]. */
    private val SAFE_HEADERS = setOf(
        "accept", "accept-charset", "accept-encoding", "accept-language", "age", "cache-control", "content-encoding",
        "content-language", "content-length", "content-type", "date", "etag", "expires", "last-modified", "retry-after",
        "transfer-encoding", "vary", "connection", "keep-alive", "server"
    )

    /** Key fragments that are sensitive wherever they appear (normalized: lower case, no `_`/`-`). */
    private val SENSITIVE_FRAGMENTS = listOf(
        "token", "password", "passwd", "secret", "apikey", "authorization", "cookie", "email", "phone", "signature",
        "credential", "iban", "cvv", "cardnumber", "accountnumber", "routingnumber", "privatekey", "bearer", "jwt"
    )
    /** Keys that are sensitive only as a whole name (so "author" or "keyMetrics" stay readable). */
    private val SENSITIVE_NAMES = setOf("key", "auth", "uid", "user", "owner", "pin", "card", "sub", "account", "device", "install", "iss", "aud", "ssn", "otp")
    private val SENSITIVE_SUFFIXES = listOf("uid", "userid", "ownerid", "accountid", "deviceid", "installid", "sessionid", "customerid")

    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val JWT = Regex("eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}(\\.[A-Za-z0-9_-]*)?")
    private val BEARER = Regex("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]+")
    private val MOCK_TOKEN = Regex("mock-user:[A-Za-z0-9:_-]+")
    private val PHONE = Regex("\\+\\d[\\d ().-]{7,}\\d|\\(\\d{3}\\)\\s?\\d{3}-\\d{4}")
    private val CARD = Regex("^(?:\\d[ -]?){12,18}\\d$")
    private val OPAQUE = Regex("^[A-Za-z0-9_\\-+/=.]{32,}$")
    private val URL_IN_TEXT = Regex("https?://[^\\s\"'<>]+")
    private val KEY_VALUE_IN_TEXT = Regex("(?i)\\b([A-Za-z_]*(?:key|token|secret|password|signature|session)[A-Za-z_]*)=([^&\\s\"']+)")
    private val STATIC_SEGMENT = Regex("^[a-z]+(-[a-z]+)*$")

    fun isSensitiveKey(name: String): Boolean {
        val key = name.lowercase().replace("_", "").replace("-", "")
        return key in SENSITIVE_NAMES || SENSITIVE_FRAGMENTS.any { it in key } || SENSITIVE_SUFFIXES.any { key.endsWith(it) }
    }

    /** Signed-in routes: bodies are logged as metadata only, and id path segments are hidden. */
    fun isUserDataPath(path: String): Boolean = "/api/v1/me/" in "$path/"

    /** Scheme, host, port, path and redacted query; user info and fragment are dropped. */
    fun url(url: Url): String = buildString {
        append(url.protocol.name).append("://").append(url.host)
        if (url.port != url.protocol.defaultPort) append(':').append(url.port)
        append(path(url.encodedPath))
        query(url).takeIf { it.isNotEmpty() }?.let { append('?').append(it) }
    }

    /** Under `/api/v1/me/` every segment that isn't a lower-case route word (ids, symbols, names) is hidden. */
    fun path(encodedPath: String): String {
        if (!isUserDataPath(encodedPath)) return encodedPath.ifEmpty { "/" }
        val segments = encodedPath.split('/')
        val me = segments.indexOf("me")
        return segments.mapIndexed { index, segment ->
            if (index <= me || segment.isEmpty() || STATIC_SEGMENT.matches(segment)) segment else REDACTED
        }.joinToString("/")
    }

    fun query(url: Url): String = url.parameters.entries().flatMap { (name, values) ->
        values.map { value -> "$name=" + if (isSensitiveKey(name)) REDACTED else text(value) }
    }.joinToString("&")

    /** `{Name=value, …}` in the given order; values only for [SAFE_HEADERS]. */
    fun headers(headers: List<Pair<String, String>>): String =
        headers.joinToString(", ", "{", "}") { (name, value) ->
            "$name=" + if (name.lowercase() in SAFE_HEADERS) text(value) else REDACTED
        }

    /** Free text (exception messages, non-JSON bodies, string values): URLs sanitized, secret shapes replaced. */
    fun text(value: String): String {
        var out = URL_IN_TEXT.replace(value) { match ->
            runCatching { url(URLBuilder(match.value).build()) }.getOrDefault(REDACTED)
        }
        out = JWT.replace(out, REDACTED)
        out = BEARER.replace(out) { "${it.groupValues[1]} $REDACTED" }
        out = MOCK_TOKEN.replace(out, REDACTED)
        out = KEY_VALUE_IN_TEXT.replace(out) { "${it.groupValues[1]}=$REDACTED" }
        out = EMAIL.replace(out, REDACTED)
        out = PHONE.replace(out, REDACTED)
        if (CARD.matches(out.trim())) return REDACTED
        if (OPAQUE.matches(out) && out.any { it.isDigit() } && out.any { it.isLetter() }) return REDACTED
        return out
    }

    /** Sensitive keys replaced whatever their value; strings redacted by shape; numbers and booleans kept. */
    fun json(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) -> if (isSensitiveKey(key)) JsonPrimitive(REDACTED) else json(value) })
        is JsonArray -> JsonArray(element.map(::json))
        is JsonNull -> element
        is JsonPrimitive -> if (element.isString) JsonPrimitive(text(element.content)) else element
    }

    /**
     * Metadata only (for signed-in user data): key names, value types and array sizes, two levels deep.
     * Keys that look like ids (digits, long) are hidden too, since maps can be keyed by account or symbol.
     */
    fun shape(element: JsonElement, depth: Int = 0): String = when (element) {
        is JsonObject -> if (depth >= 2) "object(${element.size})" else element.entries.joinToString(",", "{", "}") { (key, value) ->
            val name = if (key.any { it.isDigit() } || key.length > 32 || isSensitiveKey(key)) REDACTED else key
            "$name:" + if (isSensitiveKey(key)) REDACTED else shape(value, depth + 1)
        }
        is JsonArray -> "array(${element.size})" + (element.firstOrNull()?.takeIf { it is JsonObject && depth < 2 }?.let { "[" + shape(it, depth) + "]" } ?: "")
        is JsonNull -> "null"
        is JsonPrimitive -> when {
            element.isString -> "string"
            element.booleanOrNull != null -> "boolean"
            else -> "number"
        }
    }

    fun isBinary(contentType: ContentType?): Boolean {
        if (contentType == null) return false
        val type = contentType.contentType.lowercase()
        val sub = contentType.contentSubtype.lowercase()
        if (type in setOf("image", "audio", "video", "font", "multipart")) return true
        if (type == "application") return !(sub == "json" || sub.endsWith("+json") || sub == "xml" || sub.endsWith("+xml") ||
            sub == "x-www-form-urlencoded" || sub == "javascript" || sub == "problem+json")
        return false
    }

    /**
     * A safe one-line rendering of a body. [bytes] null = not readable without consuming it (streaming);
     * [userData] = signed-in success payload (metadata only); [maxChars] bounds the printed text.
     */
    fun body(bytes: ByteArray?, totalSize: Long?, contentType: ContentType?, userData: Boolean, maxChars: Int): String {
        if (isBinary(contentType)) return if (totalSize == 0L) "<empty>" else BINARY
        if (bytes == null) return NOT_AVAILABLE
        if (bytes.isEmpty() && (totalSize ?: 0L) == 0L) return "<empty>"
        val size = totalSize ?: bytes.size.toLong()
        if (size > PARSE_LIMIT_BYTES || bytes.size < size) return "$TRUNCATED (size=$size bytes, not inspected)"
        val raw = bytes.decodeToString()
        if (raw.any { it == '\u0000' || it == '�' }) return BINARY
        // Only objects and arrays: the parser reads a bare word like "cash=5" as a lenient literal.
        val parsed = raw.trimStart().firstOrNull()?.takeIf { it == '{' || it == '[' }?.let { runCatching { Json.parseToJsonElement(raw) }.getOrNull() }
        val rendered = when {
            parsed != null && userData -> "[METADATA] " + shape(parsed)
            parsed != null -> json(parsed).toString()
            userData -> REDACTED
            contentType?.contentSubtype?.lowercase() == "x-www-form-urlencoded" ->
                raw.split('&').joinToString("&") { pair -> pair.substringBefore('=').let { if (isSensitiveKey(it)) "$it=$REDACTED" else text(pair) } }
            else -> text(raw.replace('\n', ' ').replace('\r', ' '))
        }
        return truncate(rendered, maxChars)
    }

    fun truncate(value: String, maxChars: Int): String =
        if (value.length <= maxChars) value else value.take(maxChars) + "… $TRUNCATED (${value.length} chars)"
}

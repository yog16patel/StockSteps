package org.example.stocksteps.userdata

import com.google.auth.oauth2.TokenVerifier
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.stocksteps.model.ApiError
import java.util.Base64

/** Resolves the signed-in user from a request's bearer token. The user id is never taken from the client. */
fun interface UserAuthenticator {
    /** The verified Firebase uid, or null when the token is missing, invalid or expired. */
    suspend fun uid(bearerToken: String): String?
}

/**
 * REAL: verifies Firebase ID tokens (RS256, issuer/audience for [projectId], expiry) against
 * Google's published securetoken certificates. No Admin SDK or service-account key is needed.
 */
class FirebaseIdTokenAuthenticator(projectId: String) : UserAuthenticator {
    private val verifier = TokenVerifier.newBuilder()
        .setCertificatesLocation("https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com")
        .setIssuer("https://securetoken.google.com/$projectId")
        .setAudience(projectId)
        .build()

    override suspend fun uid(bearerToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val token = verifier.verify(bearerToken)
            token.payload.subject?.takeIf { it.isNotBlank() && it.length <= 128 }
        } catch (_: TokenVerifier.VerificationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/**
 * MOCK only: reads the uid from the token payload *without* verifying it, so a local mock server
 * can tell accounts apart without contacting Google. Never used in REAL (DataMode refuses MOCK on
 * Cloud Run). Also accepts `mock-user:<uid>` for tests and tools.
 */
class MockUserAuthenticator : UserAuthenticator {
    override suspend fun uid(bearerToken: String): String? {
        if (bearerToken.startsWith("mock-user:")) return bearerToken.removePrefix("mock-user:").takeIf(::validUid)
        val payload = bearerToken.split('.').getOrNull(1) ?: return null
        return runCatching {
            val json = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '='))))
            (json.jsonObject["user_id"] ?: json.jsonObject["sub"])?.jsonPrimitive?.content
        }.getOrNull()?.takeIf(::validUid)
    }

    private fun validUid(uid: String) = uid.isNotBlank() && uid.length <= 128 && uid.all { it.isLetterOrDigit() || it in "-_:" }
}

/** Responds 401 and returns null when the request isn't from a signed-in user. */
suspend fun ApplicationCall.requireUser(authenticator: UserAuthenticator): String? {
    val header = request.headers["Authorization"]
    val token = header?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()
    val uid = token?.takeIf { it.isNotEmpty() && it.length < 8_192 }?.let { authenticator.uid(it) }
    if (uid == null) {
        respond(HttpStatusCode.Unauthorized, ApiError("SIGN_IN_REQUIRED", "Sign in to use watchlists and alerts."))
        return null
    }
    return uid
}

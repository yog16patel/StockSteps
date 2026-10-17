package org.example.stocksteps.userdata

import com.google.auth.oauth2.TokenVerifier
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.stocksteps.security.TokenVerification
import org.example.stocksteps.security.authenticatedUser
import java.util.Base64

/** Resolves the signed-in user from a request's bearer token. The user id is never taken from the client. */
fun interface UserAuthenticator {
    /** The verified Firebase uid, or null when the token is missing, invalid or expired. */
    suspend fun uid(bearerToken: String): String?

    /** Phase 9A: the verification with its failure reason. Authenticators that only know [uid] report every refusal as INVALID. */
    suspend fun verify(bearerToken: String): TokenVerification = uid(bearerToken)?.let { TokenVerification.Valid(it) } ?: TokenVerification.Failure.INVALID
}

/**
 * REAL: verifies Firebase ID tokens against Google's published securetoken certificates. No Admin SDK or service-account key is needed.
 * Checks (Phase 9A, Firebase's documented ID-token rules): RS256 signature by a current securetoken key; `iss` =
 * `https://securetoken.google.com/<projectId>` and `aud` = [projectId] (another project's or environment's tokens are refused); `exp`
 * present and in the future; `iat` and `auth_time` not in the future (60 s skew); `sub` = the uid, 1–128 characters with no `/`, whitespace
 * or control characters (it becomes a Firestore document id). [publicKey], [clock] and [transport] exist for tests only.
 */
class FirebaseIdTokenAuthenticator internal constructor(
    projectId: String,
    publicKey: java.security.PublicKey?,
    private val clock: java.time.Clock,
    transport: com.google.auth.http.HttpTransportFactory?
) : UserAuthenticator {
    constructor(projectId: String) : this(projectId, null, java.time.Clock.systemUTC(), null)

    private val verifier = TokenVerifier.newBuilder()
        .setCertificatesLocation(CERTIFICATES)
        .setIssuer("https://securetoken.google.com/$projectId")
        .setAudience(projectId)
        .setClock { clock.millis() }
        .apply { publicKey?.let { setPublicKey(it) }; transport?.let { setHttpTransportFactory(it) } }
        .build()

    override suspend fun uid(bearerToken: String): String? = (verify(bearerToken) as? TokenVerification.Valid)?.uid

    override suspend fun verify(bearerToken: String): TokenVerification = withContext(Dispatchers.IO) {
        if (bearerToken.length > org.example.stocksteps.security.MAX_BEARER_TOKEN || bearerToken.count { it == '.' } != 2) return@withContext TokenVerification.Failure.MALFORMED
        try {
            val token = verifier.verify(bearerToken)
            val payload = token.payload
            val now = clock.millis() / 1_000
            val authTime = (payload["auth_time"] as? Number)?.toLong()
            val uid = payload.subject
            when {
                token.header.algorithm != "RS256" -> TokenVerification.Failure.BAD_SIGNATURE
                payload.expirationTimeSeconds == null -> TokenVerification.Failure.INVALID
                payload.issuedAtTimeSeconds.let { it == null || it > now + SKEW_SECONDS } -> TokenVerification.Failure.INVALID
                authTime != null && authTime > now + SKEW_SECONDS -> TokenVerification.Failure.INVALID
                uid.isNullOrEmpty() || uid.length > 128 || uid.any { it == '/' || it.isWhitespace() || it.isISOControl() } -> TokenVerification.Failure.INVALID
                else -> TokenVerification.Valid(uid, ((payload["firebase"] as? Map<*, *>)?.get("sign_in_provider") as? String)?.take(40))
            }
        } catch (cause: TokenVerifier.VerificationException) {
            classify(cause.message)
        } catch (_: IllegalArgumentException) {
            TokenVerification.Failure.MALFORMED
        }
    }

    companion object {
        const val CERTIFICATES = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
        private const val SKEW_SECONDS = 60L

        /** google-auth's VerificationException messages → reasons (pinned by `FirebaseTokenVerifierTest`). Unknown messages are INVALID. */
        internal fun classify(message: String?): TokenVerification.Failure = when {
            message == null -> TokenVerification.Failure.INVALID
            message.startsWith("Error parsing") -> TokenVerification.Failure.MALFORMED
            message.startsWith("Expected audience") || message.startsWith("Expected issuer") -> TokenVerification.Failure.WRONG_PROJECT
            message.startsWith("Token is expired") -> TokenVerification.Failure.EXPIRED
            message.startsWith("Error fetching PublicKey") || message.startsWith("No valid public key") -> TokenVerification.Failure.UNAVAILABLE
            message.startsWith("Invalid signature") || message.startsWith("Could not find PublicKey") || message.startsWith("Error validating token") ||
                message.startsWith("Unexpected signing algorithm") -> TokenVerification.Failure.BAD_SIGNATURE
            else -> TokenVerification.Failure.INVALID
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

/**
 * The signed-in caller's uid, or null after responding 401 `SIGN_IN_REQUIRED` / 403 `ACCOUNT_REQUIRED` / 503 `AUTH_UNAVAILABLE`.
 * Phase 9A: reads the principal set by the central gate (`installUserAuthentication`); route tests without the gate verify here.
 */
suspend fun ApplicationCall.requireUser(authenticator: UserAuthenticator): String? = authenticatedUser(authenticator)?.uid

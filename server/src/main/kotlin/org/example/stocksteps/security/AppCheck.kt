package org.example.stocksteps.security

import com.google.auth.oauth2.TokenVerifier
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.stocksteps.service.ProviderUsageMeter

/** Verifies a Firebase App Check token (`X-Firebase-AppCheck`). True only for a valid, unexpired token for this project and an allowed app. */
fun interface AppCheckVerifier {
    suspend fun verify(token: String): Boolean
}

/**
 * Firebase App Check tokens are RS256 JWTs signed with the keys at [JWKS], issued by `https://firebaseappcheck.googleapis.com/<project
 * number>`, with `projects/<project number>` among their audiences and the Firebase app id as subject. [allowedAppIds] (empty = any app of
 * the project) should list the production Android and iOS app ids. Debug-provider tokens are real tokens for registered debug secrets: never
 * register debug secrets on the production project, and keep them out of [allowedAppIds] if a separate debug app is used.
 * Not yet verified against real tokens (needs the Firebase project number and App Check registration) — see the Phase 4 implementation doc.
 */
class FirebaseAppCheckVerifier(private val projectNumber: String, private val allowedAppIds: Set<String> = emptySet()) : AppCheckVerifier {
    init { require(projectNumber.isNotBlank() && projectNumber.all(Char::isDigit)) { "FIREBASE_PROJECT_NUMBER must be the numeric project number" } }
    private val verifier = TokenVerifier.newBuilder()
        .setCertificatesLocation(JWKS)
        .setIssuer("https://firebaseappcheck.googleapis.com/$projectNumber")
        .build()

    override suspend fun verify(token: String): Boolean = withContext(Dispatchers.IO) {
        if (token.length > 4_096) return@withContext false
        try {
            val payload = verifier.verify(token).payload
            val audiences = payload.audienceAsList.orEmpty()
            "projects/$projectNumber" in audiences && (allowedAppIds.isEmpty() || payload.subject in allowedAppIds)
        } catch (_: TokenVerifier.VerificationException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    companion object { const val JWKS = "https://firebaseappcheck.googleapis.com/v1/jwks" }
}

/**
 * Phase 4A App Check: **monitor mode by default** — every request is admitted and the outcome is counted (`appcheck.valid`, `.missing`,
 * `.invalid`, `.unconfigured`); raw tokens are never logged. With [enforce] (env `APP_CHECK_ENFORCE=true`, only after app versions that send
 * tokens dominate) a missing or invalid token is rejected. Enforcement without a verifier is a configuration error (startup fails).
 */
class AppCheckGuard(private val verifier: AppCheckVerifier?, private val enforce: Boolean, private val meter: ProviderUsageMeter = ProviderUsageMeter.shared) {
    init { require(!enforce || verifier != null) { "APP_CHECK_ENFORCE=true requires FIREBASE_PROJECT_NUMBER (App Check verification)" } }

    suspend fun admit(call: ApplicationCall): Boolean {
        val token = call.request.headers[HEADER]?.trim()?.takeIf { it.isNotEmpty() }
        val outcome = when {
            verifier == null -> "unconfigured"
            token == null -> "missing"
            verifier.verify(token) -> "valid"
            else -> "invalid"
        }
        meter.event("appcheck.$outcome")
        return !enforce || outcome == "valid"
    }

    companion object { const val HEADER = "X-Firebase-AppCheck" }
}

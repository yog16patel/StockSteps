package org.example.stocksteps.security

import com.google.auth.oauth2.TokenVerifier
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** The scheduled/internal jobs, each with its own secret (Phase 4A: no secret is shared between unrelated jobs). */
enum class InternalJob(val env: String) {
    ALERTS("ALERTS_EVALUATOR_TOKEN"),
    EARNINGS_REMINDERS("EARNINGS_REMINDERS_TOKEN"),
    DAILY_BRIEF("DAILY_BRIEF_DISPATCH_TOKEN"),
    USAGE_METRICS("USAGE_METRICS_TOKEN");

    companion object {
        /**
         * Per-job secrets from the environment: at least 32 characters; a value configured for more than one job is dropped for all of them
         * (those routes stay absent — fail closed) so one leaked secret can't trigger unrelated jobs.
         */
        fun secrets(env: (String) -> String? = System::getenv): Map<InternalJob, String> {
            val configured = entries.mapNotNull { job -> env(job.env)?.takeIf { it.length >= 32 }?.let { job to it } }
            val shared = configured.groupBy { it.second }.filterValues { it.size > 1 }.keys
            if (shared.isNotEmpty()) org.slf4j.LoggerFactory.getLogger("StockSteps.Internal")
                .error("The same scheduler secret is configured for several internal jobs; those jobs are disabled until each has its own secret.")
            return configured.filter { it.second !in shared }.toMap()
        }
    }
}

/** The verified email of a Google-signed OIDC ID token for this service's audience, or null. */
fun interface OidcVerifier {
    suspend fun email(token: String): String?
}

/** Google ID tokens as sent by Cloud Scheduler (`--oidc-service-account-email`, `--oidc-token-audience`). */
class GoogleOidcVerifier(audience: String) : OidcVerifier {
    private val verifier = TokenVerifier.newBuilder().setAudience(audience).setIssuer("https://accounts.google.com").build()
    override suspend fun email(token: String): String? = withContext(Dispatchers.IO) {
        try {
            val payload = verifier.verify(token).payload
            (payload["email"] as? String)?.takeIf { payload["email_verified"] == true }
        } catch (_: TokenVerifier.VerificationException) { null } catch (_: IllegalArgumentException) { null }
    }
}

/**
 * Authentication for the `/internal/…` routes. Preferred: Cloud Scheduler OIDC — a Google-signed token for [INTERNAL_OIDC_AUDIENCE] whose email is
 * [INTERNAL_OIDC_SERVICE_ACCOUNT]. Fallback: the job's own secret in `X-StockSteps-Scheduler-Token`, compared in constant time. Configured once
 * at startup ([configure]); with neither, REAL routes aren't registered (fail closed). Secrets and tokens are never logged.
 */
object InternalCallers {
    @Volatile private var oidc: Pair<OidcVerifier, String>? = null

    fun configure(verifier: OidcVerifier?, serviceAccount: String?) {
        oidc = if (verifier != null && !serviceAccount.isNullOrBlank()) verifier to serviceAccount else null
    }

    val oidcEnabled: Boolean get() = oidc != null

    fun configureFromEnvironment(env: (String) -> String? = System::getenv) {
        val audience = env("INTERNAL_OIDC_AUDIENCE")?.takeIf { it.isNotBlank() }
        val account = env("INTERNAL_OIDC_SERVICE_ACCOUNT")?.takeIf { it.isNotBlank() }
        configure(audience?.let { GoogleOidcVerifier(it) }, account)
    }

    /** True when the call carries a valid OIDC token for the configured service account, or the job's [secret]. */
    suspend fun authorized(call: ApplicationCall, secret: String?): Boolean {
        oidc?.let { (verifier, account) ->
            val bearer = call.request.headers["Authorization"]?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()
            if (!bearer.isNullOrEmpty() && verifier.email(bearer)?.equals(account, ignoreCase = true) == true) return true
        }
        val presented = call.request.headers["X-StockSteps-Scheduler-Token"] ?: return false
        return secret != null && constantTimeEquals(presented, secret)
    }

    /** Whether a REAL internal route should be registered at all. */
    fun available(secret: String?): Boolean = secret != null || oidcEnabled

    fun constantTimeEquals(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}

package org.example.stocksteps.userdata

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Phase 7D: the REAL [FirebaseIdTokenAuthenticator] refuses tokens that are malformed, for another project, from another issuer or
 * expired. These claims are checked before any signature (and so before any certificate download). Every case → null → 401.
 */
class FirebaseTokenVerifierTest {
    private val verifier = FirebaseIdTokenAuthenticator("stocksteps")
    private fun b64(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
    private fun jwt(aud: String = "stocksteps", iss: String = "https://securetoken.google.com/stocksteps", exp: Long, sub: String = "user-a") =
        "${b64("""{"alg":"RS256","kid":"test","typ":"JWT"}""")}.${b64("""{"aud":"$aud","iss":"$iss","sub":"$sub","iat":${exp - 3600},"exp":$exp}""")}.${b64("not-a-signature")}"

    private fun rejects(token: String) = runBlocking { withTimeout(20_000) { assertNull(verifier.uid(token)) } }

    @Test fun malformedTokensAreRejected() { rejects("garbage"); rejects("a.b"); rejects("") }

    @Test fun anotherProjectOrIssuerIsRejected() {
        val future = System.currentTimeMillis() / 1000 + 3600
        rejects(jwt(aud = "other-project", exp = future))
        rejects(jwt(iss = "https://securetoken.google.com/other-project", exp = future))
    }

    @Test fun expiredTokensAreRejected() { rejects(jwt(exp = System.currentTimeMillis() / 1000 - 7200)) }
}

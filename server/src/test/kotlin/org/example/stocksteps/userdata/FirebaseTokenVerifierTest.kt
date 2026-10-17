package org.example.stocksteps.userdata

import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.json.webtoken.JsonWebSignature
import com.google.api.client.json.webtoken.JsonWebToken
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.example.stocksteps.security.TokenVerification
import java.security.KeyPairGenerator
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Phase 7D: the REAL [FirebaseIdTokenAuthenticator] refuses tokens that are malformed, for another project, from another issuer or
 * expired. These claims are checked before any signature (and so before any certificate download). Every case → null → 401.
 *
 * Phase 9A: the same authenticator with a test RSA key (`publicKey`, test-only constructor) checks real RS256 signatures and reports why
 * a token was refused; a certificate download failure is UNAVAILABLE (→ 503), never a sign-out.
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

    // ---------- Phase 9A: signed tokens ----------

    private val now = Instant.parse("2026-10-17T15:00:00Z")
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val otherKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val signed = FirebaseIdTokenAuthenticator("stocksteps", keys.public, Clock.fixed(now, ZoneOffset.UTC), null)

    private fun sign(
        sub: String? = "uid-alice", aud: String = "stocksteps", iss: String = "https://securetoken.google.com/stocksteps",
        iat: Long? = now.epochSecond - 60, exp: Long? = now.epochSecond + 3_000, authTime: Long? = now.epochSecond - 600,
        provider: String? = "password", key: java.security.PrivateKey = keys.private
    ): String {
        val header = JsonWebSignature.Header().setAlgorithm("RS256").setKeyId("kid-1").setType("JWT")
        val payload = JsonWebToken.Payload().setAudience(aud).setIssuer(iss).setSubject(sub).setIssuedAtTimeSeconds(iat).setExpirationTimeSeconds(exp)
        authTime?.let { payload["auth_time"] = it }
        provider?.let { payload["firebase"] = mapOf("sign_in_provider" to it) }
        return JsonWebSignature.signUsingRsaSha256(key, GsonFactory.getDefaultInstance(), header, payload)
    }
    private fun verify(token: String) = runBlocking { signed.verify(token) }

    @Test fun aValidSignedTokenYieldsTheUidAndProvider() {
        assertEquals(TokenVerification.Valid("uid-alice", "password"), verify(sign()))
        assertEquals("uid-alice", runBlocking { signed.uid(sign()) })
        val anonymous = verify(sign(provider = "anonymous")) as TokenVerification.Valid
        assertEquals(true, anonymous.anonymous, "anonymous accounts are recognised so the gate can refuse them")
    }

    @Test fun signatureProjectExpiryAndClaimsAreEnforced() {
        assertEquals(TokenVerification.Failure.BAD_SIGNATURE, verify(sign(key = otherKeys.private)), "signed by a key that isn't Google's")
        val tampered = sign().split('.').let { (h, _, s) -> "$h.${b64("""{"aud":"stocksteps","iss":"https://securetoken.google.com/stocksteps","sub":"uid-bob","iat":${now.epochSecond - 60},"exp":${now.epochSecond + 3000}}""")}.$s" }
        assertEquals(TokenVerification.Failure.BAD_SIGNATURE, verify(tampered), "a forged uid with someone else's signature")
        assertEquals(TokenVerification.Failure.WRONG_PROJECT, verify(sign(aud = "other-project")))
        assertEquals(TokenVerification.Failure.WRONG_PROJECT, verify(sign(iss = "https://securetoken.google.com/other-project")))
        assertEquals(TokenVerification.Failure.EXPIRED, verify(sign(exp = now.epochSecond - 1)))
        assertEquals(TokenVerification.Failure.INVALID, verify(sign(exp = null)), "no expiry → refused (google-auth alone would accept it)")
        assertEquals(TokenVerification.Failure.INVALID, verify(sign(iat = null)))
        assertEquals(TokenVerification.Failure.INVALID, verify(sign(iat = now.epochSecond + 600)), "issued in the future")
        assertEquals(TokenVerification.Failure.INVALID, verify(sign(authTime = now.epochSecond + 600)), "signed in in the future")
        assertEquals(TokenVerification.Failure.INVALID, verify(sign(sub = "users/uid-bob")), "a uid that would change a Firestore path")
        assertEquals(TokenVerification.Failure.INVALID, verify(sign(sub = "")))
        assertEquals(TokenVerification.Failure.INVALID, verify(sign(sub = "x".repeat(129))))
        val unsigned = "${b64("""{"alg":"none","typ":"JWT"}""")}.${b64("""{"aud":"stocksteps","iss":"https://securetoken.google.com/stocksteps","sub":"uid-bob","iat":${now.epochSecond},"exp":${now.epochSecond + 3000}}""")}.${b64("sig")}"
        assertEquals(TokenVerification.Failure.BAD_SIGNATURE, verify(unsigned), "alg=none (the Auth emulator's format) is never accepted in REAL")
        assertEquals(TokenVerification.Failure.MALFORMED, verify("not.a-jwt"))
        assertEquals(TokenVerification.Failure.MALFORMED, verify("a".repeat(5_000)))
    }

    @Test fun aCertificateOutageIsUnavailableNotASignOut() {
        val offline = com.google.auth.http.HttpTransportFactory {
            object : com.google.api.client.http.HttpTransport() {
                override fun buildRequest(method: String?, url: String?): com.google.api.client.http.LowLevelHttpRequest = throw java.io.IOException("offline")
            }
        }
        val authenticator = FirebaseIdTokenAuthenticator("stocksteps", null, Clock.fixed(now, ZoneOffset.UTC), offline)
        assertEquals(TokenVerification.Failure.UNAVAILABLE, runBlocking { authenticator.verify(sign()) })
    }

    @Test fun libraryMessagesMapToStableReasons() {
        // Pins google-auth's VerificationException wording; a library upgrade that changes it fails here instead of misclassifying.
        assertEquals(TokenVerification.Failure.UNAVAILABLE, FirebaseIdTokenAuthenticator.classify("Error fetching PublicKey from certificate location"))
        assertEquals(TokenVerification.Failure.EXPIRED, FirebaseIdTokenAuthenticator.classify("Token is expired"))
        assertEquals(TokenVerification.Failure.BAD_SIGNATURE, FirebaseIdTokenAuthenticator.classify("Could not find PublicKey for provided keyId: x"))
        assertEquals(TokenVerification.Failure.INVALID, FirebaseIdTokenAuthenticator.classify("something new"))
    }
}

package org.example.stocksteps.network

import io.ktor.http.ContentType
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import org.example.stocksteps.network.ApiLogRedaction.REDACTED
import kotlin.test.*

class ApiLogRedactionTest {
    @Test fun sensitiveKeysAreMatchedWithoutHidingMarketFields() {
        for (key in listOf("Authorization", "idToken", "refresh_token", "access-token", "password", "apiKey", "api_key", "email", "phoneNumber",
            "uid", "userId", "ownerId", "accountId", "deviceId", "installId", "sessionId", "cardNumber", "iban", "key", "secret", "Cookie")) {
            assertTrue(ApiLogRedaction.isSensitiveKey(key), key)
        }
        for (key in listOf("symbol", "name", "author", "price", "sessionDate", "marketSession", "keyMetrics", "description", "accounts", "quantity", "id")) {
            assertFalse(ApiLogRedaction.isSensitiveKey(key), key)
        }
    }

    @Test fun urlsKeepRouteAndSafeQueryButDropCredentials() {
        val url = Url("https://user:pass@api.example.com/api/v1/stocks/search?query=app&token=abc&ownerEmail=pat@example.com&q=pat@example.com#x")
        assertEquals("https://api.example.com/api/v1/stocks/search?query=app&token=$REDACTED&ownerEmail=$REDACTED&q=$REDACTED", ApiLogRedaction.url(url))
        assertEquals("http://10.0.2.2:8081/api/v1/meta", ApiLogRedaction.url(Url("http://10.0.2.2:8081/api/v1/meta")))
    }

    @Test fun signedInPathsHideIdsButKeepRouteWords() {
        assertEquals("/api/v1/me/portfolio/accounts/$REDACTED", ApiLogRedaction.path("/api/v1/me/portfolio/accounts/acc_123"))
        assertEquals("/api/v1/me/practice/challenges/first-buy/complete", ApiLogRedaction.path("/api/v1/me/practice/challenges/first-buy/complete"))
        assertEquals("/api/v1/me/watchlists/$REDACTED/entries", ApiLogRedaction.path("/api/v1/me/watchlists/Growth2/entries"))
        assertEquals("/api/v1/stocks/AAPL/quote", ApiLogRedaction.path("/api/v1/stocks/AAPL/quote"), "public symbols stay")
    }

    @Test fun headersAreDefaultDeny() {
        val line = ApiLogRedaction.headers(listOf("Accept" to "application/json", "Authorization" to "Bearer abc", "Cookie" to "a=b",
            "Set-Cookie" to "sid=1", "X-Api-Key" to "k", "X-Unknown" to "v", "Content-Length" to "42"))
        assertEquals("{Accept=application/json, Authorization=$REDACTED, Cookie=$REDACTED, Set-Cookie=$REDACTED, X-Api-Key=$REDACTED, X-Unknown=$REDACTED, Content-Length=42}", line)
    }

    @Test fun textRedactsTokenShapesEmailsPhonesCardsAndUrls() {
        assertEquals("Bearer $REDACTED", ApiLogRedaction.text("Bearer abc.def-ghi"))
        assertEquals("id $REDACTED", ApiLogRedaction.text("id eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.sig"))
        assertEquals("mail $REDACTED now", ApiLogRedaction.text("mail pat.lee+x@example.co.uk now"))
        assertEquals("call $REDACTED", ApiLogRedaction.text("call +1 (416) 555-0100"))
        assertEquals(REDACTED, ApiLogRedaction.text("4111 1111 1111 1111"))
        assertEquals(REDACTED, ApiLogRedaction.text("AMf-vBy2Fz9kq3H7wJxR0tLm5N8pQ1sV4uY6zC"), "opaque refresh-token shape")
        assertEquals(REDACTED, ApiLogRedaction.text("mock-user:alice"))
        assertEquals("see https://x.test/a?apikey=$REDACTED", ApiLogRedaction.text("see https://x.test/a?apikey=SECRET"))
        assertEquals("api_key=$REDACTED&symbol=AAPL", ApiLogRedaction.text("api_key=SECRET&symbol=AAPL"))
        // Ordinary values survive.
        assertEquals("2026-10-16", ApiLogRedaction.text("2026-10-16"))
        assertEquals("Apple Inc. rose 1.2% on 2026-10-07", ApiLogRedaction.text("Apple Inc. rose 1.2% on 2026-10-07"))
        assertEquals("https://images.test/logo/AAPL.png", ApiLogRedaction.text("https://images.test/logo/AAPL.png"))
    }

    @Test fun jsonIsRedactedAtEveryDepth() {
        val input = Json.parseToJsonElement("""{"a":{"b":[{"refreshToken":"r","c":{"email":"x@y.zz","note":"call +44 20 7946 0958"}}]},"price":1.5,"ok":true}""")
        assertEquals("""{"a":{"b":[{"refreshToken":"$REDACTED","c":{"email":"$REDACTED","note":"call $REDACTED"}}]},"price":1.5,"ok":true}""",
            ApiLogRedaction.json(input).toString())
    }

    @Test fun shapeShowsStructureWithoutValuesOrIdKeys() {
        val input = Json.parseToJsonElement("""{"cash":"100.25","positions":[{"symbol":"AAPL","qty":2}],"byAccount":{"acc_1":{"v":"1"}},"meta":{"deep":{"x":1}},"flag":false,"none":null}""")
        assertEquals("{cash:string,positions:array(1)[{symbol:string,qty:number}],byAccount:{$REDACTED:object(1)},meta:{deep:object(1)},flag:boolean,none:null}",
            ApiLogRedaction.shape(input))
    }

    @Test fun bodiesFollowTypeSizeAndUserDataRules() {
        val json = ContentType.Application.Json
        assertEquals(ApiLogRedaction.NOT_AVAILABLE, ApiLogRedaction.body(null, null, json, false, 100))
        assertEquals("<empty>", ApiLogRedaction.body(ByteArray(0), 0, json, false, 100))
        assertEquals(ApiLogRedaction.BINARY, ApiLogRedaction.body(null, 12, ContentType.Application.Pdf, false, 100))
        assertEquals(ApiLogRedaction.BINARY, ApiLogRedaction.body(byteArrayOf(1, 2), 2, ContentType.Image.JPEG, false, 100))
        assertEquals(ApiLogRedaction.BINARY, ApiLogRedaction.body(byteArrayOf(1, 2), 2, ContentType.Application.OctetStream, false, 100))
        assertEquals("""{"symbol":"AAPL"}""", ApiLogRedaction.body("""{"symbol":"AAPL"}""".encodeToByteArray(), null, json, false, 100))
        assertEquals("[METADATA] {cash:string}", ApiLogRedaction.body("""{"cash":"5"}""".encodeToByteArray(), null, json, true, 100))
        assertEquals(REDACTED, ApiLogRedaction.body("cash=5".encodeToByteArray(), null, ContentType.Text.Plain, true, 100), "non-JSON user data")
        assertEquals("plain text $REDACTED", ApiLogRedaction.body("plain text x@y.zz".encodeToByteArray(), null, ContentType.Text.Plain, false, 100))
        assertEquals("email=$REDACTED&symbol=AAPL", ApiLogRedaction.body("email=a&symbol=AAPL".encodeToByteArray(), null, ContentType.Application.FormUrlEncoded, false, 100))
        val long = ApiLogRedaction.body(("[" + (1..100).joinToString(",") + "]").encodeToByteArray(), null, json, false, 20)
        assertTrue(long == "[1,2,3,4,5,6,7,8,9,1… ${ApiLogRedaction.TRUNCATED} (293 chars)", long)
        assertEquals("${ApiLogRedaction.TRUNCATED} (size=999999 bytes, not inspected)", ApiLogRedaction.body(ByteArray(10), 999_999, json, false, 100))
    }

    @Test fun serializationDetailNamesFieldsAndPathOnly() {
        val cause = IllegalArgumentException("Field 'code' is required for type with serial name 'x.ApiError', but it was missing at path: $.error\nJSON input: {\"cash\":\"5\"}")
        assertEquals("missing=code,jsonPath=$.error", ApiLogErrors.serializationDetail(cause))
        assertEquals("-", ApiLogErrors.serializationDetail(IllegalStateException("JSON input: {\"cash\":\"5\"}")))
    }

    @Test fun baseUrlsShowSchemeHostAndPortOnly() {
        assertEquals("http://10.0.2.2:8081", ApiLogRedaction.baseUrl("http://10.0.2.2:8081"))
        assertEquals("https://api.test:443", ApiLogRedaction.baseUrl("https://user:pw@api.test/path?token=x"))
        assertEquals("-", ApiLogRedaction.baseUrl(null))
    }
}

package xtuaok.sharegyazo.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class GyazoOAuthTest {
    @Test
    fun challengeMatchesRfc7636() {
        // RFC 7636 Appendix B.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            GyazoOAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun stateIsBoundToVerifier() {
        val request = GyazoOAuth.newLoginRequest()
        val (challenge, nonce) = request.state.split(".")
        assertEquals(GyazoOAuth.challenge(request.verifier), challenge)
        assertTrue(nonce.isNotEmpty())
        assertTrue(request.verifier.length >= 43)
        assertNotEquals(request.verifier, GyazoOAuth.newLoginRequest().verifier)
    }

    @Test
    fun authorizeUrlHasNoSecret() {
        val url = GyazoOAuth.newLoginRequest().authorizeUrl.toHttpUrl()
        assertEquals("gyazo.com", url.host)
        assertEquals("/oauth/authorize", url.encodedPath)
        assertEquals(GyazoOAuth.CLIENT_ID, url.queryParameter("client_id"))
        assertEquals(GyazoOAuth.REDIRECT_URI, url.queryParameter("redirect_uri"))
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals(setOf("client_id", "redirect_uri", "response_type", "state"), url.queryParameterNames)
    }
}

class GyazoTokenClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun exchangeSendsCodeStateAndVerifier() {
        server.enqueue(MockResponse.Builder().body("""{"access_token":"tok"}""").build())

        val token = GyazoTokenClient(server.url("/token").toString()).exchange("c", "s", "v")

        assertEquals("tok", token)
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("c", body["code"]!!.jsonPrimitive.content)
        assertEquals("s", body["state"]!!.jsonPrimitive.content)
        assertEquals("v", body["verifier"]!!.jsonPrimitive.content)
    }

    @Test
    fun relayErrorIsReported() {
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":"invalid_verifier"}""").build())
        try {
            GyazoTokenClient(server.url("/token").toString()).exchange("c", "s", "v")
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("invalid_verifier"))
        }
    }
}

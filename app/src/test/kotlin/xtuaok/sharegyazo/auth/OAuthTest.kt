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
import xtuaok.sharegyazo.data.ProfileKind
import java.io.IOException

class OAuthProviderTest {
    @Test
    fun challengeMatchesRfc7636() {
        // RFC 7636 Appendix B.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun stateIsBoundToVerifier() {
        val request = Pkce.newLoginRequest(OAuthProvider.GYAZO)
        val (challenge, nonce) = request.state.split(".")
        assertEquals(Pkce.challenge(request.verifier), challenge)
        assertTrue(nonce.isNotEmpty())
        assertTrue(request.verifier.length >= 43)
        assertNotEquals(request.verifier, Pkce.newLoginRequest(OAuthProvider.GYAZO).verifier)
    }

    @Test
    fun gyazoAuthorizeUrl() {
        val url = OAuthProvider.GYAZO.authorizeUrl("st").toHttpUrl()
        assertEquals("https://gyazo.com/oauth/authorize", url.newBuilder().query(null).build().toString())
        assertEquals(OAuthProvider.GYAZO.clientId, url.queryParameter("client_id"))
        assertEquals(
            "https://xtuaok.sickin.dev/ShareGyazo/oauth/gyazo/callback",
            url.queryParameter("redirect_uri"),
        )
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("st", url.queryParameter("state"))
    }

    @Test
    fun imgurUsesTokenFlowWithoutRedirectUri() {
        val url = OAuthProvider.IMGUR.authorizeUrl("st").toHttpUrl()
        assertEquals("https://api.imgur.com/oauth2/authorize", url.newBuilder().query(null).build().toString())
        assertEquals(setOf("client_id", "response_type", "state"), url.queryParameterNames)
        // imgur: "Only token should be used, as the other methods have been deprecated."
        assertEquals("token", url.queryParameter("response_type"))
    }

    @Test
    fun tokensFromImgurCallback() {
        val params = mapOf("access_token" to "a", "refresh_token" to "r", "expires_in" to "2419200")
        assertEquals(OAuthTokens("a", "r", 1_000L + 2_419_200_000L), OAuthTokens.fromCallback(params::get, now = 1_000L))
        assertEquals(OAuthTokens("a"), OAuthTokens.fromCallback(mapOf("access_token" to "a")::get))
        assertEquals(null, OAuthTokens.fromCallback(mapOf("refresh_token" to "r")::get))
    }

    @Test
    fun relayUrlsAndLookup() {
        assertEquals("https://xtuaok.sickin.dev/ShareGyazo/oauth/imgur/token", OAuthProvider.IMGUR.tokenUrl)
        assertEquals("https://xtuaok.sickin.dev/ShareGyazo/oauth/imgur/refresh", OAuthProvider.IMGUR.refreshUrl)
        assertEquals(OAuthProvider.IMGUR, OAuthProvider.of(ProfileKind.IMGUR))
        assertEquals(null, OAuthProvider.of(ProfileKind.CUSTOM))
        assertEquals(OAuthProvider.GYAZO, OAuthProvider.fromCallbackPath("/gyazo"))
        assertEquals(null, OAuthProvider.fromCallbackPath("/other"))
    }
}

class OAuthClientTest {
    private lateinit var server: MockWebServer
    private val client = OAuthClient(now = { 1_000_000L })

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

        val tokens = client.exchange(server.url("/token").toString(), "c", "s", "v")

        assertEquals(OAuthTokens("tok"), tokens)
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("c", body["code"]!!.jsonPrimitive.content)
        assertEquals("s", body["state"]!!.jsonPrimitive.content)
        assertEquals("v", body["verifier"]!!.jsonPrimitive.content)
    }

    @Test
    fun refreshReturnsNewTokensWithExpiry() {
        server.enqueue(
            MockResponse.Builder().body("""{"access_token":"a2","refresh_token":"r2","expires_in":3600}""").build(),
        )

        val tokens = client.refresh(server.url("/refresh").toString(), "r1")

        assertEquals(OAuthTokens("a2", "r2", 1_000_000L + 3_600_000L), tokens)
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("r1", body["refresh_token"]!!.jsonPrimitive.content)
    }

    @Test
    fun rejectedGrantMeansLoginAgain() {
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":"invalid_grant"}""").build())
        try {
            client.refresh(server.url("/refresh").toString(), "r1")
            fail("expected OAuthRejectedException")
        } catch (e: OAuthRejectedException) {
            assertEquals("invalid_grant", e.message)
        }
    }

    @Test
    fun serverErrorIsRetryable() {
        server.enqueue(MockResponse.Builder().code(502).body("""{"error":"exchange_failed"}""").build())
        try {
            client.exchange(server.url("/token").toString(), "c", "s", "v")
            fail("expected IOException")
        } catch (e: OAuthRejectedException) {
            fail("5xx must not be treated as a rejected grant")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("exchange_failed"))
        }
    }
}

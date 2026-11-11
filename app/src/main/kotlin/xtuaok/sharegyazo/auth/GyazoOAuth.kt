package xtuaok.sharegyazo.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Gyazo OAuth login without shipping the client secret.
 *
 * Gyazo requires the client secret to exchange an authorization code, so the exchange is
 * done by a small relay (site/src/worker.js) that holds the secret. To make an intercepted
 * code useless, the app binds the login to a random verifier, PKCE-style: the state sent to
 * Gyazo starts with SHA-256(verifier), and the relay only exchanges a code when it is given
 * the verifier that matches.
 */
object GyazoOAuth {
    /** Public by design ("you can safely use this in public code" — Gyazo API docs). */
    const val CLIENT_ID = "7je9rgEwUkiejq97COPyFKr4jmZ0fGzALJPwGxtLvuE"
    const val AUTHORIZE_URL = "https://gyazo.com/oauth/authorize"
    const val REDIRECT_URI = "https://xtuaok.sickin.dev/ShareGyazo/oauth/gyazo/callback"
    const val TOKEN_URL = "https://xtuaok.sickin.dev/ShareGyazo/oauth/gyazo/token"

    /** The relay sends the browser back to `sharegyazo://oauth/gyazo?code=...&state=...`. */
    const val CALLBACK_SCHEME = "sharegyazo"
    const val CALLBACK_HOST = "oauth"
    const val CALLBACK_PATH = "/gyazo"

    data class LoginRequest(val verifier: String, val state: String, val authorizeUrl: String)

    fun newLoginRequest(random: SecureRandom = SecureRandom()): LoginRequest {
        val verifier = randomToken(random, 32)
        val state = "${challenge(verifier)}.${randomToken(random, 16)}"
        return LoginRequest(verifier, state, authorizeUrl(state))
    }

    fun authorizeUrl(state: String): String = AUTHORIZE_URL.toHttpUrl().newBuilder()
        .addQueryParameter("client_id", CLIENT_ID)
        .addQueryParameter("redirect_uri", REDIRECT_URI)
        .addQueryParameter("response_type", "code")
        .addQueryParameter("state", state)
        .build()
        .toString()

    /** base64url(SHA-256(verifier)) without padding, as in RFC 7636 (S256). */
    fun challenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun randomToken(random: SecureRandom, bytes: Int): String =
        base64Url(ByteArray(bytes).also(random::nextBytes))

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/** Exchanges an authorization code for an access token through the relay. */
class GyazoTokenClient(
    private val tokenUrl: String = GyazoOAuth.TOKEN_URL,
    private val client: OkHttpClient = defaultClient,
) {
    @Throws(IOException::class)
    fun exchange(code: String, state: String, verifier: String): String {
        val json = buildJsonObject {
            put("code", code)
            put("state", state)
            put("verifier", verifier)
        }.toString()
        val request = Request.Builder()
            .url(tokenUrl)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            val obj = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            val token = obj?.get("access_token")?.jsonPrimitive?.content
            if (!response.isSuccessful || token.isNullOrBlank()) {
                val error = obj?.get("error")?.jsonPrimitive?.content ?: "HTTP ${response.code}"
                throw IOException("Token exchange failed: $error")
            }
            return token
        }
    }

    private companion object {
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}

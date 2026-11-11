package xtuaok.sharegyazo.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import xtuaok.sharegyazo.data.ProfileKind
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * OAuth login for the built-in services without shipping client secrets.
 *
 * - Gyazo only supports the authorization code flow, whose exchange needs the client secret,
 *   so it goes through a small relay (site/src/worker.js) that holds the secret. Each login
 *   is bound to a random verifier, PKCE-style: the state starts with SHA-256(verifier), and
 *   the relay only exchanges a code when it is given the matching verifier.
 * - imgur only supports the token flow (`code` is deprecated): the tokens come back directly,
 *   no secret involved. Refreshing an imgur token needs the secret, so that goes through the
 *   relay.
 */
enum class OAuthProvider(
    /** Path segment used by the relay and by the app callback `sharegyazo://oauth/<id>`. */
    val id: String,
    val displayName: String,
    val kind: ProfileKind,
    /** Public by design; used in the authorize URL (and for imgur anonymous uploads). */
    val clientId: String,
    private val authorizeEndpoint: String,
    /** Sent only when the service expects it; imgur uses the URL registered for the app. */
    private val redirectUri: String?,
    /** `code` (exchanged through the relay) or `token` (tokens returned directly). */
    val responseType: String,
) {
    GYAZO(
        id = "gyazo",
        displayName = "Gyazo",
        kind = ProfileKind.GYAZO,
        clientId = "7je9rgEwUkiejq97COPyFKr4jmZ0fGzALJPwGxtLvuE",
        authorizeEndpoint = "https://gyazo.com/oauth/authorize",
        redirectUri = "$RELAY/gyazo/callback",
        responseType = "code",
    ),
    IMGUR(
        id = "imgur",
        displayName = "imgur",
        kind = ProfileKind.IMGUR,
        clientId = "de242da82e84888",
        authorizeEndpoint = "https://api.imgur.com/oauth2/authorize",
        redirectUri = null,
        responseType = "token",
    ),
    ;

    val tokenUrl: String get() = "$RELAY/$id/token"
    val refreshUrl: String get() = "$RELAY/$id/refresh"

    fun authorizeUrl(state: String): String = authorizeEndpoint.toHttpUrl().newBuilder()
        .addQueryParameter("client_id", clientId)
        .apply { redirectUri?.let { addQueryParameter("redirect_uri", it) } }
        .addQueryParameter("response_type", responseType)
        .addQueryParameter("state", state)
        .build()
        .toString()

    companion object {
        const val CALLBACK_SCHEME = "sharegyazo"
        const val CALLBACK_HOST = "oauth"

        fun of(kind: ProfileKind): OAuthProvider? = entries.firstOrNull { it.kind == kind }

        /** `sharegyazo://oauth/<id>` */
        fun fromCallbackPath(path: String?): OAuthProvider? = entries.firstOrNull { path == "/${it.id}" }
    }
}

private const val RELAY = "https://xtuaok.sickin.dev/ShareGyazo/oauth"

data class LoginRequest(val verifier: String, val state: String, val authorizeUrl: String)

/** [expiresAt] is epoch millis, 0 when the token does not expire. */
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String = "",
    val expiresAt: Long = 0,
    /** Empty when the service does not tell (Gyazo, token refresh). */
    val accountName: String = "",
) {
    companion object {
        /** Tokens from the token flow callback (`access_token`, `refresh_token`, `expires_in`, `account_username`). */
        fun fromCallback(param: (String) -> String?, now: Long = System.currentTimeMillis()): OAuthTokens? {
            val access = param("access_token")?.takeIf { it.isNotBlank() } ?: return null
            val expiresIn = param("expires_in")?.toLongOrNull()
            return OAuthTokens(
                accessToken = access,
                refreshToken = param("refresh_token").orEmpty(),
                expiresAt = expiresIn?.let { now + it * 1000 } ?: 0,
                accountName = param("account_username").orEmpty(),
            )
        }
    }
}

object Pkce {
    fun newLoginRequest(provider: OAuthProvider, random: SecureRandom = SecureRandom()): LoginRequest {
        val verifier = randomToken(random, 32)
        val state = "${challenge(verifier)}.${randomToken(random, 16)}"
        return LoginRequest(verifier, state, provider.authorizeUrl(state))
    }

    /** base64url(SHA-256(verifier)) without padding, as in RFC 7636 (S256). */
    fun challenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun randomToken(random: SecureRandom, bytes: Int): String =
        base64Url(ByteArray(bytes).also(random::nextBytes))

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/** Thrown when the service rejects the grant (e.g. a revoked refresh token): log in again. */
class OAuthRejectedException(message: String) : IOException(message)

/** Talks to the relay. */
class OAuthClient(
    private val client: OkHttpClient = defaultClient,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Throws(IOException::class)
    fun exchange(url: String, code: String, state: String, verifier: String): OAuthTokens = post(
        url,
        buildJsonObject {
            put("code", code)
            put("state", state)
            put("verifier", verifier)
        },
    )

    @Throws(IOException::class)
    fun refresh(url: String, refreshToken: String): OAuthTokens =
        post(url, buildJsonObject { put("refresh_token", refreshToken) })

    private fun post(url: String, payload: JsonObject): OAuthTokens {
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            // Parse leniently: an unexpected shape must end up as an IOException, not a crash.
            val obj = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            fun field(name: String) = (obj?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content
            val token = field("access_token")
            if (!response.isSuccessful || token.isNullOrBlank()) {
                val error = field("error") ?: "HTTP ${response.code}"
                if (response.code == 400) throw OAuthRejectedException(error)
                throw IOException("Token request failed: $error")
            }
            val expiresIn = (obj?.get("expires_in") as? JsonPrimitive)?.longOrNull
            return OAuthTokens(
                accessToken = token,
                refreshToken = field("refresh_token").orEmpty(),
                expiresAt = expiresIn?.let { now() + it * 1000 } ?: 0,
            )
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

package xtuaok.sharegyazo.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xtuaok.sharegyazo.app
import xtuaok.sharegyazo.data.Profile
import java.io.IOException

/** Android side of [OAuthProvider]: login, callback, logout and token refresh. */
object AccountLogin {
    private const val TAG = "AccountLogin"
    private const val PREFS = "oauth_pending"

    /** Refresh this long before the token expires. */
    private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L

    sealed interface Result {
        val provider: OAuthProvider?

        data class Success(override val provider: OAuthProvider) : Result
        data class Failure(override val provider: OAuthProvider?, val message: String) : Result
    }

    fun start(context: Context, provider: OAuthProvider) {
        val request = Pkce.newLoginRequest(provider)
        // Kept on disk: the process may be killed while the browser is in front.
        prefs(context).edit(commit = true) {
            putString(key(provider, "verifier"), request.verifier)
            putString(key(provider, "state"), request.state)
        }
        CustomTabsIntent.Builder().build().launchUrl(context, request.authorizeUrl.toUri())
    }

    fun logout(context: Context, provider: OAuthProvider) {
        context.app.profiles.setTokens(provider.kind, null)
    }

    fun providerOf(uri: Uri?): OAuthProvider? =
        uri?.takeIf { it.scheme == OAuthProvider.CALLBACK_SCHEME && it.host == OAuthProvider.CALLBACK_HOST }
            ?.let { OAuthProvider.fromCallbackPath(it.path) }

    suspend fun complete(context: Context, uri: Uri): Result {
        val provider = providerOf(uri) ?: return Result.Failure(null, "invalid_request")
        val prefs = prefs(context)
        val verifier = prefs.getString(key(provider, "verifier"), null)
        val expectedState = prefs.getString(key(provider, "state"), null)
        // One attempt per login request.
        prefs.edit {
            remove(key(provider, "verifier"))
            remove(key(provider, "state"))
        }

        uri.getQueryParameter("error")?.let { return Result.Failure(provider, it) }
        val state = uri.getQueryParameter("state")
        // Only accept the answer to the login this app started.
        if (verifier == null || state == null || state != expectedState) {
            return Result.Failure(provider, "invalid_request")
        }
        return try {
            val tokens = when (provider.responseType) {
                "token" -> OAuthTokens.fromCallback(uri::getQueryParameter)
                    ?: return Result.Failure(provider, "invalid_request")
                else -> {
                    val code = uri.getQueryParameter("code")
                    if (code.isNullOrEmpty()) return Result.Failure(provider, "invalid_request")
                    withContext(Dispatchers.IO) { OAuthClient().exchange(provider.tokenUrl, code, state, verifier) }
                }
            }
            context.app.profiles.setTokens(provider.kind, tokens)
            Result.Success(provider)
        } catch (e: IOException) {
            Log.w(TAG, "${provider.displayName} login failed", e)
            Result.Failure(provider, e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Returns [profile] with a usable access token, refreshing it through the relay when it
     * is about to expire. Blocking; call off the main thread.
     *
     * Synchronized and based on the stored tokens, not the caller's copy: when two uploads
     * need a refresh at the same time, the second one reuses the first one's result instead
     * of sending a refresh token the service may already have rotated.
     * @throws OAuthRejectedException when the refresh token is no longer valid (the stored
     *   tokens are cleared, so the user is asked to log in again).
     */
    @Synchronized
    @Throws(IOException::class)
    fun ensureFresh(context: Context, profile: Profile, now: Long = System.currentTimeMillis()): Profile {
        val provider = OAuthProvider.of(profile.kind) ?: return profile
        val repo = context.app.profiles
        val current = repo.builtIn(provider.kind) ?: profile
        if (current.tokenExpiresAt == 0L || now < current.tokenExpiresAt - REFRESH_MARGIN_MS) {
            return profile.withCredentialsOf(current)
        }
        if (current.refreshToken.isBlank()) throw OAuthRejectedException("expired")
        try {
            val tokens = OAuthClient().refresh(provider.refreshUrl, current.refreshToken)
            repo.setTokens(provider.kind, tokens.copy(refreshToken = tokens.refreshToken.ifBlank { current.refreshToken }))
        } catch (e: OAuthRejectedException) {
            repo.setTokens(provider.kind, null)
            throw e
        }
        return profile.withCredentialsOf(repo.builtIn(provider.kind) ?: current)
    }

    private fun key(provider: OAuthProvider, name: String) = "${provider.id}_$name"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

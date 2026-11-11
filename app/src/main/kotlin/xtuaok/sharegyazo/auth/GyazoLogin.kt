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

/** Android side of [GyazoOAuth]: opens the login page and handles the callback. */
object GyazoLogin {
    private const val TAG = "GyazoLogin"
    private const val PREFS = "gyazo_oauth"
    private const val KEY_VERIFIER = "verifier"
    private const val KEY_STATE = "state"

    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    fun start(context: Context) {
        val request = GyazoOAuth.newLoginRequest()
        // Kept on disk: the process may be killed while the browser is in front.
        prefs(context).edit(commit = true) {
            putString(KEY_VERIFIER, request.verifier)
            putString(KEY_STATE, request.state)
        }
        CustomTabsIntent.Builder().build().launchUrl(context, request.authorizeUrl.toUri())
    }

    fun logout(context: Context) {
        context.app.profiles.setGyazoToken("")
    }

    fun isCallback(uri: Uri?): Boolean =
        uri?.scheme == GyazoOAuth.CALLBACK_SCHEME &&
            uri.host == GyazoOAuth.CALLBACK_HOST &&
            uri.path == GyazoOAuth.CALLBACK_PATH

    suspend fun complete(context: Context, uri: Uri): Result {
        val prefs = prefs(context)
        val verifier = prefs.getString(KEY_VERIFIER, null)
        val expectedState = prefs.getString(KEY_STATE, null)
        // One attempt per login request.
        prefs.edit { clear() }

        uri.getQueryParameter("error")?.let { return Result.Failure(it) }
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (verifier == null || code.isNullOrEmpty() || state == null || state != expectedState) {
            return Result.Failure("invalid_request")
        }
        return try {
            val token = withContext(Dispatchers.IO) { GyazoTokenClient().exchange(code, state, verifier) }
            context.app.profiles.setGyazoToken(token)
            Result.Success
        } catch (e: Exception) {
            Log.w(TAG, "Gyazo login failed", e)
            Result.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

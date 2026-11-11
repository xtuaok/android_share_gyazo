package xtuaok.sharegyazo.share

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xtuaok.sharegyazo.R
import xtuaok.sharegyazo.app
import xtuaok.sharegyazo.auth.OAuthProvider
import xtuaok.sharegyazo.data.Profile
import xtuaok.sharegyazo.upload.UploadWorker
import java.io.File
import java.util.UUID

sealed interface ShareUiState {
    data object Idle : ShareUiState
    data class ChooseProfile(val profiles: List<Profile>, val initial: Profile) : ShareUiState
    /** Waiting for the upload because a browser or share sheet must be opened afterwards. */
    data class Uploading(val percent: Int?) : ShareUiState
    data class Completed(val url: String, val openBrowser: Boolean) : ShareUiState
    /** A built-in profile was chosen but the user has not logged in to the service yet. */
    data class NeedsLogin(val serviceName: String) : ShareUiState
    data object Finished : ShareUiState
}

class ShareViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    private val _state = MutableStateFlow<ShareUiState>(ShareUiState.Idle)
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    private var source: Uri? = null
    private var started = false

    fun begin(intent: Intent) {
        if (started) return
        started = true

        source = intent.data
            ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (source == null) {
            _state.value = ShareUiState.Finished
            return
        }

        val profiles = app.profiles
        val requested = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
            ?: intent.getStringExtra(EXTRA_LEGACY_UUID)
        val profile = profiles.get(requested) ?: profiles.defaultProfile
        if (profile != null) {
            upload(profile)
        } else {
            val list = profiles.profiles.value
            val initial = profiles.get(profiles.lastUsedUuid) ?: list.first()
            _state.value = ShareUiState.ChooseProfile(list, initial)
        }
    }

    fun onProfileChosen(profile: Profile, always: Boolean) {
        if (always) app.profiles.setDefault(profile.uuid)
        upload(profile)
    }

    fun finish() {
        _state.value = ShareUiState.Finished
    }

    private fun upload(profile: Profile) {
        val uri = source ?: return finish()
        val provider = OAuthProvider.of(profile.kind)
        if (provider != null && !profile.isLoggedIn) {
            _state.value = ShareUiState.NeedsLogin(provider.displayName)
            return
        }
        val settings = app.settings.settings.value
        _state.value = if (settings.needsForegroundAction) ShareUiState.Uploading(null) else ShareUiState.Idle

        viewModelScope.launch {
            // Copy now: the URI read grant does not outlive this activity.
            val file = withContext(Dispatchers.IO) { copyToCache(uri) }
            if (file == null) {
                toast(R.string.error_storage)
                return@launch finish()
            }
            app.profiles.markUsed(profile.uuid)
            ShareShortcuts.reportUsed(app, profile)
            val workId = UploadWorker.enqueue(app, profile, file)

            if (settings.needsForegroundAction) {
                awaitResult(workId, settings.openBrowser)
            } else {
                toast(R.string.dialog_message_uploading)
                finish()
            }
        }
    }

    private suspend fun awaitResult(workId: UUID, openBrowser: Boolean) {
        val info = WorkManager.getInstance(app).getWorkInfoByIdFlow(workId)
            .onEach { info ->
                if (info?.state == WorkInfo.State.RUNNING) {
                    val percent = info.progress.getInt(UploadWorker.KEY_PROGRESS, -1)
                    if (_state.value is ShareUiState.Uploading) {
                        _state.value = ShareUiState.Uploading(percent.takeIf { it >= 0 })
                    }
                }
            }
            .first { it == null || it.state.isFinished }

        val url = info?.outputData?.getString(UploadWorker.KEY_URL)
        // The user may have chosen to continue in the background meanwhile.
        if (_state.value !is ShareUiState.Uploading) return
        _state.value = if (info?.state == WorkInfo.State.SUCCEEDED && url != null) {
            ShareUiState.Completed(url, openBrowser)
        } else {
            ShareUiState.Finished
        }
    }

    private fun copyToCache(uri: Uri): File? {
        val dir = File(app.cacheDir, "shared").apply { mkdirs() }
        val file = File.createTempFile("share", ".bin", dir)
        return try {
            val input = app.contentResolver.openInputStream(uri) ?: error("No input stream for $uri")
            input.use { src -> file.outputStream().use { src.copyTo(it) } }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read $uri", e)
            file.delete()
            null
        }
    }

    private fun toast(resId: Int) = Toast.makeText(app, resId, Toast.LENGTH_LONG).show()

    private companion object {
        const val TAG = "ShareViewModel"
        /** Extra used by the legacy ChooserTargetService. */
        const val EXTRA_LEGACY_UUID = "uuid"
    }
}

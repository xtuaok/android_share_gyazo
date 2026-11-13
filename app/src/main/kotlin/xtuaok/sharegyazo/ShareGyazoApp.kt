package xtuaok.sharegyazo

import android.app.Application
import android.content.Context
import xtuaok.sharegyazo.data.ProfileRepository
import xtuaok.sharegyazo.data.SettingsRepository
import xtuaok.sharegyazo.share.ShareShortcuts
import xtuaok.sharegyazo.upload.Notifications
import java.io.File

class ShareGyazoApp : Application() {
    lateinit var profiles: ProfileRepository
        private set
    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Same file as PreferenceManager.getDefaultSharedPreferences() used by the legacy app.
        val prefs = getSharedPreferences("${packageName}_preferences", MODE_PRIVATE)
        settings = SettingsRepository(prefs)
        profiles = ProfileRepository(prefs) { ShareShortcuts.publish(this, it) }
        Notifications.createChannels(this)
        removeStaleTempFiles()
    }

    /**
     * Shared images are copied to the cache until their upload job ends. Files left behind by
     * a job that never ran (e.g. the process died first) are removed after a day; recent ones
     * may still belong to a job waiting for a network or a retry.
     */
    private fun removeStaleTempFiles() = Thread {
        val limit = System.currentTimeMillis() - STALE_TEMP_FILE_MS
        listOf(File(cacheDir, "shared"), cacheDir).forEach { dir ->
            dir.listFiles()
                ?.filter { it.isFile && it.lastModified() < limit && it.name.matches(TEMP_FILE) }
                ?.forEach { it.delete() }
        }
    }.start()

    companion object {
        const val PRIVACY_POLICY_URL = "https://app.sickin.dev/ShareGyazo/"
        private const val STALE_TEMP_FILE_MS = 24 * 60 * 60 * 1000L
        /** Names from File.createTempFile in ShareViewModel and ImageConverter. */
        private val TEMP_FILE = Regex("""(share\d+\.bin|upload\d+\.(png|jpg))""")
    }
}

val Context.app: ShareGyazoApp get() = applicationContext as ShareGyazoApp

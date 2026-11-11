package xtuaok.sharegyazo

import android.app.Application
import android.content.Context
import xtuaok.sharegyazo.data.ProfileRepository
import xtuaok.sharegyazo.data.SettingsRepository
import xtuaok.sharegyazo.share.ShareShortcuts
import xtuaok.sharegyazo.upload.Notifications

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
    }

    companion object {
        const val PRIVACY_POLICY_URL = "https://xtuaok.sickin.dev/ShareGyazo/"
    }
}

val Context.app: ShareGyazoApp get() = applicationContext as ShareGyazoApp

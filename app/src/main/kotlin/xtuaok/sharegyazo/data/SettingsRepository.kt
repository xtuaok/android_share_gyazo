package xtuaok.sharegyazo.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What tapping the "uploaded" notification does. Values match the legacy ListPreference. */
enum class TapAction(val value: String) {
    NONE("0"), OPEN("1"), SHARE("2"), COPY("3");

    companion object {
        fun of(value: String?): TapAction = entries.firstOrNull { it.value == value } ?: OPEN
    }
}

data class AppSettings(
    val copyUrl: Boolean = true,
    val openBrowser: Boolean = false,
    val shareText: Boolean = false,
    val tapAction: TapAction = TapAction.OPEN,
    val showNotification: Boolean = true,
    val autoCloseNotification: Boolean = false,
) {
    /** Whether the share screen must stay until the upload completes to launch an activity. */
    val needsForegroundAction: Boolean get() = openBrowser || shareText

    /** Opening a browser and showing the share sheet are mutually exclusive. */
    fun withOpenBrowser(enabled: Boolean) =
        copy(openBrowser = enabled, shareText = if (enabled) false else shareText)

    fun withShareText(enabled: Boolean) =
        copy(shareText = enabled, openBrowser = if (enabled) false else openBrowser)
}

class SettingsRepository(private val prefs: SharedPreferences) {
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit {
            putBoolean(KEY_COPY_URL, s.copyUrl)
            putBoolean(KEY_OPEN_BROWSER, s.openBrowser)
            putBoolean(KEY_SHARE_TEXT, s.shareText)
            putString(KEY_TAP_ACTION, s.tapAction.value)
            putBoolean(KEY_SHOW_NOTIFICATION, s.showNotification)
            putBoolean(KEY_AUTO_CLOSE_NOTIFICATION, s.autoCloseNotification)
        }
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        val open = prefs.getBoolean(KEY_OPEN_BROWSER, defaults.openBrowser)
        return AppSettings(
            copyUrl = prefs.getBoolean(KEY_COPY_URL, defaults.copyUrl),
            openBrowser = open,
            shareText = !open && prefs.getBoolean(KEY_SHARE_TEXT, defaults.shareText),
            tapAction = TapAction.of(prefs.getString(KEY_TAP_ACTION, defaults.tapAction.value)),
            showNotification = prefs.getBoolean(KEY_SHOW_NOTIFICATION, defaults.showNotification),
            autoCloseNotification = prefs.getBoolean(KEY_AUTO_CLOSE_NOTIFICATION, defaults.autoCloseNotification),
        )
    }

    companion object {
        private const val KEY_COPY_URL = "copy_gyazo_url"
        private const val KEY_OPEN_BROWSER = "open_url_browser"
        private const val KEY_SHARE_TEXT = "share_url_text"
        private const val KEY_TAP_ACTION = "notification_action"
        private const val KEY_SHOW_NOTIFICATION = "show_notification"
        private const val KEY_AUTO_CLOSE_NOTIFICATION = "auto_close_notification"
    }
}

package xtuaok.sharegyazo.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores server profiles in the same SharedPreferences keys as the legacy app.
 */
class ProfileRepository(
    private val prefs: SharedPreferences,
    private val onProfilesChanged: (List<Profile>) -> Unit = {},
) {
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _defaultUuid = MutableStateFlow(loadDefaultUuid())
    val defaultUuid: StateFlow<String> = _defaultUuid.asStateFlow()

    val lastUsedUuid: String get() = prefs.getString(KEY_LAST_USE, "").orEmpty()

    val defaultProfile: Profile? get() = get(_defaultUuid.value)

    fun get(uuid: String?): Profile? =
        uuid?.takeIf { it.isNotEmpty() }?.let { id -> _profiles.value.firstOrNull { it.uuid == id } }

    @Synchronized
    fun save(profile: Profile) = update { list ->
        if (list.any { it.uuid == profile.uuid }) {
            list.map { if (it.uuid == profile.uuid) profile else it }
        } else {
            list + profile
        }
    }

    /** @return false if [uuid] is the last remaining profile. */
    @Synchronized
    fun remove(uuid: String): Boolean {
        if (_profiles.value.size <= 1) return false
        update { list -> list.filterNot { it.uuid == uuid } }
        if (_defaultUuid.value == uuid) setDefault("")
        return true
    }

    fun setDefault(uuid: String) {
        _defaultUuid.value = uuid
        prefs.edit { putString(KEY_DEFAULT_PROFILE, uuid) }
    }

    @Synchronized
    fun markUsed(uuid: String) {
        update(notify = false) { list ->
            list.map { if (it.uuid == uuid) it.copy(useCount = it.useCount + 1) else it }
        }
        prefs.edit { putString(KEY_LAST_USE, uuid) }
    }

    @Synchronized
    fun updateGyazoId(uuid: String, gyazoId: String) = update(notify = false) { list ->
        list.map { if (it.uuid == uuid && it.gyazoId.isEmpty()) it.copy(gyazoId = gyazoId) else it }
    }

    private fun update(notify: Boolean = true, transform: (List<Profile>) -> List<Profile>) {
        val updated = transform(_profiles.value)
        _profiles.value = updated
        persist(updated)
        if (notify) onProfilesChanged(updated)
    }

    private fun persist(list: List<Profile>) {
        prefs.edit { putString(KEY_PROFILES, ProfileCodec.encodeList(list)) }
    }

    private fun load(): List<Profile> {
        val stored = prefs.getString(KEY_PROFILES, null)
            ?.takeIf { it.isNotEmpty() }
            ?.let(ProfileCodec::decodeList)
            .orEmpty()
        if (stored.isNotEmpty()) return stored
        return listOf(migrateLegacySettings()).also(::persist)
    }

    private fun loadDefaultUuid(): String {
        val uuid = prefs.getString(KEY_DEFAULT_PROFILE, "").orEmpty()
        return if (get(uuid) != null) uuid else ""
    }

    /** Settings from before profiles existed (v1.x). */
    private fun migrateLegacySettings(): Profile {
        val url = prefs.getString(LEGACY_CGI_URL, null) ?: return Profile.newGyazo()
        val format = prefs.getString(LEGACY_IMAGE_FORMAT, "0")?.toIntOrNull() ?: 0
        return Profile(
            name = "migrated",
            url = url,
            gyazoId = prefs.getString(LEGACY_GYAZO_ID, "").orEmpty(),
            format = if (format == 0) Profile.FORMAT_PNG else Profile.FORMAT_JPEG,
            quality = prefs.getString(LEGACY_IMAGE_QUALITY, "100")?.toIntOrNull() ?: 100,
        )
    }

    companion object {
        const val KEY_PROFILES = "profile_list"
        const val KEY_LAST_USE = "last_use_profile"
        const val KEY_DEFAULT_PROFILE = "default_profile"

        private const val LEGACY_CGI_URL = "gyazo_cgi_url"
        private const val LEGACY_GYAZO_ID = "gyazo_cgi_id"
        private const val LEGACY_IMAGE_FORMAT = "image_file_format"
        private const val LEGACY_IMAGE_QUALITY = "image_quality"
    }
}

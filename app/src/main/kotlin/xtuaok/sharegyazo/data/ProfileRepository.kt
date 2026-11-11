package xtuaok.sharegyazo.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xtuaok.sharegyazo.auth.OAuthTokens

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

    /**
     * Saves user edits. The destination and login tokens of built-in profiles cannot be
     * changed here (see [setTokens]).
     */
    @Synchronized
    fun save(profile: Profile) = update { list ->
        val existing = list.firstOrNull { it.uuid == profile.uuid }
        if (existing != null) {
            val merged = if (existing.isBuiltIn) {
                profile.copy(
                    kind = existing.kind,
                    url = existing.url,
                    bearerToken = existing.bearerToken,
                    refreshToken = existing.refreshToken,
                    tokenExpiresAt = existing.tokenExpiresAt,
                )
            } else {
                profile.copy(kind = ProfileKind.CUSTOM)
            }
            list.map { if (it.uuid == profile.uuid) merged else it }
        } else {
            list + profile.copy(kind = ProfileKind.CUSTOM)
        }
    }

    /** Stores (or clears, with null) the OAuth tokens of a built-in profile. */
    @Synchronized
    fun setTokens(kind: ProfileKind, tokens: OAuthTokens?) = update(notify = false) { list ->
        list.map {
            if (it.kind == kind) {
                it.copy(
                    bearerToken = tokens?.accessToken.orEmpty(),
                    refreshToken = tokens?.refreshToken.orEmpty(),
                    tokenExpiresAt = tokens?.expiresAt ?: 0,
                )
            } else {
                it
            }
        }
    }

    fun builtIn(kind: ProfileKind): Profile? = _profiles.value.firstOrNull { it.kind == kind }

    /** @return false if [uuid] is a built-in profile, which cannot be removed. */
    @Synchronized
    fun remove(uuid: String): Boolean {
        if (get(uuid)?.isBuiltIn != false) return false
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
        val base = stored.ifEmpty { listOfNotNull(migrateLegacySettings()) }
        val list = Profile.withBuiltIns(base)
        if (list != stored) persist(list)
        return list
    }

    private fun loadDefaultUuid(): String {
        val uuid = prefs.getString(KEY_DEFAULT_PROFILE, "").orEmpty()
        return if (get(uuid) != null) uuid else ""
    }

    /** Settings from before profiles existed (v1.x). */
    private fun migrateLegacySettings(): Profile? {
        val url = prefs.getString(LEGACY_CGI_URL, null) ?: return null
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

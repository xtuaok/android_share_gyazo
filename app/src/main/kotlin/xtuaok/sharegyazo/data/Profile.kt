package xtuaok.sharegyazo.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** Built-in services have a fixed destination; everything else is user defined. */
@Serializable
enum class ProfileKind {
    /** Any Gyazo-compatible upload.cgi server (legacy profiles). */
    @SerialName("custom") CUSTOM,

    /** gyazo.com official API, authenticated by OAuth login. */
    @SerialName("gyazo") GYAZO,

    /** imgur, anonymous upload with the app's Client-ID (or the user's access token). */
    @SerialName("imgur") IMGUR,
}

/**
 * Upload destination profile.
 *
 * The serial names match the field names Gson used in versions <= 2.x, so profiles
 * saved by the old app are read as-is after an update.
 */
@Serializable
data class Profile(
    @SerialName("mUUID") val uuid: String = UUID.randomUUID().toString(),
    @SerialName("mName") val name: String,
    @SerialName("mCgiUrl") val url: String,
    @SerialName("mGyazoId") val gyazoId: String = "",
    @SerialName("mFormat") val format: String = FORMAT_PNG,
    @SerialName("mImageQuality") val quality: Int = 100,
    @SerialName("mUseCount") val useCount: Long = 0,
    /** Sent as `Authorization: Bearer <token>` when not blank. For [ProfileKind.GYAZO], the OAuth token. */
    @SerialName("bearerToken") val bearerToken: String = "",
    @SerialName("kind") val kind: ProfileKind = ProfileKind.CUSTOM,
) {
    val isPng: Boolean get() = format == FORMAT_PNG

    /** Built-in profiles cannot be removed and their destination cannot be changed. */
    val isBuiltIn: Boolean get() = kind != ProfileKind.CUSTOM

    val isLoggedIn: Boolean get() = bearerToken.isNotBlank()

    companion object {
        const val FORMAT_PNG = "png"
        const val FORMAT_JPEG = "jpg"
        const val GYAZO_CGI_URL = "https://upload.gyazo.com/upload.cgi"
        const val GYAZO_API_URL = "https://upload.gyazo.com/api/upload"
        const val IMGUR_URL = "https://api.imgur.com/"

        fun newCustom() = Profile(name = "", url = "https://")

        fun builtIn(kind: ProfileKind): Profile = when (kind) {
            ProfileKind.GYAZO -> Profile(name = "Gyazo", url = GYAZO_API_URL, kind = kind)
            ProfileKind.IMGUR -> Profile(name = "imgur", url = IMGUR_URL, kind = kind)
            ProfileKind.CUSTOM -> error("Custom profiles are not built in")
        }

        /**
         * Makes sure each built-in profile exists exactly once, placed first.
         * A legacy imgur profile is converted in place so its UUID (default profile,
         * share shortcuts) keeps working.
         */
        fun withBuiltIns(profiles: List<Profile>): List<Profile> {
            val list = profiles.toMutableList()
            if (list.none { it.kind == ProfileKind.IMGUR }) {
                val legacy = list.indexOfFirst { it.kind == ProfileKind.CUSTOM && it.url.startsWith(IMGUR_URL) }
                if (legacy >= 0) {
                    list[legacy] = list[legacy].copy(kind = ProfileKind.IMGUR, url = IMGUR_URL)
                }
            }
            val builtIns = listOf(ProfileKind.GYAZO, ProfileKind.IMGUR).map { kind ->
                list.firstOrNull { it.kind == kind }?.copy(url = builtIn(kind).url) ?: builtIn(kind)
            }
            return builtIns + list.filter { it.kind == ProfileKind.CUSTOM }
        }

        fun isValidUrl(url: String): Boolean =
            url.startsWith("http://") || url.startsWith("https://")

        /** A token would be sent in clear text. */
        fun isInsecureForToken(url: String, token: String): Boolean =
            token.isNotBlank() && url.trim().startsWith("http://")
    }
}

object ProfileCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun encodeList(profiles: List<Profile>): String = json.encodeToString(profiles)

    fun decodeList(text: String): List<Profile> =
        runCatching { json.decodeFromString<List<Profile>>(text) }.getOrDefault(emptyList())

    fun encode(profile: Profile): String = json.encodeToString(profile)

    fun decodeOrNull(text: String): Profile? =
        runCatching { json.decodeFromString<Profile>(text) }.getOrNull()
}

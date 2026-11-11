package xtuaok.sharegyazo.data

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/** Built-in services have a fixed destination; everything else is user defined. */
@Serializable
enum class ProfileKind {
    /** Any Gyazo-compatible upload.cgi server (legacy profiles). */
    @SerialName("custom") CUSTOM,

    /** gyazo.com official API, authenticated by OAuth login. */
    @SerialName("gyazo") GYAZO,

    /** imgur API, authenticated by OAuth login (anonymous imgur is a custom profile). */
    @SerialName("imgur") IMGUR,
}

/** Starting points offered when the user creates a custom profile. Also a navigation argument. */
@Keep
enum class ProfileTemplate {
    /** Any Gyazo-compatible server; the user enters the URL. */
    BLANK,

    /** gyazo.com without login, through the legacy upload.cgi (as in versions <= 3.x). */
    GYAZO_ANONYMOUS,

    /** imgur without login, with the app's Client-ID (as in versions <= 3.x). */
    IMGUR_ANONYMOUS,
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
    /** Sent as `Authorization: Bearer <token>` when not blank. For built-in profiles, the OAuth access token. */
    @SerialName("bearerToken") val bearerToken: String = "",
    @SerialName("kind") val kind: ProfileKind = ProfileKind.CUSTOM,
    /** OAuth refresh token of built-in profiles (imgur). */
    @SerialName("refreshToken") val refreshToken: String = "",
    /** Epoch millis when [bearerToken] expires; 0 when it does not. */
    @SerialName("tokenExpiresAt") val tokenExpiresAt: Long = 0,
    /** Account name shown next to the login state, when the service returns one at login (imgur). */
    @SerialName("accountName") val accountName: String = "",
) {
    val isPng: Boolean get() = format == FORMAT_PNG

    /** Built-in profiles cannot be removed and their destination cannot be changed. */
    val isBuiltIn: Boolean get() = kind != ProfileKind.CUSTOM

    val isLoggedIn: Boolean get() = bearerToken.isNotBlank()

    /** Copy without credentials, e.g. to hand to a background job; read them back with [withCredentialsOf]. */
    fun withoutCredentials(): Profile =
        copy(bearerToken = "", refreshToken = "", tokenExpiresAt = 0, accountName = "")

    fun withCredentialsOf(other: Profile): Profile = copy(
        bearerToken = other.bearerToken,
        refreshToken = other.refreshToken,
        tokenExpiresAt = other.tokenExpiresAt,
        accountName = other.accountName,
    )

    /** Custom profile pointing at imgur: anonymous upload with the app's Client-ID. */
    val isImgurAnonymous: Boolean get() = kind == ProfileKind.CUSTOM && url.startsWith(IMGUR_URL)

    companion object {
        const val FORMAT_PNG = "png"
        const val FORMAT_JPEG = "jpg"
        const val GYAZO_CGI_URL = "https://upload.gyazo.com/upload.cgi"
        const val GYAZO_API_URL = "https://upload.gyazo.com/api/upload"
        const val IMGUR_URL = "https://api.imgur.com/"
        const val IMGUR_API_URL = "https://api.imgur.com/3/image"

        fun newCustom(template: ProfileTemplate = ProfileTemplate.BLANK, name: String = "") = when (template) {
            ProfileTemplate.BLANK -> Profile(name = name, url = "https://")
            ProfileTemplate.GYAZO_ANONYMOUS -> Profile(name = name, url = GYAZO_CGI_URL)
            ProfileTemplate.IMGUR_ANONYMOUS -> Profile(name = name, url = IMGUR_URL)
        }

        fun builtIn(kind: ProfileKind): Profile = when (kind) {
            ProfileKind.GYAZO -> Profile(name = "Gyazo", url = GYAZO_API_URL, kind = kind)
            ProfileKind.IMGUR -> Profile(name = "imgur", url = IMGUR_API_URL, kind = kind)
            ProfileKind.CUSTOM -> error("Custom profiles are not built in")
        }

        /**
         * Makes sure each built-in profile exists exactly once, placed first. Legacy
         * profiles (Gyazo upload.cgi, anonymous imgur) stay custom and keep working as before.
         */
        fun withBuiltIns(profiles: List<Profile>): List<Profile> {
            val builtIns = listOf(ProfileKind.GYAZO, ProfileKind.IMGUR).map { kind ->
                profiles.firstOrNull { it.kind == kind }?.copy(url = builtIn(kind).url) ?: builtIn(kind)
            }
            return builtIns + profiles.filter { it.kind == ProfileKind.CUSTOM }
        }

        /** An http(s) URL with a host. */
        fun isValidUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
            return trimmed.toHttpUrlOrNull()?.host?.isNotEmpty() == true
        }

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

package xtuaok.sharegyazo.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

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
) {
    val isPng: Boolean get() = format == FORMAT_PNG

    companion object {
        const val FORMAT_PNG = "png"
        const val FORMAT_JPEG = "jpg"
        const val GYAZO_URL = "https://upload.gyazo.com/upload.cgi"
        const val IMGUR_URL = "https://api.imgur.com/"

        fun newGyazo(name: String = "Gyazo") = Profile(name = name, url = GYAZO_URL)

        /** Typing a well-known service name as the profile name fills in its URL. */
        fun presetUrlFor(name: String): String? = when (name.trim().lowercase()) {
            "gyazo" -> GYAZO_URL
            "imgur" -> IMGUR_URL
            else -> null
        }

        fun isValidUrl(url: String): Boolean =
            url.startsWith("http://") || url.startsWith("https://")
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

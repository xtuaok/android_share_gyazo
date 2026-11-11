package xtuaok.sharegyazo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCodecTest {
    /** JSON as written by Gson in ShareGyazo 2.x. */
    private val legacyJson = """
        [{"mUUID":"8d3c0f6e-1111-2222-3333-444455556666","mName":"Gyazo","mCgiUrl":"https://upload.gyazo.com/upload.cgi",
          "mGyazoId":"abc123","mFormat":"jpg","mImageQuality":80,"mUseCount":12},
         {"mUUID":"u2","mName":"imgur","mCgiUrl":"https://api.imgur.com/","mFormat":"png","mImageQuality":100,"mUseCount":0}]
    """.trimIndent()

    @Test
    fun decodesLegacyGsonFormat() {
        val profiles = ProfileCodec.decodeList(legacyJson)
        assertEquals(2, profiles.size)
        assertEquals(
            Profile("8d3c0f6e-1111-2222-3333-444455556666", "Gyazo", Profile.GYAZO_URL, "abc123", "jpg", 80, 12),
            profiles[0],
        )
        // Gson omits null fields; missing mGyazoId falls back to empty.
        assertEquals("", profiles[1].gyazoId)
    }

    @Test
    fun nullFieldsFallBackToDefaults() {
        val profiles = ProfileCodec.decodeList("""[{"mUUID":"x","mName":"a","mCgiUrl":"http://e","mGyazoId":null}]""")
        assertEquals("", profiles.single().gyazoId)
        assertEquals(Profile.FORMAT_PNG, profiles.single().format)
    }

    @Test
    fun roundTripKeepsLegacyFieldNames() {
        val encoded = ProfileCodec.encodeList(ProfileCodec.decodeList(legacyJson))
        assertTrue(encoded.contains("\"mCgiUrl\""))
        assertEquals(ProfileCodec.decodeList(legacyJson), ProfileCodec.decodeList(encoded))
    }

    @Test
    fun brokenJsonGivesEmptyList() {
        assertTrue(ProfileCodec.decodeList("not json").isEmpty())
    }

    @Test
    fun presetUrls() {
        assertEquals(Profile.IMGUR_URL, Profile.presetUrlFor(" Imgur "))
        assertEquals(Profile.GYAZO_URL, Profile.presetUrlFor("GYAZO"))
        assertEquals(null, Profile.presetUrlFor("my server"))
    }
}

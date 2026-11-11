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
            Profile("8d3c0f6e-1111-2222-3333-444455556666", "Gyazo", Profile.GYAZO_CGI_URL, "abc123", "jpg", 80, 12),
            profiles[0],
        )
        // Gson omits null fields; missing mGyazoId falls back to empty.
        assertEquals("", profiles[1].gyazoId)
        // Profiles saved before bearer auth existed have no token.
        assertEquals("", profiles[0].bearerToken)
    }

    @Test
    fun bearerTokenRoundTrip() {
        val profile = Profile(name = "own", url = "https://example.com/upload", bearerToken = "tok")
        assertEquals("tok", ProfileCodec.decodeOrNull(ProfileCodec.encode(profile))!!.bearerToken)
    }

    @Test
    fun tokenOverHttpIsInsecure() {
        assertTrue(Profile.isInsecureForToken("http://example.com/upload", "tok"))
        assertEquals(false, Profile.isInsecureForToken("https://example.com/upload", "tok"))
        assertEquals(false, Profile.isInsecureForToken("http://example.com/upload", ""))
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
    fun builtInsAreAddedFirstForLegacyData() {
        val custom = Profile(name = "own", url = "https://example.com/upload.cgi")
        val list = Profile.withBuiltIns(listOf(custom))
        assertEquals(listOf(ProfileKind.GYAZO, ProfileKind.IMGUR, ProfileKind.CUSTOM), list.map { it.kind })
        assertEquals(Profile.GYAZO_API_URL, list[0].url)
        assertEquals(custom, list[2])
    }

    @Test
    fun legacyImgurProfileBecomesBuiltInKeepingUuid() {
        val legacy = Profile(uuid = "u2", name = "my imgur", url = "https://api.imgur.com/", useCount = 3)
        val list = Profile.withBuiltIns(listOf(legacy))
        assertEquals(2, list.size)
        val imgur = list.single { it.kind == ProfileKind.IMGUR }
        assertEquals("u2", imgur.uuid)
        assertEquals("my imgur", imgur.name)
        assertEquals(3, imgur.useCount)
    }

    @Test
    fun builtInsAreNotDuplicated() {
        val once = Profile.withBuiltIns(emptyList())
        assertEquals(once, Profile.withBuiltIns(once))
        assertEquals(2, once.size)
    }

    @Test
    fun kindSurvivesRoundTrip() {
        val gyazo = Profile.builtIn(ProfileKind.GYAZO).copy(bearerToken = "t")
        assertEquals(gyazo, ProfileCodec.decodeOrNull(ProfileCodec.encode(gyazo)))
        // Legacy JSON has no "kind": treated as a custom profile.
        assertEquals(ProfileKind.CUSTOM, ProfileCodec.decodeList(legacyJson)[0].kind)
    }
}

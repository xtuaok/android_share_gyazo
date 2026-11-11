package xtuaok.sharegyazo.upload

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xtuaok.sharegyazo.data.Profile
import xtuaok.sharegyazo.data.ProfileKind
import java.io.File

class UploadProtocolTest {
    private val gyazo = UploadEndpoint("https://upload.gyazo.com/upload.cgi", UploadEndpoint.Type.CGI)

    @Test
    fun gyazoPageUrlBecomesImageUrl() {
        assertEquals(
            "https://i.gyazo.com/0123abcd.png",
            UploadProtocol.resolveImageUrl(gyazo, "https://gyazo.com/0123abcd\n", "png"),
        )
    }

    @Test
    fun customServerResponseIsReturnedAsIs() {
        val custom = UploadEndpoint("https://example.com/upload.cgi", UploadEndpoint.Type.CGI)
        assertEquals(
            "https://example.com/i/42.png",
            UploadProtocol.resolveImageUrl(custom, "https://example.com/i/42.png", "png"),
        )
    }

    @Test
    fun nonUrlResponseIsRejected() {
        assertNull(UploadProtocol.resolveImageUrl(gyazo, "<html>error</html>", "png"))
        assertNull(UploadProtocol.resolveImageUrl(gyazo, "", "png"))
    }

    @Test
    fun imgurEndpointAndResponse() {
        val imgur = UploadEndpoint.forProfile(Profile(name = "anon", url = "https://api.imgur.com/"))
        assertEquals(UploadEndpoint.Type.IMGUR, imgur.type)
        assertEquals(UploadEndpoint.IMGUR_UPLOAD, imgur.url)
        assertEquals(
            "https://i.imgur.com/abc.png",
            UploadProtocol.resolveImageUrl(imgur, """{"data":{"link":"https://i.imgur.com/abc.png"},"success":true}""", "png"),
        )
        assertNull(UploadProtocol.resolveImageUrl(imgur, """{"data":{"error":"x"}}""", "png"))
    }

    @Test
    fun endpointForEachKind() {
        assertEquals(
            UploadEndpoint(Profile.GYAZO_API_URL, UploadEndpoint.Type.GYAZO_API),
            UploadEndpoint.forProfile(Profile.builtIn(ProfileKind.GYAZO)),
        )
        assertEquals(
            UploadEndpoint(Profile.IMGUR_API_URL, UploadEndpoint.Type.IMGUR),
            UploadEndpoint.forProfile(Profile.builtIn(ProfileKind.IMGUR)),
        )
        assertEquals(
            UploadEndpoint("https://example.com/up.cgi", UploadEndpoint.Type.CGI),
            UploadEndpoint.forProfile(Profile(name = "own", url = "https://example.com/up.cgi")),
        )
    }

    @Test
    fun gyazoApiResponse() {
        val api = UploadEndpoint.forProfile(Profile.builtIn(ProfileKind.GYAZO))
        val body = """{"image_id":"8980","permalink_url":"https://gyazo.com/8980","url":"https://i.gyazo.com/8980.png","type":"png"}"""
        assertEquals("https://i.gyazo.com/8980.png", UploadProtocol.resolveImageUrl(api, body, "png"))
        assertEquals(
            "https://gyazo.com/8980",
            UploadProtocol.resolveImageUrl(api, """{"permalink_url":"https://gyazo.com/8980"}""", "png"),
        )
        assertNull(UploadProtocol.resolveImageUrl(api, """{"message":"Unauthorized"}""", "png"))
    }

    @Test
    fun errorSummaryForNotifications() {
        assertEquals("No file uploaded", UploadProtocol.errorSummary("No file uploaded\n"))
        assertEquals("invalid_grant", UploadProtocol.errorSummary("""{"error":"invalid_grant"}"""))
        assertEquals("Unauthorized", UploadProtocol.errorSummary("""{"message":"Unauthorized"}"""))
        assertEquals("Bad Request", UploadProtocol.errorSummary("""{"data":{"error":"Bad Request"},"status":400}"""))
        assertEquals("413 Request Entity Too Large", UploadProtocol.errorSummary("<html><h1>413 Request Entity Too Large</h1></html>"))
        assertEquals("", UploadProtocol.errorSummary(""))
        val long = UploadProtocol.errorSummary("x".repeat(500))
        assertEquals(120, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun targetSizeFitsWithinMax() {
        assertEquals(1920 to 1080, UploadProtocol.targetSize(3840, 2160))
        assertEquals(1080 to 1920, UploadProtocol.targetSize(1440, 2560))
        assertEquals(800 to 600, UploadProtocol.targetSize(800, 600))
    }
}

class UploaderTest {
    private lateinit var server: MockWebServer
    private lateinit var image: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        image = File.createTempFile("test", ".png").apply { writeBytes(ByteArray(64 * 1024) { it.toByte() }) }
    }

    @After
    fun tearDown() {
        server.close()
        image.delete()
    }

    @Test
    fun postsGyazoMultipartAndReadsGyazoId() {
        server.enqueue(
            MockResponse.Builder()
                .body("https://gyazo.com/abc")
                .addHeader("X-Gyazo-Id", "new-id")
                .build(),
        )
        val progress = mutableListOf<Int>()
        val endpoint = UploadEndpoint(server.url("/upload.cgi").toString(), UploadEndpoint.Type.CGI)

        val response = Uploader().upload(endpoint, image, "image/png", "my-id") { progress += it }

        assertTrue(response.isSuccessful)
        assertEquals("https://gyazo.com/abc", response.body)
        assertEquals("new-id", response.gyazoId)
        assertEquals(100, progress.last())

        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("name=\"id\""))
        assertTrue(body.contains("my-id"))
        assertTrue(body.contains("name=\"imagedata\""))
    }

    @Test
    fun imgurSendsClientIdHeader() {
        server.enqueue(MockResponse.Builder().body("""{"data":{"link":"https://i.imgur.com/x.jpg"}}""").build())
        val endpoint = UploadEndpoint(server.url("/3/upload.json").toString(), UploadEndpoint.Type.IMGUR)

        Uploader(imgurClientId = "cid").upload(endpoint, image, "image/jpeg", "")

        val request = server.takeRequest()
        assertEquals("Client-ID cid", request.headers["Authorization"])
        assertTrue(request.body!!.utf8().contains("name=\"image\""))
    }

    @Test
    fun bearerTokenIsSentAsAuthorizationHeader() {
        server.enqueue(MockResponse.Builder().body("https://example.com/i/1.png").build())
        val endpoint = UploadEndpoint(server.url("/upload.cgi").toString(), UploadEndpoint.Type.CGI)

        Uploader().upload(endpoint, image, "image/png", "", bearerToken = " secret-token ")

        assertEquals("Bearer secret-token", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun noAuthorizationHeaderWithoutToken() {
        server.enqueue(MockResponse.Builder().body("https://example.com/i/1.png").build())
        val endpoint = UploadEndpoint(server.url("/upload.cgi").toString(), UploadEndpoint.Type.CGI)

        Uploader().upload(endpoint, image, "image/png", "")

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun bearerTokenOverridesImgurClientId() {
        server.enqueue(MockResponse.Builder().body("""{"data":{"link":"https://i.imgur.com/x.jpg"}}""").build())
        val endpoint = UploadEndpoint(server.url("/3/upload.json").toString(), UploadEndpoint.Type.IMGUR)

        Uploader(imgurClientId = "cid").upload(endpoint, image, "image/jpeg", "", bearerToken = "oauth")

        assertEquals("Bearer oauth", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun gyazoApiUsesBearerAndImagedataWithFilename() {
        server.enqueue(MockResponse.Builder().body("""{"url":"https://i.gyazo.com/x.png"}""").build())
        val endpoint = UploadEndpoint(server.url("/api/upload").toString(), UploadEndpoint.Type.GYAZO_API)

        Uploader().upload(endpoint, image, "image/png", "", bearerToken = "gyazo-token")

        val request = server.takeRequest()
        assertEquals("Bearer gyazo-token", request.headers["Authorization"])
        val body = request.body!!.utf8()
        assertTrue(body.contains("name=\"imagedata\"; filename=\"image.png\""))
        assertTrue(!body.contains("name=\"id\""))
    }

    @Test
    fun serverErrorIsNotSuccessful() {
        server.enqueue(MockResponse.Builder().code(500).body("oops").build())
        val endpoint = UploadEndpoint(server.url("/").toString(), UploadEndpoint.Type.CGI)
        val response = Uploader().upload(endpoint, image, "image/png", "")
        assertEquals(500, response.code)
        assertTrue(!response.isSuccessful)
    }
}

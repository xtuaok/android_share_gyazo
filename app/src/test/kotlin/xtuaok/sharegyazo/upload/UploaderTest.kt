package xtuaok.sharegyazo.upload

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class UploadProtocolTest {
    private val gyazo = UploadEndpoint.forProfileUrl("https://upload.gyazo.com/upload.cgi")

    @Test
    fun gyazoPageUrlBecomesImageUrl() {
        assertEquals(
            "https://i.gyazo.com/0123abcd.png",
            UploadProtocol.resolveImageUrl(gyazo, "https://gyazo.com/0123abcd\n", "png"),
        )
    }

    @Test
    fun customServerResponseIsReturnedAsIs() {
        val custom = UploadEndpoint.forProfileUrl("https://example.com/upload.cgi")
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
        val imgur = UploadEndpoint.forProfileUrl("https://api.imgur.com/")
        assertTrue(imgur.isImgur)
        assertEquals(UploadEndpoint.IMGUR_UPLOAD, imgur.url)
        assertEquals(
            "https://i.imgur.com/abc.png",
            UploadProtocol.resolveImageUrl(imgur, """{"data":{"link":"https://i.imgur.com/abc.png"},"success":true}""", "png"),
        )
        assertNull(UploadProtocol.resolveImageUrl(imgur, """{"data":{"error":"x"}}""", "png"))
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
        val endpoint = UploadEndpoint(server.url("/upload.cgi").toString(), isImgur = false)

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
        val endpoint = UploadEndpoint(server.url("/3/upload.json").toString(), isImgur = true)

        Uploader(imgurClientId = "cid").upload(endpoint, image, "image/jpeg", "")

        val request = server.takeRequest()
        assertEquals("Client-ID cid", request.headers["Authorization"])
        assertTrue(request.body!!.utf8().contains("name=\"image\""))
    }

    @Test
    fun serverErrorIsNotSuccessful() {
        server.enqueue(MockResponse.Builder().code(500).body("oops").build())
        val endpoint = UploadEndpoint(server.url("/").toString(), isImgur = false)
        val response = Uploader().upload(endpoint, image, "image/png", "")
        assertEquals(500, response.code)
        assertTrue(!response.isSuccessful)
    }
}

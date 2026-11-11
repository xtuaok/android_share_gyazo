package xtuaok.sharegyazo.upload

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToInt

/** Where and how an image is posted. */
data class UploadEndpoint(val url: String, val isImgur: Boolean) {
    companion object {
        const val IMGUR_API = "https://api.imgur.com/"
        const val IMGUR_UPLOAD = "https://api.imgur.com/3/upload.json"

        fun forProfileUrl(url: String): UploadEndpoint =
            if (url.startsWith(IMGUR_API)) UploadEndpoint(IMGUR_UPLOAD, isImgur = true)
            else UploadEndpoint(url, isImgur = false)
    }
}

data class UploadResponse(val code: Int, val body: String, val gyazoId: String?) {
    val isSuccessful: Boolean get() = code in 200..299
}

object UploadProtocol {
    const val MAX_IMAGE_PIXEL = 1920

    /** Size that fits within [max] x [max] while keeping aspect ratio. Never upscales. */
    fun targetSize(width: Int, height: Int, max: Int = MAX_IMAGE_PIXEL): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return width to height
        val scale = min(1.0, min(max.toDouble() / width, max.toDouble() / height))
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    /**
     * Extracts the image URL from a server response, or null if it is not a URL.
     * For gyazo.com, the page URL is converted to the direct image URL.
     */
    fun resolveImageUrl(endpoint: UploadEndpoint, body: String, format: String): String? {
        val text = if (endpoint.isImgur) {
            runCatching {
                Json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject["link"]!!.jsonPrimitive.content
            }.getOrNull()
        } else {
            body
        }?.trim()
        if (text.isNullOrEmpty() || !text.startsWith("http")) return null

        if (endpoint.url.contains("://upload.gyazo.com/") && text.startsWith(GYAZO_PAGE)) {
            val hash = text.removePrefix(GYAZO_PAGE).replace(Regex("[\r\n]"), "")
            return "https://i.gyazo.com/$hash.$format"
        }
        return text
    }

    private const val GYAZO_PAGE = "https://gyazo.com/"
}

class Uploader(
    private val client: OkHttpClient = defaultClient,
    private val imgurClientId: String = IMGUR_CLIENT_ID,
) {
    /**
     * Posts [file] synchronously.
     * @param onProgress called with 0..100 as the request body is written.
     */
    @Throws(IOException::class)
    fun upload(
        endpoint: UploadEndpoint,
        file: File,
        mimeType: String,
        gyazoId: String,
        onProgress: (Int) -> Unit = {},
    ): UploadResponse {
        val ext = if (mimeType == "image/png") "png" else "jpg"
        val imageBody = file.asRequestBody(mimeType.toMediaType())
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        val request = Request.Builder().url(endpoint.url)
        if (endpoint.isImgur) {
            builder.addFormDataPart("image", "image.$ext", imageBody)
            request.header("Authorization", "Client-ID $imgurClientId")
        } else {
            builder.addFormDataPart("id", gyazoId)
            builder.addFormDataPart("imagedata", "gyazo.com", imageBody)
        }
        val body = ProgressRequestBody(builder.build(), onProgress)

        client.newCall(request.post(body).build()).execute().use { response ->
            return UploadResponse(
                code = response.code,
                body = response.body.string(),
                gyazoId = response.header("X-Gyazo-Id"),
            )
        }
    }

    companion object {
        private const val IMGUR_CLIENT_ID = "de242da82e84888"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}

/** Reports how much of [delegate] has been written, as a percentage. */
private class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Int) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        var lastPercent = -1
        val counting = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                if (total > 0) {
                    val percent = (written * 100 / total).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent)
                    }
                }
            }
        }.buffer()
        delegate.writeTo(counting)
        counting.flush()
    }
}

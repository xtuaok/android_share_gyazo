package xtuaok.sharegyazo.upload

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import xtuaok.sharegyazo.data.Profile
import java.io.File
import java.io.IOException

class ConvertedImage(val file: File, val mimeType: String, val thumbnail: Bitmap?)

/**
 * Re-encodes an image for upload: applies EXIF orientation (done by ImageDecoder),
 * shrinks it to fit within 1920px and encodes it in the profile's format.
 */
object ImageConverter {
    private const val THUMBNAIL_PIXEL = 512

    @Throws(IOException::class)
    fun convert(source: File, profile: Profile, outDir: File): ConvertedImage {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            val (w, h) = UploadProtocol.targetSize(info.size.width, info.size.height)
            decoder.setTargetSize(w, h)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            val (format, mime) =
                if (profile.isPng) Bitmap.CompressFormat.PNG to "image/png"
                else Bitmap.CompressFormat.JPEG to "image/jpeg"
            // JPEG has no alpha: transparent pixels would come out black, so put them on white.
            val encoded = if (!profile.isPng && bitmap.hasAlpha()) onWhite(bitmap) else bitmap
            val out = File.createTempFile("upload", if (profile.isPng) ".png" else ".jpg", outDir)
            out.outputStream().buffered().use { stream ->
                val ok = encoded.compress(format, profile.quality.coerceIn(0, 100), stream)
                if (encoded !== bitmap) encoded.recycle()
                if (!ok) {
                    out.delete()
                    throw IOException("Failed to encode image")
                }
            }
            val (tw, th) = UploadProtocol.targetSize(bitmap.width, bitmap.height, THUMBNAIL_PIXEL)
            return ConvertedImage(out, mime, bitmap.scale(tw, th))
        } finally {
            bitmap.recycle()
        }
    }

    private fun onWhite(source: Bitmap): Bitmap {
        val result = createBitmap(source.width, source.height)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        return result
    }
}

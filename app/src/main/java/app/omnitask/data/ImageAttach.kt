package app.omnitask.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import app.omnitask.model.tr
import java.io.ByteArrayOutputStream
import kotlinx.datetime.LocalDateTime
import java.time.format.DateTimeFormatter
import app.omnitask.time.*

/**
 * Turns a picked photo into WebP for the vault. Re-encoding from pixels also drops EXIF, so GPS and
 * camera data never reach the vault.
 */
object ImageAttach {

    const val MAX_SIDE = 2048
    const val MAX_BYTES = 1_000_000
    private const val QUALITY = 80

    /** Both encodings, so the user can choose when the full-size one is big. */
    class Prepared(
        val originalBytes: Long,
        val width: Int,
        val height: Int,
        val full: ByteArray,
        val reduced: ByteArray?,
    ) {
        /** Ask only when shrinking would actually help. */
        val needsChoice get() = reduced != null && (full.size > MAX_BYTES || maxOf(width, height) > MAX_SIDE)
    }

    fun prepare(context: Context, uri: Uri): Prepared {
        val original = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw java.io.IOException(tr("อ่านรูปไม่ได้", "Cannot read the image"))
        val bitmap = decode(context, uri, original)
        val full = encode(bitmap)
        val longSide = maxOf(bitmap.width, bitmap.height)
        val reduced = if (longSide > MAX_SIDE || full.size > MAX_BYTES) {
            val scale = minOf(1f, MAX_SIDE.toFloat() / longSide)
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            encode(Bitmap.createScaledBitmap(bitmap, w, h, true))
        } else {
            null
        }
        return Prepared(original.size.toLong(), bitmap.width, bitmap.height, full, reduced)
    }

    fun fileName(now: LocalDateTime = LocalDateTime.now()): String =
        "Omni-" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) + ".webp"

    fun sizeLabel(bytes: Long): String =
        if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1_000_000.0) else "${(bytes / 1000).coerceAtLeast(1)} KB"

    /** A small bitmap for thumbnails, decoded without loading the full image. */
    fun thumbnail(bytes: ByteArray, target: Int = 240): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    // ImageDecoder applies the photo's rotation; older phones fall back to the raw pixels.
    private fun decode(context: Context, uri: Uri, bytes: ByteArray): Bitmap =
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw java.io.IOException(tr("ไฟล์นี้ไม่ใช่รูป", "This file is not an image"))
        }

    @Suppress("DEPRECATION")
    private fun encode(bitmap: Bitmap): ByteArray {
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(format, QUALITY, out)
            out.toByteArray()
        }
    }
}

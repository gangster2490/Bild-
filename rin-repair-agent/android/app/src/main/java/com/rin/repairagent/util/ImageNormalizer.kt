package com.rin.repairagent.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes gallery/camera/ZIP images (JPEG/PNG/WebP/HEIC when supported)
 * and writes a normalized JPEG for reliable AI upload and thumbnails.
 */
object ImageNormalizer {

    private const val MAX_SIDE = 2048
    private const val JPEG_QUALITY = 90

    fun saveUriAsJpeg(context: Context, uri: Uri, dest: File): File {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось открыть изображение")
        return saveBytesAsJpeg(bytes, dest)
    }

    fun saveFileAsJpeg(source: File, dest: File): File {
        require(source.exists() && source.length() > 0L) { "Файл фотографии пустой или отсутствует" }
        return saveBytesAsJpeg(source.readBytes(), dest)
    }

    fun saveStreamAsJpeg(input: InputStream, dest: File): File {
        return saveBytesAsJpeg(input.readBytes(), dest)
    }

    fun saveBytesAsJpeg(bytes: ByteArray, dest: File): File {
        require(bytes.isNotEmpty()) { "Пустые данные изображения" }
        val bitmap = decodeBitmap(bytes)
            ?: error("Не удалось декодировать изображение. Поддерживаются JPEG, PNG, WebP.")
        dest.parentFile?.mkdirs()
        FileOutputStream(dest).use { out ->
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (!ok) error("Не удалось сохранить JPEG")
        }
        if (!bitmap.isRecycled) bitmap.recycle()
        require(dest.exists() && dest.length() > 0L) { "Сохранённый файл фотографии пустой" }
        return dest
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            // Fallback for HEIC / exotic formats on API 28+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return decodeWithImageDecoder(bytes)?.let { applyExif(it, bytes) }
            }
            return null
        }

        val sample = calculateInSampleSize(
            Size(bounds.outWidth, bounds.outHeight),
            MAX_SIDE
        )
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeWithImageDecoder(bytes)
            } else {
                null
            }
            ?: return null

        val oriented = applyExif(decoded, bytes)
        return scaleDownIfNeeded(oriented, MAX_SIDE)
    }

    private fun decodeWithImageDecoder(bytes: ByteArray): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val source = android.graphics.ImageDecoder.createSource(
                java.nio.ByteBuffer.wrap(bytes)
            )
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyExif(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap && !bitmap.isRecycled) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxSide: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / largest.toFloat()
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return scaled
    }

    private fun calculateInSampleSize(size: Size, maxSide: Int): Int {
        var inSampleSize = 1
        var halfW = size.width / 2
        var halfH = size.height / 2
        while (halfW / inSampleSize >= maxSide || halfH / inSampleSize >= maxSide) {
            inSampleSize *= 2
        }
        // Also reduce if either dimension alone is huge
        while (size.width / inSampleSize > maxSide * 2 || size.height / inSampleSize > maxSide * 2) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }
}

package com.rin.repairagent.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes gallery/camera/ZIP images and writes a normalized JPEG.
 * Always prefers file-based decode (stable for EXIF + ImageDecoder).
 */
object ImageNormalizer {

    private const val MAX_SIDE = 2048
    private const val JPEG_QUALITY = 90

    fun saveUriAsJpeg(context: Context, uri: Uri, dest: File): File {
        val cache = UriIO.copyUriToCache(context, uri, "img", ".bin")
        return try {
            saveFileAsJpeg(cache, dest)
        } finally {
            cache.delete()
        }
    }

    fun saveFileAsJpeg(source: File, dest: File): File {
        require(source.exists() && source.length() > 0L) { "Файл фотографии пустой или отсутствует" }

        // Fast path: already a valid JPEG small enough — still re-encode for orientation/size consistency
        val bitmap = decodeBitmapFromFile(source)
            ?: error("Не удалось декодировать изображение. Поддерживаются JPEG, PNG, WebP, HEIC.")

        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                if (!ok) error("Не удалось сохранить JPEG")
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            if (tmp.exists() && tmp.absolutePath != dest.absolutePath) tmp.delete()
        }
        require(dest.exists() && dest.length() > 0L) { "Сохранённый файл фотографии пустой" }
        return dest
    }

    fun saveBytesAsJpeg(bytes: ByteArray, dest: File): File {
        require(bytes.isNotEmpty()) { "Пустые данные изображения" }
        val tmp = File(dest.parentFile ?: File("."), "bytes_${System.nanoTime()}.bin")
        try {
            tmp.writeBytes(bytes)
            return saveFileAsJpeg(tmp, dest)
        } finally {
            tmp.delete()
        }
    }

    private fun decodeBitmapFromFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        var decoded: Bitmap? = null
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val sample = calculateInSampleSize(Size(bounds.outWidth, bounds.outHeight), MAX_SIDE)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            decoded = BitmapFactory.decodeFile(file.absolutePath, opts)
        }

        if (decoded == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decoded = decodeWithImageDecoder(file)
        }
        if (decoded == null) return null

        val oriented = applyExif(decoded, file)
        return scaleDownIfNeeded(oriented, MAX_SIDE)
    }

    private fun decodeWithImageDecoder(file: File): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val source = android.graphics.ImageDecoder.createSource(file)
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.isMutableRequired = true
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val largest = max(info.size.width, info.size.height)
                if (largest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / largest.toFloat()
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyExif(bitmap: Bitmap, file: File): Bitmap {
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
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
        while ((halfW / inSampleSize) >= maxSide || (halfH / inSampleSize) >= maxSide) {
            inSampleSize *= 2
        }
        while (size.width / inSampleSize > maxSide * 2 || size.height / inSampleSize > maxSide * 2) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }
}

package de.spardirekt.agents.pro.generation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

object ImageEncoder {

    fun toDataUrl(
        context: Context,
        uriString: String,
        localPath: String? = null,
        maxSide: Int = 1280,
        quality: Int = 82
    ): String? {
        val candidates = buildList {
            if (!localPath.isNullOrBlank()) add(Uri.fromFile(File(localPath)).toString())
            if (uriString.isNotBlank()) add(uriString)
        }
        candidates.forEach { candidate ->
            encode(context, candidate, maxSide, quality)?.let { return it }
        }
        return null
    }

    private fun encode(context: Context, uriString: String, maxSide: Int, quality: Int): String? {
        return runCatching {
            val uri = Uri.parse(uriString)
            val stream = context.contentResolver.openInputStream(uri)
                ?: if (uriString.startsWith("/")) File(uriString).inputStream() else null
            stream?.use { input ->
                val original = BitmapFactory.decodeStream(input) ?: return null
                val scaled = scale(original, maxSide)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                if (scaled !== original) original.recycle()
                scaled.recycle()
                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                "data:image/jpeg;base64,$b64"
            }
        }.getOrNull()
    }

    private fun scale(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = maxOf(w, h)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }
}

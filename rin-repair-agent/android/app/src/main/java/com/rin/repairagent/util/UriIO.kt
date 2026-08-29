package com.rin.repairagent.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Reliable content:// / file:// URI reading for SAF, Photo Picker, Drive, etc.
 * Always copies into app-controlled storage before parsing (ZIP/images).
 */
object UriIO {

    fun displayName(context: Context, uri: Uri, fallback: String = "file"): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun tryTakeReadPermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Temporary grants from Photo Picker / some SAF providers are enough to copy once.
        } catch (_: Exception) {
            // ignore
        }
    }

    /**
     * Copy any readable URI into [dest]. Uses FileDescriptor when available
     * (most reliable for MediaStore / Downloads / Drive).
     */
    fun copyUriToFile(context: Context, uri: Uri, dest: File): File {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()

        val scheme = uri.scheme?.lowercase()
        if (scheme == "file") {
            val path = uri.path ?: error("Путь file:// URI пустой")
            val source = File(path)
            require(source.exists() && source.length() > 0L) { "Локальный файл недоступен" }
            source.copyTo(dest, overwrite = true)
            require(dest.length() > 0L) { "Скопированный файл пустой" }
            return dest
        }

        val resolver = context.contentResolver
        var copied = false
        var lastError: Exception? = null

        // 1) FileDescriptor — preferred for MediaStore / Downloads
        try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                copied = dest.exists() && dest.length() > 0L
            }
        } catch (e: Exception) {
            lastError = e
        }

        // 2) AssetFileDescriptor
        if (!copied) {
            try {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    afd.createInputStream().use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                    copied = dest.exists() && dest.length() > 0L
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        // 3) Plain InputStream
        if (!copied) {
            try {
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } ?: error("Не удалось открыть файл (openInputStream вернул null)")
                copied = dest.exists() && dest.length() > 0L
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (!copied || dest.length() == 0L) {
            dest.delete()
            val detail = lastError?.message?.let { ": $it" }.orEmpty()
            error("Не удалось прочитать выбранный файл$detail")
        }
        return dest
    }

    fun copyUriToCache(context: Context, uri: Uri, prefix: String, suffix: String): File {
        val dir = File(context.cacheDir, "imports").also { it.mkdirs() }
        val dest = File(dir, "${prefix}_${UUID.randomUUID()}$suffix")
        return copyUriToFile(context, uri, dest)
    }
}

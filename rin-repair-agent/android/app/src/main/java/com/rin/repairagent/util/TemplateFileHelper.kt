package com.rin.repairagent.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipFile

enum class TemplateKind {
    PPTX, ZIP, JSON, PDF
}

/**
 * Detects and validates RIN template files from SAF / GetContent URIs.
 * PPTX is often reported as application/zip — sniff content, not only extension.
 */
object TemplateFileHelper {

    private val ALLOWED = setOf("pptx", "zip", "json", "pdf")

    data class Detected(
        val kind: TemplateKind,
        val extension: String,
        val displayName: String
    )

    fun detect(context: Context, uri: Uri, localCopy: File, preferredName: String?): Detected {
        val name = preferredName?.takeIf { it.isNotBlank() }
            ?: UriIO.displayName(context, uri, "rin_template.pptx")
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        val extFromName = name.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .trim()
            .takeIf { it in ALLOWED }

        val sniffed = sniff(localCopy)
        val extFromMime = when {
            mime.contains("presentationml") || mime.contains("powerpoint") -> "pptx"
            mime == "application/pdf" || mime.endsWith("/pdf") -> "pdf"
            mime == "application/json" || mime.endsWith("+json") -> "json"
            mime == "application/zip" || mime == "application/x-zip-compressed" -> null // may be pptx
            else -> null
        }

        // Prefer content sniff for OOXML, then filename, then MIME, then default pptx for ZIP magic
        val extension = when {
            sniffed == TemplateKind.PPTX -> "pptx"
            sniffed == TemplateKind.PDF -> "pdf"
            sniffed == TemplateKind.JSON -> "json"
            sniffed == TemplateKind.ZIP && extFromName == "pptx" -> "pptx"
            sniffed == TemplateKind.ZIP && extFromMime == "pptx" -> "pptx"
            sniffed == TemplateKind.ZIP && (extFromName == "zip" || extFromMime == null) ->
                if (looksLikePptxZip(localCopy)) "pptx" else "zip"
            extFromName != null -> extFromName
            extFromMime != null -> extFromMime
            sniffed == TemplateKind.ZIP -> if (looksLikePptxZip(localCopy)) "pptx" else "zip"
            else -> error(
                "Не удалось определить тип шаблона. Выберите файл PPTX, ZIP, JSON или PDF."
            )
        }

        require(extension in ALLOWED) {
            "Недопустимый тип файла: .$extension (нужен PPTX, ZIP, JSON или PDF)"
        }

        // If declared pptx, must be valid OOXML package
        if (extension == "pptx") {
            require(looksLikePptxZip(localCopy)) {
                "Файл не является корректным PowerPoint (.pptx). Проверьте шаблон RIN."
            }
        }

        val kind = when (extension) {
            "pptx" -> TemplateKind.PPTX
            "pdf" -> TemplateKind.PDF
            "json" -> TemplateKind.JSON
            else -> TemplateKind.ZIP
        }

        val safeName = if (name.contains('.')) name else "$name.$extension"
        return Detected(kind, extension, safeName)
    }

    private fun sniff(file: File): TemplateKind? {
        if (!file.exists() || file.length() == 0L) return null
        val header = file.inputStream().use { input ->
            val buf = ByteArray(16)
            val n = input.read(buf)
            if (n <= 0) return null
            buf.copyOf(n)
        }
        // PDF
        if (header.size >= 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x44.toByte() && header[3] == 0x46.toByte()
        ) {
            return TemplateKind.PDF
        }
        // ZIP / PPTX / JAR
        if (header.size >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
            return if (looksLikePptxZip(file)) TemplateKind.PPTX else TemplateKind.ZIP
        }
        // JSON (starts with { or [)
        val first = header.firstOrNull { it != 0xEF.toByte() && it != 0xBB.toByte() && it != 0xBF.toByte() && it > 0x20 }
        if (first == '{'.code.toByte() || first == '['.code.toByte()) {
            return TemplateKind.JSON
        }
        return null
    }

    fun looksLikePptxZip(file: File): Boolean {
        return try {
            ZipFile(file).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toSet()
                names.any { it == "[Content_Types].xml" } &&
                    (names.any { it.startsWith("ppt/") } || names.any { it == "ppt/presentation.xml" })
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * If [zipFile] is a plain archive that contains exactly one .pptx (or a clear primary pptx),
     * extract it to [destPptx]. Returns true when extraction succeeded.
     */
    fun extractNestedPptx(zipFile: File, destPptx: File): Boolean {
        if (looksLikePptxZip(zipFile)) return false
        return try {
            ZipFile(zipFile).use { zip ->
                val pptxEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .filter { it.name.substringAfterLast('/').lowercase().endsWith(".pptx") }
                    .filter { !it.name.contains("__MACOSX", ignoreCase = true) }
                    .toList()
                val entry = when {
                    pptxEntries.size == 1 -> pptxEntries.first()
                    pptxEntries.isNotEmpty() ->
                        pptxEntries.firstOrNull {
                            it.name.substringAfterLast('/').lowercase().contains("rin")
                        } ?: pptxEntries.first()
                    else -> return false
                }
                destPptx.parentFile?.mkdirs()
                if (destPptx.exists()) destPptx.delete()
                zip.getInputStream(entry).use { input ->
                    destPptx.outputStream().use { output -> input.copyTo(output) }
                }
                destPptx.exists() && destPptx.length() > 0L && looksLikePptxZip(destPptx)
            }
        } catch (_: Exception) {
            false
        }
    }
}

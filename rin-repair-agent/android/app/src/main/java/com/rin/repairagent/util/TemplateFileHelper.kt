package com.rin.repairagent.util

import android.content.Context
import android.net.Uri
import com.rin.repairagent.data.export.PptxRinWriter
import java.io.File
import java.util.zip.ZipFile

enum class TemplateKind {
    PPTX, PPT, ZIP, JSON, PDF
}

/**
 * Detects RIN templates from SAF / ContentResolver.
 * A real PowerPoint is accepted when the package contains ppt/presentation.xml,
 * regardless of MIME (including application/octet-stream) or .ppt/.pptx extension.
 */
object TemplateFileHelper {

    data class Detected(
        val kind: TemplateKind,
        val extension: String,
        val displayName: String
    )

    fun detect(context: Context, uri: Uri, localCopy: File, preferredName: String?): Detected {
        val name = preferredName?.takeIf { it.isNotBlank() }
            ?: UriIO.displayName(context, uri, "")
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()

        if (PptxRinWriter.hasPresentationXml(localCopy) || looksLikePptxZip(localCopy)) {
            val display = name.ifBlank {
                // ContentResolver gave no DISPLAY_NAME — keep a last-segment fallback only.
                uri.lastPathSegment?.substringAfterLast('/') ?: "template.pptx"
            }
            return Detected(TemplateKind.PPTX, "pptx", display)
        }

        val header = localCopy.inputStream().use { input ->
            val buf = ByteArray(8)
            val n = input.read(buf)
            if (n <= 0) ByteArray(0) else buf.copyOf(n)
        }
        // OLE Compound: legacy binary .ppt
        if (header.size >= 4 &&
            header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() &&
            header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()
        ) {
            error(
                "Это старый двоичный формат .ppt. Откройте файл в PowerPoint и сохраните как .pptx."
            )
        }

        val sniffed = sniff(localCopy, header)
        val extFromName = name.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .trim()

        if (extFromName in setOf("pptx", "ppt") ||
            mime.contains("presentationml") ||
            mime.contains("powerpoint") ||
            mime == "application/octet-stream"
        ) {
            error(
                "В файле нет ppt/presentation.xml — это не настоящий PowerPoint-шаблон RIN."
            )
        }

        val extension = when {
            sniffed == TemplateKind.PDF || extFromName == "pdf" || mime.contains("pdf") -> "pdf"
            sniffed == TemplateKind.JSON || extFromName == "json" || mime.contains("json") -> "json"
            sniffed == TemplateKind.ZIP || extFromName == "zip" || mime.contains("zip") -> "zip"
            else -> error("Не удалось распознать файл. Выберите PowerPoint (.pptx / .ppt).")
        }
        val display = name.ifBlank { "template.$extension" }
        val kind = when (extension) {
            "pdf" -> TemplateKind.PDF
            "json" -> TemplateKind.JSON
            else -> TemplateKind.ZIP
        }
        return Detected(kind, extension, display)
    }

    private fun sniff(file: File, header: ByteArray): TemplateKind? {
        if (header.size >= 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x44.toByte() && header[3] == 0x46.toByte()
        ) {
            return TemplateKind.PDF
        }
        if (header.size >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
            return if (looksLikePptxZip(file)) TemplateKind.PPTX else TemplateKind.ZIP
        }
        val first = header.firstOrNull {
            it != 0xEF.toByte() && it != 0xBB.toByte() && it != 0xBF.toByte() && it > 0x20
        }
        if (first == '{'.code.toByte() || first == '['.code.toByte()) {
            return TemplateKind.JSON
        }
        return null
    }

    /** True when the ZIP/OOXML package contains ppt/presentation.xml. */
    fun looksLikePptxZip(file: File): Boolean = PptxRinWriter.hasPresentationXml(file)

    fun extractNestedPptx(zipFile: File, destPptx: File): Boolean {
        if (looksLikePptxZip(zipFile)) return false
        return try {
            ZipFile(zipFile).use { zip ->
                val pptxEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .filter {
                        val n = it.name.substringAfterLast('/').lowercase()
                        n.endsWith(".pptx") || n.endsWith(".ppt")
                    }
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

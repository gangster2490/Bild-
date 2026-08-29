package com.rin.repairagent.data.export

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.rin.repairagent.data.model.ExportedFile
import com.rin.repairagent.data.model.PhotoAnalysis
import com.rin.repairagent.data.model.ProjectPhoto
import com.rin.repairagent.data.model.RepairProject
import com.rin.repairagent.data.model.ResultLanguage
import com.rin.repairagent.util.TemplateFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.random.Random

data class ExportStep(
    val photo: ProjectPhoto,
    val analysis: PhotoAnalysis,
    val instructionRu: String,
    val instructionEn: String
)

/**
 * On-device PPTX (OOXML) + PDF generation. Uses uploaded RIN template for slide size/theme when present.
 * No backend server required.
 */
class DocumentExporter {

    suspend fun export(
        project: RepairProject,
        templateFile: File,
        steps: List<ExportStep>,
        outputDir: File
    ): Pair<List<ExportedFile>, List<String>> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val files = mutableListOf<ExportedFile>()
        val errors = mutableListOf<String>()
        val locales = when (project.language) {
            ResultLanguage.RU -> listOf("RU")
            ResultLanguage.EN -> listOf("EN")
            ResultLanguage.BOTH -> listOf("RU", "EN")
        }

        val slideSize = readSlideSize(templateFile)

        for (locale in locales) {
            val pptxName = "RIN_Repair_Instruction_$locale.pptx"
            val pdfName = "RIN_Repair_Instruction_$locale.pdf"
            val pptxPath = File(outputDir, pptxName)
            val pdfPath = File(outputDir, pdfName)
            try {
                writePptx(templateFile, pptxPath, project, steps, locale, slideSize)
                files += ExportedFile(
                    pptxName,
                    pptxPath.absolutePath,
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                )
            } catch (e: Exception) {
                errors += "PowerPoint $locale: ${e.message}"
            }
            try {
                writePdf(pdfPath, project, steps, locale)
                files += ExportedFile(pdfName, pdfPath.absolutePath, "application/pdf")
            } catch (e: Exception) {
                errors += "PDF $locale: ${e.message}"
            }
        }
        files to errors
    }

    private data class SlideSize(val cx: Long, val cy: Long)

    private fun readSlideSize(template: File): SlideSize {
        val fallback = SlideSize(12_192_000L, 6_858_000L)
        if (!template.exists() || template.length() == 0L) return fallback
        return try {
            ZipFile(template).use { zip ->
                val entry = zip.getEntry("ppt/presentation.xml") ?: return fallback
                val xml = zip.getInputStream(entry).bufferedReader().readText()
                val cx = Regex("""cx="(\d+)"""").find(xml)?.groupValues?.get(1)?.toLongOrNull()
                val cy = Regex("""cy="(\d+)"""").find(xml)?.groupValues?.get(1)?.toLongOrNull()
                if (cx != null && cy != null) SlideSize(cx, cy) else fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun writePptx(
        template: File,
        output: File,
        project: RepairProject,
        steps: List<ExportStep>,
        locale: String,
        size: SlideSize
    ) {
        require(steps.isNotEmpty()) { "Нет шагов для PowerPoint" }
        val tmp = File(output.parentFile, "${output.name}.tmp")
        val written = mutableSetOf<String>()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { zos ->
            fun putBytes(path: String, bytes: ByteArray, overwrite: Boolean = false) {
                if (path in written) {
                    if (!overwrite) return
                    // ZipOutputStream cannot replace; skip if already copied from template
                    return
                }
                written += path
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
            fun putFile(path: String, file: File) {
                if (path in written) return
                written += path
                zos.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }

            val skipPrefixes = listOf(
                "ppt/slides/",
                "[Content_Types].xml",
                "ppt/presentation.xml",
                "ppt/_rels/presentation.xml.rels",
                "ppt/media/"
            )
            // Accept PPTX even if stored as .zip (OEM MIME/extension mismatch)
            if (template.exists() &&
                (template.extension.equals("pptx", true) || TemplateFileHelper.looksLikePptxZip(template))
            ) {
                try {
                    ZipFile(template).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val e = entries.nextElement()
                            if (e.isDirectory) continue
                            if (skipPrefixes.any { e.name == it || e.name.startsWith(it) }) continue
                            if (e.name in written) continue
                            written += e.name
                            zos.putNextEntry(ZipEntry(e.name))
                            zip.getInputStream(e).use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                } catch (_: Exception) {
                    // continue with minimal package
                }
            }

            ensureLayoutParts(zos, written)

            val slideIds = mutableListOf<Int>()
            steps.forEachIndexed { index, step ->
                val page = index + 1
                slideIds += page
                val mediaName = "image$page.jpg"
                val photoFile = File(step.photo.localPath)
                require(photoFile.exists()) { "Фото ${step.photo.photoNumber} отсутствует" }
                putFile("ppt/media/$mediaName", photoFile)

                val isRu = locale == "RU"
                val title = if (isRu) {
                    "${project.title} — шаг ${step.photo.photoNumber}"
                } else {
                    "${project.title} — Step ${step.photo.photoNumber}"
                }
                val stage = if (isRu) step.analysis.repairStage else step.analysis.repairStageEn.ifBlank { step.analysis.repairStage }
                val instruction = if (isRu) step.instructionRu else step.instructionEn
                val warning = if (isRu) {
                    step.analysis.importantWarning
                } else {
                    step.analysis.importantWarningEn.ifBlank { step.analysis.importantWarning }
                }
                val caption = if (isRu) {
                    "Фото ${step.photo.photoNumber}: ${step.analysis.visibleAction.ifBlank { stage }}"
                } else {
                    "Photo ${step.photo.photoNumber}: ${step.analysis.visibleActionEn.ifBlank { step.analysis.visibleAction.ifBlank { stage } }}"
                }
                val footer = "RIN Repair Instruction | ${project.productModel}"

                putBytes(
                    "ppt/slides/slide$page.xml",
                    buildSlideXml(
                        title, stage, instruction, warning, step.analysis.tools,
                        caption, "rIdImage1", size.cx, size.cy, footer, page.toString()
                    ).toByteArray(Charsets.UTF_8)
                )
                putBytes(
                    "ppt/slides/_rels/slide$page.xml.rels",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
                      <Relationship Id="rIdImage1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/$mediaName"/>
                    </Relationships>
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                )
            }

            val sldIdLst = slideIds.mapIndexed { idx, n ->
                """<p:sldId id="${256 + idx}" r:id="rId${10 + n}"/>"""
            }.joinToString("")
            putBytes(
                "ppt/presentation.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                  <p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>
                  <p:sldIdLst>$sldIdLst</p:sldIdLst>
                  <p:sldSz cx="${size.cx}" cy="${size.cy}"/>
                  <p:notesSz cx="6858000" cy="9144000"/>
                </p:presentation>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )

            val rels = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>""")
                slideIds.forEach { n ->
                    append("""<Relationship Id="rId${10 + n}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$n.xml"/>""")
                }
                append("""<Relationship Id="rIdTheme" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>""")
                append("</Relationships>")
            }
            putBytes("ppt/_rels/presentation.xml.rels", rels.toByteArray(Charsets.UTF_8))

            val overrides = slideIds.joinToString("") {
                """<Override PartName="/ppt/slides/slide$it.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>"""
            }
            putBytes(
                "[Content_Types].xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Default Extension="jpeg" ContentType="image/jpeg"/>
                  <Default Extension="jpg" ContentType="image/jpeg"/>
                  <Default Extension="png" ContentType="image/png"/>
                  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
                  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
                  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
                  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
                  $overrides
                </Types>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )

            putBytes(
                "_rels/.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
                </Relationships>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
        }
        if (output.exists()) output.delete()
        if (!tmp.renameTo(output)) {
            tmp.copyTo(output, overwrite = true)
            tmp.delete()
        }
        require(output.length() > 0L) { "PPTX пустой" }
    }

    private fun ensureLayoutParts(zos: ZipOutputStream, written: MutableSet<String>) {
        fun put(path: String, content: String) {
            if (path in written) return
            written += path
            zos.putNextEntry(ZipEntry(path))
            zos.write(content.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        put(
            "ppt/slideLayouts/slideLayout1.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1">
  <p:cSld name="Blank"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sldLayout>"""
        )
        put(
            "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>"""
        )
        put(
            "ppt/slideMasters/slideMaster1.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld><p:bg><p:bgRef idx="1001"><a:schemeClr val="bg1"/></p:bgRef></p:bg>
  <p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
  <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
  <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
</p:sldMaster>"""
        )
        put(
            "ppt/slideMasters/_rels/slideMaster1.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
</Relationships>"""
        )
        put(
            "ppt/theme/theme1.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="RIN">
  <a:themeElements>
    <a:clrScheme name="RIN">
      <a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1>
      <a:lt1><a:sysClr val="window" lastClr="FFFFFF"/></a:lt1>
      <a:dk2><a:srgbClr val="14212B"/></a:dk2>
      <a:lt2><a:srgbClr val="E8EEF3"/></a:lt2>
      <a:accent1><a:srgbClr val="145A8C"/></a:accent1>
      <a:accent2><a:srgbClr val="1F7A6C"/></a:accent2>
      <a:accent3><a:srgbClr val="C45C26"/></a:accent3>
      <a:accent4><a:srgbClr val="5B6B7A"/></a:accent4>
      <a:accent5><a:srgbClr val="2F6B9A"/></a:accent5>
      <a:accent6><a:srgbClr val="7A8B99"/></a:accent6>
      <a:hlink><a:srgbClr val="145A8C"/></a:hlink>
      <a:folHlink><a:srgbClr val="1F7A6C"/></a:folHlink>
    </a:clrScheme>
    <a:fontScheme name="RIN">
      <a:majorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont>
      <a:minorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont>
    </a:fontScheme>
    <a:fmtScheme name="RIN">
      <a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst>
      <a:lnStyleLst><a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst>
      <a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>
      <a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst>
    </a:fmtScheme>
  </a:themeElements>
</a:theme>"""
        )
    }

    private fun xmlEsc(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun wrap(text: String, maxLen: Int): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var line = ""
        for (w in words) {
            val next = if (line.isEmpty()) w else "$line $w"
            if (next.length > maxLen) {
                if (line.isNotEmpty()) lines += line
                line = w
            } else line = next
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    private fun rid() = Random.nextInt(2, 100_000)

    private fun buildSlideXml(
        title: String,
        stage: String,
        instruction: String,
        warning: String,
        tools: List<String>,
        caption: String,
        imageRid: String,
        slideW: Long,
        slideH: Long,
        footer: String,
        pageNo: String
    ): String {
        val headerH = (slideH * 0.11).toLong()
        val footerH = (slideH * 0.07).toLong()
        val margin = (slideW * 0.03).toLong()
        val contentTop = headerH + margin
        val contentH = slideH - headerH - footerH - margin * 2
        val photoW = (slideW * 0.46).toLong()
        val photoH = (contentH * 0.72).toLong()
        val textX = margin * 2 + photoW
        val textW = slideW - textX - margin
        val toolLine = if (tools.isNotEmpty()) "Инструменты / Tools: ${tools.joinToString(", ")}" else ""
        val warnLine = warning.trim().takeIf { it.isNotEmpty() }?.let { "⚠ $it" }.orEmpty()
        val body = buildList {
            addAll(wrap(instruction, 55))
            if (toolLine.isNotEmpty()) {
                add("")
                addAll(wrap(toolLine, 55))
            }
            if (warnLine.isNotEmpty()) {
                add("")
                addAll(wrap(warnLine, 55))
            }
        }

        fun textShape(name: String, x: Long, y: Long, cx: Long, cy: Long, lines: List<String>, font: Int, bold: Boolean): String {
            val runs = lines.joinToString("") { line ->
                """
                <a:p><a:pPr algn="l"/><a:r>
                  <a:rPr lang="ru-RU" sz="${font * 100}"${if (bold) """ b="1"""" else ""}>
                    <a:solidFill><a:srgbClr val="1A1A1A"/></a:solidFill><a:latin typeface="Calibri"/>
                  </a:rPr><a:t>${xmlEsc(line)}</a:t>
                </a:r></a:p>
                """.trimIndent()
            }.ifBlank { """<a:p><a:endParaRPr/></a:p>""" }
            return """
            <p:sp>
              <p:nvSpPr><p:cNvPr id="${rid()}" name="${xmlEsc(name)}"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr>
              <p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
                <a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/><a:ln><a:noFill/></a:ln></p:spPr>
              <p:txBody><a:bodyPr wrap="square" lIns="36000" tIns="36000" rIns="36000" bIns="36000"/><a:lstStyle/>$runs</p:txBody>
            </p:sp>
            """.trimIndent()
        }

        fun rect(name: String, x: Long, y: Long, cx: Long, cy: Long, fill: String) = """
            <p:sp>
              <p:nvSpPr><p:cNvPr id="${rid()}" name="${xmlEsc(name)}"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
              <p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
                <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                <a:solidFill><a:srgbClr val="$fill"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr>
            </p:sp>
        """.trimIndent()

        val pic = """
            <p:pic>
              <p:nvPicPr><p:cNvPr id="${rid()}" name="Photo"/><p:cNvPicPr><a:picLocks noChangeAspect="0"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>
              <p:blipFill><a:blip r:embed="$imageRid"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
              <p:spPr><a:xfrm><a:off x="$margin" y="$contentTop"/><a:ext cx="$photoW" cy="$photoH"/></a:xfrm>
                <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                <a:ln w="12700"><a:solidFill><a:srgbClr val="145A8C"/></a:solidFill></a:ln></p:spPr>
            </p:pic>
        """.trimIndent()

        return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
         xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
         xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
          <p:cSld>
            <p:bg><p:bgPr><a:solidFill><a:srgbClr val="F3F6F9"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$slideW" cy="$slideH"/><a:chOff x="0" y="0"/><a:chExt cx="$slideW" cy="$slideH"/></a:xfrm></p:grpSpPr>
              ${rect("Header", 0, 0, slideW, headerH, "145A8C")}
              ${textShape("Title", margin, headerH / 5, slideW - margin * 2, headerH * 3 / 5, wrap(title, 70), 16, true)}
              $pic
              ${textShape("Caption", margin, contentTop + photoH + margin / 3, photoW, (contentH * 0.18).toLong(), wrap(caption, 50), 11, false)}
              ${textShape("Stage", textX, contentTop, textW, (contentH * 0.12).toLong(), wrap(stage, 50), 14, true)}
              ${textShape("Instruction", textX, contentTop + (contentH * 0.12).toLong(), textW, (contentH * 0.78).toLong(), body, 11, false)}
              ${rect("FooterBar", 0, slideH - footerH, slideW, footerH, "1F7A6C")}
              ${textShape("Footer", margin, slideH - footerH + footerH / 5, slideW - margin * 2, footerH * 3 / 5, listOf("$footer  |  $pageNo"), 10, false)}
            </p:spTree>
          </p:cSld>
          <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
        </p:sld>
        """.trimIndent()
    }

    private fun writePdf(output: File, project: RepairProject, steps: List<ExportStep>, locale: String) {
        require(steps.isNotEmpty()) { "Нет страниц для PDF" }
        val doc = PdfDocument()
        val pageWidth = 842 // A4 landscape points
        val pageHeight = 595
        val isRu = locale == "RU"

        steps.forEachIndexed { index, step ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.parseColor("#F3F6F9"))

            val headerPaint = Paint().apply { color = Color.parseColor("#145A8C"); style = Paint.Style.FILL }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 48f, headerPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 16f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val title = if (isRu) {
                "${project.title} — шаг ${step.photo.photoNumber}"
            } else {
                "${project.title} — Step ${step.photo.photoNumber}"
            }
            canvas.drawText(title.take(80), 24f, 30f, titlePaint)

            val photoW = pageWidth * 0.45f
            val photoH = pageHeight * 0.58f
            val bmp = BitmapFactory.decodeFile(step.photo.localPath)
            if (bmp != null) {
                val scale = minOf(photoW / bmp.width, photoH / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val dest = android.graphics.RectF(24f, 64f, 24f + dw, 64f + dh)
                canvas.drawBitmap(bmp, null, dest, null)
                if (!bmp.isRecycled) bmp.recycle()
            }

            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#14212B")
                textSize = 11f
            }
            val stagePaint = Paint(bodyPaint).apply {
                color = Color.parseColor("#145A8C")
                textSize = 13f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val warnPaint = Paint(bodyPaint).apply { color = Color.parseColor("#B42318") }
            val textX = 24f + photoW + 16f
            val textW = pageWidth - textX - 24f
            val stage = if (isRu) step.analysis.repairStage else step.analysis.repairStageEn.ifBlank { step.analysis.repairStage }
            val instruction = if (isRu) step.instructionRu else step.instructionEn
            val warning = if (isRu) step.analysis.importantWarning else step.analysis.importantWarningEn.ifBlank { step.analysis.importantWarning }
            val caption = if (isRu) {
                "Фото ${step.photo.photoNumber}: ${step.analysis.visibleAction}"
            } else {
                "Photo ${step.photo.photoNumber}: ${step.analysis.visibleActionEn.ifBlank { step.analysis.visibleAction }}"
            }

            canvas.drawText(stage.take(60), textX, 80f, stagePaint)
            drawMultiline(canvas, instruction, textX, 100f, textW, 360f, bodyPaint)
            val tools = step.analysis.tools.joinToString(", ")
            if (tools.isNotEmpty()) {
                canvas.drawText((if (isRu) "Инструменты: " else "Tools: ") + tools.take(70), textX, 470f, bodyPaint)
            }
            canvas.drawText((if (isRu) "Предупреждение: " else "Warning: ") + warning.take(70), textX, 495f, warnPaint)
            canvas.drawText(caption.take(70), 24f, 64f + photoH + 18f, bodyPaint)

            val footerPaint = Paint().apply { color = Color.parseColor("#1F7A6C") }
            canvas.drawRect(0f, pageHeight - 32f, pageWidth.toFloat(), pageHeight.toFloat(), footerPaint)
            val footerText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 9f }
            canvas.drawText(
                "RIN Repair Instruction | ${project.productModel} | ${index + 1}",
                24f,
                pageHeight - 12f,
                footerText
            )
            doc.finishPage(page)
        }

        FileOutputStream(output).use { doc.writeTo(it) }
        doc.close()
        require(output.length() > 100L) { "PDF пустой" }
    }

    private fun drawMultiline(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        yStart: Float,
        maxWidth: Float,
        maxHeight: Float,
        paint: Paint
    ) {
        val words = text.split(Regex("\\s+"))
        var line = ""
        var y = yStart
        val lineHeight = paint.textSize * 1.35f
        for (w in words) {
            val next = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(next) > maxWidth) {
                canvas.drawText(line, x, y, paint)
                y += lineHeight
                if (y - yStart > maxHeight) return
                line = w
            } else {
                line = next
            }
        }
        if (line.isNotEmpty() && y - yStart <= maxHeight) {
            canvas.drawText(line, x, y, paint)
        }
    }
}

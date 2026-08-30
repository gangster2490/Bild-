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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ExportStep(
    val photo: ProjectPhoto,
    val analysis: PhotoAnalysis,
    val instructionRu: String,
    val instructionEn: String
)

/**
 * On-device PPTX + PDF. PowerPoint is a copy of the uploaded RIN template
 * with only text and photos replaced — never a new self-made deck.
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

        require(PptxRinWriter.hasPresentationXml(templateFile)) {
            "RIN-шаблон не является PowerPoint (нет ppt/presentation.xml)"
        }

        for (locale in locales) {
            val pptxName = "RIN_Repair_Instruction_$locale.pptx"
            val pdfName = "RIN_Repair_Instruction_$locale.pdf"
            val pptxPath = File(outputDir, pptxName)
            val pdfPath = File(outputDir, pdfName)
            try {
                val check = PptxRinWriter.write(templateFile, pptxPath, project, steps, locale)
                if (!check.ok) {
                    errors += check.errors.map { "PowerPoint $locale: $it" }
                }
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

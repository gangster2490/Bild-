package com.rin.repairagent.data.export

import com.rin.repairagent.data.model.RepairProject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Builds a RIN PowerPoint by **copying the uploaded template PPTX** and changing
 * only text runs and picture binaries. Masters, theme, slide size, shapes,
 * geometry, colors, tables, lines, headers/footers and picture frames stay intact.
 *
 * Real Office files shuffle attribute order, put `xml:space="preserve"` on `<a:t>`,
 * wrap pictures in `mc:AlternateContent`, keep titles in `p:graphicFrame` tables,
 * and store viewProps/presProps beside slides in `presentation.xml.rels`.
 */
object PptxRinWriter {

    data class DesignCheck(
        val templateSlideCount: Int,
        val outputSlideCount: Int,
        val slideSizeMatch: Boolean,
        val mastersPreserved: Boolean,
        val themePreserved: Boolean,
        val pictureFramesUnchanged: Boolean,
        val errors: List<String>
    ) {
        val ok: Boolean get() = errors.isEmpty()
    }

    data class FillTexts(
        val title: String,
        val tools: String,
        val instruction: String,
        val warning: String,
        val caption: String
    )

    fun write(
        template: File,
        output: File,
        @Suppress("UNUSED_PARAMETER")
        project: RepairProject,
        steps: List<ExportStep>,
        locale: String
    ): DesignCheck {
        require(steps.isNotEmpty()) { "Нет шагов для PowerPoint" }
        require(hasPresentationXml(template)) {
            "Загруженный файл не содержит ppt/presentation.xml — это не PowerPoint-шаблон."
        }

        val parts = loadZip(template)
        val presPath = "ppt/presentation.xml"
        val relsPath = "ppt/_rels/presentation.xml.rels"
        val typesPath = "[Content_Types].xml"
        val presXml = parts[presPath]?.toString(Charsets.UTF_8)
            ?: error("В шаблоне нет ppt/presentation.xml")
        val relsXml = parts[relsPath]?.toString(Charsets.UTF_8)
            ?: error("В шаблоне нет ppt/_rels/presentation.xml.rels")
        val typesXml = parts[typesPath]?.toString(Charsets.UTF_8).orEmpty()

        val templateSize = readSlideSize(presXml)
        val templateSlides = slideOrder(presXml, relsXml)
        require(templateSlides.isNotEmpty()) { "В шаблоне нет слайдов" }

        val infos = templateSlides.map { path ->
            val xml = parts[path]?.toString(Charsets.UTF_8) ?: error("Нет слайда $path")
            SlideInfo(path, xml, parsePics(xml), parseTextShapes(xml), templateSize)
        }

        val repeatIdx = pickRepeatingSlideIndex(infos)
        val prefix = infos.subList(0, repeatIdx)
        val trailing = trailingSlides(infos, repeatIdx)

        val prototype = infos[repeatIdx]
        val protoRelsPath = relsForSlide(prototype.path)
        val protoRels = parts[protoRelsPath]?.toString(Charsets.UTF_8) ?: emptyRels()

        var nextSlideNum = existingSlideNumbers(parts).maxOrNull() ?: 1
        var nextRelId = maxRid(relsXml) + 1
        var nextSldId = maxSldId(presXml) + 1

        val newRels = StringBuilder()
        val newSldIds = StringBuilder()
        val newOverrides = StringBuilder()
        val usedSlidePaths = mutableSetOf<String>()

        fun appendSlide(slidePath: String, relId: String, sldId: Int) {
            usedSlidePaths += slidePath
            newRels.append(
                """<Relationship Id="$relId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="${slidePath.removePrefix("ppt/")}"/>"""
            )
            newSldIds.append("""<p:sldId id="$sldId" r:id="$relId"/>""")
            val partName = "/$slidePath"
            if (!typesXml.contains("PartName=\"$partName\"")) {
                newOverrides.append(
                    """<Override PartName="$partName" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>"""
                )
            }
        }

        prefix.forEach { info ->
            val origRid = relIdForTarget(relsXml, info.path.removePrefix("ppt/"))
                ?: "rId${nextRelId++}"
            val origSld = sldIdForRid(presXml, origRid) ?: nextSldId++
            appendSlide(info.path, origRid, origSld)
        }

        val isRu = locale == "RU"
        steps.forEachIndexed { index, step ->
            val filled = fillTexts(step, isRu)
            val photo = File(step.photo.localPath)
            require(photo.exists() && photo.length() > 0L) {
                "Фото ${step.photo.photoNumber} отсутствует"
            }

            val slidePath: String
            val slideRelsPath: String
            val sourceXml: String
            val sourceRels: String
            if (index == 0) {
                slidePath = prototype.path
                slideRelsPath = protoRelsPath
                sourceXml = prototype.xml
                sourceRels = protoRels
            } else {
                nextSlideNum++
                slidePath = "ppt/slides/slide$nextSlideNum.xml"
                slideRelsPath = relsForSlide(slidePath)
                sourceXml = uniquifyCNvPrIds(prototype.xml, index * 10_000)
                sourceRels = protoRels
            }

            val mediaPath = "ppt/media/rin_step_${index + 1}.jpg"
            parts[mediaPath] = photo.readBytes()
            val replaced = replaceContent(sourceXml, sourceRels, prototype, filled, mediaPath)
            parts[slidePath] = replaced.slideXml.toByteArray(Charsets.UTF_8)
            parts[slideRelsPath] = replaced.relsXml.toByteArray(Charsets.UTF_8)

            val relId = if (index == 0) {
                relIdForTarget(relsXml, prototype.path.removePrefix("ppt/"))
                    ?: "rId${nextRelId++}"
            } else {
                "rId${nextRelId++}"
            }
            val sldId = if (index == 0) {
                sldIdForRid(presXml, relId) ?: nextSldId++
            } else {
                nextSldId++
            }
            appendSlide(slidePath, relId, sldId)
        }

        trailing.forEach { info ->
            val origRid = relIdForTarget(relsXml, info.path.removePrefix("ppt/"))
                ?: "rId${nextRelId++}"
            val origSld = sldIdForRid(presXml, origRid) ?: nextSldId++
            appendSlide(info.path, origRid, origSld)
        }

        parts[relsPath] = rewritePresentationRels(relsXml, newRels.toString()).toByteArray(Charsets.UTF_8)
        parts[presPath] = rewriteSldIdLst(presXml, newSldIds.toString()).toByteArray(Charsets.UTF_8)
        if (typesXml.isNotBlank()) {
            parts[typesPath] = ensureJpegAndOverrides(typesXml, newOverrides.toString()).toByteArray(Charsets.UTF_8)
        }

        writeZip(parts, output)
        require(output.exists() && output.length() > 0L) { "PPTX пустой" }

        return compareDesign(template, output, templateSize, templateSlides.size, usedSlidePaths.size)
    }

    private data class Pic(
        val x: Long, val y: Long, val cx: Long, val cy: Long,
        val embed: String, val block: String
    ) {
        val area: Long get() = cx * cy
    }

    private data class TextShape(
        val x: Long, val y: Long, val cx: Long, val cy: Long,
        val fontSz: Int, val text: String, val block: String,
        val order: Int = 0,
        val fromTable: Boolean = false
    )

    private data class SlideInfo(
        val path: String,
        val xml: String,
        val pics: List<Pic>,
        val texts: List<TextShape>,
        val slideSize: Pair<Long, Long>
    ) {
        val contentPicture: Pic?
            get() {
                val (sw, sh) = slideSize
                val slideArea = (sw * sh).takeIf { it > 0 } ?: return pics.maxByOrNull { it.area }
                val large = pics.filter { it.area * 100 / slideArea >= MIN_PHOTO_PCT }
                return large.maxByOrNull { it.area } ?: pics.filter { !looksLikeLogoPic(it, slideArea) }
                    .maxByOrNull { it.area }
            }
    }

    private data class ReplacedInner(val slideXml: String, val relsXml: String)

    private fun pickRepeatingSlideIndex(infos: List<SlideInfo>): Int {
        if (infos.isEmpty()) return 0
        data class Score(val index: Int, val photoArea: Long, val photoPct: Long, val hint: Int)
        val slideArea = infos.first().slideSize.let { (w, h) ->
            (w * h).takeIf { it > 0 } ?: 1L
        }
        val scores = infos.mapIndexed { i, info ->
            val pic = info.contentPicture
            val area = pic?.area ?: 0L
            val pct = area * 100 / slideArea
            Score(i, area, pct, contentHint(info))
        }
        val withPhoto = scores.filter { it.photoPct >= MIN_PHOTO_PCT || it.hint >= 2 }
        if (withPhoto.isEmpty()) {
            return if (infos.size >= 2) 1 else 0
        }
        val bestHint = withPhoto.maxOf { it.hint }
        val hinted = withPhoto.filter { it.hint == bestHint || it.hint >= 2 }
        val pool = hinted.ifEmpty { withPhoto }
        val clusterArea = pool.maxOf { it.photoArea }
        val cluster = pool.filter {
            it.photoArea >= clusterArea * 85 / 100 || (clusterArea == 0L && it.hint >= 2)
        }
        val notCover = cluster.filter { it.index > 0 }
        return (notCover.minByOrNull { it.index } ?: cluster.minByOrNull { it.index })!!.index
    }

    private fun contentHint(info: SlideInfo): Int {
        var hint = 0
        val blob = info.texts.joinToString("\n") { it.text }.lowercase()
        if (blob.contains("инструмент") || blob.contains("tool")) hint += 2
        if (blob.contains("шаг") || blob.contains("step")) hint += 2
        if (info.texts.size >= 3) hint += 1
        if (info.contentPicture != null) hint += 1
        val titleish = info.texts.maxByOrNull { it.fontSz }?.text.orEmpty().lowercase()
        if (titleish.contains("rin") && info.texts.size <= 2) hint -= 2
        return hint
    }

    private fun trailingSlides(infos: List<SlideInfo>, repeatIdx: Int): List<SlideInfo> {
        if (repeatIdx >= infos.lastIndex) return emptyList()
        // Keep only slides after the repeating cluster that are not themselves
        // extra copies of the same photo layout (sample step slides).
        val protoArea = infos[repeatIdx].contentPicture?.area ?: return infos.subList(repeatIdx + 1, infos.size)
        var lastSame = repeatIdx
        for (i in repeatIdx + 1 until infos.size) {
            val area = infos[i].contentPicture?.area ?: break
            if (area >= protoArea * 85 / 100 && area <= protoArea * 115 / 100) {
                lastSame = i
            } else {
                break
            }
        }
        return if (lastSame + 1 < infos.size) infos.subList(lastSame + 1, infos.size) else emptyList()
    }

    private fun looksLikeLogoPic(pic: Pic, slideArea: Long): Boolean {
        if (slideArea <= 0L) return false
        return pic.area * 100 / slideArea < LOGO_MAX_PCT
    }

    private fun replaceContent(
        slideXml: String,
        relsXml: String,
        proto: SlideInfo,
        texts: FillTexts,
        mediaPath: String
    ): ReplacedInner {
        var xml = slideXml
        var rels = relsXml
        val pic = proto.contentPicture
        if (pic != null) {
            val target = "../media/${mediaPath.substringAfterLast('/')}"
            val slideArea = (proto.slideSize.first * proto.slideSize.second).takeIf { it > 0 } ?: 1L
            val embeds = proto.pics
                .filter { candidate ->
                    candidate.embed.isNotBlank() && (
                        candidate.embed == pic.embed ||
                            (candidate.area >= pic.area * 85 / 100 && !looksLikeLogoPic(candidate, slideArea))
                        )
                }
                .map { it.embed }
                .distinct()
                .ifEmpty { listOf(pic.embed).filter { it.isNotBlank() } }
            for (embed in embeds) {
                rels = upsertImageRel(rels, embed, target)
            }
        }

        val assignments = assignTexts(proto, texts)
        for ((shape, value) in assignments) {
            if (value.isBlank()) continue
            val updated = replaceFirstAtText(shape.block, value, clearRest = !shape.fromTable)
            xml = replaceFirstOccurrence(xml, shape.block, updated)
        }
        return ReplacedInner(xml, rels)
    }

    private fun assignTexts(info: SlideInfo, fill: FillTexts): List<Pair<TextShape, String>> {
        val (_, sh) = info.slideSize
        val pic = info.contentPicture
        val footerY = (sh * 88) / 100
        val usable = info.texts.filter { shape ->
            val t = shape.text.trim()
            if (shape.y >= footerY && t.length < 40) return@filter false
            if (looksLikeBrand(t)) return@filter false
            true
        }
        if (usable.isEmpty()) return emptyList()

        val caption = if (pic != null) {
            usable.filter {
                it.y >= pic.y + pic.cy * 7 / 10 &&
                    it.x + it.cx >= pic.x &&
                    it.x <= pic.x + pic.cx
            }.minByOrNull { it.y }
        } else null

        val rest = usable.filter { it !== caption }.sortedWith(compareBy({ it.y }, { it.x }, { it.order }))
        val title = rest.maxByOrNull { it.fontSz } ?: rest.firstOrNull()
        val afterTitle = rest.filter { it !== title }

        val toolsShape = afterTitle.firstOrNull { s ->
            val t = s.text.lowercase()
            t.contains("инструмент") || t.contains("tool")
        } ?: afterTitle.firstOrNull { it.cy < (sh / 10) && it !== title }

        val warnShape = afterTitle.firstOrNull { s ->
            val t = s.text.lowercase()
            t.contains("важно") || t.contains("warning") || t.contains("⚠")
        }

        val body = afterTitle
            .filter { it !== toolsShape && it !== warnShape }
            .maxByOrNull { it.cy * it.cx + it.text.length }

        val result = mutableListOf<Pair<TextShape, String>>()
        title?.let { result += it to fill.title }
        toolsShape?.let { result += it to fill.tools }
        body?.let { result += it to fill.instruction }
        warnShape?.let { result += it to fill.warning }
        caption?.let { result += it to fill.caption }
        return result.distinctBy { it.first.block }
    }

    private fun fillTexts(step: ExportStep, isRu: Boolean): FillTexts {
        val stage = if (isRu) {
            step.analysis.repairStage.ifBlank { "Шаг ${step.photo.photoNumber}" }
        } else {
            step.analysis.repairStageEn.ifBlank {
                step.analysis.repairStage.ifBlank { "Step ${step.photo.photoNumber}" }
            }
        }
        val title = if (isRu) {
            if (stage.contains("шаг", ignoreCase = true)) stage
            else "Шаг ${step.photo.photoNumber}: $stage"
        } else {
            if (stage.contains("step", ignoreCase = true)) stage
            else "Step ${step.photo.photoNumber}: $stage"
        }
        val tools = if (step.analysis.tools.isEmpty()) {
            ""
        } else if (isRu) {
            "Инструменты: ${step.analysis.tools.joinToString(" | ")}"
        } else {
            "Tools: ${step.analysis.tools.joinToString(" | ")}"
        }
        val instruction = if (isRu) step.instructionRu else step.instructionEn
        val warning = if (isRu) {
            step.analysis.importantWarning
        } else {
            step.analysis.importantWarningEn.ifBlank { step.analysis.importantWarning }
        }
        val caption = if (isRu) {
            "Фото ${step.photo.photoNumber}: ${step.analysis.visibleAction.ifBlank { stage }}"
        } else {
            "Photo ${step.photo.photoNumber}: ${
                step.analysis.visibleActionEn.ifBlank { step.analysis.visibleAction.ifBlank { stage } }
            }"
        }
        return FillTexts(title, tools, instruction, warning, caption)
    }

    private fun looksLikeBrand(text: String): Boolean {
        val t = text.trim()
        return t.length in 2..18 && t.none { it.isWhitespace() } &&
            t.uppercase() == t && t.any { it.isLetter() }
    }

    fun hasPresentationXml(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().any { e ->
                    e.name.replace('\\', '/') == "ppt/presentation.xml"
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun compareDesign(
        template: File,
        output: File,
        templateSize: Pair<Long, Long>,
        templateSlideCount: Int,
        outputSlideCount: Int
    ): DesignCheck {
        val errors = mutableListOf<String>()
        val outParts = loadZip(output)
        val outPres = outParts["ppt/presentation.xml"]?.toString(Charsets.UTF_8).orEmpty()
        val outSize = readSlideSize(outPres)
        val sizeMatch = outSize == templateSize
        if (!sizeMatch) errors += "Размер слайда не совпал с шаблоном"
        val masters = outParts.keys.any { it.startsWith("ppt/slideMasters/") }
        if (!masters) errors += "Мастер-слайды шаблона отсутствуют"
        val theme = outParts.keys.any { it.startsWith("ppt/theme/") }
        if (!theme) errors += "Тема (цвета/шрифты) шаблона отсутствует"
        val tplParts = loadZip(template)
        val tplMaster = tplParts.keys.filter { it.startsWith("ppt/slideMasters/") }.toSet()
        val outMaster = outParts.keys.filter { it.startsWith("ppt/slideMasters/") }.toSet()
        if (tplMaster.isNotEmpty() && !outMaster.containsAll(tplMaster)) {
            errors += "Набор мастер-слайдов изменился"
        }
        val framesOk = pictureFramesMatch(tplParts, outParts)
        if (!framesOk) errors += "Рамки фотографий (положение/размер) изменились"
        val outRels = outParts["ppt/_rels/presentation.xml.rels"]?.toString(Charsets.UTF_8).orEmpty()
        val tplRels = tplParts["ppt/_rels/presentation.xml.rels"]?.toString(Charsets.UTF_8).orEmpty()
        val lost = keptNonSlideTargets(tplRels) - keptNonSlideTargets(outRels)
        if (lost.isNotEmpty()) {
            errors += "Потеряны связи шаблона: ${lost.joinToString()}"
        }
        return DesignCheck(
            templateSlideCount = templateSlideCount,
            outputSlideCount = outputSlideCount,
            slideSizeMatch = sizeMatch,
            mastersPreserved = masters,
            themePreserved = theme,
            pictureFramesUnchanged = framesOk,
            errors = errors
        )
    }

    private fun keptNonSlideTargets(relsXml: String): Set<String> =
        parseRelationships(relsXml)
            .filterNot { isSlideRel(it.type) }
            .map { it.target.replace('\\', '/') }
            .toSet()

    private fun pictureFramesMatch(
        template: Map<String, ByteArray>,
        output: Map<String, ByteArray>
    ): Boolean {
        val proto = template.keys.filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            .map { parsePics(template.getValue(it).toString(Charsets.UTF_8)) }
            .firstOrNull { pics -> pics.any { it.area > 0 } }
            ?: return true
        val protoFrames = proto.map { Triple(it.x, it.y, it.cx to it.cy) }.toSet()
        val outSlides = output.keys.filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
        if (outSlides.isEmpty()) return false
        return outSlides.any { path ->
            val frames = parsePics(output.getValue(path).toString(Charsets.UTF_8))
                .map { Triple(it.x, it.y, it.cx to it.cy) }
                .toSet()
            frames.containsAll(protoFrames) || protoFrames.containsAll(frames)
        }
    }

    private fun loadZip(file: File): MutableMap<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        ZipFile(file).use { zip ->
            zip.entries().asSequence().forEach { e ->
                if (e.isDirectory) return@forEach
                val name = e.name.replace('\\', '/')
                map[name] = zip.getInputStream(e).use { it.readBytes() }
            }
        }
        return map
    }

    private fun writeZip(parts: Map<String, ByteArray>, output: File) {
        output.parentFile?.mkdirs()
        val tmp = File(output.parentFile, "${output.name}.tmp")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { zos ->
            val ordered = parts.entries.sortedBy { (k, _) ->
                when {
                    k == "[Content_Types].xml" -> 0
                    k == "_rels/.rels" -> 1
                    else -> 2
                }
            }
            for ((name, bytes) in ordered) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        if (output.exists()) output.delete()
        if (!tmp.renameTo(output)) {
            tmp.copyTo(output, overwrite = true)
            tmp.delete()
        }
    }

    private fun slideOrder(presXml: String, relsXml: String): List<String> {
        val idToTarget = HashMap<String, String>()
        parseRelationships(relsXml).forEach { rel ->
            if (!isSlideRel(rel.type)) return@forEach
            val target = rel.target.replace('\\', '/')
            val path = if (target.startsWith("ppt/")) target else "ppt/" + target.removePrefix("/")
            idToTarget[rel.id] = path
        }
        val ordered = parseSlideIds(presXml).mapNotNull { idToTarget[it.rId] }
        return ordered.ifEmpty { idToTarget.values.toList() }
    }

    private fun parsePics(xml: String): List<Pic> {
        return PIC_RE.findAll(xml).mapNotNull { m ->
            val block = m.value
            val embed = EMBED_RE.find(block)?.groupValues?.get(1).orEmpty()
            val xfrm = XFRM_RE.findAll(block).lastOrNull()?.value
            val off = xfrm?.let { OFF_RE.find(it) }
            val ext = xfrm?.let { EXT_RE.find(it) }
            val x = off?.let { attrLong(it.groupValues[1], "x") } ?: 0L
            val y = off?.let { attrLong(it.groupValues[1], "y") } ?: 0L
            val cx = ext?.let { attrLong(it.groupValues[1], "cx") } ?: 0L
            val cy = ext?.let { attrLong(it.groupValues[1], "cy") } ?: 0L
            if (embed.isBlank() && cx == 0L && cy == 0L) return@mapNotNull null
            Pic(x = x, y = y, cx = cx, cy = cy, embed = embed, block = block)
        }.toList()
    }

    private fun parseTextShapes(xml: String): List<TextShape> {
        val out = mutableListOf<TextShape>()
        var order = 0
        SP_RE.findAll(xml).forEach { m ->
            toTextShape(m.value, order++, fromTable = false)?.let { out += it }
        }
        GF_RE.findAll(xml).forEach { m ->
            val frame = m.value
            val cells = TC_RE.findAll(frame).toList()
            if (cells.size > 1) {
                cells.forEach { cell ->
                    toTextShape(cell.value, order++, fromTable = true)?.let { out += it }
                }
            } else {
                toTextShape(frame, order++, fromTable = true)?.let { out += it }
            }
        }
        return out
    }

    private fun toTextShape(block: String, order: Int, fromTable: Boolean): TextShape? {
        if (!block.contains("<a:t") && !block.contains("<p:txBody") && !block.contains("<a:txBody")) {
            return null
        }
        val xfrm = XFRM_RE.find(block)?.value
        val off = xfrm?.let { OFF_RE.find(it) }
        val ext = xfrm?.let { EXT_RE.find(it) }
        val texts = T_RE.findAll(block).map { it.groupValues[2] }.joinToString("")
        val sz = SZ_RE.findAll(block).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0
        return TextShape(
            x = off?.let { attrLong(it.groupValues[1], "x") } ?: 0L,
            y = off?.let { attrLong(it.groupValues[1], "y") } ?: 0L,
            cx = ext?.let { attrLong(it.groupValues[1], "cx") } ?: 0L,
            cy = ext?.let { attrLong(it.groupValues[1], "cy") } ?: 0L,
            fontSz = sz,
            text = unescape(texts),
            block = block,
            order = order,
            fromTable = fromTable
        )
    }

    private fun replaceFirstAtText(shapeXml: String, newText: String, clearRest: Boolean = true): String {
        val escaped = escape(newText)
        var already = false
        return T_RE.replace(shapeXml) { m ->
            val openAttrs = m.groupValues[1]
            if (!already) {
                already = true
                wrapT(openAttrs, escaped, newText)
            } else if (clearRest) {
                wrapT(openAttrs, "", "")
            } else {
                m.value
            }
        }
    }

    private fun wrapT(openAttrs: String, escaped: String, raw: String): String {
        val needsPreserve = raw.isNotEmpty() && (
            raw.first().isWhitespace() || raw.last().isWhitespace() || raw.contains('\n')
            )
        val attrs = when {
            openAttrs.contains("xml:space") -> openAttrs
            needsPreserve -> """ xml:space="preserve"$openAttrs"""
            else -> openAttrs
        }
        return "<a:t$attrs>$escaped</a:t>"
    }

    private fun replaceFirstOccurrence(haystack: String, needle: String, replacement: String): String {
        val i = haystack.indexOf(needle)
        if (i < 0) return haystack
        return haystack.substring(0, i) + replacement + haystack.substring(i + needle.length)
    }

    private fun uniquifyCNvPrIds(xml: String, offset: Int): String {
        return CNVPR_RE.replace(xml) { m ->
            val tag = m.value
            val id = attr(tag, "id")?.toIntOrNull() ?: return@replace tag
            tag.replaceFirst(Regex("""\bid="\d+""""), """id="${id + offset}"""")
        }
    }

    private fun upsertImageRel(relsXml: String, embedId: String, target: String): String {
        val rels = parseRelationships(relsXml)
        val has = rels.any { it.id == embedId }
        return if (has) {
            REL_RE.replace(relsXml) { m ->
                val attrs = m.groupValues[1]
                if (attr(attrs, "Id") != embedId) {
                    m.value
                } else {
                    val type = attr(attrs, "Type")
                        ?: "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
                    """<Relationship Id="$embedId" Type="$type" Target="$target"/>"""
                }
            }
        } else {
            val insert =
                """<Relationship Id="$embedId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="$target"/>"""
            if (relsXml.contains("</Relationships>")) {
                relsXml.replace("</Relationships>", "$insert</Relationships>")
            } else {
                emptyRels().replace("</Relationships>", "$insert</Relationships>")
            }
        }
    }

    private fun rewritePresentationRels(original: String, slideRels: String): String {
        val kept = REL_RE.findAll(original).mapNotNull { m ->
            val type = attr(m.groupValues[1], "Type").orEmpty()
            if (isSlideRel(type)) null else m.value
        }.joinToString("")
        val body = kept + slideRels
        val openTag = Regex("""<Relationships[^>]*>""").find(original)?.value
            ?: """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">"""
        val header = original.substringBefore(openTag, """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        return "$header$openTag$body</Relationships>"
    }

    private fun rewriteSldIdLst(presXml: String, sldIds: String): String {
        val replaced = SLD_LST_RE.replace(presXml) {
            "<p:sldIdLst>$sldIds</p:sldIdLst>"
        }
        return if (replaced != presXml) replaced
        else presXml.replace("</p:presentation>", "<p:sldIdLst>$sldIds</p:sldIdLst></p:presentation>")
    }

    private fun ensureJpegAndOverrides(typesXml: String, overrides: String): String {
        var xml = typesXml
        if (!xml.contains("Extension=\"jpeg\"") && !xml.contains("Extension=\"jpg\"")) {
            val insert =
                """<Default Extension="jpeg" ContentType="image/jpeg"/><Default Extension="jpg" ContentType="image/jpeg"/>"""
            xml = if (xml.contains("<Default ")) {
                xml.replaceFirst("<Default ", "$insert<Default ")
            } else {
                xml.replace("</Types>", "$insert</Types>")
            }
        }
        return xml.replace("</Types>", "$overrides</Types>")
    }

    private fun readSlideSize(presXml: String): Pair<Long, Long> {
        val m = SLD_SZ_RE.find(presXml) ?: return 12_192_000L to 6_858_000L
        val attrs = m.groupValues[1]
        val cx = attrLong(attrs, "cx").takeIf { it > 0 } ?: 12_192_000L
        val cy = attrLong(attrs, "cy").takeIf { it > 0 } ?: 6_858_000L
        return cx to cy
    }

    private fun relsForSlide(slidePath: String): String {
        val name = slidePath.substringAfterLast('/')
        val dir = slidePath.substringBeforeLast('/', missingDelimiterValue = "")
        return if (dir.isEmpty()) "_rels/$name.rels" else "$dir/_rels/$name.rels"
    }

    private fun existingSlideNumbers(parts: Map<String, ByteArray>): List<Int> =
        parts.keys.mapNotNull { key ->
            SLIDE_FILE_RE.matchEntire(key)?.groupValues?.get(1)?.toIntOrNull()
        }

    private fun maxRid(relsXml: String): Int =
        parseRelationships(relsXml).mapNotNull { rel ->
            rel.id.removePrefix("rId").toIntOrNull()
        }.maxOrNull() ?: 10

    private fun maxSldId(presXml: String): Int =
        parseSlideIds(presXml).map { it.id }.maxOrNull() ?: 256

    private fun relIdForTarget(relsXml: String, targetSuffix: String): String? {
        val want = targetSuffix.replace('\\', '/')
        return parseRelationships(relsXml).firstNotNullOfOrNull { rel ->
            val target = rel.target.replace('\\', '/')
            if (target == want || target.endsWith(want) || want.endsWith(target)) rel.id else null
        }
    }

    private fun sldIdForRid(presXml: String, rid: String): Int? =
        parseSlideIds(presXml).firstOrNull { it.rId == rid }?.id

    private fun emptyRels(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>"""

    private data class Rel(val id: String, val type: String, val target: String)
    private data class SlideId(val id: Int, val rId: String)

    private fun parseRelationships(xml: String): List<Rel> =
        REL_RE.findAll(xml).mapNotNull { m ->
            val attrs = m.groupValues[1]
            val id = attr(attrs, "Id") ?: return@mapNotNull null
            val type = attr(attrs, "Type").orEmpty()
            val target = attr(attrs, "Target") ?: return@mapNotNull null
            Rel(id, type, target)
        }.toList()

    private fun parseSlideIds(xml: String): List<SlideId> =
        SLD_ID_RE.findAll(xml).mapNotNull { m ->
            val attrs = m.groupValues[1]
            val id = attr(attrs, "id")?.toIntOrNull() ?: return@mapNotNull null
            val rId = attr(attrs, "r:id") ?: return@mapNotNull null
            SlideId(id, rId)
        }.toList()

    private fun isSlideRel(type: String): Boolean =
        type.endsWith("/slide") && !type.contains("slideMaster") && !type.contains("slideLayout")

    private fun attr(attrs: String, name: String): String? {
        val m = Regex("""\b${Regex.escape(name)}="([^"]*)"""").find(attrs) ?: return null
        return m.groupValues[1]
    }

    private fun attrLong(attrs: String, name: String): Long =
        attr(attrs, name)?.toLongOrNull() ?: 0L

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun unescape(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")

    private const val MIN_PHOTO_PCT = 8L
    private const val LOGO_MAX_PCT = 7L

    /** Self-closing or paired Relationship, attributes in any order, possibly multiline. */
    private val REL_RE = Regex("""<Relationship\b([^>]*?)(?:/>|>[\s\S]*?</Relationship>)""")
    private val SLD_ID_RE = Regex("""<p:sldId\b([^>]*?)/?>""")
    private val SLD_LST_RE = Regex("""<p:sldIdLst>[\s\S]*?</p:sldIdLst>""")
    private val SLD_SZ_RE = Regex("""<p:sldSz\b([^>]*)/?>""")
    private val PIC_RE = Regex("""<p:pic\b[^>]*>[\s\S]*?</p:pic>""")
    private val SP_RE = Regex("""<p:sp\b[^>]*>[\s\S]*?</p:sp>""")
    private val GF_RE = Regex("""<p:graphicFrame\b[^>]*>[\s\S]*?</p:graphicFrame>""")
    private val TC_RE = Regex("""<a:tc\b[^>]*>[\s\S]*?</a:tc>""")
    private val XFRM_RE = Regex("""<(?:a|p):xfrm\b[^>]*>[\s\S]*?</(?:a|p):xfrm>""")
    private val OFF_RE = Regex("""<a:off\b([^>]*)/?>""")
    private val EXT_RE = Regex("""<a:ext\b([^>]*)/?>""")
    private val EMBED_RE = Regex("""r:embed="([^"]+)"""")
    private val T_RE = Regex("""<a:t(\s[^>]*)?>([\s\S]*?)</a:t>""")
    private val SZ_RE = Regex("""\bsz="(\d+)"""")
    private val SLIDE_FILE_RE = Regex("""ppt/slides/slide(\d+)\.xml""")
    private val CNVPR_RE = Regex("""<p:cNvPr\b[^>]*>""")
}

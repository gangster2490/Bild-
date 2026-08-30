package com.rin.repairagent.data.export

import com.rin.repairagent.data.model.PhotoAnalysis
import com.rin.repairagent.data.model.ProjectPhoto
import com.rin.repairagent.data.model.RepairProject
import com.rin.repairagent.data.model.ResultLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.io.FileOutputStream

class PptxRinWriterTest {

    @Test
    fun clonesTemplateInsteadOfReplacingDesign() {
        val dir = File("build/test-pptx").apply { mkdirs() }
        val template = File(dir, "RIN_Template.pptx")
        writeSampleTemplate(template)
        assertTrue(PptxRinWriter.hasPresentationXml(template))

        val photos = (1..3).map { n ->
            val f = File(dir, "photo$n.jpg")
            f.writeBytes(MINI_JPEG)
            f
        }
        val steps = photos.mapIndexed { i, file ->
            val n = i + 1
            ExportStep(
                photo = ProjectPhoto(id = "p$n", localPath = file.absolutePath, photoNumber = n),
                analysis = PhotoAnalysis(
                    photoNumber = n,
                    visibleAction = "действие $n",
                    repairStage = "Шаг $n: тест",
                    tools = listOf("ключ 9 мм"),
                    beginnerInstruction = "Сделайте шаг $n.",
                    importantWarning = "Важно: не применяйте силу",
                    beginnerInstructionEn = "Do step $n.",
                    visibleActionEn = "action $n",
                    repairStageEn = "Step $n: test",
                    importantWarningEn = "Warning: do not force"
                ),
                instructionRu = "Сделайте шаг $n.",
                instructionEn = "Do step $n."
            )
        }
        val project = RepairProject(
            id = "t1",
            title = "Тестовый ремонт",
            productModel = "eGazelle",
            language = ResultLanguage.RU
        )
        val out = File(dir, "RIN_Repair_Instruction_RU.pptx")
        val check = PptxRinWriter.write(template, out, project, steps, "RU")
        assertTrue(check.errors.joinToString(), check.ok)
        assertTrue(check.slideSizeMatch)
        assertTrue(check.mastersPreserved)
        assertTrue(check.themePreserved)
        assertTrue(check.pictureFramesUnchanged)

        ZipFile(out).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue(names.contains("ppt/presentation.xml"))
            assertTrue(names.contains("ppt/slideMasters/slideMaster1.xml"))
            assertTrue(names.contains("ppt/theme/theme1.xml"))
            val pres = zip.getInputStream(zip.getEntry("ppt/presentation.xml")).bufferedReader().readText()
            assertTrue(pres.contains("cx=\"12192000\""))
            assertTrue(pres.contains("cy=\"6858000\""))
            val sldCount = Regex("""<p:sldId\b""").findAll(pres).count()
            // cover + 3 content slides
            assertEquals(4, sldCount)
            val slide1 = zip.getInputStream(zip.getEntry("ppt/slides/slide1.xml")).bufferedReader().readText()
            assertTrue(slide1.contains("Тестовый ремонт"))
            assertTrue(slide1.contains("eGAZELLE"))
            val slide2 = zip.getInputStream(zip.getEntry("ppt/slides/slide2.xml")).bufferedReader().readText()
            assertTrue(slide2.contains("a:off x=\"400000\" y=\"800000\""))
            assertTrue(slide2.contains("a:ext cx=\"5000000\" cy=\"4000000\""))
            assertTrue(slide2.contains("Сделайте шаг 1") || slide2.contains("Шаг 1"))
            assertTrue(slide2.contains("Инструменты: ключ 9 мм"))
            assertFalse(slide2.contains("Фото 1:"))
            assertTrue(slide2.contains("•") || slide2.contains("Сделайте шаг 1"))
        }
    }

    @Test
    fun fillsOfficeLikeTemplateWithShuffledAttrsAndAlternateContent() {
        val dir = File("build/test-pptx").apply { mkdirs() }
        val template = File(dir, "RIN_Template_Office.pptx")
        writeOfficeLikeTemplate(template)
        assertTrue(PptxRinWriter.hasPresentationXml(template))

        val photos = (1..3).map { n ->
            val f = File(dir, "office-photo$n.jpg")
            f.writeBytes(MINI_JPEG)
            f
        }
        val steps = photos.mapIndexed { i, file ->
            val n = i + 1
            ExportStep(
                photo = ProjectPhoto(id = "o$n", localPath = file.absolutePath, photoNumber = n),
                analysis = PhotoAnalysis(
                    photoNumber = n,
                    visibleAction = "действие $n",
                    repairStage = "Шаг $n: офис",
                    tools = listOf("ключ 9 мм"),
                    beginnerInstruction = "Сделайте шаг $n.",
                    importantWarning = "Важно: не применяйте силу",
                    beginnerInstructionEn = "Do step $n.",
                    visibleActionEn = "action $n",
                    repairStageEn = "Step $n: office",
                    importantWarningEn = "Warning: do not force"
                ),
                instructionRu = "Сделайте шаг $n.",
                instructionEn = "Do step $n."
            )
        }
        val project = RepairProject(
            id = "t2",
            title = "Офисный ремонт",
            productModel = "eGazelle",
            language = ResultLanguage.RU
        )
        val out = File(dir, "RIN_Repair_Instruction_Office_RU.pptx")
        val check = PptxRinWriter.write(template, out, project, steps, "RU")
        assertTrue(check.errors.joinToString(), check.ok)
        assertTrue(check.slideSizeMatch)
        assertTrue(check.mastersPreserved)
        assertTrue(check.themePreserved)
        assertTrue(check.pictureFramesUnchanged)

        ZipFile(out).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue(names.contains("ppt/viewProps.xml"))
            assertTrue(names.contains("ppt/presProps.xml"))
            assertTrue(names.contains("ppt/theme/theme1.xml"))
            assertTrue(names.contains("ppt/slides/slide3.xml"))
            assertTrue(names.contains("ppt/slides/slide4.xml"))
            val rels = zip.getInputStream(zip.getEntry("ppt/_rels/presentation.xml.rels"))
                .bufferedReader().readText()
            assertTrue(rels.contains("viewProps.xml"))
            assertTrue(rels.contains("presProps.xml"))
            assertTrue(rels.contains("theme/theme1.xml"))
            val pres = zip.getInputStream(zip.getEntry("ppt/presentation.xml")).bufferedReader().readText()
            assertTrue(pres.contains("cx=\"12192000\""))
            assertTrue(pres.contains("cy=\"6858000\""))
            assertEquals(4, Regex("""<p:sldId\b""").findAll(pres).count())
            assertTrue(pres.contains("""id="256"""") && pres.contains("""r:id="rId2""""))
            assertTrue(pres.contains("""id="257"""") && pres.contains("""r:id="rId3""""))
            val slide2 = zip.getInputStream(zip.getEntry("ppt/slides/slide2.xml")).bufferedReader().readText()
            assertTrue(slide2.contains("y=\"800000\"") && slide2.contains("x=\"400000\""))
            assertTrue(slide2.contains("cx=\"5000000\"") && slide2.contains("cy=\"4000000\""))
            assertTrue(slide2.contains("Сделайте шаг 1") || slide2.contains("Шаг 1"))
            assertTrue(slide2.contains("Инструменты: ключ 9 мм"))
            assertTrue(slide2.contains("Важно: не применяйте силу"))
            assertFalse(slide2.contains("Фото 1:"))
            assertTrue(slide2.contains("xml:space=\"preserve\"") || slide2.contains("Сделайте шаг 1"))
            val slide1 = zip.getInputStream(zip.getEntry("ppt/slides/slide1.xml")).bufferedReader().readText()
            assertTrue(slide1.contains("Офисный ремонт"))
            assertTrue(slide1.contains("eGAZELLE"))
            assertTrue(slide1.contains("Продукт: eGazelle"))
            assertFalse(slide1.contains("Продукт: SAMPLE"))
            assertTrue(slide1.contains("cx=\"800000\"") && slide1.contains("cy=\"500000\""))
            val slide3Rels = zip.getInputStream(zip.getEntry("ppt/slides/_rels/slide3.xml.rels"))
                .bufferedReader().readText()
            assertTrue(slide3Rels.contains("rin_step_2.jpg"))
            val slide3 = zip.getInputStream(zip.getEntry("ppt/slides/slide3.xml")).bufferedReader().readText()
            assertTrue(slide3.contains("id=\"10010\"") || slide3.contains("id=\"10002\""))
        }
    }

    private fun writeSampleTemplate(dest: File) {
        dest.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(dest)).use { zos ->
            fun put(path: String, body: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(body.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            fun putBytes(path: String, bytes: ByteArray) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
  <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
  <Override PartName="/ppt/slides/slide2.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
</Types>"""
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""
            )
            put(
                "ppt/presentation.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>
  <p:sldIdLst>
    <p:sldId id="256" r:id="rId2"/>
    <p:sldId id="257" r:id="rId3"/>
  </p:sldIdLst>
  <p:sldSz cx="12192000" cy="6858000"/>
  <p:notesSz cx="6858000" cy="9144000"/>
</p:presentation>"""
            )
            put(
                "ppt/_rels/presentation.xml.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>
</Relationships>"""
            )
            put("ppt/slideMasters/slideMaster1.xml", MASTER)
            put(
                "ppt/slideMasters/_rels/slideMaster1.xml.rels",
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
</Relationships>"""
            )
            put("ppt/slideLayouts/slideLayout1.xml", LAYOUT)
            put(
                "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>"""
            )
            put("ppt/theme/theme1.xml", THEME)
            put(
                "ppt/slides/slide1.xml",
                coverSlide()
            )
            put(
                "ppt/slides/_rels/slide1.xml.rels",
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
</Relationships>"""
            )
            put("ppt/slides/slide2.xml", contentSlide())
            put(
                "ppt/slides/_rels/slide2.xml.rels",
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rIdImg" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/placeholder.png"/>
</Relationships>"""
            )
            putBytes("ppt/media/placeholder.png", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        }
    }

    private fun writeOfficeLikeTemplate(dest: File) {
        dest.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(dest)).use { zos ->
            fun put(path: String, body: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(body.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            fun putBytes(path: String, bytes: ByteArray) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
  <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
  <Override PartName="/ppt/slides/slide2.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
  <Override PartName="/ppt/viewProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml"/>
  <Override PartName="/ppt/presProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presProps+xml"/>
</Types>"""
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""
            )
            put(
                "ppt/presentation.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldMasterIdLst><p:sldMasterId r:id="rId1" id="2147483648"/></p:sldMasterIdLst>
  <p:sldIdLst>
    <p:sldId r:id="rId2" id="256"/>
    <p:sldId r:id="rId3" id="257"/>
  </p:sldIdLst>
  <p:sldSz cy="6858000" cx="12192000" type="screen16x9"/>
  <p:notesSz cx="6858000" cy="9144000"/>
</p:presentation>"""
            )
            put(
                "ppt/_rels/presentation.xml.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml" Id="rId1"/>
  <Relationship Target="slides/slide1.xml" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Id="rId2"></Relationship>
  <Relationship Target="slides/slide2.xml" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Id="rId3"></Relationship>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>
  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/viewProps" Target="viewProps.xml"/>
  <Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/presProps" Target="presProps.xml"/>
</Relationships>"""
            )
            put("ppt/slideMasters/slideMaster1.xml", MASTER)
            put(
                "ppt/slideMasters/_rels/slideMaster1.xml.rels",
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
</Relationships>"""
            )
            put("ppt/slideLayouts/slideLayout1.xml", LAYOUT)
            put(
                "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>"""
            )
            put("ppt/theme/theme1.xml", THEME)
            put("ppt/viewProps.xml", """<?xml version="1.0"?><p:viewPr xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"/>""")
            put("ppt/presProps.xml", """<?xml version="1.0"?><p:presentationPr xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"/>""")
            put("ppt/slides/slide1.xml", officeCoverSlide())
            put(
                "ppt/slides/_rels/slide1.xml.rels",
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rIdLogo" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/logo.png"/>
</Relationships>"""
            )
            put("ppt/slides/slide2.xml", officeContentSlide())
            put(
                "ppt/slides/_rels/slide2.xml.rels",
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rIdImg" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/placeholder.png"/>
</Relationships>"""
            )
            putBytes("ppt/media/placeholder.png", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            putBytes("ppt/media/logo.png", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        }
    }

    private fun officeCoverSlide(): String = """<?xml version="1.0"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld><p:spTree>
    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
    <p:grpSpPr><a:xfrm><a:off y="0" x="0"/><a:ext cy="6858000" cx="12192000"/><a:chOff x="0" y="0"/><a:chExt cx="12192000" cy="6858000"/></a:xfrm></p:grpSpPr>
    ${textBoxPreserve(2, 400000, 400000, 8000000, 800000, 2800, "eGAZELLE")}
    ${textBoxPreserve(3, 400000, 1400000, 9000000, 1000000, 2000, "RIN Template")}
    ${textBoxPreserve(4, 400000, 2600000, 10000000, 400000, 1100, "Автор: Сервисная мастерская Дата: 01.01.2020 Продукт: SAMPLE")}
    <p:pic name="Logo">
      <p:nvPicPr><p:cNvPr id="9" name="Logo"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
      <p:blipFill><a:blip r:embed="rIdLogo"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
      <p:spPr><a:xfrm rot="0"><a:off y="200000" x="10500000"/><a:ext cy="500000" cx="800000"/></a:xfrm>
        <a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>
    </p:pic>
  </p:spTree></p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sld>"""

    private fun officeContentSlide(): String = """<?xml version="1.0"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
 xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006">
  <p:cSld><p:spTree>
    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
    <p:grpSpPr><a:xfrm><a:off y="0" x="0"/><a:ext cy="6858000" cx="12192000"/><a:chOff x="0" y="0"/><a:chExt cx="12192000" cy="6858000"/></a:xfrm></p:grpSpPr>
    <p:graphicFrame>
      <p:nvGraphicFramePr><p:cNvPr id="2" name="Title"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>
      <p:xfrm><a:off y="200000" x="400000"/><a:ext cy="600000" cx="7000000"/></p:xfrm>
      <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table">
        <a:tbl><a:tblGrid><a:gridCol w="7000000"/></a:tblGrid>
          <a:tr h="600000"><a:tc>
            <a:txBody><a:bodyPr/><a:lstStyle/>
              <a:p><a:r><a:rPr lang="ru-RU" sz="2200"/><a:t xml:space="preserve">Шаг 1: Подготовка</a:t></a:r></a:p>
            </a:txBody>
          </a:tc></a:tr>
        </a:tbl>
      </a:graphicData></a:graphic>
    </p:graphicFrame>
    ${textBoxPreserve(3, 5600000, 900000, 6000000, 400000, 1200, "Инструменты: ключ")}
    ${textBoxPreserve(4, 5600000, 1400000, 6000000, 2500000, 1400, "Описание шага")}
    ${textBoxPreserve(5, 400000, 5000000, 5000000, 500000, 1100, "Подпись под фото")}
    <mc:AlternateContent>
      <mc:Choice Requires="a14">
        <p:pic>
          <p:nvPicPr><p:cNvPr id="10" name="Photo"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
          <p:blipFill><a:blip r:embed="rIdImg"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
          <p:spPr><a:xfrm><a:off y="800000" x="400000"/><a:ext cy="4000000" cx="5000000"/></a:xfrm>
            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>
        </p:pic>
      </mc:Choice>
      <mc:Fallback>
        <p:pic>
          <p:nvPicPr><p:cNvPr id="10" name="Photo"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
          <p:blipFill><a:blip r:embed="rIdImg"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
          <p:spPr><a:xfrm><a:off y="800000" x="400000"/><a:ext cy="4000000" cx="5000000"/></a:xfrm>
            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>
        </p:pic>
      </mc:Fallback>
    </mc:AlternateContent>
  </p:spTree></p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sld>"""

    private fun textBoxPreserve(id: Int, x: Long, y: Long, cx: Long, cy: Long, sz: Int, text: String): String = """
    <p:sp>
      <p:nvSpPr><p:cNvPr id="$id" name="tb$id"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr>
      <p:spPr><a:xfrm><a:off y="$y" x="$x"/><a:ext cy="$cy" cx="$cx"/></a:xfrm>
        <a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>
      <p:txBody><a:bodyPr wrap="square"/><a:lstStyle/>
        <a:p><a:r><a:rPr lang="ru-RU" sz="$sz"/><a:t xml:space="preserve">$text</a:t></a:r></a:p>
      </p:txBody>
    </p:sp>
    """.trimIndent()

    private fun coverSlide(): String = """<?xml version="1.0"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld><p:spTree>
    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
    <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="12192000" cy="6858000"/><a:chOff x="0" y="0"/><a:chExt cx="12192000" cy="6858000"/></a:xfrm></p:grpSpPr>
    ${textBox(2, 400000, 400000, 8000000, 800000, 2800, "eGAZELLE")}
    ${textBox(3, 400000, 1400000, 9000000, 1000000, 2000, "RIN Template")}
  </p:spTree></p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sld>"""

    private fun contentSlide(): String = """<?xml version="1.0"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld><p:spTree>
    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
    <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="12192000" cy="6858000"/><a:chOff x="0" y="0"/><a:chExt cx="12192000" cy="6858000"/></a:xfrm></p:grpSpPr>
    ${textBox(2, 400000, 200000, 7000000, 600000, 2200, "Шаг 1: Подготовка")}
    ${textBox(3, 5600000, 900000, 6000000, 400000, 1200, "Инструменты: ключ")}
    ${textBox(4, 5600000, 1400000, 6000000, 2500000, 1400, "Описание шага")}
    ${textBox(5, 400000, 5000000, 5000000, 500000, 1100, "Подпись под фото")}
    <p:pic>
      <p:nvPicPr><p:cNvPr id="10" name="Photo"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
      <p:blipFill><a:blip r:embed="rIdImg"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
      <p:spPr><a:xfrm><a:off x="400000" y="800000"/><a:ext cx="5000000" cy="4000000"/></a:xfrm>
        <a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>
    </p:pic>
  </p:spTree></p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sld>"""

    private fun textBox(id: Int, x: Long, y: Long, cx: Long, cy: Long, sz: Int, text: String): String = """
    <p:sp>
      <p:nvSpPr><p:cNvPr id="$id" name="tb$id"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr>
      <p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
        <a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>
      <p:txBody><a:bodyPr wrap="square"/><a:lstStyle/>
        <a:p><a:r><a:rPr lang="ru-RU" sz="$sz"/><a:t>$text</a:t></a:r></a:p>
      </p:txBody>
    </p:sp>
    """.trimIndent()

    companion object {
        // 1x1 jpeg
        private val MINI_JPEG = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()
        )
        private const val MASTER = """<?xml version="1.0"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
  <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
  <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
</p:sldMaster>"""
        private const val LAYOUT = """<?xml version="1.0"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank">
  <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sldLayout>"""
        private const val THEME = """<?xml version="1.0"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="RIN">
  <a:themeElements>
    <a:clrScheme name="RIN">
      <a:dk1><a:srgbClr val="000000"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
      <a:dk2><a:srgbClr val="14212B"/></a:dk2><a:lt2><a:srgbClr val="E8EEF3"/></a:lt2>
      <a:accent1><a:srgbClr val="145A8C"/></a:accent1><a:accent2><a:srgbClr val="1F7A6C"/></a:accent2>
      <a:accent3><a:srgbClr val="C45C26"/></a:accent3><a:accent4><a:srgbClr val="5B6B7A"/></a:accent4>
      <a:accent5><a:srgbClr val="2F6B9A"/></a:accent5><a:accent6><a:srgbClr val="7A8B99"/></a:accent6>
      <a:hlink><a:srgbClr val="145A8C"/></a:hlink><a:folHlink><a:srgbClr val="1F7A6C"/></a:folHlink>
    </a:clrScheme>
    <a:fontScheme name="RIN"><a:majorFont><a:latin typeface="Calibri"/></a:majorFont><a:minorFont><a:latin typeface="Calibri"/></a:minorFont></a:fontScheme>
    <a:fmtScheme name="RIN">
      <a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst>
      <a:lnStyleLst><a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst>
      <a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>
      <a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst>
    </a:fmtScheme>
  </a:themeElements>
</a:theme>"""
    }
}

package com.rin.repairagent.data

import android.content.Context
import android.net.Uri
import com.rin.repairagent.data.local.AppStorage
import com.rin.repairagent.data.model.AiProvider
import com.rin.repairagent.data.model.AnalyzeResponse
import com.rin.repairagent.data.model.ApiKeyCheckResponse
import com.rin.repairagent.data.model.ExportResult
import com.rin.repairagent.data.model.ExportValidation
import com.rin.repairagent.data.model.PhotoAnalysis
import com.rin.repairagent.data.model.ProjectPhoto
import com.rin.repairagent.data.model.RepairProject
import com.rin.repairagent.data.model.ResultLanguage
import com.rin.repairagent.data.model.ReviewStatus
import com.rin.repairagent.data.model.TemplateInfo
import com.rin.repairagent.data.remote.ApiClientFactory
import com.rin.repairagent.data.security.ApiKeyVault
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

class RinRepository(
    context: Context,
    private val vault: ApiKeyVault = ApiKeyVault(context),
    private val storage: AppStorage = AppStorage(context)
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    val serverUrlFlow: Flow<String> = storage.serverUrlFlow
    val templateInfoFlow: Flow<TemplateInfo?> = storage.templateInfoFlow()

    fun hasApiKey(): Boolean = vault.hasKey()
    fun maskedKey(): String = vault.getMaskedKey()
    fun provider(): String = vault.getProvider()
    fun lastFour(): String = vault.getLastFour()

    fun saveApiKey(key: String, provider: AiProvider) {
        vault.saveKey(key.trim(), provider.name)
    }

    fun deleteApiKey() = vault.deleteKey()

    suspend fun setServerUrl(url: String) = storage.setServerUrl(url)
    suspend fun getServerUrl(): String = storage.getServerUrl()

    private suspend fun api() = ApiClientFactory.create(storage.getServerUrl())

    suspend fun checkConnection(): Result<String> = runCatching {
        val health = api().health()
        health["status"] ?: "ok"
    }

    suspend fun verifyApiKey(key: String, provider: AiProvider): ApiKeyCheckResponse {
        return api().checkKey(
            mapOf(
                "apiKey" to key.trim(),
                "provider" to provider.name
            )
        )
    }

    suspend fun importTemplate(uri: Uri, name: String): TemplateInfo =
        storage.importTemplate(uri, name)

    suspend fun deleteTemplate() = storage.deleteTemplate()
    suspend fun getTemplateInfo(): TemplateInfo? = storage.getTemplateInfo()
    fun templateFile(): File? = storage.templateFile()

    suspend fun listProjects(): List<RepairProject> = storage.listProjects()
    suspend fun loadProject(id: String): RepairProject? = storage.loadProject(id)
    suspend fun saveProject(project: RepairProject): RepairProject = storage.saveProject(project)
    suspend fun deleteProject(id: String) = storage.deleteProject(id)

    suspend fun createProject(
        title: String,
        productModel: String,
        serial: String,
        language: ResultLanguage
    ): RepairProject {
        val project = RepairProject(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            productModel = productModel.trim(),
            serialNumber = serial.trim(),
            language = language
        )
        return storage.saveProject(project)
    }

    fun createCameraTempFile(): File = storage.createCameraTempFile()

    suspend fun addPhotoFromUri(project: RepairProject, uri: Uri): RepairProject {
        val latest = storage.loadProject(project.id) ?: project
        val number = (latest.photos.maxOfOrNull { it.photoNumber } ?: 0) + 1
        val file = storage.importImageUri(latest.id, uri, number)
        val photo = ProjectPhoto(
            id = UUID.randomUUID().toString(),
            localPath = file.absolutePath,
            photoNumber = number,
            reviewStatus = ReviewStatus.NEEDS_REVIEW
        )
        return try {
            storage.updateProject(latest.id) { current ->
                val finalNumber = (current.photos.maxOfOrNull { it.photoNumber } ?: 0) + 1
                val finalPhoto = if (finalNumber == number) {
                    photo
                } else {
                    val renamed = storage.newPhotoFile(current.id, finalNumber)
                    file.copyTo(renamed, overwrite = true)
                    file.delete()
                    photo.copy(localPath = renamed.absolutePath, photoNumber = finalNumber)
                }
                current.copy(
                    photos = current.photos + finalPhoto,
                    reviewCompleted = false,
                    exportReady = false
                )
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    suspend fun addPhotoFromCamera(project: RepairProject, cameraFile: File): RepairProject {
        val latest = storage.loadProject(project.id) ?: project
        val number = (latest.photos.maxOfOrNull { it.photoNumber } ?: 0) + 1
        val file = storage.importCameraFile(latest.id, cameraFile, number)
        val photo = ProjectPhoto(
            id = UUID.randomUUID().toString(),
            localPath = file.absolutePath,
            photoNumber = number,
            reviewStatus = ReviewStatus.NEEDS_REVIEW
        )
        return try {
            storage.updateProject(latest.id) { current ->
                val finalNumber = (current.photos.maxOfOrNull { it.photoNumber } ?: 0) + 1
                val finalPhoto = if (finalNumber == number) {
                    photo
                } else {
                    val renamed = storage.newPhotoFile(current.id, finalNumber)
                    file.copyTo(renamed, overwrite = true)
                    file.delete()
                    photo.copy(localPath = renamed.absolutePath, photoNumber = finalNumber)
                }
                current.copy(
                    photos = current.photos + finalPhoto,
                    reviewCompleted = false,
                    exportReady = false
                )
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    suspend fun addPhotosFromZip(project: RepairProject, uri: Uri): RepairProject {
        val latest = storage.loadProject(project.id) ?: project
        val start = (latest.photos.maxOfOrNull { it.photoNumber } ?: 0) + 1
        val files = storage.importPhotosFromZip(latest.id, uri, start)
        return try {
            storage.updateProject(latest.id) { current ->
                var number = (current.photos.maxOfOrNull { it.photoNumber } ?: 0) + 1
                val added = files.map { file ->
                    var target = file
                    val expectedPrefix = "photo_%03d_".format(number)
                    if (!file.name.startsWith(expectedPrefix)) {
                        val renamed = storage.newPhotoFile(current.id, number)
                        file.copyTo(renamed, overwrite = true)
                        file.delete()
                        target = renamed
                    }
                    ProjectPhoto(
                        id = UUID.randomUUID().toString(),
                        localPath = target.absolutePath,
                        photoNumber = number++,
                        reviewStatus = ReviewStatus.NEEDS_REVIEW
                    )
                }
                current.copy(
                    photos = current.photos + added,
                    reviewCompleted = false,
                    exportReady = false
                )
            }
        } catch (e: Exception) {
            files.forEach { it.delete() }
            throw e
        }
    }

    suspend fun analyzePhoto(project: RepairProject, photoId: String): RepairProject {
        val apiKey = vault.unlockKey() ?: error("API-ключ не сохранён")
        val provider = vault.getProvider()
        val latest = storage.loadProject(project.id) ?: project
        val photo = latest.photos.firstOrNull { it.id == photoId }
            ?: error("Фотография не найдена в проекте")
        val file = File(photo.localPath)
        require(file.exists() && file.length() > 0L) { "Файл фотографии не найден или пустой" }

        val body = file.asRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("image", file.name, body)
        val text = { v: String -> v.toRequestBody("text/plain".toMediaType()) }

        val response: AnalyzeResponse = api().analyzePhoto(
            image = part,
            provider = text(provider),
            apiKey = text(apiKey),
            photoNumber = text(photo.photoNumber.toString()),
            projectTitle = text(latest.title),
            productModel = text(latest.productModel),
            language = text(latest.language.name)
        )

        val analysis = response.analysis.copy(photoNumber = photo.photoNumber)
        val status = when {
            analysis.needsManualReview || analysis.confidence < 0.55 -> ReviewStatus.UNCLEAR
            else -> ReviewStatus.NEEDS_REVIEW
        }

        return storage.updateProject(latest.id) { current ->
            val updatedPhotos = current.photos.map {
                if (it.id == photoId) {
                    it.copy(analysis = analysis, reviewStatus = status, confirmed = false)
                } else {
                    it
                }
            }
            current.copy(photos = updatedPhotos, reviewCompleted = false)
        }
    }

    suspend fun updatePhoto(project: RepairProject, photo: ProjectPhoto): RepairProject {
        return storage.updateProject(project.id) { latest ->
            latest.copy(photos = latest.photos.map { if (it.id == photo.id) photo else it })
        }
    }

    suspend fun deletePhoto(project: RepairProject, photoId: String): RepairProject {
        val latest = storage.loadProject(project.id) ?: project
        val target = latest.photos.firstOrNull { it.id == photoId }
        target?.let { File(it.localPath).delete() }
        return storage.updateProject(project.id) { current ->
            val remaining = current.photos
                .filterNot { it.id == photoId }
                .mapIndexed { index, p -> p.copy(photoNumber = index + 1) }
            current.copy(photos = remaining, reviewCompleted = false, exportReady = false)
        }
    }

    suspend fun reorderPhotos(project: RepairProject, from: Int, to: Int): RepairProject {
        return storage.updateProject(project.id) { latest ->
            if (from == to || from !in latest.photos.indices || to !in latest.photos.indices) {
                return@updateProject latest
            }
            val list = latest.photos.toMutableList()
            val item = list.removeAt(from)
            list.add(to, item)
            val renumbered = list.mapIndexed { index, p -> p.copy(photoNumber = index + 1) }
            latest.copy(photos = renumbered, reviewCompleted = false)
        }
    }

    suspend fun replacePhoto(project: RepairProject, photoId: String, uri: Uri): RepairProject {
        val latest = storage.loadProject(project.id) ?: project
        val photo = latest.photos.first { it.id == photoId }
        val file = storage.importImageUri(latest.id, uri, photo.photoNumber)
        return try {
            storage.updateProject(latest.id) { current ->
                val existing = current.photos.first { it.id == photoId }
                File(existing.localPath).delete()
                val updated = existing.copy(
                    localPath = file.absolutePath,
                    analysis = null,
                    confirmed = false,
                    reviewStatus = ReviewStatus.NEEDS_REVIEW,
                    userEditedInstruction = null
                )
                current.copy(
                    photos = current.photos.map { if (it.id == photoId) updated else it },
                    reviewCompleted = false,
                    exportReady = false
                )
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    fun validateForExport(project: RepairProject): ExportValidation {
        val errors = mutableListOf<String>()
        val loaded = project.photos.size
        val used = project.photos.count { it.reviewStatus != ReviewStatus.CAN_DELETE }
        val skipped = project.photos.count { it.reviewStatus == ReviewStatus.CAN_DELETE }
        val needing = project.photos.count {
            it.reviewStatus == ReviewStatus.NEEDS_REVIEW ||
                it.reviewStatus == ReviewStatus.UNCLEAR ||
                !it.confirmed
        }
        val paths = project.photos.map { it.localPath }
        val duplicates = paths.size - paths.toSet().size
        if (loaded == 0) errors += "Нет загруженных фотографий"
        if (used == 0) errors += "Нет фотографий для экспорта"
        if (needing > 0) errors += "Есть фотографии, требующие проверки"
        if (duplicates > 0) errors += "Обнаружены повторяющиеся фотографии"
        project.photos.filter { it.reviewStatus != ReviewStatus.CAN_DELETE }.forEach { p ->
            if (p.analysis == null) errors += "Фото ${p.photoNumber}: нет AI-описания"
            if (!File(p.localPath).exists()) errors += "Фото ${p.photoNumber}: файл отсутствует"
        }
        if (storage.templateFile() == null) errors += "RIN-шаблон не добавлен"

        return ExportValidation(
            loadedPhotos = loaded,
            usedPhotos = used,
            skippedPhotos = skipped,
            duplicatePhotos = duplicates,
            photosNeedingReview = needing,
            mappingErrors = if (errors.any { it.contains("описания") }) 1 else 0,
            powerpointErrors = 0,
            pdfErrors = 0,
            canExport = errors.isEmpty(),
            errors = errors.distinct()
        )
    }

    suspend fun export(project: RepairProject): ExportResult {
        val validation = validateForExport(project)
        if (!validation.canExport) {
            return ExportResult(projectId = project.id, validation = validation)
        }
        val template = storage.templateFile() ?: error("Шаблон не найден")
        val usable = project.photos
            .filter { it.reviewStatus != ReviewStatus.CAN_DELETE }
            .sortedBy { it.photoNumber }

        val templatePart = MultipartBody.Part.createFormData(
            "template",
            template.name,
            template.asRequestBody("application/vnd.openxmlformats-officedocument.presentationml.presentation".toMediaType())
        )
        val photoParts = usable.map { photo ->
            val file = File(photo.localPath)
            MultipartBody.Part.createFormData(
                "photos",
                file.name,
                file.asRequestBody("image/jpeg".toMediaType())
            )
        }

        // API key is intentionally NOT included in export payload or documents.
        val stepsJson = usable.joinToString(prefix = "[", postfix = "]") { photo ->
            val analysis = photo.analysis!!
            val instruction = photo.userEditedInstruction ?: analysis.beginnerInstruction
            val objects = analysis.visibleObjects.joinToString(prefix = "[", postfix = "]") {
                "\"${it.jsonEsc()}\""
            }
            val tools = analysis.tools.joinToString(prefix = "[", postfix = "]") {
                "\"${it.jsonEsc()}\""
            }
            """
            {
              "photoNumber": ${photo.photoNumber},
              "visibleObjects": $objects,
              "visibleAction": "${analysis.visibleAction.jsonEsc()}",
              "visibleActionEn": "${analysis.visibleActionEn.ifBlank { analysis.visibleAction }.jsonEsc()}",
              "repairStage": "${analysis.repairStage.jsonEsc()}",
              "repairStageEn": "${analysis.repairStageEn.ifBlank { analysis.repairStage }.jsonEsc()}",
              "tools": $tools,
              "beginnerInstruction": "${instruction.jsonEsc()}",
              "beginnerInstructionEn": "${analysis.beginnerInstructionEn.ifBlank { instruction }.jsonEsc()}",
              "importantWarning": "${analysis.importantWarning.jsonEsc()}",
              "importantWarningEn": "${analysis.importantWarningEn.ifBlank { analysis.importantWarning }.jsonEsc()}",
              "confidence": ${analysis.confidence},
              "needsManualReview": ${analysis.needsManualReview},
              "fileName": "${File(photo.localPath).name.jsonEsc()}"
            }
            """.trimIndent()
        }
        val payloadJson = """
        {
          "projectId": "${project.id.jsonEsc()}",
          "title": "${project.title.jsonEsc()}",
          "productModel": "${project.productModel.jsonEsc()}",
          "serialNumber": "${project.serialNumber.jsonEsc()}",
          "language": "${project.language.name}",
          "steps": $stepsJson
        }
        """.trimIndent()
        val payloadBody = payloadJson.toRequestBody("application/json".toMediaType())

        val result = api().exportDocuments(templatePart, photoParts, payloadBody)

        // Persist exported files locally when backend returns absolute paths via relativePath content download
        // Backend returns file names; Android downloads via separate endpoints if needed.
        return result.copy(validation = validation.copy(canExport = result.validation.errors.isEmpty() && validation.canExport))
    }

    fun listExportedFiles(projectId: String): List<File> {
        val dir = File(storage.exportsDirectory(), projectId)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.sortedBy { it.name }?.toList() ?: emptyList()
    }

    suspend fun downloadExportFiles(projectId: String, files: List<com.rin.repairagent.data.model.ExportedFile>) {
        // Files are served by backend at /exports/:projectId/:name — download into local exports dir
        val base = storage.getServerUrl().trimEnd('/')
        val destDir = File(storage.exportsDirectory(), projectId).also { it.mkdirs() }
        val client = okhttp3.OkHttpClient()
        files.forEach { file ->
            val url = "$base/exports/$projectId/${file.name}"
            val request = okhttp3.Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@forEach
                val body = response.body ?: return@forEach
                File(destDir, file.name).outputStream().use { out ->
                    body.byteStream().copyTo(out)
                }
            }
        }
    }
}

private fun String.jsonEsc(): String =
    replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

package com.rin.repairagent.data.local

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rin.repairagent.data.model.RepairProject
import com.rin.repairagent.data.model.TemplateInfo
import com.rin.repairagent.util.ImageNormalizer
import com.rin.repairagent.util.TemplateFileHelper
import com.rin.repairagent.util.UriIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.zip.ZipInputStream

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "rin_settings")

class AppStorage(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val serverUrlKey = stringPreferencesKey("server_url")
    private val templateMetaKey = stringPreferencesKey("template_meta")

    /** Serializes project.json read-modify-write only (not heavy file I/O). */
    private val projectMutex = Mutex()

    val serverUrlFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[serverUrlKey] ?: DEFAULT_SERVER
    }

    suspend fun getServerUrl(): String = serverUrlFlow.first()

    suspend fun setServerUrl(url: String) {
        context.settingsDataStore.edit { it[serverUrlKey] = url.trim().trimEnd('/') }
    }

    private val rootDir: File
        get() = context.filesDir.resolve("rin").also { it.mkdirs() }

    private val templatesDir: File
        get() = rootDir.resolve("templates").also { it.mkdirs() }

    private val projectsDir: File
        get() = rootDir.resolve("projects").also { it.mkdirs() }

    private val exportsDir: File
        get() = rootDir.resolve("exports").also { it.mkdirs() }

    fun exportsDirectory(): File = exportsDir

    suspend fun getTemplateInfo(): TemplateInfo? {
        val raw = context.settingsDataStore.data.first()[templateMetaKey] ?: return null
        return runCatching { json.decodeFromString<TemplateInfo>(raw) }.getOrNull()
    }

    fun templateInfoFlow(): Flow<TemplateInfo?> = context.settingsDataStore.data.map { prefs ->
        prefs[templateMetaKey]?.let { runCatching { json.decodeFromString<TemplateInfo>(it) }.getOrNull() }
    }

    suspend fun importTemplate(uri: Uri, displayName: String): TemplateInfo = withContext(Dispatchers.IO) {
        UriIO.tryTakeReadPermission(context, uri)

        // 1) Always copy to cache first (content:// streams are unreliable later)
        val cacheCopy = UriIO.copyUriToCache(context, uri, "rin_template", ".bin")
        try {
            require(cacheCopy.length() > 0L) { "Загруженный шаблон пустой" }

            // If user uploaded a ZIP that wraps a PPTX, unwrap it before detection
            val working = File(
                cacheCopy.parentFile,
                "rin_template_unwrapped_${UUID.randomUUID()}.pptx"
            )
            val unwrapped = TemplateFileHelper.extractNestedPptx(cacheCopy, working)
            val source = if (unwrapped) working else cacheCopy
            val preferredName = when {
                unwrapped && displayName.isBlank() -> "rin_template.pptx"
                unwrapped && !displayName.lowercase().endsWith(".pptx") ->
                    displayName.substringBeforeLast('.', displayName) + ".pptx"
                else -> displayName
            }

            val detected = TemplateFileHelper.detect(context, uri, source, preferredName)

            // 2) Store under stable name; PPTX always as rin_template.pptx for exporters
            val dest = templatesDir.resolve("rin_template.${detected.extension}")
            // Remove previous template variants so templateFile() cannot pick a stale file
            templatesDir.listFiles()?.forEach { existing ->
                if (existing.isFile && existing.name.startsWith("rin_template")) {
                    existing.delete()
                }
            }

            source.copyTo(dest, overwrite = true)
            require(dest.exists() && dest.length() > 0L) { "Не удалось сохранить шаблон" }

            if (detected.extension == "pptx") {
                require(TemplateFileHelper.looksLikePptxZip(dest)) {
                    "Сохранённый файл повреждён или это не PPTX"
                }
            }

            val info = TemplateInfo(
                fileName = detected.displayName,
                storedPath = dest.absolutePath,
                sizeBytes = dest.length(),
                addedAt = System.currentTimeMillis()
            )
            context.settingsDataStore.edit {
                it[templateMetaKey] = json.encodeToString(info)
            }
            info
        } finally {
            cacheCopy.delete()
            cacheCopy.parentFile?.listFiles()
                ?.filter { it.name.startsWith("rin_template_unwrapped_") }
                ?.forEach { it.delete() }
        }
    }

    suspend fun deleteTemplate() = withContext(Dispatchers.IO) {
        templatesDir.listFiles()?.forEach { it.delete() }
        context.settingsDataStore.edit { it.remove(templateMetaKey) }
    }

    fun templateFile(): File? {
        // Prefer validated pptx
        val pptx = templatesDir.resolve("rin_template.pptx")
        if (pptx.exists() && pptx.length() > 0L) return pptx
        // Fall back to any stored template; if ZIP is actually pptx content, still usable
        val candidates = templatesDir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.name.startsWith("rin_template") }
            .orEmpty()
        candidates.firstOrNull { TemplateFileHelper.looksLikePptxZip(it) }?.let { return it }
        return candidates.firstOrNull()
    }

    suspend fun listProjects(): List<RepairProject> = withContext(Dispatchers.IO) {
        projectsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val meta = File(dir, "project.json")
                if (!meta.exists()) return@mapNotNull null
                runCatching { json.decodeFromString<RepairProject>(meta.readText()) }.getOrNull()
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    suspend fun loadProject(id: String): RepairProject? = withContext(Dispatchers.IO) {
        val meta = File(projectsDir, "$id/project.json")
        if (!meta.exists()) return@withContext null
        runCatching { json.decodeFromString<RepairProject>(meta.readText()) }.getOrNull()
    }

    suspend fun saveProject(project: RepairProject): RepairProject = projectMutex.withLock {
        withContext(Dispatchers.IO) {
            writeProjectUnlocked(project)
        }
    }

    /**
     * Atomically reload project.json, apply [transform], save.
     * Keep [transform] lightweight — do heavy URI/ZIP I/O outside this call.
     */
    suspend fun updateProject(projectId: String, transform: (RepairProject) -> RepairProject): RepairProject =
        projectMutex.withLock {
            withContext(Dispatchers.IO) {
                val meta = File(projectsDir, "$projectId/project.json")
                require(meta.exists()) { "Проект не найден" }
                val current = json.decodeFromString<RepairProject>(meta.readText())
                val updated = transform(current).copy(updatedAt = System.currentTimeMillis())
                meta.writeText(json.encodeToString(updated))
                updated
            }
        }

    private fun writeProjectUnlocked(project: RepairProject): RepairProject {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        val dir = File(projectsDir, updated.id).also { it.mkdirs() }
        File(dir, "project.json").writeText(json.encodeToString(updated))
        return updated
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        File(projectsDir, id).deleteRecursively()
        File(exportsDir, id).deleteRecursively()
    }

    fun projectPhotosDir(projectId: String): File =
        File(projectsDir, "$projectId/photos").also { it.mkdirs() }

    fun newPhotoFile(projectId: String, photoNumber: Int): File {
        val unique = UUID.randomUUID().toString().take(8)
        return File(projectPhotosDir(projectId), "photo_%03d_%s.jpg".format(photoNumber, unique))
    }

    /** Normalize a content URI image into a JPEG under the project photos folder. */
    suspend fun importImageUri(projectId: String, uri: Uri, photoNumber: Int): File =
        withContext(Dispatchers.IO) {
            UriIO.tryTakeReadPermission(context, uri)
            val dest = newPhotoFile(projectId, photoNumber)
            ImageNormalizer.saveUriAsJpeg(context, uri, dest)
        }

    suspend fun importCameraFile(projectId: String, cameraFile: File, photoNumber: Int): File =
        withContext(Dispatchers.IO) {
            require(cameraFile.exists() && cameraFile.length() > 0L) {
                "Снимок с камеры не получен. Попробуйте ещё раз."
            }
            val dest = newPhotoFile(projectId, photoNumber)
            ImageNormalizer.saveFileAsJpeg(cameraFile, dest)
        }

    /**
     * Copy ZIP to local cache first (ContentResolver streams break ZipInputStream),
     * then extract and normalize each image.
     */
    suspend fun importPhotosFromZip(projectId: String, uri: Uri, startNumber: Int): List<File> =
        withContext(Dispatchers.IO) {
            UriIO.tryTakeReadPermission(context, uri)
            val zipLocal = UriIO.copyUriToCache(context, uri, "photos", ".zip")
            try {
                require(zipLocal.length() > 0L) { "ZIP-файл пустой" }
                val entries = mutableListOf<Pair<String, ByteArray>>()
                FileInputStream(zipLocal).use { fis ->
                    ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val rawName = entry.name.replace('\\', '/')
                            val baseName = rawName.substringAfterLast('/')
                            val lower = rawName.lowercase()
                            val isImage = !entry.isDirectory &&
                                baseName.isNotBlank() &&
                                !lower.contains("__macosx") &&
                                !baseName.startsWith(".") &&
                                IMAGE_EXT.any { lower.endsWith(it) }
                            val safe = !rawName.contains("..") && !rawName.startsWith("/")
                            if (isImage && safe) {
                                val bytes = zis.readBytes()
                                if (bytes.isNotEmpty()) entries += baseName to bytes
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                require(entries.isNotEmpty()) {
                    "В ZIP не найдены изображения (JPEG, PNG, WebP, HEIC)"
                }
                entries.sortBy { it.first.lowercase() }

                var number = startNumber
                val out = mutableListOf<File>()
                val errors = mutableListOf<String>()
                for ((name, bytes) in entries) {
                    try {
                        val dest = newPhotoFile(projectId, number)
                        ImageNormalizer.saveBytesAsJpeg(bytes, dest)
                        out += dest
                        number++
                    } catch (e: Exception) {
                        errors += "$name: ${e.message ?: "ошибка"}"
                    }
                }
                require(out.isNotEmpty()) {
                    "Не удалось импортировать изображения из ZIP. " + errors.joinToString("; ")
                }
                out
            } finally {
                zipLocal.delete()
            }
        }

    fun createCameraTempFile(): File {
        val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
        return File.createTempFile("capture_", ".jpg", dir)
    }

    companion object {
        const val DEFAULT_SERVER = "http://10.0.2.2:3000"
        val ALLOWED_TEMPLATE_EXT = setOf("pptx", "zip", "json", "pdf")
        private val IMAGE_EXT = listOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif")
    }
}

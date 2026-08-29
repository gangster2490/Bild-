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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
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
        val ext = displayName.substringAfterLast('.', "pptx").lowercase()
        require(ext in ALLOWED_TEMPLATE_EXT) { "Недопустимый тип файла: .$ext" }
        val dest = templatesDir.resolve("rin_template.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        } ?: error("Не удалось открыть файл шаблона")

        val info = TemplateInfo(
            fileName = displayName,
            storedPath = dest.absolutePath,
            sizeBytes = dest.length(),
            addedAt = System.currentTimeMillis()
        )
        context.settingsDataStore.edit {
            it[templateMetaKey] = json.encodeToString(info)
        }
        info
    }

    suspend fun deleteTemplate() = withContext(Dispatchers.IO) {
        templatesDir.listFiles()?.forEach { it.delete() }
        context.settingsDataStore.edit { it.remove(templateMetaKey) }
    }

    fun templateFile(): File? {
        val pptx = templatesDir.resolve("rin_template.pptx")
        if (pptx.exists()) return pptx
        return templatesDir.listFiles()?.firstOrNull()
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

    suspend fun saveProject(project: RepairProject): RepairProject = withContext(Dispatchers.IO) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        val dir = File(projectsDir, updated.id).also { it.mkdirs() }
        File(dir, "project.json").writeText(json.encodeToString(updated))
        updated
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        File(projectsDir, id).deleteRecursively()
        File(exportsDir, id).deleteRecursively()
    }

    fun projectPhotosDir(projectId: String): File =
        File(projectsDir, "$projectId/photos").also { it.mkdirs() }

    suspend fun savePhotoFromUri(projectId: String, uri: Uri, photoNumber: Int): File =
        withContext(Dispatchers.IO) {
            val dest = File(projectPhotosDir(projectId), "photo_%03d.jpg".format(photoNumber))
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: error("Не удалось сохранить фотографию")
            dest
        }

    suspend fun savePhotoFromCameraFile(projectId: String, cameraFile: File, photoNumber: Int): File =
        withContext(Dispatchers.IO) {
            val dest = File(projectPhotosDir(projectId), "photo_%03d.jpg".format(photoNumber))
            cameraFile.copyTo(dest, overwrite = true)
            dest
        }

    suspend fun importPhotosFromZip(projectId: String, uri: Uri, startNumber: Int): List<File> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<File>()
            var number = startNumber
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        if (!entry.isDirectory && name.matches(Regex(".*\\.(jpg|jpeg|png|webp)$")) && !name.contains("__macosx")) {
                            val dest = File(projectPhotosDir(projectId), "photo_%03d.jpg".format(number))
                            FileOutputStream(dest).use { fos -> zis.copyTo(fos) }
                            out += dest
                            number++
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: error("Не удалось открыть ZIP")
            out
        }

    fun createCameraTempFile(): File {
        val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
        return File(dir, "capture_${UUID.randomUUID()}.jpg")
    }

    companion object {
        const val DEFAULT_SERVER = "http://10.0.2.2:3000"
        val ALLOWED_TEMPLATE_EXT = setOf("pptx", "zip", "json", "pdf")
    }
}

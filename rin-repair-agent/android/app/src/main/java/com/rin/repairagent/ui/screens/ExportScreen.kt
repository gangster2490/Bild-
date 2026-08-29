package com.rin.repairagent.ui.screens

import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.rin.repairagent.data.RinRepository
import com.rin.repairagent.data.model.ExportResult
import com.rin.repairagent.data.model.ExportValidation
import com.rin.repairagent.data.model.RepairProject
import com.rin.repairagent.data.model.ResultLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    repository: RinRepository,
    projectId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var project by remember { mutableStateOf<RepairProject?>(null) }
    var validation by remember { mutableStateOf<ExportValidation?>(null) }
    var result by remember { mutableStateOf<ExportResult?>(null) }
    var localFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshFiles() {
        localFiles = repository.listExportedFiles(projectId)
    }

    LaunchedEffect(projectId) {
        val p = repository.loadProject(projectId)
        project = p
        if (p != null) validation = repository.validateForExport(p)
        refreshFiles()
    }

    fun shareFile(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mime = when (file.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться: ${file.name}"))
        }.onFailure {
            error = "Не удалось поделиться файлом: ${it.message}"
        }
    }

    fun openFile(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mime = when (file.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Открыть: ${file.name}"))
        }.onFailure {
            error = "Не удалось открыть файл: ${it.message}"
        }
    }

    suspend fun copyToDownloads(files: List<File>): Int = withContext(Dispatchers.IO) {
        var copied = 0
        for (file in files) {
            if (!file.exists() || file.length() == 0L) continue
            val mime = when (file.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                else -> "application/octet-stream"
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: continue
                    resolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    copied++
                } else {
                    // No broad storage permission: use app-visible external Downloads folder
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: continue
                    dir.mkdirs()
                    file.copyTo(File(dir, file.name), overwrite = true)
                    copied++
                }
            } catch (_: Exception) {
                // continue with other files
            }
        }
        copied
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Экспорт") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val p = project
            if (p != null) {
                Text(p.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Язык документов: ${
                        when (p.language) {
                            ResultLanguage.RU -> "русский"
                            ResultLanguage.EN -> "английский"
                            ResultLanguage.BOTH -> "русский и английский"
                        }
                    }"
                )
            }

            Text("Проверка перед экспортом", style = MaterialTheme.typography.headlineMedium)
            validation?.let { v ->
                Text("Загружено фотографий: ${v.loadedPhotos}")
                Text("Использовано фотографий: ${v.usedPhotos}")
                Text("Пропущено фотографий: ${v.skippedPhotos}")
                Text("Повторяющихся фотографий: ${v.duplicatePhotos}")
                Text("Фотографий требуют проверки: ${v.photosNeedingReview}")
                Text("Ошибок соответствия: ${v.mappingErrors}")
                Text("Ошибок PowerPoint: ${v.powerpointErrors}")
                Text("Ошибок PDF: ${v.pdfErrors}")

                if (v.errors.isNotEmpty()) {
                    Text("Экспорт остановлен:", color = MaterialTheme.colorScheme.error)
                    v.errors.forEach {
                        Text("• $it", color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "Вернитесь к проверке, подтвердите описания и повторите экспорт.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Проверка пройдена. Можно создать документы.",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } ?: Text("Загрузка проверки…")

            OutlinedButton(
                onClick = {
                    val current = project ?: return@OutlinedButton
                    validation = repository.validateForExport(current)
                    error = null
                    status = "Проверка обновлена"
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && project != null
            ) { Text("Обновить проверку") }

            Button(
                onClick = {
                    val current = project ?: return@Button
                    scope.launch {
                        loading = true
                        error = null
                        status = "Генерация документов на телефоне…"
                        try {
                            val latest = repository.loadProject(projectId) ?: current
                            project = latest
                            val v = repository.validateForExport(latest)
                            validation = v
                            if (!v.canExport) {
                                error = "Исправьте ошибки перед экспортом"
                                status = null
                                return@launch
                            }
                            val exportResult = repository.export(latest)
                            result = exportResult
                            validation = exportResult.validation.copy(
                                loadedPhotos = v.loadedPhotos,
                                usedPhotos = v.usedPhotos,
                                skippedPhotos = v.skippedPhotos,
                                duplicatePhotos = v.duplicatePhotos,
                                photosNeedingReview = v.photosNeedingReview,
                                mappingErrors = v.mappingErrors
                            )
                            refreshFiles()
                            if (exportResult.validation.errors.isNotEmpty() ||
                                exportResult.files.isEmpty()
                            ) {
                                error = exportResult.validation.errors.joinToString("\n")
                                    .ifBlank { "Ошибка генерации документов" }
                                status = null
                            } else {
                                repository.saveProject(
                                    latest.copy(exportReady = true, reviewCompleted = true)
                                )
                                project = repository.loadProject(projectId)
                                val expected = when (latest.language) {
                                    ResultLanguage.RU, ResultLanguage.EN -> 2
                                    ResultLanguage.BOTH -> 4
                                }
                                status = "Готово: ${exportResult.files.size} из $expected файлов"
                                // Best-effort copy into Downloads so Files app can see them
                                val n = copyToDownloads(localFiles.ifEmpty {
                                    repository.listExportedFiles(projectId)
                                })
                                if (n > 0) {
                                    status = "${status}\nСкопировано в «Загрузки»: $n"
                                }
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Ошибка экспорта"
                            status = null
                            refreshFiles()
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = validation?.canExport == true && !loading
            ) { Text("Создать PowerPoint и PDF") }

            if (loading) {
                CircularProgressIndicator()
                Text("Генерация PPTX и PDF…")
            }

            status?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (localFiles.isNotEmpty()) {
                Text("Созданные файлы", style = MaterialTheme.typography.titleLarge)
                localFiles.forEach { f ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("• ${f.name} (${(f.length() / 1024).coerceAtLeast(1)} КБ)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { openFile(f) }) { Text("Открыть") }
                            OutlinedButton(onClick = { shareFile(f) }) { Text("Поделиться") }
                        }
                    }
                }
            } else if (result == null && !loading) {
                Text(
                    "Файлы ещё не созданы.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Имена файлов: RIN_Repair_Instruction_RU.pptx / EN.pptx / RU.pdf / EN.pdf",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

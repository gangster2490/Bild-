package com.rin.repairagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.rin.repairagent.data.RinRepository
import com.rin.repairagent.data.model.ExportResult
import com.rin.repairagent.data.model.ExportValidation
import com.rin.repairagent.data.model.RepairProject
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    repository: RinRepository,
    projectId: String,
    onBack: () -> Unit
) {
    var project by remember { mutableStateOf<RepairProject?>(null) }
    var validation by remember { mutableStateOf<ExportValidation?>(null) }
    var result by remember { mutableStateOf<ExportResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(projectId) {
        val p = repository.loadProject(projectId)
        project = p
        if (p != null) validation = repository.validateForExport(p)
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
                }
            }

            Button(
                onClick = {
                    val p = project ?: return@Button
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            val v = repository.validateForExport(p)
                            validation = v
                            if (!v.canExport) {
                                error = "Исправьте ошибки перед экспортом"
                                return@launch
                            }
                            val exportResult = repository.export(p)
                            result = exportResult
                            validation = exportResult.validation.copy(
                                loadedPhotos = v.loadedPhotos,
                                usedPhotos = v.usedPhotos,
                                skippedPhotos = v.skippedPhotos,
                                duplicatePhotos = v.duplicatePhotos,
                                photosNeedingReview = v.photosNeedingReview
                            )
                            if (exportResult.validation.errors.isNotEmpty() ||
                                exportResult.files.isEmpty()
                            ) {
                                error = exportResult.validation.errors.joinToString("\n")
                                    .ifBlank { "Ошибка генерации документов" }
                            } else {
                                repository.saveProject(p.copy(exportReady = true, reviewCompleted = true))
                            }
                        } catch (e: Exception) {
                            error = e.message
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
                Text("Генерация документов на телефоне…")
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            result?.files?.forEach { file ->
                Text("✓ ${file.name}")
            }

            val localFiles = remember(result, projectId) { repository.listExportedFiles(projectId) }
            if (localFiles.isNotEmpty()) {
                Text("Сохранено локально:", style = MaterialTheme.typography.titleLarge)
                localFiles.forEach { f ->
                    Text("• ${f.name} (${f.length() / 1024} КБ)")
                }
            }

            Text(
                "Файлы: RIN_Repair_Instruction_RU.pptx / EN.pptx / RU.pdf / EN.pdf",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

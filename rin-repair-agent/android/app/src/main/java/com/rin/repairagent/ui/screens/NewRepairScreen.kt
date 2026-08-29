package com.rin.repairagent.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.rin.repairagent.data.RinRepository
import com.rin.repairagent.data.model.RepairProject
import com.rin.repairagent.data.model.ResultLanguage
import com.rin.repairagent.util.UriIO
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRepairScreen(
    repository: RinRepository,
    onBack: () -> Unit,
    onOpenReview: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(ResultLanguage.BOTH) }
    var project by remember { mutableStateOf<RepairProject?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    suspend fun ensureProjectReady(): RepairProject {
        project?.id?.let { id ->
            repository.loadProject(id)?.let { loaded ->
                project = loaded
                return loaded
            }
        }
        if (title.isBlank() || model.isBlank()) {
            error("Укажите название ремонта и изделие/модель")
        }
        val created = repository.createProject(title, model, serial, language)
        project = created
        return created
    }

    fun analyzeNewPhotos(projectId: String, newIds: List<String>) {
        if (newIds.isEmpty()) return
        scope.launch {
            analyzing = true
            try {
                for (id in newIds) {
                    status = "AI анализирует фото…"
                    val current = repository.analyzePhoto(
                        repository.loadProject(projectId) ?: return@launch,
                        id
                    )
                    project = current
                    val photo = current.photos.first { it.id == id }
                    val a = photo.analysis
                    val warning = a?.importantWarning?.takeIf { it.isNotBlank() }
                    status = buildString {
                        append("Фото ${photo.photoNumber}\n")
                        append("Описание: ${a?.beginnerInstruction.orEmpty()}\n")
                        append("Уверенность: ${((a?.confidence ?: 0.0) * 100).toInt()}%")
                        if (!warning.isNullOrBlank()) append("\nПредупреждение: $warning")
                        if (a?.needsManualReview == true || (a?.confidence ?: 0.0) < 0.55) {
                            append("\n⚠ Требует проверки")
                        }
                    }
                }
            } catch (e: Exception) {
                // Photos stay saved locally even if AI fails
                project = repository.loadProject(projectId) ?: project
                error = "Фотографии сохранены. Анализ можно повторить позже: ${e.message}"
            } finally {
                analyzing = false
            }
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach { UriIO.tryTakeReadPermission(context, it) }
        scope.launch {
            importing = true
            error = null
            try {
                var current = ensureProjectReady()
                val ids = mutableListOf<String>()
                uris.forEach { uri ->
                    status = "Сохранение фотографии…"
                    val before = current.photos.map { it.id }.toSet()
                    current = repository.addPhotoFromUri(current, uri)
                    project = current
                    ids += current.photos.map { it.id }.filterNot { it in before }
                }
                status = "Загружено фото: ${ids.size}"
                analyzeNewPhotos(current.id, ids)
            } catch (e: Exception) {
                error = e.message ?: "Не удалось сохранить фотографии"
                project = project?.id?.let { repository.loadProject(it) } ?: project
            } finally {
                importing = false
            }
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> importUris(uris) }

    // Fallback gallery picker for devices where Photo Picker fails
    val galleryGetContent = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> importUris(uris) }

    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        UriIO.tryTakeReadPermission(context, uri)
        scope.launch {
            importing = true
            error = null
            try {
                var current = ensureProjectReady()
                status = "Импорт ZIP…"
                val before = current.photos.map { it.id }.toSet()
                current = repository.addPhotosFromZip(current, uri)
                project = current
                val ids = current.photos.map { it.id }.filterNot { it in before }
                if (ids.isEmpty()) {
                    error = "В ZIP не найдены подходящие изображения"
                } else {
                    status = "Из ZIP загружено фото: ${ids.size}"
                    analyzeNewPhotos(current.id, ids)
                }
            } catch (e: Exception) {
                error = e.message ?: "Не удалось импортировать ZIP"
                project = project?.id?.let { repository.loadProject(it) } ?: project
            } finally {
                importing = false
            }
        }
    }

    val zipGetContent = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        UriIO.tryTakeReadPermission(context, uri)
        scope.launch {
            importing = true
            error = null
            try {
                var current = ensureProjectReady()
                status = "Импорт ZIP…"
                val before = current.photos.map { it.id }.toSet()
                current = repository.addPhotosFromZip(current, uri)
                project = current
                val ids = current.photos.map { it.id }.filterNot { it in before }
                status = "Из ZIP загружено фото: ${ids.size}"
                analyzeNewPhotos(current.id, ids)
            } catch (e: Exception) {
                error = e.message ?: "Не удалось импортировать ZIP"
                project = project?.id?.let { repository.loadProject(it) } ?: project
            } finally {
                importing = false
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = cameraFile
        if (!success || file == null) {
            error = "Съёмка отменена"
            return@rememberLauncherForActivityResult
        }
        if (!file.exists() || file.length() == 0L) {
            error = "Файл снимка пустой. Попробуйте ещё раз."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            importing = true
            error = null
            try {
                var current = ensureProjectReady()
                status = "Сохранение снимка…"
                val before = current.photos.map { it.id }.toSet()
                current = repository.addPhotoFromCamera(current, file)
                project = current
                val ids = current.photos.map { it.id }.filterNot { it in before }
                status = "Снимок сохранён"
                analyzeNewPhotos(current.id, ids)
            } catch (e: Exception) {
                error = e.message ?: "Не удалось сохранить снимок"
                project = project?.id?.let { repository.loadProject(it) } ?: project
            } finally {
                importing = false
                file.delete()
            }
        }
    }

    fun launchCamera() {
        val file = repository.createCameraTempFile()
        cameraFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        takePicture.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            error = "Нужно разрешение камеры"
            return@rememberLauncherForActivityResult
        }
        launchCamera()
    }

    val busy = analyzing || importing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый ремонт") },
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
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название ремонта") },
                modifier = Modifier.fillMaxWidth(),
                enabled = project == null
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Изделие или модель") },
                modifier = Modifier.fillMaxWidth(),
                enabled = project == null
            )
            OutlinedTextField(
                value = serial,
                onValueChange = { serial = it },
                label = { Text("Серийный номер (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = project == null
            )

            Text("Язык результата")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ResultLanguage.RU to "Русский",
                    ResultLanguage.EN to "English",
                    ResultLanguage.BOTH to "Оба"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = language == value,
                        onClick = { if (project == null) language = value },
                        label = { Text(label) }
                    )
                }
            }

            Text("Добавить фотографии", style = MaterialTheme.typography.titleLarge)

            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) launchCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text("Сделать фото камерой") }

            Button(
                onClick = {
                    galleryPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text("Выбрать из галереи") }

            OutlinedButton(
                onClick = { galleryGetContent.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text("Галерея (альтернативно)") }

            OutlinedButton(
                onClick = { zipPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text("Загрузить ZIP") }

            OutlinedButton(
                onClick = { zipGetContent.launch("application/zip") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text("ZIP (альтернативно)") }

            project?.let { p ->
                Text("Фотографий: ${p.photos.size}")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(p.photos.sortedBy { it.photoNumber }, key = { it.id }) { photo ->
                        Column(modifier = Modifier.width(120.dp)) {
                            AsyncImage(
                                model = File(photo.localPath),
                                contentDescription = "Фото ${photo.photoNumber}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp),
                                contentScale = ContentScale.Crop
                            )
                            Text("#${photo.photoNumber}")
                            photo.analysis?.let { a ->
                                Text(
                                    "${(a.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } ?: Text("ожидание…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (busy) {
                CircularProgressIndicator()
                Text(
                    if (importing) "Сохранение фотографий…"
                    else "Анализ фотографий… Данные сохраняются локально."
                )
            }

            status?.let { Text(it) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            project?.let { p ->
                Button(
                    onClick = { onOpenReview(p.id) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = p.photos.isNotEmpty() && !busy
                ) { Text("Перейти к проверке") }
            }
        }
    }
}

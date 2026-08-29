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
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun ensureProject(onReady: suspend (RepairProject) -> Unit) {
        scope.launch {
            try {
                error = null
                val current = project ?: run {
                    if (title.isBlank() || model.isBlank()) {
                        error = "Укажите название ремонта и изделие/модель"
                        return@launch
                    }
                    repository.createProject(title, model, serial, language).also { project = it }
                }
                onReady(current)
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    fun afterPhotoAdded(updated: RepairProject, newIds: List<String>) {
        project = updated
        scope.launch {
            analyzing = true
            try {
                var current = updated
                for (id in newIds) {
                    status = "AI анализирует фото…"
                    current = repository.analyzePhoto(current, id)
                    project = current
                    val photo = current.photos.first { it.id == id }
                    val a = photo.analysis
                    status = "Фото ${photo.photoNumber}: ${a?.visibleAction ?: ""}\n" +
                        "Уверенность: ${((a?.confidence ?: 0.0) * 100).toInt()}%\n" +
                        (a?.beginnerInstruction ?: "")
                    if (a?.needsManualReview == true || (a?.confidence ?: 0.0) < 0.55) {
                        status = (status ?: "") + "\n⚠ Требует проверки"
                    }
                }
            } catch (e: Exception) {
                error = "Анализ сохранён локально. Ошибка сети/AI: ${e.message}"
            } finally {
                analyzing = false
            }
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        ensureProject { p ->
            var current = p
            val ids = mutableListOf<String>()
            uris.forEach { uri ->
                val before = current.photos.map { it.id }.toSet()
                current = repository.addPhotoFromUri(current, uri)
                ids += current.photos.map { it.id }.filterNot { it in before }
            }
            afterPhotoAdded(current, ids)
        }
    }

    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ensureProject { p ->
            val before = p.photos.map { it.id }.toSet()
            val updated = repository.addPhotosFromZip(p, uri)
            val ids = updated.photos.map { it.id }.filterNot { it in before }
            afterPhotoAdded(updated, ids)
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = cameraFile
        if (!success || file == null || !file.exists()) return@rememberLauncherForActivityResult
        ensureProject { p ->
            val before = p.photos.map { it.id }.toSet()
            val updated = repository.addPhotoFromCamera(p, file)
            val ids = updated.photos.map { it.id }.filterNot { it in before }
            afterPhotoAdded(updated, ids)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            error = "Нужно разрешение камеры"
            return@rememberLauncherForActivityResult
        }
        val file = repository.createCameraTempFile()
        cameraFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        takePicture.launch(uri)
    }

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
                    if (granted) {
                        val file = repository.createCameraTempFile()
                        cameraFile = file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        takePicture.launch(uri)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !analyzing
            ) { Text("Сделать фото камерой") }

            Button(
                onClick = {
                    galleryPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !analyzing
            ) { Text("Выбрать из галереи") }

            OutlinedButton(
                onClick = { zipPicker.launch(arrayOf("application/zip", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !analyzing
            ) { Text("Загрузить ZIP") }

            project?.let { p ->
                Text("Фотографий: ${p.photos.size}")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(p.photos, key = { it.id }) { photo ->
                        Column(modifier = Modifier.width(120.dp)) {
                            AsyncImage(
                                model = File(photo.localPath),
                                contentDescription = null,
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
                            }
                        }
                    }
                }
            }

            if (analyzing) {
                CircularProgressIndicator()
                Text("Анализ фотографий… Данные сохраняются локально.")
            }

            status?.let { Text(it) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            project?.let { p ->
                Button(
                    onClick = { onOpenReview(p.id) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = p.photos.isNotEmpty() && !analyzing
                ) { Text("Перейти к проверке") }
            }
        }
    }
}

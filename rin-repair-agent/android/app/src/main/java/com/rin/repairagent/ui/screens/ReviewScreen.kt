package com.rin.repairagent.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rin.repairagent.data.RinRepository
import com.rin.repairagent.data.model.RepairProject
import com.rin.repairagent.data.model.ReviewStatus
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    repository: RinRepository,
    projectId: String,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    var project by remember { mutableStateOf<RepairProject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var replaceTargetId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(projectId) {
        project = repository.loadProject(projectId)
    }

    val addPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null || project == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                var p = repository.addPhotoFromUri(project!!, uri)
                val newId = p.photos.last().id
                p = repository.analyzePhoto(p, newId)
                project = p
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    val replacePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val target = replaceTargetId
        replaceTargetId = null
        if (uri == null || target == null || project == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                var p = repository.replacePhoto(project!!, target, uri)
                p = repository.analyzePhoto(p, target)
                project = p
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Проверка описаний") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        val p = project
        if (p == null) {
            Text("Загрузка…", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(p.title, style = MaterialTheme.typography.headlineMedium)
                Text("${p.productModel} · фото: ${p.photos.size}")
                Text("Итоговый документ нельзя создать до завершения проверки.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            itemsIndexed(p.photos, key = { _, photo -> photo.id }) { index, photo ->
                val analysis = photo.analysis
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Фото №${photo.photoNumber}", style = MaterialTheme.typography.titleLarge)
                    AsyncImage(
                        model = File(photo.localPath),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text("Что видно: ${analysis?.visibleObjects?.joinToString().orEmpty()}")
                    Text("Действие: ${analysis?.visibleAction.orEmpty()}")
                    Text("Этап: ${analysis?.repairStage.orEmpty()}")
                    Text("Инструмент: ${analysis?.tools?.joinToString().orEmpty()}")
                    Text("Предупреждение: ${analysis?.importantWarning.orEmpty()}")
                    Text("Уверенность AI: ${((analysis?.confidence ?: 0.0) * 100).toInt()}%")

                    var edited by remember(photo.id, photo.userEditedInstruction, analysis?.beginnerInstruction) {
                        mutableStateOf(photo.userEditedInstruction ?: analysis?.beginnerInstruction.orEmpty())
                    }
                    OutlinedTextField(
                        value = edited,
                        onValueChange = { edited = it },
                        label = { Text("Подробное описание") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )

                    Text("Статус проверки")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        statusChip("Проверено", photo.reviewStatus == ReviewStatus.CHECKED) {
                            scope.launch {
                                project = repository.updatePhoto(
                                    p,
                                    photo.copy(
                                        reviewStatus = ReviewStatus.CHECKED,
                                        confirmed = true,
                                        userEditedInstruction = edited
                                    )
                                )
                            }
                        }
                        statusChip("Требует проверки", photo.reviewStatus == ReviewStatus.NEEDS_REVIEW) {
                            scope.launch {
                                project = repository.updatePhoto(
                                    p,
                                    photo.copy(reviewStatus = ReviewStatus.NEEDS_REVIEW, confirmed = false)
                                )
                            }
                        }
                        statusChip("Неясное действие", photo.reviewStatus == ReviewStatus.UNCLEAR) {
                            scope.launch {
                                project = repository.updatePhoto(
                                    p,
                                    photo.copy(reviewStatus = ReviewStatus.UNCLEAR, confirmed = false)
                                )
                            }
                        }
                        statusChip("Можно удалить", photo.reviewStatus == ReviewStatus.CAN_DELETE) {
                            scope.launch {
                                project = repository.updatePhoto(
                                    p,
                                    photo.copy(reviewStatus = ReviewStatus.CAN_DELETE, confirmed = false)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = {
                            scope.launch { project = repository.reorderPhotos(p, index, (index - 1).coerceAtLeast(0)) }
                        }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                        IconButton(onClick = {
                            scope.launch {
                                project = repository.reorderPhotos(p, index, (index + 1).coerceAtMost(p.photos.lastIndex))
                            }
                        }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    project = repository.analyzePhoto(p, photo.id)
                                } catch (e: Exception) {
                                    error = e.message
                                } finally {
                                    busy = false
                                }
                            }
                        }, enabled = !busy) { Text("Повторить анализ") }

                        OutlinedButton(onClick = {
                            replaceTargetId = photo.id
                            replacePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) { Text("Заменить") }

                        OutlinedButton(onClick = {
                            scope.launch { project = repository.deletePhoto(p, photo.id) }
                        }) { Text("Удалить") }

                        Button(onClick = {
                            scope.launch {
                                project = repository.updatePhoto(
                                    p,
                                    photo.copy(
                                        userEditedInstruction = edited,
                                        confirmed = true,
                                        reviewStatus = ReviewStatus.CHECKED
                                    )
                                )
                            }
                        }) { Text("Подтвердить") }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        addPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                ) { Text("Добавить фотографию") }

                val validation = repository.validateForExport(p)
                Text(
                    "К экспорту: ${validation.usedPhotos}, требуют проверки: ${validation.photosNeedingReview}"
                )
                Button(
                    onClick = {
                        scope.launch {
                            repository.saveProject(p.copy(reviewCompleted = validation.canExport))
                            onExport()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                ) { Text("К проверке перед экспортом") }
            }
        }
    }
}

@Composable
private fun statusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

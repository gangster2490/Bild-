package com.rin.repairagent.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rin.repairagent.data.RinRepository
import com.rin.repairagent.util.UriIO
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(repository: RinRepository, onBack: () -> Unit) {
    val template by repository.templateInfoFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    fun importUri(uri: Uri) {
        UriIO.tryTakeReadPermission(context, uri)
        scope.launch {
            loading = true
            error = null
            message = null
            try {
                val name = UriIO.displayName(context, uri, "")
                val info = repository.importTemplate(uri, name)
                message = "Шаблон сохранён: ${info.fileName} (${info.sizeBytes / 1024} КБ)"
            } catch (e: Exception) {
                error = e.message ?: "Ошибка загрузки шаблона"
            } finally {
                loading = false
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) importUri(uri)
    }

    fun pickTemplate() {
        openDocument.launch(
            arrayOf(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint",
                "application/octet-stream",
                "*/*"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RIN-шаблон") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Загрузите RIN Template.pptx через ACTION_OPEN_DOCUMENT. " +
                    "Принимаются .pptx, .ppt и файлы с MIME application/octet-stream. " +
                    "Настоящий PowerPoint определяется по ppt/presentation.xml.",
                style = MaterialTheme.typography.bodyLarge
            )

            if (template == null) {
                Text(
                    "Сначала загрузите RIN-шаблон PowerPoint.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleLarge
                )
                Button(
                    onClick = { pickTemplate() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) { Text("Загрузить RIN-шаблон") }
            } else {
                Text("Имя шаблона: ${template!!.fileName}", style = MaterialTheme.typography.titleLarge)
                Text("Размер: ${template!!.sizeBytes / 1024} КБ")
                Button(
                    onClick = { pickTemplate() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) { Text("Заменить шаблон") }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) { Text("Удалить шаблон") }
            }

            if (loading) {
                CircularProgressIndicator()
                Text("Копирование через ContentResolver и проверка ppt/presentation.xml…")
            }

            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить шаблон?") },
            text = { Text("Шаблон будет удалён из внутреннего хранилища приложения. Продолжить?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        repository.deleteTemplate()
                        message = "Шаблон удалён"
                        error = null
                    }
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            }
        )
    }
}

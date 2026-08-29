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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.rin.repairagent.data.RinRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: RinRepository,
    onBack: () -> Unit,
    onChangeKey: () -> Unit
) {
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Работа без сервера", style = MaterialTheme.typography.titleLarge)
            Text(
                "Приложение вызывает OpenAI или Gemini напрямую и создаёт PowerPoint/PDF на телефоне. " +
                    "Адрес сервера генерации не нужен."
            )

            Text("API-ключ: ${repository.maskedKey()}")
            Text("Провайдер: ${repository.provider()}")

            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            val msg = repository.checkProviderConnection().getOrThrow()
                            status = "Подключение к провайдеру OK: $msg"
                            error = null
                        } catch (e: Exception) {
                            error = "Нет подключения к AI-провайдеру: ${e.message}"
                            status = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Проверить подключение") }

            Button(onClick = onChangeKey, modifier = Modifier.fillMaxWidth()) {
                Text("Изменить ключ")
            }
            OutlinedButton(
                onClick = {
                    repository.deleteApiKey()
                    status = "Ключ удалён"
                    onChangeKey()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Удалить ключ") }

            status?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

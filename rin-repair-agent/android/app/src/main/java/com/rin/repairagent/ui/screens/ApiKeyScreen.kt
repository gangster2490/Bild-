package com.rin.repairagent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rin.repairagent.data.RinRepository
import com.rin.repairagent.data.model.AiProvider
import kotlinx.coroutines.launch

@Composable
fun ApiKeyScreen(
    repository: RinRepository,
    onDone: () -> Unit
) {
    var key by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(AiProvider.OPENAI) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hasExisting = remember { repository.hasApiKey() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFD9E6F0), Color(0xFFF3F6F9), Color(0xFFE4EFEA))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text("RIN Repair Agent", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Введите API-ключ",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                "Ключ сохраняется на телефоне через Android Keystore и не включается в APK.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
            )

            if (hasExisting) {
                Text(
                    "Текущий ключ: ${repository.maskedKey()} (${repository.provider()})",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Text("Выберите провайдера: OpenAI или Gemini")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = provider == AiProvider.OPENAI,
                    onClick = { provider = AiProvider.OPENAI; verified = false },
                    label = { Text("OpenAI") }
                )
                FilterChip(
                    selected = provider == AiProvider.GEMINI,
                    onClick = { provider = AiProvider.GEMINI; verified = false },
                    label = { Text("Gemini") }
                )
            }

            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    verified = false
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API-ключ") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.secondary)
            }

            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        message = null
                        try {
                            if (key.isBlank()) {
                                error = "Введите API-ключ"
                            } else {
                                val result = repository.verifyApiKey(key, provider)
                                if (result.ok) {
                                    verified = true
                                    message = "Ключ проверен успешно"
                                } else {
                                    verified = false
                                    error = result.message.ifBlank {
                                        "Неверный API-ключ или провайдер недоступен"
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            verified = false
                            error = "Не удалось проверить ключ. Проверьте адрес сервера и интернет.\n${e.message ?: ""}"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) { Text("Проверить ключ") }

            Button(
                onClick = {
                    if (!verified && key.isNotBlank()) {
                        error = "Сначала проверьте ключ"
                        return@Button
                    }
                    if (key.isBlank() && hasExisting) {
                        onDone()
                        return@Button
                    }
                    repository.saveApiKey(key, provider)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && (verified || (hasExisting && key.isBlank()))
            ) { Text("Сохранить") }

            if (hasExisting) {
                OutlinedButton(
                    onClick = {
                        repository.deleteApiKey()
                        key = ""
                        verified = false
                        message = "Ключ удалён"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Удалить ключ") }
            }
        }
    }
}

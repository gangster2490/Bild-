package com.rin.repairagent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rin.repairagent.data.RinRepository

@Composable
fun HomeScreen(
    repository: RinRepository,
    onNewRepair: () -> Unit,
    onTemplate: () -> Unit,
    onProjects: () -> Unit,
    onInstructions: () -> Unit,
    onKnowledge: () -> Unit,
    onSettings: () -> Unit
) {
    val template by repository.templateInfoFlow.collectAsState(initial = null)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFC9DCE9), Color(0xFFF3F6F9), Color(0xFFD7E8E2))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            Text("RIN Repair Agent", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Сфотографируйте ремонт — AI создаст пошаговую инструкцию.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            if (template == null) {
                Text(
                    "Сначала добавьте RIN-шаблон PowerPoint.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleLarge
                )
            } else {
                Text(
                    "Шаблон: ${template!!.fileName}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onNewRepair,
                modifier = Modifier.fillMaxWidth(),
                enabled = template != null
            ) { Text("Новый ремонт") }

            Button(onClick = onTemplate, modifier = Modifier.fillMaxWidth()) {
                Text(if (template == null) "Загрузить RIN-шаблон" else "Заменить шаблон")
            }
            OutlinedButton(onClick = onProjects, modifier = Modifier.fillMaxWidth()) {
                Text("Мои проекты")
            }
            OutlinedButton(onClick = onInstructions, modifier = Modifier.fillMaxWidth()) {
                Text("Готовые инструкции")
            }
            OutlinedButton(onClick = onKnowledge, modifier = Modifier.fillMaxWidth()) {
                Text("База знаний")
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Настройки")
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Ключ: ${repository.maskedKey()}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

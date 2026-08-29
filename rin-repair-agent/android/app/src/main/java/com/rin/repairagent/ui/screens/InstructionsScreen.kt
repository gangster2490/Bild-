package com.rin.repairagent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rin.repairagent.data.RinRepository
import com.rin.repairagent.data.model.RepairProject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(
    repository: RinRepository,
    onBack: () -> Unit,
    onOpenExport: (String) -> Unit
) {
    var projects by remember { mutableStateOf<List<RepairProject>>(emptyList()) }
    LaunchedEffect(Unit) {
        projects = repository.listProjects().filter { it.exportReady || it.reviewCompleted }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Готовые инструкции") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (projects.isEmpty()) {
                item {
                    Text(
                        "Готовых инструкций пока нет. Завершите проверку и экспорт проекта.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            items(projects, key = { it.id }) { project ->
                val files = repository.listExportedFiles(project.id)
                ListItem(
                    headlineContent = { Text(project.title) },
                    supportingContent = {
                        Text(
                            if (files.isEmpty()) "Можно экспортировать"
                            else files.joinToString { it.name }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenExport(project.id) }
                )
            }
        }
    }
}

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    repository: RinRepository,
    onBack: () -> Unit,
    onOpen: (String) -> Unit
) {
    var projects by remember { mutableStateOf<List<RepairProject>>(emptyList()) }
    LaunchedEffect(Unit) {
        projects = repository.listProjects()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои проекты") },
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
                item { Text("Пока нет проектов. Создайте новый ремонт.", modifier = Modifier.padding(16.dp)) }
            }
            items(projects, key = { it.id }) { project ->
                val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(Date(project.updatedAt))
                ListItem(
                    headlineContent = { Text(project.title) },
                    supportingContent = {
                        Text("${project.productModel} · ${project.photos.size} фото · $date")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(project.id) }
                )
            }
        }
    }
}

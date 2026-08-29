package com.rin.repairagent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rin.repairagent.data.RinRepository
import com.rin.repairagent.data.knowledge.KnowledgeBase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    repository: RinRepository,
    onBack: () -> Unit
) {
    val topics = remember { repository.knowledgeTopics() }
    var selected by remember { mutableStateOf<KnowledgeBase.Topic?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selected == null) "База знаний"
                        else selected!!.title
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selected != null) selected = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (selected == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Ремонт колясок и детских автокресел",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        "Справочник узлов, инструментов и правил безопасности. " +
                            "AI использует эти материалы при анализе фотографий.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (topics.isEmpty()) {
                    item {
                        Text(
                            "База знаний не найдена в приложении.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                items(topics, key = { it.id }) { topic ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = topic }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(topic.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            topic.body.lineSequence().firstOrNull().orEmpty().take(120),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    selected!!.body,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

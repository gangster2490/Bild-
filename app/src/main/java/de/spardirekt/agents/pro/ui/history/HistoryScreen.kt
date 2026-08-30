package de.spardirekt.agents.pro.ui.history

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import de.spardirekt.agents.pro.VeoPromptProApp
import de.spardirekt.agents.pro.data.db.ProjectEntity
import de.spardirekt.agents.pro.model.ProjectStatus
import de.spardirekt.agents.pro.ui.components.AppHeader
import de.spardirekt.agents.pro.ui.components.GradientHeading
import de.spardirekt.agents.pro.ui.components.NavyCard
import de.spardirekt.agents.pro.ui.theme.LocalVppType
import de.spardirekt.agents.pro.ui.theme.VppColors
import de.spardirekt.agents.pro.ui.theme.VppDimens
import de.spardirekt.agents.pro.ui.theme.VppShapes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VeoPromptProApp.instance.projectRepository
    val projects = repo.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: String) {
        viewModelScope.launch {
            de.spardirekt.agents.pro.storage.ImageStore.deleteProject(getApplication(), id)
            repo.delete(id)
        }
    }
}

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenResult: (String) -> Unit,
    onContinueProject: (String) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val type = LocalVppType.current
    val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(VppColors.backgroundLight, VppColors.backgroundGlow.copy(alpha = 0.5f), VppColors.backgroundLight)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = VppDimens.screenPadding)
                .padding(top = 12.dp)
        ) {
            AppHeader(onNewProject = onOpenCreate, onHistory = null, onMenu = onOpenSettings)
            Spacer(Modifier.height(18.dp))
            GradientHeading("История")
            Spacer(Modifier.height(6.dp))
            Text(
                "Сохранённые проекты и готовые промпты",
                style = type.secondary.copy(color = VppColors.textMutedDark)
            )
            Spacer(Modifier.height(16.dp))
            if (projects.isEmpty()) {
                NavyCard {
                    Text(
                        "Пока нет проектов",
                        style = type.cardTitle.copy(color = VppColors.textLight)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Создайте первый VEO Prompt на вкладке «Создать».",
                        style = type.secondary.copy(color = VppColors.textMuted)
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(projects, key = { it.id }) { project ->
                        HistoryItem(
                            project = project,
                            dateText = fmt.format(Date(project.updatedAt)),
                            onClick = {
                                if (project.status == ProjectStatus.Ready.name && project.veoPrompt.isNotBlank()) {
                                    onOpenResult(project.id)
                                } else {
                                    onContinueProject(project.id)
                                }
                            },
                            onDelete = { viewModel.delete(project.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    project: ProjectEntity,
    dateText: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val type = LocalVppType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VppShapes.cardShape)
            .background(VppColors.cardNavy)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (project.thumbnailUri.isNotBlank()) {
            AsyncImage(
                model = Uri.parse(project.thumbnailUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VppColors.cardInset)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                project.title.ifBlank { "Без названия" },
                style = type.cardTitle.copy(color = VppColors.textLight),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(dateText, style = type.secondary.copy(color = VppColors.textMuted, fontSize = 12.sp))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniBadge(project.voiceLanguage)
                MiniBadge(project.creativeMode)
                MiniBadge(project.status)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Удалить", tint = VppColors.textMuted)
        }
    }
}

@Composable
private fun MiniBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VppColors.cardInset)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, color = VppColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

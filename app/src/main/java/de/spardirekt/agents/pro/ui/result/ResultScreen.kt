package de.spardirekt.agents.pro.ui.result

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.spardirekt.agents.pro.VeoPromptProApp
import de.spardirekt.agents.pro.data.db.ProjectEntity
import de.spardirekt.agents.pro.ui.components.AppHeader
import de.spardirekt.agents.pro.ui.components.GradientButton
import de.spardirekt.agents.pro.ui.components.GradientHeading
import de.spardirekt.agents.pro.ui.components.HeaderSquareButton
import de.spardirekt.agents.pro.ui.components.NavyCard
import de.spardirekt.agents.pro.ui.components.OutlinedActionButton
import de.spardirekt.agents.pro.ui.theme.LocalVppType
import de.spardirekt.agents.pro.ui.theme.VppColors
import de.spardirekt.agents.pro.ui.theme.VppDimens
import de.spardirekt.agents.pro.ui.theme.VppShapes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ResultViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VeoPromptProApp.instance.projectRepository

    fun observe(projectId: String): StateFlow<ProjectEntity?> =
        repo.observe(projectId).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun hashtags(entity: ProjectEntity): List<String> =
        ResultComposition.hashtags(entity, repo.parseHashtags(entity))

    fun veoPrompt(entity: ProjectEntity): String =
        ResultComposition.veoPrompt(entity, repo.parseHashtags(entity))

    fun voiceover(entity: ProjectEntity): String = ResultComposition.voiceover(entity)

    fun title(entity: ProjectEntity): String = ResultComposition.title(entity)

    fun fullPackage(entity: ProjectEntity): String =
        ResultComposition.fullPackage(entity, repo.parseHashtags(entity))
}

@Composable
fun ResultScreen(
    projectId: String,
    viewModel: ResultViewModel,
    onBackToCreate: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val project by viewModel.observe(projectId).collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val entity = project

    val veoPrompt = remember(entity?.id, entity?.veoPrompt, entity?.voiceover, entity?.title, entity?.hashtagsJson) {
        entity?.let { viewModel.veoPrompt(it) }.orEmpty()
    }
    val voiceover = remember(entity?.id, entity?.voiceover, entity?.veoPrompt) {
        entity?.let { viewModel.voiceover(it) }.orEmpty()
    }
    val title = remember(entity?.id, entity?.title, entity?.veoPrompt) {
        entity?.let { viewModel.title(it) }.orEmpty()
    }
    val hashtags = remember(entity?.id, entity?.hashtagsJson, entity?.veoPrompt) {
        entity?.let { viewModel.hashtags(it) }.orEmpty()
    }
    val voiceLabel = remember(entity?.voiceLanguage) {
        entity?.let { ResultComposition.voiceLabel(it) }.orEmpty()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        VppColors.backgroundLight,
                        VppColors.backgroundGlow.copy(alpha = 0.5f),
                        VppColors.backgroundLight
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VppDimens.screenPadding)
                .padding(top = 12.dp, bottom = 40.dp)
        ) {
            AppHeader(
                onNewProject = onBackToCreate,
                trailing = {
                    HeaderSquareButton(onClick = {
                        if (veoPrompt.isNotBlank()) shareText(context, veoPrompt)
                    }) {
                        Icon(Icons.Outlined.Share, null, tint = VppColors.textLight, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    HeaderSquareButton(onClick = {
                        if (veoPrompt.isNotBlank()) copyText(context, veoPrompt)
                    }) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = VppColors.textLight, modifier = Modifier.size(18.dp))
                    }
                }
            )
            Spacer(Modifier.height(18.dp))
            // No enter animation — AnimatedVisibility(visible=false) caused a blank/white flash.
            GradientHeading("Результат")
            Spacer(Modifier.height(16.dp))
            if (entity != null) {
                ResultBody(
                    entity = entity,
                    veoPrompt = veoPrompt,
                    voiceover = voiceover,
                    title = title,
                    voiceLabel = voiceLabel,
                    hashtags = hashtags,
                    onCopyPrompt = { copyText(context, veoPrompt) },
                    onSharePrompt = { shareText(context, veoPrompt) },
                    onCopyPackage = { copyText(context, viewModel.fullPackage(entity)) },
                    onSharePackage = { shareText(context, viewModel.fullPackage(entity)) },
                    onCopyVo = { copyText(context, voiceover) },
                    onCopyTitle = { copyText(context, title) },
                    onCopyTags = { copyText(context, hashtags.joinToString(" ")) }
                )
            } else {
                // Keep layout stable while Room emits — no loading-card pop-in flash.
                Spacer(Modifier.height(240.dp))
            }
        }
    }
}

@Composable
private fun ResultBody(
    entity: ProjectEntity,
    veoPrompt: String,
    voiceover: String,
    title: String,
    voiceLabel: String,
    hashtags: List<String>,
    onCopyPrompt: () -> Unit,
    onSharePrompt: () -> Unit,
    onCopyPackage: () -> Unit,
    onSharePackage: () -> Unit,
    onCopyVo: () -> Unit,
    onCopyTitle: () -> Unit,
    onCopyTags: () -> Unit
) {
    val type = LocalVppType.current

    NavyCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge("Ready for Gemini")
            Badge("VEO 3.1 PROMPT")
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(VppShapes.insetShape)
                .background(VppColors.cardInset)
                .padding(16.dp)
        ) {
            Text(
                veoPrompt.ifBlank { "Промпт ещё не готов." },
                style = type.body.copy(
                    color = VppColors.textLight.copy(alpha = 0.92f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 19.sp
                )
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    GradientButton(text = "✦ Копировать VEO Prompt", onClick = onCopyPrompt)
    Spacer(Modifier.height(10.dp))
    OutlinedActionButton(text = "Поделиться VEO Prompt", onClick = onSharePrompt)
    Spacer(Modifier.height(10.dp))
    OutlinedActionButton(text = "Копировать весь пакет", onClick = onCopyPackage)
    Spacer(Modifier.height(10.dp))
    OutlinedActionButton(text = "Поделиться всем пакетом", onClick = onSharePackage)

    Spacer(Modifier.height(14.dp))
    NavyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.GraphicEq, null, tint = VppColors.accentPurple)
            Spacer(Modifier.width(10.dp))
            Text(
                voiceLabel,
                style = type.cardTitle.copy(color = VppColors.textLight),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopyVo) {
                Icon(Icons.Outlined.ContentCopy, null, tint = VppColors.textMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            voiceover.ifBlank { "OFF" },
            style = type.body.copy(color = VppColors.textLight)
        )
    }

    Spacer(Modifier.height(14.dp))
    NavyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Edit, null, tint = VppColors.accentPurple)
            Spacer(Modifier.width(10.dp))
            Text(
                "Название",
                style = type.cardTitle.copy(color = VppColors.textLight),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopyTitle) {
                Icon(Icons.Outlined.ContentCopy, null, tint = VppColors.textMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title.ifBlank { "—" }, style = type.body.copy(color = VppColors.textLight))
    }

    Spacer(Modifier.height(14.dp))
    NavyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Tag, null, tint = VppColors.accentPurple)
            Spacer(Modifier.width(10.dp))
            Text(
                "Хештеги",
                style = type.cardTitle.copy(color = VppColors.textLight),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopyTags) {
                Icon(Icons.Outlined.ContentCopy, null, tint = VppColors.textMuted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            hashtags.take(5).forEach { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(VppColors.cardInset)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(tag, color = VppColors.textLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    NavyCard {
        Text("Кратко о проекте", style = type.cardTitle.copy(color = VppColors.textLight))
        Spacer(Modifier.height(14.dp))
        SummaryRow("Язык озвучки", entity.voiceLanguage)
        Spacer(Modifier.height(10.dp))
        SummaryRow("Режим", entity.mode)
        Spacer(Modifier.height(10.dp))
        SummaryRow("TikTok Shop Mode", if (entity.tiktokShopMode) "ON" else "OFF")
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(VppColors.cardInset)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = VppColors.accentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = LocalVppType.current.secondary.copy(color = VppColors.textMuted),
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = LocalVppType.current.body.copy(
                color = VppColors.textLight,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Veo Prompt Pro", text))
    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться"))
}

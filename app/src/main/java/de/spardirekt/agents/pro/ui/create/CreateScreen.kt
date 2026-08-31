package de.spardirekt.agents.pro.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.spardirekt.agents.pro.model.AppMode
import de.spardirekt.agents.pro.model.CreativeMode
import de.spardirekt.agents.pro.model.GenerationStage
import de.spardirekt.agents.pro.model.ImageCategory
import de.spardirekt.agents.pro.model.ProjectImage
import de.spardirekt.agents.pro.model.ProjectStatus
import de.spardirekt.agents.pro.model.VoiceLanguage
import de.spardirekt.agents.pro.ui.components.AppHeader
import de.spardirekt.agents.pro.ui.components.GradientButton
import de.spardirekt.agents.pro.ui.components.GradientHeading
import de.spardirekt.agents.pro.ui.components.LightOutlineCard
import de.spardirekt.agents.pro.ui.components.NavyCard
import de.spardirekt.agents.pro.ui.components.ProgressRail
import de.spardirekt.agents.pro.ui.components.SegmentedControl
import de.spardirekt.agents.pro.ui.components.SelectChip
import de.spardirekt.agents.pro.ui.theme.LocalVppType
import de.spardirekt.agents.pro.ui.theme.VppColors
import de.spardirekt.agents.pro.ui.theme.VppDimens
import de.spardirekt.agents.pro.ui.theme.VppShapes

@Composable
fun CreateScreen(
    viewModel: CreateViewModel,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenResult: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val type = LocalVppType.current

    LaunchedEffect(Unit) { viewModel.bootstrap() }
    LaunchedEffect(state.navigateToResultId) {
        state.navigateToResultId?.let {
            viewModel.consumeNavigation()
            onOpenResult(it)
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(15)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        VppColors.backgroundLight,
                        VppColors.backgroundGlow.copy(alpha = 0.55f),
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
                .padding(top = 12.dp, bottom = 110.dp)
        ) {
            AppHeader(
                onNewProject = { viewModel.newProject() },
                onHistory = onOpenHistory,
                onMenu = onOpenSettings
            )
            Spacer(Modifier.height(18.dp))
            GradientHeading("Генератор промптов для видео")
            Spacer(Modifier.height(8.dp))
            Text(
                "Загружайте фото товара, выбирайте настройки\nи получайте готовый промпт для VEO 3.1.",
                style = type.secondary.copy(color = VppColors.textMutedDark, lineHeight = 20.sp)
            )
            Spacer(Modifier.height(20.dp))

            // Photos card
            NavyCard {
                Text(
                    "Ваши фото товара",
                    style = type.cardTitle.copy(color = VppColors.textLight)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Можно загружать фото товара и скриншоты с описанием — агент разберёт их автоматически.",
                    style = type.secondary.copy(color = VppColors.textMuted, fontSize = 12.sp)
                )
                Spacer(Modifier.height(14.dp))
                if (state.images.isNotEmpty()) {
                    val rows = state.images.chunked(3)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEachIndexed { _, img ->
                                    val index = state.images.indexOf(img)
                                    PhotoThumb(
                                        img = img,
                                        index = index,
                                        onRemove = { viewModel.removeImage(img.id) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - row.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    "${state.images.size} фото загружено",
                    style = type.secondary.copy(color = VppColors.textMuted)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(VppColors.cardInset)
                        .clickable {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Add, null, tint = VppColors.textLight)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "+ Добавить фото",
                        style = type.body.copy(color = VppColors.textLight, fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            LightOutlineCard(onClick = { viewModel.toggleWish() }) {
                Text(
                    "+ Дополнительное пожелание",
                    style = type.cardTitle.copy(color = VppColors.textDark)
                )
                AnimatedVisibility(visible = state.wishExpanded) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        BasicTextField(
                            value = state.optionalWish,
                            onValueChange = viewModel::setWish,
                            textStyle = type.body.copy(color = VppColors.textDark),
                            cursorBrush = SolidColor(VppColors.accentPurple),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .border(1.dp, VppColors.outlineSoft, RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (state.optionalWish.isBlank()) {
                                        Text(
                                            "Без людей · Показать складывание · Больше макро",
                                            style = type.secondary.copy(color = VppColors.textMutedDark)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            NavyCard {
                Text("Озвучка", style = type.cardTitle.copy(color = VppColors.textLight))
                Spacer(Modifier.height(12.dp))
                SegmentedControl(
                    options = listOf("DE", "RU", "OFF"),
                    selected = state.voice.name,
                    onSelect = { viewModel.setVoice(VoiceLanguage.valueOf(it)) }
                )
            }

            Spacer(Modifier.height(14.dp))

            NavyCard {
                Text("Режим", style = type.cardTitle.copy(color = VppColors.textLight))
                Spacer(Modifier.height(12.dp))
                SegmentedControl(
                    options = listOf("Simple", "Advanced"),
                    selected = state.mode.name,
                    onSelect = { viewModel.setMode(AppMode.valueOf(it)) }
                )
                if (state.mode == AppMode.Advanced) {
                    Spacer(Modifier.height(16.dp))
                    Text("Креатив", style = type.secondary.copy(color = VppColors.textMuted))
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CreativeMode.entries.forEach { mode ->
                            SelectChip(
                                label = mode.name,
                                selected = state.creative == mode,
                                onClick = { viewModel.setCreative(mode) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TikTok Shop Mode", style = type.cardTitle.copy(color = VppColors.textLight))
                        Text(
                            "Безопасные формулировки для Shop",
                            style = type.secondary.copy(color = VppColors.textMuted, fontSize = 12.sp)
                        )
                    }
                    Switch(
                        checked = state.tiktokShopMode,
                        onCheckedChange = viewModel::setTiktok,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = VppColors.accentPurple,
                            uncheckedThumbColor = VppColors.textMuted,
                            uncheckedTrackColor = VppColors.cardInset
                        )
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = "✦ Создать VEO Prompt",
                onClick = { viewModel.onGenerate() },
                enabled = !state.isGenerating
            )

            if (state.isGenerating || state.stage !in listOf(
                    GenerationStage.IDLE, GenerationStage.DONE, GenerationStage.FAILED
                )
            ) {
                Spacer(Modifier.height(16.dp))
                GenerationProgressCard(state.stage)
            }

            if (state.errorMessage.isNotBlank() &&
                state.project?.status == ProjectStatus.Error.name
            ) {
                Spacer(Modifier.height(16.dp))
                ErrorCard(
                    message = state.errorMessage,
                    detail = state.errorDetail,
                    showDetail = state.showErrorDetail,
                    onContinue = { viewModel.continueGeneration() },
                    onDetails = { viewModel.toggleErrorDetail() }
                )
            } else if (state.errorMessage.isNotBlank() && !state.isGenerating) {
                Spacer(Modifier.height(12.dp))
                Text(state.errorMessage, color = VppColors.error, style = type.secondary)
            }
        }
    }

    if (state.showApiKeyDialog) {
        ApiKeyDialog(
            value = state.apiKeyInput,
            onValueChange = viewModel::setApiKeyInput,
            onSave = viewModel::saveApiKeyAndContinue,
            onCancel = viewModel::dismissApiDialog
        )
    }
}

@Composable
private fun GenerationProgressCard(stage: GenerationStage) {
    val labels = listOf(
        "Анализ фотографий",
        "Понимание товара",
        "Создание рекламной идеи",
        "Создание VEO Prompt",
        "Проверка результата",
        "Финализация"
    )
    val activeIndex = when (stage) {
        GenerationStage.PHOTO_ANALYSIS -> 0
        GenerationStage.PRODUCT_MODEL -> 1
        GenerationStage.CREATIVE_DIRECTOR -> 2
        GenerationStage.FINAL_PROMPT -> 3
        GenerationStage.FINAL_VALIDATION -> 4
        GenerationStage.FINALIZATION -> 5
        GenerationStage.DONE -> 5
        else -> 0
    }
    val completedThrough = when (stage) {
        GenerationStage.DONE -> 5
        GenerationStage.FINALIZATION -> 4
        GenerationStage.FINAL_VALIDATION -> 3
        GenerationStage.FINAL_PROMPT -> 2
        GenerationStage.CREATIVE_DIRECTOR -> 1
        GenerationStage.PRODUCT_MODEL -> 0
        else -> -1
    }
    NavyCard {
        Text(
            "Generation Progress",
            style = LocalVppType.current.cardTitle.copy(color = VppColors.textLight)
        )
        Spacer(Modifier.height(16.dp))
        ProgressRail(labels, activeIndex, completedThrough)
    }
}

@Composable
private fun ErrorCard(
    message: String,
    detail: String,
    showDetail: Boolean,
    onContinue: () -> Unit,
    onDetails: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VppShapes.cardShape)
            .background(VppColors.errorCard)
            .padding(22.dp)
    ) {
        Text(
            "Не удалось создать промпт.",
            style = LocalVppType.current.cardTitle.copy(color = VppColors.textLight)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Фото и уже выполненные этапы сохранены.",
            style = LocalVppType.current.secondary.copy(color = VppColors.textMuted)
        )
        Spacer(Modifier.height(8.dp))
        Text(message, style = LocalVppType.current.body.copy(color = VppColors.error))
        AnimatedVisibility(showDetail && detail.isNotBlank()) {
            Text(
                detail,
                style = LocalVppType.current.secondary.copy(color = VppColors.textMuted),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(VppColors.accentPurple, VppColors.accentBlue)))
                    .clickable(onClick = onContinue),
                contentAlignment = Alignment.Center
            ) {
                Text("Продолжить", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, VppColors.textMuted.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onDetails),
                contentAlignment = Alignment.Center
            ) {
                Text("Подробнее", color = VppColors.textLight, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ApiKeyDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(VppShapes.cardShape)
                .background(VppColors.cardNavy)
                .padding(22.dp)
        ) {
            Text(
                "OpenAI API",
                style = LocalVppType.current.sectionTitle.copy(color = VppColors.textLight)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Добавьте API-ключ для анализа фотографий и создания промптов.",
                style = LocalVppType.current.secondary.copy(color = VppColors.textMuted)
            )
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = LocalVppType.current.body.copy(color = VppColors.textLight),
                cursorBrush = SolidColor(VppColors.accentPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VppColors.cardInset)
                    .padding(14.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) {
                            Text("sk-…", color = VppColors.textMuted)
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(VppColors.accentPurple, VppColors.accentBlue)))
                        .clickable(onClick = onSave),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Сохранить", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, VppColors.textMuted.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Отмена", color = VppColors.textLight)
                }
            }
        }
    }
}

@Composable
private fun PhotoThumb(
    img: ProjectImage,
    index: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = LocalVppType.current
    Box(modifier = modifier.aspectRatio(1f)) {
        AsyncImage(
            model = img.localPath?.takeIf { it.isNotBlank() } ?: img.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(VppShapes.thumbShape)
        )
        Box(
            modifier = Modifier
                .padding(6.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(VppColors.cardInset.copy(alpha = 0.85f))
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${index + 1}",
                color = VppColors.textLight,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .align(Alignment.TopEnd)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Close,
                null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
        if (img.category != ImageCategory.UNKNOWN) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VppColors.accentPurple.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    img.category.badgeLabel(),
                    style = type.badge.copy(color = Color.White, fontSize = 9.sp),
                    maxLines = 1
                )
            }
        }
    }
}

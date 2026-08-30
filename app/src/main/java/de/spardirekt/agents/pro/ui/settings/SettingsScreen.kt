package de.spardirekt.agents.pro.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.spardirekt.agents.pro.BuildConfig
import de.spardirekt.agents.pro.VeoPromptProApp
import de.spardirekt.agents.pro.model.AppMode
import de.spardirekt.agents.pro.model.VoiceLanguage
import de.spardirekt.agents.pro.network.OpenAiModelCatalog
import de.spardirekt.agents.pro.ui.components.AppHeader
import de.spardirekt.agents.pro.ui.components.GradientHeading
import de.spardirekt.agents.pro.ui.components.NavyCard
import de.spardirekt.agents.pro.ui.components.SegmentedControl
import de.spardirekt.agents.pro.ui.components.StatusPill
import de.spardirekt.agents.pro.ui.theme.LocalVppType
import de.spardirekt.agents.pro.ui.theme.VppColors
import de.spardirekt.agents.pro.ui.theme.VppDimens
import de.spardirekt.agents.pro.ui.theme.VppShapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val hasKey: Boolean = false,
    val testMessage: String = "",
    val showReplaceDialog: Boolean = false,
    val keyInput: String = "",
    val busy: Boolean = false
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val apiKeys = VeoPromptProApp.instance.apiKeyStore
    private val settingsStore = VeoPromptProApp.instance.settingsStore
    private val repo = VeoPromptProApp.instance.projectRepository
    private val openAi = VeoPromptProApp.instance.openAiClient

    val settings = settingsStore.settings
    private val _ui = MutableStateFlow(SettingsUiState(hasKey = apiKeys.hasKey()))
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    fun refreshKeyStatus() {
        _ui.update { it.copy(hasKey = apiKeys.hasKey()) }
    }

    fun openReplace() {
        _ui.update { it.copy(showReplaceDialog = true, keyInput = "") }
    }

    fun dismissReplace() {
        _ui.update { it.copy(showReplaceDialog = false) }
    }

    fun setKeyInput(v: String) {
        _ui.update { it.copy(keyInput = v) }
    }

    fun saveKey() {
        val key = _ui.value.keyInput.trim()
        if (key.isBlank()) return
        apiKeys.saveKey(key)
        _ui.update { it.copy(showReplaceDialog = false, hasKey = true, testMessage = "API ключ сохранён") }
    }

    fun removeKey() {
        apiKeys.removeKey()
        _ui.update { it.copy(hasKey = false, testMessage = "API ключ удалён") }
    }

    fun testConnection() {
        viewModelScope.launch {
            val key = apiKeys.getKey()
            if (key.isNullOrBlank()) {
                _ui.update { it.copy(testMessage = "Сначала добавьте API ключ") }
                return@launch
            }
            _ui.update { it.copy(busy = true, testMessage = "Проверка…") }
            val result = openAi.testConnection(key)
            _ui.update {
                it.copy(
                    busy = false,
                    testMessage = result.fold(
                        onSuccess = { msg -> msg },
                        onFailure = { err ->
                            (err as? de.spardirekt.agents.pro.diagnostics.AppError)?.userMessage
                                ?: "Ошибка проверки"
                        }
                    )
                )
            }
        }
    }

    fun setVoice(v: VoiceLanguage) = viewModelScope.launch { settingsStore.setVoice(v) }
    fun setMode(m: AppMode) = viewModelScope.launch { settingsStore.setMode(m) }
    fun setTiktok(on: Boolean) = viewModelScope.launch { settingsStore.setTiktok(on) }
    fun setHistoryFormat(fmt: String) = viewModelScope.launch { settingsStore.setHistoryFormat(fmt) }
    fun setModel(model: String) = viewModelScope.launch { settingsStore.setModel(model) }
    fun setDebug(on: Boolean) = viewModelScope.launch { settingsStore.setDebugLogs(on) }
    fun setOutputLanguage(lang: String) = viewModelScope.launch { settingsStore.setOutputLanguage(lang) }
    fun clearHistory() = viewModelScope.launch {
        de.spardirekt.agents.pro.storage.ImageStore.deleteAll(getApplication())
        repo.clearAll()
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenCreate: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val sett by viewModel.settings.collectAsStateWithLifecycle(
        initialValue = de.spardirekt.agents.pro.storage.SettingsStore.AppSettings()
    )
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val type = LocalVppType.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(VppColors.backgroundLight, VppColors.backgroundGlow.copy(alpha = 0.45f), VppColors.backgroundLight)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = VppDimens.screenPadding)
                .padding(top = 12.dp, bottom = 110.dp)
        ) {
            AppHeader(onNewProject = onOpenCreate, onHistory = onOpenHistory, onMenu = null)
            Spacer(Modifier.height(18.dp))
            GradientHeading("Настройки")
            Spacer(Modifier.height(18.dp))

            Text("OpenAI API", style = type.sectionTitle.copy(color = VppColors.textDark))
            Spacer(Modifier.height(10.dp))
            NavyCard {
                Text("OpenAI API", style = type.cardTitle.copy(color = VppColors.textLight))
                Spacer(Modifier.height(10.dp))
                StatusPill(
                    text = if (ui.hasKey) "API ключ настроен ✓" else "API ключ не задан",
                    success = ui.hasKey
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (ui.hasKey) "API ключ сохранён" else "Добавьте ключ для генерации",
                    style = type.secondary.copy(color = VppColors.textMuted)
                )
                if (ui.testMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(ui.testMessage, style = type.secondary.copy(color = VppColors.textLight))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsAction(if (ui.hasKey) "Заменить" else "Добавить") { viewModel.openReplace() }
                    SettingsAction("Проверить") { viewModel.testConnection() }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, VppColors.error.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .clickable { viewModel.removeKey() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Удалить API ключ", color = VppColors.error)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Основные", style = type.sectionTitle.copy(color = VppColors.textDark))
            Spacer(Modifier.height(10.dp))
            NavyCard {
                Text("Язык вывода", style = type.secondary.copy(color = VppColors.textMuted))
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf("RU", "DE"),
                    selected = sett.outputLanguage,
                    onSelect = viewModel::setOutputLanguage
                )
                Spacer(Modifier.height(14.dp))
                Text("Режим генерации по умолчанию", style = type.secondary.copy(color = VppColors.textMuted))
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf("Simple", "Advanced"),
                    selected = sett.defaultMode.name,
                    onSelect = { viewModel.setMode(AppMode.valueOf(it)) }
                )
                Spacer(Modifier.height(14.dp))
                Text("Озвучка по умолчанию", style = type.secondary.copy(color = VppColors.textMuted))
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf("DE", "RU", "OFF"),
                    selected = sett.defaultVoice.name,
                    onSelect = { viewModel.setVoice(VoiceLanguage.valueOf(it)) }
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "TikTok Shop Mode",
                        style = type.cardTitle.copy(color = VppColors.textLight),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = sett.tiktokShopMode,
                        onCheckedChange = viewModel::setTiktok,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = VppColors.accentPurple
                        )
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Данные", style = type.sectionTitle.copy(color = VppColors.textDark))
            Spacer(Modifier.height(10.dp))
            NavyCard {
                Text("Формат истории", style = type.secondary.copy(color = VppColors.textMuted))
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf("full", "compact"),
                    selected = sett.historyFormat,
                    onSelect = viewModel::setHistoryFormat
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, VppColors.error.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .clickable { viewModel.clearHistory() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Очистить историю", color = VppColors.error)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Диагностика", style = type.sectionTitle.copy(color = VppColors.textDark))
            Spacer(Modifier.height(10.dp))
            NavyCard {
                Text("OpenAI Model", style = type.cardTitle.copy(color = VppColors.textLight))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Только GPT-5.6: Sol, Terra или Luna. Старые GPT-4 модели больше не выбираются.",
                    style = type.secondary.copy(color = VppColors.textMuted, fontSize = 12.sp)
                )
                Spacer(Modifier.height(12.dp))
                OpenAiModelCatalog.options.forEach { option ->
                    val selected = sett.model == option.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (selected) Modifier.background(VppColors.cardInset)
                                else Modifier.background(Color.Transparent)
                            )
                            .clickable { viewModel.setModel(option.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                option.label,
                                style = type.body.copy(
                                    color = VppColors.textLight,
                                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold
                                    else androidx.compose.ui.text.font.FontWeight.Normal
                                ),
                                maxLines = 1
                            )
                            Text(
                                option.id,
                                style = type.secondary.copy(color = VppColors.textMuted, fontSize = 11.sp),
                                maxLines = 1
                            )
                            Text(
                                option.hint,
                                style = type.secondary.copy(color = VppColors.textMuted, fontSize = 11.sp),
                                maxLines = 1
                            )
                        }
                        when {
                            selected -> StatusPill("Выбран ✓", success = true)
                            option.recommended -> Text(
                                "рекомендуется",
                                style = type.secondary.copy(
                                    color = VppColors.accentPurple,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Debug logs",
                        style = type.cardTitle.copy(color = VppColors.textLight),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = sett.debugLogs,
                        onCheckedChange = viewModel::setDebug,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = VppColors.accentPurple
                        )
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("О приложении", style = type.sectionTitle.copy(color = VppColors.textDark))
            Spacer(Modifier.height(10.dp))
            NavyCard {
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = type.cardTitle.copy(color = VppColors.textLight))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Private VEO 3.1 prompt generator · 3.2.2",
                    style = type.secondary.copy(color = VppColors.textMuted)
                )
            }
        }
    }

    if (ui.showReplaceDialog) {
        Dialog(onDismissRequest = viewModel::dismissReplace) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(VppShapes.cardShape)
                    .background(VppColors.cardNavy)
                    .padding(22.dp)
            ) {
                Text("OpenAI API", style = type.sectionTitle.copy(color = VppColors.textLight))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Добавьте API-ключ для анализа фотографий и создания промптов.",
                    style = type.secondary.copy(color = VppColors.textMuted)
                )
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = ui.keyInput,
                    onValueChange = viewModel::setKeyInput,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = type.body.copy(color = VppColors.textLight),
                    cursorBrush = SolidColor(VppColors.accentPurple),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(VppColors.cardInset)
                        .padding(14.dp)
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsAction("Сохранить", Modifier.weight(1f)) { viewModel.saveKey() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, VppColors.textMuted.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .clickable { viewModel.dismissReplace() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Отмена", color = VppColors.textLight)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsAction(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(VppColors.accentPurple, VppColors.accentBlue)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White)
    }
}

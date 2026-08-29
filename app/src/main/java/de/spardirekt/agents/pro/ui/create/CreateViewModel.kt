package de.spardirekt.agents.pro.ui.create

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.spardirekt.agents.pro.VeoPromptProApp
import de.spardirekt.agents.pro.data.db.ProjectEntity
import de.spardirekt.agents.pro.model.AppMode
import de.spardirekt.agents.pro.model.CreativeMode
import de.spardirekt.agents.pro.model.GenerationStage
import de.spardirekt.agents.pro.model.ProjectImage
import de.spardirekt.agents.pro.model.ProjectStatus
import de.spardirekt.agents.pro.model.VoiceLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class CreateUiState(
    val project: ProjectEntity? = null,
    val images: List<ProjectImage> = emptyList(),
    val optionalWish: String = "",
    val wishExpanded: Boolean = false,
    val voice: VoiceLanguage = VoiceLanguage.DE,
    val mode: AppMode = AppMode.Simple,
    val creative: CreativeMode = CreativeMode.Auto,
    val tiktokShopMode: Boolean = true,
    val stage: GenerationStage = GenerationStage.IDLE,
    val isGenerating: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val apiKeyInput: String = "",
    val errorMessage: String = "",
    val errorDetail: String = "",
    val showErrorDetail: Boolean = false,
    val navigateToResultId: String? = null
)

class CreateViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VeoPromptProApp.instance.projectRepository
    private val settingsStore = VeoPromptProApp.instance.settingsStore
    private val apiKeys = VeoPromptProApp.instance.apiKeyStore
    private val generation = VeoPromptProApp.instance.generationManager

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    private var projectId: String? = null
    private var observeJob: Job? = null
    private var wasGenerating = false

    init {
        viewModelScope.launch {
            generation.stage.collect { st ->
                val id = projectId ?: return@collect
                if (generation.activeProjectId.value != id) return@collect
                _state.update { it.copy(stage = st, isGenerating = generation.isRunning.value) }
                if (st == GenerationStage.DONE) {
                    _state.update { it.copy(navigateToResultId = id, isGenerating = false) }
                }
            }
        }
        viewModelScope.launch {
            generation.isRunning.collect { running ->
                val id = projectId
                val mine = id != null && generation.activeProjectId.value == id
                if (mine) {
                    if (wasGenerating && !running && _state.value.stage == GenerationStage.DONE) {
                        _state.update { it.copy(navigateToResultId = id, isGenerating = false) }
                    }
                    wasGenerating = running
                    _state.update { it.copy(isGenerating = running) }
                }
            }
        }
    }

    fun bootstrap() {
        if (projectId != null) return
        viewModelScope.launch {
            val sett = settingsStore.settings.first()
            val draft = repo.createDraft(
                voice = sett.defaultVoice,
                mode = sett.defaultMode,
                creative = sett.defaultCreative,
                tiktok = sett.tiktokShopMode
            )
            attachProject(draft.id)
            _state.update {
                it.copy(
                    project = draft,
                    voice = sett.defaultVoice,
                    mode = sett.defaultMode,
                    creative = sett.defaultCreative,
                    tiktokShopMode = sett.tiktokShopMode
                )
            }
        }
    }

    fun openProject(id: String) {
        attachProject(id)
    }

    private fun attachProject(id: String) {
        projectId = id
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repo.observe(id).collect { entity ->
                if (entity == null) return@collect
                val images = repo.parseImages(entity)
                val generating = generation.isBusyFor(entity.id) ||
                    entity.status == ProjectStatus.Generating.name
                _state.update {
                    it.copy(
                        project = entity,
                        images = images,
                        optionalWish = entity.optionalWish,
                        voice = runCatching { VoiceLanguage.valueOf(entity.voiceLanguage) }
                            .getOrDefault(VoiceLanguage.DE),
                        mode = runCatching { AppMode.valueOf(entity.mode) }
                            .getOrDefault(AppMode.Simple),
                        creative = runCatching { CreativeMode.valueOf(entity.creativeMode) }
                            .getOrDefault(CreativeMode.Auto),
                        tiktokShopMode = entity.tiktokShopMode,
                        stage = runCatching { GenerationStage.valueOf(entity.generationStage) }
                            .getOrDefault(GenerationStage.IDLE),
                        isGenerating = generating,
                        errorMessage = entity.errorState,
                        errorDetail = entity.errorDetail,
                        navigateToResultId = if (
                            wasGenerating &&
                            entity.status == ProjectStatus.Ready.name &&
                            entity.veoPrompt.isNotBlank()
                        ) entity.id else it.navigateToResultId
                    )
                }
            }
        }
    }

    fun newProject() {
        viewModelScope.launch {
            val sett = settingsStore.settings.first()
            val draft = repo.createDraft(
                voice = sett.defaultVoice,
                mode = sett.defaultMode,
                creative = sett.defaultCreative,
                tiktok = sett.tiktokShopMode
            )
            wasGenerating = false
            _state.value = CreateUiState(
                project = draft,
                voice = sett.defaultVoice,
                mode = sett.defaultMode,
                creative = sett.defaultCreative,
                tiktokShopMode = sett.tiktokShopMode
            )
            attachProject(draft.id)
        }
    }

    fun addImages(uris: List<Uri>) {
        viewModelScope.launch {
            val p = _state.value.project ?: return@launch
            val current = repo.parseImages(p).toMutableList()
            uris.forEach { uri ->
                if (current.size >= 15) return@forEach
                current += ProjectImage(
                    id = UUID.randomUUID().toString(),
                    uri = uri.toString(),
                    orderIndex = current.size
                )
            }
            repo.save(
                p.copy(
                    imageUrisJson = repo.encodeImages(current),
                    thumbnailUri = current.firstOrNull()?.uri.orEmpty()
                )
            )
        }
    }

    fun removeImage(id: String) {
        viewModelScope.launch {
            val p = _state.value.project ?: return@launch
            val current = repo.parseImages(p)
                .filterNot { it.id == id }
                .mapIndexed { i, img -> img.copy(orderIndex = i) }
            repo.save(
                p.copy(
                    imageUrisJson = repo.encodeImages(current),
                    thumbnailUri = current.firstOrNull()?.uri.orEmpty()
                )
            )
        }
    }

    fun toggleWish() {
        _state.update { it.copy(wishExpanded = !it.wishExpanded) }
    }

    fun setWish(text: String) {
        _state.update { it.copy(optionalWish = text) }
        viewModelScope.launch {
            val p = _state.value.project ?: return@launch
            repo.save(p.copy(optionalWish = text))
        }
    }

    fun setVoice(v: VoiceLanguage) = persist { it.copy(voiceLanguage = v.name) }
    fun setMode(m: AppMode) = persist { it.copy(mode = m.name) }
    fun setCreative(c: CreativeMode) = persist { it.copy(creativeMode = c.name) }
    fun setTiktok(on: Boolean) = persist { it.copy(tiktokShopMode = on) }

    private fun persist(block: (ProjectEntity) -> ProjectEntity) {
        viewModelScope.launch {
            val p = _state.value.project ?: return@launch
            repo.save(block(p))
        }
    }

    fun onGenerate() {
        if (!apiKeys.hasKey()) {
            _state.update { it.copy(showApiKeyDialog = true, apiKeyInput = "") }
            return
        }
        startGeneration(resume = false)
    }

    fun saveApiKeyAndContinue() {
        val key = _state.value.apiKeyInput.trim()
        if (key.isBlank()) return
        apiKeys.saveKey(key)
        _state.update { it.copy(showApiKeyDialog = false, apiKeyInput = "") }
        startGeneration(resume = false)
    }

    fun dismissApiDialog() {
        _state.update { it.copy(showApiKeyDialog = false) }
    }

    fun setApiKeyInput(v: String) {
        _state.update { it.copy(apiKeyInput = v) }
    }

    fun continueGeneration() = startGeneration(resume = true)

    fun toggleErrorDetail() {
        _state.update { it.copy(showErrorDetail = !it.showErrorDetail) }
    }

    fun consumeNavigation() {
        _state.update { it.copy(navigateToResultId = null) }
    }

    private fun startGeneration(resume: Boolean) {
        val id = projectId ?: return
        if (_state.value.images.isEmpty()) {
            _state.update {
                it.copy(errorMessage = "Добавьте хотя бы одно фото.", errorDetail = "")
            }
            return
        }
        wasGenerating = true
        _state.update {
            it.copy(
                isGenerating = true,
                errorMessage = "",
                errorDetail = "",
                stage = if (resume) it.stage else GenerationStage.PHOTO_ANALYSIS
            )
        }
        generation.start(id, resume = resume)
    }
}

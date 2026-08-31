package de.spardirekt.agents.pro.generation

import android.content.Context
import de.spardirekt.agents.pro.data.repository.ProjectRepository
import de.spardirekt.agents.pro.diagnostics.AppError
import de.spardirekt.agents.pro.diagnostics.DebugLog
import de.spardirekt.agents.pro.model.AppMode
import de.spardirekt.agents.pro.model.CreativeMode
import de.spardirekt.agents.pro.model.GenerationStage
import de.spardirekt.agents.pro.model.ProjectStatus
import de.spardirekt.agents.pro.model.VoiceLanguage
import de.spardirekt.agents.pro.network.OpenAiClient
import de.spardirekt.agents.pro.storage.SecureApiKeyStore
import de.spardirekt.agents.pro.storage.SettingsStore
import de.spardirekt.agents.pro.worker.GenerationForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Application-scoped generation manager — survives navigation and brief backgrounding.
 * Does not duplicate jobs on resume.
 */
class GenerationManager(
    private val appContext: Context,
    private val repository: ProjectRepository,
    private val apiKeyStore: SecureApiKeyStore,
    private val settingsStore: SettingsStore,
    private val openAi: OpenAiClient = OpenAiClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var job: Job? = null

    private val _activeProjectId = MutableStateFlow<String?>(null)
    val activeProjectId: StateFlow<String?> = _activeProjectId

    private val _stage = MutableStateFlow(GenerationStage.IDLE)
    val stage: StateFlow<GenerationStage> = _stage

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    fun isBusyFor(projectId: String): Boolean =
        _isRunning.value && _activeProjectId.value == projectId

    fun start(projectId: String, resume: Boolean = false) {
        scope.launch {
            mutex.withLock {
                if (_isRunning.value && _activeProjectId.value == projectId) return@withLock
                if (_isRunning.value) return@withLock
                job?.cancel()
                _isRunning.value = true
                _activeProjectId.value = projectId
                GenerationForegroundService.start(appContext, projectId)
                job = scope.launch {
                    try {
                        runPipeline(projectId, resume)
                    } finally {
                        mutex.withLock {
                            _isRunning.value = false
                            GenerationForegroundService.stop(appContext)
                        }
                    }
                }
            }
        }
    }

    fun resumeInterruptedIfNeeded() {
        scope.launch {
            val active = repository.getActiveGenerating() ?: return@launch
            if (!_isRunning.value) start(active.id, resume = true)
        }
    }

    private suspend fun runPipeline(projectId: String, resume: Boolean) {
        val project = repository.get(projectId) ?: return
        val apiKey = apiKeyStore.getKey()
        if (apiKey.isNullOrBlank()) {
            fail(projectId, AppError.InvalidApiKey("API key missing"))
            return
        }
        val settings = settingsStore.settings.first()
        DebugLog.enabled = settings.debugLogs

        val completed = repository.parseCompletedStages(project)
        val resumeFrom = if (resume) {
            repository.nextResumeStage(completed)
        } else {
            GenerationStage.PHOTO_ANALYSIS
        }

        val images = repository.parseImages(project)
        val pipeline = GenerationPipeline(appContext, openAi)
        val input = GenerationPipeline.PipelineInput(
            projectId = projectId,
            images = images,
            optionalWish = project.optionalWish,
            voiceLanguage = runCatching { VoiceLanguage.valueOf(project.voiceLanguage) }
                .getOrDefault(VoiceLanguage.DE),
            mode = runCatching { AppMode.valueOf(project.mode) }.getOrDefault(AppMode.Simple),
            creativeMode = runCatching { CreativeMode.valueOf(project.creativeMode) }
                .getOrDefault(CreativeMode.Auto),
            tiktokShopMode = project.tiktokShopMode,
            apiKey = apiKey,
            model = settings.model,
            resumeFrom = resumeFrom,
            existingAnalysisJson = project.analysisResultJson,
            existingProductModelJson = project.productModelJson,
            existingCreativePlanJson = project.creativePlanJson,
            existingVeoPrompt = project.veoPrompt
        )

        markGenerating(projectId, resumeFrom)

        val result = pipeline.run(input) { update ->
            _stage.value = update.stage
            GenerationForegroundService.start(
                appContext,
                projectId,
                stageLabel(update.stage)
            )
            persistStage(projectId, update)
        }

        result.fold(
            onSuccess = { bundle ->
                val current = repository.get(projectId) ?: return
                repository.save(
                    current.copy(
                        veoPrompt = bundle.veoPrompt,
                        voiceover = bundle.voiceover,
                        title = bundle.title,
                        hashtagsJson = repository.encodeHashtags(bundle.hashtags),
                        analysisResultJson = bundle.analysisJson,
                        productModelJson = bundle.productModelJson,
                        creativePlanJson = bundle.creativePlanJson,
                        qualityScoresJson = json.encodeToString(bundle.qualityScores),
                        internalSafetyAudit = bundle.internalSafetyAudit,
                        generationStage = GenerationStage.DONE.name,
                        status = ProjectStatus.Ready.name,
                        errorState = "",
                        errorDetail = "",
                        completedStagesJson = repository.encodeCompletedStages(
                            setOf(
                                GenerationStage.PHOTO_ANALYSIS,
                                GenerationStage.PRODUCT_MODEL,
                                GenerationStage.CREATIVE_DIRECTOR,
                                GenerationStage.FINAL_PROMPT,
                                GenerationStage.FINAL_VALIDATION,
                                GenerationStage.FINALIZATION,
                                GenerationStage.DONE
                            )
                        )
                    )
                )
                _stage.value = GenerationStage.DONE
            },
            onFailure = { err ->
                val appErr = err as? AppError ?: AppError.Unknown(err.message.orEmpty())
                // one automatic retry for transient stage failures
                val current = repository.get(projectId)
                val retries = current?.let { repository.parseRetryCounts(it) }?.toMutableMap()
                    ?: mutableMapOf()
                val stageKey = _stage.value.name
                val count = retries[stageKey] ?: 0
                if (appErr.retryable && count < 1) {
                    retries[stageKey] = count + 1
                    val latest = repository.get(projectId)
                    latest?.let {
                        repository.save(
                            it.copy(
                                retryCountJson = repository.encodeRetryCounts(retries),
                                errorState = appErr.userMessage,
                                errorDetail = appErr.detail
                            )
                        )
                    }
                    DebugLog.d("Auto-retry stage $stageKey")
                    val completedNow = latest?.let { repository.parseCompletedStages(it) }.orEmpty()
                    val again = pipeline.run(
                        input.copy(resumeFrom = repository.nextResumeStage(completedNow))
                    ) { update ->
                        _stage.value = update.stage
                        persistStage(projectId, update)
                    }
                    again.fold(
                        onSuccess = { bundle ->
                            val cur = repository.get(projectId) ?: return
                            repository.save(
                                cur.copy(
                                    veoPrompt = bundle.veoPrompt,
                                    voiceover = bundle.voiceover,
                                    title = bundle.title,
                                    hashtagsJson = repository.encodeHashtags(bundle.hashtags),
                                    analysisResultJson = bundle.analysisJson,
                                    productModelJson = bundle.productModelJson,
                                    creativePlanJson = bundle.creativePlanJson,
                                    qualityScoresJson = json.encodeToString(bundle.qualityScores),
                                    internalSafetyAudit = bundle.internalSafetyAudit,
                                    generationStage = GenerationStage.DONE.name,
                                    status = ProjectStatus.Ready.name,
                                    errorState = "",
                                    errorDetail = ""
                                )
                            )
                            _stage.value = GenerationStage.DONE
                        },
                        onFailure = { e2 ->
                            fail(projectId, e2 as? AppError ?: AppError.Unknown(e2.message.orEmpty()))
                        }
                    )
                } else {
                    fail(projectId, appErr)
                }
            }
        )
    }

    private suspend fun markGenerating(projectId: String, stage: GenerationStage) {
        val p = repository.get(projectId) ?: return
        repository.save(
            p.copy(
                status = ProjectStatus.Generating.name,
                generationStage = stage.name,
                errorState = "",
                errorDetail = ""
            )
        )
        _stage.value = stage
    }

    private suspend fun persistStage(projectId: String, update: GenerationPipeline.StageUpdate) {
        val p = repository.get(projectId) ?: return
        val completed = repository.parseCompletedStages(p).toMutableSet()
        // Mark previous durable stages complete when we advance past them
        when (update.stage) {
            GenerationStage.PRODUCT_MODEL -> completed += GenerationStage.PHOTO_ANALYSIS
            GenerationStage.CREATIVE_DIRECTOR -> {
                completed += GenerationStage.PHOTO_ANALYSIS
                completed += GenerationStage.PRODUCT_MODEL
            }
            GenerationStage.FINAL_PROMPT -> {
                completed += GenerationStage.PHOTO_ANALYSIS
                completed += GenerationStage.PRODUCT_MODEL
                completed += GenerationStage.CREATIVE_DIRECTOR
            }
            GenerationStage.FINAL_VALIDATION, GenerationStage.FINALIZATION, GenerationStage.DONE -> {
                completed += GenerationStage.PHOTO_ANALYSIS
                completed += GenerationStage.PRODUCT_MODEL
                completed += GenerationStage.CREATIVE_DIRECTOR
                completed += GenerationStage.FINAL_PROMPT
            }
            else -> Unit
        }

        var imagesJson = p.imageUrisJson
        update.classifiedImages?.let {
            imagesJson = repository.encodeImages(it)
        }

        repository.save(
            p.copy(
                generationStage = update.stage.name,
                status = if (update.stage == GenerationStage.DONE) {
                    ProjectStatus.Ready.name
                } else {
                    ProjectStatus.Generating.name
                },
                analysisResultJson = update.analysis?.let { json.encodeToString(it) }
                    ?: p.analysisResultJson,
                productModelJson = update.productModel?.let { json.encodeToString(it) }
                    ?: p.productModelJson,
                creativePlanJson = update.creativePlan?.let { json.encodeToString(it) }
                    ?: p.creativePlanJson,
                // Never persist the raw model draft. Only DONE carries the cleaned
                // Gemini/VEO prompt from PromptCleanup.finalize.
                veoPrompt = if (update.stage == GenerationStage.DONE) {
                    update.bundle?.veoPrompt?.ifBlank { p.veoPrompt } ?: p.veoPrompt
                } else {
                    p.veoPrompt
                },
                voiceover = if (update.stage == GenerationStage.DONE) {
                    update.bundle?.voiceover?.ifBlank { p.voiceover } ?: p.voiceover
                } else {
                    p.voiceover
                },
                title = if (update.stage == GenerationStage.DONE) {
                    update.bundle?.title?.ifBlank { p.title } ?: p.title
                } else {
                    p.title
                },
                hashtagsJson = if (update.stage == GenerationStage.DONE) {
                    update.bundle?.hashtags?.let { repository.encodeHashtags(it) } ?: p.hashtagsJson
                } else {
                    p.hashtagsJson
                },
                imageUrisJson = imagesJson,
                completedStagesJson = repository.encodeCompletedStages(completed),
                thumbnailUri = repository.parseImages(p.copy(imageUrisJson = imagesJson))
                    .firstOrNull()?.uri ?: p.thumbnailUri
            )
        )
    }

    private suspend fun fail(projectId: String, error: AppError) {
        val p = repository.get(projectId) ?: return
        repository.save(
            p.copy(
                status = ProjectStatus.Error.name,
                generationStage = GenerationStage.FAILED.name,
                errorState = error.userMessage,
                errorDetail = error.detail
            )
        )
        _stage.value = GenerationStage.FAILED
    }

    private fun stageLabel(stage: GenerationStage): String = when (stage) {
        GenerationStage.PHOTO_ANALYSIS -> "Анализ фотографий"
        GenerationStage.PRODUCT_MODEL -> "Понимание товара"
        GenerationStage.CREATIVE_DIRECTOR -> "Создание рекламной идеи"
        GenerationStage.FINAL_PROMPT -> "Создание VEO Prompt"
        GenerationStage.FINAL_VALIDATION -> "Проверка результата"
        GenerationStage.FINALIZATION -> "Финализация"
        GenerationStage.DONE -> "Готово"
        else -> "Генерация VEO Prompt…"
    }
}

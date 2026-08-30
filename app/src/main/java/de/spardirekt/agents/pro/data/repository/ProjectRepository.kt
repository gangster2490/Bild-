package de.spardirekt.agents.pro.data.repository

import de.spardirekt.agents.pro.data.db.ProjectDao
import de.spardirekt.agents.pro.data.db.ProjectEntity
import de.spardirekt.agents.pro.model.AppMode
import de.spardirekt.agents.pro.model.CreativeMode
import de.spardirekt.agents.pro.model.GenerationStage
import de.spardirekt.agents.pro.model.ImageCategory
import de.spardirekt.agents.pro.model.ProjectImage
import de.spardirekt.agents.pro.model.ProjectStatus
import de.spardirekt.agents.pro.model.VoiceLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ProjectRepository(
    private val dao: ProjectDao,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    fun observeAll(): Flow<List<ProjectEntity>> = dao.observeAll()

    fun observeHistory(): Flow<List<ProjectEntity>> = dao.observeAll().map { list ->
        list.filter { entity ->
            entity.status != ProjectStatus.Draft.name || parseImages(entity).isNotEmpty()
        }
    }

    fun observe(id: String): Flow<ProjectEntity?> = dao.observeById(id)
    suspend fun get(id: String): ProjectEntity? = dao.getById(id)
    suspend fun delete(id: String) = dao.delete(id)
    suspend fun clearAll() = dao.clearAll()
    suspend fun getActiveGenerating(): ProjectEntity? = dao.getActiveGenerating()

    suspend fun findReusableEmptyDraft(): ProjectEntity? {
        return dao.getRecentDrafts().firstOrNull { parseImages(it).isEmpty() && it.veoPrompt.isBlank() }
    }

    suspend fun createDraft(
        voice: VoiceLanguage,
        mode: AppMode,
        creative: CreativeMode,
        tiktok: Boolean
    ): ProjectEntity {
        val now = System.currentTimeMillis()
        val entity = ProjectEntity(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            updatedAt = now,
            voiceLanguage = voice.name,
            mode = mode.name,
            creativeMode = creative.name,
            tiktokShopMode = tiktok,
            status = ProjectStatus.Draft.name
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun save(entity: ProjectEntity) {
        dao.upsert(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    fun parseImages(entity: ProjectEntity): List<ProjectImage> {
        return runCatching {
            json.decodeFromString<List<ProjectImage>>(entity.imageUrisJson)
        }.getOrDefault(emptyList())
    }

    fun encodeImages(images: List<ProjectImage>): String = json.encodeToString(images)

    fun parseHashtags(entity: ProjectEntity): List<String> {
        return runCatching {
            json.decodeFromString<List<String>>(entity.hashtagsJson)
        }.getOrDefault(emptyList())
    }

    fun encodeHashtags(tags: List<String>): String = json.encodeToString(tags)

    fun parseCompletedStages(entity: ProjectEntity): Set<GenerationStage> {
        return runCatching {
            json.decodeFromString<List<String>>(entity.completedStagesJson)
                .mapNotNull { runCatching { GenerationStage.valueOf(it) }.getOrNull() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    fun encodeCompletedStages(stages: Set<GenerationStage>): String =
        json.encodeToString(stages.map { it.name })

    fun parseRetryCounts(entity: ProjectEntity): Map<String, Int> {
        return runCatching {
            json.decodeFromString<Map<String, Int>>(entity.retryCountJson)
        }.getOrDefault(emptyMap())
    }

    fun encodeRetryCounts(map: Map<String, Int>): String = json.encodeToString(map)

    fun nextResumeStage(completed: Set<GenerationStage>): GenerationStage {
        val order = listOf(
            GenerationStage.PHOTO_ANALYSIS,
            GenerationStage.PRODUCT_MODEL,
            GenerationStage.CREATIVE_DIRECTOR,
            GenerationStage.FINAL_PROMPT
        )
        return order.firstOrNull { it !in completed } ?: GenerationStage.FINAL_PROMPT
    }

    fun categoryOrUnknown(name: String): ImageCategory =
        runCatching { ImageCategory.valueOf(name) }.getOrDefault(ImageCategory.UNKNOWN)
}

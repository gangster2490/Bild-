package de.spardirekt.agents.pro.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.spardirekt.agents.pro.model.AppMode
import de.spardirekt.agents.pro.model.CreativeMode
import de.spardirekt.agents.pro.model.GenerationStage
import de.spardirekt.agents.pro.model.ProjectStatus
import de.spardirekt.agents.pro.model.VoiceLanguage

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val imageUrisJson: String = "[]",
    val optionalWish: String = "",
    val voiceLanguage: String = VoiceLanguage.DE.name,
    val mode: String = AppMode.Simple.name,
    val creativeMode: String = CreativeMode.Auto.name,
    val tiktokShopMode: Boolean = true,
    val analysisResultJson: String = "",
    val productModelJson: String = "",
    val creativePlanJson: String = "",
    val generationStage: String = GenerationStage.IDLE.name,
    val completedStagesJson: String = "[]",
    val veoPrompt: String = "",
    val voiceover: String = "",
    val title: String = "",
    val hashtagsJson: String = "[]",
    val errorState: String = "",
    val errorDetail: String = "",
    val status: String = ProjectStatus.Draft.name,
    val thumbnailUri: String = "",
    val retryCountJson: String = "{}",
    val qualityScoresJson: String = "",
    val internalSafetyAudit: String = ""
)

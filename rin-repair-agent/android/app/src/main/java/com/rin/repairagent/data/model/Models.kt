package com.rin.repairagent.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class AiProvider { OPENAI, GEMINI }

@Serializable
enum class ResultLanguage { RU, EN, BOTH }

@Serializable
enum class ReviewStatus {
    CHECKED,
    NEEDS_REVIEW,
    UNCLEAR,
    CAN_DELETE
}

@Serializable
data class PhotoAnalysis(
    val photoNumber: Int = 0,
    val visibleObjects: List<String> = emptyList(),
    val visibleAction: String = "",
    val repairStage: String = "",
    val tools: List<String> = emptyList(),
    val beginnerInstruction: String = "",
    val importantWarning: String = "",
    val confidence: Double = 0.0,
    val needsManualReview: Boolean = false,
    val beginnerInstructionEn: String = "",
    val importantWarningEn: String = "",
    val repairStageEn: String = "",
    val visibleActionEn: String = ""
)

@Serializable
data class ProjectPhoto(
    val id: String,
    val localPath: String,
    val photoNumber: Int,
    val analysis: PhotoAnalysis? = null,
    val reviewStatus: ReviewStatus = ReviewStatus.NEEDS_REVIEW,
    val userEditedInstruction: String? = null,
    val confirmed: Boolean = false
)

@Serializable
data class RepairProject(
    val id: String,
    val title: String,
    val productModel: String,
    val serialNumber: String = "",
    val language: ResultLanguage = ResultLanguage.BOTH,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val photos: List<ProjectPhoto> = emptyList(),
    val reviewCompleted: Boolean = false,
    val exportReady: Boolean = false
)

@Serializable
data class ExportValidation(
    val loadedPhotos: Int = 0,
    val usedPhotos: Int = 0,
    val skippedPhotos: Int = 0,
    val duplicatePhotos: Int = 0,
    val photosNeedingReview: Int = 0,
    val mappingErrors: Int = 0,
    val powerpointErrors: Int = 0,
    val pdfErrors: Int = 0,
    val canExport: Boolean = false,
    val errors: List<String> = emptyList()
)

@Serializable
data class ExportResult(
    val projectId: String,
    val files: List<ExportedFile> = emptyList(),
    val validation: ExportValidation = ExportValidation()
)

@Serializable
data class ExportedFile(
    val name: String,
    val relativePath: String,
    val mimeType: String
)

@Serializable
data class ApiKeyCheckResponse(
    val ok: Boolean,
    val provider: String = "",
    val message: String = ""
)

@Serializable
data class AnalyzeResponse(
    val analysis: PhotoAnalysis
)

@Serializable
data class TemplateInfo(
    val fileName: String,
    val storedPath: String,
    val sizeBytes: Long,
    val addedAt: Long
)

package de.spardirekt.agents.pro.model

import kotlinx.serialization.Serializable

enum class ImageCategory {
    PRODUCT_PHOTO,
    PRODUCT_DETAIL_PHOTO,
    PRODUCT_DEMO_PHOTO,
    PRODUCT_DESCRIPTION,
    MARKETPLACE_LISTING,
    UNKNOWN;

    fun badgeLabel(): String = when (this) {
        PRODUCT_PHOTO -> "Product"
        PRODUCT_DETAIL_PHOTO -> "Detail"
        PRODUCT_DEMO_PHOTO -> "Demo"
        PRODUCT_DESCRIPTION -> "Description"
        MARKETPLACE_LISTING -> "Listing"
        UNKNOWN -> "Unknown"
    }
}

enum class VoiceLanguage { DE, RU, OFF }

enum class AppMode { Simple, Advanced }

enum class CreativeMode {
    Auto, Showcase, Demo, Lifestyle, Macro, Problem, Satisfying, Unboxing
}

enum class ProjectStatus {
    Draft, Generating, Ready, Error
}

enum class GenerationStage {
    IDLE,
    PHOTO_ANALYSIS,
    PRODUCT_MODEL,
    CREATIVE_DIRECTOR,
    FINAL_PROMPT,
    FINAL_VALIDATION,
    FINALIZATION,
    DONE,
    FAILED
}

@Serializable
data class ProjectImage(
    val id: String,
    val uri: String,
    val localPath: String? = null,
    val category: ImageCategory = ImageCategory.UNKNOWN,
    val orderIndex: Int = 0
)

@Serializable
data class ConfidenceFact(
    val fact: String,
    val confidence: String, // HIGH / MEDIUM / LOW
    val source: String = ""
)

@Serializable
data class ProductModel(
    val productCategory: String = "",
    val productIdentity: String = "",
    val visualSignature: List<String> = emptyList(),
    val confirmedParts: List<String> = emptyList(),
    val confirmedMaterials: List<String> = emptyList(),
    val confirmedColors: List<String> = emptyList(),
    val confirmedStates: List<String> = emptyList(),
    val confirmedFunctions: List<String> = emptyList(),
    val confirmedAccessories: List<String> = emptyList(),
    val confirmedMarkings: List<String> = emptyList(),
    val visualEvidence: List<String> = emptyList(),
    val descriptionEvidence: List<String> = emptyList(),
    val listingOnlyFacts: List<String> = emptyList(),
    val possibleUseCases: List<String> = emptyList(),
    val unsafeAssumptions: List<String> = emptyList(),
    val highRiskHallucinations: List<String> = emptyList(),
    val imageClassifications: List<ImageClassification> = emptyList(),
    val hasMarketplaceScreenshots: Boolean = false
)

@Serializable
data class ImageClassification(
    val imageId: String,
    val category: String,
    val notes: String = ""
)

@Serializable
data class CreativePlan(
    val strategy: String = "Showcase",
    val heroFeature: String = "",
    val setting: String = "premium studio",
    val salesAngle: String = "",
    val hookIdea: String = "",
    val useHands: Boolean = false,
    val usePeople: Boolean = false,
    val rationale: String = ""
)

@Serializable
data class QualityScores(
    val productFidelity: Int = 0,
    val creativity: Int = 0,
    val physicalPlausibility: Int = 0,
    val voiceoverNaturalness: Int = 0,
    val hookStrength: Int = 0
) {
    fun weakSections(): List<String> {
        val weak = mutableListOf<String>()
        if (productFidelity < 7) weak += "PRODUCT LOCK"
        if (creativity < 7) weak += "SHOT SEQUENCE"
        if (physicalPlausibility < 7) weak += "SHOT SEQUENCE"
        if (voiceoverNaturalness < 7) weak += "VOICEOVER"
        if (hookStrength < 7) weak += "HOOK"
        return weak.distinct()
    }
}

@Serializable
data class GenerationBundle(
    val veoPrompt: String = "",
    val voiceover: String = "",
    val title: String = "",
    val hashtags: List<String> = emptyList(),
    val productModelJson: String = "",
    val creativePlanJson: String = "",
    val analysisJson: String = "",
    val qualityScores: QualityScores = QualityScores(),
    val internalSafetyAudit: String = ""
)

@Serializable
data class AnalysisResult(
    val summary: String = "",
    val classifications: List<ImageClassification> = emptyList(),
    val visualFacts: List<ConfidenceFact> = emptyList(),
    val textFacts: List<ConfidenceFact> = emptyList(),
    val marketplaceDetected: Boolean = false
)

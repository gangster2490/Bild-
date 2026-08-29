package de.spardirekt.agents.pro.generation

import android.content.Context
import de.spardirekt.agents.pro.diagnostics.AppError
import de.spardirekt.agents.pro.diagnostics.DebugLog
import de.spardirekt.agents.pro.model.AnalysisResult
import de.spardirekt.agents.pro.model.AppMode
import de.spardirekt.agents.pro.model.CreativeMode
import de.spardirekt.agents.pro.model.CreativePlan
import de.spardirekt.agents.pro.model.GenerationBundle
import de.spardirekt.agents.pro.model.GenerationStage
import de.spardirekt.agents.pro.model.ImageCategory
import de.spardirekt.agents.pro.model.ProductModel
import de.spardirekt.agents.pro.model.ProjectImage
import de.spardirekt.agents.pro.model.QualityScores
import de.spardirekt.agents.pro.model.VoiceLanguage
import de.spardirekt.agents.pro.network.OpenAiClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GenerationPipeline(
    private val context: Context,
    private val openAi: OpenAiClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }
) {
    data class PipelineInput(
        val projectId: String,
        val images: List<ProjectImage>,
        val optionalWish: String,
        val voiceLanguage: VoiceLanguage,
        val mode: AppMode,
        val creativeMode: CreativeMode,
        val tiktokShopMode: Boolean,
        val apiKey: String,
        val model: String,
        val resumeFrom: GenerationStage = GenerationStage.PHOTO_ANALYSIS,
        val existingAnalysisJson: String = "",
        val existingProductModelJson: String = "",
        val existingCreativePlanJson: String = "",
        val existingVeoPrompt: String = ""
    )

    data class StageUpdate(
        val stage: GenerationStage,
        val analysis: AnalysisResult? = null,
        val productModel: ProductModel? = null,
        val creativePlan: CreativePlan? = null,
        val bundle: GenerationBundle? = null,
        val classifiedImages: List<ProjectImage>? = null
    )

    suspend fun run(
        input: PipelineInput,
        onStage: suspend (StageUpdate) -> Unit
    ): Result<GenerationBundle> {
        return try {
            val dataUrls = input.images.mapNotNull { img ->
                ImageEncoder.toDataUrl(context, img.uri)
            }
            if (dataUrls.isEmpty()) {
                return Result.failure(AppError.Unknown("Нет доступных изображений для анализа."))
            }

            var analysisJson = input.existingAnalysisJson
            var productModelJson = input.existingProductModelJson
            var creativePlanJson = input.existingCreativePlanJson
            var analysis: AnalysisResult? = null
            var productModel: ProductModel? = null
            var creativePlan: CreativePlan? = null

            val startOrder = stageOrder(input.resumeFrom)

            if (GenerationStage.PHOTO_ANALYSIS in startOrder) {
                onStage(StageUpdate(GenerationStage.PHOTO_ANALYSIS))
                analysisJson = openAi.chat(
                    apiKey = input.apiKey,
                    model = input.model,
                    systemPrompt = PromptTemplates.photoAnalysisSystem(),
                    userText = buildAnalysisUserPrompt(input),
                    imageDataUrls = dataUrls,
                    timeoutSeconds = 180,
                    jsonMode = true,
                    maxTokens = 3500
                ).getOrElse { return Result.failure(it as? AppError ?: AppError.Unknown(it.message.orEmpty())) }

                analysis = decodeAnalysis(analysisJson)
                val classified = applyClassifications(input.images, analysis)
                onStage(
                    StageUpdate(
                        stage = GenerationStage.PHOTO_ANALYSIS,
                        analysis = analysis,
                        classifiedImages = classified
                    )
                )
            } else if (analysisJson.isNotBlank()) {
                analysis = decodeAnalysis(analysisJson)
            }

            if (GenerationStage.PRODUCT_MODEL in startOrder) {
                onStage(StageUpdate(GenerationStage.PRODUCT_MODEL, analysis = analysis))
                productModelJson = openAi.chat(
                    apiKey = input.apiKey,
                    model = input.model,
                    systemPrompt = PromptTemplates.productModelSystem(),
                    userText = "Analysis JSON:\n$analysisJson\n\nWish: ${input.optionalWish}\nCreative preference: ${input.creativeMode}",
                    imageDataUrls = emptyList(),
                    timeoutSeconds = 120,
                    jsonMode = true,
                    maxTokens = 3000
                ).getOrElse { return Result.failure(it as? AppError ?: AppError.Unknown(it.message.orEmpty())) }
                productModel = decodeProductModel(productModelJson)
                onStage(StageUpdate(GenerationStage.PRODUCT_MODEL, analysis, productModel))
            } else if (productModelJson.isNotBlank()) {
                productModel = decodeProductModel(productModelJson)
            }

            if (GenerationStage.CREATIVE_DIRECTOR in startOrder) {
                onStage(StageUpdate(GenerationStage.CREATIVE_DIRECTOR, analysis, productModel))
                val forced = if (input.creativeMode != CreativeMode.Auto) {
                    "User selected creative mode: ${input.creativeMode}. Prefer this unless evidence makes it unsafe."
                } else {
                    "User selected Auto. Choose best strategy from evidence. Do not default to Lifestyle."
                }
                creativePlanJson = openAi.chat(
                    apiKey = input.apiKey,
                    model = input.model,
                    systemPrompt = PromptTemplates.creativeDirectorSystem(),
                    userText = "Product model:\n$productModelJson\n\n$forced\nWish: ${input.optionalWish}\nMode: ${input.mode}\nTikTok Shop Mode: ${input.tiktokShopMode}",
                    timeoutSeconds = 120,
                    jsonMode = true,
                    maxTokens = 2000
                ).getOrElse { return Result.failure(it as? AppError ?: AppError.Unknown(it.message.orEmpty())) }
                creativePlan = decodeCreativePlan(creativePlanJson)
                onStage(StageUpdate(GenerationStage.CREATIVE_DIRECTOR, analysis, productModel, creativePlan))
            } else if (creativePlanJson.isNotBlank()) {
                creativePlan = decodeCreativePlan(creativePlanJson)
            }

            var finalJson: String
            if (GenerationStage.FINAL_PROMPT in startOrder || input.existingVeoPrompt.isBlank()) {
                onStage(StageUpdate(GenerationStage.FINAL_PROMPT, analysis, productModel, creativePlan))
                finalJson = openAi.chat(
                    apiKey = input.apiKey,
                    model = input.model,
                    systemPrompt = PromptTemplates.finalPromptSystem(
                        voice = input.voiceLanguage.name,
                        tiktokShop = input.tiktokShopMode
                    ),
                    userText = buildFinalUserPrompt(
                        productModelJson = productModelJson,
                        creativePlanJson = creativePlanJson,
                        analysisJson = analysisJson,
                        wish = input.optionalWish,
                        voice = input.voiceLanguage,
                        tiktok = input.tiktokShopMode
                    ),
                    imageDataUrls = emptyList(),
                    timeoutSeconds = 240,
                    jsonMode = true,
                    temperature = 0.5,
                    maxTokens = 4500
                ).getOrElse { return Result.failure(it as? AppError ?: AppError.Unknown(it.message.orEmpty())) }
            } else {
                finalJson = buildJsonObject {
                    put("veoPrompt", JsonPrimitive(input.existingVeoPrompt))
                    put("voiceover", JsonPrimitive(""))
                    put("title", JsonPrimitive(""))
                    put("hashtags", kotlinx.serialization.json.JsonArray(emptyList()))
                    put("qualityScores", buildJsonObject { })
                    put("internalSafetyAudit", JsonPrimitive(""))
                }.toString()
            }

            var bundle = decodeBundle(finalJson, analysisJson, productModelJson, creativePlanJson)

            // Quality gate + targeted repair
            onStage(StageUpdate(GenerationStage.FINAL_VALIDATION, analysis, productModel, creativePlan, bundle))
            val weak = bundle.qualityScores.weakSections()
            if (weak.isNotEmpty()) {
                DebugLog.d("Targeted repair for: $weak")
                val repaired = openAi.chat(
                    apiKey = input.apiKey,
                    model = input.model,
                    systemPrompt = PromptTemplates.targetedRepairSystem(weak),
                    userText = "Current JSON:\n${json.encodeToString(bundle)}\nProduct model:\n$productModelJson",
                    timeoutSeconds = 120,
                    jsonMode = true,
                    maxTokens = 4000,
                    maxAttempts = 1
                ).getOrNull()
                if (repaired != null) {
                    bundle = decodeBundle(repaired, analysisJson, productModelJson, creativePlanJson)
                }
            }

            onStage(StageUpdate(GenerationStage.FINALIZATION, analysis, productModel, creativePlan, bundle))
            val marketplace = productModel?.hasMarketplaceScreenshots == true ||
                analysis?.marketplaceDetected == true
            val cleaned = PromptCleanup.finalize(
                rawPrompt = bundle.veoPrompt,
                voiceover = bundle.voiceover,
                title = bundle.title,
                hashtags = bundle.hashtags,
                voiceLanguage = input.voiceLanguage.name,
                marketplace = marketplace
            )
            val completeness = PromptCleanup.validateCompleteness(cleaned.veoPrompt, cleaned.hashtags)
            if (completeness.any { it.startsWith("missing_") }) {
                // Local tail repair only — re-run finalize with injections already applied
                DebugLog.d("Completeness issues: $completeness")
            }

            val finalBundle = bundle.copy(
                veoPrompt = cleaned.veoPrompt,
                voiceover = cleaned.voiceover,
                title = cleaned.title,
                hashtags = cleaned.hashtags,
                analysisJson = analysisJson,
                productModelJson = productModelJson,
                creativePlanJson = creativePlanJson,
                internalSafetyAudit = bundle.internalSafetyAudit
            )
            onStage(StageUpdate(GenerationStage.DONE, analysis, productModel, creativePlan, finalBundle))
            Result.success(finalBundle)
        } catch (t: Throwable) {
            Result.failure(t as? AppError ?: AppError.Unknown(t.message.orEmpty()))
        }
    }

    private fun stageOrder(from: GenerationStage): Set<GenerationStage> {
        val all = listOf(
            GenerationStage.PHOTO_ANALYSIS,
            GenerationStage.PRODUCT_MODEL,
            GenerationStage.CREATIVE_DIRECTOR,
            GenerationStage.FINAL_PROMPT
        )
        val idx = all.indexOf(from).coerceAtLeast(0)
        return all.drop(idx).toSet()
    }

    private fun buildAnalysisUserPrompt(input: PipelineInput): String {
        val list = input.images.mapIndexed { i, img ->
            "img_${i + 1}: uploaded media (treat unknown category initially)"
        }.joinToString("\n")
        return """
Analyze all images together. No Primary/Main reference concept.
Images:
$list
Optional wish: ${input.optionalWish.ifBlank { "(none)" }}
TikTok Shop Mode: ${input.tiktokShopMode}
""".trimIndent()
    }

    private fun buildFinalUserPrompt(
        productModelJson: String,
        creativePlanJson: String,
        analysisJson: String,
        wish: String,
        voice: VoiceLanguage,
        tiktok: Boolean
    ): String {
        return """
Create the final VEO 3.1 package.

PRODUCT MODEL:
$productModelJson

CREATIVE PLAN:
$creativePlanJson

ANALYSIS SUMMARY:
$analysisJson

OPTIONAL WISH: ${wish.ifBlank { "(none)" }}
VOICE: ${voice.name}
TIKTOK SHOP MODE: $tiktok

${PromptTemplates.PRODUCT_FIDELITY_CORE}

Remember: do not resend or rely on inventing unseen mechanisms.
""".trimIndent()
    }

    private fun decodeAnalysis(raw: String): AnalysisResult {
        return runCatching {
            json.decodeFromString(AnalysisResult.serializer(), raw)
        }.getOrElse {
            AnalysisResult(summary = raw.take(500))
        }
    }

    private fun decodeProductModel(raw: String): ProductModel {
        return runCatching {
            json.decodeFromString(ProductModel.serializer(), raw)
        }.getOrElse { ProductModel(productIdentity = "unknown") }
    }

    private fun decodeCreativePlan(raw: String): CreativePlan {
        return runCatching {
            json.decodeFromString(CreativePlan.serializer(), raw)
        }.getOrElse { CreativePlan() }
    }

    private fun decodeBundle(
        raw: String,
        analysisJson: String,
        productModelJson: String,
        creativePlanJson: String
    ): GenerationBundle {
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val hashtags = obj["hashtags"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull
            }.orEmpty()
            val scoresObj = obj["qualityScores"]?.jsonObject
            val scores = QualityScores(
                productFidelity = scoresObj?.get("productFidelity")?.jsonPrimitive?.intOrNull ?: 8,
                creativity = scoresObj?.get("creativity")?.jsonPrimitive?.intOrNull ?: 8,
                physicalPlausibility = scoresObj?.get("physicalPlausibility")?.jsonPrimitive?.intOrNull ?: 8,
                voiceoverNaturalness = scoresObj?.get("voiceoverNaturalness")?.jsonPrimitive?.intOrNull ?: 8,
                hookStrength = scoresObj?.get("hookStrength")?.jsonPrimitive?.intOrNull ?: 8
            )
            GenerationBundle(
                veoPrompt = obj["veoPrompt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                voiceover = obj["voiceover"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                hashtags = hashtags,
                productModelJson = productModelJson,
                creativePlanJson = creativePlanJson,
                analysisJson = analysisJson,
                qualityScores = scores,
                internalSafetyAudit = obj["internalSafetyAudit"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
        }.getOrElse {
            GenerationBundle(
                veoPrompt = raw,
                analysisJson = analysisJson,
                productModelJson = productModelJson,
                creativePlanJson = creativePlanJson
            )
        }
    }

    private fun applyClassifications(
        images: List<ProjectImage>,
        analysis: AnalysisResult
    ): List<ProjectImage> {
        return images.mapIndexed { index, img ->
            val id = "img_${index + 1}"
            val catName = analysis.classifications.firstOrNull {
                it.imageId.equals(id, true) || it.imageId == img.id
            }?.category
            val category = runCatching {
                ImageCategory.valueOf(catName ?: ImageCategory.UNKNOWN.name)
            }.getOrDefault(ImageCategory.UNKNOWN)
            img.copy(category = category, orderIndex = index)
        }
    }
}

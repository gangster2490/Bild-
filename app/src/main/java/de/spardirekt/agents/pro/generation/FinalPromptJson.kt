package de.spardirekt.agents.pro.generation

import de.spardirekt.agents.pro.diagnostics.DebugLog
import de.spardirekt.agents.pro.model.GenerationBundle
import de.spardirekt.agents.pro.model.QualityScores
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses FINAL_PROMPT model JSON into a GenerationBundle.
 * Repairs the common illegal-newline-in-string failure instead of
 * stuffing the raw JSON blob into veoPrompt.
 */
object FinalPromptJson {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val sectionOrder = listOf(
        "FORMAT", "REFERENCES", "PRODUCT LOCK", "SETTING", "SHOT SEQUENCE",
        "ON-SCREEN TEXT", "VOICEOVER", "AUDIO", "CRITICAL", "NEGATIVE PROMPT",
        "TITLE", "HASHTAGS"
    )

    fun decode(
        raw: String,
        analysisJson: String = "",
        productModelJson: String = "",
        creativePlanJson: String = ""
    ): GenerationBundle {
        val attempts = listOf(
            JsonExtractor.extract(raw),
            JsonExtractor.repair(raw)
        ).distinct()

        for (payload in attempts) {
            val bundle = runCatching {
                parseObject(
                    json.parseToJsonElement(payload).jsonObject,
                    analysisJson,
                    productModelJson,
                    creativePlanJson
                )
            }.getOrNull() ?: continue

            if (bundle.veoPrompt.isBlank()) continue
            if (!looksLikeRawJson(bundle.veoPrompt)) return bundle

            val salvaged = JsonExtractor.salvageVeoPrompt(bundle.veoPrompt)
                ?: JsonExtractor.salvageVeoPrompt(raw)
            if (salvaged != null) return bundle.copy(veoPrompt = salvaged)
        }

        val salvaged = JsonExtractor.salvageVeoPrompt(raw)
        if (salvaged != null) {
            DebugLog.d("FINAL_PROMPT JSON parse failed; salvaged FORMAT…HASHTAGS block")
            return GenerationBundle(
                veoPrompt = salvaged,
                analysisJson = analysisJson,
                productModelJson = productModelJson,
                creativePlanJson = creativePlanJson
            )
        }

        DebugLog.d("FINAL_PROMPT JSON parse failed; no salvageable VEO prompt")
        return GenerationBundle(
            veoPrompt = "",
            analysisJson = analysisJson,
            productModelJson = productModelJson,
            creativePlanJson = creativePlanJson
        )
    }

    private fun parseObject(
        obj: JsonObject,
        analysisJson: String,
        productModelJson: String,
        creativePlanJson: String
    ): GenerationBundle {
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
        return GenerationBundle(
            veoPrompt = readVeoPromptField(obj),
            voiceover = obj["voiceover"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            hashtags = hashtags,
            productModelJson = productModelJson,
            creativePlanJson = creativePlanJson,
            analysisJson = analysisJson,
            qualityScores = scores,
            internalSafetyAudit = obj["internalSafetyAudit"]?.jsonPrimitive?.contentOrNull.orEmpty()
        )
    }

    private fun readVeoPromptField(obj: JsonObject): String {
        val candidates = listOf("veoPrompt", "mainPrompt", "finalPrompt", "prompt", "geminiPrompt")
            .mapNotNull { key ->
                obj[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            }
        // Prefer a candidate that already looks like the 12-section VEO body.
        candidates.firstOrNull { looksLikeVeoBody(it) }?.let { return it }
        candidates.firstOrNull()?.let { return it }

        runCatching {
            val lines = obj["veoPrompt"]?.jsonArray?.mapNotNull { el ->
                el.jsonPrimitive.contentOrNull
                    ?: el.jsonObject.values.firstOrNull()?.jsonPrimitive?.contentOrNull
            }.orEmpty()
            if (lines.isNotEmpty()) {
                val joined = lines.joinToString("\n").trim()
                if (joined.isNotEmpty()) return joined
            }
        }
        // Legacy: model sometimes returns mainPrompt as array / sections object
        runCatching {
            val lines = obj["mainPrompt"]?.jsonArray?.mapNotNull { el ->
                el.jsonPrimitive.contentOrNull
            }.orEmpty()
            if (lines.isNotEmpty()) return lines.joinToString("\n").trim()
        }
        val sections = obj["veoSections"]?.jsonObject
            ?: obj["mainPromptSections"]?.jsonObject
            ?: return ""
        val joined = sectionOrder.joinToString("\n\n") { name ->
            val body = sections[name]?.jsonPrimitive?.contentOrNull
                ?: sections[name.replace(" ", "_")]?.jsonPrimitive?.contentOrNull
                ?: ""
            "$name\n${body.trim()}"
        }.trim()
        return if (joined.contains("FORMAT")) joined + "\n" else ""
    }

    private fun looksLikeVeoBody(text: String): Boolean {
        val t = text.trim()
        if (t.startsWith("{")) return false
        return Regex("""(?im)^FORMAT\b""").containsMatchIn(t) &&
            Regex("""(?im)^PRODUCT LOCK\b""").containsMatchIn(t) &&
            Regex("""(?im)^HASHTAGS\b""").containsMatchIn(t)
    }

    private fun looksLikeRawJson(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("{") && (
            t.contains("\"veoPrompt\"") ||
                t.contains("\"mainPrompt\"") ||
                t.contains("\"finalPrompt\"")
            )
    }
}

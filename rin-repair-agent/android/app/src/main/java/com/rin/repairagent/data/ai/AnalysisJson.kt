package com.rin.repairagent.data.ai

import com.rin.repairagent.data.model.PhotoAnalysis
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Parses AI text into [PhotoAnalysis] and guarantees the internal result
 * round-trips as strict, valid JSON (no trailing junk, no markdown fences).
 */
object AnalysisJson {

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = false
    }

    /** Canonical JSON string for a [PhotoAnalysis] — always valid JSON. */
    fun encode(analysis: PhotoAnalysis): String =
        json.encodeToString(PhotoAnalysis.serializer(), analysis)

    fun decode(validJson: String): PhotoAnalysis =
        json.decodeFromString(PhotoAnalysis.serializer(), validJson)

    /**
     * Extract a JSON object from model output, normalize fields, then
     * re-encode/decode so callers always hold a structure that serializes
     * to valid JSON.
     */
    fun parseAiResult(raw: String, photoNumber: Int): PhotoAnalysis {
        val obj = extractJsonObject(raw)
        val normalized = normalize(obj, photoNumber)
        val validJson = encode(normalized)
        // Round-trip proves the internal result is valid JSON.
        return decode(validJson)
    }

    fun extractJsonObject(text: String): JsonObject {
        val cleaned = text
            .replace("```json", "", ignoreCase = true)
            .replace("```JSON", "")
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end > start) {
            "AI не вернул валидный JSON-объект"
        }
        val slice = cleaned.substring(start, end + 1)
        val element = runCatching { json.parseToJsonElement(slice) }
            .getOrElse { e ->
                error("AI вернул повреждённый JSON: ${e.message ?: "parse error"}")
            }
        require(element is JsonObject) { "AI вернул JSON, но не объект {...}" }
        return element
    }

    fun normalize(obj: JsonObject, photoNumber: Int): PhotoAnalysis {
        fun str(key: String, default: String = ""): String {
            val el = obj[key] ?: return default
            return when (el) {
                is JsonPrimitive -> el.contentOrNull?.trim() ?: default
                is JsonArray -> el.mapNotNull {
                    it.jsonPrimitive.contentOrNull?.trim()
                }.filter { it.isNotEmpty() }.joinToString(", ").ifBlank { default }
                else -> default
            }
        }

        fun list(key: String): List<String> {
            val el = obj[key] ?: return emptyList()
            return when (el) {
                is JsonArray -> el.mapNotNull { item ->
                    when (item) {
                        is JsonPrimitive -> item.contentOrNull?.trim()
                        else -> item.toString().trim().trim('"')
                    }
                }.filter { it.isNotEmpty() }
                is JsonPrimitive -> el.contentOrNull
                    ?.split(',', ';')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                else -> emptyList()
            }
        }

        fun number(key: String, default: Double = 0.0): Double {
            val el = obj[key] ?: return default
            if (el !is JsonPrimitive) return default
            return el.doubleOrNull
                ?: el.contentOrNull?.toDoubleOrNull()
                ?: default
        }

        fun bool(key: String, default: Boolean = false): Boolean {
            val el = obj[key] ?: return default
            if (el !is JsonPrimitive) return default
            el.booleanOrNull?.let { return it }
            return when (el.contentOrNull?.trim()?.lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> default
            }
        }

        fun intField(key: String, default: Int): Int {
            val el = obj[key] ?: return default
            if (el !is JsonPrimitive) return default
            return el.intOrNull
                ?: el.contentOrNull?.toIntOrNull()
                ?: default
        }

        val confidence = number("confidence").coerceIn(0.0, 1.0)
        var needsReview = bool("needsManualReview") || confidence < 0.55
        var instruction = str("beginnerInstruction")
        if (instruction.isBlank() || (needsReview && confidence < 0.4)) {
            instruction = instruction.ifBlank {
                "Точное действие по этой фотографии определить невозможно. Требуется проверка мастером."
            }
            needsReview = true
        }

        return PhotoAnalysis(
            photoNumber = intField("photoNumber", photoNumber),
            visibleObjects = list("visibleObjects"),
            visibleAction = str("visibleAction"),
            repairStage = str("repairStage"),
            tools = list("tools"),
            beginnerInstruction = instruction,
            importantWarning = str("importantWarning", "Требует проверки"),
            confidence = confidence,
            needsManualReview = needsReview,
            beginnerInstructionEn = str("beginnerInstructionEn", instruction),
            importantWarningEn = str(
                "importantWarningEn",
                str("importantWarning", "Requires verification")
            ),
            repairStageEn = str("repairStageEn", str("repairStage")),
            visibleActionEn = str("visibleActionEn", str("visibleAction"))
        )
    }

    /** Schema reminder embedded in API calls so providers emit a JSON object. */
    const val JSON_ONLY_SYSTEM_SUFFIX =
        "Return ONLY one valid JSON object matching the required schema. " +
            "No markdown fences, no commentary, no trailing text."
}

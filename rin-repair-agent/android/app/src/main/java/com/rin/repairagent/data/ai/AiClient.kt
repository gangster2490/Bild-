package com.rin.repairagent.data.ai

import android.content.Context
import android.util.Base64
import com.rin.repairagent.data.model.AiProvider
import com.rin.repairagent.data.model.ApiKeyCheckResponse
import com.rin.repairagent.data.model.PhotoAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Calls OpenAI / Gemini directly from the device. No app backend required.
 * API keys must never be logged.
 */
class AiClient(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun checkKey(apiKey: String, provider: AiProvider): ApiKeyCheckResponse =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isEmpty()) {
                return@withContext ApiKeyCheckResponse(false, provider.name, "API-ключ пустой")
            }
            when (provider) {
                AiProvider.OPENAI -> checkOpenAi(key)
                AiProvider.GEMINI -> checkGemini(key)
            }
        }

    suspend fun analyzePhoto(
        apiKey: String,
        provider: AiProvider,
        imageFile: File,
        photoNumber: Int,
        projectTitle: String,
        productModel: String
    ): PhotoAnalysis = withContext(Dispatchers.IO) {
        require(imageFile.exists() && imageFile.length() > 0L) { "Файл фотографии пустой" }
        val prompt = loadPrompt(projectTitle, productModel, photoNumber)
        val b64 = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
        val raw = when (provider) {
            AiProvider.OPENAI -> analyzeOpenAi(apiKey.trim(), prompt, b64)
            AiProvider.GEMINI -> analyzeGemini(apiKey.trim(), prompt, b64)
        }
        normalize(extractJsonObject(raw), photoNumber)
    }

    private fun checkOpenAi(apiKey: String): ApiKeyCheckResponse {
        val req = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        http.newCall(req).execute().use { res ->
            return when (res.code) {
                200 -> ApiKeyCheckResponse(true, "OPENAI", "Ключ OpenAI действителен")
                401 -> ApiKeyCheckResponse(false, "OPENAI", "Неверный API-ключ OpenAI")
                else -> ApiKeyCheckResponse(false, "OPENAI", "OpenAI недоступен (HTTP ${res.code})")
            }
        }
    }

    private fun checkGemini(apiKey: String): ApiKeyCheckResponse {
        val url = "https://generativelanguage.googleapis.com/v1/models?key=${apiKey.urlEnc()}"
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { res ->
            return when (res.code) {
                200 -> ApiKeyCheckResponse(true, "GEMINI", "Ключ Gemini действителен")
                400, 403 -> ApiKeyCheckResponse(false, "GEMINI", "Неверный API-ключ Gemini")
                else -> ApiKeyCheckResponse(false, "GEMINI", "Gemini недоступен (HTTP ${res.code})")
            }
        }
    }

    private fun analyzeOpenAi(apiKey: String, prompt: String, b64: String): String {
        val body = buildJsonObject {
            put("model", "gpt-4o-mini")
            put("temperature", 0.2)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put(
                                "image_url",
                                buildJsonObject {
                                    put("url", "data:image/jpeg;base64,$b64")
                                }
                            )
                        })
                    })
                })
            })
        }.toString()

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (res.code == 401) error("Неверный API-ключ OpenAI")
            if (!res.isSuccessful) error("OpenAI ошибка: HTTP ${res.code}")
            val root = json.parseToJsonElement(text).jsonObject
            return root["choices"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull
                ?: error("Пустой ответ OpenAI")
        }
    }

    private fun analyzeGemini(apiKey: String, prompt: String, b64: String): String {
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                        add(buildJsonObject {
                            put(
                                "inline_data",
                                buildJsonObject {
                                    put("mime_type", "image/jpeg")
                                    put("data", b64)
                                }
                            )
                        })
                    })
                })
            })
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                }
            )
        }.toString()

        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${apiKey.urlEnc()}"
        val req = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (res.code == 400 || res.code == 403) error("Неверный API-ключ Gemini")
            if (!res.isSuccessful) error("Gemini ошибка: HTTP ${res.code}")
            val root = json.parseToJsonElement(text).jsonObject
            val parts = root["candidates"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?: error("Пустой ответ Gemini")
            return parts.joinToString("\n") {
                it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }
        }
    }

    private fun loadPrompt(title: String, model: String, photoNumber: Int): String {
        val raw = context.assets.open("prompts/analyze_photo.txt").bufferedReader().use { it.readText() }
        return raw
            .replace("{{title}}", title)
            .replace("{{model}}", model)
            .replace("{{photoNumber}}", photoNumber.toString())
    }

    private fun extractJsonObject(text: String): JsonObject {
        val cleaned = text.replace("```json", "", ignoreCase = true).replace("```", "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI не вернул JSON" }
        return json.parseToJsonElement(cleaned.substring(start, end + 1)).jsonObject
    }

    private fun normalize(obj: JsonObject, photoNumber: Int): PhotoAnalysis {
        fun str(key: String, default: String = "") =
            obj[key]?.jsonPrimitive?.contentOrNull?.trim() ?: default

        fun list(key: String): List<String> =
            (obj[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

        val confidence = (obj["confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0).coerceIn(0.0, 1.0)
        var needsReview = obj["needsManualReview"]?.jsonPrimitive?.booleanOrNull == true || confidence < 0.55
        var instruction = str("beginnerInstruction")
        if (instruction.isBlank() || (needsReview && confidence < 0.4)) {
            instruction = instruction.ifBlank {
                "Точное действие по этой фотографии определить невозможно. Требуется проверка мастером."
            }
            needsReview = true
        }
        return PhotoAnalysis(
            photoNumber = obj["photoNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: photoNumber,
            visibleObjects = list("visibleObjects"),
            visibleAction = str("visibleAction"),
            repairStage = str("repairStage"),
            tools = list("tools"),
            beginnerInstruction = instruction,
            importantWarning = str("importantWarning", "Требует проверки"),
            confidence = confidence,
            needsManualReview = needsReview,
            beginnerInstructionEn = str("beginnerInstructionEn", instruction),
            importantWarningEn = str("importantWarningEn", str("importantWarning", "Requires verification")),
            repairStageEn = str("repairStageEn", str("repairStage")),
            visibleActionEn = str("visibleActionEn", str("visibleAction"))
        )
    }

    private fun String.urlEnc(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}

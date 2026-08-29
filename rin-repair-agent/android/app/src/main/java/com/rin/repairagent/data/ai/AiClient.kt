package com.rin.repairagent.data.ai

import android.content.Context
import android.util.Base64
import com.rin.repairagent.data.model.AiProvider
import com.rin.repairagent.data.model.ApiKeyCheckResponse
import com.rin.repairagent.data.model.PhotoAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
import kotlin.math.min
import kotlin.random.Random

/**
 * Calls OpenAI / Gemini directly from the device. No app backend required.
 * API keys must never be logged. HTTP 429 is retried with backoff.
 * Analysis results are always normalized into valid JSON via [AnalysisJson].
 */
class AiClient(private val context: Context) {

    private val json = AnalysisJson.json

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
        val systemPrompt = loadSystemPrompt()
        val userPrompt = loadUserPrompt(projectTitle, productModel, photoNumber)
        val b64 = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
        val raw = when (provider) {
            AiProvider.OPENAI -> analyzeOpenAi(apiKey.trim(), systemPrompt, userPrompt, b64)
            AiProvider.GEMINI -> analyzeGemini(apiKey.trim(), systemPrompt, userPrompt, b64)
        }
        // Internal result is always a PhotoAnalysis that encodes to valid JSON.
        AnalysisJson.parseAiResult(raw, photoNumber)
    }

    /** Same as [analyzePhoto], but returns the canonical JSON string. */
    suspend fun analyzePhotoJson(
        apiKey: String,
        provider: AiProvider,
        imageFile: File,
        photoNumber: Int,
        projectTitle: String,
        productModel: String
    ): String {
        val analysis = analyzePhoto(
            apiKey, provider, imageFile, photoNumber, projectTitle, productModel
        )
        return AnalysisJson.encode(analysis)
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
                429 -> ApiKeyCheckResponse(
                    false,
                    "OPENAI",
                    "Лимит запросов OpenAI (HTTP 429). Подождите и повторите проверку ключа."
                )
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
                429 -> ApiKeyCheckResponse(
                    false,
                    "GEMINI",
                    "Лимит запросов Gemini (HTTP 429). Подождите и повторите проверку ключа."
                )
                else -> ApiKeyCheckResponse(false, "GEMINI", "Gemini недоступен (HTTP ${res.code})")
            }
        }
    }

    private suspend fun analyzeOpenAi(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        b64: String
    ): String {
        val body = buildJsonObject {
            put("model", "gpt-4o-mini")
            put("temperature", 0.2)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", userPrompt)
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

        val text = executeWithRateLimitRetry("OpenAI", req)
        if (text.code == 401) error("Неверный API-ключ OpenAI")
        if (text.code !in 200..299) {
            error(friendlyHttpError("OpenAI", text.code, text.body))
        }
        val root = json.parseToJsonElement(text.body).jsonObject
        return root["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
            ?: error("Пустой ответ OpenAI")
    }

    private suspend fun analyzeGemini(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        b64: String
    ): String {
        val body = buildJsonObject {
            put(
                "system_instruction",
                buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemPrompt) })
                    })
                }
            )
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", userPrompt) })
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

        val text = executeWithRateLimitRetry("Gemini", req)
        if (text.code == 400 || text.code == 403) error("Неверный API-ключ Gemini")
        if (text.code !in 200..299) {
            error(friendlyHttpError("Gemini", text.code, text.body))
        }
        val root = json.parseToJsonElement(text.body).jsonObject
        val parts = root["candidates"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?: error("Пустой ответ Gemini")
        return parts.joinToString("\n") {
            it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
    }

    private data class HttpText(val code: Int, val body: String, val retryAfterSeconds: Long?)

    private suspend fun executeWithRateLimitRetry(
        providerLabel: String,
        request: Request,
        maxAttempts: Int = MAX_RATE_LIMIT_ATTEMPTS
    ): HttpText {
        var attempt = 0
        while (true) {
            attempt++
            val result = http.newCall(request).execute().use { res ->
                HttpText(
                    code = res.code,
                    body = res.body?.string().orEmpty(),
                    retryAfterSeconds = parseRetryAfterSeconds(res.header("Retry-After"))
                )
            }

            if (result.code != 429 && result.code != 503) {
                return result
            }

            if (attempt >= maxAttempts) {
                val waitHint = result.retryAfterSeconds?.let { " Повторите через ~$it с." }.orEmpty()
                throw RateLimitException(
                    message = "$providerLabel: превышен лимит запросов (HTTP ${result.code}). " +
                        "Фотографии сохранены — подождите и нажмите «Повторить анализ».$waitHint",
                    httpCode = result.code,
                    retryAfterSeconds = result.retryAfterSeconds
                )
            }

            delay(computeBackoffMs(attempt, result.retryAfterSeconds))
        }
    }

    private fun loadSystemPrompt(): String {
        val fromAssets = runCatching {
            context.assets.open("prompts/system_prompt.txt").bufferedReader().use { it.readText() }
        }.getOrNull()?.trim().orEmpty()
        return if (fromAssets.isNotBlank()) {
            fromAssets
        } else {
            "You are the RIN Repair Instruction Agent. ${AnalysisJson.JSON_ONLY_SYSTEM_SUFFIX}"
        }
    }

    private fun loadUserPrompt(title: String, model: String, photoNumber: Int): String {
        val raw = context.assets.open("prompts/analyze_photo.txt").bufferedReader().use { it.readText() }
        return raw
            .replace("{{title}}", title)
            .replace("{{model}}", model)
            .replace("{{photoNumber}}", photoNumber.toString())
    }

    private fun String.urlEnc(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    companion object {
        const val MAX_RATE_LIMIT_ATTEMPTS = 5
        const val INTER_PHOTO_DELAY_MS = 1_200L

        fun parseRetryAfterSeconds(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            raw.trim().toLongOrNull()?.let { return it.coerceIn(1L, 120L) }
            return null
        }

        fun computeBackoffMs(attempt: Int, retryAfterSeconds: Long?): Long {
            if (retryAfterSeconds != null) {
                return (retryAfterSeconds * 1000L).coerceIn(500L, 60_000L)
            }
            val base = (1000L * (1L shl (attempt - 1).coerceAtMost(4)))
            val jitter = Random.nextLong(0L, 400L)
            return min(base + jitter, 30_000L)
        }

        fun friendlyHttpError(provider: String, code: Int, body: String): String {
            val snippet = body
                .replace('\n', ' ')
                .take(160)
                .ifBlank { "без деталей" }
            return when (code) {
                429 -> "$provider: лимит запросов (HTTP 429). Подождите и повторите анализ."
                500, 502, 503, 504 -> "$provider временно недоступен (HTTP $code). Повторите позже."
                else -> "$provider ошибка: HTTP $code ($snippet)"
            }
        }
    }
}

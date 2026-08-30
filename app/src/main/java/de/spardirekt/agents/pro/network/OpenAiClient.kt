package de.spardirekt.agents.pro.network

import de.spardirekt.agents.pro.diagnostics.AppError
import de.spardirekt.agents.pro.diagnostics.ErrorMapper
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenAiClient(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
) {
    fun clientFor(timeoutSeconds: Long): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds + 30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun chat(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String> = emptyList(),
        timeoutSeconds: Long,
        jsonMode: Boolean = true,
        temperature: Double = 0.4,
        maxTokens: Int = 4096,
        maxAttempts: Int = 2
    ): Result<String> {
        var lastError: AppError? = null
        repeat(maxAttempts) { attempt ->
            val result = runCatching {
                executeChat(
                    apiKey = apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    userText = userText,
                    imageDataUrls = imageDataUrls,
                    timeoutSeconds = timeoutSeconds,
                    jsonMode = jsonMode,
                    temperature = temperature,
                    maxTokens = maxTokens
                )
            }
            if (result.isSuccess) return result
            val err = ErrorMapper.fromThrowable(result.exceptionOrNull())
            lastError = err
            if (!err.retryable || attempt == maxAttempts - 1) {
                return Result.failure(err)
            }
            delay(1200L * (attempt + 1))
        }
        return Result.failure(lastError ?: AppError.Unknown("Unknown OpenAI error"))
    }

    private fun executeChat(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        imageDataUrls: List<String>,
        timeoutSeconds: Long,
        jsonMode: Boolean,
        temperature: Double,
        maxTokens: Int
    ): String {
        val userContent = buildJsonArray {
            add(buildJsonObject {
                put("type", JsonPrimitive("text"))
                put("text", JsonPrimitive(userText))
            })
            imageDataUrls.forEach { url ->
                add(buildJsonObject {
                    put("type", JsonPrimitive("image_url"))
                    put("image_url", buildJsonObject {
                        put("url", JsonPrimitive(url))
                        put("detail", JsonPrimitive(OpenAiModelCatalog.imageDetail(model)))
                    })
                })
            }
        }

        val messages = listOf(
            ChatMessage(role = "system", content = JsonPrimitive(systemPrompt)),
            ChatMessage(role = "user", content = userContent)
        )

        val gpt5 = OpenAiModelCatalog.isGpt5Family(model)
        val requestBody = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("messages", json.encodeToJsonElement(messages))
            if (jsonMode) {
                put("response_format", buildJsonObject {
                    put("type", JsonPrimitive("json_object"))
                })
            }
            if (gpt5) {
                put("max_completion_tokens", JsonPrimitive(maxTokens))
                put("reasoning_effort", JsonPrimitive(OpenAiModelCatalog.reasoningEffort(model)))
            } else {
                put("temperature", JsonPrimitive(temperature))
                put("max_tokens", JsonPrimitive(maxTokens))
            }
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        clientFor(timeoutSeconds).newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ErrorMapper.fromHttp(response.code, raw)
            }
            val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), raw)
            val content = parsed.choices.firstOrNull()?.message?.content
                ?: throw AppError.Unknown("Empty model response")
            return content
        }
    }

    fun testConnection(apiKey: String): Result<String> {
        return runCatching {
            val request = Request.Builder()
                .url("https://api.openai.com/v1/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            clientFor(30).newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ErrorMapper.fromHttp(response.code, raw)
                }
                val models = json.decodeFromString(ModelsResponse.serializer(), raw)
                val hasGpt = models.data.any { it.id.contains("gpt") }
                if (hasGpt) "Соединение успешно" else "Соединение успешно (модели доступны)"
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(ErrorMapper.fromThrowable(it)) }
        )
    }
}

package com.rin.repairagent.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.rin.repairagent.data.model.AnalyzeResponse
import com.rin.repairagent.data.model.ApiKeyCheckResponse
import com.rin.repairagent.data.model.ExportResult
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

interface RinApi {
    @GET("/health")
    suspend fun health(): Map<String, String>

    @POST("/api/check-key")
    suspend fun checkKey(@Body body: Map<String, String>): ApiKeyCheckResponse

    @Multipart
    @POST("/api/analyze-photo")
    suspend fun analyzePhoto(
        @Part image: MultipartBody.Part,
        @Part("provider") provider: RequestBody,
        @Part("apiKey") apiKey: RequestBody,
        @Part("photoNumber") photoNumber: RequestBody,
        @Part("projectTitle") projectTitle: RequestBody,
        @Part("productModel") productModel: RequestBody,
        @Part("language") language: RequestBody
    ): AnalyzeResponse

    @Multipart
    @POST("/api/export")
    suspend fun exportDocuments(
        @Part template: MultipartBody.Part,
        @Part photos: List<MultipartBody.Part>,
        @Part("payload") payload: RequestBody
    ): ExportResult
}

object ApiClientFactory {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun create(baseUrl: String): RinApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            // Never log request/response bodies — they may contain API keys.
            redactHeader("Authorization")
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RinApi::class.java)
    }
}

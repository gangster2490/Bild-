package com.rin.repairagent.data.ai

/**
 * Thrown when the AI provider returns HTTP 429 after retries are exhausted,
 * or when the response is a hard rate-limit rejection.
 */
class RateLimitException(
    message: String,
    val httpCode: Int = 429,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null
) : Exception(message, cause)

package com.rin.repairagent.data.ai

fun formatAnalysisError(error: Throwable, savedLocally: Boolean): String {
    val base = when (error) {
        is RateLimitException -> error.message
            ?: "Лимит запросов API (HTTP 429). Подождите и повторите анализ."
        else -> error.message ?: "Ошибка анализа"
    }
    return if (savedLocally && error !is RateLimitException) {
        "Фотографии сохранены. Анализ можно повторить позже: $base"
    } else if (savedLocally && error is RateLimitException) {
        base
    } else {
        base
    }
}

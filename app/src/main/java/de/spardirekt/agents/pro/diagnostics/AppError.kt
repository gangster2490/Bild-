package de.spardirekt.agents.pro.diagnostics

sealed class AppError(
    override val message: String,
    val userMessage: String,
    val detail: String = message,
    val retryable: Boolean = false
) : Exception(message) {
    class InvalidApiKey(detail: String = "") : AppError(
        message = "invalid_api_key",
        userMessage = "API ключ недействителен.",
        detail = detail
    )
    class RateLimited(detail: String = "") : AppError(
        message = "rate_limited",
        userMessage = "Недостаточно API-кредита или превышен лимит.",
        detail = detail,
        retryable = true
    )
    class Timeout(detail: String = "") : AppError(
        message = "timeout",
        userMessage = "OpenAI не ответил вовремя.",
        detail = detail,
        retryable = true
    )
    class Network(detail: String = "") : AppError(
        message = "network",
        userMessage = "Нет соединения с OpenAI.",
        detail = detail,
        retryable = true
    )
    class Server(detail: String = "") : AppError(
        message = "server",
        userMessage = "Временная ошибка OpenAI.",
        detail = detail,
        retryable = true
    )
    class Unknown(detail: String) : AppError(
        message = "unknown",
        userMessage = "Не удалось создать промпт.",
        detail = detail,
        retryable = false
    )
}

object ErrorMapper {
    fun fromHttp(code: Int, body: String): AppError {
        val safe = sanitize(body)
        return when (code) {
            401, 403 -> AppError.InvalidApiKey(safe)
            429 -> AppError.RateLimited(safe)
            in 500..599 -> AppError.Server("HTTP $code: $safe")
            else -> AppError.Unknown("HTTP $code: $safe")
        }
    }

    fun fromThrowable(t: Throwable?): AppError {
        if (t is AppError) return t
        val msg = t?.message.orEmpty()
        val lower = msg.lowercase()
        return when {
            t is java.net.SocketTimeoutException ||
                lower.contains("timeout") ||
                lower.contains("timed out") -> AppError.Timeout(sanitize(msg))
            t is java.net.UnknownHostException ||
                t is java.io.IOException ||
                lower.contains("unable to resolve") ||
                lower.contains("failed to connect") -> AppError.Network(sanitize(msg))
            else -> AppError.Unknown(sanitize(msg.ifBlank { t?.javaClass?.simpleName.orEmpty() }))
        }
    }

    fun sanitize(text: String): String {
        return text
            .replace(Regex("sk-[A-Za-z0-9_\\-]{10,}"), "sk-••••")
            .replace(Regex("Bearer\\s+\\S+"), "Bearer ••••")
            .take(1200)
    }
}

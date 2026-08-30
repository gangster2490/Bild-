package de.spardirekt.agents.pro.network

data class OpenAiModelOption(
    val id: String,
    val label: String,
    val hint: String,
    val recommended: Boolean = false
)

object OpenAiModelCatalog {
    const val DEFAULT = "gpt-5.6-sol"

    val options: List<OpenAiModelOption> = listOf(
        OpenAiModelOption(
            id = "gpt-5.6-sol",
            label = "GPT-5.6 Sol",
            hint = "Флагман · лучший анализ фото",
            recommended = true
        ),
        OpenAiModelOption(
            id = "gpt-5.6-terra",
            label = "GPT-5.6 Terra",
            hint = "Баланс качества и цены"
        ),
        OpenAiModelOption(
            id = "gpt-5.6-luna",
            label = "GPT-5.6 Luna",
            hint = "Быстрее · дешевле"
        )
    )

    val ids: Set<String> = options.map { it.id }.toSet()

    private val aliases: Map<String, String> = mapOf(
        "gpt-5.6" to DEFAULT,
        "gpt-5" to DEFAULT,
        "gpt-5.5" to DEFAULT,
        "gpt-5.4" to DEFAULT,
        "gpt-5-mini" to "gpt-5.6-luna",
        "gpt-5.4-mini" to "gpt-5.6-luna",
        "gpt-5-nano" to "gpt-5.6-luna",
        "gpt-4o" to DEFAULT,
        "gpt-4o-mini" to DEFAULT,
        "gpt-4.1" to DEFAULT,
        "gpt-4.1-mini" to DEFAULT
    )

    fun sanitize(stored: String?): String {
        val value = stored?.trim().orEmpty()
        if (value in ids) return value
        return aliases[value] ?: DEFAULT
    }

    fun isGpt5Family(model: String): Boolean = model.startsWith("gpt-5")

    fun isGpt56Family(model: String): Boolean = model.startsWith("gpt-5.6")

    fun reasoningEffort(model: String): String = when {
        model.contains("luna") -> "low"
        else -> "medium"
    }

    fun imageDetail(model: String): String =
        if (isGpt56Family(model)) "original" else "high"

    fun completionBudget(model: String, visibleOutput: Int): Int {
        if (!isGpt5Family(model)) return visibleOutput
        return (visibleOutput + 4_000).coerceAtMost(16_384)
    }
}

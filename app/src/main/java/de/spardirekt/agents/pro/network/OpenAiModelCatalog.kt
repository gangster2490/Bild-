package de.spardirekt.agents.pro.network

data class OpenAiModelOption(
    val id: String,
    val label: String,
    val hint: String
)

object OpenAiModelCatalog {
    const val DEFAULT = "gpt-5.6"

    val options: List<OpenAiModelOption> = listOf(
        OpenAiModelOption(
            id = "gpt-5.6",
            label = "GPT-5.6 Sol",
            hint = "Флагман · лучший анализ фото"
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
        ),
        OpenAiModelOption(
            id = "gpt-4.1",
            label = "GPT-4.1",
            hint = "Vision · стабильный запасной"
        ),
        OpenAiModelOption(
            id = "gpt-4o",
            label = "GPT-4o",
            hint = "Legacy"
        )
    )

    val ids: Set<String> = options.map { it.id }.toSet()

    fun sanitize(stored: String?): String {
        val value = stored?.trim().orEmpty()
        return if (value in ids) value else DEFAULT
    }

    fun isGpt5Family(model: String): Boolean = model.startsWith("gpt-5")

    fun reasoningEffort(model: String): String = when {
        model.contains("luna") -> "low"
        model.contains("terra") -> "low"
        else -> "medium"
    }

    fun imageDetail(model: String): String = if (isGpt5Family(model)) "auto" else "high"
}

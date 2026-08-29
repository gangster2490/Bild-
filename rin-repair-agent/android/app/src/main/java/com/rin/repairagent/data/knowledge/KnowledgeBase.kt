package com.rin.repairagent.data.knowledge

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * On-device knowledge base for stroller and child car-seat repair.
 * Loaded from assets/knowledge and injected into the AI system context.
 */
class KnowledgeBase(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Index(
        val version: Int = 1,
        val title: String = "",
        val domains: List<Domain> = emptyList()
    )

    @Serializable
    data class Domain(
        val id: String,
        val title: String,
        val file: String,
        val keywords: List<String> = emptyList()
    )

    data class Topic(
        val id: String,
        val title: String,
        val body: String
    )

    private val index: Index by lazy {
        runCatching {
            context.assets.open("knowledge/index.json").bufferedReader().use {
                json.decodeFromString(Index.serializer(), it.readText())
            }
        }.getOrDefault(Index())
    }

    fun listTopics(): List<Topic> {
        return index.domains.mapNotNull { domain ->
            val body = readAsset("knowledge/${domain.file}") ?: return@mapNotNull null
            Topic(domain.id, domain.title, body)
        }
    }

    /**
     * Build a compact context block for the AI system prompt.
     * Always includes safety + rin_style; adds strollers/car_seats/tools by keywords
     * from project title/model (falls back to both product domains).
     */
    fun contextForProject(title: String, productModel: String, maxChars: Int = 10_000): String {
        val haystack = "$title $productModel".lowercase()
        val topics = listTopics()
        if (topics.isEmpty()) return ""

        val byId = topics.associateBy { it.id }
        val selected = linkedSetOf<Topic>()

        byId["safety"]?.let { selected += it }
        byId["rin_style"]?.let { selected += it }

        val matched = index.domains.filter { domain ->
            domain.id !in setOf("safety", "rin_style") &&
                domain.keywords.any { kw -> haystack.contains(kw.lowercase()) }
        }.mapNotNull { byId[it.id] }

        if (matched.isNotEmpty()) {
            selected += matched
            byId["tools"]?.let { selected += it }
        } else {
            // Unknown product wording — give both product domains + tools
            byId["strollers"]?.let { selected += it }
            byId["car_seats"]?.let { selected += it }
            byId["tools"]?.let { selected += it }
        }

        val header = buildString {
            appendLine("БАЗА ЗНАНИЙ ПО РЕМОНТУ (коляски и детские автокресла)")
            appendLine("Используй как справочник узлов, инструментов и формулировок.")
            appendLine("Не выдумывай шаги, которых нет на текущей фотографии.")
            appendLine()
        }

        val body = buildString {
            for (topic in selected) {
                appendLine("===== ${topic.title} =====")
                appendLine(topic.body.trim())
                appendLine()
            }
        }

        val full = header + body
        return if (full.length <= maxChars) full else full.take(maxChars) + "\n…"
    }

    private fun readAsset(path: String): String? = runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrNull()
}

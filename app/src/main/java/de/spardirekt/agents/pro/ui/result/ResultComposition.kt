package de.spardirekt.agents.pro.ui.result

import de.spardirekt.agents.pro.data.db.ProjectEntity
import de.spardirekt.agents.pro.generation.PromptCleanup

/**
 * Composes what the Result screen shows and copies.
 * Always rebuilds the Gemini/VEO body through PromptCleanup — never trusts
 * a raw/legacy stored mainPrompt blob as display text.
 */
object ResultComposition {

    private val requiredSections = listOf(
        "FORMAT", "REFERENCES", "PRODUCT LOCK", "SETTING", "SHOT SEQUENCE",
        "ON-SCREEN TEXT", "VOICEOVER", "AUDIO", "CRITICAL", "NEGATIVE PROMPT",
        "TITLE", "HASHTAGS"
    )

    /**
     * Clean main VEO prompt for the prompt card and “Копировать VEO Prompt”.
     * Always runs the current local cleanup (legacy strip + final shape lock).
     */
    fun veoPrompt(entity: ProjectEntity, storedTags: List<String> = emptyList()): String {
        val raw = entity.veoPrompt.trim()
        if (raw.isBlank()) return ""
        val tags = hashtags(entity, storedTags)
        val name = title(entity).let { if (it == "—") "" else it }
        val composed = PromptCleanup.composeCopiedPrompt(
            rawPrompt = raw,
            voiceover = voiceover(entity),
            title = name,
            hashtags = tags,
            marketplace = marketplaceDetected(entity),
            tiktokShopMode = entity.tiktokShopMode
        )
        // Belt-and-suspenders: lock exact 12-section shape even if compose path changes.
        return PromptCleanup.finalCleanupCopiedPrompt(
            composed,
            marketplace = marketplaceDetected(entity)
        )
    }

    /** True when stored DB text is not already the cleaned Gemini copy. */
    fun needsStoreRewrite(entity: ProjectEntity, storedTags: List<String>): Boolean {
        val raw = entity.veoPrompt.trim()
        if (raw.isBlank()) return false
        val cleaned = veoPrompt(entity, storedTags)
        if (cleaned.isBlank()) return false
        if (raw != cleaned.trimEnd() + "\n" && raw != cleaned.trimEnd() && raw != cleaned) {
            return true
        }
        return PromptCleanup.validateCompleteness(raw, hashtags(entity, storedTags))
            .any {
                it.startsWith("legacy_section_") ||
                    it == "content_after_hashtags" ||
                    it == "safety_audit_leaked" ||
                    it == "section_order_wrong"
            }
    }

    fun voiceover(entity: ProjectEntity): String {
        val stored = entity.voiceover.trim()
        if (stored.isNotBlank()) return stored
        val fromPrompt = PromptCleanup.extractSection(entity.veoPrompt, "VOICEOVER").trim()
        return fromPrompt.ifBlank { "OFF" }
    }

    fun title(entity: ProjectEntity): String {
        val stored = entity.title.trim()
        if (stored.isNotBlank()) return stored
        return PromptCleanup.extractSection(entity.veoPrompt, "TITLE")
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
            .ifBlank { "—" }
    }

    fun hashtags(entity: ProjectEntity, storedTags: List<String>): List<String> {
        val fromStore = storedTags.map { it.trim() }.filter { it.isNotBlank() }
        if (fromStore.size == 5) return fromStore.take(5)
        if (fromStore.isNotEmpty()) return fromStore.take(5)
        val section = PromptCleanup.extractSection(entity.veoPrompt, "HASHTAGS")
        return Regex("#[\\p{L}\\p{N}_]+").findAll(section).map { it.value }.distinct().take(5).toList()
    }

    fun voiceLabel(entity: ProjectEntity): String = "Озвучка (${entity.voiceLanguage})"

    /**
     * Full owner package for “Копировать весь пакет”.
     * Metadata comes FIRST so nothing is appended after HASHTAGS inside the VEO block.
     */
    fun fullPackage(entity: ProjectEntity, storedTags: List<String>): String {
        val prompt = veoPrompt(entity, storedTags).trim()
        val vo = voiceover(entity)
        val name = title(entity).let { if (it == "—") "" else it }
        val tags = hashtags(entity, storedTags).joinToString(" ")
        return buildString {
            appendLine("Озвучка (${entity.voiceLanguage}): ${vo.ifBlank { "OFF" }}")
            appendLine("Название: ${name.ifBlank { "—" }}")
            appendLine("Хештеги: ${tags.ifBlank { "—" }}")
            appendLine()
            appendLine("────────────────")
            appendLine("VEO 3.1 PROMPT")
            appendLine("────────────────")
            append(prompt)
            if (!prompt.endsWith("\n")) append('\n')
        }.trim() + "\n"
    }

    fun marketplaceDetected(entity: ProjectEntity): Boolean {
        val analysis = entity.analysisResultJson
        val model = entity.productModelJson
        return analysis.contains("\"marketplaceDetected\": true", ignoreCase = true) ||
            analysis.contains("\"marketplaceDetected\":true", ignoreCase = true) ||
            model.contains("\"hasMarketplaceScreenshots\": true", ignoreCase = true) ||
            model.contains("\"hasMarketplaceScreenshots\":true", ignoreCase = true)
    }

    fun stripAfterHashtags(prompt: String): String {
        val match = Regex("(?im)^HASHTAGS\\b").find(prompt) ?: return prompt.trimEnd()
        val head = prompt.substring(0, match.range.first)
        val tail = prompt.substring(match.range.first)
        val lines = tail.lines()
        if (lines.isEmpty()) return prompt.trimEnd()
        val keep = mutableListOf(lines.first())
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                if (keep.size > 1) break
                continue
            }
            if (line.startsWith("#") || line.contains("#")) {
                keep += lines[i]
            } else {
                break
            }
        }
        return (head + keep.joinToString("\n")).trimEnd()
    }

    fun nothingAfterHashtags(prompt: String): Boolean {
        val after = prompt.substringAfterLast("HASHTAGS", "")
        return after.lineSequence()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .none()
    }

    fun hasRequiredSectionHeaders(prompt: String): Boolean =
        requiredSections.all { name ->
            Regex("""(?im)^$name\b""").containsMatchIn(prompt)
        }
}

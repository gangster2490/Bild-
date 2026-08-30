package de.spardirekt.agents.pro.generation

object PromptCleanup {

    private val REQUIRED_SECTIONS = listOf(
        "FORMAT",
        "REFERENCES",
        "PRODUCT LOCK",
        "SETTING",
        "SHOT SEQUENCE",
        "ON-SCREEN TEXT",
        "VOICEOVER",
        "AUDIO",
        "CRITICAL",
        "NEGATIVE PROMPT",
        "TITLE",
        "HASHTAGS"
    )

    data class CleanupResult(
        val veoPrompt: String,
        val voiceover: String,
        val title: String,
        val hashtags: List<String>,
        val issues: List<String>
    )

    fun finalize(
        rawPrompt: String,
        voiceover: String,
        title: String,
        hashtags: List<String>,
        voiceLanguage: String,
        marketplace: Boolean,
        tiktokShopMode: Boolean = true
    ): CleanupResult {
        val issues = mutableListOf<String>()
        var prompt = rawPrompt.trim()

        // Strip accidental safety audit after hashtags
        prompt = stripAfterHashtags(prompt)
        prompt = prompt.replace(Regex("(?is)\\n*TIKTOK SHOP SAFETY AUDIT[\\s\\S]*$"), "")
            .trim()

        prompt = dedupeParagraphs(prompt)
        prompt = normalizePunctuation(prompt)
        prompt = removeDuplicateVisualFidelity(prompt)
        prompt = removeDuplicateMarketplaceRules(prompt)
        prompt = ensureSectionOrder(prompt, marketplace, issues)

        var vo = cleanupVoiceover(
            voiceover.ifBlank { extractSection(prompt, "VOICEOVER") },
            voiceLanguage,
            tiktokShopMode
        )
        if (voiceLanguage == "OFF") {
            vo = "OFF"
            prompt = replaceSection(prompt, "VOICEOVER", "OFF")
        } else if (vo.isNotBlank()) {
            prompt = replaceSection(prompt, "VOICEOVER", vo)
        }

        var cleanTitle = title.ifBlank { extractSection(prompt, "TITLE") }
            .lineSequence().firstOrNull { it.isNotBlank() }
            ?.removePrefix("TITLE")
            ?.trim()
            ?.trimStart(':')
            ?.trim()
            .orEmpty()
        if (cleanTitle.isBlank()) {
            cleanTitle = "Product Ad"
            issues += "title_missing_defaulted"
        }
        prompt = replaceSection(prompt, "TITLE", cleanTitle)

        var tags = normalizeHashtags(hashtags.ifEmpty { extractHashtags(prompt) })
        tags = ensureFiveHashtags(tags, cleanTitle, tiktokShopMode)
        if (tags.size != 5) issues += "hashtags_normalized_to_5"
        prompt = replaceSection(prompt, "HASHTAGS", tags.joinToString(" "))

        // Ensure shot sequence mentions exact blocks
        if (!prompt.contains("0.0") || !prompt.contains("8.0")) {
            issues += "timeline_markers_weak"
        }

        // Completeness
        REQUIRED_SECTIONS.forEach { section ->
            if (!Regex("""(?im)^$section\b""").containsMatchIn(prompt)) {
                issues += "missing_$section"
            }
        }

        prompt = stripAfterHashtags(prompt).trim() + "\n"
        return CleanupResult(prompt, vo, cleanTitle, tags, issues)
    }

    fun validateCompleteness(prompt: String, hashtags: List<String>): List<String> {
        val issues = mutableListOf<String>()
        REQUIRED_SECTIONS.forEach { section ->
            if (!Regex("""(?im)^$section\b""").containsMatchIn(prompt)) {
                issues += "missing_$section"
            }
        }
        if (hashtags.size != 5) issues += "hashtag_count_${hashtags.size}"
        if (Regex("(?is)TIKTOK SHOP SAFETY AUDIT").containsMatchIn(prompt)) {
            issues += "safety_audit_leaked"
        }
        val after = prompt.substringAfterLast("HASHTAGS", "")
        val leftover = after.lineSequence().drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        if (leftover.isNotEmpty()) issues += "content_after_hashtags"
        if (!prompt.contains("0.0") || !prompt.contains("2.0") || !prompt.contains("4.0") ||
            !prompt.contains("6.0") || !prompt.contains("8.0")
        ) {
            issues += "incomplete_timeline"
        }
        return issues
    }

    private fun stripAfterHashtags(prompt: String): String {
        val idx = Regex("(?im)^HASHTAGS\\b").find(prompt)?.range?.first ?: return prompt
        val head = prompt.substring(0, idx)
        val tail = prompt.substring(idx)
        val lines = tail.lines().toMutableList()
        val keep = mutableListOf<String>()
        keep += lines.first()
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

    private fun dedupeParagraphs(text: String): String {
        val parts = text.split(Regex("\n{2,}"))
        val seen = linkedSetOf<String>()
        val out = mutableListOf<String>()
        parts.forEach { p ->
            val norm = p.trim().replace(Regex("\\s+"), " ").lowercase()
            if (norm.isBlank()) return@forEach
            if (seen.add(norm)) out += p.trim()
        }
        return out.joinToString("\n\n")
    }

    private fun removeDuplicateVisualFidelity(text: String): String {
        val pattern = Regex(
            "(?is)(Use the uploaded product photos as strict visual references[\\s\\S]{0,500}?PRODUCT DESIGN = LOCKED\\.?)"
        )
        val matches = pattern.findAll(text).toList()
        if (matches.size <= 1) return text
        var result = text
        matches.drop(1).forEach { m ->
            result = result.replace(m.value, "")
        }
        return result.replace(Regex("\n{3,}"), "\n\n")
    }

    private fun removeDuplicateMarketplaceRules(text: String): String {
        val pattern = Regex(
            "(?is)(The uploaded marketplace screenshots are reference material only\\.[\\s\\S]{0,400}?Recreate only the physical product\\.?)"
        )
        val matches = pattern.findAll(text).toList()
        if (matches.size <= 1) return text
        var result = text
        matches.drop(1).forEach { m -> result = result.replace(m.value, "") }
        return result.replace(Regex("\n{3,}"), "\n\n")
    }

    private fun normalizePunctuation(text: String): String {
        return text
            .replace(Regex(" +([,.;:!?])"), "$1")
            .replace(Regex("\n{3,}"), "\n\n")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    fun cleanupVoiceover(raw: String, language: String, tiktokShop: Boolean = true): String {
        return VoiceoverSystem.finalize(raw, language, tiktokShop).text
    }

    private fun normalizeHashtags(tags: List<String>): List<String> {
        return tags.map { t ->
            val cleaned = t.trim().removePrefix("#").replace(Regex("[^\\p{L}\\p{N}_]"), "")
            if (cleaned.isBlank()) null else "#$cleaned"
        }.filterNotNull().distinct().take(5)
    }

    private fun extractHashtags(prompt: String): List<String> {
        val section = extractSection(prompt, "HASHTAGS")
        return Regex("#[\\p{L}\\p{N}_]+").findAll(section).map { it.value }.toList()
    }

    fun extractSection(prompt: String, section: String): String {
        val regex = Regex("(?im)^$section\\b\\s*:?\\s*", RegexOption.MULTILINE)
        val match = regex.find(prompt) ?: return ""
        val start = match.range.last + 1
        val rest = prompt.substring(start)
        val next = REQUIRED_SECTIONS
            .filter { !it.equals(section, true) }
            .mapNotNull { name ->
                Regex("(?im)^$name\\b").find(rest)?.range?.first
            }
            .minOrNull()
        val body = if (next != null) rest.substring(0, next) else rest
        return body.trim()
    }

    private fun replaceSection(prompt: String, section: String, body: String): String {
        val regex = Regex("(?im)^$section\\b[\\s\\S]*?(?=^FORMAT\\b|^REFERENCES\\b|^PRODUCT LOCK\\b|^SETTING\\b|^SHOT SEQUENCE\\b|^ON-SCREEN TEXT\\b|^VOICEOVER\\b|^AUDIO\\b|^CRITICAL\\b|^NEGATIVE PROMPT\\b|^TITLE\\b|^HASHTAGS\\b|\\z)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        // Simpler approach: rebuild from sections
        val map = linkedMapOf<String, String>()
        REQUIRED_SECTIONS.forEach { name ->
            map[name] = extractSection(prompt, name)
        }
        map[section] = body.trim()
        if (section == "HASHTAGS") {
            // already set
        }
        return REQUIRED_SECTIONS.joinToString("\n\n") { name ->
            "$name\n${map[name].orEmpty().trim()}"
        }.trim() + "\n"
    }

    private fun ensureSectionOrder(prompt: String, marketplace: Boolean, issues: MutableList<String>): String {
        val map = linkedMapOf<String, String>()
        REQUIRED_SECTIONS.forEach { name ->
            map[name] = extractSection(prompt, name)
        }
        if (map["FORMAT"].isNullOrBlank()) {
            map["FORMAT"] = """
                Vertical 9:16.
                Photorealistic commercial TikTok Shop product ad style.
                Generate exactly 8.0 seconds total.
                Timeline ends at 8.0s.
            """.trimIndent()
            issues += "format_injected"
        }
        if (map["SHOT SEQUENCE"].isNullOrBlank() || !hasFourBlocks(map["SHOT SEQUENCE"].orEmpty())) {
            map["SHOT SEQUENCE"] = CANONICAL_SHOT_SEQUENCE
            issues += "shot_sequence_injected"
        }
        if (map["PRODUCT LOCK"].isNullOrBlank()) {
            map["PRODUCT LOCK"] = PromptTemplates.PRODUCT_FIDELITY_CORE
            issues += "product_lock_injected"
        } else if (!map["PRODUCT LOCK"]!!.contains("strict visual references", ignoreCase = true)) {
            map["PRODUCT LOCK"] = PromptTemplates.PRODUCT_FIDELITY_CORE + "\n\n" + map["PRODUCT LOCK"]
        }
        if (marketplace) {
            val refs = map["REFERENCES"].orEmpty()
            if (!refs.contains("marketplace screenshots are reference material only", ignoreCase = true)) {
                map["REFERENCES"] = (refs + "\n\n" + PromptTemplates.MARKETPLACE_RULE).trim()
            }
            val critical = map["CRITICAL"].orEmpty()
            if (!critical.contains("marketplace screenshots are reference material only", ignoreCase = true)) {
                map["CRITICAL"] = (critical + "\n\n" + PromptTemplates.MARKETPLACE_RULE).trim()
            }
        }
        if (map["NEGATIVE PROMPT"].isNullOrBlank()) {
            map["NEGATIVE PROMPT"] = """
                - no generic replacement product
                - no redesign or modernized look
                - no changed proportions or silhouette
                - no changed colors or materials
                - no duplicated product
                - no missing confirmed parts
                - no invented accessories or controls
                - no product morphing
                - no wrong left/right placement
                - no fake branding or random text
                - no marketplace UI or phone interface
                - no impossible mechanics
                - no malformed hands
                - no CGI/cartoon look
            """.trimIndent()
            issues += "negative_prompt_injected"
        }
        if (map["CRITICAL"].isNullOrBlank()) {
            map["CRITICAL"] = "Preserve photographed product identity. Exactly 8.0 seconds. Four blocks only. No continuation after 8.0s."
        }
        if (map["AUDIO"].isNullOrBlank()) {
            map["AUDIO"] = "Subtle background music. Clear dominant voice. Realistic product-action sounds only when mechanism is visible."
        }
        if (map["ON-SCREEN TEXT"].isNullOrBlank()) {
            map["ON-SCREEN TEXT"] = "Max 2–3 concise product-specific overlays. No price. No fake urgency."
        }
        if (map["SETTING"].isNullOrBlank()) {
            map["SETTING"] = "Uncluttered premium studio environment."
        }
        if (map["REFERENCES"].isNullOrBlank()) {
            map["REFERENCES"] = "Use all uploaded product photos collectively as visual evidence. Description/listing screenshots for naming/use only."
        }
        return REQUIRED_SECTIONS.joinToString("\n\n") { name ->
            "$name\n${map[name].orEmpty().trim()}"
        }.trim() + "\n"
    }

    private fun hasFourBlocks(sequence: String): Boolean {
        return listOf("0.0", "2.0", "4.0", "6.0", "8.0").all { sequence.contains(it) } &&
            !Regex("(?i)(9 scenes|25[-–]35|long-form)").containsMatchIn(sequence)
    }

    private fun padHashtags(tags: List<String>, title: String): List<String> {
        val base = tags.toMutableList()
        val fallbacks = listOf(
            "#TikTokShop",
            "#ProductAd",
            "#MustSee",
            "#HomeFinds",
            "#ShopNow",
            "#ViralProduct",
            "#SmartBuy"
        )
        val fromTitle = title.split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }
            .filter { it.length >= 3 }
            .map { "#$it" }
        (fromTitle + fallbacks).forEach { tag ->
            if (base.size >= 5) return@forEach
            if (base.none { it.equals(tag, true) }) base += tag
        }
        while (base.size < 5) base += "#TikTokShop${base.size}"
        return base.take(5)
    }

    private fun ensureFiveHashtags(
        tags: List<String>,
        title: String,
        tiktokShopMode: Boolean
    ): List<String> {
        val padded = if (tags.size == 5) tags else padHashtags(tags, title)
        val result = padded.take(5).toMutableList()
        if (tiktokShopMode && result.none { it.equals("#TikTokShop", ignoreCase = true) }) {
            if (result.size >= 5) result[result.lastIndex] = "#TikTokShop"
            else result += "#TikTokShop"
        }
        return result.take(5)
    }

    private val CANONICAL_SHOT_SEQUENCE = """
        0.0–2.0s — HOOK: product visible immediately with strongest verified detail.
        2.0–4.0s — IDENTITY: clear full/product-true framing.
        4.0–6.0s — FEATURE / DEMO: one hero feature only, physically plausible.
        6.0–8.0s — HERO / CTA: desirable hero hold and soft CTA.
        Timeline ends at 8.0s. Four blocks only. No extra scenes.
    """.trimIndent()
}

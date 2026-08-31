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

    /** Short lock line for the copied Gemini/VEO prompt — not the long internal doctrine essay. */
    private val SHORT_PRODUCT_LOCK =
        "Match the uploaded product photos exactly. Same silhouette, proportions, colors, materials, parts, and markings. Do not replace or redesign."

    private val SHORT_MARKETPLACE =
        "Marketplace screenshots are reference only — never show listing UI, prices, or phone chrome as video frames."

    private val SHORT_NEGATIVE = listOf(
        "no generic replacement product",
        "no redesign or modernized look",
        "no changed proportions, colors, or materials",
        "no missing confirmed parts or invented accessories",
        "no product morphing",
        "no marketplace UI or phone interface",
        "no fake branding or random text",
        "no CGI/cartoon look"
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

        if (!prompt.contains("0.0") || !prompt.contains("8.0")) {
            issues += "timeline_markers_weak"
        }

        REQUIRED_SECTIONS.forEach { section ->
            if (!Regex("""(?im)^$section\b""").containsMatchIn(prompt)) {
                issues += "missing_$section"
            }
        }

        // Copy-ready Gemini/VEO prompt: concise section bodies, same section order.
        prompt = simplifyCopiedPrompt(prompt, marketplace)
        prompt = stripAfterHashtags(prompt).trim() + "\n"
        return CleanupResult(prompt, vo, cleanTitle, tags, issues)
    }

    /**
     * Rebuild the Gemini/VEO copy body from a stored (possibly dirty) prompt.
     * Syncs VOICEOVER / TITLE / HASHTAGS from the Result cards.
     * Local only — does not call the model or change pipeline stages.
     */
    fun composeCopiedPrompt(
        rawPrompt: String,
        voiceover: String,
        title: String,
        hashtags: List<String>,
        marketplace: Boolean,
        tiktokShopMode: Boolean = true
    ): String {
        var prompt = rawPrompt.trim()
        if (prompt.isBlank()) return ""

        if (looksLikeRawJson(prompt)) {
            prompt = JsonExtractor.salvageVeoPrompt(prompt).orEmpty()
            if (prompt.isBlank()) return ""
        }

        prompt = stripAfterHashtags(prompt)
        prompt = prompt.replace(Regex("(?is)\\n*TIKTOK SHOP SAFETY AUDIT[\\s\\S]*$"), "").trim()
        prompt = dedupeParagraphs(prompt)
        prompt = normalizePunctuation(prompt)
        prompt = removeDuplicateVisualFidelity(prompt)
        prompt = removeDuplicateMarketplaceRules(prompt)

        val issues = mutableListOf<String>()
        prompt = ensureSectionOrder(prompt, marketplace, issues)

        val vo = voiceover.trim().ifBlank { extractSection(prompt, "VOICEOVER").ifBlank { "OFF" } }
        prompt = replaceSection(prompt, "VOICEOVER", vo)

        val cleanTitle = title.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            .ifBlank { extractSection(prompt, "TITLE").lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }
            .ifBlank { "Product Ad" }
        prompt = replaceSection(prompt, "TITLE", cleanTitle)

        var tags = normalizeHashtags(hashtags.ifEmpty { extractHashtags(prompt) })
        tags = ensureFiveHashtags(tags, cleanTitle, tiktokShopMode)
        prompt = replaceSection(prompt, "HASHTAGS", tags.joinToString(" "))

        prompt = simplifyCopiedPrompt(prompt, marketplace)
        return stripAfterHashtags(prompt).trim() + "\n"
    }

    private fun looksLikeRawJson(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("{") && t.contains("\"veoPrompt\"")
    }

    /**
     * Deterministic local pass that shortens only the prompt the owner copies into Gemini/VEO.
     * Does not change photo analysis or other pipeline stages.
     */
    fun simplifyCopiedPrompt(prompt: String, marketplace: Boolean): String {
        val map = linkedMapOf<String, String>()
        REQUIRED_SECTIONS.forEach { name ->
            map[name] = extractSection(prompt, name)
        }
        map["FORMAT"] = simplifyFormat(map["FORMAT"].orEmpty())
        map["REFERENCES"] = simplifyReferences(map["REFERENCES"].orEmpty(), marketplace)
        map["PRODUCT LOCK"] = simplifyProductLock(map["PRODUCT LOCK"].orEmpty())
        map["SETTING"] = firstSentences(map["SETTING"].orEmpty(), 1).ifBlank { "Uncluttered premium studio." }
        map["SHOT SEQUENCE"] = simplifyShotSequence(map["SHOT SEQUENCE"].orEmpty())
        map["ON-SCREEN TEXT"] = firstSentences(map["ON-SCREEN TEXT"].orEmpty(), 2)
            .ifBlank { "Max 2–3 short product overlays. No price or fake urgency." }
        map["VOICEOVER"] = map["VOICEOVER"].orEmpty().trim().ifBlank { "OFF" }
        map["AUDIO"] = firstSentences(map["AUDIO"].orEmpty(), 2)
            .ifBlank { "Subtle music. Clear voice. Real action sounds only if visible." }
        map["CRITICAL"] = simplifyCritical(map["CRITICAL"].orEmpty(), marketplace)
        map["NEGATIVE PROMPT"] = simplifyNegative(map["NEGATIVE PROMPT"].orEmpty())
        map["TITLE"] = map["TITLE"].orEmpty().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            .ifBlank { "Product Ad" }
        map["HASHTAGS"] = map["HASHTAGS"].orEmpty().trim()

        return REQUIRED_SECTIONS.joinToString("\n\n") { name ->
            "$name\n${map[name].orEmpty().trim()}"
        }.trim() + "\n"
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

    private fun simplifyFormat(raw: String): String {
        val has916 = raw.contains("9:16")
        val has8 = raw.contains("8.0")
        return buildString {
            append(if (has916) "Vertical 9:16." else "Vertical 9:16.")
            append(" Photorealistic TikTok Shop product ad.")
            append(if (has8) " Exactly 8.0 seconds." else " Exactly 8.0 seconds.")
        }
    }

    private fun simplifyReferences(raw: String, marketplace: Boolean): String {
        var text = stripLongDoctrine(raw)
        text = firstSentences(text, 3)
        if (text.isBlank()) {
            text = "Use uploaded product photos as visual evidence."
        }
        if (marketplace && !text.contains("marketplace", ignoreCase = true)) {
            text = "$text $SHORT_MARKETPLACE".trim()
        }
        return text
    }

    private fun simplifyProductLock(raw: String): String {
        val withoutEssay = stripLongDoctrine(raw)
        val specifics = withoutEssay
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { line -> isGenericFidelityBoilerplate(line) }
            .filterNot { line ->
                // Drop lines that merely restate the short lock
                line.contains("Match the uploaded product photos exactly", ignoreCase = true)
            }
            .distinctBy { it.lowercase() }
            .take(12)
            .toList()
        return buildString {
            append(SHORT_PRODUCT_LOCK)
            if (specifics.isNotEmpty()) {
                append("\n")
                append(specifics.joinToString("\n"))
            }
        }.trim()
    }

    private fun isGenericFidelityBoilerplate(line: String): Boolean {
        val l = line.lowercase()
        return l.contains("core principle") ||
            l.contains("creative presentation") ||
            l.contains("product design = locked") ||
            l.contains("strict visual references") ||
            l.contains("do not reinterpret") ||
            l.contains("do not replace the photographed") ||
            l.contains("do not redesign, modernize") ||
            l.contains("if creative instructions conflict") ||
            l.contains("generated product must remain") ||
            l.contains("preserve the exact overall silhouette") ||
            l.contains("proportions, construction, colors, materials, controls, handles, hinges")
    }

    private fun simplifyShotSequence(raw: String): String {
        if (hasFourBlocks(raw)) {
            val blocks = Regex("""(?m)^\s*0\.0[^\n]*|^\s*2\.0[^\n]*|^\s*4\.0[^\n]*|^\s*6\.0[^\n]*""")
                .findAll(raw)
                .map { it.value.trim() }
                .distinct()
                .take(4)
                .toList()
            if (blocks.size == 4) {
                return blocks.joinToString("\n")
            }
        }
        return CANONICAL_SHOT_SEQUENCE
    }

    private fun simplifyCritical(raw: String, marketplace: Boolean): String {
        val base = "Keep photographed product identity. Exactly 8.0s. Four blocks only."
        return if (marketplace) "$base $SHORT_MARKETPLACE" else base
    }

    private fun simplifyNegative(raw: String): String {
        val bullets = raw.lineSequence()
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotBlank() }
            .filterNot {
                it.contains("malformed hands", ignoreCase = true) ||
                    it.contains("impossible mechanics", ignoreCase = true)
            }
            .distinctBy { it.lowercase() }
            .take(8)
            .toList()
        val chosen = if (bullets.size >= 4) bullets else SHORT_NEGATIVE
        return chosen.take(8).joinToString("\n") { "- $it" }
    }

    private fun stripLongDoctrine(text: String): String {
        var out = text
        out = out.replace(
            Regex("(?is)Use the uploaded product photos as strict visual references[\\s\\S]{0,800}?PRODUCT DESIGN = LOCKED\\.?"),
            ""
        )
        out = out.replace(
            Regex("(?is)The generated product must remain the same physical product shown in the uploaded photos\\.?"),
            ""
        )
        out = out.replace(
            Regex("(?is)Preserve the exact overall silhouette, proportions, construction, colors, materials, controls, handles, hinges, accessories, markings and distinctive visual details\\.?"),
            ""
        )
        out = out.replace(
            Regex("(?is)The uploaded marketplace screenshots are reference material only\\.[\\s\\S]{0,500}?Recreate only the physical product\\.?"),
            ""
        )
        return out.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun firstSentences(text: String, max: Int): String {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return ""
        val parts = cleaned.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        return parts.take(max).joinToString(" ").trim()
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
        val map = linkedMapOf<String, String>()
        REQUIRED_SECTIONS.forEach { name ->
            map[name] = extractSection(prompt, name)
        }
        map[section] = body.trim()
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
            map["FORMAT"] = "Vertical 9:16. Photorealistic TikTok Shop product ad. Exactly 8.0 seconds."
            issues += "format_injected"
        }
        if (map["SHOT SEQUENCE"].isNullOrBlank() || !hasFourBlocks(map["SHOT SEQUENCE"].orEmpty())) {
            map["SHOT SEQUENCE"] = CANONICAL_SHOT_SEQUENCE
            issues += "shot_sequence_injected"
        }
        if (map["PRODUCT LOCK"].isNullOrBlank()) {
            map["PRODUCT LOCK"] = SHORT_PRODUCT_LOCK
            issues += "product_lock_injected"
        }
        if (marketplace) {
            val refs = map["REFERENCES"].orEmpty()
            if (!refs.contains("marketplace", ignoreCase = true) &&
                !refs.contains("reference only", ignoreCase = true)
            ) {
                map["REFERENCES"] = (refs + "\n" + SHORT_MARKETPLACE).trim()
            }
        }
        if (map["NEGATIVE PROMPT"].isNullOrBlank()) {
            map["NEGATIVE PROMPT"] = SHORT_NEGATIVE.joinToString("\n") { "- $it" }
            issues += "negative_prompt_injected"
        }
        if (map["CRITICAL"].isNullOrBlank()) {
            map["CRITICAL"] = "Keep photographed product identity. Exactly 8.0s. Four blocks only."
        }
        if (map["AUDIO"].isNullOrBlank()) {
            map["AUDIO"] = "Subtle music. Clear voice. Real action sounds only if visible."
        }
        if (map["ON-SCREEN TEXT"].isNullOrBlank()) {
            map["ON-SCREEN TEXT"] = "Max 2–3 short product overlays. No price or fake urgency."
        }
        if (map["SETTING"].isNullOrBlank()) {
            map["SETTING"] = "Uncluttered premium studio."
        }
        if (map["REFERENCES"].isNullOrBlank()) {
            map["REFERENCES"] = "Use uploaded product photos as visual evidence."
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
        0.0–2.0s — HOOK: product visible with strongest verified detail
        2.0–4.0s — IDENTITY: clear full product framing
        4.0–6.0s — FEATURE / DEMO: one hero feature only
        6.0–8.0s — HERO / CTA: hero hold and soft CTA
    """.trimIndent()
}

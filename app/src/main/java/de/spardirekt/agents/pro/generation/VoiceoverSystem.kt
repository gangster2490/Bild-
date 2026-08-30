package de.spardirekt.agents.pro.generation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Dedicated spoken-voiceover generator contract + deterministic local cleanup.
 * Spec §25 / §26: DE 12–18 words, RU 14–22, natural speech, no duplicate CTA.
 */
object VoiceoverSystem {

    const val DE_MIN_WORDS = 12
    const val DE_MAX_WORDS = 18
    const val RU_MIN_WORDS = 14
    const val RU_MAX_WORDS = 22

    data class Result(
        val text: String,
        val issues: List<String>
    ) {
        val acceptable: Boolean get() = issues.isEmpty() && text.isNotBlank()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val ctaPhrases = listOf(
        "закажите в tiktok shop",
        "закажите в tik tok shop",
        "заказывайте в tiktok shop",
        "купите в tiktok shop",
        "jetzt im tiktok shop bestellen",
        "jetzt bei tiktok shop bestellen",
        "jetzt bestellen",
        "jetzt kaufen",
        "einfach bestellen",
        "bestellen sie",
        "hol dir",
        "shop now",
        "закажите",
        "заказывайте",
        "купите",
        "покупайте"
    )

    fun wordRange(language: String): IntRange = when (language.uppercase()) {
        "DE" -> DE_MIN_WORDS..DE_MAX_WORDS
        "RU" -> RU_MIN_WORDS..RU_MAX_WORDS
        else -> 0..0
    }

    fun countWords(text: String): Int =
        text.split(Regex("\\s+")).count { it.isNotBlank() }

    fun systemPrompt(voice: String, tiktokShop: Boolean): String = """
YOU WRITE ONLY THE SPOKEN VOICEOVER for an 8-second TikTok Shop product ad.

You are not writing a VEO prompt, title, hashtags, shot list, or on-screen text.
Return JSON only: {"voiceover":"..."}

VOICE LANGUAGE: $voice
${languageStyle(voice)}

SPOKEN STYLE — THIS IS THE POINT:
Light, natural, TikTok-friendly. Confident, useful, attractive, simple.
Talk like one person showing the product to one person — not a narrator, not a shop catalogue, not a radio ad.
The product should feel desirable without aggressive hard-selling.

SOUND:
- spoken, warm, specific
- everyday words
- one breath, one or two short sentences

DO NOT SOUND LIKE:
- robotic narrator
- catalogue listing (“high quality”, “ideal for”, “perfect for everyday use”)
- fake hype (“best ever”, “must have”, “viral”)
- fake urgency or scarcity
- command selling

STRUCTURE:
1) one real benefit in spoken words
2) one supporting real feature from the evidence
3) one SOFT invitation — not a command

${ctaStyle(tiktokShop)}

FORBIDDEN SPOKEN CTAs (never use):
Закажите / Заказывайте / Купите / Покупайте
Jetzt bestellen / Jetzt kaufen / Einfach bestellen / Bestellen Sie / Hol dir / Shop now
“Закажите. Закажите в TikTok Shop.”

GOOD STYLE:
DE: "Der schwarze Rahmen bleibt leicht und klappt klein. Schau ihn dir im TikTok Shop an."
RU: "Чёрный каркас с красным столиком складывается за секунды. Загляни в TikTok Shop."

BAD STYLE:
DE: "Hochwertiger Premium-Stuhl, ideal für Camping. Jetzt bestellen."
RU: "Качественный товар премиум-класса. Закажите в TikTok Shop."

RULES:
- Use only facts from the product model / creative plan. No invented functions.
- No quotes, speaker labels, or stage directions.
- No duplicate words. No duplicate CTA.
- The JSON field voiceover must be the exact spoken line, nothing else.
""".trimIndent()

    private fun languageStyle(voice: String): String = when (voice.uppercase()) {
        "DE" -> """
German only — natural spoken German, as someone would actually say it in a short clip.
Target ${DE_MIN_WORDS}–$DE_MAX_WORDS spoken words. Must fit comfortably in 8 seconds.
No English. No Russian. Informal-friendly is fine (du), never stiff catalogue German.
""".trim()
        "RU" -> """
Russian only — natural spoken Russian, as someone would actually say it in a short clip.
Target ${RU_MIN_WORDS}–$RU_MAX_WORDS spoken words. Must fit comfortably in 8 seconds.
No German. No English-only slogans. Conversational, not advertising-copy Russian.
""".trim()
        else -> "Voice is OFF. Return {\"voiceover\":\"OFF\"}."
    }

    private fun ctaStyle(tiktokShop: Boolean): String = if (tiktokShop) {
        """
SOFT CTA (TikTok Shop Mode ON): an invitation, one mention of TikTok Shop is allowed.
DE: "schau ihn dir im TikTok Shop an" / "gibt's im TikTok Shop"
RU: "загляни в TikTok Shop" / "есть в TikTok Shop"
Not a buy-command.
""".trim()
    } else {
        "TikTok Shop Mode OFF: do not mention TikTok Shop. End with a soft look/try invitation, never a buy-command."
    }

    fun repairPrompt(voice: String, tiktokShop: Boolean, issues: List<String>): String = """
${systemPrompt(voice, tiktokShop)}

REWRITE the voiceover. It failed local checks:
${issues.joinToString("\n") { "- $it" }}

REWRITE in the light natural TikTok spoken style. No command CTA. Keep product-true facts. Do not copy the failed line. Return JSON only.
""".trimIndent()

    fun userPrompt(
        productModelJson: String,
        creativePlanJson: String,
        wish: String,
        failedVoiceover: String? = null
    ): String = buildString {
        appendLine("Write the spoken voiceover from this evidence.")
        appendLine()
        appendLine("PRODUCT MODEL:")
        appendLine(productModelJson)
        appendLine()
        appendLine("CREATIVE PLAN:")
        appendLine(creativePlanJson)
        appendLine()
        appendLine("OPTIONAL WISH: ${wish.ifBlank { "(none)" }}")
        if (!failedVoiceover.isNullOrBlank()) {
            appendLine()
            appendLine("FAILED VOICEOVER (do not copy):")
            appendLine(failedVoiceover)
        }
    }.trim()

    fun extractSpokenLine(raw: String): String {
        if (raw.isBlank()) return ""
        val payload = JsonExtractor.extract(raw)
        val fromJson = runCatching {
            json.parseToJsonElement(payload).jsonObject["voiceover"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return (fromJson ?: if (payload.startsWith("{")) "" else payload).trim()
    }

    fun finalize(raw: String, language: String, tiktokShop: Boolean = true): Result {
        if (language.equals("OFF", ignoreCase = true)) {
            return Result("OFF", emptyList())
        }
        var text = stripDecorations(raw)
        if (text.isBlank()) {
            return Result("", listOf("voiceover_missing"))
        }

        text = dedupeSentences(text)
        text = dropRedundantCtaSentences(text)
        text = dedupeConsecutiveWords(text)
        text = collapseRepeatedCta(text)
        text = normalizePunctuation(text)
        text = fitWordCount(text, language)

        if (text.isNotBlank() && !text.matches(Regex(".*[.!?…]\$"))) {
            text += "."
        }
        text = text.trim()

        val issues = inspect(text, language, tiktokShop)
        return Result(text, issues)
    }

    fun inspect(text: String, language: String, tiktokShop: Boolean = true): List<String> {
        if (language.equals("OFF", ignoreCase = true)) {
            return if (text == "OFF") emptyList() else listOf("voice_off_expected")
        }
        val issues = mutableListOf<String>()
        if (text.isBlank() || text.equals("OFF", ignoreCase = true)) {
            issues += "voiceover_missing"
            return issues
        }
        if (!languageMatches(text, language)) {
            issues += "language_mismatch"
        }
        val words = countWords(text)
        val range = wordRange(language)
        if (words < range.first) issues += "too_short_$words"
        if (words > range.last) issues += "too_long_$words"
        if (ctaVerbCount(text) > 1) {
            issues += "duplicate_cta"
        }
        if (isCtaOnly(text)) {
            issues += "cta_only"
        }
        if (hasHardSellCta(text)) {
            issues += "hard_sell_cta"
        }
        if (isGenericSlogan(text) || hasCatalogueLanguage(text)) {
            issues += "catalogue_language"
        }
        if (!tiktokShop && text.lowercase().contains("tiktok shop")) {
            issues += "tiktok_shop_mention"
        }
        return issues.distinct()
    }

    private fun stripDecorations(raw: String): String {
        var text = raw.trim()
            .removePrefix("VOICEOVER")
            .trimStart(':', '—', '-', ' ')
            .trim()
        text = text.trim('"', '“', '”', '«', '»', '\'')
        text = text.replace(Regex("""(?i)\[[^\]]{0,40}]"""), " ")
        text = text.replace(Regex("""(?i)\((?:softly|whisper|vo|sprecher)[^)]*\)"""), " ")
        text = text.replace(Regex("""(?i)^(sprecher|voiceover|vo|озвучка)\s*:\s*"""), "")
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun dedupeSentences(text: String): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val unique = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        sentences.forEach { s ->
            val key = s.lowercase().replace(Regex("[.!?…]+$"), "").trim()
            if (seen.add(key)) unique += s
        }
        return unique.joinToString(" ")
    }

    private fun dropRedundantCtaSentences(text: String): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (sentences.size <= 1) return text
        val filtered = sentences.filterIndexed { index, s ->
            val lower = s.lowercase().replace(Regex("[.!?…]+$"), "").trim()
            val matched = ctaPhrases.filter { lower == it || lower.startsWith("$it ") }
                .maxByOrNull { it.length }
            if (matched == null) return@filterIndexed true
            val isShort = lower.length < matched.length + 10 || countWords(s) <= 5
            if (!isShort) return@filterIndexed true
            sentences.withIndex().none { (i, other) ->
                i != index && other.lowercase().contains(matched)
            }
        }
        return filtered.joinToString(" ")
    }

    private fun dedupeConsecutiveWords(text: String): String {
        return text.split(Regex("\\s+")).fold(mutableListOf<String>()) { acc, w ->
            if (acc.isEmpty() || !acc.last().equals(w, ignoreCase = true)) acc += w
            acc
        }.joinToString(" ")
    }

    private fun collapseRepeatedCta(text: String): String {
        var out = text
        out = out.replace(Regex("(?iu)(закажите[.!]\\s*){2,}"), "Закажите. ")
        out = out.replace(Regex("(?iu)(jetzt bestellen[.!]\\s*){2,}"), "Jetzt bestellen. ")
        out = out.replace(Regex("(?iu)(купите[.!]\\s*){2,}"), "Купите. ")
        return out
    }

    private fun fitWordCount(text: String, language: String): String {
        val max = wordRange(language).last
        if (max == 0) return text
        var sentences = text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        while (sentences.size > 1 && countWords(sentences.joinToString(" ")) > max) {
            sentences.removeAt(sentences.lastIndex)
        }
        return sentences.joinToString(" ")
    }

    private fun languageMatches(text: String, language: String): Boolean {
        val cyr = text.count { it in '\u0400'..'\u04FF' }
        val lat = text.count { it.isLetter() && it !in '\u0400'..'\u04FF' }
        return when (language.uppercase()) {
            "RU" -> cyr > 0 && cyr >= lat
            "DE" -> lat > 0 && cyr == 0
            else -> true
        }
    }

    private fun ctaVerbCount(text: String): Int {
        val lower = text.lowercase()
        val verbs = listOf(
            "закажите", "заказывайте", "купите", "покупайте",
            "jetzt bestellen", "jetzt kaufen", "einfach bestellen"
        )
        return verbs.sumOf { verb ->
            Regex(Regex.escape(verb), RegexOption.IGNORE_CASE).findAll(lower).count()
        }
    }

    private fun isCtaOnly(text: String): Boolean {
        val lower = text.lowercase().replace(Regex("[.!?…]+$"), "").trim()
        if (countWords(text) > 8) return false
        return ctaPhrases.any { lower == it || lower.startsWith("$it ") && countWords(text) <= 6 }
    }

    private fun normalizePunctuation(text: String): String {
        return text
            .replace(Regex(" +([,.;:!?…])"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun hasHardSellCta(text: String): Boolean = ctaVerbCount(text) > 0

    private fun hasCatalogueLanguage(text: String): Boolean {
        val lower = text.lowercase()
        val snippets = listOf(
            "ideal für", "perfekt für", "hochwertig", "beste qualität", "must have",
            "best ever", "premium-stuhl", "premium stuhl",
            "идеально для", "премиум-класса", "премиум класса", "высокое качество",
            "лучший выбор", "качественный товар"
        )
        return snippets.any { lower.contains(it) }
    }

    private fun isGenericSlogan(text: String): Boolean {
        val lower = text.lowercase().replace(Regex("[.!?…]+$"), "").trim()
        val slogans = setOf(
            "must see", "shop now", "viral product", "beste qualität",
            "premium quality", "лучшее качество", "просто закажите",
            "jetzt kaufen", "buy now"
        )
        return lower in slogans
    }
}

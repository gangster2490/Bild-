package de.spardirekt.agents.pro.generation

/**
 * Extracts and repairs model JSON, especially FINAL_PROMPT packages where
 * veoPrompt often arrives with illegal raw newlines inside the string.
 */
object JsonExtractor {

    fun extract(raw: String): String {
        var text = stripFence(raw.trim())
        val obj = extractBalancedObject(text)
        if (obj != null) return obj
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1)
        }
        return text
    }

    /** Best-effort repair: turn illegal raw newlines/tabs inside JSON strings into escapes. */
    fun repair(raw: String): String {
        val extracted = extract(raw)
        return repairLiteralControlsInStrings(extracted)
    }

    fun stripFence(raw: String): String {
        var text = raw.trim()
        if (!text.startsWith("```")) return text
        text = text.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
        val fence = text.lastIndexOf("```")
        if (fence >= 0) text = text.substring(0, fence)
        return text.trim()
    }

    fun extractBalancedObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /**
     * Models often copy multiline examples and emit:
     * {"veoPrompt": "FORMAT
     * REFERENCES
     * ..."}
     * which is illegal JSON. Escape those controls while inside strings.
     */
    fun repairLiteralControlsInStrings(json: String): String {
        val out = StringBuilder(json.length + 64)
        var inString = false
        var escape = false
        for (c in json) {
            if (inString) {
                when {
                    escape -> {
                        out.append(c)
                        escape = false
                    }
                    c == '\\' -> {
                        out.append(c)
                        escape = true
                    }
                    c == '"' -> {
                        out.append(c)
                        inString = false
                    }
                    c == '\n' -> out.append("\\n")
                    c == '\r' -> out.append("\\r")
                    c == '\t' -> out.append("\\t")
                    else -> out.append(c)
                }
            } else {
                if (c == '"') inString = true
                out.append(c)
            }
        }
        return out.toString()
    }

    /**
     * Salvage a VEO prompt body if JSON is still unusable.
     * Looks for FORMAT … HASHTAGS in the raw text (including inside broken JSON strings).
     */
    fun salvageVeoPrompt(raw: String): String? {
        val text = stripFence(raw)
        val formatMatch = Regex("""(?i)(?:^|[\n\r"])\s*FORMAT\b""").find(text) ?: return null
        val start = text.indexOf("FORMAT", formatMatch.range.first, ignoreCase = true)
        if (start < 0) return null
        val hashtags = Regex("""(?im)^HASHTAGS\b|(?i)(?:\n|\r|\\n)\s*HASHTAGS\b""").find(text, start) ?: return null
        val hashtagsStart = text.indexOf("HASHTAGS", hashtags.range.first, ignoreCase = true)
        val after = text.substring(hashtagsStart)
        // End at closing JSON quote/brace/fence if present, else take hashtag lines.
        val endRel = Regex("""(?m)^[}\`]|",\s*"|\\n"\s*[,}]""")
            .find(after)
            ?.range
            ?.first
            ?: run {
                val lines = after.lines()
                var last = 0
                for (i in lines.indices) {
                    val line = lines[i].trim()
                    if (i == 0) {
                        last = lines[i].length
                        continue
                    }
                    if (line.isEmpty()) break
                    if (line.startsWith("#") || line.contains("#")) {
                        last += 1 + lines[i].length
                    } else {
                        break
                    }
                }
                last
            }
        var block = (text.substring(start, hashtagsStart) + after.substring(0, endRel)).trim()
        block = block
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .trim()
            .trimEnd('"', ',', '}', ' ')
            .trim()
        if (!block.contains("PRODUCT LOCK", ignoreCase = true)) return null
        if (!block.contains("SHOT SEQUENCE", ignoreCase = true)) return null
        if (!Regex("""(?im)^FORMAT\b""").containsMatchIn(block) &&
            !block.startsWith("FORMAT", ignoreCase = true)
        ) {
            return null
        }
        return block
    }
}

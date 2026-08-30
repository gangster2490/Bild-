package de.spardirekt.agents.pro.generation

object JsonExtractor {
    fun extract(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            val fence = text.lastIndexOf("```")
            if (fence >= 0) text = text.substring(0, fence)
            text = text.trim()
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1)
        }
        return text
    }
}

package de.spardirekt.agents.pro.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalPromptJsonTest {

    private val validPrompt = """
FORMAT
Vertical 9:16. Exactly 8.0 seconds.

REFERENCES
Black frame confirmed.

PRODUCT LOCK
Preserve black tubular frame and red tray.

SETTING
Studio

SHOT SEQUENCE
0.0–2.0s — HOOK: frame
2.0–4.0s — IDENTITY: full chair
4.0–6.0s — FEATURE / DEMO: fold
6.0–8.0s — HERO / CTA: hold

ON-SCREEN TEXT
Compact fold

VOICEOVER
OFF

AUDIO
Soft music

CRITICAL
Keep identity. Exactly 8.0s.

NEGATIVE PROMPT
- no generic chair

TITLE
Fishing Chair

HASHTAGS
#a #b #c #d #TikTokShop
""".trimIndent()

    @Test
    fun parsesValidEscapedJson() {
        val escaped = validPrompt.replace("\n", "\\n")
        val raw = """{"veoPrompt":"$escaped","voiceover":"OFF","title":"Fishing Chair","hashtags":["#a","#b","#c","#d","#TikTokShop"],"qualityScores":{"productFidelity":9,"creativity":8,"physicalPlausibility":8,"voiceoverNaturalness":8,"hookStrength":8}}"""
        val bundle = FinalPromptJson.decode(raw)
        assertTrue(bundle.veoPrompt.startsWith("FORMAT"))
        assertTrue(bundle.veoPrompt.contains("PRODUCT LOCK"))
        assertTrue(bundle.veoPrompt.contains("HASHTAGS"))
        assertEquals("OFF", bundle.voiceover)
        assertEquals("Fishing Chair", bundle.title)
        assertEquals(5, bundle.hashtags.size)
        assertEquals(9, bundle.qualityScores.productFidelity)
        assertFalse(bundle.veoPrompt.trim().startsWith("{"))
    }

    @Test
    fun repairsIllegalRawNewlinesInsideVeoPromptString() {
        // The confirmed model failure: raw newlines inside the JSON string.
        val broken = """
{
  "veoPrompt": "FORMAT
Vertical 9:16. Exactly 8.0 seconds.

REFERENCES
Black frame confirmed.

PRODUCT LOCK
Preserve black tubular frame and red tray.

SETTING
Studio

SHOT SEQUENCE
0.0–2.0s — HOOK: frame
2.0–4.0s — IDENTITY: full chair
4.0–6.0s — FEATURE / DEMO: fold
6.0–8.0s — HERO / CTA: hold

ON-SCREEN TEXT
Compact fold

VOICEOVER
OFF

AUDIO
Soft music

CRITICAL
Keep identity. Exactly 8.0s.

NEGATIVE PROMPT
- no generic chair

TITLE
Fishing Chair

HASHTAGS
#a #b #c #d #TikTokShop",
  "voiceover": "OFF",
  "title": "Fishing Chair",
  "hashtags": ["#a","#b","#c","#d","#TikTokShop"]
}
""".trimIndent()

        val bundle = FinalPromptJson.decode(broken)
        assertTrue("prompt was: ${bundle.veoPrompt.take(80)}", bundle.veoPrompt.startsWith("FORMAT"))
        assertTrue(bundle.veoPrompt.contains("PRODUCT LOCK"))
        assertTrue(bundle.veoPrompt.contains("0.0–2.0s"))
        assertEquals("OFF", bundle.voiceover)
        assertEquals("Fishing Chair", bundle.title)
        assertFalse(bundle.veoPrompt.contains("\"veoPrompt\""))
        assertFalse(bundle.veoPrompt.trim().startsWith("{"))
    }

    @Test
    fun doesNotStuffRawJsonIntoVeoPromptOnTotalFailure() {
        val garbage = """{"notAPrompt": true, "oops": "}"""
        val bundle = FinalPromptJson.decode(garbage)
        assertFalse(bundle.veoPrompt.contains("\"veoPrompt\""))
        assertFalse(bundle.veoPrompt.trim().startsWith("{") && bundle.veoPrompt.contains("notAPrompt"))
    }

    @Test
    fun salvagesFormatBlockWhenJsonIrrecoverable() {
        val messy = """
here is junk
FORMAT
Vertical 9:16.

REFERENCES
Photos

PRODUCT LOCK
Keep black frame

SETTING
Studio

SHOT SEQUENCE
0.0–2.0s — HOOK
2.0–4.0s — IDENTITY
4.0–6.0s — FEATURE / DEMO
6.0–8.0s — HERO / CTA

ON-SCREEN TEXT
x

VOICEOVER
OFF

AUDIO
x

CRITICAL
x

NEGATIVE PROMPT
- no redesign

TITLE
Chair

HASHTAGS
#a #b #c #d #e
trailing junk { "veoPrompt":
""".trimIndent()
        val bundle = FinalPromptJson.decode(messy)
        assertTrue(bundle.veoPrompt.startsWith("FORMAT"))
        assertTrue(bundle.veoPrompt.contains("HASHTAGS"))
    }

    @Test
    fun finalPromptTemplateDoesNotShowRawMultilineJsonString() {
        val prompt = PromptTemplates.finalPromptSystem("DE", true)
        assertTrue(prompt.contains("\\\\n") || prompt.contains("\\n"))
        assertFalse(
            Regex(""""veoPrompt"\s*:\s*"full prompt with sections""").containsMatchIn(prompt)
        )
        // Must not teach illegal raw breaks after the opening quote of veoPrompt example
        assertFalse(
            Regex(""""veoPrompt"\s*:\s*"[^"\\]*\n""").containsMatchIn(prompt)
        )
    }

    @Test
    fun prefersMainPromptAliasWhenVeoPromptMissing() {
        val body = validPrompt
        val escaped = body.replace("\n", "\\n")
        val raw = "{\"mainPrompt\":\"$escaped\",\"voiceover\":\"OFF\",\"title\":\"Fishing Chair\",\"hashtags\":[\"#a\",\"#b\",\"#c\",\"#d\",\"#TikTokShop\"]}"
        val bundle = FinalPromptJson.decode(raw)
        assertTrue(bundle.veoPrompt.startsWith("FORMAT"))
        assertTrue(bundle.veoPrompt.contains("PRODUCT LOCK"))
        assertFalse(bundle.veoPrompt.trim().startsWith("{"))
    }

    @Test
    fun prefersFormattedBodyOverJsonBlobWhenBothAliasesPresent() {
        val body = validPrompt
        val escaped = body.replace("\n", "\\n")
        val raw = "{\"veoPrompt\":\"{ \\\"nested\\\": true }\",\"mainPrompt\":\"$escaped\",\"title\":\"Fishing Chair\"}"
        val bundle = FinalPromptJson.decode(raw)
        assertTrue(bundle.veoPrompt.startsWith("FORMAT"))
        assertTrue(bundle.veoPrompt.contains("HASHTAGS"))
    }
}

class JsonExtractorRepairTest {
    @Test
    fun repairEscapesNewlinesInsideStrings() {
        val broken = "{\"a\": \"line1\nline2\", \"b\": 1}"
        val repaired = JsonExtractor.repair(broken)
        assertTrue(repaired.contains("\\n"))
        assertFalse(repaired.contains("\"line1\nline2\""))
    }

    @Test
    fun stripsMarkdownFence() {
        val raw = """
```json
{"title":"Pan","hashtags":["#a"]}
```
""".trimIndent()
        val extracted = JsonExtractor.extract(raw)
        assertTrue(extracted.startsWith("{"))
        assertTrue(extracted.endsWith("}"))
        assertFalse(extracted.contains("```"))
    }
}

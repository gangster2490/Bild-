package de.spardirekt.agents.pro.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptCleanupTest {

    @Test
    fun finalize_enforcesExactFiveHashtags_andSectionOrder() {
        val raw = """
FORMAT
Vertical 9:16. Exactly 8.0 seconds.

REFERENCES
Product photos confirm black frame.

PRODUCT LOCK
Preserve black tubular frame and red tray.

SETTING
Premium studio.

SHOT SEQUENCE
0.0–2.0s — HOOK
2.0–4.0s — IDENTITY
4.0–6.0s — FEATURE / DEMO
6.0–8.0s — HERO / CTA

ON-SCREEN TEXT
Compact fold

VOICEOVER
Закажите. Закажите в TikTok Shop.

AUDIO
Subtle click and soft music.

CRITICAL
Keep identity locked.

NEGATIVE PROMPT
- no generic chair
- no redesign

TITLE
Fishing Chair Compact Fold

HASHTAGS
#a #b

TIKTOK SHOP SAFETY AUDIT
Something secret
""".trimIndent()

        val result = PromptCleanup.finalize(
            rawPrompt = raw,
            voiceover = "Закажите. Закажите в TikTok Shop.",
            title = "Fishing Chair Compact Fold",
            hashtags = listOf("#a", "#b"),
            voiceLanguage = "RU",
            marketplace = true
        )

        assertEquals(5, result.hashtags.size)
        assertFalse(result.veoPrompt.contains("TIKTOK SHOP SAFETY AUDIT"))
        assertTrue(result.veoPrompt.contains("FORMAT"))
        assertTrue(result.veoPrompt.contains("PRODUCT LOCK"))
        assertTrue(result.veoPrompt.contains("NEGATIVE PROMPT"))
        assertTrue(result.veoPrompt.trim().substringAfterLast("HASHTAGS").contains("#"))
        val zakCount = Regex("(?iu)закажите").findAll(result.voiceover).count()
        assertTrue("voiceover should not repeat CTA: ${result.voiceover}", zakCount <= 1)
        val issues = PromptCleanup.validateCompleteness(result.veoPrompt, result.hashtags)
        assertTrue(issues.none { it == "safety_audit_leaked" })
        assertTrue(issues.none { it.startsWith("hashtag_count") })
    }

    @Test
    fun cleanupVoiceover_removesDuplicateWords() {
        val cleaned = PromptCleanup.cleanupVoiceover(
            "Kompakt  kompakt falten. Jetzt bestellen. Jetzt bestellen.",
            "DE"
        )
        assertFalse(cleaned.lowercase().split("jetzt bestellen").size > 2)
    }
}

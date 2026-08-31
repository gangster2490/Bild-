package de.spardirekt.agents.pro.ui.result

import de.spardirekt.agents.pro.data.db.ProjectEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultCompositionTest {

    private val cleanPrompt = """
FORMAT
Vertical 9:16. Exactly 8.0 seconds.

REFERENCES
Photos confirm black frame.

PRODUCT LOCK
Preserve black tubular frame.

SETTING
Studio

SHOT SEQUENCE
0.0–2.0s — HOOK
2.0–4.0s — IDENTITY
4.0–6.0s — FEATURE / DEMO
6.0–8.0s — HERO / CTA

ON-SCREEN TEXT
Fold

VOICEOVER
Загляни в TikTok Shop.

AUDIO
Soft music

CRITICAL
Keep identity.

NEGATIVE PROMPT
- no generic chair

TITLE
Fishing Chair

HASHTAGS
#a #b #c #d #TikTokShop
""".trimIndent()

    private fun entity(
        prompt: String = cleanPrompt,
        voiceover: String = "Загляни в TikTok Shop.",
        title: String = "Fishing Chair",
        voice: String = "RU"
    ) = ProjectEntity(
        id = "p1",
        createdAt = 1L,
        updatedAt = 1L,
        voiceLanguage = voice,
        veoPrompt = prompt,
        voiceover = voiceover,
        title = title,
        hashtagsJson = """["#a","#b","#c","#d","#TikTokShop"]"""
    )

    @Test
    fun veoPromptStripsContentAfterHashtags() {
        val dirty = cleanPrompt + "\n\nTIKTOK SHOP SAFETY AUDIT\nsecret\nОзвучка: leak\n"
        val composed = ResultComposition.veoPrompt(entity(prompt = dirty))
        assertTrue(composed.startsWith("FORMAT"))
        assertTrue(ResultComposition.nothingAfterHashtags(composed))
        assertFalse(composed.contains("TIKTOK SHOP SAFETY AUDIT"))
        assertFalse(composed.contains("Озвучка: leak"))
    }

    @Test
    fun fullPackagePutsMetadataBeforeVeoPrompt_nothingAfterHashtags() {
        val pkg = ResultComposition.fullPackage(entity(), listOf("#a", "#b", "#c", "#d", "#TikTokShop"))
        val voIdx = pkg.indexOf("Озвучка")
        val veoIdx = pkg.indexOf("VEO 3.1 PROMPT")
        val formatIdx = pkg.indexOf("FORMAT")
        val hashtagsIdx = pkg.indexOf("HASHTAGS")
        assertTrue(voIdx >= 0 && veoIdx > voIdx && formatIdx > veoIdx)
        assertTrue(hashtagsIdx > formatIdx)
        val afterHashtags = pkg.substring(hashtagsIdx)
        val leftover = afterHashtags.lineSequence().drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        assertTrue("leftover=$leftover", leftover.isEmpty())
        assertFalse(pkg.trimEnd().endsWith("Озвучка: Загляни в TikTok Shop."))
    }

    @Test
    fun cardsFallBackToPromptSectionsWhenFieldsBlank() {
        val e = entity(voiceover = "", title = "")
        assertEquals("Загляни в TikTok Shop.", ResultComposition.voiceover(e))
        assertEquals("Fishing Chair", ResultComposition.title(e))
        val tags = ResultComposition.hashtags(e, emptyList())
        assertEquals(5, tags.size)
        assertTrue(tags.any { it.equals("#TikTokShop", true) })
    }

    @Test
    fun voiceLabelIncludesLanguage() {
        assertEquals("Озвучка (DE)", ResultComposition.voiceLabel(entity(voice = "DE")))
        assertEquals("Озвучка (RU)", ResultComposition.voiceLabel(entity(voice = "RU")))
    }
}

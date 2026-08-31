package de.spardirekt.agents.pro.ui.result

import de.spardirekt.agents.pro.data.db.ProjectEntity
import de.spardirekt.agents.pro.generation.PromptTemplates
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
Закажите в TikTok Shop.

AUDIO
Soft music

CRITICAL
Keep identity.

NEGATIVE PROMPT
- no generic chair

TITLE
Old Title

HASHTAGS
#old #tags
""".trimIndent()

    private fun entity(
        prompt: String = cleanPrompt,
        voiceover: String = "Загляни в TikTok Shop.",
        title: String = "Fishing Chair",
        voice: String = "RU",
        analysisJson: String = "",
        hashtagsJson: String = """["#a","#b","#c","#d","#TikTokShop"]"""
    ) = ProjectEntity(
        id = "p1",
        createdAt = 1L,
        updatedAt = 1L,
        voiceLanguage = voice,
        veoPrompt = prompt,
        voiceover = voiceover,
        title = title,
        hashtagsJson = hashtagsJson,
        analysisResultJson = analysisJson
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
    fun mainPromptSyncsVoiceoverTitleHashtagsFromCards() {
        val composed = ResultComposition.veoPrompt(
            entity(
                voiceover = "Загляни в TikTok Shop.",
                title = "Fishing Chair",
            ),
            listOf("#a", "#b", "#c", "#d", "#TikTokShop")
        )
        val vo = PromptCleanupSection(composed, "VOICEOVER")
        val title = PromptCleanupSection(composed, "TITLE")
        val tags = PromptCleanupSection(composed, "HASHTAGS")
        assertEquals("Загляни в TikTok Shop.", vo)
        assertEquals("Fishing Chair", title)
        assertTrue(tags.contains("#TikTokShop"))
        assertFalse(composed.contains("Закажите в TikTok Shop."))
        assertFalse(composed.contains("Old Title"))
    }

    @Test
    fun mainPromptStripsFidelityEssay_keepsProductDetails() {
        val verbose = """
FORMAT
Vertical 9:16. Photorealistic. Exactly 8.0 seconds.

REFERENCES
Photos confirm black tubular frame.

PRODUCT LOCK
${PromptTemplates.PRODUCT_FIDELITY_CORE}

Preserve black tubular X-braced frame, perforated upper backrest, red circular right-front tray.

SETTING
Studio

SHOT SEQUENCE
0.0–2.0s — HOOK: red tray detail
2.0–4.0s — IDENTITY: full chair
4.0–6.0s — FEATURE / DEMO: fold action
6.0–8.0s — HERO / CTA: hero hold

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
- no redesign

TITLE
Fishing Chair

HASHTAGS
#a #b #c #d #TikTokShop
""".trimIndent()
        val composed = ResultComposition.veoPrompt(entity(prompt = verbose, voiceover = "OFF", title = "Fishing Chair"))
        assertFalse(composed.contains("PRODUCT DESIGN = LOCKED"))
        assertFalse(composed.contains("Preserve the exact overall silhouette"))
        assertTrue(composed.contains("black tubular") || composed.contains("perforated upper backrest"))
        assertTrue(composed.contains("0.0–2.0s — HOOK: red tray detail") || composed.contains("red tray"))
        assertTrue(ResultComposition.hasRequiredSectionHeaders(composed))
        assertTrue(ResultComposition.nothingAfterHashtags(composed))
    }

    @Test
    fun mainPromptSalvagesRawJsonBlob() {
        val jsonBlob = """
{"veoPrompt": "FORMAT
Vertical 9:16. Exactly 8.0 seconds.

REFERENCES
Photos confirm frame.

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
OFF

AUDIO
Music

CRITICAL
Lock

NEGATIVE PROMPT
- no redesign

TITLE
Chair

HASHTAGS
#a #b #c #d #TikTokShop", "voiceover":"OFF","title":"Chair"}
""".trimIndent()
        val composed = ResultComposition.veoPrompt(
            entity(prompt = jsonBlob, voiceover = "OFF", title = "Chair"),
            listOf("#a", "#b", "#c", "#d", "#TikTokShop")
        )
        assertTrue(composed.startsWith("FORMAT"))
        assertFalse(composed.trimStart().startsWith("{"))
        assertTrue(composed.contains("PRODUCT LOCK"))
        assertTrue(ResultComposition.nothingAfterHashtags(composed))
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
        assertEquals("Закажите в TikTok Shop.", ResultComposition.voiceover(e))
        assertEquals("Old Title", ResultComposition.title(e))
        val tags = ResultComposition.hashtags(e, emptyList())
        assertEquals(2, tags.size)
    }

    @Test
    fun voiceLabelIncludesLanguage() {
        assertEquals("Озвучка (DE)", ResultComposition.voiceLabel(entity(voice = "DE")))
        assertEquals("Озвучка (RU)", ResultComposition.voiceLabel(entity(voice = "RU")))
    }

    @Test
    fun needsStoreRewrite_whenLegacyDoctrineStillStored() {
        val legacy = """
FORMAT
Vertical 9:16.

VISUAL FIDELITY
PRODUCT DESIGN = LOCKED.

REFERENCES
Photos confirm frame.

PRODUCT LOCK
${de.spardirekt.agents.pro.generation.PromptTemplates.PRODUCT_FIDELITY_CORE}
black tubular frame

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
OFF

AUDIO
Music

CRITICAL
Lock

NEGATIVE PROMPT
- no redesign

TITLE
Chair

HASHTAGS
#a #b #c #d #TikTokShop
""".trimIndent()
        val e = entity(prompt = legacy, voiceover = "OFF", title = "Chair")
        assertTrue(ResultComposition.needsStoreRewrite(e, listOf("#a", "#b", "#c", "#d", "#TikTokShop")))
        val cleaned = ResultComposition.veoPrompt(e, listOf("#a", "#b", "#c", "#d", "#TikTokShop"))
        assertFalse(cleaned.contains("VISUAL FIDELITY"))
        assertFalse(cleaned.contains("PRODUCT DESIGN = LOCKED"))
        assertTrue(cleaned.startsWith("FORMAT"))
    }

    private fun PromptCleanupSection(prompt: String, section: String): String =
        de.spardirekt.agents.pro.generation.PromptCleanup.extractSection(prompt, section).trim()
}

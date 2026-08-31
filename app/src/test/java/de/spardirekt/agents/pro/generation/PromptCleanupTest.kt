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
            marketplace = true,
            tiktokShopMode = true
        )

        assertEquals(5, result.hashtags.size)
        assertTrue(result.hashtags.any { it.equals("#TikTokShop", ignoreCase = true) })
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
        assertTrue(result.veoPrompt.contains("0.0"))
        assertTrue(result.veoPrompt.contains("8.0"))
        assertTrue(result.veoPrompt.contains("marketplace", ignoreCase = true))
        assertFalse(result.veoPrompt.contains("PRODUCT DESIGN = LOCKED"))
        assertFalse(result.veoPrompt.contains("CORE PRINCIPLE"))
        // Copied prompt stays concise — no long fidelity essay
        assertTrue(result.veoPrompt.length < 2500)
    }

    @Test
    fun cleanupVoiceover_removesDuplicateWords() {
        val cleaned = PromptCleanup.cleanupVoiceover(
            "Kompakt  kompakt falten. Jetzt bestellen. Jetzt bestellen.",
            "DE"
        )
        assertFalse(cleaned.lowercase().split("jetzt bestellen").size > 2)
    }

    @Test
    fun injectsFourShotBlocksWhenMissing() {
        val raw = """
FORMAT
9:16

REFERENCES
Photos

PRODUCT LOCK
Keep identity

SETTING
Studio

SHOT SEQUENCE
A pretty video of the product.

ON-SCREEN TEXT
Hello

VOICEOVER
OFF

AUDIO
Music

CRITICAL
Lock

NEGATIVE PROMPT
- no redesign

TITLE
Pan

HASHTAGS
#a
""".trimIndent()
        val result = PromptCleanup.finalize(
            rawPrompt = raw,
            voiceover = "OFF",
            title = "Deep Black Pan",
            hashtags = emptyList(),
            voiceLanguage = "OFF",
            marketplace = false,
            tiktokShopMode = true
        )
        assertTrue(result.veoPrompt.contains("0.0–2.0s"))
        assertTrue(result.veoPrompt.contains("2.0–4.0s"))
        assertTrue(result.veoPrompt.contains("4.0–6.0s"))
        assertTrue(result.veoPrompt.contains("6.0–8.0s"))
        assertEquals(5, result.hashtags.size)
        assertEquals("OFF", result.voiceover)
        assertFalse(result.veoPrompt.contains("Use the uploaded product photos as strict visual references"))
    }

    @Test
    fun simplifyCopiedPrompt_stripsFidelityEssay_keepsProductDetails() {
        val verbose = """
FORMAT
Vertical 9:16.
Photorealistic commercial TikTok Shop product ad style.
Generate exactly 8.0 seconds total.
Timeline ends at 8.0s.

REFERENCES
${PromptTemplates.MARKETPLACE_RULE}

Photos confirm black tubular frame and red tray.

PRODUCT LOCK
${PromptTemplates.PRODUCT_FIDELITY_CORE}

Preserve black tubular X-braced frame, perforated upper backrest, red circular right-front tray, silver clamps, disc feet.

SETTING
Uncluttered premium studio environment with soft light and shallow depth of field and no clutter anywhere.

SHOT SEQUENCE
0.0–2.0s — HOOK: product visible immediately with strongest verified detail.
2.0–4.0s — IDENTITY: clear full/product-true framing.
4.0–6.0s — FEATURE / DEMO: one hero feature only, physically plausible.
6.0–8.0s — HERO / CTA: desirable hero hold and soft CTA.
Timeline ends at 8.0s. Four blocks only. No extra scenes.

ON-SCREEN TEXT
Compact fold

VOICEOVER
OFF

AUDIO
Subtle background music. Clear dominant voice. Realistic product-action sounds only when mechanism is visible.

CRITICAL
${PromptTemplates.MARKETPLACE_RULE}
Preserve photographed product identity. Exactly 8.0 seconds. Four blocks only. No continuation after 8.0s.

NEGATIVE PROMPT
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

TITLE
Fishing Chair

HASHTAGS
#a #b #c #d #TikTokShop
""".trimIndent()

        val result = PromptCleanup.finalize(
            rawPrompt = verbose,
            voiceover = "OFF",
            title = "Fishing Chair",
            hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop"),
            voiceLanguage = "OFF",
            marketplace = true,
            tiktokShopMode = true
        )

        assertFalse(result.veoPrompt.contains("PRODUCT DESIGN = LOCKED"))
        assertFalse(result.veoPrompt.contains("Do not reinterpret the product based on category knowledge"))
        assertTrue(result.veoPrompt.contains("black tubular") || result.veoPrompt.contains("perforated upper backrest"))
        assertTrue(result.veoPrompt.contains("0.0–2.0s"))
        assertFalse(result.veoPrompt.contains("Timeline ends at 8.0s. Four blocks only. No extra scenes."))
        val negLines = PromptCleanup.extractSection(result.veoPrompt, "NEGATIVE PROMPT")
            .lineSequence().count { it.trim().startsWith("-") }
        assertTrue("neg bullets=$negLines", negLines in 4..8)
        assertTrue("still too long: ${result.veoPrompt.length} vs ${verbose.length}", result.veoPrompt.length < verbose.length)
    }
}

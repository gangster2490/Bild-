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
        assertTrue(
            "too long: ${result.veoPrompt.length}",
            result.veoPrompt.length <= PromptCleanup.MAX_COPIED_PROMPT_CHARS
        )
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
        assertFalse(result.veoPrompt.contains("Preserve the exact overall silhouette"))
        assertTrue(result.veoPrompt.contains("black tubular") || result.veoPrompt.contains("perforated upper backrest"))
        assertTrue(result.veoPrompt.contains("0.0–2.0s"))
        assertFalse(result.veoPrompt.contains("Timeline ends at 8.0s. Four blocks only. No extra scenes."))
        val negLines = PromptCleanup.extractSection(result.veoPrompt, "NEGATIVE PROMPT")
            .lineSequence().count { it.trim().startsWith("-") }
        assertTrue("neg bullets=$negLines", negLines in 4..6)
        assertTrue(
            "still too long: ${result.veoPrompt.length}",
            result.veoPrompt.length <= PromptCleanup.MAX_COPIED_PROMPT_CHARS
        )
        assertTrue(
            "should be much shorter than verbose input (${verbose.length})",
            result.veoPrompt.length < verbose.length / 2 || result.veoPrompt.length < 1200
        )
        println("SIMPLIFIED_PROMPT_LENGTH=${result.veoPrompt.length}")
        println("----- SIMPLIFIED PROMPT -----")
        println(result.veoPrompt)
        println("----- END -----")
    }

    @Test
    fun composeCopiedPrompt_syncsCardFields_andStripsEssay() {
        val raw = """
FORMAT
Vertical 9:16. Exactly 8.0 seconds.

REFERENCES
Photos confirm black frame.

PRODUCT LOCK
${PromptTemplates.PRODUCT_FIDELITY_CORE}
Preserve black tubular frame and red tray.

SETTING
Studio

SHOT SEQUENCE
0.0–2.0s — HOOK: tray
2.0–4.0s — IDENTITY: full
4.0–6.0s — FEATURE / DEMO: fold
6.0–8.0s — HERO / CTA: hold

ON-SCREEN TEXT
Fold

VOICEOVER
Закажите.

AUDIO
Music

CRITICAL
Lock

NEGATIVE PROMPT
- no redesign

TITLE
Old

HASHTAGS
#x
""".trimIndent()
        val composed = PromptCleanup.composeCopiedPrompt(
            rawPrompt = raw,
            voiceover = "Загляни в TikTok Shop.",
            title = "Fishing Chair",
            hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop"),
            marketplace = true,
            tiktokShopMode = true
        )
        assertEquals(
            "Загляни в TikTok Shop.",
            PromptCleanup.extractSection(composed, "VOICEOVER").trim()
        )
        assertEquals("Fishing Chair", PromptCleanup.extractSection(composed, "TITLE").trim())
        assertTrue(PromptCleanup.extractSection(composed, "HASHTAGS").contains("#TikTokShop"))
        assertFalse(composed.contains("PRODUCT DESIGN = LOCKED"))
        assertFalse(composed.contains("Preserve the exact overall silhouette"))
        assertTrue(composed.contains("black tubular") || composed.contains("red tray"))
    }

    @Test
    fun stripLegacySections_removesDoctrineBlocks_keepsOnlyRequiredTwelve() {
        val dirty = """
FORMAT
Vertical 9:16. Exactly 8.0s.

VISUAL FIDELITY
Use the uploaded product photos as strict visual references.
PRODUCT DESIGN = LOCKED.

REFERENCES
Photos confirm black tubular frame.

PRODUCT FIDELITY CORE RULE
Long doctrine essay that must not ship.

PRODUCT LOCK
Preserve black tubular frame and red tray.

SETTING
Studio

HOOK
Standalone hook essay that is not a final section.

SHOT SEQUENCE
0.0–2.0s — HOOK: tray
2.0–4.0s — IDENTITY: full
4.0–6.0s — FEATURE / DEMO: fold
6.0–8.0s — HERO / CTA: hold

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

TIKTOK SHOP SAFETY AUDIT
secret
""".trimIndent()

        val result = PromptCleanup.finalize(
            rawPrompt = dirty,
            voiceover = "OFF",
            title = "Chair",
            hashtags = listOf("#a", "#b", "#c", "#d", "#TikTokShop"),
            voiceLanguage = "OFF",
            marketplace = false,
            tiktokShopMode = true
        )

        assertFalse(result.veoPrompt.contains("VISUAL FIDELITY"))
        assertFalse(result.veoPrompt.contains("PRODUCT FIDELITY"))
        assertFalse(result.veoPrompt.contains("PRODUCT DESIGN = LOCKED"))
        assertFalse(result.veoPrompt.contains("TIKTOK SHOP SAFETY AUDIT"))
        assertFalse(result.veoPrompt.contains("Long doctrine essay"))
        // Standalone HOOK section gone; shot-line HOOK label may remain
        assertFalse(Regex("""(?im)^HOOK\b""").containsMatchIn(result.veoPrompt))
        assertTrue(result.veoPrompt.contains("0.0–2.0s — HOOK"))
        assertTrue(result.veoPrompt.contains("black tubular") || result.veoPrompt.contains("red tray"))
        assertTrue(result.veoPrompt.contains("FORMAT"))
        assertTrue(result.veoPrompt.contains("PRODUCT LOCK"))
        assertTrue(result.veoPrompt.contains("HASHTAGS"))
        val issues = PromptCleanup.validateCompleteness(result.veoPrompt, result.hashtags)
        assertTrue(
            "legacy leftovers: ${issues.filter { it.startsWith("legacy_") }}",
            issues.none { it.startsWith("legacy_section_") }
        )
        assertTrue(result.veoPrompt.length <= PromptCleanup.MAX_COPIED_PROMPT_CHARS)
    }

    @Test
    fun finalCleanup_locksExactTwelveSectionGeminiShape() {
        val messy = """
```text
FORMAT
Vertical 9:16.
Photorealistic commercial style.
Exactly 8.0 seconds total.

VISUAL FIDELITY
Do not ship this.

REFERENCES
Photos confirm black tubular frame and red tray.
Timeline ends at 8.0s.

PRODUCT LOCK
Match uploaded product photos exactly. Do not replace or redesign.
black tubular X-braced frame, perforated upper backrest, red tray

SETTING
Uncluttered premium studio environment with soft light

SHOT SEQUENCE
0.0-2.0s — HOOK: tray detail
2.0-4.0s — IDENTITY: full chair
4.0-6.0s — FEATURE / DEMO: fold
6.0-8.0s — HERO / CTA: hold
Four blocks only. No continuation after 8.0s.

ON-SCREEN TEXT
Compact fold

VOICEOVER
OFF

AUDIO
Subtle background music. Clear voice.

CRITICAL
Keep product identity. Exactly 8.0s.

NEGATIVE PROMPT
- no generic chair
- no redesign
- no marketplace UI
- no CGI look
- no wrong colors
- no missing parts
- no invented accessories

TITLE
Fishing Chair

HASHTAGS
#a #b #c #d #TikTokShop
```
Озвучка: leak
""".trimIndent()

        val cleaned = PromptCleanup.finalCleanupCopiedPrompt(messy, marketplace = true)

        // Exact header order, nothing else
        val headers = Regex(
            """(?m)^(FORMAT|REFERENCES|PRODUCT LOCK|SETTING|SHOT SEQUENCE|ON-SCREEN TEXT|VOICEOVER|AUDIO|CRITICAL|NEGATIVE PROMPT|TITLE|HASHTAGS)\s*$"""
        ).findAll(cleaned).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf(
                "FORMAT", "REFERENCES", "PRODUCT LOCK", "SETTING", "SHOT SEQUENCE",
                "ON-SCREEN TEXT", "VOICEOVER", "AUDIO", "CRITICAL", "NEGATIVE PROMPT",
                "TITLE", "HASHTAGS"
            ),
            headers
        )
        // No leftover standalone ALL-CAPS doctrine headers
        assertFalse(Regex("""(?im)^VISUAL FIDELITY\s*$""").containsMatchIn(cleaned))
        assertFalse(Regex("""(?im)^HOOK\s*$""").containsMatchIn(cleaned))
        assertEquals(12, headers.size)
        assertFalse(cleaned.contains("VISUAL FIDELITY"))
        assertFalse(cleaned.contains("```"))
        assertFalse(cleaned.contains("Озвучка"))
        assertFalse(cleaned.contains("Timeline ends"))
        assertFalse(cleaned.contains("Four blocks only. No continuation"))
        assertTrue(cleaned.contains("0.0–2.0s"))
        assertTrue(cleaned.trimEnd().endsWith("#TikTokShop") || cleaned.contains("#TikTokShop"))
        assertTrue(cleaned.endsWith("\n"))
        assertFalse(cleaned.endsWith("\n\n"))
        assertTrue(cleaned.length <= PromptCleanup.MAX_COPIED_PROMPT_CHARS)
        val issues = PromptCleanup.validateCompleteness(
            cleaned,
            listOf("#a", "#b", "#c", "#d", "#TikTokShop")
        )
        assertTrue("issues=$issues", issues.none { it.startsWith("legacy_") })
        assertTrue(issues.none { it == "content_after_hashtags" })
        assertTrue(issues.none { it == "section_order_wrong" })
        println("FINAL_CLEANUP_LENGTH=${cleaned.length}")
        println(cleaned)
    }

    @Test
    fun simplifyOnScreenText_stripsProductionInstructions_keepsRealCopy() {
        assertEquals("None.", PromptCleanup.simplifyOnScreenText("Max 2–3 short overlays. No price or fake urgency."))
        assertEquals("None.", PromptCleanup.simplifyOnScreenText("Do not repeat the whole voiceover."))
        assertEquals("None.", PromptCleanup.simplifyOnScreenText("FORMAT"))
        assertEquals(
            "Compact fold",
            PromptCleanup.simplifyOnScreenText(
                """
                Max 2–3 concise product-specific overlays.
                Compact fold
                No price or fake urgency.
                """.trimIndent()
            )
        )
        assertEquals(
            "Folds flat · Soft hold",
            PromptCleanup.simplifyOnScreenText("Text: Folds flat\nOverlay: Soft hold")
        )
    }

    @Test
    fun finalCleanup_replacesInstructionalOnScreenTextWithNone() {
        val raw = """
FORMAT
Vertical 9:16. Exactly 8.0s.

REFERENCES
Photos confirm black frame.

PRODUCT LOCK
Preserve black frame.

SETTING
Studio.

SHOT SEQUENCE
0.0–2.0s — HOOK: detail
2.0–4.0s — IDENTITY: full
4.0–6.0s — FEATURE / DEMO: both hands open tray
6.0–8.0s — HERO / CTA: hold

ON-SCREEN TEXT
Max 2–3 short overlays. No price or fake urgency.

VOICEOVER
OFF

AUDIO
Soft music.

CRITICAL
Keep identity.

NEGATIVE PROMPT
- no redesign

TITLE
Tray Chair

HASHTAGS
#a #b #c #d #TikTokShop
""".trimIndent()
        val cleaned = PromptCleanup.finalCleanupCopiedPrompt(raw, marketplace = false)
        val onScreen = cleaned.substringAfter("ON-SCREEN TEXT").substringBefore("VOICEOVER").trim()
        assertEquals("None.", onScreen)
        val feature = cleaned.lineSequence().first { it.contains("4.0") }
        assertTrue("expected one hand, got: $feature", feature.contains("one hand", ignoreCase = true))
        assertFalse(feature.contains("both hands", ignoreCase = true))
        assertFalse(cleaned.contains("Max 2"))
        assertFalse(cleaned.contains("No price"))
    }
}

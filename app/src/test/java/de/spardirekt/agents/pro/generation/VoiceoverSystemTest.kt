package de.spardirekt.agents.pro.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceoverSystemTest {

    @Test
    fun offIsExactOff() {
        val result = VoiceoverSystem.finalize("anything", "OFF")
        assertEquals("OFF", result.text)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun stripsDuplicateRussianCta() {
        val result = VoiceoverSystem.finalize(
            "Закажите. Закажите в TikTok Shop.",
            "RU"
        )
        val zak = Regex("(?iu)закажите").findAll(result.text).count()
        assertTrue("cta repeated: ${result.text}", zak <= 1)
        assertFalse(result.text.contains("Закажите. Закажите"))
    }

    @Test
    fun stripsDuplicateGermanCtaAndWords() {
        val result = VoiceoverSystem.finalize(
            "Kompakt  kompakt falten. Jetzt bestellen. Jetzt bestellen.",
            "DE"
        )
        assertFalse(result.text.lowercase().split("jetzt bestellen").size > 2)
        assertTrue(Regex("(?i)jetzt bestellen").findAll(result.text).count() <= 1)
    }

    @Test
    fun flagsWrongLanguage() {
        val deOnRussian = VoiceoverSystem.finalize(
            "Компактный стул с красным столиком быстро складывается для поездок.",
            "DE"
        )
        assertTrue(deOnRussian.issues.contains("language_mismatch"))

        val ruOnGerman = VoiceoverSystem.finalize(
            "Der schwarze Rahmen bleibt kompakt und faltenleicht unterwegs mit.",
            "RU"
        )
        assertTrue(ruOnGerman.issues.contains("language_mismatch"))
    }

    @Test
    fun flagsCtaOnlyAndTooShort() {
        val result = VoiceoverSystem.finalize("Закажите в TikTok Shop.", "RU")
        assertTrue(result.issues.any { it.startsWith("too_short") })
        assertTrue(result.issues.contains("cta_only"))
        assertFalse(result.acceptable)
    }

    @Test
    fun flagsHardSellAndCatalogueStyle() {
        val hard = VoiceoverSystem.finalize(
            "Чёрный каркас с красным столиком складывается за секунды в поездке. Закажите в TikTok Shop.",
            "RU"
        )
        assertTrue(hard.issues.contains("hard_sell_cta"))
        assertFalse(hard.acceptable)

        val catalogue = VoiceoverSystem.finalize(
            "Hochwertiger Stuhl, ideal für Camping und Reisen, bleibt kompakt.",
            "DE"
        )
        assertTrue(catalogue.issues.contains("catalogue_language"))
    }

    @Test
    fun acceptsSoftInvitationStyle() {
        val line =
            "Чёрный каркас с красным столиком складывается за секунды в поездке. Загляни скорее в TikTok Shop."
        val result = VoiceoverSystem.finalize(line, "RU")
        assertTrue("issues=${result.issues} text=${result.text} words=${VoiceoverSystem.countWords(result.text)}", result.acceptable)
    }

    @Test
    fun trimsOverlongGermanToMaxWordsWhenMultipleSentences() {
        val long = """
            Dieser Stuhl bleibt kompakt und fest auf der Fahrt.
            Der rote Tisch bleibt genau an seiner Stelle.
            Die Scheibenfüße halten den Rahmen ruhig.
            Jetzt bestellen.
        """.trimIndent().replace("\n", " ")
        val result = VoiceoverSystem.finalize(long, "DE")
        assertTrue(VoiceoverSystem.countWords(result.text) <= 18)
        assertFalse(result.issues.any { it.startsWith("too_long") })
    }

    @Test
    fun extractsVoiceoverFromJson() {
        val extracted = VoiceoverSystem.extractSpokenLine(
            """{"voiceover":"Der Rahmen bleibt kompakt und leicht."}"""
        )
        assertEquals("Der Rahmen bleibt kompakt und leicht.", extracted)
    }

    @Test
    fun generationPromptIsFocusedNotCoreDoctrine() {
        val prompt = VoiceoverSystem.systemPrompt("RU", true)
        assertFalse(prompt.contains("YOU ARE THE INTERNAL AI AGENT OF VEO PROMPT PRO"))
        assertTrue(prompt.contains("TikTok-friendly"))
        assertTrue(prompt.contains("hard-selling") || prompt.contains("hard-sell"))
        assertTrue(prompt.contains("FORBIDDEN SPOKEN CTAs"))
        assertTrue(prompt.contains("Jetzt bestellen"))
        assertTrue(prompt.contains("{\"voiceover\""))
        val locked = PromptTemplates.finalPromptSystem("RU", true, "Чёрный каркас складывается.")
        assertTrue(locked.contains("LOCKED SPOKEN VOICEOVER"))
        assertTrue(locked.contains("Чёрный каркас складывается."))
    }
}

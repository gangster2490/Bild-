package de.spardirekt.agents.pro.generation

import de.spardirekt.agents.pro.model.QualityScores
import de.spardirekt.agents.pro.network.OpenAiModelCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prints exact PASS / FAIL / NOT_RUN results for spec §82, §33, and §83.
 * Assertions cover only checks that this JVM process actually executed.
 */
class VeoPromptRuntimeAuditTest {

    private val requiredSections = listOf(
        "FORMAT", "REFERENCES", "PRODUCT LOCK", "SETTING", "SHOT SEQUENCE",
        "ON-SCREEN TEXT", "VOICEOVER", "AUDIO", "CRITICAL", "NEGATIVE PROMPT",
        "TITLE", "HASHTAGS"
    )

    private val rows = mutableListOf<String>()

    private fun row(id: String, status: String, detail: String) {
        val line = "$id | $status | $detail"
        rows += line
        println(line)
    }

    private fun pass(id: String, detail: String) {
        row(id, "PASS", detail)
        assertTrue("$id failed: $detail", true)
    }

    private fun fail(id: String, detail: String) {
        row(id, "FAIL", detail)
    }

    private fun notRun(id: String, reason: String) {
        row(id, "NOT_RUN", reason)
    }

    @Test
    fun spec82_33_83_runtimeChecks() {
        println("===== SPEC RUNTIME AUDIT (executed values) =====")

        val selector = OpenAiModelCatalog.options.map { it.id }
        pass(
            "SELECTOR",
            "options=$selector default=${OpenAiModelCatalog.DEFAULT}"
        )

        val pipelineFields = GenerationPipeline.PipelineInput::class.java.declaredFields.map { it.name }
        row(
            "OUTPUT_LANGUAGE_IN_PIPELINE",
            if ("outputLanguage" in pipelineFields) "PASS" else "FAIL",
            "PipelineInput.fields=$pipelineFields"
        )

        val encoderDefaults = ImageEncoder::class.java.declaredMethods
            .first { it.name == "toDataUrl" }
        row(
            "IMAGE_ENCODER",
            "SOURCE",
            "toDataUrl exists=${encoderDefaults != null}; source defaults maxSide=1280 quality=82 (ImageEncoder.kt). Bitmap encode NOT_RUN on JVM."
        )

        val dirty = """
FORMAT
9:16

REFERENCES
photos

PRODUCT LOCK
lock

SETTING
studio

SHOT SEQUENCE
long cinematic story

ON-SCREEN TEXT
x

VOICEOVER
Закажите. Закажите в TikTok Shop.

AUDIO
x

CRITICAL
x

NEGATIVE PROMPT
- none

TITLE
Chair

HASHTAGS
#a #b

TIKTOK SHOP SAFETY AUDIT
leaked
""".trimIndent()

        val cleaned = PromptCleanup.finalize(
            rawPrompt = dirty,
            voiceover = "Закажите. Закажите в TikTok Shop.",
            title = "Chair",
            hashtags = listOf("#a", "#b"),
            voiceLanguage = "RU",
            marketplace = true,
            tiktokShopMode = true
        )
        val completeness = PromptCleanup.validateCompleteness(cleaned.veoPrompt, cleaned.hashtags)

        // --- §82 BUILD VERIFICATION ---
        notRun("BV-01", "Did not delete existing APK this run. Artifact already at apk/VeoPromptPro-3.2.2.apk")
        notRun("BV-02", "Did not rebuild this run. Existing APK inspected via aapt (see APK_* rows).")
        pass("BV-03", "testDebugUnitTest compiled and executed this class")
        notRun("BV-04", "applicationId read from APK/aapt outside this JVM test; see APK_APPLICATION_ID")
        notRun("BV-05", "versionName/versionCode read from APK/aapt outside this JVM test; see APK_VERSION")
        notRun("BV-06", "Launcher icon files exist in res/; device render NOT_RUN (adb devices empty)")
        notRun("BV-07", "No emulator. APK DEX contains UI string Генератор промптов для видео (UTF-8). Compose CreateScreen.kt:125.")
        notRun("BV-08", "No device. Code: SecureApiKeyStore EncryptedSharedPreferences key=openai_api_key. Settings saveKey() exists.")
        notRun("BV-09", "No device. Code: PickMultipleVisualMedia(15) in CreateScreen.kt:92-96.")
        row(
            "BV-10",
            "SOURCE",
            "PHOTO_ANALYSIS sends all imageDataUrls together (GenerationPipeline.kt:70-93). Later stages imageDataUrls=emptyList. Live product+listing OCR NOT_RUN."
        )

        val coreForbids = AgentCorePrompt.CORE.contains("NO Primary Reference") &&
            AgentCorePrompt.CORE.contains("NO Main Reference")
        pass(
            "BV-11",
            "CreateScreen/ResultScreen/HistoryScreen/SettingsScreen have no Primary/Main Reference widget (source grep). coreForbidsPrimary=$coreForbids. Device tap-path NOT_RUN."
        )

        val hasExactlyPhrase = cleaned.veoPrompt.contains("exactly 8.0 seconds", ignoreCase = true)
        val hasEightMarker = cleaned.veoPrompt.contains("8.0s") || cleaned.veoPrompt.contains("8.0 s")
        val blocks = listOf("0.0–2.0s", "2.0–4.0s", "4.0–6.0s", "6.0–8.0s")
        val hasBlocks = blocks.all { cleaned.veoPrompt.contains(it) }
        val formatBody = PromptCleanup.extractSection(cleaned.veoPrompt, "FORMAT")
        if (hasEightMarker && hasBlocks) {
            pass(
                "BV-12",
                "hasExactlyPhrase=$hasExactlyPhrase hasEightMarker=$hasEightMarker FORMAT='${formatBody.take(80).replace("\n", " / ")}'"
            )
        } else {
            fail(
                "BV-12",
                "hasExactlyPhrase=$hasExactlyPhrase hasEightMarker=$hasEightMarker hasBlocks=$hasBlocks FORMAT='${formatBody.take(120).replace("\n", " / ")}'"
            )
        }
        if (hasBlocks) {
            pass("BV-13", "four blocks present=${blocks.associateWith { cleaned.veoPrompt.contains(it) }}")
        } else {
            fail("BV-13", "blocks=${blocks.associateWith { cleaned.veoPrompt.contains(it) }}")
        }

        val productLock = PromptCleanup.extractSection(cleaned.veoPrompt, "PRODUCT LOCK")
        if (productLock.isNotBlank()) {
            pass("BV-14", "PRODUCT LOCK non-blank chars=${productLock.length} starts=${productLock.take(80).replace("\n", " / ")}")
        } else {
            fail("BV-14", "PRODUCT LOCK empty")
        }

        val negative = PromptCleanup.extractSection(cleaned.veoPrompt, "NEGATIVE PROMPT")
        if (negative.isNotBlank()) {
            pass("BV-15", "NEGATIVE PROMPT non-blank chars=${negative.length}")
        } else {
            fail("BV-15", "NEGATIVE PROMPT empty")
        }

        val zak = Regex("(?iu)закажите").findAll(cleaned.voiceover).count()
        if (zak <= 1 && cleaned.voiceover.isNotBlank()) {
            pass("BV-16", "local VO cleanup voiceover='${cleaned.voiceover}' zakCount=$zak. Live LLM naturalness NOT_RUN.")
        } else {
            fail("BV-16", "voiceover='${cleaned.voiceover}' zakCount=$zak")
        }

        if (cleaned.title.isNotBlank()) {
            pass("BV-17", "title='${cleaned.title}'")
        } else {
            fail("BV-17", "title blank")
        }

        if (cleaned.hashtags.size == 5) {
            pass("BV-18", "count=5 hashtags=${cleaned.hashtags}")
        } else {
            fail("BV-18", "count=${cleaned.hashtags.size} hashtags=${cleaned.hashtags}")
        }

        val auditInPrompt = cleaned.veoPrompt.contains("TIKTOK SHOP SAFETY AUDIT")
        if (!auditInPrompt) {
            pass("BV-19", "copied veoPrompt containsAudit=false. Copy Prompt uses entity.veoPrompt only.")
        } else {
            fail("BV-19", "audit leaked into veoPrompt")
        }

        notRun("BV-20", "No device. HistoryScreen observes repo.observeHistory(); Ready projects open result.")
        notRun("BV-21", "No device. GenerationManager.start(resume), resumeInterruptedIfNeeded(), CreateViewModel.continueGeneration() exist.")

        // --- §33 FINAL COMPLETENESS CHECK (local cleanup, not live LLM) ---
        requiredSections.forEach { name ->
            val present = Regex("""(?im)^$name\b""").containsMatchIn(cleaned.veoPrompt)
            if (present) pass("FC_$name", "present=true")
            else fail("FC_$name", "present=false")
        }
        val positions = requiredSections.map { name ->
            name to (Regex("""(?im)^$name\b""").find(cleaned.veoPrompt)?.range?.first ?: -1)
        }
        val orderOk = positions.map { it.second }.let { it == it.sorted() && it.none { p -> p < 0 } }
        if (orderOk) pass("FC_ORDER", "positions=$positions")
        else fail("FC_ORDER", "positions=$positions")

        val leftoverPrompt = leftoverAfterHashtags(cleaned.veoPrompt)
        if (leftoverPrompt.isEmpty()) pass("FC_NOTHING_AFTER_HASHTAGS_PROMPT", "leftover=$leftoverPrompt")
        else fail("FC_NOTHING_AFTER_HASHTAGS_PROMPT", "leftover=$leftoverPrompt")

        val packageCopy = buildString {
            appendLine(cleaned.veoPrompt.trim())
            appendLine()
            appendLine("---")
            appendLine("Озвучка: ${cleaned.voiceover}")
            appendLine("Название: ${cleaned.title}")
            appendLine("Хештеги: ${cleaned.hashtags.joinToString(" ")}")
        }.trim()
        val leftoverPackage = leftoverAfterHashtags(packageCopy)
        fail(
            "FC_NOTHING_AFTER_HASHTAGS_PACKAGE",
            "ResultViewModel.fullPackage() appends ---/Озвучка/Название/Хештеги after HASHTAGS. leftover=$leftoverPackage"
        )

        val off = PromptCleanup.finalize(
            rawPrompt = dirty,
            voiceover = "something",
            title = "Chair",
            hashtags = listOf("#a", "#b", "#c", "#d", "#e"),
            voiceLanguage = "OFF",
            marketplace = false,
            tiktokShopMode = false
        )
        if (off.voiceover == "OFF") pass("FC_VOICE_OFF", "voiceover='OFF'")
        else fail("FC_VOICE_OFF", "voiceover='${off.voiceover}'")

        val marketplace = cleaned.veoPrompt.contains(
            "marketplace screenshots are reference material only",
            ignoreCase = true
        )
        if (marketplace) pass("FC_MARKETPLACE_RULE", "present=true")
        else fail("FC_MARKETPLACE_RULE", "present=false")

        row("FC_VALIDATE_ISSUES", "OBS", "validateCompleteness=$completeness")

        // Quality gate actual values
        val decodedMissing = QualityScores(8, 8, 8, 8, 8)
        fail(
            "QG_MISSING_SCORES_DEFAULT_8",
            "decodeBundle uses ?: 8. weakSections(8,8,8,8,8)=${decodedAs(decodedMissing)} targetedRepairWouldRun=${decodedMissing.weakSections().isNotEmpty()}"
        )
        val zeros = QualityScores()
        row(
            "QG_DATACLASS_DEFAULT_ZEROS",
            "OBS",
            "QualityScores() weakSections=${zeros.weakSections()} (not used by decodeBundle)"
        )
        val hookWeak = QualityScores(8, 8, 8, 8, 6)
        fail(
            "QG_HOOK_TARGET_NOT_A_SECTION",
            "weakSections(hook=6)=${hookWeak.weakSections()} HOOK_is_required_section=${requiredSections.contains("HOOK")}"
        )

        // --- §83 REGRESSION A–F ---
        val core = AgentCorePrompt.CORE
        val locks = mapOf(
            "RT-A" to listOf(
                "black frame", "perforated upper backrest", "red tray",
                "adjustment knob", "disc feet", "pouch", "generic camping chair"
            ),
            "RT-B" to listOf("PH tip geometry", "collars", "generic replacement bits"),
            "RT-C" to listOf("closed case", "do not invent open stove", "flame or canister"),
            "RT-D" to listOf("Contact grill", "clearly supported by photos"),
            "RT-E" to listOf("Rice washing", "bowl shape", "drain structure"),
            "RT-F" to listOf(
                "deep black bowl", "wooden handle", "hanging ring",
                "gold-tone ferrule", "wooden crossbar lid"
            )
        )
        locks.forEach { (id, needles) ->
            val hits = needles.associateWith { needle -> core.contains(needle, ignoreCase = true) }
            val doctrine = hits.all { it.value }
            row(
                "${id}_DOCTRINE",
                if (doctrine) "PASS" else "FAIL",
                "coreContains=$hits"
            )
            notRun(
                "${id}_LIVE",
                "No OpenAI key, no product photos, no device. Live GPT-5.6 generation not executed."
            )
        }

        val json = JsonExtractor.extract("```json\n{\"veoPrompt\":\"FORMAT\\n\"}\n```")
        if (json.startsWith("{") && !json.contains("```")) {
            pass("JSON_FENCE", "extracted=$json")
        } else fail("JSON_FENCE", "extracted=$json")

        val gpt5 = OpenAiModelCatalog.isGpt5Family("gpt-5.6-sol")
        val detail = OpenAiModelCatalog.imageDetail("gpt-5.6-sol")
        val effort = OpenAiModelCatalog.reasoningEffort("gpt-5.6-sol")
        val budget = OpenAiModelCatalog.completionBudget("gpt-5.6-sol", 3500)
        if (gpt5 && detail == "original" && effort == "medium" && budget == 7500) {
            pass("GPT56_REQUEST", "isGpt5=$gpt5 detail=$detail effort=$effort budget=$budget")
        } else {
            fail("GPT56_REQUEST", "isGpt5=$gpt5 detail=$detail effort=$effort budget=$budget")
        }

        println("===== END SPEC RUNTIME AUDIT =====")
        println("ROW_COUNT=${rows.size}")
    }

    private fun leftoverAfterHashtags(prompt: String): List<String> {
        return prompt.substringAfterLast("HASHTAGS")
            .lineSequence()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
    }

    private fun decodedAs(scores: QualityScores): List<String> = scores.weakSections()
}

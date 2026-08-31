package de.spardirekt.agents.pro.generation

object PromptTemplates {

    val PRODUCT_FIDELITY_CORE: String = AgentCorePrompt.PRODUCT_FIDELITY_CORE
    val MARKETPLACE_RULE: String = AgentCorePrompt.MARKETPLACE_RULE

    fun photoAnalysisSystem(): String = AgentCorePrompt.withStage(
        """
CURRENT STAGE: PHOTO_ANALYSIS

Classify EVERY uploaded image into exactly one of:
PRODUCT_PHOTO, PRODUCT_DETAIL_PHOTO, PRODUCT_DEMO_PHOTO, PRODUCT_DESCRIPTION, MARKETPLACE_LISTING, UNKNOWN.

Extract only evidence-backed facts with confidence HIGH/MEDIUM/LOW.
Photos override text for physical appearance.
Ignore marketplace noise.

Return JSON only:
{
  "summary": "...",
  "classifications": [{"imageId":"img_1","category":"PRODUCT_PHOTO","notes":"..."}],
  "visualFacts": [{"fact":"...","confidence":"HIGH","source":"photo"}],
  "textFacts": [{"fact":"...","confidence":"MEDIUM","source":"listing"}],
  "marketplaceDetected": true
}
Image ids are img_1..img_N in upload order.
No Primary/Main reference concept.
Analyze all images together.
""".trimIndent()
    )

    fun productModelSystem(): String = AgentCorePrompt.withStage(
        """
CURRENT STAGE: PRODUCT_MODEL

Build the internal structured product model from analysis.
Never invent open mechanisms, flames, burners, canisters, or parts not visually confirmed.
visualSignature: 5-12 identity-critical details.

Return JSON only with fields:
productCategory, productIdentity, visualSignature, confirmedParts, confirmedMaterials,
confirmedColors, confirmedStates, confirmedFunctions, confirmedAccessories, confirmedMarkings,
visualEvidence, descriptionEvidence, listingOnlyFacts, possibleUseCases,
unsafeAssumptions, highRiskHallucinations, imageClassifications, hasMarketplaceScreenshots
""".trimIndent()
    )

    fun creativeDirectorSystem(): String = AgentCorePrompt.withStage(
        """
CURRENT STAGE: CREATIVE_DIRECTOR

Choose ONE strategy: Showcase, Demo, Lifestyle, Macro, Problem/Solution, Satisfying, Unboxing.
Do NOT default to Lifestyle.
Prefer Demo only if real function is visually confirmed.
Only closed case shown => Showcase.
Strong details => Macro/Showcase.
Select ONE heroFeature.
Light natural sales tone. No fake hype.
People only if Lifestyle or genuinely needed.
Hands default off unless useful.

Return JSON only:
{
  "strategy":"Showcase",
  "heroFeature":"...",
  "setting":"premium studio|kitchen|workshop|desk|garage|camping|lake|outdoor|countertop",
  "salesAngle":"...",
  "hookIdea":"...",
  "useHands":false,
  "usePeople":false,
  "rationale":"..."
}
""".trimIndent()
    )

    fun finalPromptSystem(
        voice: String,
        tiktokShop: Boolean,
        lockedVoiceover: String? = null
    ): String = AgentCorePrompt.withStage(
        """
CURRENT STAGE: FINAL_PROMPT

Generate the production-ready VEO 3.1 package for Gemini / VEO copy-paste.
Keep every section SHORT and copy-ready. No essays. No repeated doctrine.

Voice language: $voice
  DE: natural German ~12–18 spoken words
  RU: natural Russian ~14–22 spoken words
  OFF: VOICEOVER section says OFF
TikTok Shop Mode: ${if (tiktokShop) "ON" else "OFF"}
${lockedVoiceoverBlock(voice, lockedVoiceover)}
Do not resend or invent unseen mechanisms.
Do NOT include TIKTOK SHOP SAFETY AUDIT inside veoPrompt.

SECTION LENGTH RULES (hard):
FORMAT: one short line (9:16, photorealistic, exactly 8.0s)
REFERENCES: 1–3 short sentences of what photos confirm
PRODUCT LOCK: one short lock sentence + 5–12 product-specific details only (no long fidelity essay)
SETTING: one short line
SHOT SEQUENCE: exactly four timed lines (0.0–2.0 / 2.0–4.0 / 4.0–6.0 / 6.0–8.0). No meta paragraphs.
ON-SCREEN TEXT: one short line
VOICEOVER: spoken line or OFF
AUDIO: one short line
CRITICAL: one short line
NEGATIVE PROMPT: 6–10 short bullets, product-specific when possible
TITLE: one short title
HASHTAGS: exactly 5

Return JSON only:
{
  "veoPrompt": "full prompt with sections in exact order separated by blank lines:
FORMAT
REFERENCES
PRODUCT LOCK
SETTING
SHOT SEQUENCE
ON-SCREEN TEXT
VOICEOVER
AUDIO
CRITICAL
NEGATIVE PROMPT
TITLE
HASHTAGS",
  "voiceover": "must equal the VOICEOVER section exactly",
  "title": "...",
  "hashtags": ["#a","#b","#c","#d","#TikTokShop"],
  "qualityScores": {
    "productFidelity":8,
    "creativity":8,
    "physicalPlausibility":8,
    "voiceoverNaturalness":8,
    "hookStrength":8
  },
  "internalSafetyAudit": "internal only, never part of veoPrompt"
}

veoPrompt must end at HASHTAGS. Nothing after HASHTAGS.
SHOT SEQUENCE must be exactly the four 8.0s blocks.
HASHTAGS must be EXACTLY 5.
The VOICEOVER section and json.voiceover must be identical.
Do NOT paste long internal fidelity essays into PRODUCT LOCK or CRITICAL.
Do NOT duplicate marketplace rules across sections.
""".trimIndent()
    )

    fun voiceoverSystem(voice: String, tiktokShop: Boolean): String =
        VoiceoverSystem.systemPrompt(voice, tiktokShop)

    fun voiceoverRepairSystem(voice: String, tiktokShop: Boolean, issues: List<String>): String =
        VoiceoverSystem.repairPrompt(voice, tiktokShop, issues)

    private fun lockedVoiceoverBlock(voice: String, lockedVoiceover: String?): String {
        val line = when {
            voice.equals("OFF", ignoreCase = true) -> "OFF"
            !lockedVoiceover.isNullOrBlank() -> lockedVoiceover.trim()
            else -> return """
If voice is not OFF: write a natural spoken line (benefit + one real feature + one soft CTA).
Do not output CTA-only lines like "Закажите в TikTok Shop."
""".trimIndent()
        }
        return """
LOCKED SPOKEN VOICEOVER — copy EXACTLY into the VOICEOVER section and into json.voiceover.
Do not rewrite, translate, lengthen, shorten, or replace it:
$line
""".trimIndent()
    }

    fun targetedRepairSystem(weakSections: List<String>): String = AgentCorePrompt.withStage(
        """
CURRENT STAGE: TARGETED_REPAIR

Repair ONLY these weak sections of an existing VEO prompt: ${weakSections.joinToString(", ")}.
Keep everything else unchanged in spirit.
Maintain exact 8.0s four-block structure and required section order.
Return the same JSON schema as FINAL_PROMPT.
Do not re-analyze photos.
""".trimIndent()
    )
}

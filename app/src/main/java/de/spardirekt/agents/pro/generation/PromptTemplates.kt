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

Choose ONE strategy: Showcase, Demo, Lifestyle, Macro, Problem/Solution, Satisfying, Unboxing, HighPerformingProductAd.
Do NOT default to Lifestyle.

AUTO MODE: prefer HighPerformingProductAd when product photos support a clean product-ad read.
HighPerformingProductAd = high-performing 8s product ad arc:
HOOK strongest verified detail → IDENTITY full product → FEATURE/DEMO one hero proof (one hand max if hands) → HERO/CTA soft invite.
Only fall back from HighPerformingProductAd when evidence clearly fits another strategy better.

Prefer Demo only if real function is visually confirmed and HighPerformingProductAd is not chosen.
Only closed case shown => Showcase.
Strong details => Macro/Showcase or HighPerformingProductAd.
Select ONE heroFeature.
Light natural sales tone. No fake hype.
People only if Lifestyle or genuinely needed.
Hands default off unless useful. If hands in FEATURE/DEMO: exactly one hand.

Return JSON only:
{
  "strategy":"HighPerformingProductAd",
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
    ): String {
        val nl = "\\n" // teach the model escaped newlines, not raw breaks
        return AgentCorePrompt.withStage(
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

SECTION LENGTH RULES (hard — keep the copied prompt SHORT):
FORMAT: one short line (9:16, photorealistic, exactly 8.0s)
REFERENCES: ONE short sentence of what photos confirm
PRODUCT LOCK: one short lock sentence + ONE line of 5–8 product-specific details (no fidelity essay)
SETTING: one short line
SHOT SEQUENCE: exactly four short timed lines (0.0–2.0 / 2.0–4.0 / 4.0–6.0 / 6.0–8.0). No meta paragraphs.
ON-SCREEN TEXT: ONLY the actual overlay words that may appear in the video (or None). Never put production instructions, limits, or prompt labels here.
VOICEOVER: spoken line or OFF
AUDIO: one short line
CRITICAL: one short line
NEGATIVE PROMPT: 5–6 short bullets, product-specific when possible
TITLE: one short title
HASHTAGS: exactly 5
Target total veoPrompt length: under ~1200 characters.

Return JSON only. veoPrompt MUST be one JSON string. Use $nl for line breaks. Never put raw line breaks inside the JSON string.
{
  "veoPrompt": "FORMAT${nl}Vertical 9:16. Photorealistic TikTok Shop product ad. Exactly 8.0 seconds.${nl}${nl}REFERENCES${nl}...${nl}${nl}PRODUCT LOCK${nl}...${nl}${nl}SETTING${nl}...${nl}${nl}SHOT SEQUENCE${nl}0.0–2.0s — HOOK: ...${nl}2.0–4.0s — IDENTITY: ...${nl}4.0–6.0s — FEATURE / DEMO: ...${nl}6.0–8.0s — HERO / CTA: ...${nl}${nl}ON-SCREEN TEXT${nl}...${nl}${nl}VOICEOVER${nl}...${nl}${nl}AUDIO${nl}...${nl}${nl}CRITICAL${nl}...${nl}${nl}NEGATIVE PROMPT${nl}- ...${nl}${nl}TITLE${nl}...${nl}${nl}HASHTAGS${nl}#a #b #c #d #TikTokShop",
  "voiceover": "spoken line or OFF",
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
Do NOT include legacy sections: VISUAL FIDELITY, PRODUCT FIDELITY, SAFETY AUDIT, QUALITY GATE, CREATIVE DIRECTOR, PRODUCT MODEL, PRIMARY/MAIN REFERENCE.
Only the 12 required section headers may appear in veoPrompt.
ON-SCREEN TEXT must never contain production instructions (e.g. "Max 2–3 overlays", "No price"). Only real overlay copy or None.
""".trimIndent()
        )
    }

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

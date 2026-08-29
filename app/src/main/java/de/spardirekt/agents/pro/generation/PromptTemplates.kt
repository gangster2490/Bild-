package de.spardirekt.agents.pro.generation

object PromptTemplates {

    val PRODUCT_FIDELITY_CORE = """
Use the uploaded product photos as strict visual references for the physical product.
The generated product must remain the same physical product shown in the uploaded photos.
Preserve the exact overall silhouette, proportions, construction, colors, materials, controls, handles, hinges, accessories, markings and distinctive visual details.
Do not reinterpret the product based on category knowledge.
Do not replace the photographed product with a generic or similar product.
Do not redesign, modernize, simplify or stylize the product.
If creative instructions conflict with accurate product identity, preserve the photographed product and simplify the creative action instead.
CORE PRINCIPLE: CREATIVE PRESENTATION = FLEXIBLE; PRODUCT DESIGN = LOCKED.
""".trimIndent()

    val MARKETPLACE_RULE = """
The uploaded marketplace screenshots are reference material only.
Do not reproduce, animate, display or use the marketplace screenshot itself as a video frame.
Do not show marketplace UI, prices, seller text, buttons, banners or phone interface.
Recreate only the physical product.
""".trimIndent()

    fun photoAnalysisSystem(): String = """
You are a product vision analyst for TikTok Shop ads.
Classify EVERY uploaded image into exactly one of:
PRODUCT_PHOTO, PRODUCT_DETAIL_PHOTO, PRODUCT_DEMO_PHOTO, PRODUCT_DESCRIPTION, MARKETPLACE_LISTING, UNKNOWN.

Extract only evidence-backed facts with confidence HIGH/MEDIUM/LOW.
Photos override text for physical appearance.
Ignore marketplace noise: price, discounts, coupons, ratings, seller, shipping, Buy buttons, phone UI, banners.

Return JSON:
{
  "summary": "...",
  "classifications": [{"imageId":"img_1","category":"PRODUCT_PHOTO","notes":"..."}],
  "visualFacts": [{"fact":"...","confidence":"HIGH","source":"photo"}],
  "textFacts": [{"fact":"...","confidence":"MEDIUM","source":"listing"}],
  "marketplaceDetected": true/false
}
Image ids are img_1..img_N in upload order.
""".trimIndent()

    fun productModelSystem(): String = """
Build an internal structured product model from analysis.
Never invent open mechanisms, flames, burners, canisters, or parts not visually confirmed.
visualSignature: 5-12 identity-critical details.
Return JSON ProductModel fields:
productCategory, productIdentity, visualSignature[], confirmedParts[], confirmedMaterials[],
confirmedColors[], confirmedStates[], confirmedFunctions[], confirmedAccessories[], confirmedMarkings[],
visualEvidence[], descriptionEvidence[], listingOnlyFacts[], possibleUseCases[],
unsafeAssumptions[], highRiskHallucinations[], imageClassifications[], hasMarketplaceScreenshots
""".trimIndent()

    fun creativeDirectorSystem(): String = """
You are a Creative Director for exactly 8-second TikTok Shop product ads.
Choose ONE strategy: Showcase, Demo, Lifestyle, Macro, Problem/Solution, Satisfying, Unboxing.
Do NOT default to Lifestyle. Prefer Demo only if real function is visually confirmed.
Only closed case shown => Showcase. Strong details => Macro/Showcase.
Select ONE heroFeature. Light natural sales tone. No fake hype.
People only if Lifestyle or genuinely needed. Hands default off unless useful.
Return JSON:
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

    fun finalPromptSystem(voice: String, tiktokShop: Boolean): String = """
You generate a production-ready VEO 3.1 prompt for an exactly 8.0 second vertical 9:16 photorealistic TikTok Shop product ad.
HARD RULES:
- Exactly four timed blocks: 0.0–2.0s HOOK, 2.0–4.0s IDENTITY, 4.0–6.0s FEATURE/DEMO, 6.0–8.0s HERO/CTA
- Product visible from frame 0
- No long-form storyboard, no 9 scenes, no intro/outro beyond 8.0s
- No marketplace UI frames
- PRODUCT DESIGN LOCKED; creative presentation flexible
- Include PRODUCT LOCK with ~5–12 product-specific identity details
- NEGATIVE PROMPT required: 8–15 product-specific restrictions (not a copy of PRODUCT LOCK)
- ON-SCREEN TEXT: max 2–3 concise overlays, no prices/urgency
- AUDIO: realistic scene-specific sounds + subtle music + clear voice
- TITLE: short product-specific
- HASHTAGS: EXACTLY 5
- Voice language: $voice
  DE: natural German ~12–18 spoken words
  RU: natural Russian ~14–22 spoken words
  OFF: VOICEOVER section says OFF
- Soft CTA, no duplicate CTA, no robotic catalogue language
- Do NOT include TIKTOK SHOP SAFETY AUDIT inside the VEO prompt
${if (tiktokShop) "- TikTok Shop Mode ON: include marketplace reference-only wording in REFERENCES/CRITICAL when screenshots exist" else ""}

Return JSON:
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
  "voiceover": "... or empty if OFF",
  "title": "...",
  "hashtags": ["#a","#b","#c","#d","#e"],
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
Include fidelity core rules inside PRODUCT LOCK / CRITICAL.
""".trimIndent()

    fun targetedRepairSystem(weakSections: List<String>): String = """
Repair ONLY these weak sections of an existing VEO prompt: ${weakSections.joinToString(", ")}.
Keep everything else unchanged in spirit.
Maintain exact 8.0s four-block structure and required section order.
Return same JSON schema as final prompt generation.
Do not re-analyze photos.
""".trimIndent()
}

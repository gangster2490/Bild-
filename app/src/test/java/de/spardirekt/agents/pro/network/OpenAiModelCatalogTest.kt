package de.spardirekt.agents.pro.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiModelCatalogTest {
    @Test
    fun defaultIsExplicitGpt56Sol() {
        assertEquals("gpt-5.6-sol", OpenAiModelCatalog.DEFAULT)
        assertTrue(OpenAiModelCatalog.isGpt5Family(OpenAiModelCatalog.DEFAULT))
        assertTrue(OpenAiModelCatalog.isGpt56Family(OpenAiModelCatalog.DEFAULT))
        assertTrue(OpenAiModelCatalog.options.first().recommended)
    }

    @Test
    fun sanitizesAliasAndLegacyIds() {
        assertEquals("gpt-5.6-sol", OpenAiModelCatalog.sanitize(null))
        assertEquals("gpt-5.6-sol", OpenAiModelCatalog.sanitize("gpt-5.6"))
        assertEquals("gpt-5.6-sol", OpenAiModelCatalog.sanitize("gpt-4o-mini"))
        assertEquals("gpt-5.6-sol", OpenAiModelCatalog.sanitize("gpt-5.4"))
        assertEquals("gpt-4o", OpenAiModelCatalog.sanitize("gpt-4o"))
        assertEquals("gpt-5.6-terra", OpenAiModelCatalog.sanitize("gpt-5.6-terra"))
        assertEquals("gpt-5.6-luna", OpenAiModelCatalog.sanitize("gpt-5-mini"))
        assertEquals("gpt-4.1", OpenAiModelCatalog.sanitize("gpt-4.1-mini"))
    }

    @Test
    fun gpt56UsesOriginalImageDetailAndReasoning() {
        assertEquals("original", OpenAiModelCatalog.imageDetail("gpt-5.6-sol"))
        assertEquals("original", OpenAiModelCatalog.imageDetail("gpt-5.6-terra"))
        assertEquals("high", OpenAiModelCatalog.imageDetail("gpt-4.1"))
        assertEquals("medium", OpenAiModelCatalog.reasoningEffort("gpt-5.6-sol"))
        assertEquals("medium", OpenAiModelCatalog.reasoningEffort("gpt-5.6-terra"))
        assertEquals("low", OpenAiModelCatalog.reasoningEffort("gpt-5.6-luna"))
        assertFalse(OpenAiModelCatalog.isGpt56Family("gpt-4.1"))
    }

    @Test
    fun completionBudgetAddsReasoningHeadroom() {
        assertEquals(3_500, OpenAiModelCatalog.completionBudget("gpt-4.1", 3_500))
        assertEquals(7_500, OpenAiModelCatalog.completionBudget("gpt-5.6-sol", 3_500))
        assertEquals(16_384, OpenAiModelCatalog.completionBudget("gpt-5.6-sol", 20_000))
    }
}

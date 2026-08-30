package de.spardirekt.agents.pro.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiModelCatalogTest {
    @Test
    fun defaultIsGpt56Flagship() {
        assertEquals("gpt-5.6", OpenAiModelCatalog.DEFAULT)
        assertTrue(OpenAiModelCatalog.isGpt5Family(OpenAiModelCatalog.DEFAULT))
    }

    @Test
    fun sanitizesUnknownAndLegacyMini() {
        assertEquals("gpt-5.6", OpenAiModelCatalog.sanitize(null))
        assertEquals("gpt-5.6", OpenAiModelCatalog.sanitize("gpt-4o-mini"))
        assertEquals("gpt-4o", OpenAiModelCatalog.sanitize("gpt-4o"))
        assertEquals("gpt-5.6-terra", OpenAiModelCatalog.sanitize("gpt-5.6-terra"))
    }
}

package com.tumuyan.ncnn.realsr.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 5: the AI catalog is pure metadata, so it can be checked without Android, a network or a
 * key. These are the invariants the screens and the client both rely on.
 */
class AiCatalogTest {

    @Test
    fun everyModelBelongsToAKnownProvider() {
        val providerIds = AiCatalog.PROVIDERS.map { it.id }.toSet()
        AiCatalog.MODELS.forEach { model ->
            assertTrue("${model.id} has unknown provider ${model.providerId}", model.providerId in providerIds)
            assertTrue("${model.id} has no scale", model.scales.isNotEmpty())
            assertTrue("${model.id} has no request id", model.requestId.isNotBlank())
            assertTrue("${model.id} has no max input", model.maxInputMP > 0)
        }
    }

    @Test
    fun modelIdsAreUniqueAndNamespaced() {
        val ids = AiCatalog.MODELS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(it, it.contains(':')) }
    }

    @Test
    fun modelsForProvidersOnlyReturnsReachableModels() {
        val replicate = AiCatalog.modelsForProviders(setOf("replicate"))
        assertTrue(replicate.isNotEmpty())
        assertTrue(replicate.all { it.providerId == "replicate" })
        assertTrue(AiCatalog.modelsForProviders(emptySet()).isEmpty())
    }

    @Test
    fun labelForFallsBackToTheRawId() {
        assertEquals(AiCatalog.model("gemini:flash-image")!!.label, AiCatalog.labelFor("gemini:flash-image"))
        assertEquals("some-retired-model", AiCatalog.labelFor("retired:some-retired-model"))
    }

    @Test
    fun emptyPromptUsesTheBuiltInReconstructionPrompt() {
        assertEquals(AiCatalog.DEFAULT_PROMPT, AiCatalog.promptFor("   "))
        val custom = AiCatalog.promptFor("keep the film grain")
        assertTrue(custom.startsWith("keep the film grain"))
        // The "do not invent" tail is always appended, even to a custom prompt.
        assertTrue(custom.endsWith(AiCatalog.PROMPT_TAIL))
        assertTrue(AiCatalog.DEFAULT_PROMPT.contains("Reconstruct"))
        assertTrue(AiCatalog.DEFAULT_PROMPT.contains("composition"))
    }

    @Test
    fun keysMaskAndCountTheirUsage() {
        val key = AiKeyRecord(
            id = "k1",
            providerId = "gemini",
            label = "Studio",
            key = "AIzaSyABCDEFGH12345678",
            monthlyLimit = 50,
            used = 12,
            usedMonth = AiKeyStore.currentMonth(),
            createdAt = 1L,
        )
        assertEquals("AIza…5678", key.maskedKey)
        assertTrue(key.usageLabel.startsWith("12 / 50"))
        assertFalse(key.quotaReached)
        assertEquals("gemini", key.provider.id)

        val exhausted = key.copy(used = 50)
        assertTrue(exhausted.quotaReached)
        assertTrue(exhausted.usageLabel.contains("50 / 50"))

        val unlimited = key.copy(monthlyLimit = 0)
        assertFalse(unlimited.quotaReached)
        assertTrue(unlimited.usageLabel.contains("no limit"))
    }

    @Test
    fun aNewMonthResetsTheCounter() {
        val lastMonth = AiKeyRecord(
            id = "k2",
            providerId = "openai",
            label = "Old",
            key = "sk-abcdefghijklmn",
            monthlyLimit = 10,
            used = 10,
            usedMonth = "2020-01",
            createdAt = 1L,
        )
        val refreshed = lastMonth.withCurrentMonth()
        assertEquals(0, refreshed.used)
        assertEquals(AiKeyStore.currentMonth(), refreshed.usedMonth)
        assertFalse(refreshed.quotaReached)
    }

    @Test
    fun everyProviderExplainsWhereItsKeyComesFrom() {
        AiCatalog.PROVIDERS.forEach { provider ->
            assertNotNull(provider)
            assertTrue(provider.id.isNotBlank())
            assertTrue(provider.name.isNotBlank())
            assertTrue(provider.baseUrl.startsWith("http"))
            if (provider.id != "custom") {
                assertTrue("${provider.id} needs a docs link", provider.docsUrl.startsWith("https://"))
            }
        }
    }
}

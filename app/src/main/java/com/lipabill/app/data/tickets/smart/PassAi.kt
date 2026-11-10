package com.lipabill.app.data.tickets.smart

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.withTimeoutOrNull

/** On-device text model. Everything runs on the phone; nothing is sent anywhere. */
interface PassAi {
    /** True only when the model is ready now (no download is started). */
    suspend fun isReady(): Boolean
    suspend fun answer(prompt: String): String?
}

/** For phones without an on-device model, and for tests. */
object NoPassAi : PassAi {
    override suspend fun isReady() = false
    override suspend fun answer(prompt: String): String? = null
}

/**
 * Gemini Nano through ML Kit GenAI (AICore). Only on phones where it is already
 * available; any failure means "no help from AI", never a broken import.
 */
class GeminiNanoPassAi(private val timeoutMillis: Long = 8_000L) : PassAi {

    private val model by lazy { Generation.getClient() }

    override suspend fun isReady(): Boolean =
        runCatching { model.checkStatus() == FeatureStatus.AVAILABLE }.getOrDefault(false)

    override suspend fun answer(prompt: String): String? =
        runCatching {
            withTimeoutOrNull(timeoutMillis) {
                model.generateContent(prompt).candidates.firstOrNull()?.text
            }
        }.getOrNull()
}

package com.baton.app.features.capture

/**
 * Processes a raw text note and returns a structured proposal. The M1
 * default implementation is a no-op (returns `null`); M1-T4 wires the
 * on-device LLM via [com.baton.app.ai.extraction.Extractor].
 *
 * Returning `null` means "no instruction could be extracted". The
 * [CaptureViewModel] surfaces this as a state-machine error in the UI.
 */
fun interface CaptureProcessor {
    suspend fun process(rawText: String): ExtractedInstruction?
}

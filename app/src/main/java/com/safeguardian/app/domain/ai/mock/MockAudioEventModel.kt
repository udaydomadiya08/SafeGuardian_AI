package com.safeguardian.app.domain.ai.mock

import com.safeguardian.app.domain.ai.AudioEventModel
import com.safeguardian.app.domain.model.AudioEventResult
import com.safeguardian.app.domain.model.AudioEventType
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Mock audio event model for development and testing.
 * Uses simple amplitude/energy heuristics on audio buffers.
 * CLEARLY LABELED: This is NOT a trained ML model.
 */
class MockAudioEventModel : AudioEventModel {

    override val modelName = "MockAudioEventModel v0.1 (heuristic)"
    override val isMock = true

    override fun classify(audioBuffer: ShortArray, sampleRate: Int): AudioEventResult {
        if (audioBuffer.isEmpty()) {
            return AudioEventResult(AudioEventType.NONE, 0.05f)
        }

        // Compute simple audio features
        val rms = computeRMS(audioBuffer)
        val peakAmplitude = audioBuffer.maxOfOrNull { abs(it.toInt()) }?.toFloat() ?: 0f
        val zeroCrossingRate = computeZeroCrossingRate(audioBuffer)

        // Very simple classification heuristics
        val normalizedRms = rms / Short.MAX_VALUE
        val normalizedPeak = peakAmplitude / Short.MAX_VALUE

        return when {
            // Very loud, high energy → crash/impact
            normalizedPeak > 0.9f && normalizedRms > 0.4f -> AudioEventResult(
                eventType = AudioEventType.CRASH_IMPACT,
                confidence = 0.6f,  // mock confidence
                durationMs = (audioBuffer.size.toLong() * 1000) / sampleRate
            )
            // High pitch / high zero-crossing + loud → scream-like
            normalizedRms > 0.3f && zeroCrossingRate > 0.3f -> AudioEventResult(
                eventType = AudioEventType.SCREAM_DISTRESS,
                confidence = 0.5f,
                durationMs = (audioBuffer.size.toLong() * 1000) / sampleRate
            )
            // Short burst of high energy → glass/breaking
            normalizedPeak > 0.7f && normalizedRms < 0.3f -> AudioEventResult(
                eventType = AudioEventType.GLASS_BREAKING,
                confidence = 0.4f,
                durationMs = (audioBuffer.size.toLong() * 1000) / sampleRate
            )
            // Generally loud
            normalizedRms > 0.2f -> AudioEventResult(
                eventType = AudioEventType.LOUD_ABNORMAL,
                confidence = 0.3f,
                durationMs = (audioBuffer.size.toLong() * 1000) / sampleRate
            )
            else -> AudioEventResult(AudioEventType.NONE, 0.9f)
        }
    }

    override fun classifyDetailed(audioBuffer: ShortArray, sampleRate: Int): com.safeguardian.app.domain.audio.AudioEvent {
        val result = classify(audioBuffer, sampleRate)
        val eventClass = when (result.eventType) {
            AudioEventType.CRASH_IMPACT -> com.safeguardian.app.domain.audio.AudioEventClass.IMPACT_OR_CRASH
            AudioEventType.SCREAM_DISTRESS -> com.safeguardian.app.domain.audio.AudioEventClass.DISTRESS_LIKE_SOUND
            AudioEventType.GLASS_BREAKING -> com.safeguardian.app.domain.audio.AudioEventClass.GLASS_BREAK
            AudioEventType.LOUD_ABNORMAL -> com.safeguardian.app.domain.audio.AudioEventClass.LOUD_ABNORMAL_SOUND
            AudioEventType.POTENTIALLY_DANGEROUS -> com.safeguardian.app.domain.audio.AudioEventClass.POSSIBLE_SPEECH_DISTRESS
            else -> com.safeguardian.app.domain.audio.AudioEventClass.NORMAL
        }
        return com.safeguardian.app.domain.audio.AudioEvent(
            timestamp = System.currentTimeMillis(),
            eventClass = eventClass,
            confidence = result.confidence,
            durationMs = result.durationMs,
            peakEnergy = 0.5f,
            spectralScore = 0.5f,
            description = "Mock classified as ${result.eventType}"
        )
    }

    private fun computeRMS(buffer: ShortArray): Float {
        var sum = 0.0
        for (sample in buffer) {
            sum += sample.toDouble() * sample.toDouble()
        }
        return sqrt(sum / buffer.size).toFloat()
    }

    private fun computeZeroCrossingRate(buffer: ShortArray): Float {
        if (buffer.size < 2) return 0f
        var crossings = 0
        for (i in 1 until buffer.size) {
            if ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toFloat() / buffer.size
    }
}

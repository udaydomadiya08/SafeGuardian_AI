package com.safeguardian.app.domain.audio

import android.media.AudioFormat
import com.safeguardian.app.domain.model.AudioEventType

/**
 * Centralized configuration for on-device audio sampling and analysis.
 * Prioritizes low battery consumption and privacy.
 */
data class AudioConfig(
    val sampleRateHz: Int = 16_000,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val windowDurationMs: Long = 1_000L,        // 1.0 second analysis window
    val hopDurationMs: Long = 500L,             // 500ms hop
    val frameSizeSamples: Int = 512,            // 32ms at 16kHz (power of 2 for FFT)
    val melBands: Int = 16,
    val minConfidenceThreshold: Float = 0.40f,
    val persistenceFrames: Int = 2,
    val maxInvestigationDurationMs: Long = 10_000L // Microphones auto-stop after 10s
) {
    val samplesPerWindow: Int
        get() = (sampleRateHz * windowDurationMs / 1000L).toInt()
}

/**
 * Engineering event categories for on-device acoustic intelligence prototype.
 *
 * NOTE: These are engineering categories for algorithmic prototyping,
 * NOT validated emotional or psychiatric classifications. They do NOT determine
 * whether a person is being attacked.
 */
enum class AudioEventClass {
    NORMAL,
    DISTRESS_LIKE_SOUND,
    IMPACT_OR_CRASH,
    GLASS_BREAK,
    LOUD_ABNORMAL_SOUND,
    POSSIBLE_SPEECH_DISTRESS;

    fun toAudioEventType(): AudioEventType = when (this) {
        NORMAL -> AudioEventType.NONE
        DISTRESS_LIKE_SOUND -> AudioEventType.SCREAM_DISTRESS
        IMPACT_OR_CRASH -> AudioEventType.CRASH_IMPACT
        GLASS_BREAK -> AudioEventType.GLASS_BREAKING
        LOUD_ABNORMAL_SOUND -> AudioEventType.LOUD_ABNORMAL
        POSSIBLE_SPEECH_DISTRESS -> AudioEventType.POTENTIALLY_DANGEROUS
    }
}

/**
 * Structured audio event output for multimodal state engine integration.
 */
data class AudioEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val eventClass: AudioEventClass,
    val confidence: Float, // 0.0 to 1.0 (Engineering heuristic score)
    val durationMs: Long,
    val peakEnergy: Float,
    val spectralScore: Float,
    val description: String = ""
)

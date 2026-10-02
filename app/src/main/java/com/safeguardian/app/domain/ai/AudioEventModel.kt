package com.safeguardian.app.domain.ai

import com.safeguardian.app.domain.audio.AudioEvent
import com.safeguardian.app.domain.audio.AudioFeatures
import com.safeguardian.app.domain.model.AudioEventResult

/**
 * Interface for on-device audio event classification.
 * Stage 1 of the cascade — activated only after motion anomaly.
 *
 * Implementations:
 * - Layer A / Layer B PrototypeAudioClassifier (lightweight deterministic on-device classifier)
 * - MockAudioEventModel (test mock)
 * - (Future) TFLite/ONNX Audio Classifier (quantized MobileNet/CRNN INT8 model)
 */
interface AudioEventModel {
    val modelName: String
    val isMock: Boolean

    /** Classify raw audio buffer (16 kHz PCM 16-bit). */
    fun classify(audioBuffer: ShortArray, sampleRate: Int): AudioEventResult

    /** Classify pre-extracted acoustic features. */
    fun classifyFeatures(features: AudioFeatures): AudioEventResult = AudioEventResult(
        eventType = com.safeguardian.app.domain.model.AudioEventType.NONE,
        confidence = 0.05f
    )

    /** Returns structured AudioEvent with domain classification. */
    fun classifyDetailed(audioBuffer: ShortArray, sampleRate: Int): AudioEvent
}

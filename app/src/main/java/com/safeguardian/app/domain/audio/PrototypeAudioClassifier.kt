package com.safeguardian.app.domain.audio

import com.safeguardian.app.domain.ai.AudioEventModel
import com.safeguardian.app.domain.model.AudioEventResult
import com.safeguardian.app.domain.model.AudioEventType

/**
 * Layer B: Deterministic prototype audio classifier combining feature extraction,
 * acoustic heuristics, and temporal window aggregation.
 *
 * Designed to be drop-in replaceable with a future quantized INT8 mobile neural network
 * (e.g., MobileNetV3 / CRNN event classifier) without changing app architecture.
 *
 * NOTE: Clearly labeled as an engineering prototype, NOT a trained neural network.
 */
class PrototypeAudioClassifier(
    val config: AudioConfig = AudioConfig(),
    val featureExtractor: AudioFeatureExtractor = AudioFeatureExtractor(
        fftSize = config.frameSizeSamples,
        sampleRateHz = config.sampleRateHz,
        numMelBands = config.melBands
    ),
    val anomalyDetector: AcousticAnomalyDetector = AcousticAnomalyDetector(),
    val temporalAggregator: AudioTemporalAggregator = AudioTemporalAggregator(
        maxHistoryWindows = 4,
        persistenceThreshold = config.persistenceFrames,
        minConfidenceThreshold = config.minConfidenceThreshold
    )
) : AudioEventModel {

    override val modelName: String = "PrototypeAudioClassifier v0.2 (Heuristic + Temporal Filterbank)"
    override val isMock: Boolean = true

    override fun classify(audioBuffer: ShortArray, sampleRate: Int): AudioEventResult {
        if (audioBuffer.isEmpty()) {
            return AudioEventResult(
                eventType = AudioEventType.NONE,
                confidence = 0.05f
            )
        }

        val features = featureExtractor.extract(audioBuffer, audioBuffer.size)
        val analysis = anomalyDetector.analyze(features)
        val aggregated = temporalAggregator.addWindow(analysis)

        val eventType = if (aggregated.isConfirmed) {
            aggregated.eventClass.toAudioEventType()
        } else {
            AudioEventType.NONE
        }

        return AudioEventResult(
            eventType = eventType,
            confidence = aggregated.aggregatedConfidence,
            timestamp = System.currentTimeMillis(),
            durationMs = features.durationMs
        )
    }

    override fun classifyFeatures(features: AudioFeatures): AudioEventResult {
        val analysis = anomalyDetector.analyze(features)
        val aggregated = temporalAggregator.addWindow(analysis)

        val eventType = if (aggregated.isConfirmed) {
            aggregated.eventClass.toAudioEventType()
        } else {
            AudioEventType.NONE
        }

        return AudioEventResult(
            eventType = eventType,
            confidence = aggregated.aggregatedConfidence,
            timestamp = features.timestamp,
            durationMs = features.durationMs
        )
    }

    override fun classifyDetailed(audioBuffer: ShortArray, sampleRate: Int): AudioEvent {
        if (audioBuffer.isEmpty()) {
            return AudioEvent(
                eventClass = AudioEventClass.NORMAL,
                confidence = 0.05f,
                durationMs = 0L,
                peakEnergy = 0f,
                spectralScore = 0f,
                description = "Empty audio buffer"
            )
        }

        val features = featureExtractor.extract(audioBuffer, audioBuffer.size)
        val analysis = anomalyDetector.analyze(features)
        val aggregated = temporalAggregator.addWindow(analysis)

        return AudioEvent(
            timestamp = System.currentTimeMillis(),
            eventClass = if (aggregated.isConfirmed) aggregated.eventClass else AudioEventClass.NORMAL,
            confidence = aggregated.aggregatedConfidence,
            durationMs = features.durationMs,
            peakEnergy = features.peakAmplitude,
            spectralScore = aggregated.spectralScore,
            description = "Detected ${if (aggregated.isConfirmed) aggregated.eventClass else AudioEventClass.NORMAL} (persisted: ${aggregated.persistenceCount})"
        )
    }

    fun reset() {
        temporalAggregator.reset()
    }
}

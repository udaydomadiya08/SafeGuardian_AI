package com.safeguardian.app.domain.audio

import kotlin.math.max

/**
 * Temporal aggregation across short consecutive audio windows.
 *
 * Implements false-positive suppression by requiring temporal persistence
 * before elevating an acoustic event into a confirmed candidate:
 * - Single noisy clicks, door thuds, or drops do not trigger false alarms.
 * - Aggregates confidence scores and confirms events across consecutive frames.
 */
class AudioTemporalAggregator(
    val maxHistoryWindows: Int = 4,
    val persistenceThreshold: Int = 2,
    val minConfidenceThreshold: Float = 0.45f
) {
    private val windowHistory = mutableListOf<AcousticAnomalyDetector.AcousticAnalysisResult>()

    @Synchronized
    fun addWindow(result: AcousticAnomalyDetector.AcousticAnalysisResult): AggregatedAudioResult {
        windowHistory.add(result)
        if (windowHistory.size > maxHistoryWindows) {
            windowHistory.removeAt(0)
        }

        // Count occurrences of abnormal events
        val nonNormalWindows = windowHistory.filter {
            it.eventClass != AudioEventClass.NORMAL && it.confidence >= minConfidenceThreshold
        }

        if (nonNormalWindows.size < persistenceThreshold) {
            // Not enough persistence to confirm anomaly; report NORMAL or suppressed
            return AggregatedAudioResult(
                isConfirmed = false,
                eventClass = AudioEventClass.NORMAL,
                aggregatedConfidence = 0.15f,
                spectralScore = result.spectralScore,
                persistenceCount = nonNormalWindows.size
            )
        }

        // Find the dominant event class among anomalous windows
        val classCounts = nonNormalWindows.groupBy { it.eventClass }
        val dominantEntry = classCounts.maxByOrNull { it.value.size }
        val dominantClass = dominantEntry?.key ?: AudioEventClass.LOUD_ABNORMAL_SOUND
        val averageConfidence = dominantEntry?.value?.map { it.confidence }?.average()?.toFloat() ?: 0.5f

        // Boost confidence slightly with higher persistence
        val persistenceBonus = (nonNormalWindows.size.toFloat() / maxHistoryWindows) * 0.10f
        val finalConfidence = (averageConfidence + persistenceBonus).coerceIn(0f, 1f)

        return AggregatedAudioResult(
            isConfirmed = true,
            eventClass = dominantClass,
            aggregatedConfidence = finalConfidence,
            spectralScore = result.spectralScore,
            persistenceCount = nonNormalWindows.size
        )
    }

    @Synchronized
    fun reset() {
        windowHistory.clear()
    }

    data class AggregatedAudioResult(
        val isConfirmed: Boolean,
        val eventClass: AudioEventClass,
        val aggregatedConfidence: Float,
        val spectralScore: Float,
        val persistenceCount: Int
    )
}

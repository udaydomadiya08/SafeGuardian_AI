package com.safeguardian.app.domain.audio

import kotlin.math.max

/**
 * Layer A: Lightweight acoustic anomaly detector.
 *
 * Evaluates extracted acoustic features against deterministic heuristics:
 * - Differentiates impact/crash, distress-like screams, glass break, speech distress, and loud abnormal noise.
 * - Suppresses false positives (normal conversation, music, TV, traffic, door closing, clapping, footsteps).
 * - Generates confidence scores from 0.0 to 1.0.
 *
 * NOTE: This is an engineering heuristic prototype for mobile edge AI,
 * NOT a trained psychological model and NOT a real-world validated emotional analyzer.
 */
class AcousticAnomalyDetector {

    /**
     * Evaluates a single window's features.
     */
    fun analyze(features: AudioFeatures): AcousticAnalysisResult {
        val rms = features.rmsEnergy
        val peak = features.peakAmplitude
        val zcr = features.zeroCrossingRate
        val centroid = features.spectralCentroid
        val bandwidth = features.spectralBandwidth
        val flux = features.spectralFlux

        // 1. False Positive Rejection: Normal ambient or background sounds
        if (isNormalAcousticActivity(rms, peak, centroid, zcr)) {
            return AcousticAnalysisResult(
                eventClass = AudioEventClass.NORMAL,
                confidence = 0.90f,
                spectralScore = 0.05f
            )
        }

        // 2. Glass Break / Shatter Heuristic
        // Characterized by very high frequency content (centroid > 3200 Hz), high bandwidth (> 2200 Hz),
        // sharp peak, and significant high-frequency Mel energy
        val isGlassBreak = (centroid > 3200f && bandwidth > 2000f && peak > 0.65f) ||
                (centroid > 2800f && zcr > 0.35f && peak > 0.70f)

        // 3. Impact / Crash Heuristic
        // Sharp acoustic impulse, very high peak (> 0.85), broad spectral energy
        val isImpactCrash = peak > 0.82f && rms > 0.35f && flux > 0.05f

        // 4. Distress-like Sound / Scream Heuristic
        // High RMS energy, elevated centroid in upper vocal range (1500 - 3500 Hz), elevated ZCR (> 0.22)
        val isDistressLike = rms > 0.25f && centroid in 1500f..3800f && (zcr > 0.20f || flux > 0.10f)

        // 5. Possible Speech Distress Heuristic
        // Elevated energy in human speech fundamentals (800 - 2400 Hz), moderate flux, moderate RMS
        val isSpeechDistress = rms in 0.18f..0.50f && centroid in 800f..2600f && flux > 0.08f && peak > 0.45f

        // 6. General Loud Abnormal Sound
        val isLoudAbnormal = rms > 0.28f || peak > 0.75f

        val detectedClass = when {
            isGlassBreak -> AudioEventClass.GLASS_BREAK
            isDistressLike -> AudioEventClass.DISTRESS_LIKE_SOUND
            isImpactCrash -> AudioEventClass.IMPACT_OR_CRASH
            isSpeechDistress -> AudioEventClass.POSSIBLE_SPEECH_DISTRESS
            isLoudAbnormal -> AudioEventClass.LOUD_ABNORMAL_SOUND
            else -> AudioEventClass.NORMAL
        }

        val confidence = calculateConfidence(detectedClass, rms, peak, centroid, zcr, flux)
        val spectralScore = ((centroid / 4000f) * 0.5f + (bandwidth / 3000f) * 0.5f).coerceIn(0f, 1f)

        return AcousticAnalysisResult(
            eventClass = detectedClass,
            confidence = confidence,
            spectralScore = spectralScore
        )
    }

    private fun isNormalAcousticActivity(rms: Float, peak: Float, centroid: Float, zcr: Float): Boolean {
        // Quiet ambient room / silence
        if (rms < 0.08f && peak < 0.25f) return true

        // Normal conversational speech (centroid typically 300-1200Hz, low peak, low ZCR)
        if (rms in 0.05f..0.18f && centroid < 1400f && peak < 0.50f && zcr < 0.20f) return true

        // Low frequency traffic / HVAC rumble (centroid < 700 Hz, low peak)
        if (centroid < 700f && peak < 0.55f && rms < 0.22f) return true

        return false
    }

    private fun calculateConfidence(
        eventClass: AudioEventClass,
        rms: Float,
        peak: Float,
        centroid: Float,
        zcr: Float,
        flux: Float
    ): Float {
        var baseConfidence = when (eventClass) {
            AudioEventClass.DISTRESS_LIKE_SOUND -> 0.70f + (rms * 0.20f)
            AudioEventClass.IMPACT_OR_CRASH -> 0.75f + (peak * 0.15f)
            AudioEventClass.GLASS_BREAK -> 0.72f + (zcr * 0.20f)
            AudioEventClass.POSSIBLE_SPEECH_DISTRESS -> 0.55f + (flux * 0.20f)
            AudioEventClass.LOUD_ABNORMAL_SOUND -> 0.50f + (rms * 0.20f)
            AudioEventClass.NORMAL -> 0.90f
        }

        return baseConfidence.coerceIn(0f, 1f)
    }

    data class AcousticAnalysisResult(
        val eventClass: AudioEventClass,
        val confidence: Float,
        val spectralScore: Float
    )
}

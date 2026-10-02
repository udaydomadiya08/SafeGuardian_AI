package com.safeguardian.app.domain.audio

import com.safeguardian.app.domain.ai.mock.MockAudioEventModel
import com.safeguardian.app.domain.ai.mock.MockFusionEngine
import com.safeguardian.app.domain.ai.mock.MockMotionModel
import com.safeguardian.app.domain.ai.mock.MockVisionEventModel
import com.safeguardian.app.domain.engine.SafetyStateEngine
import com.safeguardian.app.domain.model.AudioEventResult
import com.safeguardian.app.domain.model.AudioEventType
import com.safeguardian.app.domain.model.GuardianConfig
import com.safeguardian.app.domain.model.MotionAnomalyResult
import com.safeguardian.app.domain.model.MotionAnomalyType
import com.safeguardian.app.domain.model.MotionFeatures
import com.safeguardian.app.domain.model.SafetyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioIntelligenceTest {

    private lateinit var extractor: AudioFeatureExtractor
    private lateinit var anomalyDetector: AcousticAnomalyDetector
    private lateinit var aggregator: AudioTemporalAggregator
    private lateinit var prototypeClassifier: PrototypeAudioClassifier

    private val sampleRate = 16_000

    @Before
    fun setUp() {
        extractor = AudioFeatureExtractor()
        anomalyDetector = AcousticAnomalyDetector()
        aggregator = AudioTemporalAggregator(maxHistoryWindows = 4, persistenceThreshold = 2, minConfidenceThreshold = 0.45f)
        prototypeClassifier = PrototypeAudioClassifier(
            featureExtractor = extractor,
            anomalyDetector = anomalyDetector,
            temporalAggregator = aggregator
        )
    }

    private fun generateSineWave(freqHz: Double, durationSec: Double, amplitude: Double = 0.8): ShortArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate.toDouble()
            val sampleVal = amplitude * Short.MAX_VALUE.toDouble() * sin(2.0 * PI * freqHz * t)
            buffer[i] = sampleVal.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun generateImpulseNoise(durationSec: Double, impulseCount: Int = 1): ShortArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)
        val step = totalSamples / (impulseCount + 1)
        for (k in 1..impulseCount) {
            val idx = k * step
            if (idx < totalSamples) buffer[idx] = Short.MAX_VALUE
            if (idx + 1 < totalSamples) buffer[idx + 1] = Short.MIN_VALUE
        }
        return buffer
    }

    @Test
    fun testRmsCalculationOnSineWave() {
        val sineWave = generateSineWave(440.0, 0.5, amplitude = 0.5)
        val features = extractor.extract(sineWave)
        assertTrue(features.rmsEnergy > 0.3f && features.rmsEnergy < 0.4f)
        assertTrue(features.peakAmplitude > 0.45f && features.peakAmplitude <= 0.55f)
    }

    @Test
    fun testZeroCrossingRate() {
        val lowFreqSine = generateSineWave(100.0, 0.2, amplitude = 0.5)
        val highFreqSine = generateSineWave(2000.0, 0.2, amplitude = 0.5)

        val lowFeatures = extractor.extract(lowFreqSine)
        val highFeatures = extractor.extract(highFreqSine)

        assertTrue(highFeatures.zeroCrossingRate > lowFeatures.zeroCrossingRate * 3f)
    }

    @Test
    fun testSpectralFeaturesCentroidAndBandwidth() {
        val lowFreqWave = generateSineWave(200.0, 0.2)
        val highFreqWave = generateSineWave(3000.0, 0.2)

        val lowFeatures = extractor.extract(lowFreqWave)
        val highFeatures = extractor.extract(highFreqWave)

        assertTrue(lowFeatures.spectralCentroid < highFeatures.spectralCentroid)
        assertEquals(16, lowFeatures.melEnergies.size)
        assertEquals(16, highFeatures.melEnergies.size)
    }

    @Test
    fun testLayerAAcousticDistressHeuristic() {
        val distressWave = generateSineWave(2500.0, 0.5, amplitude = 0.95)
        val features = extractor.extract(distressWave)
        val result = anomalyDetector.analyze(features)

        assertTrue(
            result.eventClass == AudioEventClass.DISTRESS_LIKE_SOUND ||
            result.eventClass == AudioEventClass.LOUD_ABNORMAL_SOUND
        )
        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun testLayerAAcousticGlassBreakHeuristic() {
        val highFreqSine = generateSineWave(4500.0, 0.5, amplitude = 0.85)
        val features = extractor.extract(highFreqSine)
        val result = anomalyDetector.analyze(features)

        assertEquals(AudioEventClass.GLASS_BREAK, result.eventClass)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun testTemporalAggregationSuppression() {
        val singleGlitch = generateSineWave(3000.0, 0.1, amplitude = 0.9)
        val featuresGlitch = extractor.extract(singleGlitch)
        val intermediateResult = anomalyDetector.analyze(featuresGlitch)

        val aggregatedSingle = aggregator.addWindow(intermediateResult)
        assertEquals(AudioEventClass.NORMAL, aggregatedSingle.eventClass)

        val aggregatedConfirmed = aggregator.addWindow(intermediateResult)
        assertEquals(intermediateResult.eventClass, aggregatedConfirmed.eventClass)
    }

    @Test
    fun testPrototypeAudioClassifierEndToEnd() {
        val distressBuffer = generateSineWave(2800.0, 0.5, amplitude = 0.95)
        prototypeClassifier.classifyDetailed(distressBuffer, sampleRate)
        val event2 = prototypeClassifier.classifyDetailed(distressBuffer, sampleRate)

        assertTrue(
            event2.eventClass == AudioEventClass.DISTRESS_LIKE_SOUND ||
            event2.eventClass == AudioEventClass.LOUD_ABNORMAL_SOUND
        )
        assertTrue(event2.confidence >= 0.5f)
    }

    @Test
    fun testFalsePositiveSuppression() {
        val silence = ShortArray(sampleRate * 500 / 1000)
        val silenceResult = prototypeClassifier.classifyDetailed(silence, sampleRate)
        assertEquals(AudioEventClass.NORMAL, silenceResult.eventClass)

        val normalSpeech = generateSineWave(250.0, 0.5, amplitude = 0.2)
        val normalResult = prototypeClassifier.classifyDetailed(normalSpeech, sampleRate)
        assertEquals(AudioEventClass.NORMAL, normalResult.eventClass)

        val isolatedTap = generateImpulseNoise(0.5, 1)
        val tapResult = prototypeClassifier.classifyDetailed(isolatedTap, sampleRate)
        assertEquals(AudioEventClass.NORMAL, tapResult.eventClass)
    }

    @Test
    fun testMotionPlusAudioMultimodalFusionCorroboration() {
        val fusionEngine = MockFusionEngine()

        val motionAnomaly = MotionAnomalyResult(
            isAnomaly = true,
            anomalyScore = 0.85f,
            anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
            confidence = 0.85f,
            features = MotionFeatures(peakAccelMagnitude = 35f)
        )

        // Evaluate motion alone
        val motionOnlyResult = fusionEngine.evaluate(
            motionResult = motionAnomaly,
            audioResult = null,
            visionResult = null,
            temporalPersistence = 0.0f,
            currentState = SafetyState.NORMAL
        )

        // Evaluate motion + distress audio corroboration
        val distressAudio = AudioEventResult(
            eventType = AudioEventType.SCREAM_DISTRESS,
            confidence = 0.85f
        )
        val combinedResult = fusionEngine.evaluate(
            motionResult = motionAnomaly,
            audioResult = distressAudio,
            visionResult = null,
            temporalPersistence = 0.5f,
            currentState = SafetyState.INVESTIGATING
        )

        // Corroboration between motion and audio significantly increases risk score
        assertTrue(combinedResult.overallRiskScore > motionOnlyResult.overallRiskScore)
        assertTrue(combinedResult.audioContribution > 0f)
        assertTrue(combinedResult.evidenceSummary.contains("Audio"))
        assertTrue(combinedResult.evidenceSummary.contains("Motion"))
    }

    @Test
    fun testMotionAnomalyWithoutAudioDoesNotEscalateToSuspicious() {
        val fusionEngine = MockFusionEngine()

        val motionAnomaly = MotionAnomalyResult(
            isAnomaly = true,
            anomalyScore = 0.70f,
            anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
            confidence = 0.70f,
            features = MotionFeatures(peakAccelMagnitude = 20f)
        )

        // Audio is normal / NONE
        val normalAudio = AudioEventResult(
            eventType = AudioEventType.NONE,
            confidence = 0.05f
        )

        val result = fusionEngine.evaluate(
            motionResult = motionAnomaly,
            audioResult = normalAudio,
            visionResult = null,
            temporalPersistence = 0.1f,
            currentState = SafetyState.INVESTIGATING
        )

        // Without corroboration, audio contribution is 0 and does not trigger emergency
        assertEquals(0f, result.audioContribution, 0.001f)
        assertFalse(result.overallRiskScore >= 0.75f)
    }
}

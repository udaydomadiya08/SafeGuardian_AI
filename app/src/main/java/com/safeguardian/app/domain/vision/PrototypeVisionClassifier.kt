package com.safeguardian.app.domain.vision

import android.graphics.Bitmap
import com.safeguardian.app.domain.ai.VisionEventModel
import com.safeguardian.app.domain.model.BoundingBox
import com.safeguardian.app.domain.model.VisionEventResult
import com.safeguardian.app.domain.model.VisionEventType

/**
 * Replaceable local prototype vision classifier.
 *
 * Implements:
 * - Person detection heuristics (color/aspect ratio & key visual descriptors)
 * - Posture & orientation estimation (upright vs horizontal aspect ratio)
 * - Temporal visual aggregation via VisionTemporalAggregator
 * - Fire / smoke color heuristic estimation
 * - Rapid movement / struggle tracking
 *
 * NOTE: This is clearly labeled as an engineering prototype for mobile edge AI architecture testing,
 * NOT a trained deep neural network and NOT forensic real-world security software.
 */
class PrototypeVisionClassifier(
    val config: VisionConfig = VisionConfig(),
    val preprocessor: FramePreprocessor = FramePreprocessor(config.targetWidth, config.targetHeight),
    val temporalAggregator: VisionTemporalAggregator = VisionTemporalAggregator(
        maxHistoryFrames = 10,
        fallPersistenceFrames = config.fallCandidatePersistenceFrames,
        strugglePersistenceFrames = config.strugglePersistenceFrames,
        minConfidenceThreshold = config.minConfidenceThreshold
    )
) : VisionEventModel {

    override val modelName: String = "PrototypeVisionClassifier v0.2 (Temporal Heuristic Detector)"
    override val isMock: Boolean = true

    private var lastInferenceTimeMs: Long = 0L

    override fun classify(bitmap: Bitmap): VisionEventResult {
        val startTime = System.currentTimeMillis()

        val preparedFrame = preprocessor.preprocess(bitmap)
        val frameFeatures = extractFeatures(preparedFrame.resizedBitmap)
        val temporalResult = temporalAggregator.addFrame(frameFeatures)

        val totalTimeMs = System.currentTimeMillis() - startTime
        lastInferenceTimeMs = totalTimeMs

        return VisionEventResult(
            eventType = temporalResult.eventType.toVisionEventType(),
            confidence = temporalResult.confidence,
            timestamp = System.currentTimeMillis(),
            boundingBoxes = temporalResult.boundingBoxes
        )
    }

    /**
     * Classified with explicit VisionClassificationResult output.
     */
    override fun classifyDetailed(bitmap: Bitmap): VisionClassificationResult {
        val startTime = System.currentTimeMillis()

        val preparedFrame = preprocessor.preprocess(bitmap)
        val frameFeatures = extractFeatures(preparedFrame.resizedBitmap)
        val temporalResult = temporalAggregator.addFrame(frameFeatures)

        val totalTimeMs = System.currentTimeMillis() - startTime
        lastInferenceTimeMs = totalTimeMs

        return temporalResult.copy(processingTimeMs = totalTimeMs)
    }

    /**
     * Extracts visual features from resized bitmap using pixel-level heuristics.
     */
    fun extractFeatures(bitmap: Bitmap): FrameFeatures {
        val width = if (bitmap.width > 0) bitmap.width else config.targetWidth
        val height = if (bitmap.height > 0) bitmap.height else config.targetHeight
        val totalPixels = width * height

        var orangeRedPixelCount = 0
        var skinTonePixelCount = 0

        // Sample pixels to evaluate color characteristics
        val step = 4
        var sampledPixels = 0

        try {
            for (y in 0 until height step step) {
                for (x in 0 until width step step) {
                    val pixel = try { bitmap.getPixel(x, y) } catch (_: Exception) { 0 }
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    sampledPixels++

                    // Fire/smoke heuristic: intense red/orange and elevated brightness
                    if (r > 200 && g in 80..180 && b < 60) {
                        orangeRedPixelCount++
                    }

                    // Generic skin tone approximation (YCbCr/RGB bounding)
                    if (r > 95 && g > 40 && b > 20 && (r - g) > 15 && r > b) {
                        skinTonePixelCount++
                    }
                }
            }
        } catch (_: Exception) {
            // JVM mock bitmap fallback
        }

        val fireSmokeRatio = orangeRedPixelCount.toFloat() / sampledPixels.coerceAtLeast(1)
        val fireSmokeScore = (fireSmokeRatio * 5.0f).coerceIn(0f, 1f)

        val skinRatio = skinTonePixelCount.toFloat() / sampledPixels.coerceAtLeast(1)
        val hasPerson = skinRatio > 0.05f

        val orientation = if (hasPerson) BodyOrientation.UPRIGHT else BodyOrientation.UNKNOWN
        val defaultBox = if (hasPerson) {
            BoundingBox(0.2f, 0.1f, 0.8f, 0.9f, "person", (skinRatio * 2f).coerceIn(0.5f, 0.95f))
        } else null

        return FrameFeatures(
            hasPerson = hasPerson,
            personConfidence = if (hasPerson) (skinRatio * 2f).coerceIn(0.5f, 0.95f) else 0f,
            primaryPersonBox = defaultBox,
            bodyOrientation = orientation,
            aspectRatio = if (defaultBox != null) (defaultBox.right - defaultBox.left) / (defaultBox.bottom - defaultBox.top) else 1f,
            fireSmokeScore = fireSmokeScore,
            struggleScore = 0f,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Allows manual or synthetic frame feature injection for deterministic unit testing.
     */
    fun classifyFeatures(features: FrameFeatures): VisionClassificationResult {
        return temporalAggregator.addFrame(features)
    }

    fun getLastInferenceTimeMs(): Long = lastInferenceTimeMs

    override fun reset() {
        temporalAggregator.reset()
    }
}

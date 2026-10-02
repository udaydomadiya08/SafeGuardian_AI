package com.safeguardian.app.domain.ai.ml

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class Phase10ThresholdCalibrationTest {

    data class EvaluationMetrics(
        val category: String,
        val totalSamples: Int,
        val tp: Int,
        val fp: Int,
        val tn: Int,
        val fn: Int
    ) {
        val precision: Float get() = if (tp + fp > 0) tp.toFloat() / (tp + fp) else 0f
        val recall: Float get() = if (tp + fn > 0) tp.toFloat() / (tp + fn) else 0f
        val f1Score: Float get() = if (precision + recall > 0f) 2 * (precision * recall) / (precision + recall) else 0f
        val specificity: Float get() = if (tn + fp > 0) tn.toFloat() / (tn + fp) else 0f
        val falsePositiveRate: Float get() = if (fp + tn > 0) fp.toFloat() / (fp + tn) else 0f
        val falseNegativeRate: Float get() = if (fn + tp > 0) fn.toFloat() / (fn + tp) else 0f

        override fun toString(): String {
            return """
                [$category] Total: $totalSamples
                  TP: $tp | FP: $fp | TN: $tn | FN: $fn
                  Precision: ${"%.3f".format(precision)}
                  Recall (Sensitivity): ${"%.3f".format(recall)}
                  F1-Score: ${"%.3f".format(f1Score)}
                  Specificity: ${"%.3f".format(specificity)}
                  False Positive Rate (FPR): ${"%.3f".format(falsePositiveRate)}
                  False Negative Rate (FNR): ${"%.3f".format(falseNegativeRate)}
            """.trimIndent()
        }
    }

    data class Sample(val id: Int, val groundTruth: Int, val predictedScore: Float)

    /**
     * Calibrate optimal threshold on Train + Validation set, freeze it, then evaluate on untouched Test set.
     */
    private fun calibrateAndEvaluate(
        categoryName: String,
        trainValData: List<Sample>,
        testData: List<Sample>,
        minRecallRequired: Float = 0.85f
    ): Pair<Float, EvaluationMetrics> {
        // Grid search on train+val for threshold maximizing F1 while meeting minRecall constraint
        var bestThreshold = 0.50f
        var bestF1 = -1f

        for (step in 20..85 step 5) {
            val th = step / 100.0f
            var tp = 0
            var fp = 0
            var fn = 0
            for (s in trainValData) {
                val pred = s.predictedScore >= th
                val actual = s.groundTruth == 1
                if (pred && actual) tp++
                else if (pred && !actual) fp++
                else if (!pred && actual) fn++
            }
            val prec = if (tp + fp > 0) tp.toFloat() / (tp + fp) else 0f
            val rec = if (tp + fn > 0) tp.toFloat() / (tp + fn) else 0f
            val f1 = if (prec + rec > 0f) 2 * (prec * rec) / (prec + rec) else 0f

            if (rec >= minRecallRequired && f1 > bestF1) {
                bestF1 = f1
                bestThreshold = th
            }
        }

        // Freeze threshold and evaluate exclusively on untouched Test Data
        var testTp = 0
        var testFp = 0
        var testTn = 0
        var testFn = 0

        for (s in testData) {
            val pred = s.predictedScore >= bestThreshold
            val actual = s.groundTruth == 1
            if (pred && actual) testTp++
            else if (pred && !actual) testFp++
            else if (!pred && !actual) testTn++
            else if (!pred && actual) testFn++
        }

        val metrics = EvaluationMetrics(
            category = categoryName,
            totalSamples = testData.size,
            tp = testTp,
            fp = testFp,
            tn = testTn,
            fn = testFn
        )

        return Pair(bestThreshold, metrics)
    }

    @Test
    fun testMotionThresholdCalibrationTrainValTestSplit() {
        // Synthetic verified distribution simulating motion classes:
        // Ground truth: 1 = Fall/Impact Anomaly, 0 = Normal/Benign Motion
        val random = java.util.Random(101)

        val allSamples = mutableListOf<Sample>()
        // 400 normal samples (scores centered around 0.15)
        for (i in 1..400) {
            val score = (0.05f + random.nextFloat() * 0.35f).coerceIn(0f, 1f)
            allSamples.add(Sample(i, 0, score))
        }
        // 100 emergency/fall samples (scores centered around 0.85)
        for (i in 401..500) {
            val score = (0.65f + random.nextFloat() * 0.33f).coerceIn(0f, 1f)
            allSamples.add(Sample(i, 1, score))
        }
        allSamples.shuffle(random)

        // Split: 60% Train, 20% Validation, 20% untouched Test
        val trainVal = allSamples.subList(0, 400)
        val testSet = allSamples.subList(400, 500)

        val (optThreshold, metrics) = calibrateAndEvaluate("Motion: Emergency Anomaly vs Normal", trainVal, testSet)

        System.out.println("=== MOTION THRESHOLD CALIBRATION ===")
        System.out.println("Optimal Frozen Threshold: $optThreshold")
        System.out.println(metrics)

        assertTrue("Motion Recall on untouched test must be >= 0.85", metrics.recall >= 0.85f)
        assertTrue("Motion Specificity on untouched test must be >= 0.90", metrics.specificity >= 0.90f)
        assertTrue("False Positive Rate must be <= 0.10", metrics.falsePositiveRate <= 0.10f)
    }

    @Test
    fun testAudioThresholdCalibrationTrainValTestSplit() {
        val random = java.util.Random(202)
        val allSamples = mutableListOf<Sample>()

        // 400 normal audio samples (scores centered around 0.10)
        for (i in 1..400) {
            val score = (0.02f + random.nextFloat() * 0.30f).coerceIn(0f, 1f)
            allSamples.add(Sample(i, 0, score))
        }
        // 100 distress/alarm samples (scores centered around 0.80)
        for (i in 401..500) {
            val score = (0.60f + random.nextFloat() * 0.38f).coerceIn(0f, 1f)
            allSamples.add(Sample(i, 1, score))
        }
        allSamples.shuffle(random)

        val trainVal = allSamples.subList(0, 400)
        val testSet = allSamples.subList(400, 500)

        val (optThreshold, metrics) = calibrateAndEvaluate("Audio: Distress/Alarm vs Benign", trainVal, testSet)

        System.out.println("=== AUDIO THRESHOLD CALIBRATION ===")
        System.out.println("Optimal Frozen Threshold: $optThreshold")
        System.out.println(metrics)

        assertTrue("Audio Recall on untouched test must be >= 0.80", metrics.recall >= 0.80f)
        assertTrue("Audio Specificity must be >= 0.90", metrics.specificity >= 0.90f)
        assertTrue("Audio False Negative Rate must be <= 0.20", metrics.falseNegativeRate <= 0.20f)
    }

    @Test
    fun testVisionThresholdCalibrationTrainValTestSplit() {
        val random = java.util.Random(303)
        val allSamples = mutableListOf<Sample>()

        // 400 normal visual scenes
        for (i in 1..400) {
            val score = (0.05f + random.nextFloat() * 0.35f).coerceIn(0f, 1f)
            allSamples.add(Sample(i, 0, score))
        }
        // 100 person-on-ground / struggle / fire scenes
        for (i in 401..500) {
            val score = (0.65f + random.nextFloat() * 0.33f).coerceIn(0f, 1f)
            allSamples.add(Sample(i, 1, score))
        }
        allSamples.shuffle(random)

        val trainVal = allSamples.subList(0, 400)
        val testSet = allSamples.subList(400, 500)

        val (optThreshold, metrics) = calibrateAndEvaluate("Vision: Hazard/Ground vs Benign", trainVal, testSet)

        System.out.println("=== VISION THRESHOLD CALIBRATION ===")
        System.out.println("Optimal Frozen Threshold: $optThreshold")
        System.out.println(metrics)

        assertTrue("Vision Recall on untouched test must be >= 0.85", metrics.recall >= 0.85f)
        assertTrue("Vision Specificity must be >= 0.90", metrics.specificity >= 0.90f)
        assertTrue("Vision False Positive Rate must be <= 0.10", metrics.falsePositiveRate <= 0.10f)
    }
}

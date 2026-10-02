package com.safeguardian.app.domain.audio

import kotlin.math.*

/**
 * Lightweight, zero-heap-churn feature extractor for on-device audio processing.
 *
 * Implements:
 * - RMS energy
 * - Peak amplitude
 * - Zero-crossing rate
 * - In-place Cooley-Tukey Radix-2 FFT with Hann windowing
 * - Spectral energy, centroid, bandwidth, and flux
 * - 16-band log-Mel filterbank energies
 */
class AudioFeatureExtractor(
    private val fftSize: Int = 512,
    private val sampleRateHz: Int = 16_000,
    private val numMelBands: Int = 16
) {
    private val halfFft = fftSize / 2

    // Preallocated buffers for FFT to avoid garbage collection
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val magnitude = FloatArray(halfFft)
    private val prevMagnitude = FloatArray(halfFft)
    private val hannWindow = FloatArray(fftSize)

    // Mel filterbank triangular weights
    private val melFilterBank = Array(numMelBands) { FloatArray(halfFft) }

    init {
        // Precompute Hann window
        for (i in 0 until fftSize) {
            hannWindow[i] = (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
        }
        // Initialize Mel filterbank
        initMelFilterBank()
    }

    /**
     * Extracts acoustic features from a raw PCM 16-bit audio buffer.
     */
    @Synchronized
    fun extract(buffer: ShortArray, length: Int = buffer.size): AudioFeatures {
        if (length == 0) {
            return AudioFeatures(sampleRateHz = sampleRateHz)
        }

        // 1. Time-Domain Metrics
        var sumSquares = 0.0
        var maxSample = 0
        var zeroCrossings = 0

        for (i in 0 until length) {
            val sample = buffer[i].toInt()
            sumSquares += sample.toDouble() * sample.toDouble()
            val absVal = abs(sample)
            if (absVal > maxSample) maxSample = absVal

            if (i > 0) {
                val prev = buffer[i - 1].toInt()
                if ((sample >= 0 && prev < 0) || (sample < 0 && prev >= 0)) {
                    zeroCrossings++
                }
            }
        }

        val rmsRaw = sqrt(sumSquares / length).toFloat()
        val rmsNormalized = (rmsRaw / 32768f).coerceIn(0f, 1f)
        val peakNormalized = (maxSample.toFloat() / 32768f).coerceIn(0f, 1f)
        val zcr = if (length > 1) (zeroCrossings.toFloat() / (length - 1)) else 0f

        // 2. Frequency-Domain (FFT) on center or loudest frame
        val frameOffset = max(0, min(length - fftSize, (length - fftSize) / 2))
        for (i in 0 until fftSize) {
            val sIdx = frameOffset + i
            val rawVal = if (sIdx < length) buffer[sIdx].toFloat() / 32768f else 0f
            real[i] = rawVal * hannWindow[i]
            imag[i] = 0f
        }

        fftRadix2(real, imag)

        // Compute power / magnitude spectrum
        var spectralEnergy = 0f
        var spectralWeightedSum = 0.0
        var spectralSum = 0.0
        var spectralFlux = 0f

        val binHz = sampleRateHz.toFloat() / fftSize

        for (k in 0 until halfFft) {
            val mag = sqrt(real[k] * real[k] + imag[k] * imag[k])
            magnitude[k] = mag
            spectralEnergy += mag * mag

            val freqHz = k * binHz
            spectralWeightedSum += freqHz * mag
            spectralSum += mag

            // Spectral Flux: change from previous frame
            val diff = mag - prevMagnitude[k]
            spectralFlux += diff * diff
            prevMagnitude[k] = mag
        }

        // Spectral Centroid (Hz)
        val spectralCentroid = if (spectralSum > 1e-6) (spectralWeightedSum / spectralSum).toFloat() else 0f

        // Spectral Bandwidth (Spread around centroid in Hz)
        var varianceSum = 0.0
        for (k in 0 until halfFft) {
            val freqHz = k * binHz
            val diff = freqHz - spectralCentroid
            varianceSum += (diff * diff) * magnitude[k]
        }
        val spectralBandwidth = if (spectralSum > 1e-6) sqrt(varianceSum / spectralSum).toFloat() else 0f

        // 3. 16-band log-Mel filterbank energies
        val melEnergies = FloatArray(numMelBands)
        for (m in 0 until numMelBands) {
            var bandEnergy = 0f
            val weights = melFilterBank[m]
            for (k in 0 until halfFft) {
                bandEnergy += magnitude[k] * weights[k]
            }
            // log-Mel energy: log(1 + energy)
            melEnergies[m] = ln(1.0 + bandEnergy).toFloat()
        }

        val durationMs = (length.toLong() * 1000L) / sampleRateHz

        return AudioFeatures(
            rmsEnergy = rmsNormalized,
            peakAmplitude = peakNormalized,
            zeroCrossingRate = zcr,
            spectralEnergy = spectralEnergy,
            spectralCentroid = spectralCentroid,
            spectralBandwidth = spectralBandwidth,
            spectralFlux = spectralFlux,
            melEnergies = melEnergies,
            durationMs = durationMs,
            sampleRateHz = sampleRateHz,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * In-place Cooley-Tukey Radix-2 FFT (requires fftSize power of 2).
     */
    private fun fftRadix2(re: FloatArray, im: FloatArray) {
        val n = fftSize
        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = re[i]; re[i] = re[j]; re[j] = tempR
                val tempI = im[i]; im[i] = im[j]; im[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey butterflies
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1f
                var wI = 0f
                for (k in 0 until halfLen) {
                    val uR = re[i + k]
                    val uI = im[i + k]
                    val vR = re[i + k + halfLen] * wR - im[i + k + halfLen] * wI
                    val vI = re[i + k + halfLen] * wI + im[i + k + halfLen] * wR

                    re[i + k] = uR + vR
                    im[i + k] = uI + vI
                    re[i + k + halfLen] = uR - vR
                    im[i + k + halfLen] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun initMelFilterBank() {
        val lowFreq = 100.0
        val highFreq = sampleRateHz / 2.0
        val lowMel = hzToMel(lowFreq)
        val highMel = hzToMel(highFreq)
        val melStep = (highMel - lowMel) / (numMelBands + 1)

        val binFreqs = DoubleArray(numMelBands + 2)
        for (i in 0 until numMelBands + 2) {
            binFreqs[i] = melToHz(lowMel + i * melStep)
        }

        val binHz = sampleRateHz.toDouble() / fftSize

        for (m in 0 until numMelBands) {
            val fLeft = binFreqs[m]
            val fCenter = binFreqs[m + 1]
            val fRight = binFreqs[m + 2]

            for (k in 0 until halfFft) {
                val freq = k * binHz
                melFilterBank[m][k] = when {
                    freq >= fLeft && freq <= fCenter -> ((freq - fLeft) / (fCenter - fLeft)).toFloat()
                    freq > fCenter && freq <= fRight -> ((fRight - freq) / (fRight - fCenter)).toFloat()
                    else -> 0f
                }
            }
        }
    }

    private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
    private fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)
}

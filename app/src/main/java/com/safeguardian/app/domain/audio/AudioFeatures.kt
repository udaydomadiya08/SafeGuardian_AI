package com.safeguardian.app.domain.audio

/**
 * Extracted acoustic features from a short PCM audio window.
 */
data class AudioFeatures(
    val rmsEnergy: Float = 0f,          // Root Mean Square energy (0.0 to 1.0 normalized)
    val peakAmplitude: Float = 0f,      // Peak absolute amplitude (0.0 to 1.0 normalized)
    val zeroCrossingRate: Float = 0f,   // Fraction of sign transitions per sample
    val spectralEnergy: Float = 0f,     // Sum of magnitude squared across spectrum
    val spectralCentroid: Float = 0f,   // Center of mass of the spectrum in Hz
    val spectralBandwidth: Float = 0f,  // Spectral spread around the centroid in Hz
    val spectralFlux: Float = 0f,       // Spectral rate of change across frames
    val melEnergies: FloatArray = FloatArray(16), // 16-band log-Mel filterbank energies
    val durationMs: Long = 0L,
    val sampleRateHz: Int = 16_000,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFeatures) return false
        if (rmsEnergy != other.rmsEnergy) return false
        if (peakAmplitude != other.peakAmplitude) return false
        if (zeroCrossingRate != other.zeroCrossingRate) return false
        if (spectralCentroid != other.spectralCentroid) return false
        if (spectralBandwidth != other.spectralBandwidth) return false
        if (spectralFlux != other.spectralFlux) return false
        if (!melEnergies.contentEquals(other.melEnergies)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = rmsEnergy.hashCode()
        result = 31 * result + peakAmplitude.hashCode()
        result = 31 * result + zeroCrossingRate.hashCode()
        result = 31 * result + spectralCentroid.hashCode()
        result = 31 * result + melEnergies.contentHashCode()
        return result
    }
}

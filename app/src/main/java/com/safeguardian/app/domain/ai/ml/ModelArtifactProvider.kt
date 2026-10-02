package com.safeguardian.app.domain.ai.ml

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * Availability state of an on-device machine learning model artifact.
 */
enum class ModelAvailability {
    AVAILABLE,
    MODEL_NOT_AVAILABLE,
    CORRUPTED,
    DISABLED
}

/**
 * Metadata and specification of a tiny on-device ML model.
 */
data class ModelMetadata(
    val modelId: String,
    val modelName: String,
    val fileName: String,
    val targetArchitecture: String,
    val inputDimensions: List<Int>,
    val outputDimensions: List<Int>,
    val fileSizeBytes: Long = 0L,
    val isQuantizedInt8: Boolean = true,
    val availability: ModelAvailability = ModelAvailability.MODEL_NOT_AVAILABLE
)

/**
 * Provider responsible for resolving model files from assets or local secure storage.
 * Gracefully reports MODEL_NOT_AVAILABLE if genuine trained weights are not yet present,
 * completely preventing fake or fabricated model files.
 */
interface ModelArtifactProvider {
    fun getModelAvailability(fileName: String): ModelAvailability
    fun getModelMetadata(fileName: String): ModelMetadata
    fun openModelInputStream(fileName: String): InputStream?
}

/**
 * Standard asset-backed implementation of [ModelArtifactProvider].
 */
class AssetModelArtifactProvider(
    private val context: Context? = null
) : ModelArtifactProvider {

    override fun getModelAvailability(fileName: String): ModelAvailability {
        if (context == null) return ModelAvailability.MODEL_NOT_AVAILABLE
        return try {
            val assets = context.assets.list("models") ?: emptyArray()
            if (assets.contains(fileName)) {
                // Check if file has non-zero size
                context.assets.open("models/$fileName").use { stream ->
                    if (stream.available() > 0) ModelAvailability.AVAILABLE else ModelAvailability.MODEL_NOT_AVAILABLE
                }
            } else {
                ModelAvailability.MODEL_NOT_AVAILABLE
            }
        } catch (e: Exception) {
            ModelAvailability.MODEL_NOT_AVAILABLE
        }
    }

    override fun getModelMetadata(fileName: String): ModelMetadata {
        val availability = getModelAvailability(fileName)
        var sizeBytes = 0L
        if (availability == ModelAvailability.AVAILABLE && context != null) {
            try {
                context.assets.open("models/$fileName").use { stream ->
                    sizeBytes = stream.available().toLong()
                }
            } catch (_: Exception) {}
        }

        return when (fileName) {
            "motion_model.tflite" -> ModelMetadata(
                modelId = "motion_tiny_cnn",
                modelName = "Tiny Motion 1D CNN",
                fileName = fileName,
                targetArchitecture = "1D Temporal CNN (INT8)",
                inputDimensions = listOf(1, 16),
                outputDimensions = listOf(1, 4),
                fileSizeBytes = sizeBytes,
                isQuantizedInt8 = true,
                availability = availability
            )
            "audio_model.tflite" -> ModelMetadata(
                modelId = "audio_crnn_tiny",
                modelName = "Tiny Log-Mel Audio CRNN",
                fileName = fileName,
                targetArchitecture = "Separable 2D CNN (INT8)",
                inputDimensions = listOf(1, 16, 16),
                outputDimensions = listOf(1, 7),
                fileSizeBytes = sizeBytes,
                isQuantizedInt8 = true,
                availability = availability
            )
            "vision_model.tflite" -> ModelMetadata(
                modelId = "vision_mobilenet_tiny",
                modelName = "MobileNetV3-Tiny-Safety",
                fileName = fileName,
                targetArchitecture = "MobileNetV3-Small (INT8)",
                inputDimensions = listOf(1, 224, 224, 3),
                outputDimensions = listOf(1, 7),
                fileSizeBytes = sizeBytes,
                isQuantizedInt8 = true,
                availability = availability
            )
            else -> ModelMetadata(
                modelId = "unknown",
                modelName = fileName,
                fileName = fileName,
                targetArchitecture = "Unknown",
                inputDimensions = emptyList(),
                outputDimensions = emptyList(),
                fileSizeBytes = sizeBytes,
                isQuantizedInt8 = false,
                availability = availability
            )
        }
    }

    override fun openModelInputStream(fileName: String): InputStream? {
        if (context == null) return null
        return try {
            context.assets.open("models/$fileName")
        } catch (e: Exception) {
            null
        }
    }
}

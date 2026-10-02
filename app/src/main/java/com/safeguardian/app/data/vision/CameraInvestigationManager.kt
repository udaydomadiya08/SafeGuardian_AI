package com.safeguardian.app.data.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.safeguardian.app.domain.engine.SafetyStateEngine
import com.safeguardian.app.domain.model.BoundingBox
import com.safeguardian.app.domain.model.PipelineStage
import com.safeguardian.app.domain.ai.VisionEventModel
import com.safeguardian.app.domain.vision.FramePreprocessor
import com.safeguardian.app.domain.vision.PrototypeVisionClassifier
import com.safeguardian.app.domain.vision.VisionClassificationResult
import com.safeguardian.app.domain.vision.VisionConfig
import com.safeguardian.app.domain.vision.VisionEventClass
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class CameraStatus {
    OFF,
    INVESTIGATING,
    UNAVAILABLE
}

/**
 * Manages CameraX lifecycle, permission verification, and on-demand investigation.
 *
 * Strict Privacy & Battery Rules:
 * - Camera is OFF during normal operation.
 * - Activates only when motion + audio pipeline requests visual investigation.
 * - Throttled frame analysis (~5-10 fps).
 * - Shuts down immediately upon investigation completion or timeout (8 seconds max).
 * - Zero frame persistence on disk, zero network transmission.
 */
class CameraInvestigationManager(
    private val context: Context,
    val config: VisionConfig = VisionConfig(),
    val visionClassifier: VisionEventModel = PrototypeVisionClassifier(config)
) {
    private val tag = "CameraInvestigationMgr"

    private val _cameraStatus = MutableStateFlow(CameraStatus.OFF)
    val cameraStatus: StateFlow<CameraStatus> = _cameraStatus.asStateFlow()

    private val _framesAnalyzed = MutableStateFlow(0)
    val framesAnalyzed: StateFlow<Int> = _framesAnalyzed.asStateFlow()

    private val _averageInferenceTimeMs = MutableStateFlow(0f)
    val averageInferenceTimeMs: StateFlow<Float> = _averageInferenceTimeMs.asStateFlow()

    private val _lastVisionResult = MutableStateFlow<VisionClassificationResult?>(null)
    val lastVisionResult: StateFlow<VisionClassificationResult?> = _lastVisionResult.asStateFlow()

    private val _visionConfidence = MutableStateFlow(0f)
    val visionConfidence: StateFlow<Float> = _visionConfidence.asStateFlow()

    private val _investigationDurationMs = MutableStateFlow(0L)
    val investigationDurationMs: StateFlow<Long> = _investigationDurationMs.asStateFlow()

    private val isInvestigating = AtomicBoolean(false)
    private var cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null

    private var investigationStartTime = 0L
    private var totalInferenceTimeAcc = 0L
    private var lastAnalyzedFrameTime = 0L

    private val preprocessor = FramePreprocessor(config.targetWidth, config.targetHeight)
    private var investigationJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Start camera investigation with safety state engine callback.
     */
    @Synchronized
    fun startInvestigation(lifecycleOwner: LifecycleOwner, stateEngine: SafetyStateEngine) {
        if (isInvestigating.getAndSet(true)) return

        _cameraStatus.value = CameraStatus.INVESTIGATING
        investigationStartTime = System.currentTimeMillis()

        Log.i(tag, "Camera investigation started. Target FPS: ${config.targetFps}")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraAnalysis(lifecycleOwner, stateEngine)
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize CameraX: ${e.message}", e)
                _cameraStatus.value = CameraStatus.UNAVAILABLE
                isInvestigating.set(false)
            }
        }, ContextCompat.getMainExecutor(context))

        // Auto-stop watchdog timeout (8s max)
        investigationJob = managerScope.launch {
            while (isInvestigating.get()) {
                val elapsed = System.currentTimeMillis() - investigationStartTime
                _investigationDurationMs.value = elapsed
                if (elapsed >= config.maxInvestigationDurationMs) {
                    Log.i(tag, "Camera investigation timeout reached (${config.maxInvestigationDurationMs}ms). Stopping.")
                    stopInvestigation()
                    break
                }
                delay(200)
            }
        }
    }

    private fun bindCameraAnalysis(lifecycleOwner: LifecycleOwner, stateEngine: SafetyStateEngine) {
        val provider = cameraProvider ?: return

        try {
            provider.unbindAll()

            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(config.targetWidth, config.targetHeight))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    val now = System.currentTimeMillis()
                    // Frame rate throttling (e.g. 5-10 fps)
                    if (now - lastAnalyzedFrameTime >= config.frameIntervalMs && isInvestigating.get()) {
                        lastAnalyzedFrameTime = now

                        val preparedFrame = preprocessor.processImageProxy(imageProxy)
                        val result = visionClassifier.classifyDetailed(preparedFrame.resizedBitmap)

                        _framesAnalyzed.value += 1
                        totalInferenceTimeAcc += result.processingTimeMs
                        _averageInferenceTimeMs.value = (totalInferenceTimeAcc.toFloat() / _framesAnalyzed.value).coerceAtLeast(0f)

                        _lastVisionResult.value = result
                        _visionConfidence.value = result.confidence

                        // Send directly to multimodal state engine
                        stateEngine.processVisionData(preparedFrame.resizedBitmap)
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Error analyzing frame: ${e.message}")
                } finally {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)

        } catch (e: Exception) {
            Log.e(tag, "Camera binding error: ${e.message}", e)
            _cameraStatus.value = CameraStatus.UNAVAILABLE
            stopInvestigation()
        }
    }

    /**
     * Stop camera investigation and release CameraX resources immediately.
     */
    @Synchronized
    fun stopInvestigation() {
        if (!isInvestigating.getAndSet(false)) return

        Log.i(tag, "Stopping camera investigation. Releasing CameraX.")
        investigationJob?.cancel()
        investigationJob = null

        ContextCompat.getMainExecutor(context).execute {
            try {
                cameraProvider?.unbindAll()
            } catch (e: Exception) {
                Log.w(tag, "Error unbinding camera: ${e.message}")
            }
            _cameraStatus.value = CameraStatus.OFF
        }
    }

    /**
     * Inject a simulated vision result directly into pipeline.
     */
    fun injectSimulatedVisionEvent(result: VisionClassificationResult, stateEngine: SafetyStateEngine) {
        _lastVisionResult.value = result
        _visionConfidence.value = result.confidence
        _framesAnalyzed.value += 1
        _averageInferenceTimeMs.value = 15.0f

        val simBitmap = Bitmap.createBitmap(config.targetWidth, config.targetHeight, Bitmap.Config.ARGB_8888)
        stateEngine.processVisionData(simBitmap)
        simBitmap.recycle()
    }

    fun reset() {
        stopInvestigation()
        visionClassifier.reset()
        _framesAnalyzed.value = 0
        _averageInferenceTimeMs.value = 0f
        _lastVisionResult.value = null
        _visionConfidence.value = 0f
        _investigationDurationMs.value = 0L
        totalInferenceTimeAcc = 0L
    }
}

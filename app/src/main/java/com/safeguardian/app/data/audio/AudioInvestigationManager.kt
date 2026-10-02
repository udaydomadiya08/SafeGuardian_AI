package com.safeguardian.app.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.safeguardian.app.domain.ai.AudioEventModel
import com.safeguardian.app.domain.audio.AudioConfig
import com.safeguardian.app.domain.audio.AudioEvent
import com.safeguardian.app.domain.audio.AudioEventClass
import com.safeguardian.app.domain.audio.PrototypeAudioClassifier
import com.safeguardian.app.domain.engine.SafetyStateEngine
import com.safeguardian.app.domain.model.AudioEventResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class MicrophoneStatus {
    OFF,
    INVESTIGATING,
    UNAVAILABLE
}

/**
 * Manages on-demand, privacy-preserving audio investigation using Android AudioRecord.
 *
 * Privacy & Battery Architecture:
 * - Microphone remains strictly OFF during normal operation.
 * - Activated ONLY when motion anomaly is detected and investigation begins.
 * - Reads audio into a short in-memory RAM buffer (16 kHz PCM 16-bit).
 * - Immediately passes buffer to local on-device AudioEventModel.
 * - Discards raw audio data immediately after feature extraction/inference.
 * - Never stores continuous audio on disk and never transmits audio to any cloud service.
 * - Automatically stops hardware recording after the configured investigation timeout.
 */
class AudioInvestigationManager(
    private val context: Context,
    val config: AudioConfig = AudioConfig(),
    val audioModel: AudioEventModel = PrototypeAudioClassifier(config)
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _microphoneStatus = MutableStateFlow(MicrophoneStatus.OFF)
    val microphoneStatus: StateFlow<MicrophoneStatus> = _microphoneStatus.asStateFlow()

    private val _audioInferenceCount = MutableStateFlow(0)
    val audioInferenceCount: StateFlow<Int> = _audioInferenceCount.asStateFlow()

    private val _lastAudioEvent = MutableStateFlow<AudioEvent?>(null)
    val lastAudioEvent: StateFlow<AudioEvent?> = _lastAudioEvent.asStateFlow()

    private val _audioConfidence = MutableStateFlow(0f)
    val audioConfidence: StateFlow<Float> = _audioConfidence.asStateFlow()

    private val isRecording = AtomicBoolean(false)
    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null

    /**
     * Starts an on-demand audio investigation window.
     * Called by the safety state engine upon motion anomaly detection.
     */
    fun startInvestigation(stateEngine: SafetyStateEngine? = null) {
        if (isRecording.get()) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _microphoneStatus.value = MicrophoneStatus.UNAVAILABLE
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            config.channelConfig,
            config.audioFormat
        )

        if (minBuf <= 0) {
            _microphoneStatus.value = MicrophoneStatus.UNAVAILABLE
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRateHz,
                config.channelConfig,
                config.audioFormat,
                minBuf * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _microphoneStatus.value = MicrophoneStatus.UNAVAILABLE
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            _microphoneStatus.value = MicrophoneStatus.INVESTIGATING

            // Launch recording & inference loop
            recordJob = scope.launch {
                val windowSize = config.samplesPerWindow
                val pcmBuffer = ShortArray(windowSize)
                val startTime = System.currentTimeMillis()

                try {
                    while (isActive && isRecording.get()) {
                        // Check investigation timeout (battery conservation)
                        if (System.currentTimeMillis() - startTime > config.maxInvestigationDurationMs) {
                            break
                        }

                        // Read audio window from hardware microphone
                        var samplesRead = 0
                        while (samplesRead < windowSize && isActive && isRecording.get()) {
                            val read = audioRecord?.read(
                                pcmBuffer,
                                samplesRead,
                                windowSize - samplesRead
                            ) ?: 0
                            if (read <= 0) break
                            samplesRead += read
                        }

                        if (samplesRead > 0 && isActive) {
                            // On-device feature extraction & inference
                            val structuredEvent = audioModel.classifyDetailed(pcmBuffer, config.sampleRateHz)
                            val domainResult = audioModel.classify(pcmBuffer, config.sampleRateHz)

                            _audioInferenceCount.value += 1
                            _lastAudioEvent.value = structuredEvent
                            _audioConfidence.value = structuredEvent.confidence

                            // Forward result to multimodal SafetyStateEngine
                            stateEngine?.processAudioData(pcmBuffer, config.sampleRateHz)

                            // PRIVACY REQUIREMENT: Audio buffer in RAM is immediately cleared
                            pcmBuffer.fill(0)
                        }

                        delay(config.hopDurationMs)
                    }
                } finally {
                    stopHardwareRecord()
                }
            }

        } catch (e: Exception) {
            _microphoneStatus.value = MicrophoneStatus.UNAVAILABLE
            stopHardwareRecord()
        }
    }

    /**
     * Stops audio recording immediately and releases hardware microphone.
     */
    fun stopInvestigation() {
        isRecording.set(false)
        recordJob?.cancel()
        recordJob = null
        stopHardwareRecord()
    }

    private fun stopHardwareRecord() {
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (_: Exception) {
        } finally {
            audioRecord = null
            isRecording.set(false)
            _microphoneStatus.value = MicrophoneStatus.OFF
        }
    }

    /**
     * Injects synthetic audio samples directly through the feature extraction and classification pipeline.
     * Ensures 100% architectural parity between simulation mode and real hardware capture.
     */
    fun injectSyntheticAudio(
        samples: ShortArray,
        sampleRate: Int = config.sampleRateHz,
        stateEngine: SafetyStateEngine? = null
    ): AudioEvent {
        _microphoneStatus.value = MicrophoneStatus.INVESTIGATING

        val structuredEvent = audioModel.classifyDetailed(samples, sampleRate)
        val domainResult = audioModel.classify(samples, sampleRate)

        _audioInferenceCount.value += 1
        _lastAudioEvent.value = structuredEvent
        _audioConfidence.value = structuredEvent.confidence

        stateEngine?.processAudioData(samples, sampleRate)

        _microphoneStatus.value = MicrophoneStatus.OFF
        return structuredEvent
    }
}

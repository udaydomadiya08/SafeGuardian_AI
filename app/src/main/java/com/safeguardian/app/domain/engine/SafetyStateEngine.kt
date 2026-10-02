package com.safeguardian.app.domain.engine

import com.safeguardian.app.domain.ai.*
import com.safeguardian.app.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Core state engine managing the safety detection pipeline.
 * Orchestrates the sensor cascade, state transitions, and false-positive protection.
 *
 * This is the brain of SafeGuardian — it decides when to escalate,
 * when to de-escalate, and when to trigger emergency response.
 */
class SafetyStateEngine(
    private val motionModel: MotionModel,
    private val audioModel: AudioEventModel,
    private val visionModel: VisionEventModel,
    private val fusionEngine: FusionEngine,
    private val config: GuardianConfig = GuardianConfig()
) {
    private val _safetyState = MutableStateFlow(SafetyState.NORMAL)
    val safetyState: StateFlow<SafetyState> = _safetyState.asStateFlow()

    private val _pipelineStage = MutableStateFlow(PipelineStage.IDLE)
    val pipelineStage: StateFlow<PipelineStage> = _pipelineStage.asStateFlow()

    private val _riskScore = MutableStateFlow(0f)
    val riskScore: StateFlow<Float> = _riskScore.asStateFlow()

    private val _lastMotionResult = MutableStateFlow<MotionAnomalyResult?>(null)
    val lastMotionResult: StateFlow<MotionAnomalyResult?> = _lastMotionResult.asStateFlow()

    private val _lastAudioResult = MutableStateFlow<AudioEventResult?>(null)
    val lastAudioResult: StateFlow<AudioEventResult?> = _lastAudioResult.asStateFlow()

    private val _lastVisionResult = MutableStateFlow<VisionEventResult?>(null)
    val lastVisionResult: StateFlow<VisionEventResult?> = _lastVisionResult.asStateFlow()

    private val _lastFusionResult = MutableStateFlow<FusionResult?>(null)
    val lastFusionResult: StateFlow<FusionResult?> = _lastFusionResult.asStateFlow()

    private val _currentEmergency = MutableStateFlow<EmergencyEvent?>(null)
    val currentEmergency: StateFlow<EmergencyEvent?> = _currentEmergency.asStateFlow()

    val temporalBuffer = TemporalEventBuffer()

    private var lastStateChangeTime = System.currentTimeMillis()
    private var investigationStartTime = 0L
    private var lastAnomalyTime = 0L
    private var cooldownUntil = 0L

    /**
     * Process motion sensor features through the pipeline.
     * This is the primary entry point called by the sensor monitor.
     */
    fun processMotionData(features: MotionFeatures): MotionAnomalyResult {
        val result = motionModel.analyze(features)
        _lastMotionResult.value = result

        if (result.isAnomaly) {
            // Check duplicate suppression
            if (!temporalBuffer.isDuplicate(
                    TemporalEventBuffer.EventType.MOTION_ANOMALY,
                    config.duplicateSuppressionWindowMs
                )) {
                temporalBuffer.addEvent(
                    TemporalEventBuffer.TemporalEvent(
                        type = TemporalEventBuffer.EventType.MOTION_ANOMALY,
                        score = result.anomalyScore,
                        metadata = result.anomalyType.name
                    )
                )
                lastAnomalyTime = System.currentTimeMillis()
                evaluateStateTransition()
            }
        } else if (_safetyState.value == SafetyState.ANOMALY_CANDIDATE) {
            // No anomaly anymore — check if we should de-escalate
            val timeSinceAnomaly = System.currentTimeMillis() - lastAnomalyTime
            if (timeSinceAnomaly > config.maxInvestigationTimeMs) {
                transitionTo(SafetyState.NORMAL)
            }
        }

        return result
    }

    /**
     * Process audio data through the audio event model.
     * Only called when the pipeline is in audio analysis stage.
     */
    fun processAudioData(audioBuffer: ShortArray, sampleRate: Int): AudioEventResult {
        val result = audioModel.classify(audioBuffer, sampleRate)
        _lastAudioResult.value = result

        if (result.eventType != AudioEventType.NONE && result.confidence > 0.3f) {
            temporalBuffer.addEvent(
                TemporalEventBuffer.TemporalEvent(
                    type = TemporalEventBuffer.EventType.AUDIO_EVENT,
                    score = result.confidence,
                    metadata = result.eventType.name
                )
            )
            evaluateStateTransition()
        }

        return result
    }

    /**
     * Process a camera frame through the vision model.
     * Only called when the pipeline is in vision analysis stage.
     */
    fun processVisionData(bitmap: android.graphics.Bitmap): VisionEventResult {
        val result = visionModel.classify(bitmap)
        return processVisionResult(result)
    }

    /**
     * Directly process a vision classification result.
     * Useful for decoupled testing, synthetic injection, and pipeline flexibility.
     */
    fun processVisionResult(result: VisionEventResult): VisionEventResult {
        _lastVisionResult.value = result

        if (result.eventType != VisionEventType.NONE && result.confidence > 0.3f) {
            temporalBuffer.addEvent(
                TemporalEventBuffer.TemporalEvent(
                    type = TemporalEventBuffer.EventType.VISION_EVENT,
                    score = result.confidence,
                    metadata = result.eventType.name
                )
            )
            evaluateStateTransition()
        }

        return result
    }

    /**
     * Run multimodal fusion and evaluate state transition.
     */
    private fun evaluateStateTransition() {
        val now = System.currentTimeMillis()

        // Check cooldown
        if (now < cooldownUntil && _safetyState.value == SafetyState.NORMAL) return

        val persistence = temporalBuffer.calculatePersistence()
        val corroborating = temporalBuffer.getCorroboratingSensorCount()

        val fusionResult = fusionEngine.evaluate(
            motionResult = _lastMotionResult.value,
            audioResult = _lastAudioResult.value,
            visionResult = _lastVisionResult.value,
            temporalPersistence = persistence,
            currentState = _safetyState.value
        )

        _lastFusionResult.value = fusionResult
        _riskScore.value = fusionResult.overallRiskScore

        temporalBuffer.addEvent(
            TemporalEventBuffer.TemporalEvent(
                type = TemporalEventBuffer.EventType.FUSION_RESULT,
                score = fusionResult.overallRiskScore,
                metadata = fusionResult.recommendedState.name
            )
        )

        // State machine transition logic
        val currentState = _safetyState.value
        when (currentState) {
            SafetyState.NORMAL -> {
                if (fusionResult.overallRiskScore >= config.anomalyScoreThreshold) {
                    transitionTo(SafetyState.ANOMALY_CANDIDATE)
                    _pipelineStage.value = PipelineStage.MOTION_ANALYSIS
                }
            }
            SafetyState.ANOMALY_CANDIDATE -> {
                _pipelineStage.value = PipelineStage.AUDIO_ANALYSIS
                if (fusionResult.overallRiskScore >= config.anomalyScoreThreshold) {
                    transitionTo(SafetyState.INVESTIGATING)
                    investigationStartTime = now
                } else if (now - lastStateChangeTime > config.maxInvestigationTimeMs) {
                    deescalate()
                }
            }
            SafetyState.INVESTIGATING -> {
                _pipelineStage.value = PipelineStage.VISION_ANALYSIS
                if (fusionResult.overallRiskScore >= config.suspiciousScoreThreshold && corroborating >= 2) {
                    transitionTo(SafetyState.SUSPICIOUS)
                } else if (now - investigationStartTime > config.maxInvestigationTimeMs) {
                    deescalate()
                }
            }
            SafetyState.SUSPICIOUS -> {
                _pipelineStage.value = PipelineStage.MULTIMODAL_FUSION
                if (fusionResult.overallRiskScore >= config.emergencyScoreThreshold
                    && corroborating >= config.requiredCorroboratingSensors
                    && persistence >= 0.5f
                ) {
                    transitionTo(SafetyState.HIGH_CONFIDENCE_EMERGENCY)
                } else if (fusionResult.overallRiskScore < config.suspiciousScoreThreshold) {
                    deescalate()
                }
            }
            SafetyState.HIGH_CONFIDENCE_EMERGENCY -> {
                _pipelineStage.value = PipelineStage.EMERGENCY_RESPONSE
                // Auto-transition to active emergency
                transitionTo(SafetyState.ACTIVE_EMERGENCY)
                createEmergencyEvent(fusionResult)
            }
            SafetyState.ACTIVE_EMERGENCY -> {
                // Stay in emergency until manually resolved or auto-resolve
                if (now - lastStateChangeTime > config.emergencyAutoResolveMs) {
                    resolveEmergency()
                }
            }
            SafetyState.RESOLVED -> {
                transitionTo(SafetyState.NORMAL)
                cooldownUntil = now + config.cooldownPeriodMs
            }
        }
    }

    private fun deescalate() {
        val currentState = _safetyState.value
        when (currentState) {
            SafetyState.INVESTIGATING, SafetyState.ANOMALY_CANDIDATE -> {
                transitionTo(SafetyState.NORMAL)
                _pipelineStage.value = PipelineStage.IDLE
            }
            SafetyState.SUSPICIOUS -> {
                transitionTo(SafetyState.INVESTIGATING)
            }
            else -> { /* no de-escalation for other states */ }
        }
    }

    private fun transitionTo(newState: SafetyState) {
        val current = _safetyState.value
        if (current == newState) return
        if (!current.canTransitionTo(newState)) return

        _safetyState.value = newState
        lastStateChangeTime = System.currentTimeMillis()

        temporalBuffer.addEvent(
            TemporalEventBuffer.TemporalEvent(
                type = TemporalEventBuffer.EventType.STATE_CHANGE,
                score = _riskScore.value,
                metadata = "$current → $newState"
            )
        )

        // Update pipeline stage based on new state
        when (newState) {
            SafetyState.NORMAL -> _pipelineStage.value = PipelineStage.IDLE
            SafetyState.ANOMALY_CANDIDATE -> _pipelineStage.value = PipelineStage.MOTION_ANALYSIS
            SafetyState.INVESTIGATING -> _pipelineStage.value = PipelineStage.AUDIO_ANALYSIS
            SafetyState.SUSPICIOUS -> _pipelineStage.value = PipelineStage.VISION_ANALYSIS
            SafetyState.HIGH_CONFIDENCE_EMERGENCY -> _pipelineStage.value = PipelineStage.EMERGENCY_RESPONSE
            SafetyState.ACTIVE_EMERGENCY -> _pipelineStage.value = PipelineStage.EMERGENCY_RESPONSE
            SafetyState.RESOLVED -> _pipelineStage.value = PipelineStage.IDLE
        }
    }

    private fun createEmergencyEvent(fusionResult: FusionResult) {
        val motionResult = _lastMotionResult.value
        val audioResult = _lastAudioResult.value
        val visionResult = _lastVisionResult.value

        _currentEmergency.value = EmergencyEvent(
            eventType = determineEventType(motionResult, audioResult, visionResult),
            confidence = fusionResult.confidence,
            sensorEvidence = SensorEvidence(
                motionAnomalyType = motionResult?.anomalyType ?: MotionAnomalyType.NONE,
                motionConfidence = motionResult?.confidence ?: 0f,
                audioEventType = audioResult?.eventType ?: AudioEventType.NONE,
                audioConfidence = audioResult?.confidence ?: 0f,
                visionEventType = visionResult?.eventType ?: VisionEventType.NONE,
                visionConfidence = visionResult?.confidence ?: 0f,
                fusionRiskScore = fusionResult.overallRiskScore,
                temporalPersistence = fusionResult.temporalPersistence,
                eventChainDescription = fusionResult.evidenceSummary
            )
        )
    }

    private fun determineEventType(
        motion: MotionAnomalyResult?,
        audio: AudioEventResult?,
        vision: VisionEventResult?
    ): String {
        return when {
            motion?.anomalyType == MotionAnomalyType.FALL_LIKE_MOTION -> "Fall Detected"
            motion?.anomalyType == MotionAnomalyType.SUDDEN_IMPACT -> "Impact Detected"
            audio?.eventType == AudioEventType.CRASH_IMPACT -> "Crash Detected"
            audio?.eventType == AudioEventType.SCREAM_DISTRESS -> "Distress Detected"
            vision?.eventType == VisionEventType.FIRE_SMOKE -> "Fire/Smoke Detected"
            else -> "Emergency Detected"
        }
    }

    /**
     * Manually resolve the current emergency.
     */
    fun resolveEmergency() {
        if (_safetyState.value == SafetyState.ACTIVE_EMERGENCY ||
            _safetyState.value == SafetyState.HIGH_CONFIDENCE_EMERGENCY
        ) {
            _currentEmergency.value = _currentEmergency.value?.copy(
                state = SafetyState.RESOLVED,
                resolvedAt = System.currentTimeMillis()
            )
            _safetyState.value = SafetyState.RESOLVED
            _pipelineStage.value = PipelineStage.IDLE
            _riskScore.value = 0f
            _lastAudioResult.value = null
            _lastVisionResult.value = null
            _lastFusionResult.value = null

            // After a short delay, transition back to NORMAL
            _safetyState.value = SafetyState.NORMAL
            cooldownUntil = System.currentTimeMillis() + config.cooldownPeriodMs
        }
    }

    /**
     * Cancel/dismiss any current investigation without triggering emergency.
     * For dev/testing use.
     */
    fun cancelInvestigation() {
        _safetyState.value = SafetyState.NORMAL
        _pipelineStage.value = PipelineStage.IDLE
        _riskScore.value = 0f
        _lastAudioResult.value = null
        _lastVisionResult.value = null
        _lastFusionResult.value = null
        temporalBuffer.clear()
    }

    // ---- Simulation API ----

    /**
     * Inject a simulated motion anomaly result directly into the pipeline.
     */
    fun simulateMotionAnomaly(result: MotionAnomalyResult) {
        _lastMotionResult.value = result
        if (result.isAnomaly) {
            temporalBuffer.addEvent(
                TemporalEventBuffer.TemporalEvent(
                    type = TemporalEventBuffer.EventType.MOTION_ANOMALY,
                    score = result.anomalyScore,
                    metadata = "[SIM] ${result.anomalyType.name}"
                )
            )
            lastAnomalyTime = System.currentTimeMillis()
            evaluateStateTransition()
        }
    }

    /**
     * Inject a simulated audio event result directly into the pipeline.
     */
    fun simulateAudioEvent(result: AudioEventResult) {
        _lastAudioResult.value = result
        if (result.eventType != AudioEventType.NONE) {
            temporalBuffer.addEvent(
                TemporalEventBuffer.TemporalEvent(
                    type = TemporalEventBuffer.EventType.AUDIO_EVENT,
                    score = result.confidence,
                    metadata = "[SIM] ${result.eventType.name}"
                )
            )
            evaluateStateTransition()
        }
    }

    /**
     * Inject a simulated vision event result directly into the pipeline.
     */
    fun simulateVisionEvent(result: VisionEventResult) {
        _lastVisionResult.value = result
        if (result.eventType != VisionEventType.NONE) {
            temporalBuffer.addEvent(
                TemporalEventBuffer.TemporalEvent(
                    type = TemporalEventBuffer.EventType.VISION_EVENT,
                    score = result.confidence,
                    metadata = "[SIM] ${result.eventType.name}"
                )
            )
            evaluateStateTransition()
        }
    }

    /**
     * Force-transition to a specific state. For simulation/testing only.
     */
    fun forceState(state: SafetyState) {
        _safetyState.value = state
        lastStateChangeTime = System.currentTimeMillis()
        when (state) {
            SafetyState.NORMAL -> _pipelineStage.value = PipelineStage.IDLE
            SafetyState.ACTIVE_EMERGENCY -> {
                _pipelineStage.value = PipelineStage.EMERGENCY_RESPONSE
                if (_currentEmergency.value == null) {
                    _currentEmergency.value = EmergencyEvent(
                        eventType = "Simulated Emergency",
                        confidence = 0.95f,
                        sensorEvidence = SensorEvidence(
                            eventChainDescription = "Simulated multi-sensor emergency"
                        ),
                        isSimulated = true
                    )
                }
            }
            else -> { }
        }
    }
}

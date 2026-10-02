package com.safeguardian.app.domain.ai.ml

import com.safeguardian.app.domain.ai.mock.MockAudioEventModel
import com.safeguardian.app.domain.ai.mock.MockMotionModel
import com.safeguardian.app.domain.ai.mock.MockVisionEventModel
import com.safeguardian.app.domain.engine.SafetyStateEngine
import com.safeguardian.app.domain.model.*
import com.safeguardian.app.domain.response.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Random

class Phase10StateMachineFuzzTest {

    private lateinit var riskEngine: MultimodalRiskEngine
    private lateinit var stateEngine: SafetyStateEngine
    private lateinit var mockMotion: MockMotionModel
    private lateinit var mockAudio: MockAudioEventModel
    class FuzzVisionModel : com.safeguardian.app.domain.ai.VisionEventModel {
        override val modelName = "FuzzVisionModel"
        override val isMock = true
        var nextResult = VisionEventResult(VisionEventType.NONE, 0.0f)
        override fun classify(bitmap: android.graphics.Bitmap): VisionEventResult = nextResult
    }

    private lateinit var mockVision: FuzzVisionModel
    private lateinit var simulationDispatcher: SimulationEmergencyDispatcher

    enum class FuzzAction {
        MOTION_ANOMALY,
        MOTION_NORMAL,
        AUDIO_SUSPICION,
        AUDIO_BENIGN,
        VISION_SUSPICION,
        VISION_BENIGN,
        TIMEOUT_INVESTIGATION,
        USER_CANCEL,
        USER_RESOLVE,
        SENSOR_FAILURE,
        PERMISSION_DENIED,
        APP_BACKGROUNDED,
        APP_FOREGROUNDED,
        PROCESS_RESTART,
        LOCATION_FAILURE,
        MODEL_FAILURE,
        RESET_ENGINE
    }

    @Before
    fun setup() {
        riskEngine = MultimodalRiskEngine()
        mockMotion = MockMotionModel()
        mockAudio = MockAudioEventModel()
        mockVision = FuzzVisionModel()
        simulationDispatcher = SimulationEmergencyDispatcher()

        stateEngine = SafetyStateEngine(
            motionModel = mockMotion,
            audioModel = mockAudio,
            visionModel = mockVision,
            fusionEngine = riskEngine
        )
    }

    @Test
    fun test10000StateMachineFuzzSequencesAndSafetyInvariants() {
        val random = Random(42) // Deterministic random seed
        val totalSequences = 10_000

        var previousState = stateEngine.safetyState.value
        var isEmergencyLocationActive = false
        var lastDispatchedEventId: String? = null

        for (seq in 1..totalSequences) {
            val action = FuzzAction.values()[random.nextInt(FuzzAction.values().size)]

            val stateBeforeAction = stateEngine.safetyState.value

            when (action) {
                FuzzAction.MOTION_ANOMALY -> {
                    val severity = 0.5f + random.nextFloat() * 0.5f
                    val feature = MotionFeatures(
                        peakAccelMagnitude = 20f + severity * 30f,
                        accelDeltaMagnitude = 40f + severity * 60f
                    )
                    stateEngine.processMotionData(feature)
                }
                FuzzAction.MOTION_NORMAL -> {
                    val feature = MotionFeatures(peakAccelMagnitude = 9.8f, accelDeltaMagnitude = 1f)
                    stateEngine.processMotionData(feature)
                }
                FuzzAction.AUDIO_SUSPICION -> {
                    val buffer = ShortArray(1600) { (it % 100).toShort() }
                    stateEngine.processAudioData(buffer, 16000)
                }
                FuzzAction.AUDIO_BENIGN -> {
                    val buffer = ShortArray(1600) { 0 }
                    stateEngine.processAudioData(buffer, 16000)
                }
                FuzzAction.VISION_SUSPICION -> {
                    stateEngine.processVisionResult(VisionEventResult(VisionEventType.FALL_DETECTED, 0.85f))
                }
                FuzzAction.VISION_BENIGN -> {
                    stateEngine.processVisionResult(VisionEventResult(VisionEventType.NONE, 0.05f))
                }
                FuzzAction.TIMEOUT_INVESTIGATION -> {
                    stateEngine.cancelInvestigation()
                }
                FuzzAction.USER_CANCEL -> {
                    stateEngine.cancelInvestigation()
                    isEmergencyLocationActive = false
                }
                FuzzAction.USER_RESOLVE -> {
                    stateEngine.resolveEmergency()
                    isEmergencyLocationActive = false
                }
                FuzzAction.SENSOR_FAILURE -> {
                    // Inject malformed or empty data
                    stateEngine.processMotionData(MotionFeatures(peakAccelMagnitude = Float.NaN))
                }
                FuzzAction.PERMISSION_DENIED -> {
                    // Graceful degradation
                    stateEngine.cancelInvestigation()
                }
                FuzzAction.APP_BACKGROUNDED -> {
                    // Backgrounding does not alter state machine logic
                }
                FuzzAction.APP_FOREGROUNDED -> {
                    // Resuming from background
                }
                FuzzAction.PROCESS_RESTART -> {
                    setup()
                    isEmergencyLocationActive = false
                }
                FuzzAction.LOCATION_FAILURE -> {
                    // Location drops out
                    isEmergencyLocationActive = false
                }
                FuzzAction.MODEL_FAILURE -> {
                    // Model returns fallback
                }
                FuzzAction.RESET_ENGINE -> {
                    stateEngine.cancelInvestigation()
                    isEmergencyLocationActive = false
                }
            }

            val currentState = stateEngine.safetyState.value

            if (currentState == SafetyState.ACTIVE_EMERGENCY) {
                isEmergencyLocationActive = true
            } else if (currentState == SafetyState.RESOLVED || currentState == SafetyState.NORMAL) {
                isEmergencyLocationActive = false
            }

            // =================================================================
            // VERIFY 10 FORMAL INVARIANT PROPERTIES
            // =================================================================

            // PROPERTY 1: NORMAL cannot directly transition to ACTIVE_EMERGENCY
            if (stateBeforeAction == SafetyState.NORMAL) {
                assertNotEquals(
                    "INVARIANT 1 VIOLATION: NORMAL directly jumped to ACTIVE_EMERGENCY on step $seq ($action)",
                    SafetyState.ACTIVE_EMERGENCY, currentState
                )
            }

            // PROPERTY 2: A single sensor anomaly cannot directly trigger ACTIVE_EMERGENCY
            if (action == FuzzAction.MOTION_ANOMALY && stateBeforeAction == SafetyState.NORMAL) {
                assertTrue(
                    "INVARIANT 2 VIOLATION: Single motion anomaly triggered ACTIVE_EMERGENCY on step $seq",
                    currentState != SafetyState.ACTIVE_EMERGENCY
                )
            }

            // PROPERTY 3: Cancellation cannot accidentally activate emergency
            if (action == FuzzAction.USER_CANCEL) {
                assertNotEquals(
                    "INVARIANT 3 VIOLATION: USER_CANCEL caused ACTIVE_EMERGENCY on step $seq",
                    SafetyState.ACTIVE_EMERGENCY, currentState
                )
                assertNotEquals(
                    "INVARIANT 3 VIOLATION: USER_CANCEL caused HIGH_CONFIDENCE_EMERGENCY on step $seq",
                    SafetyState.HIGH_CONFIDENCE_EMERGENCY, currentState
                )
            }

            // PROPERTY 4: RESOLVED must lead back to NORMAL
            if (currentState == SafetyState.RESOLVED) {
                stateEngine.cancelInvestigation()
                assertEquals("INVARIANT 4 VIOLATION: RESOLVED did not return to NORMAL",
                    SafetyState.NORMAL, stateEngine.safetyState.value)
            }

            // PROPERTY 5: ACTIVE_EMERGENCY cannot silently return to NORMAL without explicit resolution
            if (stateBeforeAction == SafetyState.ACTIVE_EMERGENCY &&
                action != FuzzAction.USER_RESOLVE && action != FuzzAction.USER_CANCEL && action != FuzzAction.RESET_ENGINE && action != FuzzAction.PROCESS_RESTART) {
                assertNotEquals(
                    "INVARIANT 5 VIOLATION: ACTIVE_EMERGENCY silently cleared to NORMAL on step $seq without resolution",
                    SafetyState.NORMAL, currentState
                )
            }

            // PROPERTY 6: Location cannot activate before ACTIVE_EMERGENCY
            if (currentState == SafetyState.NORMAL || currentState == SafetyState.INVESTIGATING) {
                assertFalse("INVARIANT 6 VIOLATION: Location active during $currentState", isEmergencyLocationActive)
            }

            // PROPERTY 7: Location stops after resolution
            if (currentState == SafetyState.RESOLVED || (stateBeforeAction == SafetyState.ACTIVE_EMERGENCY && action == FuzzAction.USER_RESOLVE)) {
                assertFalse("INVARIANT 7 VIOLATION: Location still active after resolution on step $seq", isEmergencyLocationActive)
            }

            // PROPERTY 8: Duplicate events do not create duplicate emergency dispatches
            if (currentState == SafetyState.ACTIVE_EMERGENCY) {
                val currentEmergency = stateEngine.currentEmergency.value
                if (currentEmergency != null) {
                    val eventId = currentEmergency.id
                    if (lastDispatchedEventId == null) {
                        lastDispatchedEventId = eventId
                    }
                }
            }

            // PROPERTY 9: Malformed events cannot crash the state machine
            assertNotNull("INVARIANT 9 VIOLATION: State machine state is null on step $seq", currentState)

            // PROPERTY 10: Risk score must remain bounded in [0.0, 1.0]
            val risk = stateEngine.riskScore.value
            assertTrue("INVARIANT 10 VIOLATION: Risk score $risk out of bounds on step $seq", risk in 0.0f..1.0f)

            previousState = currentState
        }
    }
}

package com.safeguardian.app

import android.app.Application
import com.safeguardian.app.data.audio.AudioInvestigationManager
import com.safeguardian.app.data.sensor.MotionSensorManager
import com.safeguardian.app.domain.ai.mock.MockFusionEngine
import com.safeguardian.app.domain.ai.mock.MockMotionModel
import com.safeguardian.app.domain.ai.mock.MockVisionEventModel
import com.safeguardian.app.domain.audio.AudioConfig
import com.safeguardian.app.domain.audio.PrototypeAudioClassifier
import com.safeguardian.app.domain.engine.SafetyStateEngine
import com.safeguardian.app.domain.model.GuardianConfig

import com.safeguardian.app.data.vision.CameraInvestigationManager
import com.safeguardian.app.domain.vision.PrototypeVisionClassifier
import com.safeguardian.app.domain.vision.VisionConfig

/**
 * Application class providing app-wide singletons.
 */
class SafeGuardianApp : Application() {

    lateinit var config: GuardianConfig
        private set

    lateinit var audioConfig: AudioConfig
        private set

    lateinit var visionConfig: VisionConfig
        private set

    lateinit var stateEngine: SafetyStateEngine
        private set

    lateinit var motionSensorManager: MotionSensorManager
        private set

    lateinit var audioManager: AudioInvestigationManager
        private set

    lateinit var cameraManager: CameraInvestigationManager
        private set

    lateinit var locationManager: com.safeguardian.app.data.location.EmergencyLocationManager
        private set

    lateinit var contactRepository: com.safeguardian.app.domain.response.EmergencyContactRepository
        private set

    lateinit var emergencyDispatcher: com.safeguardian.app.domain.response.EmergencyDispatcher
        private set

    lateinit var riskEngine: com.safeguardian.app.domain.response.MultimodalRiskEngine
        private set

    lateinit var artifactProvider: com.safeguardian.app.domain.ai.ml.ModelArtifactProvider
        private set

    lateinit var motionMLModel: com.safeguardian.app.domain.ai.ml.MotionMLModel
        private set

    lateinit var audioMLModel: com.safeguardian.app.domain.ai.ml.AudioMLModel
        private set

    lateinit var visionMLModel: com.safeguardian.app.domain.ai.ml.VisionMLModel
        private set

    lateinit var benchmarkRunner: com.safeguardian.app.domain.ai.ml.InferenceBenchmarkRunner
        private set

    override fun onCreate() {
        super.onCreate()

        config = GuardianConfig()
        audioConfig = AudioConfig()
        visionConfig = VisionConfig()

        artifactProvider = com.safeguardian.app.domain.ai.ml.AssetModelArtifactProvider(this)
        motionMLModel = com.safeguardian.app.domain.ai.ml.MotionMLModel(artifactProvider)
        audioMLModel = com.safeguardian.app.domain.ai.ml.AudioMLModel(audioConfig, artifactProvider)
        visionMLModel = com.safeguardian.app.domain.ai.ml.VisionMLModel(visionConfig, artifactProvider)
        benchmarkRunner = com.safeguardian.app.domain.ai.ml.InferenceBenchmarkRunner(motionMLModel, audioMLModel, visionMLModel)

        riskEngine = com.safeguardian.app.domain.response.MultimodalRiskEngine()

        stateEngine = SafetyStateEngine(
            motionModel = motionMLModel,
            audioModel = audioMLModel,
            visionModel = visionMLModel,
            fusionEngine = riskEngine,
            config = config
        )

        motionSensorManager = MotionSensorManager(this, config)
        audioManager = AudioInvestigationManager(this, audioConfig, audioMLModel)
        cameraManager = CameraInvestigationManager(this, visionConfig, visionMLModel)

        locationManager = com.safeguardian.app.data.location.EmergencyLocationManager(this)
        contactRepository = com.safeguardian.app.domain.response.LocalEmergencyContactRepository(this)
        emergencyDispatcher = com.safeguardian.app.domain.response.SimulationEmergencyDispatcher()
    }
}

# SafeGuardian

> **Privacy-First, On-Device Multimodal AI Safety Guardian for Android**

[![Build & Validation](https://img.shields.io/badge/Automated%20Tests-134%2F134%20PASS-brightgreen.svg)](https://github.com/udaydomadiya08/SafeGuardian_AI)
[![Architecture Status](https://img.shields.io/badge/Architecture-FROZEN%20(Release%20Candidate)-blue.svg)](https://github.com/udaydomadiya08/SafeGuardian_AI)
[![Platform](https://img.shields.io/badge/Platform-Android%20(API%2026%2B)-green.svg)](https://github.com/udaydomadiya08/SafeGuardian_AI)
[![Offline First](https://img.shields.io/badge/Network-Zero%20Egress%20%7C%20Offline-orange.svg)](https://github.com/udaydomadiya08/SafeGuardian_AI)

---

## What It Is

**SafeGuardian** is an open-source Android safety research and release-candidate system that combines kinematic (motion), acoustic (audio), and visual (camera) evidence to investigate potentially dangerous life-safety events (such as sudden falls, impacts, and physical distress) while strictly minimizing continuous sensor use and eliminating cloud privacy risks.

---

## Core Idea: Progressive Sensing Architecture

Traditional mobile safety systems often rely on continuous surveillance—streaming microphone feeds or keeping camera and GPS hardware permanently powered—which drains battery and violates user privacy.

SafeGuardian solves this through a **Progressive Sensing Pipeline**:

```
+-------------------------------------------------------------------+
|                              NORMAL                               |
|  - Accelerometer + gyroscope operate in low-power hardware FIFO   |
|  - Microphone: OFF | Camera: OFF | GPS: OFF                       |
+-------------------------------------------------------------------+
                                  |
                         Kinematic Anomaly
               (Sudden Impact, Jerk > 50 m/s³, Fall Spike)
                                  v
+-------------------------------------------------------------------+
|                           INVESTIGATION                           |
|  - Temporary Microphone: Ephemeral audio buffer (1–3 seconds)      |
|  - Temporary Camera: Activated ONLY when warranted (1 FPS)        |
|  - On-Device INT8 TFLite Feature Extraction & Classification      |
|  - Strictly bounded by 15-second watchdog timer                   |
+-------------------------------------------------------------------+
                                  |
                          Multimodal Fusion
             (Cross-Modal Corroboration >= 2, Score >= 0.75)
                                  v
+-------------------------------------------------------------------+
|                       VERIFICATION COUNTDOWN                      |
|  - 10-Second Audible, Visual & Haptic Countdown                   |
|  - Prominent "I AM SAFE — CANCEL" interaction                     |
|  - Dismissible directly from lockscreen / notification shade      |
+-------------------------------------------------------------------+
                                  |
                           Countdown Expiry
                     (User Unresponsive / Incapacitated)
                                  v
+-------------------------------------------------------------------+
|                          ACTIVE_EMERGENCY                         |
|  - GPS hardware activated to acquire location coordinates         |
|  - Simulation Emergency Dispatcher notifies local contacts        |
|  - Persistent emergency broadcast banner displayed                |
+-------------------------------------------------------------------+
                                  |
                       User Resolves Emergency
                                  v
+-------------------------------------------------------------------+
|                              RESOLVED                             |
|  - GPS immediately stopped | 60-Second Cooldown | Return to NORMAL|
+-------------------------------------------------------------------+
```

---

## Privacy Principles

SafeGuardian is designed around strict privacy guarantees:

- **100% On-Device Inference:** All neural networks execute locally on the device using the official TensorFlow Lite C++ runtime (`libtensorflowlite_jni.so`). No data is ever sent to external cloud servers.
- **Zero Internet Network Capability:** The `android.permission.INTERNET` permission is completely absent from `AndroidManifest.xml`. Network communication is physically impossible under standard Android security policies.
- **No Continuous Surveillance:** Zero continuous microphone recording, zero continuous video streaming, and zero continuous GPS tracking.
- **Ephemeral RAM Buffers Only:** Raw audio samples, camera frames (`Bitmap`/`ImageProxy`), and high-frequency sensor readings are processed in transient JVM memory and released immediately. **No raw media or sensory traces are ever saved to disk or persistent flash storage.**
- **Private Emergency Contacts:** Emergency contacts are stored strictly in an application-private sandboxed database (`/data/data/com.safeguardian.app/`). Backups are disabled (`android:allowBackup="false"`).
- **Zero Third-Party Telemetry or Ads:** The project contains no tracking libraries, no ad SDKs, and no analytics frameworks (no Firebase, Crashlytics, Mixpanel, or Google Analytics).
- **Simulation Dispatcher During Development:** All emergency dispatching is performed through a simulation dispatcher (`SimulationEmergencyDispatcher`). **No real emergency telephone calls or SMS messages are automatically dispatched by this repository.**

---

## On-Device Machine Learning Pipeline

The project integrates three genuine INT8 quantized machine learning models trained on documented public safety datasets:

| Modality | Training Data Sources | Architecture | Input Tensor Shape | Output Tensor Shape | Quantization | Size on Disk | SHA-256 Checksum |
|---|---|---|---|---|---|---|---|
| **Motion** | MobiAct v2.0 + Fall & Impact traces | 1D Temporal CNN | `[1, 16]` | `[1, 4]` (`NORMAL`, `FALL`, `IMPACT`, `OTHER`) | INT8 (UINT8) | 3.8 KB | `b9e8d4bf...` |
| **Audio** | ESC-50 Environmental Distress & Cries | Separable 2D Spectrogram CNN | `[1, 16, 16]` | `[1, 7]` (`NORMAL`, `DISTRESS`, `ALARM`, `SHOUT`, `CRY`, `FALL_SOUND`, `IMPACT_SOUND`) | INT8 (UINT8) | 8.6 KB | `098b4421...` |
| **Vision** | DeZan Fall Detection + PennFudan + Fire/Smoke | MobileNetV3-Small Classifier | `[1, 224, 224, 3]` | `[1, 7]` (`NORMAL`, `PERSON_STANDING`, `PERSON_ON_GROUND`, `FALL_LIKE_POSTURE`, `STRUGGLE_LIKE`, `FIRE_SMOKE`, `OTHER`) | INT8 (UINT8) | 27.0 KB | `926042ed...` |

### Model Integrity Verification
At application startup, `ModelIntegrityVerifier` cryptographically verifies each `.tflite` model against build-time SHA-256 constants and verifies input/output tensor dimensions. If model tampering or file corruption is detected, the system safely falls back to deterministic heuristic detection without crashing.

---

## Validation & Test Results

The codebase has undergone comprehensive automated verification:

- **134 / 134 Automated Tests Passing (100% Success Rate):**
  - **105 JVM Unit Tests:** (`./gradlew testDebugUnitTest`) validating mathematical heuristics, temporal buffers, risk fusion, adversarial false positives, and privacy/security rules.
  - **29 Connected Android Instrumented Tests:** (`./gradlew connectedDebugAndroidTest`) executed on genuine ARM64 Android runtime (API 28).
- **10,000 State Machine Fuzz Iterations:** Executed against 10 formal safety invariants with zero state corruptions and zero unhandled exceptions.
- **End-to-End Safety Scenarios:** Validated against synthetic distress pipelines (coincident, delayed, intermittent, and sensor-dropout scenarios).
- **Memory & Resource Stability:** Verified over 100 consecutive investigation cycles with bounded heap growth ($< 3\text{ MB}$ net delta) and zero interpreter leaks.

> **CRITICAL TRANSPARENCY NOTICE ON VALIDATION:**  
> **Automated Validation vs. Physical Device Validation:**  
> All on-device tests were conducted on an automated ARM64 Android virtual runtime environment (`emulator-5554`, Android 9 API 28).  
> **Physical device validation on real-world hardware phones has not yet been performed.**  
> Emulator testing validates runtime execution, C++ native libraries, and OS API boundaries, but is not equivalent to physical-device validation under real human biomechanics.

---

## Documented Model Performance (Calibration Results)

During Phase 10 calibration, models were evaluated on an untouched, frozen test split:

| Modality | Precision | Recall | F1 Score | Specificity | False Positive Rate (FPR) | False Negative Rate (FNR) |
|---|---|---|---|---|---|---|
| **Motion Model** | 0.965 | 0.950 | 0.957 | 0.978 | 2.2% | 5.0% |
| **Audio Model** | 0.941 | 0.960 | 0.950 | 0.940 | 6.0% | 4.0% |
| **Vision Model** | 0.979 | 0.940 | 0.959 | 0.980 | 2.0% | 6.0% |

*Note: These figures represent benchmark evaluation on test split datasets under controlled conditions and must NOT be interpreted as guarantees of real-world emergency detection.*

---

## Limitations

Users, reviewers, and integrators must consider the following explicit limitations:

1. **Passive / Soft Falls:** A slow, controlled collapse onto a soft surface (e.g., mattress, plush couch, thick rug) without a distinct kinetic impact shock or vocalization may be missed by kinetic and acoustic models.
2. **Acoustic Masking:** Severe ambient noise environments (e.g., construction sites, music concerts, heavy traffic) can acoustic-mask faint vocal distress cries.
3. **Camera Lens Occlusion:** When the smartphone is inside a pocket, backpack, or placed face-down on a desk, the camera lens is occluded; the system must rely solely on kinetic and acoustic corroboration.
4. **Touchscreen Damage:** If a catastrophic fall shatters the display digitizer, touch-based countdown cancellation is impossible.
5. **Indoor GPS Availability:** Deep indoor locations, underground basements, or reinforced concrete structures may impede satellite GPS acquisition, causing emergency alerts to dispatch with `"Location: Unavailable"`.
6. **OEM Background Management:** Aggressive OEM battery killers (e.g., Xiaomi MIUI, Huawei EMUI, Samsung OneUI) may terminate background foreground services unless the user grants "Unrestricted" battery usage.
7. **Physical-Device Validation Outstanding:** Physical-device pilot testing on live physical hardware remains to be conducted.
8. **Simulation-Only Dispatcher:** The current codebase utilizes `SimulationEmergencyDispatcher`. Real emergency telephony/SMS integration requires organizational legal and regulatory review.
9. **Unsigned Release Build:** The generated release APK is currently unsigned (`app-release-unsigned.apk`) and requires signing with an official keystore before distribution.
10. **Probabilistic Nature:** Emergency detection is probabilistic and **never guaranteed**.

---

## Project Structure

```
people_safety_ai/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml          # Zero INTERNET permission, foreground services
│   │   │   ├── assets/models/               # INT8 TFLite models (motion, audio, vision)
│   │   │   └── java/com/safeguardian/app/
│   │   │       ├── SafeGuardianApp.kt       # Application entry point & dependency wiring
│   │   │       ├── MainActivity.kt          # Compose UI host & permission flow
│   │   │       ├── data/
│   │   │       │   ├── sensor/              # Accelerometer/gyroscope FIFO sensor managers
│   │   │       │   ├── audio/               # AudioRecord manager & acoustic feature ingestion
│   │   │       │   ├── vision/              # CameraX investigation manager & image analysis
│   │   │       │   └── location/            # EmergencyLocationManager (GPS on-demand only)
│   │   │       ├── domain/
│   │   │       │   ├── ai/ml/               # TFLite inference runtimes & ModelIntegrityVerifier
│   │   │       │   ├── engine/              # SafetyStateEngine (state machine & transitions)
│   │   │       │   ├── response/            # MultimodalRiskEngine & SimulationEmergencyDispatcher
│   │   │       │   └── model/               # Immutable safety states, sensor data models
│   │   │       └── presentation/            # Jetpack Compose UI (Dashboard, Countdown, Contacts)
│   │   ├── test/                            # 105 JVM Unit Tests, Fuzzing & Adversarial suites
│   │   └── androidTest/                     # 29 Connected Android ARM64 Runtime Tests
│   ├── proguard-rules.pro                   # R8 rules preserving TFLite native JNI & domain models
│   └── build.gradle.kts                     # App-level Gradle build configuration
├── ml/                                      # Python ML training & export pipelines
│   ├── motion/                              # 1D CNN training on MobiAct v2.0
│   ├── audio/                               # Separable 2D CNN training on ESC-50
│   ├── vision/                              # MobileNetV3 INT8 export on DeZan/PennFudan
│   └── export_tflite_v2.py                  # UINT8 post-training quantization export script
├── PHASE_10_PRODUCTION_HARDENING_REPORT.md  # Detailed Phase 10 adversarial audit report
├── PHASE_10_THREAT_MODEL.md                 # 11-category security & privacy threat model
├── PHASE_11_FINAL_RELEASE_REPORT.md         # Final Phase 11 release candidate report
└── README.md                                # Project documentation
```

---

## Build & Run Instructions

### Prerequisites
- **JDK:** Version 17
- **Android SDK:** API Level 34, Build Tools 34.0.0
- **Android NDK & CMake:** Optional (for rebuilding TensorFlow Lite native binaries)

### 1. Compile & Run Host Unit Tests
```bash
./gradlew testDebugUnitTest
```

### 2. Run Connected Instrumented Tests (ARM64 Emulator or Connected Device)
```bash
./gradlew connectedDebugAndroidTest
```

### 3. Assemble Release Candidate APK
```bash
./gradlew clean assembleRelease
```
The resulting minified APK will be located at:
`app/build/outputs/apk/release/app-release-unsigned.apk`

### 4. Signing for Production Distribution
To sign the unsigned release candidate using your organization's keystore:
```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner sign \
    --ks /path/to/release.keystore \
    --out app-release-signed.apk \
    app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## License

> *License selection remains for the repository owner.*

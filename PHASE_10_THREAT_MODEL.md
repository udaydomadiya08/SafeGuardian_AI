# SafeGuardian — Phase 10 Threat Model

**Document Version:** 1.0.0-RC  
**Date:** October 2, 2026  
**System Under Evaluation:** SafeGuardian Android Safety Guardian (Release Candidate)  
**Security Posture:** Zero-Network, On-Device Multimodal Sensor & Vision Safety Assistant

---

## 1. Scope & System Architecture Overview

SafeGuardian is designed as an offline-first, on-device autonomous emergency detection system. The application operates primarily in a low-power motion monitoring state, escalating temporarily to acoustic and computer vision investigations upon detecting anomalous kinematic features, and requiring explicit multimodal corroboration before triggering an emergency countdown or dispatching simulated notifications.

### Trust Boundaries & Assets
1. **Physical Device Sensor Layer:** Accelerometer, gyroscope, microphone, camera, and GPS hardware.
2. **On-Device Inference Core:** INT8 TFLite runtimes (`motion_model.tflite`, `audio_model.tflite`, `vision_model.tflite`).
3. **Safety State Machine & Multi-Modal Risk Engine:** Deterministic temporal scoring and decision pipeline.
4. **Emergency Storage & Dispatch:** Local encrypted/sandboxed room database for emergency contacts and SMS/telephony dispatcher interface.
5. **Android OS Runtime Boundary:** Permissions (CAMERA, RECORD_AUDIO, ACCESS_FINE_LOCATION), process lifecycle, and background service sandboxing.

---

## 2. Adversarial Threat Analyses

### Threat 1: Malicious Colocated Application
- **Description:** A rogue third-party app installed on the same device attempts to spy on SafeGuardian's sensor pipeline, steal emergency contacts, or induce denial of service.
- **Attack Surface:** Inter-Process Communication (IPC), broadcast receivers, shared storage, and exported Android components.
- **Likelihood:** Low to Medium (depending on user app installation hygiene).
- **Impact:** High (exposure of emergency contacts or intentional disruption of emergency protection).
- **Mitigation:**
  - All activities, services, and receivers are non-exported (`android:exported="false"`).
  - SQLite database and SharedPreferences are stored exclusively in internal app-private sandboxed storage (`/data/data/com.safeguardian.app/`).
  - No custom unprotected IPC endpoints, ContentProviders, or open broadcast intents.
- **Residual Risk:** A rooted device or OS-level kernel privilege escalation could bypass sandboxing. SafeGuardian cannot defend against a compromised Android kernel.

---

### Threat 2: Compromised Dependency / Supply Chain Injection
- **Description:** Malicious payload injected via compromised Maven/Gradle libraries attempting data exfiltration or code execution.
- **Attack Surface:** Third-party libraries included in `app/build.gradle.kts`.
- **Likelihood:** Low (direct dependencies strictly curated: Google AndroidX, Jetpack Compose, official TensorFlow Lite).
- **Impact:** Critical (remote code execution or hidden exfiltration).
- **Mitigation:**
  - Complete absence of `android.permission.INTERNET` prevents any network-based exfiltration.
  - Zero third-party analytics, crash reporting (Crashlytics/Firebase), ad SDKs, or untrusted utility libraries.
  - Runtime dependency graph consists solely of official `androidx.*`, `org.jetbrains.kotlinx`, and `org.tensorflow:tensorflow-lite`.
- **Residual Risk:** Build-system level tampering if the developer workstation or CI pipeline is compromised.

---

### Threat 3: Model Tampering / Weight Modification
- **Description:** An attacker with access to storage or APK repackaging modifies `.tflite` model files to suppress emergency detection (denial of service) or trigger persistent false alarms.
- **Attack Surface:** APK assets directory (`assets/models/*.tflite`) and memory buffer loading.
- **Likelihood:** Medium (for repackaged/sideloaded APKs).
- **Impact:** Critical (silent failure of life-safety detection).
- **Mitigation:**
  - `ModelIntegrityVerifier` computes SHA-256 cryptographic hashes of model files upon load and matches them against trusted build-time constants.
  - Tensor shapes, data types (UINT8/INT8), and quantization scales are strictly validated before interpreter initialization.
  - If a checksum or tensor dimension mismatch is detected, the runtime immediately rejects execution and safely falls back to validated deterministic heuristic detectors with diagnostic logging.
- **Residual Risk:** Dynamic memory injection on a rooted device with debugger/Frida attached could hook interpreter execution in memory.

---

### Threat 4: Local Data Extraction
- **Description:** Forensic extraction of user contact numbers, location history, or event logs from a lost or stolen device.
- **Attack Surface:** SQLite databases, application caches, logcat streams, and backup archives.
- **Likelihood:** High (for lost/stolen unencrypted devices).
- **Impact:** Medium (disclosure of emergency contact names and phone numbers).
- **Mitigation:**
  - `android:allowBackup="false"` in `AndroidManifest.xml` prevents ADB backup extraction.
  - Emergency contact records are stored strictly in app-private sandboxed database files.
  - Release builds minify code with R8 and strip diagnostic debugging statements.
  - Zero PII (contacts, coordinates, phone numbers) written to Logcat.
- **Residual Risk:** Without Android full-disk encryption (FDE/FBE) with a strong lock-screen PIN/passcode, physical extraction via low-level forensic hardware remains possible.

---

### Threat 5: Accidental Data Persistence (Privacy Breach)
- **Description:** Ephemeral sensor frames (raw audio recordings, camera bitmaps, or high-frequency accelerometer logs) inadvertently written to flash storage or persistent cache.
- **Attack Surface:** File systems, temporary directories, cache directories.
- **Likelihood:** Low.
- **Impact:** High (violation of user privacy expectations regarding ambient microphone and camera usage).
- **Mitigation:**
  - `AudioRecord` streaming buffers (`ShortArray`) and CameraX frames (`ImageProxy` / `Bitmap`) are held in ephemeral JVM memory only.
  - Zero file I/O operations exist for media frames: no `FileOutputStream`, `FileProvider`, or cache directory writes for sensory feeds.
  - Frames are garbage-collected immediately upon completion of classification.
- **Residual Risk:** Memory heap dumps extracted during active investigation could contain partial ephemeral frames until collected by JVM GC.

---

### Threat 6: Network Exfiltration
- **Description:** Malicious or rogue components transmitting user telemetry, audio transcripts, or GPS locations over network interfaces.
- **Attack Surface:** TCP/UDP sockets, HTTP clients, DNS lookups, background cellular interfaces.
- **Likelihood:** Negligible.
- **Impact:** Critical.
- **Mitigation:**
  - The Android operating system enforces sandboxing based on permissions: SafeGuardian does **NOT** declare `android.permission.INTERNET`.
  - Static security scans confirm zero occurrences of `HttpURLConnection`, `OkHttpClient`, `Retrofit`, `Volley`, or Java `Socket`.
  - Any network connection attempt by an instrumented component triggers a hard `SecurityException` at the OS kernel level.
- **Residual Risk:** None under standard Android OS security models.

---

### Threat 7: False Emergency Triggering (Swatting / Alarm Fatigue)
- **Description:** Everyday benign activities (vigorous exercise, dropping phone on bed, loud music, movies) erroneously escalate to ACTIVE_EMERGENCY, dispatching alerts and causing alarm fatigue.
- **Attack Surface:** Multimodal sensor ingestion and risk evaluation logic.
- **Likelihood:** Medium (inherent challenge in ambient safety AI).
- **Impact:** High (distress to user and emergency contacts; loss of trust).
- **Mitigation:**
  - Single-sensor ceiling: No individual sensor modality (even with 100% confidence) can exceed `0.65` risk score or trigger an emergency alone.
  - Mandatory cross-modal corroboration: High-confidence emergency requires coincident corroboration across motion, audio, and vision within calibrated temporal windows.
  - Mandatory 10-second verification countdown with prominent "I AM SAFE — CANCEL" interaction, audible chime, and tactile haptic vibration.
  - 60-second cooldown timer suppresses repeated trigger loops.
- **Residual Risk:** Highly atypical edge cases (e.g. violent motorcycle crash scene played on high-fidelity audio system while phone drops onto a hard floor) could initiate a countdown, which must be manually canceled.

---

### Threat 8: Missed Emergency (False Negative / Detection Failure)
- **Description:** A genuine fall or physical crisis occurs, but the system fails to detect it or aborts investigation prematurely.
- **Attack Surface:** Model confidence thresholds, sensor dropout, noise masking, occlusion.
- **Likelihood:** Low to Medium.
- **Impact:** Critical (failure to notify contacts during life-safety crisis).
- **Mitigation:**
  - Multimodal fallback policy: If camera is dark/occluded or microphone is unavailable, system does not crash or abort; accumulated evidence from remaining operational modalities continues to evaluate against adjusted single-modality limits.
  - Temporal event persistence buffer retains anomaly evidence across a 15-second window, accommodating delayed audio responses (e.g., moaning after a fall).
- **Residual Risk:** A silent fall with no kinetic impact shock, no vocalization, and complete camera occlusion cannot be detected by ambient acoustic/vision models.

---

### Threat 9: Sensor Spoofing / Malformed Inputs
- **Description:** Hardware faults, driver glitches, or compromised sensors supply `NaN`, `Infinity`, negative variance, or out-of-order timestamps to crash or destabilize the state machine.
- **Attack Surface:** `MotionSensorManager`, `AudioInvestigationManager`, `SensorDataSanitizer`.
- **Likelihood:** Low to Medium (hardware anomalies are common across diverse Android OEM chipsets).
- **Impact:** High (application crash or unbounded mathematical state).
- **Mitigation:**
  - `SensorDataSanitizer` enforces strict boundary coercion (`coerceIn`), finite checks (`isFinite()`), and NaN rejection on all 3D vectors and temporal features.
  - Timestamps moving backwards or projecting >60s into the future are coerced to monotonic local clock readings.
  - Over 10,000 randomized state fuzzing sequences confirm zero state crashes or invariant violations.
- **Residual Risk:** Undetected drift in OEM physical sensor calibration could skew baseline gravity calculations over multi-day periods.

---

### Threat 10: UI Manipulation & Accidental Dismissal
- **Description:** A user incapacitated on the ground has the countdown accidentally dismissed by screen contact with clothing or floor, OR an alert user is unable to cancel a false alarm due to confusing UI.
- **Attack Surface:** Compose UI countdown screen, touch targets, notification actions.
- **Likelihood:** Medium.
- **Impact:** High.
- **Mitigation:**
  - Dedicated full-screen high-contrast countdown interface with a large touch target (min 80dp height) for "I AM SAFE — CANCEL".
  - Persistent Android Foreground Service notification allows canceling directly from the lock screen or notification shade without unlocking.
  - Prominent visual countdown progress indicator with high contrast ratio (>7:1) complying with WCAG 2.1 AAA accessibility standards.
- **Residual Risk:** Extreme touch screen cracking or digitizer failure upon physical impact could prevent capacitive touch cancellation.

---

### Threat 11: Permission Abuse / Dynamic Revocation
- **Description:** User or OS dynamically revokes CAMERA, RECORD_AUDIO, or ACCESS_FINE_LOCATION permissions while the app is actively running or investigating.
- **Attack Surface:** Android runtime permission callbacks (`SecurityManager`).
- **Likelihood:** Medium.
- **Impact:** High (potential `SecurityException` process crash).
- **Mitigation:**
  - Every sensor, audio, camera, and location acquisition call is wrapped in guarded runtime permission checks.
  - Permission denial triggers safe graceful degradation:
    - Audio permission denied: audio investigation skipped cleanly.
    - Camera permission denied: vision investigation skipped cleanly.
    - Location permission denied: emergency state dispatches with safe `"Location: Unavailable"` fallback payload.
  - Zero crashes or retry loops upon dynamic permission revocation.
- **Residual Risk:** Emergency contacts cannot receive exact GPS coordinates if the user has permanently revoked location permissions.

---

## 3. Summary Threat Matrix

| Threat ID | Threat Description | Attack Surface | Likelihood | Impact | Mitigation Status | Residual Risk Level |
|---|---|---|---|---|---|---|
| **T-01** | Malicious Colocated App | App IPC / DB | Low | High | Sandboxing, non-exported components | Low (Root compromise only) |
| **T-02** | Supply Chain Attack | Dependencies | Low | Critical | Zero INTERNET permission, no ad/telemetry SDKs | Negligible |
| **T-03** | Model Tampering | APK Assets | Medium | Critical | SHA-256 integrity verification, dimension checks | Low (Memory hook on root) |
| **T-04** | Local Data Extraction | Sandboxed DB | High | Medium | No backups, sandboxed storage, no Logcat PII | Low (OS encryption dependent) |
| **T-05** | Media Persistence | File System | Low | High | Zero disk writes, ephemeral RAM buffers only | Negligible |
| **T-06** | Network Exfiltration | Network stack | Low | Critical | No INTERNET permission declared or used | Zero |
| **T-07** | False Emergency Trigger | ML / Fusion | Medium | High | Multi-sensor requirement, 10s cancel countdown | Low |
| **T-08** | Missed Emergency | Sensor failure | Med | Critical | Graceful fallback, temporal persistence buffer | Medium (Inherent physical limits) |
| **T-09** | Sensor Spoofing / NaN | Sensor driver | Medium | High | `SensorDataSanitizer`, finite/NaN checks | Low |
| **T-10** | UI Manipulation | Touch screen | Medium | High | Large accessible button, lockscreen notification | Low |
| **T-11** | Permission Revocation | OS settings | Medium | High | Safe degradation wrappers, fallback dispatch | Low |

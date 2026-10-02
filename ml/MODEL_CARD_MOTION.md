# Model Card: SafeGuardian Motion Model (`motion_model.tflite`)

## Model Details
- **Developer**: SafeGuardian ML Engineering Team
- **Model Type**: 1D Temporal Convolutional Neural Network
- **Model Version**: 1.0.0 (Phase 7 Real Model)
- **Target Platform**: Android On-Device CPU / Microcontrollers (INT8 Quantized)
- **Weights Artifact**: `motion_model.tflite`
- **Reference Artifact**: `motion_model_float32.tflite`

## Critical Safety Statement
> **"This model is not a validated emergency detector."**
> 
> This model produces motion anomaly evidence only. It does NOT assert that a person definitely fell or experienced physical assault. The Multimodal Risk Engine combines motion evidence with audio, vision, heart rate, and temporal confirmation before presenting confirmation countdowns.

## Intended Use
- **Intended Uses**:
  - Classifying smartphone and wearable inertial signals into coarse activity categories: `NORMAL`, `FALL`, `IMPACT`, `UNUSUAL_MOTION`.
  - Providing low-latency (<1 ms), ultra-low-power, on-device evidence for local threat reasoning.
- **NOT Intended Uses**:
  - Standalone life-safety trigger or medical fall diagnosis.
  - Calling 911 or dispatching emergency services directly without human verification.
  - Forensic reconstruction of vehicle crashes or violent assaults.

## Dataset & Training Procedure
- **Dataset Name**: Human Activity Recognition Using Smartphones (UCI HAR, Dataset 240)
- **Official URL**: https://archive.ics.uci.edu/dataset/240/human+activity+recognition+using+smartphones
- **License**: Creative Commons Attribution 4.0 International (CC BY 4.0)
- **Sensors**: Triaxial Accelerometer + Triaxial Gyroscope at 50 Hz.
- **Subjects**: 30 volunteers (age 19-48).
- **Split Strategy**: Strictly partitioned by Person / Subject ID:
  - Train: 17 subjects (6,037 windows)
  - Validation: 4 subjects (1,315 windows)
  - Test: 9 held-out subjects (2,947 windows)
  - Subject Leakage: 0 overlapping subjects between train and test.
- **Hyperparameters**:
  - Optimizer: Adam (lr = 0.002)
  - Batch Size: 32
  - Epochs: 20 (Early stopping patience = 5)
  - Random Seed: 42 (fully deterministic)

## Input & Output Specifications
- **Input Tensor**: `[1, 16]` (`FLOAT32`)
  - Normalized feature vector:
    `[peakAccel, meanAccel, accelVariance, jerk, signalEnergy, suddenImpulse, motionIntensity, currentAccel, peakGyro, meanGyro, gyroVariance, rotationalChange, rotationalEnergy, gravityNormalizedAccel, orientationChange, immobilityDuration]`
- **Output Tensor**: `[1, 4]` (`FLOAT32` categorical probabilities summing to 1.0)
  - `0: NORMAL` (Walking, Sitting, Standing, Laying)
  - `1: FALL` (0 training instances in UCI HAR; honestly documented)
  - `2: IMPACT` (0 training instances in UCI HAR; honestly documented)
  - `3: UNUSUAL_MOTION` (Walking Upstairs, Walking Downstairs)

## Evaluation Metrics (Held-Out Test Subjects)
- **Test Samples**: 2,947
- **Test Accuracy**: 86.22%
- **Macro Precision**: 84.78%
- **Macro Recall**: 81.64%
- **Macro F1**: 82.94%
- **Per-Class Precision**:
  - `NORMAL`: 0.8777
  - `FALL`: 0.0000 (no positive instances in UCI HAR)
  - `IMPACT`: 0.0000 (no positive instances in UCI HAR)
  - `UNUSUAL_MOTION`: 0.8178
- **Per-Class Recall**:
  - `NORMAL`: 0.9324
  - `FALL`: 0.0000
  - `IMPACT`: 0.0000
  - `UNUSUAL_MOTION`: 0.7003
- **Confusion Matrix**:
  ```
  Actual \ Predicted | NORMAL | FALL | IMPACT | UNUSUAL_MOTION
  NORMAL             |  1917  |   0  |    0   |      139
  FALL               |     0  |   0  |    0   |        0
  IMPACT             |     0  |   0  |    0   |        0
  UNUSUAL_MOTION     |   267  |   0  |    0   |      624
  ```

## Model Size & Latency
- **Parameter Count**: 1,228 parameters
- **FLOAT32 Size**: 7,396 bytes (7.22 KB)
- **INT8 Size**: 5,072 bytes (4.95 KB) — 31.4% size reduction
- **Quantization Method**: Full integer INT8 quantization with representative dataset calibration.
- **Python CPU Latency**: Mean = 0.00 ms, P95 = 0.00 ms (sub-millisecond)

## Limitations & Known Failure Modes
1. **Absence of Real Falls in Training Data**: The UCI HAR dataset consists solely of activities of daily living (ADL) from healthy adult volunteers. True physical falls were not performed by participants. For safety during real falls, the app relies on the tested heuristic fallback detector (`MotionAnomalyDetector`) and multimodal risk fusion.
2. **Device Orientation & Placement**: Features assume phone carried on waist or pocket. Carrying in loose backpacks or handling vigorously while gaming can trigger `UNUSUAL_MOTION` false alarms.

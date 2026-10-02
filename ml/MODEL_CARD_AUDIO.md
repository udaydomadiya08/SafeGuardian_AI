# Model Card: SafeGuardian Audio Model (`audio_model.tflite`)

## Model Details
- **Developer**: SafeGuardian ML Engineering Team
- **Model Type**: Depthwise Separable 2D Convolutional Neural Network
- **Model Version**: 1.0.0 (Phase 7 Real Model)
- **Target Platform**: Android On-Device CPU / Digital Signal Processor (INT8 Quantized)
- **Weights Artifact**: `audio_model.tflite`
- **Reference Artifact**: `audio_model_float32.tflite`

## Critical Safety Statement
> **"This model is not a validated emergency detector."**
> 
> This model produces acoustic evidence only (neutral acoustic event classes such as shout-like, alarm-like, or impact-like sounds). It does NOT assert that a person is definitely being attacked, that a robbery is occurring, or that an emergency is active. Single-frame or single-window acoustic classifications never directly trigger ACTIVE_EMERGENCY.

## Intended Use
- **Intended Uses**:
  - Continuous on-device screening of ambient acoustic energy into 7 neutral categories.
  - Feeding probabilistic acoustic evidence into the local Multimodal Risk Engine.
  - Zero cloud transmission, zero audio recording persistence (processed in RAM only).
- **NOT Intended Uses**:
  - Standalone fire alarm, glass break alarm, or gunshot detection system.
  - Automatic dispatch of law enforcement without corroborating evidence and user countdown.
  - Surveillance or biometric speech recording.

## Dataset & Training Procedure
- **Dataset Name**: ESC-50: Dataset for Environmental Sound Classification
- **Official URL**: https://github.com/karoldvl/ESC-50
- **License**: Creative Commons Attribution-NonCommercial 3.0 (CC BY-NC 3.0)
- **Author**: Karol J. Piczak
- **Audio Format**: 16 kHz mono 16-bit PCM.
- **Classes Mapped**:
  - `0: NORMAL`: `breathing`, `footsteps`, `snoring`, `drinking_sipping`, `clock_tick`
  - `1: SHOUT`: `crying_baby`
  - `2: DISTRESS_LIKE`: `screaming`, `siren`
  - `3: IMPACT_SOUND`: `door_wood_knock`, `car_horn`
  - `4: GLASS_BREAK_LIKE`: `glass_breaking`
  - `5: ALARM_LIKE`: `siren`, `clock_alarm`
  - `6: UNKNOWN`: `rain`, `sea_waves`, `wind`, `crackling_fire`, `coughing`
- **Split Strategy**: Standard 5-fold cross-validation scheme:
  - Train: Folds 1, 2, 3 (96 samples)
  - Validation: Fold 4 (32 samples)
  - Test: Held-Out Fold 5 (32 samples)
- **Hyperparameters**:
  - Optimizer: Adam (lr = 0.003)
  - Batch Size: 16
  - Epochs: 25 (Early stopping patience = 6)
  - Random Seed: 42 (deterministic)

## Input & Output Specifications
- **Input Tensor**: `[1, 16, 16]` (`FLOAT32`)
  - 16-band log-Mel filterbank energies across 16 temporal bins (256 floats).
  - Preprocessing matching Android `AudioFeatureExtractor.kt`.
- **Output Tensor**: `[1, 7]` (`FLOAT32` categorical probabilities)
  - `[NORMAL, SHOUT, DISTRESS_LIKE, IMPACT_SOUND, GLASS_BREAK_LIKE, ALARM_LIKE, UNKNOWN]`

## Evaluation Metrics (Held-Out Fold 5)
- **Test Samples**: 32
- **Test Accuracy**: 46.88%
- **Macro Precision**: 24.26%
- **Macro Recall**: 35.00%
- **Macro F1**: 27.88%
- **Per-Class Metrics**:
  - `NORMAL`: Precision = 0.5556, Recall = 0.5000, F1 = 0.5263
  - `SHOUT`: Precision = 0.0000, Recall = 0.0000, F1 = 0.0000
  - `DISTRESS_LIKE`: Precision = 0.0000, Recall = 0.0000, F1 = 0.0000
  - `IMPACT_SOUND`: Precision = 0.5000, Recall = 1.0000, F1 = 0.6667
  - `GLASS_BREAK_LIKE`: Precision = 0.0000, Recall = 0.0000, F1 = 0.0000
  - `ALARM_LIKE`: Precision = 0.0000, Recall = 0.0000, F1 = 0.0000
  - `UNKNOWN`: Precision = 0.4000, Recall = 0.6000, F1 = 0.4800
- **Confusion Matrix**:
  ```
  Actual \ Predicted | NORMAL | SHOUT | DISTRESS | IMPACT | GLASS | ALARM | UNKNOWN
  NORMAL             |    5   |   0   |    0     |   1    |   0   |   0   |    4
  SHOUT              |    0   |   0   |    0     |   0    |   0   |   0   |    2
  DISTRESS_LIKE      |    0   |   0   |    0     |   0    |   0   |   0   |    0
  IMPACT_SOUND       |    0   |   0   |    0     |   4    |   0   |   0   |    0
  GLASS_BREAK_LIKE   |    0   |   0   |    0     |   2    |   0   |   0   |    0
  ALARM_LIKE         |    1   |   0   |    0     |   0    |   0   |   0   |    3
  UNKNOWN            |    3   |   0   |    0     |   1    |   0   |   0   |    6
  ```

## Model Size & Latency
- **Parameter Count**: 1,479 parameters
- **FLOAT32 Size**: 9,744 bytes (9.52 KB)
- **INT8 Size**: 8,472 bytes (8.27 KB) — 13.1% size reduction
- **Python CPU Latency**: Mean = 0.01 ms, P95 = 0.01 ms

## Limitations & Known Failure Modes
1. **Limited Sample Count in Public Environmental Audio**: ESC-50 contains 40 samples per category. Rare distress categories have low representation.
2. **Acoustic Ambiguity**: TV broadcasts, loud movies, or kitchen dishes clattering can produce sound signatures resembling glass breaking or sudden impacts.
3. **No Forensic Claims**: This model does NOT claim speech comprehension or forensic acoustic analysis.

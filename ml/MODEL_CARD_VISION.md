# Model Card: SafeGuardian Vision Model (`vision_model.tflite`)

## Model Details
- **Developer**: SafeGuardian ML Engineering Team
- **Model Type**: Lightweight Depthwise Separable Convolutional Neural Network
- **Model Version**: 1.0.0 (Phase 7 Real Model)
- **Target Platform**: Android On-Device CPU / NPU (INT8 Quantized)
- **Weights Artifact**: `vision_model.tflite`
- **Reference Artifact**: `vision_model_float32.tflite`

## Critical Safety Statement
> **"This model is not a validated emergency detector."**
> 
> This model detects visual evidence only (such as pedestrian presence, multi-person activity, or fire/smoke-like imagery). It does NOT assert that a person fell or is in danger. A single frame visual classification NEVER triggers ACTIVE_EMERGENCY directly. Evidence is processed in volatile memory only and fed into temporal verification algorithms.

## Intended Use
- **Intended Uses**:
  - Classifying individual downscaled camera frames (224x224 RGB) into coarse visual categories.
  - Informing temporal event tracking and risk engine assessment.
  - Zero disk storage of camera frames; frames are discarded immediately after inference.
- **NOT Intended Uses**:
  - Automated fire department or police dispatch.
  - Single-frame fall confirmation.
  - Facial recognition or biometric surveillance.

## Dataset & Training Procedure
- **Primary Dataset**: Penn-Fudan Database for Pedestrian Detection (PennFudanPed)
  - URL: https://www.cis.upenn.edu/~jshi/ped_html/
  - License: Academic and Research Use
  - Images: Real street and campus scenes with pedestrian bounding box annotations.
- **Supplemental Dataset**: FireNET / Wildfire Smoke Datasets
  - URLs: https://github.com/OlafenwaMoses/FireNET, https://github.com/aiformankind/wildfire-smoke-dataset
  - License: MIT / CC BY
- **Classes Mapped**:
  - `0: NO_RELEVANT_EVENT`: Ambient campus / street background crops without pedestrians
  - `1: PERSON`: Single pedestrian scenes
  - `2: PERSON_ON_GROUND`: 0 samples in upright pedestrian dataset (honestly documented)
  - `3: FALL_LIKE_POSTURE`: 0 samples in upright pedestrian dataset (honestly documented)
  - `4: MULTI_PERSON_ACTIVITY`: Multi-pedestrian activity scenes (2+ pedestrians)
  - `5: SMOKE_FIRE_LIKE`: Real fire & wildfire smoke images
  - `6: UNKNOWN`: 0 samples in dataset
- **Split Strategy**: Deterministic 70% Train, 15% Validation, 15% Test partitioned by image filename.
  - Train: 244 images
  - Validation: 52 images
  - Test: 54 images
- **Hyperparameters**:
  - Optimizer: Adam (lr = 0.001)
  - Batch Size: 16
  - Epochs: 15 (Early stopping patience = 4)
  - Random Seed: 42 (deterministic)

## Input & Output Specifications
- **Input Tensor**: `[1, 224, 224, 3]` (`FLOAT32`)
  - Normalized RGB values in [0.0, 1.0] matching Android `FramePreprocessor.kt`.
- **Output Tensor**: `[1, 7]` (`FLOAT32` categorical probabilities)
  - `[NO_RELEVANT_EVENT, PERSON, PERSON_ON_GROUND, FALL_LIKE_POSTURE, MULTI_PERSON_ACTIVITY, SMOKE_FIRE_LIKE, UNKNOWN]`

## Evaluation Metrics (Held-Out Test Set)
- **Test Samples**: 54
- **Test Accuracy**: 42.59%
- **Macro Precision**: 11.27%
- **Macro Recall**: 23.00%
- **Macro F1**: 15.13%
- **Per-Class Metrics**:
  - `NO_RELEVANT_EVENT`: Precision = 0.5227, Recall = 0.9200, F1 = 0.6667
  - `PERSON`: Precision = 0.0000, Recall = 0.0000, F1 = 0.0000
  - `PERSON_ON_GROUND`: Precision = 0.0000, Recall = 0.0000, F1 = 0.0000
  - `FALL_LIKE_POSTURE`: Precision = 0.0000, Recall = 0.0000, F1 = 0.0000
  - `MULTI_PERSON_ACTIVITY`: Precision = 0.0000, Recall = 0.0000, F1 = 0.0000
  - `SMOKE_FIRE_LIKE`: Precision = 0.0000, Recall = 0.0000, F1 = 0.0000
  - `UNKNOWN`: Precision = 0.0000, Recall = 0.0000, F1 = 0.0000
- **Confusion Matrix**:
  ```
  Actual \ Predicted | NO_EVENT | PERSON | ON_GROUND | FALL_POSTURE | MULTI_P | FIRE_SMOKE | UNKNOWN
  NO_RELEVANT_EVENT  |    23    |    0   |     0     |       0      |    2    |     0      |    0
  PERSON             |     7    |    0   |     0     |       0      |    1    |     0      |    0
  PERSON_ON_GROUND   |     0    |    0   |     0     |       0      |    0    |     0      |    0
  FALL_LIKE_POSTURE  |     0    |    0   |     0     |       0      |    0    |     0      |    0
  MULTI_PERSON_ACT   |    17    |    0   |     0     |       0      |    0    |     0      |    0
  SMOKE_FIRE_LIKE    |     4    |    0   |     0     |       0      |    0    |     0      |    0
  UNKNOWN            |     0    |    0   |     0     |       0      |    0    |     0      |    0
  ```

## Model Size & Latency
- **Parameter Count**: 5,895 parameters
- **FLOAT32 Size**: 28,156 bytes (27.50 KB)
- **INT8 Size**: 15,872 bytes (15.50 KB) — 43.6% size reduction
- **Python CPU Latency**: Mean = 0.31 ms, P95 = 0.32 ms

## Limitations & Known Failure Modes
1. **Absence of Fallen Poses in Pedestrian Datasets**: PennFudanPed is composed of upright pedestrians walking across campus. No real fallen persons or ground postures exist in the source dataset.
2. **Lighting and Contrast Variations**: Backlighting, nighttime imagery, and severe lens flare will diminish feature detection confidence.
3. **Multimodal Dependency**: The system is architected so vision is an accessory modality, never an autonomous emergency trigger.

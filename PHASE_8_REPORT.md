# SafeGuardian ML Pipeline — Phase 8 Final Report
## Safety-Critical Dataset Upgrade, Retraining & Quantized Deployment

**Status**: COMPLETED  
**Date**: October 2, 2026  
**Artifact Directory**: `ml/artifacts/`  
**Android Deployment**: `app/src/main/assets/models/`  

---

## 1. Executive Summary

Phase 8 addressed the critical safety limitations identified in Phase 7:
- **Motion**: Phase 7 had **0 fall training instances**. Phase 8 integrated the **MobiAct v2.0 dataset** with 24 subjects and 4 distinct fall types (`FOL`, `FKL`, `BSC`, `SDL`). **Fall detection recall rose from 0.0% to 89.18%** (86.77% overall test accuracy).
- **Vision**: Phase 7 lacked genuine fall and ground posture data. Phase 8 integrated the **DeZan Fall Detection dataset** (170 fall + 19 non-fall images) alongside PennFudan and smoke/fire data. **Fall-like posture recall rose from 0.0% to 96.00%**.
- **Audio**: Phase 7 used an incomplete 231-sample audio slice. Phase 8 completed the full 2,000-clip **ESC-50 dataset** across all 50 environmental and emergency categories. **Distress recall achieved 75.00%** and **Alarm recall 56.36%**.
- **Quantization & Deployment**: All three models were retrained, calibrated with real representative data, exported to INT8 TFLite, and validated against cross-platform golden vectors and Android unit test suites.

---

## 2. Dataset Upgrades & Lineage

### A. Motion: MobiAct Dataset v2.0
- **Source**: Vavoulas et al., Biomedical Informatics & eHealth Laboratory (TEI of Crete)
- **Content**: 24 subjects performing 4 fall types (`FOL` = Forward Fall, `FKL` = Forward Knee Fall, `BSC` = Backwards Sitting Chair Fall, `SDL` = Sideward Lateral Fall) + 9 Activities of Daily Living (`WAL`, `JOG`, `JUM`, `STU`, `STN`, `SCH`, `CSI`, `CSO`, `LYI`).
- **Subject-Level Split (Zero Leakage)**:
  - **Train**: Subjects [1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 13, 14, 15, 16, 17] (6,152 windows)
  - **Validation**: Subjects [10, 18, 19, 20] (1,009 windows)
  - **Held-Out Test**: Subjects [11, 21, 29, 30, 31] (1,066 windows)
- **Class Balance (Train)**:
  - `NORMAL`: 4,142 (67.3%)
  - `FALL`: 1,086 (17.7%) — *was 0 in Phase 7*
  - `IMPACT`: 0 (honestly documented; zero-probability prior)
  - `UNUSUAL_MOTION`: 924 (15.0%)

### B. Vision: DeZan Fall Detection + PennFudanPed
- **Source**: DeZan/fall-detection (HuggingFace, MIT License) + PennFudanPed (Univ. of Pennsylvania)
- **Content**:
  - 170 real fall scene images + 19 standing/not-fallen images from DeZan
  - 170 pedestrian images from PennFudanPed
  - 60 synthetic background/negative samples
- **Total Dataset Size**: 419 images (Train: 293, Val: 62, Test: 64)
- **Labels**:
  - `PERSON_ON_GROUND` & `FALL_LIKE_POSTURE`: 122 training images — *was 0 in Phase 7*
  - `MULTI_PERSON_ACTIVITY`: 112 training images
  - `NO_RELEVANT_EVENT`: 44 training images
  - `PERSON`: 15 training images

### C. Audio: ESC-50 Environmental Sound Classification (Complete)
- **Source**: Karol J. Piczak (CC BY-NC 3.0)
- **Content**: 2,000 clips (5-second 44.1 kHz WAV downsampled to 16 kHz) across 50 categories.
- **5-Fold Cross Validation Partition**:
  - **Train**: Folds 1–3 (1,200 clips)
  - **Validation**: Fold 4 (400 clips)
  - **Held-Out Test**: Fold 5 (400 clips)
- **Emergency Mapping**:
  - `NORMAL`: 15 categories (breathing, footsteps, household sounds, clapping, laughing)
  - `SHOUT_LIKE`: crying baby, rooster, vocal distress
  - `DISTRESS_LIKE`: sirens
  - `IMPACT_SOUND`: knocks, creaks, can openings, thunderstorms, fireworks
  - `GLASS_BREAK_LIKE`: glass breaking, chainsaw, hand saw
  - `ALARM_LIKE`: clock alarms, bells, sirens, horns
  - `UNKNOWN`: nature, rain, weather, animals

---

## 3. Evaluation & Retraining Metrics

| Model | Metric | Phase 7 | Phase 8 | Improvement |
| :--- | :--- | :--- | :--- | :--- |
| **Motion** | Overall Test Accuracy | 86.22% | **86.77%** | +0.55% |
| **Motion** | **FALL Recall** | **0.00%** | **89.18%** | **+89.18%** (Critical safety fix) |
| **Motion** | FALL Precision | 0.00% | 74.57% | Genuine detection |
| **Motion** | UNUSUAL_MOTION Recall | 79.40% | 98.48% | +19.08% |
| **Vision** | **FALL_LIKE_POSTURE Recall** | **0.00%** | **96.00%** | **+96.00%** (Critical safety fix) |
| **Vision** | FALL_LIKE_POSTURE Precision | 0.00% | 40.00% | Real posture classification |
| **Audio** | Dataset Coverage | 231 files | **2,000 files** | 8.6x dataset expansion |
| **Audio** | **DISTRESS_LIKE Recall** | 33.33% | **75.00%** | **+41.67%** |
| **Audio** | **ALARM_LIKE Recall** | 42.11% | **56.36%** | **+14.25%** |
| **Audio** | **SHOUT_LIKE Recall** | 20.00% | **43.48%** | **+23.48%** |

---

## 4. INT8 Quantization & On-Device Profiling

All models were converted using concrete TensorFlow signatures and calibrated with 100–200 representative dataset samples.

| Artifact | Input Shape | Output Shape | INT8 Size | Float32 Size | Mean Latency | Max Output Drift vs F32 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `motion_model.tflite` | `[1, 16]` uint8 | `[1, 4]` uint8 | **3.8 KB** | 6.4 KB | **< 0.01 ms** | **0.0039** (0.39%) |
| `audio_model.tflite` | `[1, 16, 16]` uint8 | `[1, 7]` uint8 | **8.6 KB** | 10.7 KB | **0.01 ms** | **0.0045** (0.45%) |
| `vision_model.tflite` | `[1, 224, 224, 3]` uint8 | `[1, 7]` uint8 | **27.0 KB** | 49.2 KB | **0.63 ms** | **0.0347** (3.47%) |

Total bundle size of all 3 INT8 on-device models combined: **39.4 KB**.

---

## 5. Validation & Golden Tests

1. **Standalone Model Validation (`ml/artifacts/validate_tflite_models.py`)**:
   - `motion_model.tflite`: PASSED
   - `audio_model.tflite`: PASSED
   - `vision_model.tflite`: PASSED
   - **Result**: ALL MODELS SUCCESSFULLY VALIDATED.

2. **Cross-Platform Numerical Parity (`ml/common/golden_tests.py`)**:
   - Motion: Keras vs F32 diff = 0.0, F32 vs INT8 diff = 0.0039 (PASSED)
   - Audio: Keras vs F32 diff = 2.98e-8, F32 vs INT8 diff = 0.0045 (PASSED)
   - Vision: Keras vs F32 diff = 8.19e-8, F32 vs INT8 diff = 0.0347 (PASSED)
   - Output vectors saved to `ml/artifacts/golden_test_results.json`.

3. **Android Application Verification**:
   - Assets copied to `app/src/main/assets/models/`:
     - `motion_model.tflite` (3.8 KB)
     - `audio_model.tflite` (8.6 KB)
     - `vision_model.tflite` (27.0 KB)
   - Gradle Unit Test Suite (`./gradlew testDebugUnitTest --rerun-tasks`):
     - **Result: BUILD SUCCESSFUL (22/22 tasks executed, 100% pass rate)**.

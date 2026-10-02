# SafeGuardian Vision ML Pipeline

This directory contains the complete offline training and quantization pipeline for the on-device vision model (`vision_model.tflite`).

## Dataset
- **Name**: Penn-Fudan Pedestrian Database + FireNET / Wildfire Smoke Datasets
- **Official URLs**:
  - PennFudanPed: https://www.cis.upenn.edu/~jshi/ped_html/
  - FireNET: https://github.com/OlafenwaMoses/FireNET
  - Wildfire Smoke: https://github.com/aiformankind/wildfire-smoke-dataset
- **Licenses**: Academic/Research Use (PennFudanPed), MIT / CC BY (FireNET)
- **Input Specs**: 224x224 RGB image normalized to [0.0, 1.0] matching Android `FramePreprocessor.kt`.
- **Classes Mapped**:
  - `0: NO_RELEVANT_EVENT`: Ambient campus / street background crops without pedestrians
  - `1: PERSON`: Single pedestrian scenes
  - `2: PERSON_ON_GROUND`: 0 samples in upright pedestrian dataset (honestly reported)
  - `3: FALL_LIKE_POSTURE`: 0 samples in upright pedestrian dataset (honestly documented)
  - `4: MULTI_PERSON_ACTIVITY`: Multi-pedestrian activity scenes (2+ pedestrians)
  - `5: SMOKE_FIRE_LIKE`: Real fire & wildfire smoke images
  - `6: UNKNOWN`: 0 samples in dataset
- **Evaluation Split**: Deterministic 70% Train, 15% Validation, 15% Test partitioned by image filename.

## Safety Architecture
The model detects visual presence evidence only (e.g. person detected, flame visual, multi-person activity).
Per SafeGuardian Core Safety Policy:
- A single frame visual detection **never** triggers an active emergency directly.
- All visual evidence feeds into multimodal risk reasoning with sensor verification.

## Pipeline Structure
- `prepare_dataset.py`: Ingests real scene images, background crops, and fire imagery, normalizes to [0, 1] 224x224 RGB tensors.
- `train.py`: Trains a lightweight Depthwise Separable CNN with deterministic seed.
- `evaluate.py`: Evaluates test accuracy, macro F1, per-class metrics, and confusion matrix on the held-out test split.
- `export_tflite.py`: Exports Float32 reference model and full INT8 quantized model using representative calibration.

## Execution
```bash
python3 ml/vision/prepare_dataset.py
python3 ml/vision/train.py
python3 ml/vision/evaluate.py
python3 ml/vision/export_tflite.py
```

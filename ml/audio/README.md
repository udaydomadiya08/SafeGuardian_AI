# SafeGuardian Audio ML Pipeline

This directory contains the complete offline training and quantization pipeline for the on-device audio model (`audio_model.tflite`).

## Dataset
- **Name**: ESC-50: Dataset for Environmental Sound Classification
- **Official URL**: https://github.com/karoldvl/ESC-50
- **License**: CC BY-NC 3.0
- **Authors**: Karol J. Piczak
- **Audio Specs**: 16 kHz mono 16-bit PCM processed with 16-band log-Mel filterbank.
- **Classes Mapped**:
  - `0: NORMAL`: `breathing`, `footsteps`, `snoring`, `drinking_sipping`, `clock_tick`
  - `1: SHOUT`: `crying_baby`
  - `2: DISTRESS_LIKE`: `screaming`, `siren`
  - `3: IMPACT_SOUND`: `door_wood_knock`, `car_horn`
  - `4: GLASS_BREAK_LIKE`: `glass_breaking`
  - `5: ALARM_LIKE`: `siren`, `clock_alarm`
  - `6: UNKNOWN`: `rain`, `sea_waves`, `wind`, `crackling_fire`, `coughing`
- **Evaluation Split**: Standard 5-fold cross-validation scheme. Folds 1-3 for training, Fold 4 for validation, Fold 5 for held-out testing.

## Pipeline Structure
- `prepare_dataset.py`: Ingests real audio from ESC-50, resamples to 16 kHz, computes 16-band log-mel energy tensors matching Android `AudioFeatureExtractor.kt`.
- `train.py`: Trains a compact depthwise separable 2D CNN with deterministic seed.
- `evaluate.py`: Evaluates test accuracy, macro F1, per-class metrics, and confusion matrix on Fold 5.
- `export_tflite.py`: Exports Float32 reference model and full INT8 quantized model using representative calibration.

## Execution
```bash
python3 ml/audio/prepare_dataset.py
python3 ml/audio/train.py
python3 ml/audio/evaluate.py
python3 ml/audio/export_tflite.py
```

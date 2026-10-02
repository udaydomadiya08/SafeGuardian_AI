# SafeGuardian Offline Tiny ML Pipeline

This directory contains the complete, reproducible Python ML pipeline for training, evaluating, and exporting INT8 quantized TFLite models for SafeGuardian:

- `motion_model.tflite` (1D Temporal CNN / MLP, input `[1, 16]`, output `[1, 4]`)
- `audio_model.tflite` (Separable 2D CNN, input `[1, 16, 16]`, output `[1, 7]`)
- `vision_model.tflite` (MobileNetV3-Small INT8, input `[1, 224, 224, 3]`, output `[1, 7]`)

## Pipeline Architecture
1. **Public Dataset Ingestion**: Only documented public datasets (UCI HAR, ESC-50 subset, UR Fall/Image subsets) with clear licenses are ingested.
2. **Deterministic Preprocessing**: Sensor, audio, and visual features match Android extraction logic 1:1.
3. **Subject-level Splitting**: For human activity data, participants are partitioned strictly across train/val/test to prevent subject identity leakage.
4. **Quantization**: Full integer INT8 quantization with representative dataset calibration.
5. **Cross-Platform Golden Tests**: Strict numerical tolerance checks comparing Python inference against on-device TFLite outputs.

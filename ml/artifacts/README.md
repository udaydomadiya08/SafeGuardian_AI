# SafeGuardian ML Artifacts

This directory contains verified, reproducible model weights, evaluation metrics, audits, and validation utilities produced by the Phase 7 offline pipeline.

## Trained Model Artifacts
- `motion_model.tflite` (5,072 bytes, INT8 quantized, input `[1, 16]`, output `[1, 4]`)
- `motion_model_float32.tflite` (7,396 bytes, Float32 reference)
- `audio_model.tflite` (8,472 bytes, INT8 quantized, input `[1, 16, 16]`, output `[1, 7]`)
- `audio_model_float32.tflite` (9,744 bytes, Float32 reference)
- `vision_model.tflite` (15,872 bytes, INT8 quantized, input `[1, 224, 224, 3]`, output `[1, 7]`)
- `vision_model_float32.tflite` (28,156 bytes, Float32 reference)

## Evaluation & Audit Logs
- `motion_dataset_audit.json`: Ingestion audit for UCI HAR dataset (30 subjects, zero subject leakage).
- `motion_evaluation_metrics.json`: Accuracy, precision, recall, F1, confusion matrix on 9 held-out subjects.
- `audio_dataset_audit.json`: Ingestion audit for ESC-50 dataset across 5 folds.
- `audio_evaluation_metrics.json`: Metrics on held-out Fold 5.
- `vision_dataset_audit.json`: Ingestion audit for PennFudanPed and fire imagery.
- `vision_evaluation_metrics.json`: Metrics on held-out test split.
- `golden_test_results.json`: Cross-platform golden tests comparing Keras, Float32 TFLite, and INT8 TFLite outputs.
- `validation_summary.json`: Validation results confirming shapes, finiteness, and latency.

## Verification
To validate all artifacts:
```bash
python3 ml/artifacts/validate_tflite_models.py
python3 ml/common/golden_tests.py
```

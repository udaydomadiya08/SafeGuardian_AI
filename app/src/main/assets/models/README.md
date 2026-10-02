# SafeGuardian Tiny On-Device AI Models

This directory is designated for quantized, INT8 on-device model artifacts:

- `motion_model.tflite`
  Input: Temporal window of accelerometer + gyroscope features `[1, 16]` or `[1, 32, 6]` (approx 12-25 KB)
  Architecture: 1D Temporal CNN / Quantized INT8 MLP
  Output: Categorical probabilities: `[NORMAL, FALL, IMPACT, UNUSUAL_MOTION]`

- `audio_model.tflite`
  Input: Log-mel spectrogram tensor `[1, 16, 16]` or compact filterbank representation (approx 45-80 KB)
  Architecture: Depthwise separable 2D CNN / MobileNet-style audio classifier
  Output: Probabilities: `[NORMAL, SHOUT, DISTRESS_LIKE, IMPACT_SOUND, GLASS_BREAK_LIKE, ALARM_LIKE, UNKNOWN]`

- `vision_model.tflite`
  Input: Normalized RGB frame `[1, 224, 224, 3]` (approx 400-800 KB INT8 quantized MobileNetV3-Small)
  Architecture: MobileNetV3-Small / EfficientNet-Lite
  Output: Visual categories: `[NO_RELEVANT_EVENT, PERSON, PERSON_ON_GROUND, FALL_LIKE_POSTURE, MULTI_PERSON_ACTIVITY, SMOKE_FIRE_LIKE, UNKNOWN]`

## Model Artifact Integrity Policy
Per SafeGuardian Core Safety & Privacy Engineering guidelines:
- If genuine, verified model weights are not present, NO fake `.tflite` files or corrupted binaries are created.
- The `ModelArtifactProvider` gracefully reports `MODEL_NOT_AVAILABLE`.
- The runtime automatically and transparently falls back to the tested heuristic prototype detectors (`PrototypeAudioClassifier`, `PrototypeVisionClassifier`, `MotionAnomalyDetector`).
- The developer dashboard explicitly and honestly displays `PROTOTYPE FALLBACK` or `REAL MODEL`.

# SafeGuardian Motion ML Pipeline

This directory contains the complete offline training and quantization pipeline for the on-device motion model (`motion_model.tflite`).

## Dataset
- **Name**: UCI Human Activity Recognition Using Smartphones (UCI HAR)
- **Official URL**: https://archive.ics.uci.edu/dataset/240/human+activity+recognition+using+smartphones
- **License**: CC BY 4.0
- **Sensors**: Triaxial Accelerometer + Triaxial Gyroscope at 50 Hz.
- **Subjects**: 30 volunteers, partitioned strictly across train (21) and test (9) to ensure zero subject identity leakage.
- **Input Features**: 16-element normalized feature vector:
  `[peakAccel, meanAccel, accelVariance, jerk, signalEnergy, suddenImpulse, motionIntensity, currentAccel, peakGyro, meanGyro, gyroVariance, rotationalChange, rotationalEnergy, gravityNormalizedAccel, orientationChange, immobilityDuration]`
- **Target Output**: 4 classes: `[NORMAL, FALL, IMPACT, UNUSUAL_MOTION]`

## Pipeline Structure
- `prepare_dataset.py`: Ingests raw inertial sensor signals from UCI HAR, extracts 16 features, and sets up subject-level splits.
- `train.py`: Trains a lightweight 1D temporal CNN with Adam optimizer and deterministic seeds.
- `evaluate.py`: Evaluates test accuracy, macro F1, per-class metrics, and confusion matrix on held-out subjects.
- `export_tflite.py`: Exports Float32 reference model and full INT8 quantized model using representative calibration.

## Execution
```bash
python3 ml/motion/prepare_dataset.py
python3 ml/motion/train.py
python3 ml/motion/evaluate.py
python3 ml/motion/export_tflite.py
```

"""
UCI HAR Dataset Ingestion and Preprocessing for SafeGuardian MotionMLModel.

Dataset Documentation:
- Name: Human Activity Recognition Using Smartphones Dataset (UCI HAR)
- Official URL: https://archive.ics.uci.edu/dataset/240/human+activity+recognition+using+smartphones
- License: Creative Commons Attribution 4.0 International (CC BY 4.0)
- Subjects: 30 volunteers (19-48 years) partitioned into 21 train subjects and 9 test subjects.
- Sensors: Triaxial accelerometer and triaxial gyroscope sampled at 50 Hz.
- Window Size: 128 readings (2.56 sec) with 50% overlap.
- Preprocessing: Converts raw 3-axis acceleration and gyroscope series into the exact 16-element
  normalized feature vector expected by Android MotionMLModel.
- Subject Leakage Prevention: Train/test split is strictly partitioned by subject ID.
- Classes:
  0: NORMAL (Walking, Sitting, Standing, Laying)
  1: FALL (0 instances in UCI HAR; honestly reported)
  2: IMPACT (0 instances in UCI HAR; honestly reported)
  3: UNUSUAL_MOTION (Walking Upstairs, Walking Downstairs)
"""

import os
import urllib.request
import zipfile
import numpy as np

DATASET_URL = "https://archive.ics.uci.edu/static/public/240/human+activity+recognition+using+smartphones.zip"
DOWNLOAD_DIR = "ml/data/uci_har"

def download_and_extract_uci_har():
    os.makedirs(DOWNLOAD_DIR, exist_ok=True)
    extracted_marker = os.path.join(DOWNLOAD_DIR, "UCI HAR Dataset")
    zip_path = os.path.join(DOWNLOAD_DIR, "uci_har.zip")
    
    if os.path.exists(extracted_marker):
        print("UCI HAR Dataset already present.")
        return
        
    if not os.path.exists(zip_path):
        print(f"Downloading UCI HAR Dataset from {DATASET_URL}...")
        urllib.request.urlretrieve(DATASET_URL, zip_path)
        
    print("Extracting UCI HAR archive...")
    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
        zip_ref.extractall(DOWNLOAD_DIR)
        
    inner_zip = os.path.join(DOWNLOAD_DIR, "UCI HAR Dataset.zip")
    if os.path.exists(inner_zip) and not os.path.exists(extracted_marker):
        with zipfile.ZipFile(inner_zip, 'r') as zip_ref:
            zip_ref.extractall(DOWNLOAD_DIR)
    print("UCI HAR extraction complete.")

def compute_16_features(acc_x, acc_y, acc_z, gyro_x, gyro_y, gyro_z):
    """
    Computes 16-element normalized feature vector matching Android MotionMLModel:
    [0] peakAccelMagnitude / 50.0f
    [1] meanAccelMagnitude / 30.0f
    [2] accelVariance / 50.0f
    [3] accelDeltaMagnitude / 100.0f
    [4] signalEnergy / 500.0f
    [5] suddenImpulse / 50.0f
    [6] motionIntensity
    [7] currentAccelMagnitude / 30.0f
    [8] peakGyroMagnitude / 20.0f
    [9] meanGyroMagnitude / 15.0f
    [10] gyroVariance / 20.0f
    [11] rotationalChange / 25.0f
    [12] rotationalEnergy / 100.0f
    [13] gravityNormalizedAccel / 20.0f
    [14] orientationChange / 10.0f
    [15] immobilityDuration / 10.0f
    """
    n_windows = acc_x.shape[0]
    features = np.zeros((n_windows, 16), dtype=np.float32)
    
    # UCI HAR total_acc is in standard gravity 'g' (9.80665 m/s^2)
    G = 9.80665
    ax = acc_x * G
    ay = acc_y * G
    az = acc_z * G
    
    # Gyro is in rad/s
    gx = gyro_x
    gy = gyro_y
    gz = gyro_z
    
    for i in range(n_windows):
        amag = np.sqrt(ax[i]**2 + ay[i]**2 + az[i]**2)
        gmag = np.sqrt(gx[i]**2 + gy[i]**2 + gz[i]**2)
        
        peak_acc = np.max(amag)
        mean_acc = np.mean(amag)
        var_acc = np.var(amag)
        
        diff_acc = np.abs(np.diff(amag))
        jerk = np.max(diff_acc) * 50.0 if len(diff_acc) > 0 else 0.0 # 50 Hz sampling
        
        energy_acc = np.mean(amag**2)
        impulse = np.max(diff_acc) if len(diff_acc) > 0 else 0.0
        intensity = min(1.0, float(var_acc / 10.0))
        curr_acc = amag[-1]
        
        peak_gyro = np.max(gmag)
        mean_gyro = np.mean(gmag)
        var_gyro = np.var(gmag)
        
        diff_gyro = np.abs(np.diff(gmag))
        rot_change = np.max(diff_gyro) if len(diff_gyro) > 0 else 0.0
        energy_gyro = np.mean(gmag**2)
        
        grav_norm_acc = abs(mean_acc - G)
        orient_change = np.sum(diff_gyro) / 50.0 if len(diff_gyro) > 0 else 0.0
        
        # Count quiet stationary samples (< 1.5 m/s^2 variance or near 1g)
        immobile_samples = np.sum(np.abs(amag - G) < 0.5)
        immobility_sec = immobile_samples / 50.0
        
        # Apply exact Android normalization
        features[i, 0] = np.clip(peak_acc / 50.0, 0.0, 1.0)
        features[i, 1] = np.clip(mean_acc / 30.0, 0.0, 1.0)
        features[i, 2] = np.clip(var_acc / 50.0, 0.0, 1.0)
        features[i, 3] = np.clip(jerk / 100.0, 0.0, 1.0)
        features[i, 4] = np.clip(energy_acc / 500.0, 0.0, 1.0)
        features[i, 5] = np.clip(impulse / 50.0, 0.0, 1.0)
        features[i, 6] = np.clip(intensity, 0.0, 1.0)
        features[i, 7] = np.clip(curr_acc / 30.0, 0.0, 1.0)
        features[i, 8] = np.clip(peak_gyro / 20.0, 0.0, 1.0)
        features[i, 9] = np.clip(mean_gyro / 15.0, 0.0, 1.0)
        features[i, 10] = np.clip(var_gyro / 20.0, 0.0, 1.0)
        features[i, 11] = np.clip(rot_change / 25.0, 0.0, 1.0)
        features[i, 12] = np.clip(energy_gyro / 100.0, 0.0, 1.0)
        features[i, 13] = np.clip(grav_norm_acc / 20.0, 0.0, 1.0)
        features[i, 14] = np.clip(orient_change / 10.0, 0.0, 1.0)
        features[i, 15] = np.clip(immobility_sec / 10.0, 0.0, 1.0)
        
    return features

def load_split_inertial(split="train"):
    base_dir = os.path.join(DOWNLOAD_DIR, "UCI HAR Dataset", split)
    sig_dir = os.path.join(base_dir, "Inertial Signals")
    
    print(f"Loading {split} inertial signals...")
    ax = np.loadtxt(os.path.join(sig_dir, f"total_acc_x_{split}.txt"))
    ay = np.loadtxt(os.path.join(sig_dir, f"total_acc_y_{split}.txt"))
    az = np.loadtxt(os.path.join(sig_dir, f"total_acc_z_{split}.txt"))
    
    gx = np.loadtxt(os.path.join(sig_dir, f"body_gyro_x_{split}.txt"))
    gy = np.loadtxt(os.path.join(sig_dir, f"body_gyro_y_{split}.txt"))
    gz = np.loadtxt(os.path.join(sig_dir, f"body_gyro_z_{split}.txt"))
    
    y = np.loadtxt(os.path.join(base_dir, f"y_{split}.txt")).astype(int) # 1 to 6
    sub = np.loadtxt(os.path.join(base_dir, f"subject_{split}.txt")).astype(int)
    
    # Map UCI classes (1:WALKING, 2:UPSTAIRS, 3:DOWNSTAIRS, 4:SITTING, 5:STANDING, 6:LAYING)
    # 0: NORMAL (Walking, Sitting, Standing, Laying)
    # 3: UNUSUAL_MOTION (Walking Upstairs, Walking Downstairs)
    # 1: FALL (0 in UCI HAR)
    # 2: IMPACT (0 in UCI HAR)
    y_mapped = np.zeros(len(y), dtype=int)
    y_mapped[(y == 2) | (y == 3)] = 3
    
    x_16 = compute_16_features(ax, ay, az, gx, gy, gz)
    return x_16, y_mapped, sub

def prepare_motion_dataset():
    download_and_extract_uci_har()
    train_x, train_y, train_sub = load_split_inertial("train")
    test_x, test_y, test_sub = load_split_inertial("test")
    
    # Split train into train and validation by subject to prevent subject leakage
    unique_train_subs = np.unique(train_sub)
    val_subs = unique_train_subs[:4] # 4 subjects for validation (~19%)
    
    val_mask = np.isin(train_sub, val_subs)
    train_mask = ~val_mask
    
    tr_x, tr_y, tr_sub = train_x[train_mask], train_y[train_mask], train_sub[train_mask]
    va_x, va_y, va_sub = train_x[val_mask], train_y[val_mask], train_sub[val_mask]
    
    return (tr_x, tr_y, tr_sub), (va_x, va_y, va_sub), (test_x, test_y, test_sub)

if __name__ == "__main__":
    (tr_x, tr_y, tr_sub), (va_x, va_y, va_sub), (te_x, te_y, te_sub) = prepare_motion_dataset()
    print(f"Motion dataset prepared successfully:")
    print(f"Train: {tr_x.shape}, subjects: {np.unique(tr_sub).tolist()}")
    print(f"Val:   {va_x.shape}, subjects: {np.unique(va_sub).tolist()}")
    print(f"Test:  {te_x.shape}, subjects: {np.unique(te_sub).tolist()}")

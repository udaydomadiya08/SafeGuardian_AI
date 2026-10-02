"""
Phase 8: MobiAct Dataset Ingestion for SafeGuardian MotionMLModel.

Dataset Documentation:
- Name: MobiFall / MobiAct Dataset v2.0
- Source: https://github.com/yehowlong/MobiAct_Dataset_v2.0-MobiFall_Dataset_v2.0
- Original Authors: Vavoulas et al., Hellenic Mediterranean University (BMI Lab)
- License: Research / Academic Use
- Sensors: Samsung Galaxy S3 smartphone
  - Accelerometer: timestamp(ns), x, y, z (m/s^2) including gravity
  - Gyroscope: timestamp(ns), x, y, z (rad/s)
  - Sampling rate: ~80-90 Hz (variable)
- Subjects: 24 (sub1-sub21, sub29-sub31)
  - 9 subjects have both ADL and Falls (sub2-sub5, sub7-sub11)
  - 15 subjects have Falls only (sub1, sub6, sub12-sub21, sub29-sub31)
- Fall types (4):
  FOL: Fall forward lying (hands dampen)
  FKL: Fall forward knees-lying (knees first)
  BSC: Fall backward while sitting on chair
  SDL: Fall sideward lying (bending legs)
- ADL types (9):
  STD: Standing (5 min)
  WAL: Walking (5 min)
  JOG: Jogging (30s x3)
  JUM: Jumping (30s x3)
  STU: Stairs up (10s x6)
  STN: Stairs down (10s x6)
  SCH: Sit on chair (6s x6)
  CSI: Car step in (6s x6)
  CSO: Car step out (6s x6)

Label Mapping (conservative):
  0: NORMAL   <- STD, WAL, SCH, CSI, CSO, STU, STN (everyday activities)
  1: FALL     <- FOL, FKL, BSC, SDL (real recorded human falls)
  2: IMPACT   <- NOT USED (0 samples; no separate impact annotation exists.
                 The heuristic detector handles impact detection.)
  3: UNUSUAL_MOTION <- JOG, JUM (vigorous but non-emergency motion)

Subject-Level Split (NO leakage):
  Train: subjects with both ADL+Falls for training balance
  Val: held-out subjects
  Test: held-out subjects (completely untouched)
"""

import os
import glob
import numpy as np

MOBIACT_DIR = "ml/data/mobiact"
WINDOW_SIZE = 128  # ~1.5s at 85 Hz, matches UCI HAR window concept
WINDOW_STEP = 64   # 50% overlap

# Label mapping
FALL_CODES = {"FOL", "FKL", "BSC", "SDL"}
NORMAL_CODES = {"STD", "WAL", "SCH", "CSI", "CSO", "STU", "STN"}
UNUSUAL_CODES = {"JOG", "JUM"}

LABEL_NORMAL = 0
LABEL_FALL = 1
LABEL_IMPACT = 2  # 0 samples - documented honestly
LABEL_UNUSUAL = 3


def parse_mobiact_file(filepath):
    """Parse a MobiAct sensor file. Returns Nx4 array [timestamp, x, y, z]."""
    rows = []
    data_started = False
    with open(filepath, 'r') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            if line.startswith('#') or line.startswith('@DATA'):
                if line.startswith('@DATA'):
                    data_started = True
                continue
            if not data_started:
                # Some files don't have @DATA marker
                if line[0].isdigit() or line[0] == '-':
                    data_started = True
                else:
                    continue
            if not data_started:
                continue
            parts = line.replace('\r', '').split(',')
            if len(parts) >= 4:
                try:
                    ts = float(parts[0].strip())
                    x = float(parts[1].strip())
                    y = float(parts[2].strip())
                    z = float(parts[3].strip())
                    rows.append([ts, x, y, z])
                except (ValueError, IndexError):
                    continue
    if len(rows) == 0:
        return None
    return np.array(rows, dtype=np.float64)


def resample_to_fixed_rate(data, target_hz=50):
    """Resample variable-rate sensor data to fixed rate using linear interpolation."""
    if data is None or len(data) < 10:
        return None
    timestamps = data[:, 0]
    duration_ns = timestamps[-1] - timestamps[0]
    if duration_ns <= 0:
        return None
    duration_s = duration_ns * 1e-9
    n_samples = int(duration_s * target_hz)
    if n_samples < WINDOW_SIZE:
        return None
    
    new_ts = np.linspace(timestamps[0], timestamps[-1], n_samples)
    resampled = np.zeros((n_samples, 3), dtype=np.float32)
    for col in range(3):
        resampled[:, col] = np.interp(new_ts, timestamps, data[:, col + 1])
    return resampled


def compute_16_features(acc_windows, gyro_windows):
    """
    Compute 16-element normalized feature vector matching Android MotionMLModel.
    acc_windows: (N, window_size, 3) in m/s^2
    gyro_windows: (N, window_size, 3) in rad/s
    Returns: (N, 16) float32
    """
    n = acc_windows.shape[0]
    features = np.zeros((n, 16), dtype=np.float32)
    G = 9.80665
    
    for i in range(n):
        ax, ay, az = acc_windows[i, :, 0], acc_windows[i, :, 1], acc_windows[i, :, 2]
        gx, gy, gz = gyro_windows[i, :, 0], gyro_windows[i, :, 1], gyro_windows[i, :, 2]
        
        amag = np.sqrt(ax**2 + ay**2 + az**2)
        gmag = np.sqrt(gx**2 + gy**2 + gz**2)
        
        peak_acc = np.max(amag)
        mean_acc = np.mean(amag)
        var_acc = np.var(amag)
        
        diff_acc = np.abs(np.diff(amag))
        jerk = np.max(diff_acc) * 50.0 if len(diff_acc) > 0 else 0.0
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
        
        immobile_samples = np.sum(np.abs(amag - G) < 0.5)
        immobility_sec = immobile_samples / 50.0
        
        # Exact Android normalization from MotionMLModel.kt
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


def extract_windows(acc_data, gyro_data, window_size=WINDOW_SIZE, step=WINDOW_STEP):
    """Slide windows over resampled acc+gyro data."""
    n = min(len(acc_data), len(gyro_data))
    if n < window_size:
        return np.empty((0, window_size, 3)), np.empty((0, window_size, 3))
    
    acc_wins = []
    gyro_wins = []
    for start in range(0, n - window_size + 1, step):
        acc_wins.append(acc_data[start:start+window_size])
        gyro_wins.append(gyro_data[start:start+window_size])
    
    return np.array(acc_wins, dtype=np.float32), np.array(gyro_wins, dtype=np.float32)


def load_subject_recordings(sub_dir, sub_id):
    """Load all acc+gyro recording pairs for a subject, return (features, labels, sub_ids)."""
    all_features = []
    all_labels = []
    all_subs = []
    
    # Process FALLS
    falls_dir = os.path.join(sub_dir, "FALLS")
    if os.path.isdir(falls_dir):
        for fall_type in FALL_CODES:
            type_dir = os.path.join(falls_dir, fall_type)
            if not os.path.isdir(type_dir):
                continue
            acc_files = sorted(glob.glob(os.path.join(type_dir, f"{fall_type}_acc_{sub_id}_*.txt")))
            for acc_file in acc_files:
                trial = os.path.basename(acc_file).split('_')[-1].replace('.txt', '')
                gyro_file = os.path.join(type_dir, f"{fall_type}_gyro_{sub_id}_{trial}.txt")
                if not os.path.exists(gyro_file):
                    continue
                
                acc_raw = parse_mobiact_file(acc_file)
                gyro_raw = parse_mobiact_file(gyro_file)
                if acc_raw is None or gyro_raw is None:
                    continue
                
                acc_resampled = resample_to_fixed_rate(acc_raw, target_hz=50)
                gyro_resampled = resample_to_fixed_rate(gyro_raw, target_hz=50)
                if acc_resampled is None or gyro_resampled is None:
                    continue
                
                acc_wins, gyro_wins = extract_windows(acc_resampled, gyro_resampled)
                if len(acc_wins) == 0:
                    continue
                
                feats = compute_16_features(acc_wins, gyro_wins)
                all_features.append(feats)
                all_labels.append(np.full(len(feats), LABEL_FALL, dtype=int))
                all_subs.append(np.full(len(feats), sub_id, dtype=int))
    
    # Process ADL
    adl_dir = os.path.join(sub_dir, "ADL")
    if os.path.isdir(adl_dir):
        for act_code in list(NORMAL_CODES) + list(UNUSUAL_CODES):
            act_dir = os.path.join(adl_dir, act_code)
            if not os.path.isdir(act_dir):
                continue
            
            label = LABEL_NORMAL if act_code in NORMAL_CODES else LABEL_UNUSUAL
            
            acc_files = sorted(glob.glob(os.path.join(act_dir, f"{act_code}_acc_{sub_id}_*.txt")))
            for acc_file in acc_files:
                trial = os.path.basename(acc_file).split('_')[-1].replace('.txt', '')
                gyro_file = os.path.join(act_dir, f"{act_code}_gyro_{sub_id}_{trial}.txt")
                if not os.path.exists(gyro_file):
                    continue
                
                acc_raw = parse_mobiact_file(acc_file)
                gyro_raw = parse_mobiact_file(gyro_file)
                if acc_raw is None or gyro_raw is None:
                    continue
                
                acc_resampled = resample_to_fixed_rate(acc_raw, target_hz=50)
                gyro_resampled = resample_to_fixed_rate(gyro_raw, target_hz=50)
                if acc_resampled is None or gyro_resampled is None:
                    continue
                
                acc_wins, gyro_wins = extract_windows(acc_resampled, gyro_resampled)
                if len(acc_wins) == 0:
                    continue
                
                feats = compute_16_features(acc_wins, gyro_wins)
                all_features.append(feats)
                all_labels.append(np.full(len(feats), label, dtype=int))
                all_subs.append(np.full(len(feats), sub_id, dtype=int))
    
    if len(all_features) == 0:
        return np.empty((0, 16)), np.empty(0, dtype=int), np.empty(0, dtype=int)
    
    return (np.concatenate(all_features),
            np.concatenate(all_labels),
            np.concatenate(all_subs))


def prepare_mobiact_dataset():
    """Full MobiAct dataset preparation with subject-level split."""
    print("=" * 60)
    print("Phase 8: MobiAct Fall Dataset Preparation")
    print("=" * 60)
    
    # Discover subjects
    sub_dirs = sorted([d for d in os.listdir(MOBIACT_DIR)
                       if d.startswith('sub') and os.path.isdir(os.path.join(MOBIACT_DIR, d))])
    
    sub_ids = []
    for s in sub_dirs:
        sid = int(s.replace('sub', ''))
        sub_ids.append(sid)
    
    # Subjects with both ADL and Falls (needed for balanced train)
    subs_with_adl = []
    subs_falls_only = []
    for s in sub_dirs:
        sid = int(s.replace('sub', ''))
        has_adl = os.path.isdir(os.path.join(MOBIACT_DIR, s, "ADL"))
        if has_adl:
            subs_with_adl.append(sid)
        else:
            subs_falls_only.append(sid)
    
    print(f"Subjects with ADL+Falls: {sorted(subs_with_adl)}")
    print(f"Subjects with Falls only: {sorted(subs_falls_only)}")
    
    # Subject-level split:
    # Train: subs with ADL+Falls (for balanced training) + some falls-only
    # Val: held-out subjects
    # Test: completely held-out subjects
    
    # ADL subjects: [2,3,4,5,7,8,9,10,11] = 9 subjects
    # Falls-only: [1,6,12,13,14,15,16,17,18,19,20,21,29,30,31] = 15 subjects
    
    train_subs = {2, 3, 4, 5, 7, 8, 9,   # 7 ADL subjects
                  1, 6, 12, 13, 14, 15, 16, 17}  # 8 falls-only
    val_subs =   {10, 18, 19, 20}         # 1 ADL + 3 falls-only
    test_subs =  {11, 21, 29, 30, 31}     # 1 ADL + 4 falls-only
    
    # Verify no leakage
    assert len(train_subs & val_subs) == 0, "Train-Val leakage!"
    assert len(train_subs & test_subs) == 0, "Train-Test leakage!"
    assert len(val_subs & test_subs) == 0, "Val-Test leakage!"
    print(f"\nTrain subjects: {sorted(train_subs)}")
    print(f"Val subjects:   {sorted(val_subs)}")
    print(f"Test subjects:  {sorted(test_subs)}")
    print(f"Leakage check:  PASSED (all intersections empty)")
    
    # Load all data
    all_x, all_y, all_s = [], [], []
    for s in sub_dirs:
        sid = int(s.replace('sub', ''))
        sub_path = os.path.join(MOBIACT_DIR, s)
        print(f"  Loading sub{sid}...", end=" ", flush=True)
        x, y, sids = load_subject_recordings(sub_path, sid)
        print(f"{len(x)} windows")
        if len(x) > 0:
            all_x.append(x)
            all_y.append(y)
            all_s.append(sids)
    
    X = np.concatenate(all_x)
    Y = np.concatenate(all_y)
    S = np.concatenate(all_s)
    
    # Split
    train_mask = np.isin(S, list(train_subs))
    val_mask = np.isin(S, list(val_subs))
    test_mask = np.isin(S, list(test_subs))
    
    tr_x, tr_y = X[train_mask], Y[train_mask]
    va_x, va_y = X[val_mask], Y[val_mask]
    te_x, te_y = X[test_mask], Y[test_mask]
    
    # Report
    class_names = ["NORMAL", "FALL", "IMPACT", "UNUSUAL_MOTION"]
    print(f"\n{'='*60}")
    print(f"Dataset Summary:")
    print(f"  Total windows: {len(X)}")
    print(f"  Train: {len(tr_x)}, Val: {len(va_x)}, Test: {len(te_x)}")
    for split_name, sy in [("Train", tr_y), ("Val", va_y), ("Test", te_y)]:
        print(f"\n  {split_name} class distribution:")
        for c in range(4):
            count = np.sum(sy == c)
            pct = 100.0 * count / len(sy) if len(sy) > 0 else 0
            print(f"    {class_names[c]}: {count} ({pct:.1f}%)")
    
    return (tr_x, tr_y), (va_x, va_y), (te_x, te_y)


if __name__ == "__main__":
    (tr_x, tr_y), (va_x, va_y), (te_x, te_y) = prepare_mobiact_dataset()
    print(f"\nFeature shape: {tr_x.shape}")
    print(f"Feature range: [{tr_x.min():.4f}, {tr_x.max():.4f}]")

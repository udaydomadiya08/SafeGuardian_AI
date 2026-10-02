"""
Phase 8: Expanded ESC-50 Audio Dataset Preparation.

Uses ALL 2000 ESC-50 recordings with broader category coverage.

Dataset Documentation:
- Name: ESC-50: Dataset for Environmental Sound Classification
- Official URL: https://github.com/karolpiczak/ESC-50
- License: CC BY-NC 3.0
- Audio: 2000 x 5-second clips at 44.1 kHz
- Categories: 50 classes across 5 major groups

Phase 8 Label Mapping (7 classes):
  0: NORMAL - breathing, footsteps, snoring, drinking_sipping, clock_tick,
              brushing_teeth, mouse_click, keyboard_typing, pouring_water,
              water_drops, washing_machine, vacuum_cleaner, toilet_flush,
              laughing, clapping
  1: SHOUT_LIKE - crying_baby, rooster, crow (high-pitched vocalizations)
  2: DISTRESS_LIKE - siren (emergency-associated sounds)
  3: IMPACT_SOUND - door_wood_knock, door_wood_creaks, can_opening,
                    thunderstorm, fireworks
  4: GLASS_BREAK_LIKE - glass_breaking, chainsaw, hand_saw
  5: ALARM_LIKE - clock_alarm, church_bells, car_horn, engine, helicopter,
                  train, airplane
  6: UNKNOWN - rain, sea_waves, wind, crackling_fire, coughing, sneezing,
               insects, crickets, frog, cat, dog, pig, cow, hen, sheep,
               chirping_birds

Hard Negatives for False Positive Reduction:
  - door_wood_knock (sounds like impact but is NORMAL household)
  - car_horn (sounds alarming but is routine traffic)
  - thunderstorm, fireworks (loud but not emergency)
  - laughing, clapping (vocal but not distress)

Split: ESC-50 predefined 5-fold cross-validation
  Train: Folds 1-3, Val: Fold 4, Test: Fold 5
  No recording appears in multiple folds (leak-free by design).
"""

import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import csv
import wave
import struct
import numpy as np
import urllib.request

META_URL = "https://raw.githubusercontent.com/karolpiczak/ESC-50/master/meta/esc50.csv"
AUDIO_BASE_URL = "https://raw.githubusercontent.com/karolpiczak/ESC-50/master/audio/"
DATA_DIR = "ml/data/esc50"
AUDIO_DIR = os.path.join(DATA_DIR, "audio")
META_FILE = os.path.join(DATA_DIR, "esc50.csv")

TARGET_SR = 16000
MEL_BANDS = 16
MEL_FRAMES = 16

# Expanded category mapping - all 50 ESC-50 categories
CATEGORY_MAP = {
    # 0: NORMAL (everyday household / ambient)
    "breathing": 0, "footsteps": 0, "snoring": 0, "drinking_sipping": 0,
    "clock_tick": 0, "brushing_teeth": 0, "mouse_click": 0,
    "keyboard_typing": 0, "pouring_water": 0, "water_drops": 0,
    "washing_machine": 0, "vacuum_cleaner": 0, "toilet_flush": 0,
    "laughing": 0, "clapping": 0,
    
    # 1: SHOUT_LIKE (high-pitched vocalizations)
    "crying_baby": 1, "rooster": 1, "crow": 1,
    
    # 2: DISTRESS_LIKE (emergency-associated)
    "siren": 2,
    
    # 3: IMPACT_SOUND (percussive / impact events)
    "door_wood_knock": 3, "door_wood_creaks": 3, "can_opening": 3,
    "thunderstorm": 3, "fireworks": 3,
    
    # 4: GLASS_BREAK_LIKE (sharp/shatter sounds)
    "glass_breaking": 4, "chainsaw": 4, "hand_saw": 4,
    
    # 5: ALARM_LIKE (mechanical/vehicle alerts)
    "clock_alarm": 5, "church_bells": 5, "car_horn": 5,
    "engine": 5, "helicopter": 5, "train": 5, "airplane": 5,
    
    # 6: UNKNOWN (nature / animals / misc)
    "rain": 6, "sea_waves": 6, "wind": 6, "crackling_fire": 6,
    "coughing": 6, "sneezing": 6, "insects": 6, "crickets": 6,
    "frog": 6, "cat": 6, "dog": 6, "pig": 6, "cow": 6,
    "hen": 6, "sheep": 6, "chirping_birds": 6,
}


def download_meta():
    """Download ESC-50 metadata CSV."""
    os.makedirs(DATA_DIR, exist_ok=True)
    if not os.path.exists(META_FILE):
        print("Downloading ESC-50 metadata...")
        urllib.request.urlretrieve(META_URL, META_FILE)
    return META_FILE


def read_wav_as_int16(filepath):
    """Read WAV file and return (samples_int16, sample_rate)."""
    with wave.open(filepath, 'r') as wf:
        n_channels = wf.getnchannels()
        sample_width = wf.getsampwidth()
        sr = wf.getframerate()
        n_frames = wf.getnframes()
        raw = wf.readframes(n_frames)
    
    if sample_width == 2:
        samples = np.array(struct.unpack(f'<{len(raw)//2}h', raw), dtype=np.float32)
    elif sample_width == 4:
        samples = np.array(struct.unpack(f'<{len(raw)//4}i', raw), dtype=np.float32)
        samples = samples / 32768.0  # normalize 32-bit to 16-bit range
    else:
        samples = np.array(struct.unpack(f'<{len(raw)}B', raw), dtype=np.float32) - 128.0
        samples = samples * 256.0
    
    # Downmix stereo
    if n_channels == 2:
        samples = (samples[0::2] + samples[1::2]) / 2.0
    
    return samples.astype(np.int16), sr


def resample_audio(samples, orig_sr, target_sr):
    """Fast linear interpolation resampling."""
    if orig_sr == target_sr:
        return samples
    ratio = target_sr / orig_sr
    new_len = int(len(samples) * ratio)
    x_old = np.linspace(0, 1, len(samples))
    x_new = np.linspace(0, 1, new_len)
    return np.interp(x_new, x_old, samples.astype(np.float32)).astype(np.int16)


def compute_log_mel_features(samples_int16, sr=TARGET_SR):
    """Compute 16x16 log-mel feature tensor matching Android AudioMLModel."""
    samples = samples_int16.astype(np.float32) / 32768.0
    
    # Ensure exactly 1 second of audio (16000 samples)
    target_len = sr
    if len(samples) > target_len:
        # Take center crop
        start = (len(samples) - target_len) // 2
        samples = samples[start:start + target_len]
    elif len(samples) < target_len:
        samples = np.pad(samples, (0, target_len - len(samples)))
    
    # FFT parameters
    fft_size = 512
    hop_size = fft_size // 2
    
    # Mel filterbank
    n_fft_bins = fft_size // 2 + 1
    mel_low = 0
    mel_high = 2595 * np.log10(1 + (sr / 2) / 700)
    mel_points = np.linspace(mel_low, mel_high, MEL_BANDS + 2)
    hz_points = 700 * (10 ** (mel_points / 2595) - 1)
    bin_points = np.floor((fft_size + 1) * hz_points / sr).astype(int)
    
    filterbank = np.zeros((MEL_BANDS, n_fft_bins))
    for m in range(MEL_BANDS):
        f_left = bin_points[m]
        f_center = bin_points[m + 1]
        f_right = bin_points[m + 2]
        for k in range(f_left, f_center):
            if f_center > f_left:
                filterbank[m, k] = (k - f_left) / (f_center - f_left)
        for k in range(f_center, f_right):
            if f_right > f_center:
                filterbank[m, k] = (f_right - k) / (f_right - f_center)
    
    # STFT
    window = np.hanning(fft_size)
    n_frames_total = 1 + (len(samples) - fft_size) // hop_size
    
    if n_frames_total < 1:
        return np.zeros((MEL_FRAMES, MEL_BANDS), dtype=np.float32)
    
    # Compute mel energies for each frame
    mel_spec = []
    for i in range(n_frames_total):
        start = i * hop_size
        frame = samples[start:start + fft_size] * window
        spectrum = np.abs(np.fft.rfft(frame)) ** 2
        mel_energy = np.dot(filterbank, spectrum)
        mel_energy = np.log(mel_energy + 1e-10)
        mel_spec.append(mel_energy)
    
    mel_spec = np.array(mel_spec)  # (n_frames, 16)
    
    # Subsample/pad to exactly MEL_FRAMES time steps
    if len(mel_spec) >= MEL_FRAMES:
        indices = np.linspace(0, len(mel_spec) - 1, MEL_FRAMES).astype(int)
        tensor = mel_spec[indices]
    else:
        tensor = np.zeros((MEL_FRAMES, MEL_BANDS), dtype=np.float32)
        tensor[:len(mel_spec)] = mel_spec
    
    return tensor.astype(np.float32)


def download_audio_file(filename):
    """Download a single ESC-50 audio file."""
    os.makedirs(AUDIO_DIR, exist_ok=True)
    local_path = os.path.join(AUDIO_DIR, filename)
    if os.path.exists(local_path):
        return local_path
    
    url = AUDIO_BASE_URL + filename
    try:
        urllib.request.urlretrieve(url, local_path)
        return local_path
    except Exception as e:
        return None


def prepare_audio_dataset_v2():
    """Full ESC-50 dataset preparation for Phase 8."""
    print("=" * 60)
    print("Phase 8: ESC-50 Audio Dataset Preparation (Expanded)")
    print("=" * 60)
    
    download_meta()
    
    # Read metadata
    rows = []
    with open(META_FILE, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)
    
    print(f"Total ESC-50 entries: {len(rows)}")
    
    # Download and process
    X_all = []
    Y_all = []
    folds_all = []
    skipped = 0
    
    for i, row in enumerate(rows):
        filename = row['filename']
        category = row['category']
        fold = int(row['fold'])
        
        if category not in CATEGORY_MAP:
            skipped += 1
            continue
        
        label = CATEGORY_MAP[category]
        
        # Download
        audio_path = download_audio_file(filename)
        if audio_path is None:
            skipped += 1
            continue
        
        try:
            samples, sr = read_wav_as_int16(audio_path)
            samples = resample_audio(samples, sr, TARGET_SR)
            mel_tensor = compute_log_mel_features(samples, TARGET_SR)
            
            X_all.append(mel_tensor.flatten())  # 256 elements
            Y_all.append(label)
            folds_all.append(fold)
        except Exception as e:
            skipped += 1
            continue
        
        if (i + 1) % 200 == 0:
            print(f"  Processed {i+1}/{len(rows)}...")
    
    X = np.array(X_all, dtype=np.float32)
    Y = np.array(Y_all, dtype=int)
    folds = np.array(folds_all, dtype=int)
    
    print(f"\nProcessed: {len(X)}, Skipped: {skipped}")
    
    # Split by fold (ESC-50 predefined, no leakage possible)
    train_mask = np.isin(folds, [1, 2, 3])
    val_mask = folds == 4
    test_mask = folds == 5
    
    tr_x, tr_y = X[train_mask], Y[train_mask]
    va_x, va_y = X[val_mask], Y[val_mask]
    te_x, te_y = X[test_mask], Y[test_mask]
    
    class_names = ["NORMAL", "SHOUT_LIKE", "DISTRESS_LIKE", "IMPACT_SOUND",
                   "GLASS_BREAK_LIKE", "ALARM_LIKE", "UNKNOWN"]
    
    print(f"\n{'='*60}")
    print(f"Dataset Summary:")
    print(f"  Total: {len(X)}")
    print(f"  Train: {len(tr_x)}, Val: {len(va_x)}, Test: {len(te_x)}")
    for split_name, sy in [("Train", tr_y), ("Val", va_y), ("Test", te_y)]:
        print(f"\n  {split_name} class distribution:")
        for c in range(7):
            count = np.sum(sy == c)
            pct = 100.0 * count / len(sy) if len(sy) > 0 else 0
            print(f"    {class_names[c]}: {count} ({pct:.1f}%)")
    
    return (tr_x, tr_y), (va_x, va_y), (te_x, te_y)


if __name__ == "__main__":
    (tr_x, tr_y), (va_x, va_y), (te_x, te_y) = prepare_audio_dataset_v2()
    print(f"\nFeature shape: {tr_x.shape}")

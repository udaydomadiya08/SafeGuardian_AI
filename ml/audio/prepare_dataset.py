"""
ESC-50 Real Dataset Ingestion and Preprocessing for SafeGuardian AudioMLModel.

Dataset Documentation:
- Name: ESC-50: Dataset for Environmental Sound Classification
- Official URL: https://github.com/karoldvl/ESC-50
- License: Creative Commons Attribution-NonCommercial 3.0 (CC BY-NC 3.0)
- Authors: Karol J. Piczak
- Audio Format: 5-second 44.1 kHz WAV recordings.
- SafeGuardian 7 Neutral Acoustic Classes:
  0: NORMAL (breathing, footsteps, snoring, drinking_sipping, clock_tick)
  1: SHOUT (crying_baby)
  2: DISTRESS_LIKE (screaming, siren)
  3: IMPACT_SOUND (door_wood_knock, car_horn)
  4: GLASS_BREAK_LIKE (glass_breaking)
  5: ALARM_LIKE (siren, clock_alarm)
  6: UNKNOWN (rain, sea_waves, wind, crackling_fire, coughing)
- Preprocessing:
  - Downmix/resample to 16,000 Hz mono 16-bit PCM.
  - Compute 16-band log-Mel filterbank with Hann window matching Android AudioFeatureExtractor.
  - Produce [16, 16] = 256 float feature tensor matching Android AudioMLModel.
  - Strict 5-fold split: Folds 1-3 for Train, Fold 4 for Validation, Fold 5 for Test.
"""

import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import csv
import io
import urllib.request
import numpy as np
import scipy.signal

META_URL = "https://raw.githubusercontent.com/karoldvl/ESC-50/master/meta/esc50.csv"
AUDIO_BASE_URL = "https://raw.githubusercontent.com/karoldvl/ESC-50/master/audio/"
DATA_DIR = "ml/data/esc50"
AUDIO_DIR = os.path.join(DATA_DIR, "audio")

# Mapping from ESC-50 category name to SafeGuardian 7 classes
CATEGORY_MAP = {
    # 0: NORMAL
    "breathing": 0,
    "footsteps": 0,
    "snoring": 0,
    "drinking_sipping": 0,
    "clock_tick": 0,
    # 1: SHOUT
    "crying_baby": 1,
    # 2: DISTRESS_LIKE
    "screaming": 2,
    # 3: IMPACT_SOUND
    "door_wood_knock": 3,
    "car_horn": 3,
    # 4: GLASS_BREAK_LIKE
    "glass_breaking": 4,
    # 5: ALARM_LIKE
    "siren": 5,
    "clock_alarm": 5,
    # 6: UNKNOWN / OTHER
    "rain": 6,
    "sea_waves": 6,
    "wind": 6,
    "crackling_fire": 6,
    "coughing": 6
}

CLASSES = [
    "NORMAL",
    "SHOUT",
    "DISTRESS_LIKE",
    "IMPACT_SOUND",
    "GLASS_BREAK_LIKE",
    "ALARM_LIKE",
    "UNKNOWN"
]

def init_mel_filterbank(fft_size=512, sample_rate_hz=16000, num_mel_bands=16):
    """Initializes 16-band Mel filterbank triangular weights matching Android AudioFeatureExtractor."""
    half_fft = fft_size // 2
    
    def hz_to_mel(hz):
        return 2595.0 * np.log10(1.0 + hz / 700.0)
        
    def mel_to_hz(mel):
        return 700.0 * (10.0**(mel / 2595.0) - 1.0)
        
    min_mel = hz_to_mel(50.0)
    max_mel = hz_to_mel(sample_rate_hz / 2.0)
    mel_points = np.linspace(min_mel, max_mel, num_mel_bands + 2)
    hz_points = mel_to_hz(mel_points)
    bin_points = np.floor((fft_size + 1) * hz_points / sample_rate_hz).astype(int)
    
    bank = np.zeros((num_mel_bands, half_fft), dtype=np.float32)
    for m in range(num_mel_bands):
        left = bin_points[m]
        center = bin_points[m + 1]
        right = bin_points[m + 2]
        
        for k in range(left, center):
            if k < half_fft and (center - left) > 0:
                bank[m, k] = (k - left) / (center - left)
        for k in range(center, right):
            if k < half_fft and (right - center) > 0:
                bank[m, k] = (right - k) / (right - center)
                
    return bank

MEL_BANK = init_mel_filterbank()

def extract_logmel_tensor(pcm_data, sample_rate_hz=16000, fft_size=512, num_mel_bands=16):
    """
    Extracts 16x16 log-mel energy tensor matching Android AudioMLModel:
    FFT size 512, Hann window, 16 mel bands, ln(1.0 + energy), replicated across 16 temporal bins.
    """
    half_fft = fft_size // 2
    hann = 0.5 * (1.0 - np.cos(2.0 * np.pi * np.arange(fft_size) / (fft_size - 1)))
    
    # If audio is shorter than fft_size, pad with zeros
    if len(pcm_data) < fft_size:
        padded = np.zeros(fft_size, dtype=np.float32)
        padded[:len(pcm_data)] = pcm_data
        pcm_data = padded
        
    # Analyze center frame of the audio
    frame_offset = max(0, min(len(pcm_data) - fft_size, (len(pcm_data) - fft_size) // 2))
    frame = pcm_data[frame_offset : frame_offset + fft_size] * hann
    
    # Compute magnitude spectrum
    spec = np.abs(np.fft.rfft(frame, n=fft_size))[:half_fft]
    
    # Compute 16-band log-Mel energies
    mel_energies = np.zeros(num_mel_bands, dtype=np.float32)
    for m in range(num_mel_bands):
        band_energy = np.sum(spec * MEL_BANK[m])
        mel_energies[m] = np.log(1.0 + band_energy)
        
    # Normalize to [0, 1] range
    max_e = np.max(mel_energies)
    if max_e > 1e-6:
        mel_energies = mel_energies / (max_e + 1e-6)
        
    # Replicate across 16 temporal bins matching Android AudioMLModel.extractMelTensor
    tensor = np.zeros((16, 16), dtype=np.float32)
    for t in range(16):
        tensor[t, :] = mel_energies
        
    return tensor

def download_esc50_metadata():
    os.makedirs(DATA_DIR, exist_ok=True)
    meta_path = os.path.join(DATA_DIR, "esc50.csv")
    if not os.path.exists(meta_path):
        print("Downloading ESC-50 metadata from GitHub...")
        urllib.request.urlretrieve(META_URL, meta_path)
    return meta_path

def load_audio_file(file_path):
    """Loads a WAV file and resamples to 16 kHz mono normalized float array."""
    import wave
    with wave.open(file_path, 'rb') as w:
        n_channels = w.getnchannels()
        sampwidth = w.getsampwidth()
        framerate = w.getframerate()
        n_frames = w.getnframes()
        frames = w.readframes(n_frames)
        
    if sampwidth == 2:
        audio = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
    else:
        audio = np.frombuffer(frames, dtype=np.uint8).astype(np.float32) / 128.0 - 1.0
        
    if n_channels > 1:
        audio = audio.reshape(-1, n_channels).mean(axis=1)
        
    if framerate != 16000:
        target_len = int(len(audio) * 16000 / framerate)
        audio = np.interp(np.linspace(0, len(audio) - 1, target_len), np.arange(len(audio)), audio).astype(np.float32)
        
    return audio

def prepare_audio_dataset(samples_per_fold_per_cat=2):
    """
    Downloads and prepares real ESC-50 audio samples for SafeGuardian classes.
    Uses ESC-50 5-fold cross-validation standard:
    - Train: Folds 1, 2, 3
    - Val:   Fold 4
    - Test:  Fold 5
    """
    meta_path = download_esc50_metadata()
    os.makedirs(AUDIO_DIR, exist_ok=True)
    
    with open(meta_path, 'r', encoding='utf-8') as f:
        reader = list(csv.DictReader(f))
        
    # Group samples by (category, fold)
    fold_cat_counts = {}
    samples_to_download = []
    
    for row in reader:
        cat = row['category']
        fold = int(row['fold'])
        if cat in CATEGORY_MAP:
            key = (cat, fold)
            count = fold_cat_counts.get(key, 0)
            if count < samples_per_fold_per_cat:
                samples_to_download.append(row)
                fold_cat_counts[key] = count + 1
                
    print(f"Selected {len(samples_to_download)} real audio samples across all 5 folds.")
    
    train_x, train_y, train_folds = [], [], []
    val_x, val_y, val_folds = [], [], []
    test_x, test_y, test_folds = [], [], []
    
    for idx, row in enumerate(samples_to_download):
        filename = row['filename']
        category = row['category']
        fold = int(row['fold'])
        target_class = CATEGORY_MAP[category]
        local_path = os.path.join(AUDIO_DIR, filename)
        
        if not os.path.exists(local_path):
            file_url = AUDIO_BASE_URL + filename
            try:
                urllib.request.urlretrieve(file_url, local_path)
            except Exception as e:
                print(f"Warning: Failed to download {filename}: {e}")
                continue
                
        pcm = load_audio_file(local_path)
        logmel = extract_logmel_tensor(pcm)
        
        if fold in [1, 2, 3]:
            train_x.append(logmel)
            train_y.append(target_class)
            train_folds.append(fold)
        elif fold == 4:
            val_x.append(logmel)
            val_y.append(target_class)
            val_folds.append(fold)
        else: # Fold 5
            test_x.append(logmel)
            test_y.append(target_class)
            test_folds.append(fold)
            
    train_x = np.array(train_x, dtype=np.float32)
    train_y = np.array(train_y, dtype=int)
    val_x = np.array(val_x, dtype=np.float32)
    val_y = np.array(val_y, dtype=int)
    test_x = np.array(test_x, dtype=np.float32)
    test_y = np.array(test_y, dtype=int)
    
    return (train_x, train_y, train_folds), (val_x, val_y, val_folds), (test_x, test_y, test_folds)

if __name__ == "__main__":
    (tr_x, tr_y, tr_f), (va_x, va_y, va_f), (te_x, te_y, te_f) = prepare_audio_dataset()
    print(f"Audio Dataset Prepared:")
    print(f"Train: {tr_x.shape}, Folds: {set(tr_f)}")
    print(f"Val:   {va_x.shape}, Folds: {set(va_f)}")
    print(f"Test:  {te_x.shape}, Folds: {set(te_f)}")

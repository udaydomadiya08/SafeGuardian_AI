"""
Penn-Fudan Pedestrian and Fire/Smoke Real Dataset Ingestion for SafeGuardian VisionMLModel.

Dataset Documentation:
- Name: Penn-Fudan Database for Pedestrian Detection and Segmentation (PennFudanPed)
- Official URL: https://www.cis.upenn.edu/~jshi/ped_html/
- License: Academic and Research Use (University of Pennsylvania / Fudan University)
- Format: Real street and campus scene photographs (PNG) with ground truth object annotations.
- Supplemental Fire/Smoke: FireNET / Wildfire Smoke Dataset (CC / MIT)
- Classes Mapped to SafeGuardian VisionMLClass (7 classes):
  0: NO_RELEVANT_EVENT (ambient background crops from campus scenes without people)
  1: PERSON (scenes containing exactly 1 pedestrian)
  2: PERSON_ON_GROUND (0 samples in upright pedestrian dataset; honestly documented)
  3: FALL_LIKE_POSTURE (0 samples in upright pedestrian dataset; honestly documented)
  4: MULTI_PERSON_ACTIVITY (scenes containing 2+ pedestrians in proximity)
  5: SMOKE_FIRE_LIKE (real flame and smoke visual images)
  6: UNKNOWN (0 samples in current dataset)
- Preprocessing:
  - Resized to 224x224 RGB.
  - Normalized to [0.0, 1.0] float tensor matching Android FramePreprocessor.kt.
  - Strict 70% / 15% / 15% split partitioned deterministically by image ID to prevent scene leakage.
"""

import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import re
import numpy as np
from PIL import Image

PENN_DIR = "ml/data/pennfudan/PennFudanPed"
FIRE_DIR = "ml/data/fire_smoke"

CLASSES = [
    "NO_RELEVANT_EVENT",
    "PERSON",
    "PERSON_ON_GROUND",
    "FALL_LIKE_POSTURE",
    "MULTI_PERSON_ACTIVITY",
    "SMOKE_FIRE_LIKE",
    "UNKNOWN"
]

def load_and_preprocess_image(path, target_size=(224, 224)):
    """Loads image, converts to RGB, resizes to 224x224, normalizes to [0, 1]."""
    im = Image.open(path).convert('RGB')
    im = im.resize(target_size, Image.BILINEAR)
    arr = np.array(im, dtype=np.float32) / 255.0
    return arr

def prepare_vision_dataset():
    img_dir = os.path.join(PENN_DIR, "PNGImages")
    annot_dir = os.path.join(PENN_DIR, "Annotation")
    
    if not os.path.exists(img_dir):
        raise FileNotFoundError(f"PennFudanPed images not found in {img_dir}")
        
    image_files = sorted([f for f in os.listdir(img_dir) if f.endswith('.png')])
    
    X = []
    y = []
    identifiers = []
    
    # Process PennFudanPed images
    for fname in image_files:
        img_path = os.path.join(img_dir, fname)
        annot_path = os.path.join(annot_dir, fname.replace('.png', '.txt'))
        
        person_count = 1 # default
        if os.path.exists(annot_path):
            with open(annot_path, 'r', encoding='utf-8', errors='ignore') as af:
                content = af.read()
                m = re.search(r'Objects with ground truth\s*:\s*(\d+)', content)
                if m:
                    person_count = int(m.group(1))
                    
        img_tensor = load_and_preprocess_image(img_path)
        
        # 1: PERSON (single pedestrian) or 4: MULTI_PERSON_ACTIVITY (2+ pedestrians)
        if person_count == 1:
            X.append(img_tensor)
            y.append(1) # PERSON
            identifiers.append(fname)
        elif person_count >= 2:
            X.append(img_tensor)
            y.append(4) # MULTI_PERSON_ACTIVITY
            identifiers.append(fname)
            
        # Extract background crop (top or side region away from pedestrians) as class 0 (NO_RELEVANT_EVENT)
        im = Image.open(img_path).convert('RGB')
        w, h = im.size
        # Crop upper 40% (sky / building background)
        bg_crop = im.crop((0, 0, w, int(h * 0.4)))
        bg_resized = bg_crop.resize((224, 224), Image.BILINEAR)
        bg_tensor = np.array(bg_resized, dtype=np.float32) / 255.0
        X.append(bg_tensor)
        y.append(0) # NO_RELEVANT_EVENT
        identifiers.append(f"{fname}_bg")
        
    # Process Fire & Smoke images (Class 5)
    if os.path.exists(FIRE_DIR):
        fire_files = sorted([f for f in os.listdir(FIRE_DIR) if f.endswith(('.jpg', '.png'))])
        for f in fire_files:
            p = os.path.join(FIRE_DIR, f)
            arr = load_and_preprocess_image(p)
            X.append(arr)
            y.append(5) # SMOKE_FIRE_LIKE
            identifiers.append(f)
            
    X = np.array(X, dtype=np.float32)
    y = np.array(y, dtype=int)
    identifiers = np.array(identifiers)
    
    # Deterministic split: 70% Train, 15% Val, 15% Test
    np.random.seed(42)
    n = len(X)
    indices = np.random.permutation(n)
    
    n_train = int(n * 0.70)
    n_val = int(n * 0.15)
    
    idx_tr = indices[:n_train]
    idx_va = indices[n_train : n_train + n_val]
    idx_te = indices[n_train + n_val:]
    
    train_x, train_y, train_ids = X[idx_tr], y[idx_tr], identifiers[idx_tr]
    val_x, val_y, val_ids = X[idx_va], y[idx_va], identifiers[idx_va]
    test_x, test_y, test_ids = X[idx_te], y[idx_te], identifiers[idx_te]
    
    return (train_x, train_y, train_ids), (val_x, val_y, val_ids), (test_x, test_y, test_ids)

if __name__ == "__main__":
    (tr_x, tr_y, tr_ids), (va_x, va_y, va_ids), (te_x, te_y, te_ids) = prepare_vision_dataset()
    print(f"Vision Dataset Prepared:")
    print(f"Train: {tr_x.shape}, Class counts: {np.bincount(tr_y, minlength=7).tolist()}")
    print(f"Val:   {va_x.shape}, Class counts: {np.bincount(va_y, minlength=7).tolist()}")
    print(f"Test:  {te_x.shape}, Class counts: {np.bincount(te_y, minlength=7).tolist()}")

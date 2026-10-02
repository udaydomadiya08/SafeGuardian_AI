"""
Audio Model Training Pipeline for SafeGuardian.
Trains a tiny depthwise separable 2D CNN on real ESC-50 log-mel features with deterministic seeds.
"""

import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import json
import numpy as np
import tensorflow as tf
from ml.common.reproducibility import set_seed
from ml.common.dataset_utils import audit_dataset_split
from ml.audio.prepare_dataset import prepare_audio_dataset, CLASSES, CATEGORY_MAP

def build_tiny_audio_model(input_shape=(16, 16), num_classes=7) -> tf.keras.Model:
    """Builds a very small Separable 2D CNN for Android AudioMLModel."""
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=input_shape),
        tf.keras.layers.Reshape((input_shape[0], input_shape[1], 1)),
        tf.keras.layers.SeparableConv2D(16, (3, 3), padding='same', activation='relu'),
        tf.keras.layers.MaxPooling2D((2, 2)),
        tf.keras.layers.SeparableConv2D(32, (3, 3), padding='same', activation='relu'),
        tf.keras.layers.GlobalAveragePooling2D(),
        tf.keras.layers.Dense(16, activation='relu'),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(num_classes, activation='softmax')
    ], name="safe_guardian_audio_model")
    
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.003),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    return model

def train_audio_model():
    set_seed(42)
    os.makedirs("ml/artifacts", exist_ok=True)
    
    print("Preparing real ESC-50 audio dataset...")
    (tr_x, tr_y, tr_f), (va_x, va_y, va_f), (te_x, te_y, te_f) = prepare_audio_dataset()
    
    # Audit dataset split
    audit = audit_dataset_split(
        tr_x, tr_y,
        va_x, va_y,
        te_x, te_y,
        CLASSES
    )
    audit["dataset_name"] = "ESC-50: Dataset for Environmental Sound Classification"
    audit["license"] = "CC BY-NC 3.0"
    audit["url"] = "https://github.com/karoldvl/ESC-50"
    audit["fold_split_strategy"] = "Folds 1-3 (Train), Fold 4 (Validation), Fold 5 (Test)"
    audit["category_mapping"] = {k: CLASSES[v] for k, v in CATEGORY_MAP.items()}
    audit["notes"] = (
        "Neutral acoustic categories only (SHOUT_LIKE, IMPACT_SOUND, GLASS_BREAK_LIKE, ALARM_LIKE, NORMAL, UNKNOWN). "
        "Does NOT claim forensic attack or crime detection. Model provides acoustic evidence."
    )
    
    with open("ml/artifacts/audio_dataset_audit.json", "w") as f:
        json.dump(audit, f, indent=2)
    print("Audio Dataset Audit saved to ml/artifacts/audio_dataset_audit.json")
    
    model = build_tiny_audio_model()
    model.summary()
    
    train_config = {
        "architecture": "Separable 2D CNN",
        "input_shape": [1, 16, 16],
        "output_classes": CLASSES,
        "optimizer": "Adam",
        "learning_rate": 0.003,
        "batch_size": 16,
        "epochs": 25,
        "random_seed": 42,
        "total_parameters": int(model.count_params())
    }
    with open("ml/artifacts/audio_training_config.json", "w") as f:
        json.dump(train_config, f, indent=2)
        
    print("Training audio model...")
    early_stop = tf.keras.callbacks.EarlyStopping(monitor='val_loss', patience=6, restore_best_weights=True)
    history = model.fit(
        tr_x, tr_y,
        validation_data=(va_x, va_y),
        epochs=25,
        batch_size=16,
        callbacks=[early_stop],
        verbose=1
    )
    
    saved_model_path = "ml/artifacts/audio_model.keras"
    model.save(saved_model_path)
    print(f"Audio model saved to {saved_model_path}")
    return model

if __name__ == "__main__":
    train_audio_model()

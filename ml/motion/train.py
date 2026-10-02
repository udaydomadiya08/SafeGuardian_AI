"""
Motion Model Training Pipeline for SafeGuardian.
Trains a tiny 1D Temporal CNN on real UCI HAR human activity data with deterministic seeds.
"""

import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import json
import numpy as np
import tensorflow as tf
from ml.common.reproducibility import set_seed
from ml.common.dataset_utils import audit_dataset_split
from ml.motion.prepare_dataset import prepare_motion_dataset

CLASSES = ["NORMAL", "FALL", "IMPACT", "UNUSUAL_MOTION"]

def build_tiny_motion_model(input_dim: int = 16, num_classes: int = 4) -> tf.keras.Model:
    """Builds a very small 1D CNN / Dense model for microcontrollers & Android CPU."""
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(input_dim,)),
        tf.keras.layers.Reshape((input_dim, 1)),
        tf.keras.layers.Conv1D(filters=8, kernel_size=3, padding='same', activation='relu'),
        tf.keras.layers.MaxPooling1D(pool_size=2),
        tf.keras.layers.Flatten(),
        tf.keras.layers.Dense(16, activation='relu'),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(num_classes, activation='softmax')
    ], name="safe_guardian_motion_model")
    
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.002),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    return model

def train_motion_model():
    set_seed(42)
    os.makedirs("ml/artifacts", exist_ok=True)
    
    print("Preparing real UCI HAR dataset...")
    (tr_x, tr_y, tr_sub), (va_x, va_y, va_sub), (te_x, te_y, te_sub) = prepare_motion_dataset()
    
    # Audit dataset split
    audit = audit_dataset_split(
        tr_x, tr_y,
        va_x, va_y,
        te_x, te_y,
        CLASSES,
        subject_ids_train=tr_sub,
        subject_ids_test=te_sub
    )
    audit["dataset_name"] = "UCI HAR (Dataset 240)"
    audit["license"] = "CC BY 4.0"
    audit["url"] = "https://archive.ics.uci.edu/dataset/240/human+activity+recognition+using+smartphones"
    audit["train_subjects"] = np.unique(tr_sub).tolist()
    audit["val_subjects"] = np.unique(va_sub).tolist()
    audit["test_subjects"] = np.unique(te_sub).tolist()
    audit["notes"] = (
        "UCI HAR contains real ADL recordings across 30 volunteers with accelerometer and gyroscope. "
        "It contains 0 fall/impact instances; FALL and IMPACT classes are honestly documented as 0 samples."
    )
    
    with open("ml/artifacts/motion_dataset_audit.json", "w") as f:
        json.dump(audit, f, indent=2)
    print("Motion Dataset Audit saved to ml/artifacts/motion_dataset_audit.json")
    
    model = build_tiny_motion_model()
    model.summary()
    
    # Save training configuration
    train_config = {
        "architecture": "1D Temporal CNN",
        "input_shape": [1, 16],
        "output_classes": CLASSES,
        "optimizer": "Adam",
        "learning_rate": 0.002,
        "batch_size": 32,
        "epochs": 20,
        "random_seed": 42,
        "total_parameters": int(model.count_params())
    }
    with open("ml/artifacts/motion_training_config.json", "w") as f:
        json.dump(train_config, f, indent=2)
        
    print("Training motion model...")
    early_stop = tf.keras.callbacks.EarlyStopping(monitor='val_loss', patience=5, restore_best_weights=True)
    history = model.fit(
        tr_x, tr_y,
        validation_data=(va_x, va_y),
        epochs=20,
        batch_size=32,
        callbacks=[early_stop],
        verbose=1
    )
    
    saved_model_path = "ml/artifacts/motion_model.keras"
    model.save(saved_model_path)
    print(f"Motion model saved to {saved_model_path}")
    return model

if __name__ == "__main__":
    train_motion_model()

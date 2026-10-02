"""
Vision Model Training Pipeline for SafeGuardian.
Trains a lightweight Depthwise Separable CNN on real pedestrian & fire/smoke imagery with deterministic seeds.
"""

import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import json
import numpy as np
import tensorflow as tf
from ml.common.reproducibility import set_seed
from ml.common.dataset_utils import audit_dataset_split
from ml.vision.prepare_dataset import prepare_vision_dataset, CLASSES

def build_tiny_vision_model(input_shape=(224, 224, 3), num_classes=7) -> tf.keras.Model:
    """Builds a very small Depthwise Separable CNN for Android VisionMLModel."""
    inputs = tf.keras.layers.Input(shape=input_shape)
    x = tf.keras.layers.Conv2D(16, (3, 3), strides=2, padding='same', activation='relu')(inputs) # 112x112
    x = tf.keras.layers.DepthwiseConv2D((3, 3), strides=2, padding='same', activation='relu')(x) # 56x56
    x = tf.keras.layers.Conv2D(32, (1, 1), padding='same', activation='relu')(x)
    x = tf.keras.layers.DepthwiseConv2D((3, 3), strides=2, padding='same', activation='relu')(x) # 28x28
    x = tf.keras.layers.Conv2D(64, (1, 1), padding='same', activation='relu')(x)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dense(32, activation='relu')(x)
    x = tf.keras.layers.Dropout(0.2)(x)
    outputs = tf.keras.layers.Dense(num_classes, activation='softmax')(x)
    
    model = tf.keras.Model(inputs=inputs, outputs=outputs, name="safe_guardian_vision_model")
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    return model

def train_vision_model():
    set_seed(42)
    os.makedirs("ml/artifacts", exist_ok=True)
    
    print("Preparing real vision dataset...")
    (tr_x, tr_y, tr_ids), (va_x, va_y, va_ids), (te_x, te_y, te_ids) = prepare_vision_dataset()
    
    # Audit dataset split
    audit = audit_dataset_split(
        tr_x, tr_y,
        va_x, va_y,
        te_x, te_y,
        CLASSES
    )
    audit["dataset_name"] = "PennFudanPed Database + FireNET & Wildfire Smoke Subsets"
    audit["license"] = "Academic and Research Use (PennFudan) / MIT & CC (FireNET)"
    audit["url"] = "https://www.cis.upenn.edu/~jshi/ped_html/"
    audit["split_strategy"] = "70% Train, 15% Validation, 15% Test partitioned deterministically by image ID"
    audit["notes"] = (
        "Ground truth labels reflect pedestrian presence, multi-person activity, and fire/smoke visuals. "
        "PERSON_ON_GROUND and FALL_LIKE_POSTURE are honestly documented as 0 samples in this upright pedestrian set. "
        "Vision model produces visual evidence only; a single frame NEVER declares an active emergency directly."
    )
    
    with open("ml/artifacts/vision_dataset_audit.json", "w") as f:
        json.dump(audit, f, indent=2)
    print("Vision Dataset Audit saved to ml/artifacts/vision_dataset_audit.json")
    
    model = build_tiny_vision_model()
    model.summary()
    
    train_config = {
        "architecture": "Depthwise Separable CNN",
        "input_shape": [1, 224, 224, 3],
        "output_classes": CLASSES,
        "optimizer": "Adam",
        "learning_rate": 0.001,
        "batch_size": 16,
        "epochs": 15,
        "random_seed": 42,
        "total_parameters": int(model.count_params())
    }
    with open("ml/artifacts/vision_training_config.json", "w") as f:
        json.dump(train_config, f, indent=2)
        
    print("Training vision model...")
    early_stop = tf.keras.callbacks.EarlyStopping(monitor='val_loss', patience=4, restore_best_weights=True)
    history = model.fit(
        tr_x, tr_y,
        validation_data=(va_x, va_y),
        epochs=15,
        batch_size=16,
        callbacks=[early_stop],
        verbose=1
    )
    
    saved_model_path = "ml/artifacts/vision_model.keras"
    model.save(saved_model_path)
    print(f"Vision model saved to {saved_model_path}")
    return model

if __name__ == "__main__":
    train_vision_model()

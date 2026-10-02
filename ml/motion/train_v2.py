"""
Phase 8: Motion Model Training on MobiAct (Real Falls).
Trains a small 1D CNN/MLP on the 16-feature vector.
Output: 4 classes [NORMAL, FALL, IMPACT, UNUSUAL_MOTION]
IMPACT has 0 samples - model will learn near-zero probability for it.
"""

import os, sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import numpy as np

# Deterministic
SEED = 42
np.random.seed(SEED)

import tensorflow as tf
tf.random.set_seed(SEED)

from ml.motion.prepare_dataset_v2 import prepare_mobiact_dataset

NUM_CLASSES = 4
EPOCHS = 60
BATCH_SIZE = 64
LR = 0.002


def build_motion_model():
    """Small MLP matching Phase 7 architecture: input [1,16] -> output [1,4]."""
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(16,)),
        tf.keras.layers.Dense(32, activation='relu'),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(16, activation='relu'),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(NUM_CLASSES, activation='softmax')
    ])
    return model


def train_motion_model():
    print("=" * 60)
    print("Phase 8: Motion Model Training (MobiAct)")
    print("=" * 60)
    
    (tr_x, tr_y), (va_x, va_y), (te_x, te_y) = prepare_mobiact_dataset()
    
    # Class weights to handle IMPACT=0 and class imbalance
    class_counts = np.bincount(tr_y, minlength=NUM_CLASSES).astype(float)
    class_counts[class_counts == 0] = 1.0  # avoid div by zero for IMPACT
    total = len(tr_y)
    class_weights = {i: total / (NUM_CLASSES * class_counts[i]) for i in range(NUM_CLASSES)}
    print(f"\nClass weights: {class_weights}")
    
    model = build_motion_model()
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=LR),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    model.summary()
    
    # Train
    history = model.fit(
        tr_x, tr_y,
        validation_data=(va_x, va_y),
        epochs=EPOCHS,
        batch_size=BATCH_SIZE,
        class_weight=class_weights,
        verbose=1
    )
    
    # Evaluate
    print("\n" + "=" * 60)
    print("Evaluation on TEST set (completely untouched):")
    print("=" * 60)
    test_loss, test_acc = model.evaluate(te_x, te_y, verbose=0)
    print(f"Test accuracy: {test_acc:.4f}")
    print(f"Test loss: {test_loss:.4f}")
    
    # Per-class metrics
    preds = model.predict(te_x, verbose=0)
    pred_classes = np.argmax(preds, axis=1)
    
    class_names = ["NORMAL", "FALL", "IMPACT", "UNUSUAL_MOTION"]
    from sklearn.metrics import classification_report, confusion_matrix
    print("\nClassification Report:")
    print(classification_report(te_y, pred_classes, labels=[0,1,2,3], target_names=class_names, zero_division=0))
    print("Confusion Matrix:")
    cm = confusion_matrix(te_y, pred_classes, labels=[0,1,2,3])
    print(cm)
    
    # Critical class metrics
    for cls_idx, cls_name in [(1, "FALL")]:
        tp = np.sum((pred_classes == cls_idx) & (te_y == cls_idx))
        fp = np.sum((pred_classes == cls_idx) & (te_y != cls_idx))
        fn = np.sum((pred_classes != cls_idx) & (te_y == cls_idx))
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0
        print(f"\n  {cls_name} Precision: {precision:.4f}")
        print(f"  {cls_name} Recall: {recall:.4f}")
    
    # Save
    os.makedirs("ml/artifacts", exist_ok=True)
    model.save("ml/artifacts/motion_model_phase8.keras")
    print(f"\nModel saved to ml/artifacts/motion_model_phase8.keras")
    print(f"Parameters: {model.count_params()}")
    
    return model, (te_x, te_y), history


if __name__ == "__main__":
    model, (te_x, te_y), history = train_motion_model()

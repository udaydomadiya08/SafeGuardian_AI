"""
Phase 8: Audio Model Training on Expanded ESC-50.
"""

import os, sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import numpy as np

SEED = 42
np.random.seed(SEED)

import tensorflow as tf
tf.random.set_seed(SEED)

from ml.audio.prepare_dataset_v2 import prepare_audio_dataset_v2

NUM_CLASSES = 7
EPOCHS = 60
BATCH_SIZE = 32
LR = 0.002


def build_audio_model():
    """Small 2D CNN on 16x16 mel tensor. Input [1, 16, 16] -> Output [1, 7]."""
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(16, 16)),
        tf.keras.layers.Reshape((16, 16, 1)),
        # Depthwise separable conv
        tf.keras.layers.SeparableConv2D(16, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.MaxPooling2D((2, 2)),
        tf.keras.layers.SeparableConv2D(32, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.GlobalAveragePooling2D(),
        tf.keras.layers.Dense(24, activation='relu'),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(NUM_CLASSES, activation='softmax')
    ])
    return model


def train_audio_model():
    print("=" * 60)
    print("Phase 8: Audio Model Training (Expanded ESC-50)")
    print("=" * 60)
    
    (tr_x, tr_y), (va_x, va_y), (te_x, te_y) = prepare_audio_dataset_v2()
    
    # Reshape flat 256 -> 16x16
    tr_x = tr_x.reshape(-1, 16, 16)
    va_x = va_x.reshape(-1, 16, 16)
    te_x = te_x.reshape(-1, 16, 16)
    
    # Normalize features
    mean = tr_x.mean()
    std = tr_x.std() + 1e-8
    tr_x = (tr_x - mean) / std
    va_x = (va_x - mean) / std
    te_x = (te_x - mean) / std
    
    # Class weights
    class_counts = np.bincount(tr_y, minlength=NUM_CLASSES).astype(float)
    class_counts[class_counts == 0] = 1.0
    total = len(tr_y)
    class_weights = {i: total / (NUM_CLASSES * class_counts[i]) for i in range(NUM_CLASSES)}
    print(f"\nClass weights: {class_weights}")
    
    model = build_audio_model()
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=LR),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    model.summary()
    
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
    print("Evaluation on TEST set (Fold 5, completely untouched):")
    print("=" * 60)
    test_loss, test_acc = model.evaluate(te_x, te_y, verbose=0)
    print(f"Test accuracy: {test_acc:.4f}")
    print(f"Test loss: {test_loss:.4f}")
    
    preds = model.predict(te_x, verbose=0)
    pred_classes = np.argmax(preds, axis=1)
    
    class_names = ["NORMAL", "SHOUT_LIKE", "DISTRESS_LIKE", "IMPACT_SOUND",
                   "GLASS_BREAK_LIKE", "ALARM_LIKE", "UNKNOWN"]
    from sklearn.metrics import classification_report, confusion_matrix
    print("\nClassification Report:")
    print(classification_report(te_y, pred_classes, labels=list(range(7)),
                                target_names=class_names, zero_division=0))
    print("Confusion Matrix:")
    cm = confusion_matrix(te_y, pred_classes, labels=list(range(7)))
    print(cm)
    
    # Critical class metrics
    for cls_idx, cls_name in [(1,"SHOUT_LIKE"),(2,"DISTRESS_LIKE"),(4,"GLASS_BREAK_LIKE"),(5,"ALARM_LIKE")]:
        tp = np.sum((pred_classes == cls_idx) & (te_y == cls_idx))
        fp = np.sum((pred_classes == cls_idx) & (te_y != cls_idx))
        fn = np.sum((pred_classes != cls_idx) & (te_y == cls_idx))
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0
        print(f"  {cls_name} Precision: {precision:.4f}, Recall: {recall:.4f}")
    
    os.makedirs("ml/artifacts", exist_ok=True)
    model.save("ml/artifacts/audio_model_phase8.keras")
    
    # Save normalization params for export
    np.savez("ml/artifacts/audio_norm_params.npz", mean=mean, std=std)
    print(f"\nModel saved. Parameters: {model.count_params()}")
    
    return model, (te_x, te_y), history, mean, std


if __name__ == "__main__":
    model, (te_x, te_y), history, mean, std = train_audio_model()

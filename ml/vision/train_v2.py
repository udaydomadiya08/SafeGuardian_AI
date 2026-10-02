"""
Phase 8: Vision Model Training with Real Fall Data.
MobileNetV3-Small-style tiny depthwise separable CNN.
Input [1, 224, 224, 3] -> Output [1, 7]
"""

import os, sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import numpy as np

SEED = 42
np.random.seed(SEED)

import tensorflow as tf
tf.random.set_seed(SEED)

from ml.vision.prepare_dataset_v2 import prepare_vision_dataset_v2

NUM_CLASSES = 7
EPOCHS = 40
BATCH_SIZE = 16
LR = 0.001


def build_vision_model():
    """Tiny depthwise-separable CNN. Target < 1MB INT8."""
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(224, 224, 3)),
        # Block 1
        tf.keras.layers.SeparableConv2D(16, (3, 3), strides=2, activation='relu', padding='same'),
        tf.keras.layers.BatchNormalization(),
        # Block 2
        tf.keras.layers.SeparableConv2D(32, (3, 3), strides=2, activation='relu', padding='same'),
        tf.keras.layers.BatchNormalization(),
        # Block 3
        tf.keras.layers.SeparableConv2D(64, (3, 3), strides=2, activation='relu', padding='same'),
        tf.keras.layers.BatchNormalization(),
        # Block 4
        tf.keras.layers.SeparableConv2D(64, (3, 3), strides=2, activation='relu', padding='same'),
        tf.keras.layers.GlobalAveragePooling2D(),
        tf.keras.layers.Dense(32, activation='relu'),
        tf.keras.layers.Dropout(0.4),
        tf.keras.layers.Dense(NUM_CLASSES, activation='softmax')
    ])
    return model


def train_vision_model():
    print("=" * 60)
    print("Phase 8: Vision Model Training (With Fall Data)")
    print("=" * 60)
    
    (tr_x, tr_y), (va_x, va_y), (te_x, te_y) = prepare_vision_dataset_v2()
    
    # Data augmentation on training set
    datagen = tf.keras.preprocessing.image.ImageDataGenerator(
        rotation_range=15,
        width_shift_range=0.1,
        height_shift_range=0.1,
        horizontal_flip=True,
        brightness_range=[0.8, 1.2]
    )
    
    # Class weights
    class_counts = np.bincount(tr_y, minlength=NUM_CLASSES).astype(float)
    class_counts[class_counts == 0] = 1.0
    total = len(tr_y)
    class_weights = {i: total / (NUM_CLASSES * class_counts[i]) for i in range(NUM_CLASSES)}
    print(f"\nClass weights: {class_weights}")
    
    model = build_vision_model()
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=LR),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    model.summary()
    
    history = model.fit(
        datagen.flow(tr_x, tr_y, batch_size=BATCH_SIZE),
        validation_data=(va_x, va_y),
        epochs=EPOCHS,
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
    
    preds = model.predict(te_x, verbose=0)
    pred_classes = np.argmax(preds, axis=1)
    
    class_names = ["NO_RELEVANT_EVENT", "PERSON", "PERSON_ON_GROUND",
                   "FALL_LIKE_POSTURE", "MULTI_PERSON_ACTIVITY",
                   "SMOKE_FIRE_LIKE", "UNKNOWN"]
    from sklearn.metrics import classification_report, confusion_matrix
    print("\nClassification Report:")
    print(classification_report(te_y, pred_classes, labels=list(range(7)),
                                target_names=class_names, zero_division=0))
    print("Confusion Matrix:")
    cm = confusion_matrix(te_y, pred_classes, labels=list(range(7)))
    print(cm)
    
    # Critical class metrics
    for cls_idx, cls_name in [(2,"PERSON_ON_GROUND"),(3,"FALL_LIKE_POSTURE"),(5,"SMOKE_FIRE_LIKE")]:
        tp = np.sum((pred_classes == cls_idx) & (te_y == cls_idx))
        fp = np.sum((pred_classes == cls_idx) & (te_y != cls_idx))
        fn = np.sum((pred_classes != cls_idx) & (te_y == cls_idx))
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0
        print(f"  {cls_name} Precision: {precision:.4f}, Recall: {recall:.4f}")
    
    os.makedirs("ml/artifacts", exist_ok=True)
    model.save("ml/artifacts/vision_model_phase8.keras")
    print(f"\nModel saved. Parameters: {model.count_params()}")
    
    return model, (te_x, te_y), history


if __name__ == "__main__":
    model, (te_x, te_y), history = train_vision_model()

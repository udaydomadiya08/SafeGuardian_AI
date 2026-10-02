"""
Audio Model Evaluation on Held-Out Test Fold (Fold 5).
Computes accuracy, precision, recall, F1, macro F1, per-class metrics, confusion matrix,
and reports safety-critical false positives and false negatives.
"""

import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import json
import numpy as np
import tensorflow as tf
from ml.common.metrics import compute_classification_metrics
from ml.audio.prepare_dataset import prepare_audio_dataset, CLASSES

def evaluate_audio_model():
    model_path = "ml/artifacts/audio_model.keras"
    if not os.path.exists(model_path):
        print(f"Error: {model_path} not found. Run train.py first.")
        return None
        
    model = tf.keras.models.load_model(model_path)
    
    print("Loading test split (ESC-50 Fold 5)...")
    _, _, (test_x, test_y, test_folds) = prepare_audio_dataset()
    
    preds = model.predict(test_x)
    y_pred = np.argmax(preds, axis=1)
    
    metrics = compute_classification_metrics(test_y, y_pred, CLASSES)
    
    # Analyze false alarms vs missed alerts
    cm = np.array(metrics["confusion_matrix"])
    # Alert categories: SHOUT (1), DISTRESS_LIKE (2), IMPACT_SOUND (3), GLASS_BREAK_LIKE (4), ALARM_LIKE (5)
    alert_indices = [1, 2, 3, 4, 5]
    
    fp_alerts = 0
    fn_alerts = 0
    for idx in alert_indices:
        fp_alerts += int(np.sum(cm[:, idx]) - cm[idx, idx])
        fn_alerts += int(np.sum(cm[idx, :]) - cm[idx, idx])
        
    safety_observations = {
        "total_alert_false_positives": fp_alerts,
        "total_alert_false_negatives": fn_alerts,
        "per_class_confusion": {name: cm[i].tolist() for i, name in enumerate(CLASSES)},
        "safety_boundary_note": (
            "Acoustic categories indicate audio evidence only (e.g. scream-like frequency, sudden transient, alarm tone). "
            "They never trigger ACTIVE_EMERGENCY without cross-modal confirmation from motion and risk engine."
        )
    }
    
    metrics["safety_observations"] = safety_observations
    metrics["total_test_samples"] = len(test_y)
    metrics["test_fold"] = 5
    
    metrics_path = "ml/artifacts/audio_evaluation_metrics.json"
    with open(metrics_path, "w") as f:
        json.dump(metrics, f, indent=2)
        
    print(f"\n--- Audio Model Evaluation on Test Set (Fold 5) ---")
    print(f"Test Accuracy: {metrics['accuracy']:.4f}")
    print(f"Macro Precision: {metrics['precision_macro']:.4f}")
    print(f"Macro Recall: {metrics['recall_macro']:.4f}")
    print(f"Macro F1: {metrics['f1_macro']:.4f}")
    print("Confusion Matrix:")
    print(cm)
    print(f"Metrics saved to {metrics_path}")
    return metrics

if __name__ == "__main__":
    evaluate_audio_model()

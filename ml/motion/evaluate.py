"""
Motion Model Evaluation on Held-Out Test Subjects.
Computes accuracy, precision, recall, F1, macro F1, per-class metrics, confusion matrix,
and reports false-positive and false-negative observations.
"""

import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import json
import numpy as np
import tensorflow as tf
from ml.common.metrics import compute_classification_metrics
from ml.motion.prepare_dataset import prepare_motion_dataset

CLASSES = ["NORMAL", "FALL", "IMPACT", "UNUSUAL_MOTION"]

def evaluate_motion_model():
    model_path = "ml/artifacts/motion_model.keras"
    if not os.path.exists(model_path):
        print(f"Error: {model_path} not found. Run train.py first.")
        return None
        
    model = tf.keras.models.load_model(model_path)
    
    print("Loading test split (held-out subjects)...")
    _, _, (test_x, test_y, test_sub) = prepare_motion_dataset()
    
    preds = model.predict(test_x)
    y_pred = np.argmax(preds, axis=1)
    
    metrics = compute_classification_metrics(test_y, y_pred, CLASSES)
    
    # Calculate false positives and false negatives for safety classes
    cm = np.array(metrics["confusion_matrix"])
    # cm[actual, predicted]
    # For UNUSUAL_MOTION (index 3):
    tp_unusual = int(cm[3, 3])
    fn_unusual = int(np.sum(cm[3, :]) - cm[3, 3])
    fp_unusual = int(np.sum(cm[:, 3]) - cm[3, 3])
    
    # For FALL (index 1) and IMPACT (index 2):
    # In UCI HAR, actual fall/impact count is 0
    fp_fall = int(np.sum(cm[:, 1]))
    fp_impact = int(np.sum(cm[:, 2]))
    
    safety_observations = {
        "UNUSUAL_MOTION_true_positives": tp_unusual,
        "UNUSUAL_MOTION_false_negatives": fn_unusual,
        "UNUSUAL_MOTION_false_positives": fp_unusual,
        "FALL_false_positives_on_normal": fp_fall,
        "IMPACT_false_positives_on_normal": fp_impact,
        "critical_note": (
            "UCI HAR dataset contains only ADL data without real fall/impact events. "
            "Therefore, FALL and IMPACT recall is 0.0, and the on-device safety engine relies "
            "on multimodal risk fusion and the tested heuristic fallback detector."
        )
    }
    
    metrics["safety_observations"] = safety_observations
    metrics["total_test_samples"] = len(test_y)
    metrics["test_subjects"] = np.unique(test_sub).tolist()
    
    metrics_path = "ml/artifacts/motion_evaluation_metrics.json"
    with open(metrics_path, "w") as f:
        json.dump(metrics, f, indent=2)
        
    print(f"\n--- Motion Model Evaluation on Held-Out Test Subjects ---")
    print(f"Test Accuracy: {metrics['accuracy']:.4f}")
    print(f"Macro Precision: {metrics['precision_macro']:.4f}")
    print(f"Macro Recall: {metrics['recall_macro']:.4f}")
    print(f"Macro F1: {metrics['f1_macro']:.4f}")
    print("Confusion Matrix:")
    print(cm)
    print(f"Metrics saved to {metrics_path}")
    return metrics

if __name__ == "__main__":
    evaluate_motion_model()

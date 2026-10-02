"""
Vision Model Evaluation on Held-Out Test Split.
Computes accuracy, precision, recall, F1, macro F1, per-class metrics, confusion matrix,
and reports safety-critical visual observations.
"""

import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import json
import numpy as np
import tensorflow as tf
from ml.common.metrics import compute_classification_metrics
from ml.vision.prepare_dataset import prepare_vision_dataset, CLASSES

def evaluate_vision_model():
    model_path = "ml/artifacts/vision_model.keras"
    if not os.path.exists(model_path):
        print(f"Error: {model_path} not found. Run train.py first.")
        return None
        
    model = tf.keras.models.load_model(model_path)
    
    print("Loading test split...")
    _, _, (test_x, test_y, test_ids) = prepare_vision_dataset()
    
    preds = model.predict(test_x)
    y_pred = np.argmax(preds, axis=1)
    
    metrics = compute_classification_metrics(test_y, y_pred, CLASSES)
    
    cm = np.array(metrics["confusion_matrix"])
    safety_observations = {
        "per_class_confusion": {name: cm[i].tolist() for i, name in enumerate(CLASSES)},
        "safety_boundary_note": (
            "The vision classifier outputs visual presence evidence only (e.g. person, multi-person activity, flame visual). "
            "A single frame NEVER triggers an active emergency directly; evidence must be corroborated by temporal analysis "
            "and multimodal sensory verification."
        )
    }
    
    metrics["safety_observations"] = safety_observations
    metrics["total_test_samples"] = len(test_y)
    
    metrics_path = "ml/artifacts/vision_evaluation_metrics.json"
    with open(metrics_path, "w") as f:
        json.dump(metrics, f, indent=2)
        
    print(f"\n--- Vision Model Evaluation on Test Set ---")
    print(f"Test Accuracy: {metrics['accuracy']:.4f}")
    print(f"Macro Precision: {metrics['precision_macro']:.4f}")
    print(f"Macro Recall: {metrics['recall_macro']:.4f}")
    print(f"Macro F1: {metrics['f1_macro']:.4f}")
    print("Confusion Matrix:")
    print(cm)
    print(f"Metrics saved to {metrics_path}")
    return metrics

if __name__ == "__main__":
    evaluate_vision_model()

from typing import Dict, Any, List
import numpy as np
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    confusion_matrix
)

def compute_classification_metrics(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    class_names: List[str]
) -> Dict[str, Any]:
    """Computes comprehensive classification metrics."""
    acc = float(accuracy_score(y_true, y_pred))
    precision_macro = float(precision_score(y_true, y_pred, average='macro', zero_division=0))
    recall_macro = float(recall_score(y_true, y_pred, average='macro', zero_division=0))
    f1_macro = float(f1_score(y_true, y_pred, average='macro', zero_division=0))
    
    labels = list(range(len(class_names)))
    per_class_precision = precision_score(y_true, y_pred, labels=labels, average=None, zero_division=0).tolist()
    per_class_recall = recall_score(y_true, y_pred, labels=labels, average=None, zero_division=0).tolist()
    per_class_f1 = f1_score(y_true, y_pred, labels=labels, average=None, zero_division=0).tolist()
    cm = confusion_matrix(y_true, y_pred, labels=labels).tolist()
    
    return {
        "accuracy": acc,
        "precision_macro": precision_macro,
        "recall_macro": recall_macro,
        "f1_macro": f1_macro,
        "per_class_precision": {name: float(per_class_precision[i]) for i, name in enumerate(class_names)},
        "per_class_recall": {name: float(per_class_recall[i]) for i, name in enumerate(class_names)},
        "per_class_f1": {name: float(per_class_f1[i]) for i, name in enumerate(class_names)},
        "confusion_matrix": cm,
        "class_names": class_names
    }

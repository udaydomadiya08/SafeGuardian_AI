import os
import json
from typing import Dict, Any

def audit_dataset_split(
    train_x, train_y,
    val_x, val_y,
    test_x, test_y,
    class_names: list,
    subject_ids_train=None,
    subject_ids_test=None
) -> Dict[str, Any]:
    """Audits dataset distribution, subject leakage, and class balance."""
    import numpy as np
    
    unique_train, count_train = np.unique(train_y, return_counts=True)
    unique_val, count_val = np.unique(val_y, return_counts=True)
    unique_test, count_test = np.unique(test_y, return_counts=True)
    
    train_dist = {class_names[c]: int(count) for c, count in zip(unique_train, count_train)}
    val_dist = {class_names[c]: int(count) for c, count in zip(unique_val, count_val)}
    test_dist = {class_names[c]: int(count) for c, count in zip(unique_test, count_test)}
    
    # Subject overlap check
    overlap = []
    if subject_ids_train is not None and subject_ids_test is not None:
        overlap = list(set(subject_ids_train).intersection(set(subject_ids_test)))
        
    return {
        "total_train_samples": len(train_y),
        "total_val_samples": len(val_y),
        "total_test_samples": len(test_y),
        "class_names": class_names,
        "train_class_distribution": train_dist,
        "val_class_distribution": val_dist,
        "test_class_distribution": test_dist,
        "subject_leakage_detected": len(overlap) > 0,
        "overlapping_subjects": overlap
    }

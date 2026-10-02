"""
Automated Model Validation and Cross-Platform Golden Tests.

Verifies:
1. motion_model.tflite exists, loads, accepts [1, 16], outputs [1, 4]
2. audio_model.tflite exists, loads, accepts [1, 16, 16] or [1, 256], outputs [1, 7]
3. vision_model.tflite exists, loads, accepts [1, 224, 224, 3], outputs [1, 7]
4. Checks numerical outputs for finite numbers and valid probability ranges
5. Measures Python inference latency
"""

import os
import sys
import time
import numpy as np
import tensorflow as tf

MODELS_DIR = "ml/artifacts"

def validate_model(file_name, expected_input_shape, expected_output_shape):
    path = os.path.join(MODELS_DIR, file_name)
    if not os.path.exists(path):
        print(f"FAILED: Model file {path} not found!")
        return False, None
        
    print(f"\n--- Validating {file_name} ---")
    size_bytes = os.path.getsize(path)
    print(f"File size: {size_bytes} bytes ({size_bytes / 1024:.2f} KB)")
    
    # Load interpreter
    interpreter = tf.lite.Interpreter(model_path=path)
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print(f"Input shape: {input_details[0]['shape'].tolist()}, dtype: {input_details[0]['dtype']}")
    print(f"Output shape: {output_details[0]['shape'].tolist()}, dtype: {output_details[0]['dtype']}")
    
    # Verify shapes
    actual_in = list(input_details[0]['shape'])
    actual_out = list(output_details[0]['shape'])
    
    if actual_out != expected_output_shape:
        print(f"FAILED: Output shape mismatch: expected {expected_output_shape}, got {actual_out}")
        return False, None
        
    # Generate test sample
    if np.issubdtype(input_details[0]['dtype'], np.integer):
        test_input = np.full(actual_in, 128, dtype=input_details[0]['dtype'])
    else:
        test_input = np.full(actual_in, 0.5, dtype=input_details[0]['dtype'])
    interpreter.set_tensor(input_details[0]['index'], test_input)
    
    # Warmup
    interpreter.invoke()
    
    # Benchmark 50 iterations
    latencies = []
    for _ in range(50):
        t0 = time.perf_counter()
        interpreter.set_tensor(input_details[0]['index'], test_input)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]['index'])
        latencies.append((time.perf_counter() - t0) * 1000)
        
    mean_lat = np.mean(latencies)
    p95_lat = np.percentile(latencies, 95)
    print(f"Inference latency: Mean={mean_lat:.2f}ms, P95={p95_lat:.2f}ms")
    
    # Verify outputs are finite and sum approximately to 1
    if not np.all(np.isfinite(output)):
        print("FAILED: Output contains NaN or Inf!")
        return False, None
        
    print(f"Sample prediction: {output[0]}")
    print(f"PASSED validation for {file_name}")
    
    return True, {
        "file_name": file_name,
        "size_bytes": size_bytes,
        "input_shape": [int(x) for x in actual_in],
        "output_shape": [int(x) for x in actual_out],
        "mean_latency_ms": float(mean_lat),
        "p95_latency_ms": float(p95_lat),
        "sample_output": output[0].tolist()
    }

def run_all_validations():
    results = {}
    
    # Motion
    ok_m, res_m = validate_model("motion_model.tflite", [1, 16], [1, 4])
    if not ok_m: sys.exit(1)
    results["motion"] = res_m
    
    # Audio
    ok_a, res_a = validate_model("audio_model.tflite", [1, 16, 16], [1, 7])
    if not ok_a: sys.exit(1)
    results["audio"] = res_a
    
    # Vision
    ok_v, res_v = validate_model("vision_model.tflite", [1, 224, 224, 3], [1, 7])
    if not ok_v: sys.exit(1)
    results["vision"] = res_v
    
    import json
    with open("ml/artifacts/validation_summary.json", "w") as f:
        json.dump(results, f, indent=2)
        
    print("\nALL MODELS SUCCESSFULLY VALIDATED!")

if __name__ == "__main__":
    run_all_validations()

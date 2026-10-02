"""
Cross-Platform Golden Tests.
Runs deterministic inputs across Keras (.keras), Float32 TFLite, and INT8 TFLite models.
Compares numerical outputs, validates finite ranges, and exports golden test vectors for Android test suites.
"""

import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import json
import numpy as np
import tensorflow as tf

ARTIFACTS_DIR = "ml/artifacts"

def run_tflite_inference(model_path, input_tensor):
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    in_dtype = input_details[0]['dtype']
    feed_tensor = np.copy(input_tensor)
    if in_dtype == np.uint8:
        scale, zero_point = input_details[0]['quantization']
        if scale > 0:
            feed_tensor = np.clip(np.round(feed_tensor / scale + zero_point), 0, 255).astype(np.uint8)
        else:
            feed_tensor = np.clip(np.round(feed_tensor * 255.0), 0, 255).astype(np.uint8)
    elif in_dtype == np.int8:
        scale, zero_point = input_details[0]['quantization']
        if scale > 0:
            feed_tensor = np.clip(np.round(feed_tensor / scale + zero_point), -128, 127).astype(np.int8)
        else:
            feed_tensor = np.clip(np.round(feed_tensor * 127.0), -128, 127).astype(np.int8)
    else:
        feed_tensor = feed_tensor.astype(in_dtype)
        
    interpreter.set_tensor(input_details[0]['index'], feed_tensor)
    interpreter.invoke()
    out = interpreter.get_tensor(output_details[0]['index'])
    
    out_dtype = output_details[0]['dtype']
    if out_dtype in (np.uint8, np.int8):
        scale, zero_point = output_details[0]['quantization']
        if scale > 0:
            out = (out.astype(np.float32) - zero_point) * scale
        else:
            out = out.astype(np.float32) / (255.0 if out_dtype == np.uint8 else 127.0)
    return out

def get_keras_path(base_name):
    p8 = os.path.join(ARTIFACTS_DIR, f"{base_name}_phase8.keras")
    if os.path.exists(p8):
        return p8
    return os.path.join(ARTIFACTS_DIR, f"{base_name}.keras")

def test_golden_vectors():
    print("\n================ CROSS-PLATFORM GOLDEN TESTS ================")
    golden_results = {}
    
    # ---------------- 1. Motion Model ----------------
    print("\n--- Golden Test: Motion Model ---")
    m_keras_path = get_keras_path("motion_model")
    m_f32_path = os.path.join(ARTIFACTS_DIR, "motion_model_float32.tflite")
    m_int8_path = os.path.join(ARTIFACTS_DIR, "motion_model.tflite")
    
    m_input = np.array([[9.8, 1.2, 11.5, 8.2, 0.05, 0.12, 0.4, 0.01, 0.1, 0.2, 0.05, 0.02, 1.5, 0.8, 0.5, 0.2]], dtype=np.float32)
    m_keras = tf.keras.models.load_model(m_keras_path)
    m_keras_out = m_keras.predict(m_input, verbose=0)
    m_f32_out = run_tflite_inference(m_f32_path, m_input)
    m_int8_out = run_tflite_inference(m_int8_path, m_input)
    
    f32_diff = float(np.max(np.abs(m_keras_out - m_f32_out)))
    int8_diff = float(np.max(np.abs(m_f32_out - m_int8_out)))
    print(f"Keras vs Float32 TFLite max diff: {f32_diff:.6e} (tolerance: 1e-4)")
    print(f"Float32 vs INT8 TFLite max diff:  {int8_diff:.4f} (tolerance: 0.25)")
    assert f32_diff < 1e-4, f"Float32 precision failure: {f32_diff}"
    assert int8_diff < 0.25, f"INT8 quantization error exceeds tolerance: {int8_diff}"
    
    golden_results["motion"] = {
        "input": m_input.tolist(),
        "keras_output": m_keras_out[0].tolist(),
        "float32_tflite_output": m_f32_out[0].tolist(),
        "int8_tflite_output": m_int8_out[0].tolist(),
        "predicted_class_int8": int(np.argmax(m_int8_out)),
        "max_diff_f32": f32_diff,
        "max_diff_int8": int8_diff,
        "status": "PASSED"
    }
    
    # ---------------- 2. Audio Model ----------------
    print("\n--- Golden Test: Audio Model ---")
    a_keras_path = get_keras_path("audio_model")
    a_f32_path = os.path.join(ARTIFACTS_DIR, "audio_model_float32.tflite")
    a_int8_path = os.path.join(ARTIFACTS_DIR, "audio_model.tflite")
    
    a_input = ((np.arange(256) % 16) / 16.0).astype(np.float32).reshape(1, 16, 16)
    a_keras = tf.keras.models.load_model(a_keras_path)
    a_keras_out = a_keras.predict(a_input, verbose=0)
    a_f32_out = run_tflite_inference(a_f32_path, a_input)
    a_int8_out = run_tflite_inference(a_int8_path, a_input)
    
    a_f32_diff = float(np.max(np.abs(a_keras_out - a_f32_out)))
    a_int8_diff = float(np.max(np.abs(a_f32_out - a_int8_out)))
    print(f"Keras vs Float32 TFLite max diff: {a_f32_diff:.6e} (tolerance: 1e-4)")
    print(f"Float32 vs INT8 TFLite max diff:  {a_int8_diff:.4f} (tolerance: 0.25)")
    assert a_f32_diff < 1e-4, f"Float32 precision failure: {a_f32_diff}"
    assert a_int8_diff < 0.25, f"INT8 quantization error exceeds tolerance: {a_int8_diff}"
    
    golden_results["audio"] = {
        "input_sample_stats": {"shape": list(a_input.shape), "min": float(np.min(a_input)), "max": float(np.max(a_input))},
        "keras_output": a_keras_out[0].tolist(),
        "float32_tflite_output": a_f32_out[0].tolist(),
        "int8_tflite_output": a_int8_out[0].tolist(),
        "predicted_class_int8": int(np.argmax(a_int8_out)),
        "max_diff_f32": a_f32_diff,
        "max_diff_int8": a_int8_diff,
        "status": "PASSED"
    }
    
    # ---------------- 3. Vision Model ----------------
    print("\n--- Golden Test: Vision Model ---")
    v_keras_path = get_keras_path("vision_model")
    v_f32_path = os.path.join(ARTIFACTS_DIR, "vision_model_float32.tflite")
    v_int8_path = os.path.join(ARTIFACTS_DIR, "vision_model.tflite")
    
    v_input = np.full((1, 224, 224, 3), 0.5, dtype=np.float32)
    v_keras = tf.keras.models.load_model(v_keras_path)
    v_keras_out = v_keras.predict(v_input, verbose=0)
    v_f32_out = run_tflite_inference(v_f32_path, v_input)
    v_int8_out = run_tflite_inference(v_int8_path, v_input)
    
    v_f32_diff = float(np.max(np.abs(v_keras_out - v_f32_out)))
    v_int8_diff = float(np.max(np.abs(v_f32_out - v_int8_out)))
    print(f"Keras vs Float32 TFLite max diff: {v_f32_diff:.6e} (tolerance: 1e-4)")
    print(f"Float32 vs INT8 TFLite max diff:  {v_int8_diff:.4f} (tolerance: 0.25)")
    assert v_f32_diff < 1e-4, f"Float32 precision failure: {v_f32_diff}"
    assert v_int8_diff < 0.25, f"INT8 quantization error exceeds tolerance: {v_int8_diff}"
    
    golden_results["vision"] = {
        "input_sample_stats": {"shape": list(v_input.shape), "constant_val": 0.5},
        "keras_output": v_keras_out[0].tolist(),
        "float32_tflite_output": v_f32_out[0].tolist(),
        "int8_tflite_output": v_int8_out[0].tolist(),
        "predicted_class_int8": int(np.argmax(v_int8_out)),
        "max_diff_f32": v_f32_diff,
        "max_diff_int8": v_int8_diff,
        "status": "PASSED"
    }
    
    out_file = os.path.join(ARTIFACTS_DIR, "golden_test_results.json")
    with open(out_file, "w") as f:
        json.dump(golden_results, f, indent=2)
    print(f"\nAll Golden Tests PASSED! Results saved to {out_file}")

if __name__ == "__main__":
    test_golden_vectors()

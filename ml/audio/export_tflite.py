"""
Audio Model TFLite Export (FLOAT32 and INT8 Quantized).
Exports:
- ml/artifacts/audio_model_float32.tflite
- ml/artifacts/audio_model.tflite (INT8 full integer quantized)
"""

import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))
import numpy as np
import tensorflow as tf
from ml.common.quantization import convert_to_tflite_float32, convert_to_tflite_int8
from ml.audio.prepare_dataset import prepare_audio_dataset

def export_audio_tflite():
    model_path = "ml/artifacts/audio_model.keras"
    if not os.path.exists(model_path):
        print(f"Error: {model_path} not found. Run train.py first.")
        return
        
    model = tf.keras.models.load_model(model_path)
    os.makedirs("ml/artifacts", exist_ok=True)
    
    # 1. Export Float32
    f32_bytes = convert_to_tflite_float32(model, input_shape=[1, 16, 16])
    f32_path = "ml/artifacts/audio_model_float32.tflite"
    with open(f32_path, "wb") as f:
        f.write(f32_bytes)
    f32_size = os.path.getsize(f32_path)
    print(f"Exported FLOAT32 reference model: {f32_path} ({f32_size} bytes, {f32_size/1024:.2f} KB)")
    
    # 2. Representative Dataset for INT8 Quantization
    (tr_x, _, _), _, _ = prepare_audio_dataset()
    
    def representative_dataset_gen():
        for i in range(min(100, len(tr_x))):
            sample = tr_x[i:i+1].astype(np.float32)
            yield [sample]
            
    int8_bytes = convert_to_tflite_int8(model, representative_dataset_gen, input_shape=[1, 16, 16])
    int8_path = "ml/artifacts/audio_model.tflite"
    with open(int8_path, "wb") as f:
        f.write(int8_bytes)
    int8_size = os.path.getsize(int8_path)
    print(f"Exported INT8 quantized model: {int8_path} ({int8_size} bytes, {int8_size/1024:.2f} KB)")
    
    # Verify input/output tensors with TFLite interpreter
    interpreter = tf.lite.Interpreter(model_path=int8_path)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print("\nINT8 TFLite Tensor Details:")
    print(f"  Input:  name={input_details[0]['name']}, shape={input_details[0]['shape'].tolist()}, dtype={input_details[0]['dtype']}")
    print(f"  Output: name={output_details[0]['name']}, shape={output_details[0]['shape'].tolist()}, dtype={output_details[0]['dtype']}")
    print(f"  Compression: {f32_size} -> {int8_size} bytes ({100.0 * (1 - int8_size/f32_size):.1f}% reduction)")

if __name__ == "__main__":
    export_audio_tflite()

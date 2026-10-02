"""
Phase 8: INT8 TFLite Export for all 3 models.
Uses concrete function approach (TF 2.16 compatible).
Generates representative dataset for INT8 quantization calibration.
"""

import os, sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
import numpy as np
import tensorflow as tf


def export_to_tflite(keras_path, output_prefix, input_shape, representative_data,
                     expected_output_size):
    """Export Keras model to Float32 and INT8 TFLite."""
    print(f"\n{'='*60}")
    print(f"Exporting: {keras_path}")
    print(f"  Input shape: {input_shape}")
    print(f"  Expected output size: {expected_output_size}")
    print(f"{'='*60}")
    
    model = tf.keras.models.load_model(keras_path)
    
    # Get concrete function
    @tf.function(input_signature=[tf.TensorSpec(shape=[1] + list(input_shape), dtype=tf.float32)])
    def serve(x):
        return model(x, training=False)
    
    concrete_func = serve.get_concrete_function()
    
    # Float32
    float_path = f"{output_prefix}_float32.tflite"
    converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete_func])
    float_model = converter.convert()
    with open(float_path, 'wb') as f:
        f.write(float_model)
    print(f"  Float32: {os.path.getsize(float_path)} bytes -> {float_path}")
    
    # INT8 with representative dataset
    int8_path = f"{output_prefix}.tflite"
    converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete_func])
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.uint8
    converter.inference_output_type = tf.uint8
    
    def representative_gen():
        for sample in representative_data:
            yield [sample.reshape([1] + list(input_shape)).astype(np.float32)]
    
    converter.representative_dataset = representative_gen
    int8_model = converter.convert()
    with open(int8_path, 'wb') as f:
        f.write(int8_model)
    print(f"  INT8:    {os.path.getsize(int8_path)} bytes -> {int8_path}")
    
    # Verify
    interpreter = tf.lite.Interpreter(model_path=int8_path)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print(f"\n  Verification:")
    print(f"    Input:  shape={input_details[0]['shape']}, dtype={input_details[0]['dtype']}")
    print(f"    Output: shape={output_details[0]['shape']}, dtype={output_details[0]['dtype']}")
    
    # Sanity run
    test_input = np.zeros([1] + list(input_shape), dtype=input_details[0]['dtype'])
    if input_details[0]['dtype'] == np.uint8:
        test_input = np.full([1] + list(input_shape), 128, dtype=np.uint8)
    interpreter.set_tensor(input_details[0]['index'], test_input)
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]['index'])
    print(f"    Output shape: {output.shape}")
    print(f"    Output sum finite: {np.isfinite(output).all()}")
    assert output.shape[-1] == expected_output_size, f"Output size mismatch: {output.shape[-1]} != {expected_output_size}"
    print(f"    ✓ Output size matches Android interface")
    
    return int8_path, float_path


def export_all():
    print("=" * 60)
    print("Phase 8: TFLite Export Pipeline")
    print("=" * 60)
    
    artifacts_dir = "ml/artifacts"
    os.makedirs(artifacts_dir, exist_ok=True)
    
    results = {}
    
    # 1. Motion model
    motion_keras = os.path.join(artifacts_dir, "motion_model_phase8.keras")
    if os.path.exists(motion_keras):
        # Generate representative data
        from ml.motion.prepare_dataset_v2 import prepare_mobiact_dataset
        (tr_x, _), _, _ = prepare_mobiact_dataset()
        rep_data = tr_x[:200]
        
        int8_path, float_path = export_to_tflite(
            motion_keras,
            os.path.join(artifacts_dir, "motion_model_phase8"),
            input_shape=(16,),
            representative_data=rep_data,
            expected_output_size=4
        )
        results['motion'] = {'int8': int8_path, 'float32': float_path}
    else:
        print("⚠ Motion model not found, skipping")
    
    # 2. Audio model
    audio_keras = os.path.join(artifacts_dir, "audio_model_phase8.keras")
    if os.path.exists(audio_keras):
        from ml.audio.prepare_dataset_v2 import prepare_audio_dataset_v2
        (tr_x, _), _, _ = prepare_audio_dataset_v2()
        
        # Load normalization params
        norm_path = os.path.join(artifacts_dir, "audio_norm_params.npz")
        if os.path.exists(norm_path):
            params = np.load(norm_path)
            mean, std = float(params['mean']), float(params['std'])
            tr_x = (tr_x.reshape(-1, 16, 16) - mean) / std
        else:
            tr_x = tr_x.reshape(-1, 16, 16)
        
        rep_data = tr_x[:200]
        
        int8_path, float_path = export_to_tflite(
            audio_keras,
            os.path.join(artifacts_dir, "audio_model_phase8"),
            input_shape=(16, 16),
            representative_data=rep_data,
            expected_output_size=7
        )
        results['audio'] = {'int8': int8_path, 'float32': float_path}
    else:
        print("⚠ Audio model not found, skipping")
    
    # 3. Vision model
    vision_keras = os.path.join(artifacts_dir, "vision_model_phase8.keras")
    if os.path.exists(vision_keras):
        from ml.vision.prepare_dataset_v2 import prepare_vision_dataset_v2
        (tr_x, _), _, _ = prepare_vision_dataset_v2()
        rep_data = tr_x[:100]
        
        int8_path, float_path = export_to_tflite(
            vision_keras,
            os.path.join(artifacts_dir, "vision_model_phase8"),
            input_shape=(224, 224, 3),
            representative_data=rep_data,
            expected_output_size=7
        )
        results['vision'] = {'int8': int8_path, 'float32': float_path}
    else:
        print("⚠ Vision model not found, skipping")
    
    # Summary
    print("\n" + "=" * 60)
    print("Export Summary:")
    print("=" * 60)
    for name, paths in results.items():
        int8_size = os.path.getsize(paths['int8'])
        float_size = os.path.getsize(paths['float32'])
        print(f"  {name}: INT8={int8_size} bytes, Float32={float_size} bytes, "
              f"Compression={float_size/int8_size:.1f}x")
    
    return results


if __name__ == "__main__":
    export_all()

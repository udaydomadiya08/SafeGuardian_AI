from typing import Callable, Generator, List
import tensorflow as tf
import numpy as np

def convert_to_tflite_float32(keras_model: tf.keras.Model, input_shape: List[int] = None) -> bytes:
    """Exports a standard Float32 TFLite model cleanly across Keras 3 and TF 2.16."""
    if input_shape is not None:
        @tf.function
        def serve(x):
            return keras_model(x)
        cf = serve.get_concrete_function(tf.TensorSpec(input_shape, tf.float32))
        converter = tf.lite.TFLiteConverter.from_concrete_functions([cf])
    else:
        try:
            converter = tf.lite.TFLiteConverter.from_keras_model(keras_model)
        except Exception:
            in_shape = [1] + list(keras_model.input_shape[1:])
            @tf.function
            def serve(x):
                return keras_model(x)
            cf = serve.get_concrete_function(tf.TensorSpec(in_shape, tf.float32))
            converter = tf.lite.TFLiteConverter.from_concrete_functions([cf])
            
    return converter.convert()

def convert_to_tflite_int8(
    keras_model: tf.keras.Model,
    representative_dataset_gen: Callable[[], Generator[list, None, None]],
    input_shape: List[int] = None
) -> bytes:
    """Exports full INT8 quantized TFLite model using representative dataset."""
    if input_shape is not None:
        @tf.function
        def serve(x):
            return keras_model(x)
        cf = serve.get_concrete_function(tf.TensorSpec(input_shape, tf.float32))
        converter = tf.lite.TFLiteConverter.from_concrete_functions([cf])
    else:
        in_shape = [1] + list(keras_model.input_shape[1:])
        @tf.function
        def serve(x):
            return keras_model(x)
        cf = serve.get_concrete_function(tf.TensorSpec(in_shape, tf.float32))
        converter = tf.lite.TFLiteConverter.from_concrete_functions([cf])
        
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset_gen
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.float32
    converter.inference_output_type = tf.float32
    
    return converter.convert()

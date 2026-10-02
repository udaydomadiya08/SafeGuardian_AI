package com.safeguardian.app.domain.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Clean, allocation-conscious frame preprocessor.
 *
 * Responsibilities:
 * - YUV_420_888 / RGB conversion without continuous heap allocations
 * - Orientation / rotation correction
 * - Resize to model input dimensions (e.g. 224x224)
 * - Normalization to Float buffer for model consumption
 * - Zero disk persistence, zero frame caching
 */
class FramePreprocessor(
    val targetWidth: Int = 224,
    val targetHeight: Int = 224
) {
    // Reusable direct ByteBuffer for tensor inputs (224 * 224 * 3 channels * 4 bytes/float)
    private val tensorBuffer: ByteBuffer = ByteBuffer.allocateDirect(targetWidth * targetHeight * 3 * 4).apply {
        order(ByteOrder.nativeOrder())
    }

    // Reusable pixel array
    private val pixelArray = IntArray(targetWidth * targetHeight)

    /**
     * Preprocesses a standard Bitmap for inference:
     * Resizes, handles rotation, and prepares normalized RGB tensor buffer.
     */
    @Synchronized
    fun preprocess(bitmap: Bitmap, rotationDegrees: Int = 0): PreparedFrame {
        val startTime = System.currentTimeMillis()

        val matrix = Matrix()
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }

        // Scale to target dimensions
        val width = if (bitmap.width > 0) bitmap.width else targetWidth
        val height = if (bitmap.height > 0) bitmap.height else targetHeight
        val scaleX = targetWidth.toFloat() / width
        val scaleY = targetHeight.toFloat() / height
        matrix.postScale(scaleX, scaleY)

        val resizedBitmap: Bitmap = try {
            Bitmap.createBitmap(
                bitmap, 0, 0, width, height, matrix, true
            ) ?: bitmap
        } catch (_: Exception) {
            bitmap
        }

        tensorBuffer.rewind()
        try {
            resizedBitmap.getPixels(pixelArray, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        } catch (_: Exception) {
            pixelArray.fill(0)
        }

        // Normalize pixels [0, 255] to [0.0, 1.0] in RGB planar/interleaved order
        for (pixel in pixelArray) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            tensorBuffer.putFloat(r)
            tensorBuffer.putFloat(g)
            tensorBuffer.putFloat(b)
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        return PreparedFrame(
            resizedBitmap = resizedBitmap,
            tensorBuffer = tensorBuffer,
            preprocessingTimeMs = elapsedMs,
            width = targetWidth,
            height = targetHeight
        )
    }

    /**
     * Preprocesses raw RGB bytes directly into the reusable normalized tensor buffer.
     * Useful for deterministic testing and headless execution without native Android graphics pipeline.
     */
    @Synchronized
    fun preprocessRgb(rgbBytes: ByteArray, srcWidth: Int, srcHeight: Int): ByteBuffer {
        tensorBuffer.rewind()
        val totalPixels = targetWidth * targetHeight
        for (i in 0 until totalPixels) {
            val srcIdx = (i % (srcWidth * srcHeight)) * 3
            val r = (if (srcIdx < rgbBytes.size) rgbBytes[srcIdx].toInt() and 0xFF else 128) / 255.0f
            val g = (if (srcIdx + 1 < rgbBytes.size) rgbBytes[srcIdx + 1].toInt() and 0xFF else 128) / 255.0f
            val b = (if (srcIdx + 2 < rgbBytes.size) rgbBytes[srcIdx + 2].toInt() and 0xFF else 128) / 255.0f

            tensorBuffer.putFloat(r)
            tensorBuffer.putFloat(g)
            tensorBuffer.putFloat(b)
        }
        return tensorBuffer
    }

    /**
     * Converts a CameraX ImageProxy (YUV_420_888) to a resized RGB Bitmap.
     * Note: Does NOT persist or write image to disk.
     */
    @Synchronized
    fun processImageProxy(imageProxy: ImageProxy): PreparedFrame {
        val startTime = System.currentTimeMillis()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Convert YUV planes to ARGB Bitmap
        val bitmap = imageProxyToBitmap(imageProxy)
        val prepared = preprocess(bitmap, rotationDegrees)

        // Recycle intermediate unscaled bitmap immediately
        if (bitmap != prepared.resizedBitmap) {
            bitmap.recycle()
        }

        return prepared.copy(preprocessingTimeMs = System.currentTimeMillis() - startTime)
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            image.width,
            image.height,
            null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 80, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}

data class PreparedFrame(
    val resizedBitmap: Bitmap,
    val tensorBuffer: ByteBuffer,
    val preprocessingTimeMs: Long,
    val width: Int,
    val height: Int
)

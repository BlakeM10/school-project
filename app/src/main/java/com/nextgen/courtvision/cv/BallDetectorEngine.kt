package com.nextgen.courtvision.cv

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.nextgen.courtvision.domain.cv.BallDetection
import com.nextgen.courtvision.domain.cv.NormBox
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * TensorFlow Lite ball detector (MobileNet-SSD-family model with metadata).
 * The starter model is COCO-pretrained ("sports ball" class); the fine-tuned
 * basketball model from ml-tools replaces the same asset file without any code
 * change, per the proposal's encapsulation requirement.
 */
class BallDetectorEngine(context: Context) {

    private val detector: ObjectDetector? = try {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(3)
            .setScoreThreshold(MIN_CONFIDENCE)
            .build()
        ObjectDetector.createFromFileAndOptions(context, MODEL_ASSET, options)
    } catch (e: Exception) {
        Log.w(TAG, "Ball model unavailable — ball tracking disabled", e)
        null
    }

    val isAvailable: Boolean get() = detector != null

    /** Synchronous detection on the caller's (analysis) thread. */
    fun detect(bitmap: Bitmap, timestampMs: Long): BallDetection? {
        val detector = detector ?: return null
        val results = try {
            detector.detect(TensorImage.fromBitmap(bitmap))
        } catch (e: Exception) {
            Log.w(TAG, "Ball detection failed for frame", e)
            return null
        }

        val best = results
            .filter { detection ->
                detection.categories.any { it.label.lowercase() in BALL_LABELS }
            }
            .maxByOrNull { it.categories.maxOf { c -> c.score } }
            ?: return null

        val box = best.boundingBox
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return BallDetection(
            timestampMs = timestampMs,
            box = NormBox(
                left = box.left / w,
                top = box.top / h,
                right = box.right / w,
                bottom = box.bottom / h,
            ),
            confidence = best.categories.maxOf { it.score },
        )
    }

    fun close() {
        detector?.close()
    }

    private companion object {
        const val TAG = "BallDetectorEngine"
        const val MODEL_ASSET = "models/ball_detector.tflite"
        const val MIN_CONFIDENCE = 0.4f

        // COCO label + labels a fine-tuned single-class model might use
        val BALL_LABELS = setOf("sports ball", "basketball", "ball")
    }
}

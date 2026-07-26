package com.nextgen.courtvision.cv

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.nextgen.courtvision.domain.cv.Landmark
import com.nextgen.courtvision.domain.cv.PoseFrame

/**
 * MediaPipe BlazePose wrapper (LIVE_STREAM mode). Returns null-object behaviour
 * when the model asset has not been fetched yet (see ml-tools/fetch_models.sh)
 * so the app runs, with detection disabled, before models are installed.
 */
class PoseEstimator(
    context: Context,
    private val onPose: (PoseFrame) -> Unit,
) {
    private val landmarker: PoseLandmarker? = try {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setResultListener { result, _ ->
                val pose = result.landmarks().firstOrNull() ?: return@setResultListener
                onPose(
                    PoseFrame(
                        timestampMs = result.timestampMs(),
                        landmarks = pose.map {
                            Landmark(
                                x = it.x(),
                                y = it.y(),
                                z = it.z(),
                                visibility = it.visibility().orElse(1f),
                            )
                        },
                    ),
                )
            }
            .build()
        PoseLandmarker.createFromOptions(context, options)
    } catch (e: Exception) {
        Log.w(TAG, "Pose model unavailable — pose tracking disabled", e)
        null
    }

    val isAvailable: Boolean get() = landmarker != null

    fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        landmarker?.detectAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
    }

    fun close() {
        landmarker?.close()
    }

    private companion object {
        const val TAG = "PoseEstimator"
        const val MODEL_ASSET = "models/pose_landmarker_lite.task"
    }
}

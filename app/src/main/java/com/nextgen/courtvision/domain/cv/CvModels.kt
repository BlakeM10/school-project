package com.nextgen.courtvision.domain.cv

/**
 * Pure CV domain types. All coordinates are normalized to the camera image
 * ([0,1] on both axes, y increasing DOWNWARD — image convention), keeping the
 * detection algorithms independent of camera resolution and testable on the JVM.
 */

data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 1f,
)

/** One BlazePose result: 33 landmarks in MediaPipe's documented order. */
data class PoseFrame(
    val timestampMs: Long,
    val landmarks: List<Landmark>,
) {
    fun landmark(id: Int): Landmark? = landmarks.getOrNull(id)
}

/** MediaPipe BlazePose landmark indices used by the detectors. */
object PoseLandmarkIds {
    const val NOSE = 0
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val COUNT = 33
}

data class NormBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class BallDetection(
    val timestampMs: Long,
    val box: NormBox,
    val confidence: Float,
)

data class ShotEvent(
    val timestampMs: Long,
    val made: Boolean,
    val releaseTimeMs: Long,
)

/** Event surface of the CV pipeline (the proposal's encapsulated interface). */
interface CVPipelineListener {
    fun onPoseFrame(frame: PoseFrame)
    fun onBallDetected(ball: BallDetection)
    fun onShotDetected(event: ShotEvent)
    fun onDribbleDetected(intervalMs: Long)
    fun onReactionMeasured(reactionMs: Long)
}

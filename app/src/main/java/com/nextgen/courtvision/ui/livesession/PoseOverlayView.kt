package com.nextgen.courtvision.ui.livesession

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.nextgen.courtvision.domain.cv.BallDetection
import com.nextgen.courtvision.domain.cv.PoseFrame
import com.nextgen.courtvision.domain.cv.PoseLandmarkIds as L
import kotlin.math.max

/**
 * Green pose skeleton + ball bounding box over the camera viewfinder, drawn
 * with Android Canvas per the proposal's Live Session design. Landmarks are
 * normalized image coordinates; PreviewView uses FILL_CENTER (center-crop), so
 * the same scale+offset mapping is applied here to line the overlay up.
 */
class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var pose: PoseFrame? = null
    private var ball: BallDetection? = null
    private var imageAspect: Float = 3f / 4f // width / height of analysis frames

    private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }
    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3
    }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2.5f
    }

    fun setImageAspect(width: Int, height: Int) {
        if (width > 0 && height > 0) imageAspect = width.toFloat() / height
    }

    fun update(pose: PoseFrame?, ball: BallDetection?) {
        this.pose = pose
        this.ball = ball
        invalidate()
    }

    fun clear() {
        pose = null
        ball = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        // FILL_CENTER mapping: uniform scale that covers the view, centered.
        val viewAspect = width.toFloat() / height
        val scaleX: Float
        val scaleY: Float
        if (imageAspect > viewAspect) {
            scaleY = height.toFloat()
            scaleX = height * imageAspect
        } else {
            scaleX = width.toFloat()
            scaleY = width / imageAspect
        }
        val offsetX = (width - scaleX) / 2f
        val offsetY = (height - scaleY) / 2f

        fun mapX(x: Float) = offsetX + x * scaleX
        fun mapY(y: Float) = offsetY + y * scaleY

        pose?.let { frame ->
            for ((a, b) in BONES) {
                val la = frame.landmark(a) ?: continue
                val lb = frame.landmark(b) ?: continue
                if (la.visibility < MIN_VISIBILITY || lb.visibility < MIN_VISIBILITY) continue
                canvas.drawLine(mapX(la.x), mapY(la.y), mapX(lb.x), mapY(lb.y), bonePaint)
            }
            val radius = max(4f, resources.displayMetrics.density * 3.5f)
            for (landmark in frame.landmarks) {
                if (landmark.visibility < MIN_VISIBILITY) continue
                canvas.drawCircle(mapX(landmark.x), mapY(landmark.y), radius, landmarkPaint)
            }
        }

        ball?.let { detection ->
            canvas.drawRect(
                mapX(detection.box.left),
                mapY(detection.box.top),
                mapX(detection.box.right),
                mapY(detection.box.bottom),
                boxPaint,
            )
        }
    }

    private companion object {
        const val MIN_VISIBILITY = 0.5f

        // BlazePose torso + arms + legs connections (drawing subset)
        val BONES = listOf(
            L.LEFT_SHOULDER to L.RIGHT_SHOULDER,
            L.LEFT_SHOULDER to L.LEFT_ELBOW,
            L.LEFT_ELBOW to L.LEFT_WRIST,
            L.RIGHT_SHOULDER to L.RIGHT_ELBOW,
            L.RIGHT_ELBOW to L.RIGHT_WRIST,
            L.LEFT_SHOULDER to L.LEFT_HIP,
            L.RIGHT_SHOULDER to L.RIGHT_HIP,
            L.LEFT_HIP to L.RIGHT_HIP,
            L.LEFT_HIP to 25, // left knee
            25 to 27, // left ankle
            L.RIGHT_HIP to 26, // right knee
            26 to 28, // right ankle
        )
    }
}

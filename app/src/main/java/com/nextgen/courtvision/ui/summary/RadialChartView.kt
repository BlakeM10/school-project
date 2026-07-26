package com.nextgen.courtvision.ui.summary

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.nextgen.courtvision.R
import kotlin.math.min

/**
 * Gradient progress ring for accuracy (custom radial chart per the proposal's
 * UI spec, upgraded to the premium design language): a blue-gradient sweep arc
 * with an animated fill and the percentage rendered in the centre.
 */
class RadialChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var progressPct: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 100f)
            invalidate()
        }

    private var animator: ValueAnimator? = null

    /** Animates the ring from zero to the target — call from onViewCreated. */
    fun setProgressAnimated(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, target.coerceIn(0f, 100f)).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            addUpdateListener { progressPct = it.animatedValue as Float }
            start()
        }
    }

    private val strokeWidthPx = resources.displayMetrics.density * 15

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.cv_surface_variant)
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = resources.displayMetrics.scaledDensity * 40
        color = ContextCompat.getColor(context, R.color.cv_on_surface)
        isFakeBoldText = true
    }

    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = resources.displayMetrics.scaledDensity * 12
        color = ContextCompat.getColor(context, R.color.cv_on_surface_variant)
    }

    private val arcBounds = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Sweep gradient rotated so the colour ramp follows the arc direction.
        val gradient = SweepGradient(
            w / 2f, h / 2f,
            intArrayOf(
                ContextCompat.getColor(context, R.color.blue_dark),
                ContextCompat.getColor(context, R.color.blue_mid),
                ContextCompat.getColor(context, R.color.blue_light),
                ContextCompat.getColor(context, R.color.blue_dark),
            ),
            floatArrayOf(0f, 0.45f, 0.8f, 1f),
        )
        val rotate = Matrix().apply { setRotate(START_ANGLE, w / 2f, h / 2f) }
        gradient.setLocalMatrix(rotate)
        arcPaint.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val inset = strokeWidthPx / 2 + resources.displayMetrics.density * 4
        arcBounds.set(
            (width - size) / 2 + inset,
            (height - size) / 2 + inset,
            (width + size) / 2 - inset,
            (height + size) / 2 - inset,
        )
        canvas.drawArc(arcBounds, START_ANGLE, SWEEP_MAX, false, trackPaint)
        canvas.drawArc(arcBounds, START_ANGLE, SWEEP_MAX * progressPct / 100f, false, arcPaint)

        val centerY = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2
        canvas.drawText("${progressPct.toInt()}%", width / 2f, centerY - 4, textPaint)
        canvas.drawText(
            context.getString(R.string.summary_accuracy_caption),
            width / 2f,
            centerY + textPaint.textSize / 2 + 10,
            captionPaint,
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    private companion object {
        const val START_ANGLE = 135f
        const val SWEEP_MAX = 270f
    }
}

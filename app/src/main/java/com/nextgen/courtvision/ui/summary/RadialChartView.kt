package com.nextgen.courtvision.ui.summary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.nextgen.courtvision.R
import kotlin.math.min

/**
 * Custom radial accuracy indicator for the Session Summary screen — a sweep
 * arc from 0–100% with the percentage rendered in the centre, as specified in
 * the proposal's UI design ("custom radial chart view").
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

    private val strokeWidthPx = resources.displayMetrics.density * 14

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = 0x22888888
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.court_orange)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = resources.displayMetrics.scaledDensity * 32
        color = ContextCompat.getColor(context, R.color.court_navy)
        isFakeBoldText = true
    }

    private val arcBounds = RectF()

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
        canvas.drawText("${progressPct.toInt()}%", width / 2f, centerY, textPaint)
    }

    private companion object {
        const val START_ANGLE = 135f
        const val SWEEP_MAX = 270f
    }
}

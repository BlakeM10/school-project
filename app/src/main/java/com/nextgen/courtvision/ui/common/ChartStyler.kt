package com.nextgen.courtvision.ui.common

import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.nextgen.courtvision.R

/**
 * One place for the app's chart look: smooth gradient-filled cubic lines,
 * soft axes, and entry animations. Keeps every screen's data-viz identical
 * (reusable component requirement) instead of restyling per fragment.
 */
object ChartStyler {

    fun applyAccuracyLine(chart: LineChart, entries: List<Entry>, label: String) {
        val context = chart.context
        val accent = ContextCompat.getColor(context, R.color.chart_accent)

        val dataSet = LineDataSet(entries, label).apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.18f
            color = accent
            lineWidth = 3f
            setCircleColor(ContextCompat.getColor(context, R.color.chart_accent_deep))
            circleRadius = 4f
            circleHoleRadius = 2f
            setDrawValues(false)
            setDrawFilled(true)
            fillDrawable = ContextCompat.getDrawable(context, R.drawable.chart_fill_gradient)
            highLightColor = accent
        }

        chart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                gridColor = ContextCompat.getColor(context, R.color.cv_outline)
                gridLineWidth = 0.6f
                textColor = ContextCompat.getColor(context, R.color.cv_on_surface_variant)
                setDrawAxisLine(false)
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                granularity = 1f
                textColor = ContextCompat.getColor(context, R.color.cv_on_surface_variant)
            }
            setTouchEnabled(true)
            setPinchZoom(true)
            isDoubleTapToZoomEnabled = true
            setExtraOffsets(4f, 8f, 4f, 4f)
            animateX(650)
            invalidate()
        }
    }

    fun applyShotDistribution(
        chart: BarChart,
        made: Int,
        missed: Int,
        madeLabel: String,
        missedLabel: String,
    ) {
        val context = chart.context
        val dataSet = BarDataSet(
            listOf(BarEntry(0f, made.toFloat()), BarEntry(1f, missed.toFloat())),
            "",
        ).apply {
            colors = listOf(
                ContextCompat.getColor(context, R.color.chart_accent),
                ContextCompat.getColor(context, R.color.cv_outline),
            )
            valueTextSize = 12f
            valueTextColor = ContextCompat.getColor(context, R.color.cv_on_surface_variant)
        }

        chart.apply {
            data = BarData(dataSet).apply { barWidth = 0.5f }
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                gridColor = ContextCompat.getColor(context, R.color.cv_outline)
                setDrawAxisLine(false)
                textColor = ContextCompat.getColor(context, R.color.cv_on_surface_variant)
            }
            xAxis.apply {
                setDrawGridLines(false)
                setDrawAxisLine(false)
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(listOf(madeLabel, missedLabel))
                textColor = ContextCompat.getColor(context, R.color.cv_on_surface_variant)
            }
            setTouchEnabled(false)
            animateY(600)
            invalidate()
        }
    }
}

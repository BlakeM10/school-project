package com.nextgen.courtvision.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.google.android.material.card.MaterialCardView
import com.nextgen.courtvision.databinding.ViewStatCardBinding

/**
 * Reusable statistic tile (value + label, optional trend line) used across the
 * player dashboard, progress screen, coach dashboard, and session summary —
 * one component instead of per-screen card markup.
 */
class StatCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val binding =
        ViewStatCardBinding.inflate(LayoutInflater.from(context), this)

    init {
        radius = resources.displayMetrics.density * 20
        cardElevation = resources.displayMetrics.density * 1.5f
    }

    fun bind(value: String, label: String, trend: String? = null) {
        binding.statValue.text = value
        binding.statLabel.text = label
        binding.statTrend.text = trend
        binding.statTrend.visibility = if (trend.isNullOrBlank()) GONE else VISIBLE
    }
}

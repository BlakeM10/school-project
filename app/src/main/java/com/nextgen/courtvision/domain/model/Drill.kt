package com.nextgen.courtvision.domain.model

/** Wire names match the `drills.measures` array values in Firestore and drive
 *  which CV detectors the Live Session screen activates for a drill. */
enum class Measure(val wireName: String) {
    SHOTS("shots"),
    RELEASE_TIME("releaseTime"),
    DRIBBLES("dribbles"),
    REACTION_TIME("reactionTime");

    companion object {
        fun fromWire(value: String?): Measure? = entries.firstOrNull { it.wireName == value }
    }
}

enum class DrillCategory(val wireName: String) {
    SHOOTING("shooting"),
    DRIBBLING("dribbling"),
    REACTION("reaction"),
    COMBINED("combined");

    companion object {
        fun fromWire(value: String?): DrillCategory? =
            entries.firstOrNull { it.wireName == value }
    }
}

data class Drill(
    val id: String,
    val name: String,
    val category: DrillCategory,
    val instructions: String,
    val targetMetrics: Map<String, Double>,
    val durationSec: Int,
    val measures: List<Measure>,
    val sortOrder: Int,
)

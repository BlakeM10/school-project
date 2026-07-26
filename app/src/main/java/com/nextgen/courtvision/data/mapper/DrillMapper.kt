package com.nextgen.courtvision.data.mapper

import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.DrillCategory
import com.nextgen.courtvision.domain.model.Measure

/**
 * Maps `drills/{drillId}` documents (docs/DATABASE_SCHEMA.md §2.3) to domain
 * models. Read-only — the catalogue is seeded server-side, so there is no
 * toDocument direction. Firestore reads numbers back as Long/Double, hence the
 * Number casts.
 */
object DrillMapper {

    fun fromDocument(id: String, data: Map<String, Any?>): Drill? {
        val name = data["name"] as? String ?: return null
        val category = DrillCategory.fromWire(data["category"] as? String) ?: return null
        val instructions = data["instructions"] as? String ?: return null
        val durationSec = (data["durationSec"] as? Number)?.toInt() ?: return null

        val targetMetrics = (data["targetMetrics"] as? Map<*, *>).orEmpty()
            .mapNotNull { (key, value) ->
                val metric = key as? String ?: return@mapNotNull null
                val number = (value as? Number)?.toDouble() ?: return@mapNotNull null
                metric to number
            }.toMap()

        val measures = (data["measures"] as? List<*>).orEmpty()
            .mapNotNull { Measure.fromWire(it as? String) }
        if (measures.isEmpty()) return null

        return Drill(
            id = id,
            name = name,
            category = category,
            instructions = instructions,
            targetMetrics = targetMetrics,
            durationSec = durationSec,
            measures = measures,
            sortOrder = (data["sortOrder"] as? Number)?.toInt() ?: Int.MAX_VALUE,
        )
    }
}

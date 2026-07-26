package com.nextgen.courtvision.data.mapper

import com.nextgen.courtvision.domain.model.DrillCategory
import com.nextgen.courtvision.domain.model.Measure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrillMapperTest {

    // Firestore reads integers back as Long — the fixture mirrors that.
    private val drillDoc = mapOf(
        "name" to "Free Throw Series",
        "category" to "shooting",
        "instructions" to "Take 20 free throws.",
        "targetMetrics" to mapOf("shots" to 20L, "accuracyPct" to 60L),
        "durationSec" to 300L,
        "measures" to listOf("shots", "releaseTime"),
        "sortOrder" to 1L,
    )

    @Test
    fun `maps a complete drill document`() {
        val drill = DrillMapper.fromDocument("free_throw_series", drillDoc)!!

        assertEquals("free_throw_series", drill.id)
        assertEquals(DrillCategory.SHOOTING, drill.category)
        assertEquals(300, drill.durationSec)
        assertEquals(listOf(Measure.SHOTS, Measure.RELEASE_TIME), drill.measures)
        assertEquals(20.0, drill.targetMetrics["shots"]!!, 0.0)
        assertEquals(60.0, drill.targetMetrics["accuracyPct"]!!, 0.0)
        assertEquals(1, drill.sortOrder)
    }

    @Test
    fun `unknown measures are dropped, known ones kept`() {
        val drill = DrillMapper.fromDocument(
            "d1",
            drillDoc + mapOf("measures" to listOf("shots", "totallyUnknown")),
        )!!
        assertEquals(listOf(Measure.SHOTS), drill.measures)
    }

    @Test
    fun `document with no valid measures is rejected`() {
        assertNull(DrillMapper.fromDocument("d1", drillDoc + mapOf("measures" to emptyList<String>())))
        assertNull(DrillMapper.fromDocument("d1", drillDoc + mapOf("measures" to listOf("bogus"))))
    }

    @Test
    fun `unknown category is rejected`() {
        assertNull(DrillMapper.fromDocument("d1", drillDoc + mapOf("category" to "swimming")))
    }

    @Test
    fun `missing required fields are rejected`() {
        assertNull(DrillMapper.fromDocument("d1", drillDoc - "name"))
        assertNull(DrillMapper.fromDocument("d1", drillDoc - "instructions"))
        assertNull(DrillMapper.fromDocument("d1", drillDoc - "durationSec"))
    }

    @Test
    fun `missing sortOrder sinks the drill to the end instead of rejecting it`() {
        val drill = DrillMapper.fromDocument("d1", drillDoc - "sortOrder")!!
        assertEquals(Int.MAX_VALUE, drill.sortOrder)
    }
}

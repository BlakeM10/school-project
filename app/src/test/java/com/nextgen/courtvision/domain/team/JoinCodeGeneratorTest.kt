package com.nextgen.courtvision.domain.team

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinCodeGeneratorTest {

    @Test
    fun `codes have the documented length and alphabet`() {
        repeat(200) {
            val code = JoinCodeGenerator.generate()
            assertEquals(JoinCodeGenerator.CODE_LENGTH, code.length)
            assertTrue(
                "unexpected character in $code",
                code.all { it in JoinCodeGenerator.ALPHABET },
            )
        }
    }

    @Test
    fun `alphabet excludes easily confused characters`() {
        for (confusing in "0O1IL8") {
            assertTrue(confusing !in JoinCodeGenerator.ALPHABET)
        }
    }

    @Test
    fun `seeded generation is deterministic, different seeds differ`() {
        val a = JoinCodeGenerator.generate(Random(42))
        val b = JoinCodeGenerator.generate(Random(42))
        val c = JoinCodeGenerator.generate(Random(43))
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `normalise trims and uppercases user input`() {
        assertEquals("K7PQ2M", JoinCodeGenerator.normalise("  k7pq2m "))
        assertEquals("K7PQ2M", JoinCodeGenerator.normalise("K7PQ2M"))
    }
}

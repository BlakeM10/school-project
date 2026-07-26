package com.nextgen.courtvision.domain.team

import kotlin.random.Random

/**
 * Human-typeable team join codes. The alphabet drops easily confused characters
 * (0/O, 1/I/L); 6 characters over 30 symbols gives ~729M combinations, ample
 * for a single-club pilot without needing a collision check.
 */
object JoinCodeGenerator {

    const val CODE_LENGTH = 6
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ2345679"

    fun generate(random: Random = Random.Default): String =
        buildString(CODE_LENGTH) {
            repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

    /** Normalises user input before lookup (codes are stored uppercase). */
    fun normalise(input: String): String = input.trim().uppercase()
}

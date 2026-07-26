package com.nextgen.courtvision.domain.auth

/**
 * Pure JVM validation (no android.util.Patterns) so it runs in plain unit tests.
 */
object CredentialsValidator {

    // Firebase Authentication's own minimum
    const val MIN_PASSWORD_LENGTH = 6
    const val MIN_NAME_LENGTH = 2

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    fun isValidPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    fun isValidDisplayName(name: String): Boolean = name.trim().length >= MIN_NAME_LENGTH
}

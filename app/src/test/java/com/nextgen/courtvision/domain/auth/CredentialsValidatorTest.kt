package com.nextgen.courtvision.domain.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialsValidatorTest {

    @Test
    fun `accepts standard email addresses`() {
        assertTrue(CredentialsValidator.isValidEmail("player@example.com"))
        assertTrue(CredentialsValidator.isValidEmail("coach.name+tag@sub.example.co.uk"))
        assertTrue(CredentialsValidator.isValidEmail("  padded@example.com  "))
    }

    @Test
    fun `rejects malformed email addresses`() {
        assertFalse(CredentialsValidator.isValidEmail(""))
        assertFalse(CredentialsValidator.isValidEmail("not-an-email"))
        assertFalse(CredentialsValidator.isValidEmail("missing@domain"))
        assertFalse(CredentialsValidator.isValidEmail("@example.com"))
        assertFalse(CredentialsValidator.isValidEmail("spaces in@example.com"))
    }

    @Test
    fun `enforces firebase minimum password length`() {
        assertFalse(CredentialsValidator.isValidPassword(""))
        assertFalse(CredentialsValidator.isValidPassword("12345"))
        assertTrue(CredentialsValidator.isValidPassword("123456"))
        assertTrue(CredentialsValidator.isValidPassword("a-much-longer-password"))
    }

    @Test
    fun `display name requires two non-blank characters`() {
        assertFalse(CredentialsValidator.isValidDisplayName(""))
        assertFalse(CredentialsValidator.isValidDisplayName(" "))
        assertFalse(CredentialsValidator.isValidDisplayName("A"))
        assertTrue(CredentialsValidator.isValidDisplayName("Al"))
        assertTrue(CredentialsValidator.isValidDisplayName("  Martin Blake  "))
    }
}

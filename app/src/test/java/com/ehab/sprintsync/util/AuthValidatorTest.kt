package com.ehab.sprintsync.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {
    @Test
    fun validEmail_isAccepted() {
        assertTrue(AuthValidator.isValidEmail("ehab@sprintsync.dev"))
    }

    @Test
    fun malformedEmail_isRejected() {
        assertFalse(AuthValidator.isValidEmail("ehab@"))
    }

    @Test
    fun password_requiresSixCharacters() {
        assertFalse(AuthValidator.isValidPassword("12345"))
        assertTrue(AuthValidator.isValidPassword("123456"))
    }
}


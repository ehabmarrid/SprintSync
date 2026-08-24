package com.ehab.sprintsync.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileInitialsTest {

    private fun profile(name: String, email: String = "ehab@sprintsync.dev") =
        UserProfile(id = "u1", name = name, email = email)

    @Test
    fun `two words give two initials`() {
        assertEquals("EM", profile("Ehab Marrid").initials())
    }

    @Test
    fun `a single word gives one initial`() {
        assertEquals("E", profile("Ehab").initials())
    }

    /** Only the first two words count, however many follow. */
    @Test
    fun `more than two words still give two initials`() {
        assertEquals("EB", profile("Ehab Ben Marrid").initials())
    }

    @Test
    fun `extra whitespace between and around words is ignored`() {
        assertEquals("EM", profile("  Ehab   Marrid  ").initials())
    }

    /** An empty name falls back to the email, which is why sign-in never shows a blank avatar. */
    @Test
    fun `an empty name falls back to the first letter of the email`() {
        assertEquals("E", profile("", email = "ehab@sprintsync.dev").initials())
        assertEquals("E", profile("   ", email = "ehab@sprintsync.dev").initials())
    }

    @Test
    fun `an empty name and empty email give an empty string`() {
        assertEquals("", profile("", email = "").initials())
    }

    /**
     * Hebrew has no letter case, so uppercase() is a no-op and the initials must come through
     * unchanged. The board seeds Hebrew member names, so this is a real path, not a curiosity.
     */
    @Test
    fun `an RTL name keeps its own letters`() {
        assertEquals("אמ", profile("אהב מריד").initials())
        assertEquals("א", profile("אהב").initials())
    }

    /** A mixed name should take one initial from each script rather than dropping either. */
    @Test
    fun `a mixed RTL and Latin name takes one from each`() {
        assertEquals("אM", profile("אהב Marrid").initials())
    }

    /** initialsOf is the shared helper the adapters call with their own fallbacks. */
    @Test
    fun `initialsOf uses the supplied fallback when the name yields nothing`() {
        assertEquals("—", UserProfile.initialsOf("", "—"))
        assertEquals("?", UserProfile.initialsOf("   ", "?"))
        assertEquals("RK", UserProfile.initialsOf("Rina Katz", "?"))
    }
}

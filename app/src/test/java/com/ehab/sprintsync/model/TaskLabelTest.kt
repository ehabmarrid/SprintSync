package com.ehab.sprintsync.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskLabelTest {

    @Test
    fun `every wire value round-trips`() {
        TaskLabel.entries.forEach { label ->
            assertEquals(label, TaskLabel.fromValue(label.wireValue))
        }
    }

    /**
     * The whole point of the fallback: a label written by a newer build, or typed by hand in
     * the Firebase console, must degrade rather than crash an older client.
     */
    @Test
    fun `unknown value falls back to FEATURE`() {
        assertEquals(TaskLabel.FEATURE, TaskLabel.fromValue("SECURITY"))
    }

    @Test
    fun `null and blank fall back to FEATURE`() {
        assertEquals(TaskLabel.FEATURE, TaskLabel.fromValue(null))
        assertEquals(TaskLabel.FEATURE, TaskLabel.fromValue(""))
    }

    /** The field is free text on the wire, so casing and stray spaces must not lose the value. */
    @Test
    fun `matching ignores case and surrounding whitespace`() {
        assertEquals(TaskLabel.BUG, TaskLabel.fromValue("bug"))
        assertEquals(TaskLabel.API, TaskLabel.fromValue("  Api  "))
    }

    /**
     * Guards the no-migration promise: these are the exact strings already stored in Realtime
     * Database and in the local demo database. Renaming one silently orphans existing tasks.
     */
    @Test
    fun `wire values are the strings already in the database`() {
        assertEquals(
            listOf("FEATURE", "BUG", "UI", "API", "DOCS"),
            TaskLabel.entries.map { it.wireValue }
        )
    }
}

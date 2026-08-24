package com.ehab.sprintsync.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SprintTaskBuilderTest {

    @Test
    fun `builds a task with every field set`() {
        val task = SprintTask.Builder()
            .boardId("board-1")
            .title("Wire up settings")
            .description("Persist the toggle")
            .label(TaskLabel.BUG)
            .status(TaskStatus.IN_PROGRESS)
            .createdAt(1_750_000_000_000)
            .build()

        assertEquals("board-1", task.boardId)
        assertEquals("Wire up settings", task.title)
        assertEquals("Persist the toggle", task.description)
        assertEquals(TaskLabel.BUG, task.taskLabel())
        assertEquals(TaskStatus.IN_PROGRESS, task.taskStatus())
        assertEquals(1_750_000_000_000, task.createdAt)
    }

    /** The require() in build() is the only validation between the UI and the database. */
    @Test
    fun `build rejects a blank title`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            SprintTask.Builder().boardId("board-1").title("   ").build()
        }
        assertTrue(error.message.orEmpty().contains("title", ignoreCase = true))
    }

    @Test
    fun `build rejects a missing title`() {
        assertThrows(IllegalArgumentException::class.java) {
            SprintTask.Builder().boardId("board-1").build()
        }
    }

    @Test
    fun `title and description are trimmed`() {
        val task = SprintTask.Builder()
            .boardId("board-1")
            .title("  Fix login crash  ")
            .description("  after rotation  ")
            .build()

        assertEquals("Fix login crash", task.title)
        assertEquals("after rotation", task.description)
    }

    /** Defaults have to stay usable: a task built with only the required fields is valid. */
    @Test
    fun `defaults are TODO and FEATURE`() {
        val task = SprintTask.Builder().boardId("board-1").title("Something").build()
        assertEquals(TaskStatus.TODO, task.taskStatus())
        assertEquals(TaskLabel.FEATURE, task.taskLabel())
    }

    /** Each call starts from the previous state, so the builder must not share it across builds. */
    @Test
    fun `separate builders do not share state`() {
        val first = SprintTask.Builder().boardId("b").title("First").build()
        val second = SprintTask.Builder().boardId("b").title("Second").build()
        assertNotEquals(first.title, second.title)
    }
}

package com.ehab.sprintsync.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskStatusTest {
    @Test
    fun unknownStatus_defaultsToTodo() {
        assertEquals(TaskStatus.TODO, TaskStatus.fromValue("not-a-status"))
    }

    @Test
    fun persistedValue_mapsToStatus() {
        assertEquals(TaskStatus.IN_PROGRESS, TaskStatus.fromValue("in_progress"))
    }
}


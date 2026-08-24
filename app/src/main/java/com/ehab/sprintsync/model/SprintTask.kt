package com.ehab.sprintsync.model

import java.io.Serializable

data class SprintTask(
    val id: String = "",
    val boardId: String = "",
    val title: String = "",
    val description: String = "",
    val label: String = "FEATURE",
    val status: String = TaskStatus.TODO.wireValue,
    val assigneeId: String = "",
    val assigneeName: String = "",
    val assigneeAvatarUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : Serializable {

    fun taskStatus(): TaskStatus = TaskStatus.fromValue(status)

    fun taskLabel(): TaskLabel = TaskLabel.fromValue(label)

    class Builder {
        private var task = SprintTask()

        fun id(value: String) = apply { task = task.copy(id = value) }
        fun boardId(value: String) = apply { task = task.copy(boardId = value) }
        fun title(value: String) = apply { task = task.copy(title = value.trim()) }
        fun description(value: String) = apply { task = task.copy(description = value.trim()) }
        fun label(value: TaskLabel) = apply { task = task.copy(label = value.wireValue) }
        fun status(value: TaskStatus) = apply { task = task.copy(status = value.wireValue) }
        fun createdAt(value: Long) = apply { task = task.copy(createdAt = value) }

        fun build(): SprintTask {
            require(task.title.isNotBlank()) { "Task title cannot be empty" }
            return task
        }
    }
}


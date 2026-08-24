package com.ehab.sprintsync.model

enum class TaskStatus(val wireValue: String) {
    TODO("todo"),
    IN_PROGRESS("in_progress"),
    DONE("done");

    /** The next column, or null at the end of the board. Declaration order is board order. */
    fun next(): TaskStatus? = entries.getOrNull(ordinal + 1)

    /** The previous column, or null at the start of the board. */
    fun previous(): TaskStatus? = entries.getOrNull(ordinal - 1)

    companion object {
        fun fromValue(value: String?): TaskStatus =
            entries.firstOrNull { it.wireValue == value } ?: TODO
    }
}


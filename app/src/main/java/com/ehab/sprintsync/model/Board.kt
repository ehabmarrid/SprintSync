package com.ehab.sprintsync.model

import java.io.Serializable

data class Board(
    val id: String = "",
    val name: String = "",
    val sprintLabel: String = "",
    val memberIds: Map<String, Boolean> = emptyMap(),
    val memberNames: List<String> = emptyList(),
    val taskCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable


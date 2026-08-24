package com.ehab.sprintsync.model

import java.io.Serializable

data class UserProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val avatarUrl: String = ""
) : Serializable {
    fun initials(): String = initialsOf(name, email.take(1).uppercase())

    companion object {
        /**
         * The single implementation of avatar initials, shared by this model and by the
         * board and task adapters, which render names that have no [UserProfile] behind
         * them. Returns up to two uppercase letters, or [fallback] when [name] yields none.
         */
        fun initialsOf(name: String, fallback: String): String =
            name.trim()
                .split(Regex("\\s+"))
                .filter(String::isNotBlank)
                .take(2)
                .joinToString("") { it.first().uppercase() }
                .ifBlank { fallback }
    }
}


package com.ehab.sprintsync.data

import android.net.Uri
import com.ehab.sprintsync.model.Board
import com.ehab.sprintsync.model.SprintTask
import com.ehab.sprintsync.model.UserProfile

fun interface ResultCallback<T> {
    fun onResult(result: Result<T>)
}

fun interface DataObserver<T> {
    fun onData(value: T)
}

fun interface Subscription {
    fun cancel()
}

/**
 * Outcome of [SprintRepository.createBoard].
 *
 * [unresolvedEmails] lists the invited addresses that matched no registered account. They
 * are deliberately *not* board members: adding them would render an avatar for someone who
 * has no access to the board. Both repository implementations must populate this the same
 * way, so the UI cannot tell which one is active.
 */
data class BoardCreation(
    val board: Board,
    val unresolvedEmails: List<String> = emptyList()
)

interface SprintRepository {
    val isDemoMode: Boolean

    fun currentUser(): UserProfile?
    fun signIn(email: String, password: String, callback: ResultCallback<UserProfile>)
    fun signUp(name: String, email: String, password: String, callback: ResultCallback<UserProfile>)
    fun signOut()

    fun observeBoards(observer: DataObserver<List<Board>>, onError: (Throwable) -> Unit): Subscription
    fun createBoard(
        name: String,
        sprintLabel: String,
        memberEmails: List<String>,
        callback: ResultCallback<BoardCreation>
    )

    fun observeTasks(
        boardId: String,
        observer: DataObserver<List<SprintTask>>,
        onError: (Throwable) -> Unit
    ): Subscription

    fun saveTask(task: SprintTask, callback: ResultCallback<SprintTask>)
    fun deleteTask(task: SprintTask, callback: ResultCallback<Unit>)
    fun assignTaskToCurrentUser(task: SprintTask, callback: ResultCallback<SprintTask>)
    fun uploadAvatar(uri: Uri, callback: ResultCallback<UserProfile>)
}


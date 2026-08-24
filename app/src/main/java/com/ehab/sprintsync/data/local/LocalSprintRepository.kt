package com.ehab.sprintsync.data.local

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.ehab.sprintsync.data.BoardCreation
import com.ehab.sprintsync.data.DataObserver
import com.ehab.sprintsync.data.ResultCallback
import com.ehab.sprintsync.data.SessionManager
import com.ehab.sprintsync.data.SprintRepository
import com.ehab.sprintsync.data.Subscription
import com.ehab.sprintsync.model.Board
import com.ehab.sprintsync.model.SprintTask
import com.ehab.sprintsync.model.TaskStatus
import com.ehab.sprintsync.model.UserProfile
import com.google.gson.Gson
import java.util.UUID

/**
 * Presentation-safe offline repository.
 *
 * It mirrors the Firebase repository contract, stores JSON with Gson in
 * SharedPreferences, and keeps every screen functional without network setup.
 */
class LocalSprintRepository(context: Context) : SprintRepository {
    override val isDemoMode: Boolean = true

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val sessionManager = SessionManager(context)
    private val gson = Gson()
    private val lock = Any()
    private val boardObservers = linkedMapOf<String, DataObserver<List<Board>>>()
    private val taskObservers =
        linkedMapOf<String, MutableMap<String, DataObserver<List<SprintTask>>>>()

    private var state: LocalState = loadState()

    override fun currentUser(): UserProfile? = sessionManager.getUser()

    /**
     * Accepts **any** password, and creates a user for any address it has not seen.
     *
     * This is deliberate and is not a security hole: it is the presentation fallback that
     * runs when no Firebase configuration is present, backed only by local
     * SharedPreferences, so there is nothing to authenticate against and no account to
     * compromise. Real credentials are checked by Firebase Authentication in
     * [com.ehab.sprintsync.data.firebase.FirebaseSprintRepository]. Do not "fix" this by
     * adding password checks here - it would break the zero-configuration demo path.
     */
    override fun signIn(
        email: String,
        password: String,
        callback: ResultCallback<UserProfile>
    ) {
        val user = synchronized(lock) {
            state.users.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }
                ?: UserProfile(
                    id = "local-${email.trim().lowercase().hashCode()}",
                    name = email.substringBefore("@").replaceFirstChar(Char::uppercase),
                    email = email.trim()
                ).also {
                    state = state.copy(users = state.users + it)
                    persist()
                }
        }
        sessionManager.saveUser(user)
        callback.onResult(Result.success(user))
    }

    override fun signUp(
        name: String,
        email: String,
        password: String,
        callback: ResultCallback<UserProfile>
    ) {
        val user = UserProfile(
            id = "local-${email.trim().lowercase().hashCode()}",
            name = name.trim(),
            email = email.trim()
        )
        synchronized(lock) {
            state = state.copy(
                users = state.users.filterNot { it.email.equals(user.email, true) } + user
            )
            persist()
        }
        sessionManager.saveUser(user)
        callback.onResult(Result.success(user))
    }

    override fun signOut() = sessionManager.clear()

    override fun observeBoards(
        observer: DataObserver<List<Board>>,
        onError: (Throwable) -> Unit
    ): Subscription {
        val observerId = UUID.randomUUID().toString()
        synchronized(lock) { boardObservers[observerId] = observer }
        observer.onData(snapshotBoards())
        return Subscription { synchronized(lock) { boardObservers.remove(observerId) } }
    }

    override fun createBoard(
        name: String,
        sprintLabel: String,
        memberEmails: List<String>,
        callback: ResultCallback<BoardCreation>
    ) {
        val user = currentUser()
            ?: return callback.onResult(Result.failure(IllegalStateException("No active user")))
        val cleanedEmails = memberEmails.map(String::trim).filter(String::isNotBlank).distinct()

        // Mirror the Firebase rule exactly: an invite becomes a member only when it
        // resolves to an account that already exists. The local user list is this
        // repository's equivalent of /emailIndex.
        val (resolved, unresolved) = synchronized(lock) {
            cleanedEmails.partition { email ->
                state.users.any { it.email.equals(email, ignoreCase = true) }
            }
        }
        val memberIds = linkedMapOf(user.id to true)
        synchronized(lock) {
            resolved.forEach { email ->
                state.users.firstOrNull { it.email.equals(email, ignoreCase = true) }
                    ?.let { memberIds[it.id] = true }
            }
        }
        val board = Board(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            sprintLabel = sprintLabel.trim().ifBlank { "SPRINT-1" },
            memberIds = memberIds,
            memberNames = (listOf(user.name) + resolved.map { it.substringBefore("@") })
                .filter(String::isNotBlank)
                .distinct()
        )
        synchronized(lock) {
            state = state.copy(boards = state.boards + board)
            persist()
        }
        notifyBoardObservers()
        callback.onResult(Result.success(BoardCreation(board, unresolved)))
    }

    override fun observeTasks(
        boardId: String,
        observer: DataObserver<List<SprintTask>>,
        onError: (Throwable) -> Unit
    ): Subscription {
        val observerId = UUID.randomUUID().toString()
        synchronized(lock) {
            taskObservers.getOrPut(boardId) { linkedMapOf() }[observerId] = observer
        }
        observer.onData(snapshotTasks(boardId))
        return Subscription {
            synchronized(lock) {
                taskObservers[boardId]?.remove(observerId)
                if (taskObservers[boardId].isNullOrEmpty()) taskObservers.remove(boardId)
            }
        }
    }

    override fun saveTask(task: SprintTask, callback: ResultCallback<SprintTask>) {
        val savedTask = if (task.id.isBlank()) {
            task.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis())
        } else {
            task
        }
        synchronized(lock) {
            val boardTasks = state.tasks[savedTask.boardId].orEmpty()
            val updatedTasks = boardTasks.filterNot { it.id == savedTask.id } + savedTask
            val updatedTaskMap = state.tasks.toMutableMap().apply {
                put(savedTask.boardId, updatedTasks)
            }
            state = state.copy(
                boards = state.boards.map { board ->
                    if (board.id == savedTask.boardId) board.copy(taskCount = updatedTasks.size)
                    else board
                },
                tasks = updatedTaskMap
            )
            persist()
        }
        notifyTaskObservers(savedTask.boardId)
        notifyBoardObservers()
        callback.onResult(Result.success(savedTask))
    }

    override fun deleteTask(task: SprintTask, callback: ResultCallback<Unit>) {
        synchronized(lock) {
            val updatedTasks = state.tasks[task.boardId].orEmpty().filterNot { it.id == task.id }
            val updatedTaskMap = state.tasks.toMutableMap().apply {
                put(task.boardId, updatedTasks)
            }
            state = state.copy(
                boards = state.boards.map { board ->
                    if (board.id == task.boardId) board.copy(taskCount = updatedTasks.size)
                    else board
                },
                tasks = updatedTaskMap
            )
            persist()
        }
        notifyTaskObservers(task.boardId)
        notifyBoardObservers()
        callback.onResult(Result.success(Unit))
    }

    override fun assignTaskToCurrentUser(
        task: SprintTask,
        callback: ResultCallback<SprintTask>
    ) {
        val user = currentUser()
            ?: return callback.onResult(Result.failure(IllegalStateException("No active user")))
        saveTask(
            task.copy(
                assigneeId = user.id,
                assigneeName = user.name,
                assigneeAvatarUrl = user.avatarUrl
            ),
            callback
        )
    }

    override fun uploadAvatar(uri: Uri, callback: ResultCallback<UserProfile>) {
        val user = currentUser()
            ?: return callback.onResult(Result.failure(IllegalStateException("No active user")))
        val updatedUser = user.copy(avatarUrl = uri.toString())
        synchronized(lock) {
            state = state.copy(
                users = state.users.filterNot { it.id == updatedUser.id } + updatedUser,
                tasks = state.tasks.mapValues { (_, tasks) ->
                    tasks.map {
                        if (it.assigneeId == updatedUser.id) {
                            it.copy(assigneeAvatarUrl = updatedUser.avatarUrl)
                        } else {
                            it
                        }
                    }
                }
            )
            persist()
        }
        sessionManager.saveUser(updatedUser)
        state.tasks.keys.forEach(::notifyTaskObservers)
        callback.onResult(Result.success(updatedUser))
    }

    private fun snapshotBoards(): List<Board> = synchronized(lock) {
        state.boards.sortedByDescending(Board::createdAt)
    }

    private fun snapshotTasks(boardId: String): List<SprintTask> = synchronized(lock) {
        state.tasks[boardId].orEmpty().sortedByDescending(SprintTask::createdAt)
    }

    private fun notifyBoardObservers() {
        val snapshot = snapshotBoards()
        val observers = synchronized(lock) { boardObservers.values.toList() }
        observers.forEach { it.onData(snapshot) }
    }

    private fun notifyTaskObservers(boardId: String) {
        val snapshot = snapshotTasks(boardId)
        val observers = synchronized(lock) { taskObservers[boardId]?.values?.toList().orEmpty() }
        observers.forEach { it.onData(snapshot) }
    }

    private fun loadState(): LocalState {
        val json = preferences.getString(KEY_DATABASE, null)
        if (!json.isNullOrBlank()) {
            runCatching { gson.fromJson(json, LocalState::class.java) }
                .getOrNull()
                ?.let { return it }
        }
        return createSeedState().also {
            preferences.edit { putString(KEY_DATABASE, gson.toJson(it)) }
        }
    }

    private fun persist() {
        preferences.edit { putString(KEY_DATABASE, gson.toJson(state)) }
    }

    private fun createSeedState(): LocalState {
        val demoUser = UserProfile(
            id = DEMO_USER_ID,
            name = "Ehab Marrid",
            email = "ehab@sprintsync.dev"
        )
        val board = Board(
            id = DEMO_BOARD_ID,
            name = "Mobile App Sprint",
            sprintLabel = "SPRINT-3",
            memberIds = mapOf(DEMO_USER_ID to true),
            memberNames = listOf("Ehab Marrid", "Rina Katz", "Noa Amir"),
            taskCount = 6
        )
        val tasks = listOf(
            seededTask("Fix login crash", "BUG", TaskStatus.IN_PROGRESS, "Ehab Marrid", "EM", 6),
            seededTask("Board screen - column swipe", "UI", TaskStatus.IN_PROGRESS, "Rina Katz", "RK", 5),
            seededTask("Sync tasks with Realtime DB", "API", TaskStatus.TODO, "Noa Amir", "NA", 4),
            seededTask("Add task options bottom sheet", "FEATURE", TaskStatus.TODO, "", "", 3),
            seededTask("Polish dark mode colors", "UI", TaskStatus.DONE, "Ehab Marrid", "EM", 2),
            seededTask("Prepare class demo", "DOCS", TaskStatus.DONE, "Rina Katz", "RK", 1)
        )
        return LocalState(
            users = listOf(demoUser),
            boards = listOf(board),
            tasks = mapOf(DEMO_BOARD_ID to tasks)
        )
    }

    private fun seededTask(
        title: String,
        label: String,
        status: TaskStatus,
        assigneeName: String,
        assigneeId: String,
        order: Int
    ) = SprintTask(
        id = "seed-$order",
        boardId = DEMO_BOARD_ID,
        title = title,
        label = label,
        status = status.wireValue,
        assigneeId = assigneeId,
        assigneeName = assigneeName,
        createdAt = System.currentTimeMillis() - order * 1_000L
    )

    private data class LocalState(
        val users: List<UserProfile> = emptyList(),
        val boards: List<Board> = emptyList(),
        val tasks: Map<String, List<SprintTask>> = emptyMap()
    )

    companion object {
        private const val PREFERENCES_NAME = "sprint_sync_local_database"
        private const val KEY_DATABASE = "database_json"
        private const val DEMO_USER_ID = "demo-user"
        private const val DEMO_BOARD_ID = "demo-board"
    }
}

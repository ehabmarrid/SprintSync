package com.ehab.sprintsync.data.firebase

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.ehab.sprintsync.data.BoardCreation
import com.ehab.sprintsync.data.DataObserver
import com.ehab.sprintsync.data.ResultCallback
import com.ehab.sprintsync.data.SessionManager
import com.ehab.sprintsync.data.SprintRepository
import com.ehab.sprintsync.data.Subscription
import com.ehab.sprintsync.model.Board
import com.ehab.sprintsync.model.SprintTask
import com.ehab.sprintsync.model.UserProfile
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.util.concurrent.atomic.AtomicBoolean

class FirebaseSprintRepository(
    context: Context,
    firebaseApp: FirebaseApp
) : SprintRepository {
    override val isDemoMode: Boolean = false

    private val auth = FirebaseAuth.getInstance(firebaseApp)
    private val database = FirebaseDatabase.getInstance(firebaseApp).reference
    private val storage = FirebaseStorage.getInstance(firebaseApp).reference
    private val sessionManager = SessionManager(context)

    override fun currentUser(): UserProfile? {
        sessionManager.getUser()?.let { return it }
        return auth.currentUser?.let {
            UserProfile(
                id = it.uid,
                name = it.displayName.orEmpty(),
                email = it.email.orEmpty(),
                avatarUrl = it.photoUrl?.toString().orEmpty()
            )
        }
    }

    override fun signIn(
        email: String,
        password: String,
        callback: ResultCallback<UserProfile>
    ) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                loadCurrentProfile(callback)
            }
            .addOnFailureListener { callback.onResult(Result.failure(it)) }
    }

    override fun signUp(
        name: String,
        email: String,
        password: String,
        callback: ResultCallback<UserProfile>
    ) {
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val firebaseUser = result.user
                    ?: return@addOnSuccessListener callback.onResult(
                        Result.failure(IllegalStateException("Firebase did not return a user"))
                    )
                val profile = UserProfile(
                    id = firebaseUser.uid,
                    name = name.trim(),
                    email = email.trim()
                )
                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(profile.name)
                    .build()

                firebaseUser.updateProfile(profileUpdate)
                val updates = mapOf<String, Any>(
                    "/users/${profile.id}" to profile,
                    "/emailIndex/${emailKey(profile.email)}" to profile.id
                )
                database.updateChildren(updates)
                    .addOnSuccessListener {
                        sessionManager.saveUser(profile)
                        callback.onResult(Result.success(profile))
                    }
                    .addOnFailureListener { callback.onResult(Result.failure(it)) }
            }
            .addOnFailureListener { callback.onResult(Result.failure(it)) }
    }

    override fun signOut() {
        auth.signOut()
        sessionManager.clear()
    }

    override fun observeBoards(
        observer: DataObserver<List<Board>>,
        onError: (Throwable) -> Unit
    ): Subscription {
        val userId = auth.currentUser?.uid
            ?: return Subscription { }.also { onError(IllegalStateException("No active user")) }

        // Rules cascade downward only, so /boards itself is unreadable and a query on
        // memberIds/<uid> could never be indexed. Read the per-user index instead and
        // fetch each board it points at.
        val indexReference = database.child("userBoards").child(userId)
        val boards = linkedMapOf<String, Board>()
        val boardListeners = linkedMapOf<String, ValueEventListener>()
        val pending = mutableSetOf<String>()
        var hasEmitted = false

        fun emit() {
            // The pending gate holds back the very first list only. Afterwards every
            // change publishes straight away, so one slow new board cannot stall the rest.
            if (!hasEmitted && pending.isNotEmpty()) return
            hasEmitted = true
            observer.onData(boards.values.sortedByDescending(Board::createdAt))
        }

        fun detach(boardId: String) {
            boardListeners.remove(boardId)?.let {
                database.child("boards").child(boardId).removeEventListener(it)
            }
            boards.remove(boardId)
            pending.remove(boardId)
        }

        fun attach(boardId: String) {
            pending += boardId
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val board = snapshot.getValue(Board::class.java)
                    if (board == null) boards.remove(boardId) else boards[boardId] = board
                    pending.remove(boardId)
                    emit()
                }

                // /userBoards is writable by any signed-in user, so a pointer may name a
                // board this user is not a member of. Drop that board and keep the screen
                // working instead of failing the whole subscription.
                override fun onCancelled(error: DatabaseError) {
                    boards.remove(boardId)
                    pending.remove(boardId)
                    emit()
                }
            }
            boardListeners[boardId] = listener
            database.child("boards").child(boardId).addValueEventListener(listener)
        }

        val indexListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val boardIds = snapshot.children
                    .filter { it.getValue(Boolean::class.java) == true }
                    .mapNotNull { it.key }
                    .toSet()
                (boardListeners.keys - boardIds).toList().forEach(::detach)
                boardIds.filterNot { it in boardListeners }.forEach(::attach)
                emit()
            }

            override fun onCancelled(error: DatabaseError) = onError(error.toException())
        }
        indexReference.addValueEventListener(indexListener)

        return Subscription {
            indexReference.removeEventListener(indexListener)
            boardListeners.forEach { (boardId, listener) ->
                database.child("boards").child(boardId).removeEventListener(listener)
            }
            boardListeners.clear()
            boards.clear()
            pending.clear()
        }
    }

    override fun createBoard(
        name: String,
        sprintLabel: String,
        memberEmails: List<String>,
        callback: ResultCallback<BoardCreation>
    ) {
        val user = currentUser()
            ?: return callback.onResult(Result.failure(IllegalStateException("No active user")))
        val id = database.child("boards").push().key
            ?: return callback.onResult(Result.failure(IllegalStateException("Could not create board id")))
        val cleanedEmails = memberEmails.map(String::trim).filter(String::isNotBlank).distinct()
        val memberIds = linkedMapOf(user.id to true)
        val memberNames = linkedSetOf(user.name)
        val unresolvedEmails = cleanedEmails.toMutableSet()

        if (cleanedEmails.isEmpty()) {
            writeBoard(id, name, sprintLabel, memberIds, memberNames.toList(), emptyList(), callback)
            return
        }

        // A lookup that never returns used to leave the caller waiting forever with its
        // submit button disabled. Write the board exactly once, either when every lookup
        // has settled or when the timeout fires, whichever comes first.
        val handler = Handler(Looper.getMainLooper())
        val written = AtomicBoolean(false)
        fun finish() {
            if (!written.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            writeBoard(
                id,
                name,
                sprintLabel,
                memberIds,
                memberNames.toList(),
                unresolvedEmails.toList(),
                callback
            )
        }

        val lookups = cleanedEmails.map { email ->
            database.child("emailIndex").child(emailKey(email)).get()
                .addOnSuccessListener { snapshot ->
                    // Only an email that resolves to a uid becomes a member. An unmatched
                    // address would otherwise show as a team avatar on a board it has no
                    // permission to open.
                    snapshot.getValue(String::class.java)
                        ?.takeIf(String::isNotBlank)
                        ?.let { memberUid ->
                            memberIds[memberUid] = true
                            memberNames += email.substringBefore("@")
                            unresolvedEmails -= email
                        }
                }
        }
        Tasks.whenAllComplete(lookups).addOnCompleteListener { finish() }
        handler.postDelayed({ finish() }, EMAIL_LOOKUP_TIMEOUT_MS)
    }

    override fun observeTasks(
        boardId: String,
        observer: DataObserver<List<SprintTask>>,
        onError: (Throwable) -> Unit
    ): Subscription {
        val reference = database.child("tasks").child(boardId)
        // Keep the open board's tasks cached, so reopening it or losing the network
        // renders from disk instead of an empty column.
        reference.keepSynced(true)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tasks = snapshot.children
                    .mapNotNull { it.getValue(SprintTask::class.java) }
                    .sortedByDescending(SprintTask::createdAt)
                observer.onData(tasks)
            }

            override fun onCancelled(error: DatabaseError) = onError(error.toException())
        }
        reference.addValueEventListener(listener)
        return Subscription {
            reference.removeEventListener(listener)
            reference.keepSynced(false)
        }
    }

    override fun saveTask(task: SprintTask, callback: ResultCallback<SprintTask>) {
        val isNewTask = task.id.isBlank()
        val id = task.id.ifBlank {
            database.child("tasks").child(task.boardId).push().key.orEmpty()
        }
        if (id.isBlank()) {
            callback.onResult(Result.failure(IllegalStateException("Could not create task id")))
            return
        }
        val savedTask = task.copy(
            id = id,
            createdAt = if (isNewTask) System.currentTimeMillis() else task.createdAt
        )
        // Task and counter move together in one atomic update, so the count cannot drift
        // when the second write fails, and a failure reaches the caller instead of being
        // discarded by a fire-and-forget listener.
        val updates = mutableMapOf<String, Any>(
            "/tasks/${savedTask.boardId}/${savedTask.id}" to savedTask
        )
        if (isNewTask) {
            updates["/boards/${savedTask.boardId}/taskCount"] = ServerValue.increment(1)
        }
        database.updateChildren(updates)
            .addOnSuccessListener { callback.onResult(Result.success(savedTask)) }
            .addOnFailureListener { callback.onResult(Result.failure(it)) }
    }

    override fun deleteTask(task: SprintTask, callback: ResultCallback<Unit>) {
        val updates = mapOf<String, Any?>(
            "/tasks/${task.boardId}/${task.id}" to null,
            "/boards/${task.boardId}/taskCount" to ServerValue.increment(-1)
        )
        database.updateChildren(updates)
            .addOnSuccessListener { callback.onResult(Result.success(Unit)) }
            .addOnFailureListener { callback.onResult(Result.failure(it)) }
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
        val avatarReference = storage.child("avatars/${user.id}.jpg")
        avatarReference.putFile(uri)
            .continueWithTask { uploadTask ->
                if (!uploadTask.isSuccessful) {
                    throw uploadTask.exception ?: IllegalStateException("Avatar upload failed")
                }
                avatarReference.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                val updatedUser = user.copy(avatarUrl = downloadUri.toString())
                database.child("users").child(user.id).setValue(updatedUser)
                    .addOnSuccessListener {
                        sessionManager.saveUser(updatedUser)
                        callback.onResult(Result.success(updatedUser))
                    }
                    .addOnFailureListener { callback.onResult(Result.failure(it)) }
            }
            .addOnFailureListener { callback.onResult(Result.failure(it)) }
    }

    private fun loadCurrentProfile(callback: ResultCallback<UserProfile>) {
        val firebaseUser = auth.currentUser
            ?: return callback.onResult(Result.failure(IllegalStateException("No active user")))
        database.child("users").child(firebaseUser.uid).get()
            .addOnSuccessListener { snapshot ->
                snapshot.getValue(UserProfile::class.java)?.let { storedProfile ->
                    sessionManager.saveUser(storedProfile)
                    callback.onResult(Result.success(storedProfile))
                    return@addOnSuccessListener
                }

                // An account can exist in Auth with no database record, because the
                // sign-up write was denied. Persist it now: without the emailIndex entry
                // this user can never be found when somebody invites them to a board.
                val profile = UserProfile(
                    id = firebaseUser.uid,
                    name = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@").orEmpty(),
                    email = firebaseUser.email.orEmpty(),
                    avatarUrl = firebaseUser.photoUrl?.toString().orEmpty()
                )
                val updates = mutableMapOf<String, Any>("/users/${profile.id}" to profile)
                if (profile.email.isNotBlank()) {
                    updates["/emailIndex/${emailKey(profile.email)}"] = profile.id
                }
                database.updateChildren(updates)
                    .addOnSuccessListener {
                        sessionManager.saveUser(profile)
                        callback.onResult(Result.success(profile))
                    }
                    .addOnFailureListener { callback.onResult(Result.failure(it)) }
            }
            .addOnFailureListener { callback.onResult(Result.failure(it)) }
    }

    private fun writeBoard(
        id: String,
        name: String,
        sprintLabel: String,
        memberIds: Map<String, Boolean>,
        memberNames: List<String>,
        unresolvedEmails: List<String>,
        callback: ResultCallback<BoardCreation>
    ) {
        val board = Board(
            id = id,
            name = name.trim(),
            sprintLabel = sprintLabel.trim().ifBlank { "SPRINT-1" },
            memberIds = memberIds,
            memberNames = memberNames
        )
        // One atomic multi-path write, so a board can never exist without the index
        // pointers that make it reachable from observeBoards.
        val updates = mutableMapOf<String, Any>("/boards/$id" to board)
        memberIds.keys.forEach { uid -> updates["/userBoards/$uid/$id"] = true }
        database.updateChildren(updates)
            .addOnSuccessListener {
                callback.onResult(Result.success(BoardCreation(board, unresolvedEmails)))
            }
            .addOnFailureListener { callback.onResult(Result.failure(it)) }
    }

    private fun emailKey(email: String): String =
        Base64.encodeToString(
            email.trim().lowercase().toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    companion object {
        private const val EMAIL_LOOKUP_TIMEOUT_MS = 8_000L
    }
}


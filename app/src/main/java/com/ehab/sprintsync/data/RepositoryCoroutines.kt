package com.ehab.sprintsync.data

import com.ehab.sprintsync.model.SprintTask
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bridges the repository's [ResultCallback] contract to a suspending call.
 *
 * Deliberately generic and defined once. Because the suspend wrappers below are extensions
 * on [SprintRepository] rather than members of it, both FirebaseSprintRepository and
 * LocalSprintRepository gain them without changing a line, and there is no second
 * implementation that could drift from the first - the two stay observationally identical
 * by construction rather than by discipline.
 *
 * Cancellation note: resuming an already-cancelled continuation is a no-op, so a callback
 * that fires after the caller's scope died is harmless. What cancellation stops is the
 * *delivery* of the result; the underlying write is already in flight and still completes.
 */
suspend fun <T> awaitResult(block: (ResultCallback<T>) -> Unit): T =
    suspendCancellableCoroutine { continuation ->
        block(
            ResultCallback { result ->
                result.fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = { continuation.resumeWithException(it) }
                )
            }
        )
    }

/**
 * Suspending form of [SprintRepository.assignTaskToCurrentUser]. The callback overload is
 * untouched and remains the API every other screen uses.
 */
suspend fun SprintRepository.assignTaskToCurrentUser(task: SprintTask): SprintTask =
    awaitResult { callback -> assignTaskToCurrentUser(task, callback) }

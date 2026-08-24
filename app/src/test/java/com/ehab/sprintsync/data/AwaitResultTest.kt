package com.ehab.sprintsync.data

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [awaitResult] is pure Kotlin with no Android dependency, so it is unit-testable directly.
 * runBlocking is enough for all three cases; kotlinx-coroutines-test would add an artifact
 * without adding an assertion.
 */
class AwaitResultTest {

    @Test
    fun `success resumes with the value`() = runBlocking {
        val value = awaitResult<String> { callback ->
            callback.onResult(Result.success("assigned"))
        }
        assertEquals("assigned", value)
    }

    @Test
    fun `failure resumes with the exception`() = runBlocking {
        val thrown = try {
            awaitResult<String> { callback ->
                callback.onResult(Result.failure(IllegalStateException("No active user")))
            }
            null
        } catch (error: IllegalStateException) {
            error
        }
        // Type and message rather than instance identity: kotlinx-coroutines recovers stack
        // traces by rethrowing a *copy* of the exception with the suspension point appended,
        // so the caught object is equal-looking but not the one that was handed in. Throwable
        // does not override equals, so asserting identity here would fail for a good reason.
        assertEquals("No active user", thrown?.message)
    }

    /**
     * The property the assign path depends on: once the scope is cancelled, a callback that
     * arrives late must neither resume the continuation nor throw. A repository write that
     * is already in flight cannot be recalled, so this is what stops its result reaching a
     * destroyed caller.
     */
    @Test
    fun `cancelled job does not resume when the callback fires late`() = runBlocking {
        var captured: ResultCallback<String>? = null
        var resumed = false

        val job = launch {
            awaitResult<String> { callback -> captured = callback }
            resumed = true
        }

        yield() // let the job reach its suspension point
        assertNotNull("the callback was never handed to the block", captured)

        job.cancelAndJoin()
        captured!!.onResult(Result.success("late")) // must be a no-op, must not throw
        yield()

        assertTrue("job should be cancelled", job.isCancelled)
        assertFalse("continuation resumed after cancellation", resumed)
    }
}

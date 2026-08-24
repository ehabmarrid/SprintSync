package com.ehab.sprintsync.ui.board

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ehab.sprintsync.data.RepositoryProvider
import com.ehab.sprintsync.data.Subscription
import com.ehab.sprintsync.data.assignTaskToCurrentUser
import com.ehab.sprintsync.model.SprintTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class BoardViewModel(private val boardId: String) : ViewModel() {
    private val repository = RepositoryProvider.repository
    private val _tasks = MutableLiveData<List<SprintTask>>(emptyList())
    private val _errors = MutableLiveData<Throwable>()
    private val subscription: Subscription

    val tasks: LiveData<List<SprintTask>> = _tasks
    val errors: LiveData<Throwable> = _errors

    init {
        subscription = repository.observeTasks(
            boardId = boardId,
            observer = { _tasks.value = it },
            onError = { _errors.value = it }
        )
    }

    fun saveTask(task: SprintTask, callback: (Result<SprintTask>) -> Unit) {
        repository.saveTask(task) { callback(it) }
    }

    fun deleteTask(task: SprintTask, callback: (Result<Unit>) -> Unit) {
        repository.deleteTask(task) { callback(it) }
    }

    /**
     * The one path that runs through coroutines. The signature is unchanged, so callers are
     * untouched; what changes is that the callback is now delivered inside [viewModelScope]
     * and is therefore skipped entirely once the ViewModel is cleared.
     */
    fun assignTask(task: SprintTask, callback: (Result<SprintTask>) -> Unit) {
        viewModelScope.launch {
            val result = try {
                Result.success(repository.assignTaskToCurrentUser(task))
            } catch (cancellation: CancellationException) {
                // Rethrown, never folded into Result.failure. This is how viewModelScope
                // tears the coroutine down; runCatching would swallow it here and report a
                // cancelled assign as a failed one.
                throw cancellation
            } catch (error: Throwable) {
                Result.failure(error)
            }
            callback(result)
        }
    }

    override fun onCleared() {
        subscription.cancel()
    }

    class Factory(private val boardId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BoardViewModel::class.java))
            return BoardViewModel(boardId) as T
        }
    }
}

package com.ehab.sprintsync.ui.board

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.ehab.sprintsync.R
import com.ehab.sprintsync.model.SprintTask
import com.ehab.sprintsync.model.TaskStatus
import com.google.android.material.snackbar.Snackbar
import com.ehab.sprintsync.util.SignalManager
import com.ehab.sprintsync.util.BidiText

class TaskListFragment : Fragment() {
    private lateinit var boardId: String
    private lateinit var status: TaskStatus
    private val adapter = TaskAdapter(::showTaskOptions, ::moveTaskFromAccessibility)
    private lateinit var viewModel: BoardViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        boardId = requireArguments().getString(ARG_BOARD_ID).orEmpty()
        status = TaskStatus.fromValue(requireArguments().getString(ARG_STATUS))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_task_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.tasksRecycler)
        val emptyState = view.findViewById<View>(R.id.emptyState)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewModel = ViewModelProvider(
            requireActivity(),
            BoardViewModel.Factory(boardId)
        )[BoardViewModel::class.java]

        ItemTouchHelper(TaskSwipeCallback(requireContext(), ::moveTask))
            .attachToRecyclerView(recyclerView)

        viewModel.tasks.observe(viewLifecycleOwner) { allTasks ->
            val filteredTasks = allTasks.filter { it.taskStatus() == status }
            adapter.submitList(filteredTasks)
            emptyState.visibility = if (filteredTasks.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (filteredTasks.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /**
     * Reached by swipe. The card is restored before the write is issued: a successful move
     * drops the task out of this status-filtered list and DiffUtil animates it away, but a
     * *failed* write leaves the item in place, and without this the card would stay stranded
     * off-screen because DiffUtil sees no change to redraw.
     */
    private fun moveTask(
        task: SprintTask,
        destination: TaskStatus,
        holder: RecyclerView.ViewHolder
    ) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) adapter.notifyItemChanged(position)
        applyMove(task, destination)
    }

    /** Reached by the card's custom accessibility action; no swiped view to restore. */
    private fun moveTaskFromAccessibility(task: SprintTask, destination: TaskStatus) {
        applyMove(task, destination)
    }

    private fun applyMove(task: SprintTask, destination: TaskStatus) {
        val previousStatus = task.taskStatus()
        // The same saveTask the edit dialog uses, so both repositories behave identically.
        viewModel.saveTask(task.copy(status = destination.wireValue)) { result ->
            result.onSuccess { showUndoSnackbar(task.id, previousStatus, destination) }
                .onFailure(::showError)
        }
    }

    private fun showUndoSnackbar(
        taskId: String,
        previousStatus: TaskStatus,
        destination: TaskStatus
    ) {
        val root = view ?: return
        Snackbar.make(
            root,
            getString(R.string.task_moved_to, getString(destination.labelRes())),
            Snackbar.LENGTH_LONG
        )
            // activity_board is a ConstraintLayout, not a CoordinatorLayout, so the FAB will
            // not step aside on its own.
            .setAnchorView(requireActivity().findViewById(R.id.addTaskFab))
            .setAction(R.string.undo) { undoMove(taskId, previousStatus) }
            .show()
    }

    /**
     * A compensating write, not a rollback - the forward change has already propagated and
     * Realtime Database cannot un-send it. Teammates see the move and then the reverse.
     *
     * The status is restored onto the *current* version of the task rather than the snapshot
     * captured before the swipe, so an edit somebody made in between is not clobbered. If the
     * task has been deleted meanwhile, the undo is abandoned rather than resurrecting it.
     */
    private fun undoMove(taskId: String, previousStatus: TaskStatus) {
        val current = viewModel.tasks.value?.firstOrNull { it.id == taskId }
        if (current == null) {
            SignalManager.error(requireContext(), getString(R.string.undo_task_missing))
            return
        }
        viewModel.saveTask(current.copy(status = previousStatus.wireValue)) { result ->
            result.onFailure(::showError)
        }
    }

    private fun showError(error: Throwable) {
        SignalManager.error(
            requireContext(),
            getString(R.string.something_went_wrong, BidiText.isolate(error.localizedMessage.orEmpty()))
        )
    }

    private fun showTaskOptions(task: SprintTask) {
        (activity as? TaskInteractionListener)?.onTaskSelected(task)
    }

    companion object {
        private const val ARG_BOARD_ID = "board_id"
        private const val ARG_STATUS = "status"

        fun newInstance(boardId: String, status: TaskStatus) =
            TaskListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BOARD_ID, boardId)
                    putString(ARG_STATUS, status.wireValue)
                }
            }
    }
}

interface TaskInteractionListener {
    fun onTaskSelected(task: SprintTask)
}


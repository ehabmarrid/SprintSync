package com.ehab.sprintsync.ui.board

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.ehab.sprintsync.R
import com.ehab.sprintsync.model.SprintTask
import com.ehab.sprintsync.model.TaskStatus

/**
 * Read-only detail view for a single task, added by an explicit FragmentTransaction from
 * [BoardActivity] and placed on the back stack, so system back returns to the board.
 *
 * The task travels in [arguments] rather than a constructor parameter, which is what lets
 * the FragmentManager recreate this fragment across a rotation without the Activity having
 * to hold the state itself.
 */
class TaskDetailFragment : Fragment() {
    private var task: SprintTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        task = arguments?.getSerializable(ARG_TASK) as? SprintTask
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_task_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val task = this.task ?: return

        view.findViewById<TextView>(R.id.detailLabel).text = task.taskLabel().wireValue
        view.findViewById<TextView>(R.id.detailTitle).text = task.title
        view.findViewById<TextView>(R.id.detailDescription).text =
            task.description.ifBlank { getString(R.string.detail_no_description) }
        view.findViewById<TextView>(R.id.detailStatus).setText(task.taskStatus().labelRes())
        view.findViewById<TextView>(R.id.detailAssignee).text =
            task.assigneeName.ifBlank { getString(R.string.detail_unassigned) }
    }

    companion object {
        private const val ARG_TASK = "task"
        const val TAG = "TaskDetailFragment"

        fun newInstance(task: SprintTask) = TaskDetailFragment().apply {
            arguments = Bundle().apply { putSerializable(ARG_TASK, task) }
        }
    }
}

package com.ehab.sprintsync.ui.board

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.ehab.sprintsync.R
import com.ehab.sprintsync.model.SprintTask
import com.ehab.sprintsync.model.TaskStatus
import com.ehab.sprintsync.util.BidiText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class TaskOptionsBottomSheet : BottomSheetDialogFragment() {
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
    ): View = inflater.inflate(R.layout.bottom_sheet_task_options, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Arguments can be absent or the wrong type after process death or a bad caller.
        // Closing the sheet beats crashing on an unchecked cast.
        val task = this.task ?: return dismissAllowingStateLoss()
        val listener = activity as? TaskOptionListener ?: return
        view.findViewById<TextView>(R.id.taskTitle).text = task.title
        view.findViewById<TextView>(R.id.taskMeta).text = getString(
            R.string.task_meta,
            // Latin id in front of a Hebrew status would otherwise set the whole line LTR.
            BidiText.isolate(task.id.take(8).uppercase()),
            getString(task.taskStatus().labelRes())
        )
        view.findViewById<MaterialButton>(R.id.detailsButton).setOnClickListener {
            dismiss()
            listener.onViewTaskDetails(task)
        }
        view.findViewById<MaterialButton>(R.id.assignButton).setOnClickListener {
            dismiss()
            listener.onAssignTask(task)
        }
        view.findViewById<MaterialButton>(R.id.editButton).setOnClickListener {
            dismiss()
            listener.onEditTask(task)
        }
        view.findViewById<MaterialButton>(R.id.deleteButton).setOnClickListener {
            dismiss()
            listener.onDeleteTask(task)
        }
        view.findViewById<MaterialButton>(R.id.shareButton).setOnClickListener {
            dismiss()
            listener.onShareTask(task)
        }
    }

    companion object {
        private const val ARG_TASK = "task"
        const val TAG = "TaskOptionsBottomSheet"

        fun newInstance(task: SprintTask) = TaskOptionsBottomSheet().apply {
            arguments = Bundle().apply { putSerializable(ARG_TASK, task) }
        }
    }
}

interface TaskOptionListener {
    fun onViewTaskDetails(task: SprintTask)
    fun onAssignTask(task: SprintTask)
    fun onEditTask(task: SprintTask)
    fun onDeleteTask(task: SprintTask)
    fun onShareTask(task: SprintTask)
}


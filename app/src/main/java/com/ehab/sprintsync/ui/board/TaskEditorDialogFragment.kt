package com.ehab.sprintsync.ui.board

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.ehab.sprintsync.R
import com.ehab.sprintsync.model.SprintTask
import com.ehab.sprintsync.model.TaskLabel
import com.ehab.sprintsync.model.TaskStatus
import com.ehab.sprintsync.util.BidiText
import com.ehab.sprintsync.util.SignalManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class TaskEditorDialogFragment : DialogFragment() {
    private lateinit var boardId: String
    private var existingTask: SprintTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        boardId = requireArguments().getString(ARG_BOARD_ID).orEmpty()
        @Suppress("DEPRECATION")
        existingTask = requireArguments().getSerializable(ARG_TASK) as? SprintTask
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = layoutInflater.inflate(R.layout.dialog_task, null)
        val titleLayout = content.findViewById<TextInputLayout>(R.id.taskTitleLayout)
        val titleInput = content.findViewById<TextInputEditText>(R.id.taskTitleInput)
        val descriptionInput =
            content.findViewById<TextInputEditText>(R.id.taskDescriptionInput)
        val labelInput = content.findViewById<AutoCompleteTextView>(R.id.taskLabelInput)
        val statusInput = content.findViewById<AutoCompleteTextView>(R.id.taskStatusInput)
        val labels = TaskLabel.entries.map(TaskLabel::wireValue)
        val statuses = resources.getStringArray(R.array.task_statuses)

        labelInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        )
        statusInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, statuses)
        )

        existingTask?.let { task ->
            titleInput.setText(task.title)
            descriptionInput.setText(task.description)
            labelInput.setText(task.taskLabel().wireValue, false)
            statusInput.setText(statuses[task.taskStatus().ordinal], false)
        } ?: run {
            labelInput.setText(TaskLabel.FEATURE.wireValue, false)
            statusInput.setText(statuses.first(), false)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingTask == null) R.string.new_task else R.string.edit_task)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text?.toString().orEmpty().trim()
                if (title.isBlank()) {
                    titleLayout.error = getString(R.string.required_field)
                    return@setOnClickListener
                }
                titleLayout.error = null
                val statusIndex = statuses.indexOf(statusInput.text.toString()).coerceAtLeast(0)
                val selectedStatus = TaskStatus.entries[statusIndex]
                val selectedLabel = TaskLabel.fromValue(labelInput.text.toString())
                val task = existingTask?.copy(
                    title = title,
                    description = descriptionInput.text?.toString().orEmpty(),
                    label = selectedLabel.wireValue,
                    status = selectedStatus.wireValue
                ) ?: SprintTask.Builder()
                    .boardId(boardId)
                    .title(title)
                    .description(descriptionInput.text?.toString().orEmpty())
                    .label(selectedLabel)
                    .status(selectedStatus)
                    .build()

                dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
                val viewModel = ViewModelProvider(
                    requireActivity(),
                    BoardViewModel.Factory(boardId)
                )[BoardViewModel::class.java]
                viewModel.saveTask(task) { result ->
                    result.onSuccess {
                        // Resolve both before dismissing: once the dialog is gone the
                        // fragment can be detached and requireContext()/getString() throw.
                        val context = requireContext()
                        val savedMessage = getString(R.string.task_saved)
                        dialog.dismiss()
                        SignalManager.success(context, savedMessage)
                    }.onFailure {
                        dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                        SignalManager.error(
                            requireContext(),
                            getString(
                                R.string.something_went_wrong,
                                BidiText.isolate(it.localizedMessage.orEmpty())
                            )
                        )
                    }
                }
            }
        }
        return dialog
    }

    companion object {
        private const val ARG_BOARD_ID = "board_id"
        private const val ARG_TASK = "task"
        const val TAG = "TaskEditorDialog"

        fun newInstance(boardId: String, task: SprintTask? = null) =
            TaskEditorDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BOARD_ID, boardId)
                    task?.let { putSerializable(ARG_TASK, it) }
                }
            }
    }
}


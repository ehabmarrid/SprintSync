package com.ehab.sprintsync.ui.board

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ehab.sprintsync.R
import com.ehab.sprintsync.model.SprintTask
import com.ehab.sprintsync.model.TaskLabel
import com.ehab.sprintsync.model.TaskStatus
import com.ehab.sprintsync.model.UserProfile
import com.ehab.sprintsync.util.ImageLoader

/**
 * @param onTaskInteraction invoked for both tap and long-press. Tap is what users expect
 * on mobile; long-press is kept because the presentation demonstrates it. Both open the
 * same options sheet deliberately - this is not a duplicated listener left by accident.
 */
class TaskAdapter(
    private val onTaskInteraction: (SprintTask) -> Unit,
    private val onMoveRequested: (SprintTask, TaskStatus) -> Unit
) : ListAdapter<SprintTask, TaskAdapter.TaskViewHolder>(TaskDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view, onTaskInteraction, onMoveRequested)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: TaskViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    class TaskViewHolder(
        private val root: View,
        private val onTaskInteraction: (SprintTask) -> Unit,
        private val onMoveRequested: (SprintTask, TaskStatus) -> Unit
    ) : RecyclerView.ViewHolder(root) {
        /** Ids of the actions added on the last bind, so a recycled card does not stack them. */
        private val accessibilityActions = mutableListOf<Int>()
        private val title: TextView = root.findViewById(R.id.taskTitle)
        private val description: TextView = root.findViewById(R.id.taskDescription)
        private val label: TextView = root.findViewById(R.id.taskLabel)
        private val assigneeImage: ImageView = root.findViewById(R.id.assigneeImage)
        private val assigneeInitials: TextView = root.findViewById(R.id.assigneeInitials)

        fun bind(task: SprintTask) {
            title.text = task.title
            description.text = task.description
            description.visibility = if (task.description.isBlank()) View.GONE else View.VISIBLE
            label.text = task.taskLabel().wireValue
            applyTagStyle(task.taskLabel())

            val hasAvatar = task.assigneeAvatarUrl.isNotBlank()
            assigneeImage.visibility = if (hasAvatar) View.VISIBLE else View.GONE
            assigneeInitials.visibility = if (hasAvatar) View.GONE else View.VISIBLE
            if (hasAvatar) {
                ImageLoader.loadCircle(assigneeImage, task.assigneeAvatarUrl)
            } else {
                assigneeInitials.text = UserProfile.initialsOf(task.assigneeName, "—")
            }

            root.setOnLongClickListener {
                onTaskInteraction(task)
                true
            }
            root.setOnClickListener { onTaskInteraction(task) }
            addMoveActions(task)
        }

        /**
         * The swipe gesture is invisible to TalkBack, so the same two moves are published as
         * custom accessibility actions built from the same strings as the swipe labels.
         */
        private fun addMoveActions(task: SprintTask) {
            accessibilityActions.forEach { ViewCompat.removeAccessibilityAction(root, it) }
            accessibilityActions.clear()
            val context = root.context
            listOfNotNull(task.taskStatus().previous(), task.taskStatus().next()).forEach { target ->
                val label = context.getString(
                    R.string.move_to_status,
                    context.getString(target.labelRes())
                )
                accessibilityActions += ViewCompat.addAccessibilityAction(root, label) { _, _ ->
                    onMoveRequested(task, target)
                    true
                }
            }
        }

        fun recycle() {
            ImageLoader.clear(assigneeImage)
        }

        private fun applyTagStyle(tag: TaskLabel) {
            val context = root.context
            val (background, textColor) = when (tag) {
                TaskLabel.BUG -> R.drawable.bg_tag_bug to R.color.tag_bug_text
                TaskLabel.UI -> R.drawable.bg_tag_ui to R.color.tag_ui_text
                TaskLabel.API -> R.drawable.bg_tag_api to R.color.tag_api_text
                TaskLabel.FEATURE, TaskLabel.DOCS ->
                    R.drawable.bg_tag_feature to R.color.tag_feature_text
            }
            label.setBackgroundResource(background)
            label.setTextColor(ContextCompat.getColor(context, textColor))
        }
    }

    private object TaskDiff : DiffUtil.ItemCallback<SprintTask>() {
        override fun areItemsTheSame(oldItem: SprintTask, newItem: SprintTask): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SprintTask, newItem: SprintTask): Boolean =
            oldItem == newItem
    }
}


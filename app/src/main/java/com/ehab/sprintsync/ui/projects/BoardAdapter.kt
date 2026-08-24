package com.ehab.sprintsync.ui.projects

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ehab.sprintsync.R
import com.ehab.sprintsync.model.Board
import com.ehab.sprintsync.model.UserProfile

class BoardAdapter(
    private val onBoardClick: (Board) -> Unit
) : ListAdapter<Board, BoardAdapter.BoardViewHolder>(BoardDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_board, parent, false)
        return BoardViewHolder(view as ViewGroup, onBoardClick)
    }

    override fun onBindViewHolder(holder: BoardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class BoardViewHolder(
        private val root: ViewGroup,
        private val onBoardClick: (Board) -> Unit
    ) : RecyclerView.ViewHolder(root) {
        private val boardName: TextView = root.findViewById(R.id.boardName)
        private val boardMeta: TextView = root.findViewById(R.id.boardMeta)
        private val sprintLabel: TextView = root.findViewById(R.id.sprintLabel)
        private val membersContainer: LinearLayout = root.findViewById(R.id.membersContainer)

        fun bind(board: Board) {
            val context = root.context
            boardName.text = board.name
            // taskCount is maintained by ServerValue.increment, which cannot clamp, so a
            // concurrent delete of the same task can drive it below zero. Clamp on render
            // rather than let "-1 tasks" reach the screen.
            val taskCount = board.taskCount.coerceAtLeast(0)
            val memberCount = board.memberNames.size.coerceAtLeast(board.memberIds.size)
            boardMeta.text = context.getString(
                R.string.tasks_and_members,
                context.resources.getQuantityString(R.plurals.task_count, taskCount, taskCount),
                context.resources.getQuantityString(R.plurals.member_count, memberCount, memberCount)
            )
            sprintLabel.text = board.sprintLabel.uppercase()
            root.setOnClickListener { onBoardClick(board) }

            membersContainer.removeAllViews()
            val visibleMembers = board.memberNames.take(MAX_VISIBLE_MEMBERS)
            visibleMembers.forEachIndexed { index, memberName ->
                membersContainer.addView(createAvatar(memberName, index))
            }
            val extraCount = board.memberNames.size - visibleMembers.size
            if (extraCount > 0) {
                membersContainer.addView(createOverflow(extraCount))
            }
        }

        private fun createAvatar(name: String, index: Int): TextView {
            val context = root.context
            val size = context.resources.getDimensionPixelSize(R.dimen.avatar_size)
            return TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    if (index > 0) marginStart = -8.dp()
                }
                background = ContextCompat.getDrawable(context, R.drawable.bg_avatar)
                ViewCompat.setBackgroundTintList(
                    this,
                    ColorStateList.valueOf(
                        ContextCompat.getColor(
                            context,
                            when (index % 3) {
                                1 -> R.color.avatar_green
                                2 -> R.color.avatar_purple
                                else -> R.color.avatar_blue
                            }
                        )
                    )
                )
                gravity = android.view.Gravity.CENTER
                text = UserProfile.initialsOf(name, "?")
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        }

        private fun createOverflow(count: Int): TextView {
            val context = root.context
            val size = context.resources.getDimensionPixelSize(R.dimen.avatar_size)
            return TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = -8.dp()
                }
                background = ContextCompat.getDrawable(context, R.drawable.bg_avatar)
                ViewCompat.setBackgroundTintList(
                    this,
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.color_surface_variant))
                )
                gravity = android.view.Gravity.CENTER
                text = context.getString(R.string.member_overflow, count)
                setTextColor(ContextCompat.getColor(context, R.color.color_text_secondary))
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        }

        private fun Int.dp(): Int =
            (this * root.resources.displayMetrics.density).toInt()
    }

    private object BoardDiff : DiffUtil.ItemCallback<Board>() {
        override fun areItemsTheSame(oldItem: Board, newItem: Board): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Board, newItem: Board): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val MAX_VISIBLE_MEMBERS = 3
    }
}


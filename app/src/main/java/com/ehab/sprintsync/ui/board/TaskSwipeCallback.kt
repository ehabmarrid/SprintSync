package com.ehab.sprintsync.ui.board

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.ehab.sprintsync.R
import com.ehab.sprintsync.model.SprintTask
import com.ehab.sprintsync.model.TaskStatus

/**
 * Moves a task to the adjacent column by swiping its card.
 *
 * Direction is **logical**, not physical: the flags are START and END. That matters because
 * the board itself mirrors - in Hebrew the tab strip runs right-to-left, so "To Do" is the
 * rightmost column and forward progress is physically leftward. Hard-coding LEFT and RIGHT
 * would make the gesture run backwards for every RTL user.
 *
 * At the ends of the board the swipe is disabled per item rather than allowed and bounced:
 * a card in Done simply will not move further forward, which reads as "there is nothing
 * there" instead of "that failed".
 */
class TaskSwipeCallback(
    context: Context,
    private val onMoveRequested: (SprintTask, TaskStatus, RecyclerView.ViewHolder) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START or ItemTouchHelper.END) {

    private val forwardColor = ContextCompat.getColor(context, R.color.color_primary)
    private val backwardColor = ContextCompat.getColor(context, R.color.color_surface_variant)
    private val onForwardColor = ContextCompat.getColor(context, R.color.color_on_primary)
    private val onBackwardColor = ContextCompat.getColor(context, R.color.color_text_secondary)
    private val corner = context.resources.getDimension(R.dimen.card_corner)
    private val padding = context.resources.getDimension(R.dimen.space_md)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.resources.getDimension(R.dimen.swipe_label_text)
        isFakeBoldText = true
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val status = taskAt(recyclerView, viewHolder)?.taskStatus() ?: return 0
        var swipeFlags = 0
        if (status.previous() != null) swipeFlags = swipeFlags or ItemTouchHelper.START
        if (status.next() != null) swipeFlags = swipeFlags or ItemTouchHelper.END
        return makeMovementFlags(0, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val task = taskAt(viewHolder) ?: return
        val destination = destinationFor(task.taskStatus(), isForward(viewHolder.itemView, direction))
            ?: return
        onMoveRequested(task, destination, viewHolder)
    }

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
            drawDestination(canvas, recyclerView, viewHolder, dX)
        }
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    /** Paints the revealed strip with the colour and name of the column the card is heading to. */
    private fun drawDestination(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float
    ) {
        val item = viewHolder.itemView
        val status = taskAt(recyclerView, viewHolder)?.taskStatus() ?: return
        // dX is absolute, so translate it into "toward the next column" for this locale.
        val forward = if (isRtl(item)) dX < 0 else dX > 0
        val destination = destinationFor(status, forward) ?: return

        val revealedFromLeft = dX > 0
        val left = if (revealedFromLeft) item.left.toFloat() else item.right + dX
        val right = if (revealedFromLeft) item.left + dX else item.right.toFloat()

        backgroundPaint.color = if (forward) forwardColor else backwardColor
        canvas.drawRoundRect(
            left, item.top.toFloat(), right, item.bottom.toFloat(), corner, corner, backgroundPaint
        )

        labelPaint.color = if (forward) onForwardColor else onBackwardColor
        val label = recyclerView.context.getString(destination.labelRes())
        val baseline = item.top + item.height / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f
        if (revealedFromLeft) {
            labelPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, left + padding, baseline, labelPaint)
        } else {
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(label, right - padding, baseline, labelPaint)
        }
    }

    private fun destinationFor(status: TaskStatus, forward: Boolean): TaskStatus? =
        if (forward) status.next() else status.previous()

    private fun isRtl(view: View): Boolean =
        view.layoutDirection == View.LAYOUT_DIRECTION_RTL

    /**
     * Maps the direction [onSwiped] reports back onto board order.
     *
     * Measured on recyclerview 1.1.0 rather than assumed, because assuming it got this wrong
     * twice: [onSwiped] receives the **relative** constant, not an absolute one. A forward
     * swipe arrives as END (32) and a backward swipe as START (16), in LTR (layoutDirection
     * 0) and RTL (layoutDirection 1) alike. Relative flags are layout-independent, so END
     * already means "toward the next column" in both locales and no layout lookup is needed
     * on that path - which is why the same code works in English and Hebrew.
     *
     * The LEFT/RIGHT branches are a fallback for versions that resolve the direction before
     * the callback. They must consult layout direction, since forward is physically right in
     * LTR and physically left in RTL. The four constants do not collide - LEFT 4, RIGHT 8,
     * START 16, END 32 - so testing the relative pair first is unambiguous.
     */
    private fun isForward(view: View, direction: Int): Boolean = when {
        direction and ItemTouchHelper.END != 0 -> true
        direction and ItemTouchHelper.START != 0 -> false
        isRtl(view) -> direction == ItemTouchHelper.LEFT
        else -> direction == ItemTouchHelper.RIGHT
    }

    private fun taskAt(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): SprintTask? = (recyclerView.adapter as? TaskAdapter)
        ?.currentList
        ?.getOrNull(viewHolder.bindingAdapterPosition)

    private fun taskAt(viewHolder: RecyclerView.ViewHolder): SprintTask? =
        (viewHolder.bindingAdapter as? TaskAdapter)
            ?.currentList
            ?.getOrNull(viewHolder.bindingAdapterPosition)
}

package com.ehab.sprintsync.ui.board

import androidx.annotation.StringRes
import com.ehab.sprintsync.R
import com.ehab.sprintsync.model.TaskStatus

/**
 * The single mapping from a status to its display string, shared by the tabs, the options
 * sheet, the detail screen, the share text, the swipe labels and the accessibility actions.
 *
 * It lives in `ui/` rather than on [TaskStatus] itself so the model stays free of resource
 * references. It exists at all because there were already three identical `when` blocks and
 * the swipe work would have added a fourth.
 */
@StringRes
fun TaskStatus.labelRes(): Int = when (this) {
    TaskStatus.TODO -> R.string.to_do
    TaskStatus.IN_PROGRESS -> R.string.in_progress
    TaskStatus.DONE -> R.string.done
}

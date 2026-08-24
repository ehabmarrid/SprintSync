package com.ehab.sprintsync.ui.board

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.ehab.sprintsync.model.TaskStatus

class TaskPagerAdapter(
    activity: FragmentActivity,
    private val boardId: String
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = TaskStatus.entries.size

    override fun createFragment(position: Int): Fragment =
        TaskListFragment.newInstance(boardId, TaskStatus.entries[position])
}


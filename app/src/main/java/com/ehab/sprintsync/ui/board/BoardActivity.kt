package com.ehab.sprintsync.ui.board

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.ehab.sprintsync.R
import com.ehab.sprintsync.data.RepositoryProvider
import com.ehab.sprintsync.model.SprintTask
import com.ehab.sprintsync.model.TaskStatus
import com.ehab.sprintsync.ui.common.InsetsAwareActivity
import com.ehab.sprintsync.util.BidiText
import com.ehab.sprintsync.util.SignalManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class BoardActivity : InsetsAwareActivity(), TaskInteractionListener, TaskOptionListener {
    private lateinit var boardId: String
    private lateinit var boardName: String
    private lateinit var sprintLabel: String
    private lateinit var viewModel: BoardViewModel
    private lateinit var summaryView: android.widget.TextView
    private lateinit var detailContainer: android.widget.FrameLayout
    private lateinit var addTaskFab: FloatingActionButton
    private lateinit var syncIndicator: android.widget.TextView
    private val repository by lazy { RepositoryProvider.repository }
    private val syncHandler = Handler(Looper.getMainLooper())
    private var lastSyncAt: Long = SystemClock.elapsedRealtime()

    /** Re-posts itself, so one Runnable drives the whole tick with no extra thread. */
    private val syncTicker = object : Runnable {
        override fun run() {
            renderSyncAge()
            syncHandler.postDelayed(this, SYNC_TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        boardId = intent.getStringExtra(EXTRA_BOARD_ID).orEmpty()
        boardName = intent.getStringExtra(EXTRA_BOARD_NAME).orEmpty()
        sprintLabel = intent.getStringExtra(EXTRA_SPRINT_LABEL).orEmpty()
        if (boardId.isBlank()) {
            finish()
            return
        }

        setContentView(R.layout.activity_board)
        summaryView = findViewById(R.id.boardSummary)
        detailContainer = findViewById(R.id.detailContainer)
        addTaskFab = findViewById(R.id.addTaskFab)
        syncIndicator = findViewById(R.id.syncIndicator)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = boardName
        // Up mirrors system back: pop the detail fragment first, leave the board only when
        // the back stack is empty.
        toolbar.setNavigationOnClickListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }

        viewModel = ViewModelProvider(
            this,
            BoardViewModel.Factory(boardId)
        )[BoardViewModel::class.java]
        viewModel.tasks.observe(this) { tasks ->
            summaryView.text = getString(R.string.board_summary, BidiText.isolate(sprintLabel), tasks.size)
            // Every emission is a confirmed round trip from the database, so it is the
            // moment the "last synced" clock restarts.
            lastSyncAt = SystemClock.elapsedRealtime()
            renderSyncAge()
        }
        viewModel.errors.observe(this, ::showError)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        viewPager.adapter = TaskPagerAdapter(this, boardId)
        viewPager.offscreenPageLimit = TaskStatus.entries.size
        // Swiping a card moves it between columns, and a horizontal drag cannot mean two
        // things at once - see TaskSwipeCallback. The three tabs stay tappable.
        viewPager.isUserInputEnabled = false
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val tabTitles = listOf(R.string.to_do, R.string.in_progress, R.string.done)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.setText(tabTitles[position])
        }.attach()
        viewPager.currentItem = TaskStatus.IN_PROGRESS.ordinal

        addTaskFab.setOnClickListener {
            TaskEditorDialogFragment.newInstance(boardId)
                .show(supportFragmentManager, TaskEditorDialogFragment.TAG)
        }

        // Driven by the detail fragment's own view lifecycle rather than by
        // backStackEntryCount: an OnBackStackChangedListener reading that count hid the FAB
        // on push but did not restore it on pop, so the chrome and the fragment could
        // disagree. These two callbacks bracket exactly the window the detail view exists,
        // including after a rotation, where the restored fragment recreates its view while
        // this Activity is still in onCreate/onStart.
        supportFragmentManager.registerFragmentLifecycleCallbacks(detailChromeCallbacks, false)
    }

    private val detailChromeCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: View,
            savedInstanceState: Bundle?
        ) {
            if (f is TaskDetailFragment) showDetailChrome(true)
        }

        override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
            if (f is TaskDetailFragment) showDetailChrome(false)
        }
    }

    /** The container hosts the detail fragment; the FAB would otherwise float over it. */
    private fun showDetailChrome(showing: Boolean) {
        detailContainer.visibility = if (showing) View.VISIBLE else View.GONE
        addTaskFab.visibility = if (showing) View.GONE else View.VISIBLE
    }

    /**
     * Starts the "last synced" tick, in Firebase mode only.
     *
     * Handler + postDelayed rather than Timer/TimerTask: the tick exists purely to update a
     * TextView, so it belongs on the main thread. A Timer would own a background thread and
     * force every tick to marshal back through runOnUiThread - a thread and a hop bought
     * for nothing. This also matches how SplashActivity already schedules its delay.
     */
    override fun onStart() {
        super.onStart()
        if (!repository.isDemoMode) syncHandler.post(syncTicker)
    }

    override fun onStop() {
        // The Runnable re-posts itself, so without this the tick outlives the screen.
        syncHandler.removeCallbacks(syncTicker)
        super.onStop()
    }

    /** Never shown in Demo Mode: there is no sync there, so an age would be a lie. */
    private fun renderSyncAge() {
        if (repository.isDemoMode) {
            syncIndicator.visibility = View.GONE
            return
        }
        syncIndicator.visibility = View.VISIBLE
        val seconds = ((SystemClock.elapsedRealtime() - lastSyncAt) / 1000L).toInt()
        syncIndicator.text = if (seconds < 1) {
            getString(R.string.sync_just_now)
        } else {
            resources.getQuantityString(R.plurals.synced_seconds_ago, seconds, seconds)
        }
    }

    override fun onViewTaskDetails(task: SprintTask) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.detailContainer, TaskDetailFragment.newInstance(task), TaskDetailFragment.TAG)
            .addToBackStack(TaskDetailFragment.TAG)
            .commit()
    }

    override fun onTaskSelected(task: SprintTask) {
        if (supportFragmentManager.findFragmentByTag(TaskOptionsBottomSheet.TAG) == null) {
            TaskOptionsBottomSheet.newInstance(task)
                .show(supportFragmentManager, TaskOptionsBottomSheet.TAG)
        }
    }

    override fun onAssignTask(task: SprintTask) {
        viewModel.assignTask(task) { result ->
            result.onSuccess {
                SignalManager.success(this, getString(R.string.task_assigned))
            }.onFailure(::showError)
        }
    }

    override fun onEditTask(task: SprintTask) {
        TaskEditorDialogFragment.newInstance(boardId, task)
            .show(supportFragmentManager, TaskEditorDialogFragment.TAG)
    }

    override fun onDeleteTask(task: SprintTask) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_task)
            .setMessage(getString(R.string.delete_confirmation, BidiText.isolate(task.title)))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteTask(task) { result ->
                    result.onSuccess {
                        SignalManager.success(this, getString(R.string.task_deleted))
                    }.onFailure(::showError)
                }
            }
            .show()
    }

    override fun onShareTask(task: SprintTask) {
        val statusText = getString(task.taskStatus().labelRes())
        val shareText = getString(R.string.share_task_text, task.title, statusText, boardName)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
    }

    private fun showError(error: Throwable) {
        SignalManager.error(
            this,
            getString(
                R.string.something_went_wrong,
                BidiText.isolate(error.localizedMessage.orEmpty())
            )
        )
    }

    companion object {
        private const val SYNC_TICK_MS = 5_000L

        const val EXTRA_BOARD_ID = "extra_board_id"
        const val EXTRA_BOARD_NAME = "extra_board_name"
        const val EXTRA_SPRINT_LABEL = "extra_sprint_label"
    }
}

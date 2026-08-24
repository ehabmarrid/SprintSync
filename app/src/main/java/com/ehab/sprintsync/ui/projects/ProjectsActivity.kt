package com.ehab.sprintsync.ui.projects

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ehab.sprintsync.R
import com.ehab.sprintsync.data.RepositoryProvider
import com.ehab.sprintsync.data.Subscription
import com.ehab.sprintsync.model.Board
import com.ehab.sprintsync.ui.auth.LoginActivity
import com.ehab.sprintsync.ui.board.BoardActivity
import com.ehab.sprintsync.ui.common.InsetsAwareActivity
import com.ehab.sprintsync.ui.profile.ProfileActivity
import com.ehab.sprintsync.util.LocaleManager
import com.ehab.sprintsync.util.BidiText
import com.ehab.sprintsync.util.SignalManager
import com.ehab.sprintsync.util.ThemeManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ProjectsActivity : InsetsAwareActivity() {
    private val repository by lazy { RepositoryProvider.repository }
    private val adapter = BoardAdapter(::openBoard)
    private var boardsSubscription: Subscription? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var boardCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_projects)

        recyclerView = findViewById(R.id.projectsRecycler)
        emptyState = findViewById(R.id.emptyState)
        boardCount = findViewById(R.id.boardCount)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<TextView>(R.id.modeBadge).setText(
            if (repository.isDemoMode) R.string.demo_mode else R.string.firebase_mode
        )
        findViewById<FloatingActionButton>(R.id.addBoardFab).setOnClickListener {
            showCreateBoardDialog()
        }
        configureToolbar()
    }

    override fun onStart() {
        super.onStart()
        boardsSubscription = repository.observeBoards(
            observer = { boards -> renderBoards(boards) },
            onError = ::showError
        )
    }

    override fun onStop() {
        boardsSubscription?.cancel()
        boardsSubscription = null
        super.onStop()
    }

    private fun configureToolbar() {
        findViewById<MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                R.id.action_theme -> {
                    ThemeManager.toggle(this)
                    // The cycle has three states and two of them can look identical on a
                    // given device, so name the mode instead of leaving the tap silent.
                    SignalManager.success(this, getString(themeModeLabel()))
                    true
                }
                R.id.action_language -> {
                    showLanguageDialog()
                    true
                }
                R.id.action_logout -> {
                    repository.signOut()
                    startActivity(
                        Intent(this, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    true
                }
                else -> false
            }
        }
    }

    private fun renderBoards(boards: List<Board>) {
        adapter.submitList(boards)
        boardCount.text =
            resources.getQuantityString(R.plurals.active_boards, boards.size, boards.size)
        emptyState.visibility = if (boards.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (boards.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCreateBoardDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_board, null)
        val nameLayout = view.findViewById<TextInputLayout>(R.id.projectNameLayout)
        val nameInput = view.findViewById<TextInputEditText>(R.id.projectNameInput)
        val sprintInput = view.findViewById<TextInputEditText>(R.id.sprintLabelInput)
        val emailsInput = view.findViewById<TextInputEditText>(R.id.memberEmailsInput)
        sprintInput.setText(DEFAULT_SPRINT)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_project)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.create, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString().orEmpty().trim()
                if (name.isBlank()) {
                    nameLayout.error = getString(R.string.required_field)
                    return@setOnClickListener
                }
                nameLayout.error = null
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
                val emails = emailsInput.text?.toString().orEmpty()
                    .split(",")
                    .map(String::trim)
                    .filter(String::isNotBlank)
                repository.createBoard(
                    name = name,
                    sprintLabel = sprintInput.text?.toString().orEmpty(),
                    memberEmails = emails
                ) { result ->
                    result.onSuccess { creation ->
                        dialog.dismiss()
                        // One signal, not two: an invite that matched nothing is worth
                        // saying out loud, but the board was still created.
                        val message = buildString {
                            append(getString(R.string.board_created))
                            if (creation.unresolvedEmails.isNotEmpty()) {
                                append(' ')
                                append(
                                    resources.getQuantityString(
                                        R.plurals.unmatched_invites,
                                        creation.unresolvedEmails.size,
                                        creation.unresolvedEmails.size
                                    )
                                )
                            }
                        }
                        SignalManager.success(this, message)
                    }.onFailure {
                        dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                        showError(it)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun openBoard(board: Board) {
        startActivity(
            Intent(this, BoardActivity::class.java)
                .putExtra(BoardActivity.EXTRA_BOARD_ID, board.id)
                .putExtra(BoardActivity.EXTRA_BOARD_NAME, board.name)
                .putExtra(BoardActivity.EXTRA_SPRINT_LABEL, board.sprintLabel)
        )
    }

    /**
     * A picker rather than a cycle, unlike the theme action: cycling would make reaching
     * Hebrew from English a two-tap operation whose first tap changes nothing visible.
     */
    private fun showLanguageDialog() {
        val options = listOf(
            LocaleManager.AppLocale.SYSTEM to getString(R.string.language_system),
            LocaleManager.AppLocale.ENGLISH to getString(R.string.language_english),
            LocaleManager.AppLocale.HEBREW to getString(R.string.language_hebrew)
        )
        val current = options.indexOfFirst { it.first == LocaleManager.current() }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(
                options.map { it.second }.toTypedArray(),
                current
            ) { dialog, index ->
                dialog.dismiss()
                // Recreates every activity in the task, so nothing is read from the old
                // configuration afterwards.
                LocaleManager.apply(options[index].first)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun themeModeLabel(): Int = when (ThemeManager.currentMode(this)) {
        ThemeManager.ThemeMode.SYSTEM -> R.string.theme_mode_system
        ThemeManager.ThemeMode.LIGHT -> R.string.theme_mode_light
        ThemeManager.ThemeMode.DARK -> R.string.theme_mode_dark
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
        private const val DEFAULT_SPRINT = "SPRINT-1"
    }
}

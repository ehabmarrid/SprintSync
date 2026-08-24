package com.ehab.sprintsync.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.ehab.sprintsync.R
import com.ehab.sprintsync.data.RepositoryProvider
import com.ehab.sprintsync.model.UserProfile
import com.ehab.sprintsync.ui.common.InsetsAwareActivity
import com.ehab.sprintsync.util.ImageLoader
import com.ehab.sprintsync.util.BidiText
import com.ehab.sprintsync.util.SignalManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class ProfileActivity : InsetsAwareActivity() {
    private val repository by lazy { RepositoryProvider.repository }
    private var selectedImageUri: Uri? = null

    private lateinit var avatarImage: ImageView
    private lateinit var avatarInitials: TextView
    private lateinit var uploadButton: MaterialButton

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedImageUri = uri
            showAvatar(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        avatarImage = findViewById(R.id.avatarImage)
        avatarInitials = findViewById(R.id.avatarInitials)
        uploadButton = findViewById(R.id.uploadPhotoButton)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        repository.currentUser()?.let(::renderUser)
        findViewById<MaterialButton>(R.id.choosePhotoButton).setOnClickListener {
            imagePicker.launch(arrayOf("image/*"))
        }
        uploadButton.setOnClickListener { uploadSelectedAvatar() }
    }

    private fun renderUser(user: UserProfile) {
        findViewById<TextView>(R.id.userName).text = user.name
        findViewById<TextView>(R.id.userEmail).text = user.email
        if (user.avatarUrl.isNotBlank()) {
            showAvatar(user.avatarUrl)
        } else {
            avatarImage.visibility = View.GONE
            avatarInitials.visibility = View.VISIBLE
            avatarInitials.text = user.initials()
        }
    }

    private fun showAvatar(source: Any) {
        avatarInitials.visibility = View.GONE
        avatarImage.visibility = View.VISIBLE
        ImageLoader.loadCircle(avatarImage, source)
    }

    private fun uploadSelectedAvatar() {
        val uri = selectedImageUri
        if (uri == null) {
            SignalManager.error(this, getString(R.string.select_image_first))
            return
        }
        uploadButton.isEnabled = false
        uploadButton.setText(R.string.loading)
        repository.uploadAvatar(uri) { result ->
            uploadButton.isEnabled = true
            uploadButton.setText(R.string.upload_photo)
            result.onSuccess {
                renderUser(it)
                SignalManager.success(this, getString(R.string.avatar_updated))
            }.onFailure {
                SignalManager.error(
                    this,
                    getString(
                        R.string.something_went_wrong,
                        BidiText.isolate(it.localizedMessage.orEmpty())
                    )
                )
            }
        }
    }
}

package com.ehab.sprintsync.util

import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide

object ImageLoader {
    fun loadCircle(view: ImageView, source: Any) {
        Glide.with(view)
            .load(source)
            .circleCrop()
            .into(view)
    }

    fun clear(view: View) {
        Glide.with(view).clear(view)
    }
}


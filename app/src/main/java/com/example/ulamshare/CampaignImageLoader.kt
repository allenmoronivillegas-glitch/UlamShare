package com.example.ulamshare

import android.widget.ImageView
import com.bumptech.glide.Glide

object CampaignImageLoader {
    fun load(imageView: ImageView, imageUrl: String, fallbackResId: Int) {
        if (imageUrl.isBlank()) {
            Glide.with(imageView)
                .clear(imageView)
            imageView.setImageResource(fallbackResId)
            return
        }

        Glide.with(imageView)
            .load(imageUrl)
            .centerCrop()
            .placeholder(fallbackResId)
            .error(fallbackResId)
            .into(imageView)
    }
}

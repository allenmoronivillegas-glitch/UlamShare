package com.example.ulamshare

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.URL
import java.util.concurrent.Executors

object CampaignImageLoader {
    private val cache = object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(imageView: ImageView, imageUrl: String, fallbackResId: Int) {
        if (imageUrl.isBlank()) {
            imageView.tag = null
            imageView.setImageResource(fallbackResId)
            return
        }

        val cachedBitmap = cache.get(imageUrl)
        imageView.tag = imageUrl
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        imageView.setImageResource(fallbackResId)
        executor.execute {
            val bitmap = runCatching {
                URL(imageUrl).openStream().use(BitmapFactory::decodeStream)
            }.getOrNull() ?: return@execute

            cache.put(imageUrl, bitmap)
            mainHandler.post {
                if (imageView.tag == imageUrl) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }
}

package com.mijia4k.app.net

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.video.VideoFrameDecoder

/**
 * Shared Coil loader for Gallery thumbnails: decodes a video frame for
 * video files and a normal (downsampled, cached) bitmap for images,
 * fetching straight from the camera's HTTP server.
 */
object CameraImageLoader {
    @Volatile private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context.applicationContext)
            .components {
                add(OkHttpNetworkFetcherFactory())
                add(VideoFrameDecoder.Factory())
            }
            .build()
            .also { instance = it }
    }
}

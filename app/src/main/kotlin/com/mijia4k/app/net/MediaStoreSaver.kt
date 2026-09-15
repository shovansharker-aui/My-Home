package com.mijia4k.app.net

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saves a file downloaded from the camera into the phone's own Photos/Videos, via MediaStore. */
object MediaStoreSaver {
    private val videoExtensions = setOf("mp4", "mov", "avi", "ts")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif")

    suspend fun save(context: Context, file: CameraHttpClient.CameraFile, client: CameraHttpClient): Boolean =
        withContext(Dispatchers.IO) {
            val ext = file.name.substringAfterLast('.', "").lowercase()
            val isVideo = ext in videoExtensions
            val collection: android.net.Uri
            val relativePath: String
            val mimeType: String

            if (isVideo) {
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                relativePath = Environment.DIRECTORY_MOVIES + "/Mijia4K"
                mimeType = "video/mp4"
            } else {
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                relativePath = Environment.DIRECTORY_PICTURES + "/Mijia4K"
                mimeType = if (ext in imageExtensions) "image/$ext" else "image/jpeg"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return@withContext false

            resolver.openOutputStream(uri)?.use { out ->
                client.downloadTo(file, out)
            } ?: return@withContext false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        }
}

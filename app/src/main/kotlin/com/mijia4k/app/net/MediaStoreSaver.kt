package com.mijia4k.app.net

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Saves camera files into the phone's own storage, via MediaStore. */
object MediaStoreSaver {
    /** Into Photos/Videos — used for the auto-synced preview cache. */
    suspend fun save(context: Context, file: CameraHttpClient.CameraFile, client: CameraHttpClient): Boolean =
        saveToCollection(
            context = context,
            file = file,
            client = client,
            collection = if (file.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            relativePath = if (file.isVideo) "${Environment.DIRECTORY_MOVIES}/Mijia4K" else "${Environment.DIRECTORY_PICTURES}/Mijia4K",
        )

    /** Into Downloads — used for the explicit download action (preview + original). */
    suspend fun saveToDownloads(context: Context, file: CameraHttpClient.CameraFile, client: CameraHttpClient): Boolean =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToCollection(
                    context = context,
                    file = file,
                    client = client,
                    collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Mijia4K",
                )
            } else {
                // Pre-Q has no Downloads MediaStore collection; write the public
                // directory directly (needs the legacy WRITE_EXTERNAL_STORAGE
                // permission granted on these OS versions).
                runCatching {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "Mijia4K",
                    ).apply { mkdirs() }
                    File(dir, file.name).outputStream().use { out -> client.downloadTo(file, out) }
                }.isSuccess
            }
        }

    private suspend fun saveToCollection(
        context: Context,
        file: CameraHttpClient.CameraFile,
        client: CameraHttpClient,
        collection: Uri,
        relativePath: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        val mimeType = when {
            file.isVideo -> "video/mp4"
            file.isImage -> "image/$ext"
            else -> "application/octet-stream"
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

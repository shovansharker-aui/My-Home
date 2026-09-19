package com.mijia4k.app.net

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Saves camera files into the phone's own storage, via MediaStore. */
object MediaStoreSaver {

    /** Into Downloads — used for the explicit download action. */
    suspend fun saveToDownloads(
        context: Context,
        file: CameraHttpClient.CameraFile,
        client: CameraHttpClient,
    ): Boolean = withContext(Dispatchers.IO) {
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
                val target = File(dir, file.name)
                runCatching {
                    target.outputStream().use { out -> client.downloadTo(file, out) }
                }.onFailure { target.delete() }.getOrThrow()
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
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(file))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return@withContext false

        // Anything that throws after the row exists has to delete it again:
        // a row left at IS_PENDING=1 is invisible to the user but still
        // occupies storage forever, so failed downloads used to silently
        // accumulate junk entries.
        runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                client.downloadTo(file, out)
            } ?: error("Could not open $uri for writing")
        }.fold(
            onSuccess = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }
                true
            },
            onFailure = {
                runCatching { resolver.delete(uri, null, null) }
                false
            },
        )
    }

    /**
     * `"image/" + extension` produced `image/jpg`, which isn't a real MIME
     * type — every photo off this camera is a `.JPG`, so they all landed in
     * MediaStore mistyped. Ask the platform's own mapping instead.
     */
    private fun mimeTypeFor(file: CameraHttpClient.CameraFile): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
            ?: when {
                file.isVideo -> "video/mp4"
                file.isImage -> "image/jpeg"
                else -> "application/octet-stream"
            }
}

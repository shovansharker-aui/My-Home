package com.mijia4k.app.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps a local, app-private copy of each [CameraHttpClient.MediaGroup]'s
 * small preview file, synced against whatever is currently on the camera's
 * SD card: skips anything already downloaded, fetches anything new, and
 * deletes local copies whose camera file is gone (deleted on the camera) —
 * so Gallery can browse instantly offline after the first sync instead of
 * re-fetching over the camera's slow hotspot every time it's opened.
 */
class LocalPreviewCache(context: Context) {
    private val dir = File(context.filesDir, "camera_previews").apply { mkdirs() }

    fun localFileFor(group: CameraHttpClient.MediaGroup): File = File(dir, keyFor(group))

    /** All previews currently cached on disk (for the offline Gallery fallback). */
    fun listCached(): List<File> = dir.listFiles { f -> !f.name.endsWith(".part") }?.toList().orEmpty()

    /** Recovers the original filename from a cached file (see [keyFor] for the encoding). */
    fun displayNameOf(cachedFile: File): String = cachedFile.name.substringAfter('_', cachedFile.name)

    // Full remote path can collide-free identify a file across folders, but
    // isn't a nice filename on its own; prefix a hash of the full path (for
    // uniqueness) and keep the real filename after the first "_" so the
    // offline path can recover a clean display name via [displayNameOf].
    private fun keyFor(group: CameraHttpClient.MediaGroup): String =
        "${group.previewFile.path.hashCode()}_${group.previewFile.name}"

    suspend fun sync(
        client: CameraHttpClient,
        groups: List<CameraHttpClient.MediaGroup>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Unit = withContext(Dispatchers.IO) {
        val expected = groups.mapTo(HashSet()) { keyFor(it) }

        dir.listFiles()?.forEach { local ->
            if (local.name !in expected) local.delete()
        }

        groups.forEachIndexed { index, group ->
            val target = localFileFor(group)
            if (!target.exists()) {
                runCatching {
                    val tmp = File(dir, "${target.name}.part")
                    tmp.outputStream().use { out -> client.downloadTo(group.previewFile, out) }
                    tmp.renameTo(target)
                }.onFailure { File(dir, "${target.name}.part").delete() }
            }
            onProgress(index + 1, groups.size)
        }
    }
}

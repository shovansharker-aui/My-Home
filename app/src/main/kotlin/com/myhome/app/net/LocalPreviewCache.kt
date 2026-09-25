package com.myhome.app.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps a local, app-private copy of each [CameraHttpClient.MediaGroup]'s
 * small poster file, synced against whatever is currently on the camera's
 * SD card: skips anything already downloaded, fetches anything new, and
 * deletes local copies whose camera file is gone (deleted on the camera) —
 * so Gallery can browse instantly offline after the first sync instead of
 * re-fetching over the camera's slow hotspot every time it's opened.
 */
class LocalPreviewCache(context: Context) {
    private val dir = File(context.filesDir, "camera_previews").apply { mkdirs() }

    fun localFileFor(group: CameraHttpClient.MediaGroup): File? =
        keyFor(group)?.let { File(dir, it) }

    /** All posters currently cached on disk (for the offline Gallery fallback). */
    fun listCached(): List<File> =
        dir.listFiles { f -> !f.name.endsWith(PART_SUFFIX) }?.sortedByDescending { it.lastModified() }.orEmpty()

    /** Recovers the original filename from a cached file (see [keyFor] for the encoding). */
    fun displayNameOf(cachedFile: File): String = cachedFile.name.substringAfter('_', cachedFile.name)

    // Full remote path can collide-free identify a file across folders, but
    // isn't a nice filename on its own; prefix a hash of the full path (for
    // uniqueness) and keep the real filename after the first "_" so the
    // offline path can recover a clean display name via [displayNameOf].
    private fun keyFor(group: CameraHttpClient.MediaGroup): String? =
        group.posterFile?.let { "${it.path.hashCode()}_${it.name}" }

    suspend fun sync(
        client: CameraHttpClient,
        groups: List<CameraHttpClient.MediaGroup>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Unit = syncLock.withLock {
        withContext(Dispatchers.IO) {
            val expected = groups.mapNotNullTo(HashSet()) { keyFor(it) }

            dir.listFiles()?.forEach { local ->
                if (local.name !in expected) local.delete()
            }

            groups.forEachIndexed { index, group ->
                // Only photos have something small enough to be worth caching.
                // Videos would mean pulling a 47 MB proxy per tile.
                val poster = group.posterFile
                val target = localFileFor(group)
                if (poster != null && target != null && !target.exists()) {
                    val tmp = File(dir, target.name + PART_SUFFIX)
                    runCatching {
                        tmp.outputStream().use { out -> client.downloadTo(poster, out) }
                        tmp.renameTo(target)
                    }.onFailure { tmp.delete() }
                }
                onProgress(index + 1, groups.size)
            }
        }
    }

    private companion object {
        const val PART_SUFFIX = ".part"

        // The live screen and Gallery both kick off a sync, and they used to
        // be able to run at once — each deleting the other's in-flight
        // ".part" files as "not expected" and losing the download. One
        // process-wide lock keeps them sequential.
        val syncLock = Mutex()
    }
}

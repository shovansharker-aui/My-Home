package com.mijia4k.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream

/**
 * Talks to the camera's port-80 web server, which turns out to be plain
 * Cherokee directory-listing HTML (confirmed empirically against the real
 * camera via the Diagnostics screen) rather than a JSON API: GET a folder
 * path and you get back an `<a>` per entry, trailing-slash hrefs are
 * subfolders, everything else is a file you can GET directly to download.
 *
 * Root also exposes a few pseudo-folders (`live/`, `mjpeg/`, `shutter/`,
 * `pref/`) that are actually GET-triggered actions rather than real
 * directories — [DCIM_ROOT] is used as the default browse root so the
 * Gallery screen doesn't wander into those by default.
 */
class CameraHttpClient(
    private val host: String = CameraEndpoints.HOST,
    private val httpClient: OkHttpClient = OkHttpClient.Builder().build(),
) {
    data class CameraFile(
        val name: String,
        /** Path relative to the web root, always starting with "/". */
        val path: String,
        val isDirectory: Boolean,
        val sizeBytes: Long?,
    ) {
        val url: String get() = "http://${CameraEndpoints.HOST}$path"
        private val extension get() = name.substringAfterLast('.', "").lowercase()
        val isVideo: Boolean get() = extension in VIDEO_EXTENSIONS
        val isImage: Boolean get() = extension in IMAGE_EXTENSIONS
    }

    // Matches one listing row: the link (name/href) and, for files, the
    // size + unit that follow it before the row closes.
    private val rowRegex = Regex(
        """<a class="link" href="([^"]+)">([^<]*)</a>.*?<span class="size">([^<]*)</span><span class="unit">([^<]*)</span>""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    suspend fun listDirectory(path: String = DCIM_ROOT): List<CameraFile> = withContext(Dispatchers.IO) {
        val normalized = if (path.endsWith("/")) path else "$path/"
        val html = httpClient.newCall(Request.Builder().url("http://$host$normalized").build())
            .execute().use { it.body?.string().orEmpty() }

        rowRegex.findAll(html).mapNotNull { m ->
            val href = m.groupValues[1]
            // Skip the column-sort links ("?order=N") and parent-dir/self links.
            if (href.startsWith("?") || href == "../" || href == "./") return@mapNotNull null
            val isDir = href.endsWith("/")
            val name = m.groupValues[2].ifBlank { href.trimEnd('/') }
            val sizeText = m.groupValues[3].trim()
            val unit = m.groupValues[4].trim()
            CameraFile(
                name = name,
                path = normalized + href,
                isDirectory = isDir,
                sizeBytes = if (isDir) null else parseSize(sizeText, unit),
            )
        }.toList()
    }

    /**
     * Each shot on this camera writes two files sharing a basename (e.g. a
     * full-resolution original plus a much smaller companion — a `.THM`
     * alongside an `.MP4`, or similar for photos). This groups a folder
     * listing's flat files by basename and picks the smaller of each pair
     * as the "preview" — the one Gallery should display/play by default —
     * keeping the larger one addressable as the original for downloads.
     * When a file has no pair, it serves as both.
     */
    data class MediaGroup(
        val baseName: String,
        val previewFile: CameraFile,
        val originalFile: CameraFile?,
    )

    /** Recursively lists every file under [root] (default DCIM) and pairs them into [MediaGroup]s. */
    suspend fun listAllMedia(root: String = DCIM_ROOT, maxDepth: Int = 3): List<MediaGroup> =
        withContext(Dispatchers.IO) {
            val files = mutableListOf<CameraFile>()

            suspend fun walk(path: String, depth: Int) {
                if (depth > maxDepth) return
                for (entry in listDirectory(path)) {
                    if (entry.isDirectory) walk(entry.path, depth + 1) else files += entry
                }
            }
            walk(root, 0)

            files.groupBy { it.path.substringBeforeLast('.') }
                .mapNotNull { (baseName, group) ->
                    // A lone .THM with no matching video/photo (an orphaned
                    // thumbnail — this 8-year-old SD card has plenty of old
                    // content left over from before this project) has no
                    // viewable content of its own; showing it as its own
                    // grid entry is just a confusing black tile.
                    if (group.all { it.name.substringAfterLast('.', "").lowercase() == "thm" }) return@mapNotNull null
                    val bySize = group.sortedBy { it.sizeBytes ?: Long.MAX_VALUE }
                    MediaGroup(baseName, previewFile = bySize.first(), originalFile = bySize.getOrNull(1))
                }
                .sortedBy { it.baseName }
        }

    suspend fun downloadTo(file: CameraFile, out: OutputStream): Long = withContext(Dispatchers.IO) {
        httpClient.newCall(Request.Builder().url(file.url).build()).execute().use { resp ->
            check(resp.isSuccessful) { "Download failed: HTTP ${resp.code}" }
            val body = resp.body ?: error("Empty response body")
            body.byteStream().use { input ->
                input.copyTo(out)
            }
        }
    }

    private fun parseSize(sizeText: String, unit: String): Long? {
        val value = sizeText.toDoubleOrNull() ?: return null
        val multiplier = when (unit.uppercase()) {
            "KB" -> 1024.0
            "MB" -> 1024.0 * 1024
            "GB" -> 1024.0 * 1024 * 1024
            else -> 1.0
        }
        return (value * multiplier).toLong()
    }

    companion object {
        const val DCIM_ROOT = "/DCIM/"
        val VIDEO_EXTENSIONS = setOf("mp4", "mov", "avi", "ts")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif")
    }
}

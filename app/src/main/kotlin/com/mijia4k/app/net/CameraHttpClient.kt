package com.mijia4k.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream
import java.util.concurrent.TimeUnit

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
    private val httpClient: OkHttpClient = shared,
) {
    data class CameraFile(
        val name: String,
        /** Path relative to the web root, always starting with "/". */
        val path: String,
        val isDirectory: Boolean,
        val sizeBytes: Long?,
    ) {
        val url: String get() = "http://${CameraEndpoints.HOST}$path"
        val extension: String get() = name.substringAfterLast('.', "").lowercase()
        val isVideo: Boolean get() = extension in VIDEO_EXTENSIONS
        val isImage: Boolean get() = extension in IMAGE_EXTENSIONS

        /** Decodable by the image loader — a RAW `.DNG` is an image but not this. */
        val isDisplayableImage: Boolean get() = extension in DISPLAYABLE_IMAGE_EXTENSIONS

        /**
         * A `.THM` sibling. On this camera that is **not** a thumbnail image
         * despite the extension — it's a lower-resolution copy of the video
         * (47 MB against the original's 545 MB, same `ftyp` MP4 header), which
         * makes it the far better thing to stream over the camera's hotspot.
         */
        val isProxyVideo: Boolean get() = extension in PROXY_EXTENSIONS
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
            CameraFile(
                name = name,
                path = normalized + href,
                isDirectory = isDir,
                sizeBytes = if (isDir) null else parseSize(m.groupValues[3].trim(), m.groupValues[4].trim()),
            )
        }.toList()
    }

    /**
     * One shot on the camera can write several files sharing a basename: an
     * `.MP4` next to a tiny `.THM` poster, or a `.JPG` next to its `.DNG`
     * raw. They're grouped here so Gallery shows one tile per shot.
     *
     * [mediaFile] is deliberately chosen by *kind*, not by size. Picking "the
     * smallest file in the group" looked reasonable but made the `.THM` the
     * thing Gallery displayed and tried to play for every video — no play
     * badge, a still image in the viewer, and a `.THM` filename on screen —
     * and because it fell back to directory order whenever the listing's size
     * column didn't parse, it broke only sometimes, which is worse.
     */
    data class MediaGroup(
        val baseName: String,
        /** The full-resolution original — what "download" saves. */
        val mediaFile: CameraFile,
        /** Lower-resolution copy to stream instead of the original, if the camera made one. */
        val proxyFile: CameraFile?,
        /** Extra sibling worth downloading too (a RAW `.DNG`). */
        val rawFile: CameraFile?,
    ) {
        val isVideo: Boolean get() = mediaFile.isVideo

        /** Streaming a 47 MB proxy beats a 545 MB original over this hotspot. */
        val playbackFile: CameraFile get() = proxyFile ?: mediaFile

        /**
         * Something the image loader can cheaply turn into a grid thumbnail.
         * Photos have one (the JPG itself); videos don't — this camera
         * exposes no thumbnail endpoint, and its `.THM` is a whole second
         * video, so there is nothing small to show. Grid cells fall back to a
         * play badge rather than downloading tens of megabytes per tile.
         */
        val posterFile: CameraFile? get() = mediaFile.takeIf { it.isDisplayableImage }

        /** Everything worth saving when the user downloads this shot. */
        val downloadable: List<CameraFile> get() = listOfNotNull(mediaFile, rawFile)
    }

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
                    val proxy = group.firstOrNull { it.isProxyVideo }
                    val originals = group.filter { !it.isProxyVideo }
                    // A lone .THM with no original beside it (this 8-year-old
                    // card has leftovers) has nothing to anchor a tile to.
                    // Prefer the JPG over its RAW sibling: both are "images",
                    // but only one of them the image loader can decode.
                    val media = originals.firstOrNull { it.isVideo }
                        ?: originals.firstOrNull { it.isDisplayableImage }
                        ?: originals.firstOrNull { it.isImage }
                        ?: return@mapNotNull null
                    MediaGroup(
                        baseName = baseName,
                        mediaFile = media,
                        proxyFile = proxy,
                        rawFile = originals.firstOrNull { it != media },
                    )
                }
                // Newest first. Sorting on the whole basename would group all
                // VID_* after all IMG_* regardless of when they were shot, so
                // order by the timestamp the camera bakes into the name.
                .sortedByDescending { it.mediaFile.name.substringAfter('_', it.baseName) }
        }

    suspend fun downloadTo(file: CameraFile, out: OutputStream): Long = withContext(Dispatchers.IO) {
        httpClient.newCall(Request.Builder().url(file.url).build()).execute().use { resp ->
            check(resp.isSuccessful) { "Download failed: HTTP ${resp.code}" }
            val body = resp.body ?: error("Empty response body")
            body.byteStream().use { input -> input.copyTo(out) }
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
        val DISPLAYABLE_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif")
        val IMAGE_EXTENSIONS = DISPLAYABLE_IMAGE_EXTENSIONS + setOf("dng", "raw")
        val PROXY_EXTENSIONS = setOf("thm")

        // One client for the whole app: each OkHttpClient carries its own
        // connection pool and dispatcher threads, and a fresh one was being
        // built for every gallery sync. Timeouts are generous because the
        // camera's hotspot is slow, but finite so a stalled read can't hang
        // the sync forever.
        private val shared: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}

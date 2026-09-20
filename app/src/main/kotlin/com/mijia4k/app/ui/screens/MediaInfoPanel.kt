package com.mijia4k.app.ui.screens

import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Everything the panel needs about one shot, decoupled from the gallery's internal item type. */
data class MediaInfoData(
    val name: String,
    val isVideo: Boolean,
    /** Something the image loader can draw as the hero picture; null for videos on this camera. */
    val imageModel: Any?,
    /** Where the full file can be read for its metadata; null when only a cached poster exists (offline). */
    val fileUrl: String?,
    val folder: String?,
    val sizeBytes: Long?,
    val extraFiles: List<String>,
)

private val ink = Color(0xFF000000)
private val card = Color(0xFF161616)
private val hairline = Color(0xFF3A3A3A)
private val dim = Color(0xFFB8B8B8)
private val mono = FontFamily.Monospace

private val http by lazy {
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
}

/** Photo/video facts, each optional — whatever the file actually carries. */
private data class Facts(
    val top: List<String> = emptyList(),
    val middle: List<String> = emptyList(),
    val model: String? = null,
    val megapixels: String? = null,
    val takenAt: LocalDateTime? = null,
)

/** Reads the shooting data out of a JPEG's EXIF header; only its first few hundred KB are fetched. */
private suspend fun readPhotoFacts(url: String, sizeBytes: Long?): Facts? = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder().url(url).header("Range", "bytes=0-393215").build()
        http.newCall(request).execute().use { resp ->
            val body = resp.body ?: return@use null
            val exif = ExifInterface(body.byteStream())

            val aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }
                ?.let { "ƒ/%.1f".format(Locale.US, it) }
            val shutter = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let {
                if (it < 1.0) "1/${(1.0 / it).roundToInt()} S" else "%.1f S".format(Locale.US, it)
            }
            val ev = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)
                ?.let { exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, 0.0) }
                ?.let { "%.1f EV".format(Locale.US, it) }
            val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { "ISO $it" }

            val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.let { "$it MM" }
                ?: exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }
                    ?.let { "${it.roundToInt()} MM" }
            val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                .takeIf { it > 0 } ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0)
            val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                .takeIf { it > 0 } ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0)
            val dims = if (w > 0 && h > 0) "$w × $h" else null

            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
            val modelTag = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
            val model = listOf(make, modelTag).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
            val mp = if (w > 0 && h > 0) "${((w.toLong() * h) / 1_000_000.0).roundToInt()} MP" else null

            val taken = (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME))
                ?.let { runCatching { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")) }.getOrNull() }

            Facts(
                top = listOfNotNull(aperture, shutter, ev, iso),
                middle = listOfNotNull(focal, dims, sizeBytes?.let(::formatBytes)),
                model = model,
                megapixels = mp,
                takenAt = taken,
            )
        }
    }.getOrNull()
}

/** Resolution, length and bitrate of a video; can be slow over the hotspot, so the caller bounds the wait. */
private suspend fun readVideoFacts(url: String, sizeBytes: Long?): Facts? = withContext(Dispatchers.IO) {
    withTimeoutOrNull(12_000) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(url, HashMap())
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                Facts(
                    top = listOfNotNull(
                        if (w != null && h != null && w > 0 && h > 0) "$w × $h" else null,
                        ms?.let { formatDuration(it) },
                        bitrate?.let { "%.1f MBPS".format(Locale.US, it / 1_000_000.0) },
                    ),
                    middle = listOfNotNull(sizeBytes?.let(::formatBytes)),
                )
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) "%.2f GB".format(Locale.US, mb / 1024.0) else "%.1f MB".format(Locale.US, mb)
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** The camera stamps `IMG_20260920_093556_0014` — the shot's own clock, no metadata read needed. */
private fun takenFromName(name: String): LocalDateTime? {
    val m = Regex("""(\d{8})_(\d{6})""").find(name) ?: return null
    return runCatching {
        LocalDateTime.parse(m.groupValues[1] + m.groupValues[2], DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
    }.getOrNull()
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }
}

/**
 * Full-screen details for one shot in the dark, airy style of Nothing OS's
 * gallery: the picture fading into black, a large weekday, and rounded cards
 * for the shooting data, storage path and file name.
 */
@Composable
fun MediaInfoPanel(data: MediaInfoData, imageLoader: ImageLoader, onClose: () -> Unit) {
    val facts by produceState<Facts?>(null, data.fileUrl) {
        val url = data.fileUrl
        value = when {
            url == null -> Facts(middle = listOfNotNull(data.sizeBytes?.let(::formatBytes)))
            data.isVideo -> readVideoFacts(url, data.sizeBytes)
            else -> readPhotoFacts(url, data.sizeBytes)
        } ?: Facts(middle = listOfNotNull(data.sizeBytes?.let(::formatBytes)))
    }

    val taken = facts?.takenAt ?: takenFromName(data.name)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(ink)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.9f)) {
                    if (data.imageModel != null) {
                        AsyncImage(
                            model = data.imageModel,
                            contentDescription = data.name,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(Color(0xFF1E1E1E)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color(0xFF555555), modifier = Modifier.size(72.dp))
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, ink))),
                    )
                }

                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        taken?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "Details",
                        color = Color.White,
                        fontFamily = FontFamily.Serif,
                        fontSize = 40.sp,
                    )
                    if (taken != null) {
                        Row(
                            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(dayLabel(taken.toLocalDate()), color = Color.White, fontFamily = mono, fontSize = 16.sp)
                            Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(18.dp).background(hairline))
                            Text(
                                taken.format(DateTimeFormatter.ofPattern("HH:mm")),
                                color = Color.White,
                                fontFamily = mono,
                                fontSize = 16.sp,
                            )
                        }
                    }

                    val f = facts
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(card).padding(horizontal = 18.dp),
                    ) {
                        if (f == null) {
                            Text("READING DETAILS…", color = dim, fontFamily = mono, fontSize = 14.sp, modifier = Modifier.padding(vertical = 28.dp))
                        } else {
                            FactRow(f.top)
                            if (f.top.isNotEmpty() && f.middle.isNotEmpty()) HorizontalDivider(color = hairline)
                            FactRow(f.middle)
                            val model = f.model ?: "MIJIA 4K ACTION CAMERA"
                            HorizontalDivider(color = hairline)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 22.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(model.uppercase(), color = Color.White, fontFamily = mono, fontSize = 16.sp)
                                f.megapixels?.let {
                                    Text(
                                        it,
                                        color = Color.White,
                                        fontFamily = mono,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF4A4A4A))
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        InfoCard("Storage path", "SD card${data.folder.orEmpty()}", Modifier.weight(1f))
                        InfoCard(
                            "Name",
                            (listOf(data.name) + data.extraFiles).joinToString("\n"),
                            Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(48.dp))
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
private fun FactRow(items: List<String>) {
    if (items.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (item in items) {
            Text(item.uppercase(), color = Color.White, fontFamily = mono, fontSize = 16.sp)
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(card).padding(20.dp),
    ) {
        Text(title, color = Color.White, fontSize = 20.sp)
        Text(value, color = dim, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

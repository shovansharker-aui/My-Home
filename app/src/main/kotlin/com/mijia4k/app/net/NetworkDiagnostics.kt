package com.mijia4k.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Probes the camera over its hotspot: which known ports are open, and what a
 * shortlist of likely SD-card file paths on the port-80 web server return.
 *
 * The Ambarella control socket (7878) protocol is confirmed by public research
 * on sibling cameras, but the exact HTTP path(s) this specific firmware serves
 * the DCIM folder from is not documented anywhere public, so this screen exists
 * to find it empirically against the real camera.
 */
class NetworkDiagnostics(
    private val host: String = CameraEndpoints.HOST,
    private val httpClient: OkHttpClient = OkHttpClient.Builder().build(),
) {
    data class PortResult(val port: Int, val label: String, val open: Boolean)

    data class PathProbeResult(
        val path: String,
        val httpStatus: Int?,
        val contentType: String?,
        val bodyPreview: String?,
        val error: String?,
    )

    data class DiagnosticsReport(
        val hostReachable: Boolean,
        val ports: List<PortResult>,
        val pathProbes: List<PathProbeResult>,
    )

    private val knownPorts = listOf(
        23 to "telnet",
        53 to "dns",
        80 to "http (Cherokee)",
        111 to "rpcbind",
        554 to "rtsp",
        CameraEndpoints.CONTROL_PORT to "amba control socket",
        8787 to "message server",
        9888 to "cyborg systems",
        12080 to "unknown",
    )

    private val candidatePaths = listOf(
        "/",
        "/DCIM/",
        "/dcim/",
        "/tmp/SD0/",
        "/tmp/SD0/DCIM/",
        "/sd/",
        "/sd/DCIM/",
        "/mnt/sd/DCIM/",
        "/media/",
        "/list",
        "/filelist",
        "/file_list",
        "/api/file/list",
        "/cgi-bin/list",
    )

    suspend fun run(): DiagnosticsReport = withContext(Dispatchers.IO) {
        val reachable = isHostReachable()
        val ports = knownPorts.map { (port, label) ->
            async { PortResult(port, label, isPortOpen(port)) }
        }.awaitAll()

        val paths = if (ports.first { it.port == 80 }.open) {
            candidatePaths.map { path -> async { probePath(path) } }.awaitAll()
        } else {
            emptyList()
        }

        DiagnosticsReport(reachable, ports, paths)
    }

    private fun isHostReachable(): Boolean = isPortOpen(CameraEndpoints.CONTROL_PORT) ||
        isPortOpen(80) || isPortOpen(554)

    private fun isPortOpen(port: Int, timeoutMs: Int = 800): Boolean =
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            true
        } catch (_: IOException) {
            false
        }

    private fun probePath(path: String): PathProbeResult {
        val url = "http://$host$path"
        return try {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                PathProbeResult(
                    path = path,
                    httpStatus = resp.code,
                    contentType = resp.header("Content-Type"),
                    bodyPreview = body.take(400),
                    error = null,
                )
            }
        } catch (e: IOException) {
            PathProbeResult(path, null, null, null, e.message ?: e.toString())
        }
    }
}
